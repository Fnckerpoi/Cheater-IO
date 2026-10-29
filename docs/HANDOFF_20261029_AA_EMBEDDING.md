# Turbo-IO × Agents-Anywhere 同包嵌入 — 任务交接摘要

> 交接生成时间：2026-10-29（本会话无法读取原会话 `session-0b1795ed-4587-4c73-be68-25baf025a463`，内容由仓库证据 + 记忆重建）
> 仓库：`C:\Users\duriea\Documents\GitHub\Turbo-IO`　分支：`android`　HEAD：`3f3159c`

---

## 1. 任务目标

将 **Agents-Anywhere（AA）** 的原生 Android 界面编入 **Turbo-IO** 的同一个 APK，两个项目保持独立且各自可独立构建；Turbo-IO 侧作为宿主，负责身份、草稿、绑定与派发守卫。

首期（已与用户确认）支持的 AA 能力范围：

1. 发送任务
2. 查询进度与结果
3. 同一会话续聊

**不在首期范围**：取消任务、审批、语音端到端。Server 与电脑端 Connector 保持现有远端部署。

---

## 2. 已冻结契约（不要重新设计）

### 2.1 六卡草稿桥（宿主唯一持有草稿）

- 操作：`selection.draft.open/list/choose/save/close`
- 快照 `selected[level]` = Option Bundle（`id/label/subtitle/available`）；候选字段 `options`；保存成功返回 `saved`
- 每个响应回显 `draftId / requestDraftRevision / authGeneration / level / requestId` 及完整父级；客户端据此丢弃过期回包
- 所有 draft 错误（含 `HOST_CLOSED`、`WORKER_REQUIRED`）必须携带原请求归属
- 级联规则由宿主唯一负责：电脑变化清其余五级；项目变化仅清 Session；运行时变化清 Session/模型/权限；同 ID 重复选择幂等
- 保存只在 DSH 提交锁内执行，原子 commit 后逐字段读回；存储失败或未知送达一律拒绝且不清绑定
- 显式新建会话沿用既有 `@new-session` 标识，保存后写入空锚点；列表缺失/加载失败保留原绑定并标记未验证
- 项目候选与已选卡片只展示名称，`subtitle` 恒空，路径与可能是路径的 ID 不回显；模型与执行权限保留 `subtitle`
- 变更请求进行中禁止关闭候选弹窗；宿主拒绝过期操作后提供显式"重新加载配置"恢复路径

### 2.2 载荷与装配

- AA 独立构建目标：`:app:assembleEmbeddedDebug`（保留 `assembleDebug`）
- 载荷：`assets/turboio/aa/aa-embedded-payload.apk`，不单独安装
- 宿主 framework Activity 使用**隔离类加载器 + 专属资源 Context**
- `com.agentsanywhere.embedded.PayloadEntry` 提供 `create/lifecycle/dispose`
- 宿主桥 `call(String, Bundle): Bundle` 承担 auth / selection / dsh 操作；凭据只在进程内传递，不进 Intent、日志、AA 持久化
- 原 PUBLIC 单点更新流程（仅更新 `classes4.dex`）审计不变；嵌入走独立入口与独立审计
- **宿主 DEX 槽位**：宿主嵌入产物使用 `classes4.dex`，常规 `host-build.mjs` 使用 `classes2.dex`，组合使用前须确认槽位一致

### 2.3 宿主侧安全规则（不得放宽）

- `unknown_delivery` 保护、目标/消息归属校验、会话绑定不得因集成放宽
- 浏览页面不改变绑定；只有显式选择才提交
- 提交前在锁内重验配置版本与身份世代；过期操作返回 `STALE_SELECTION` / `STALE_AUTH` 而非误发
- 实际失败不放宽原子保存、身份代次、未知送达守卫

---

## 3. 已完成并有证据的部分

### 3.1 代码与离线验收（✅）

| 层级 | 结果 | 证据位置 |
|---|---|---|
| AA 聚焦测试 | `ConfigurationStateTest` 22 项、`WorkbenchPolicyTest` 9 项，failures=0 errors=0 | JUnit XML |
| 宿主独立回归（Contract v3，669 项） | DshClient 240 / DshDirectory 30 / DshSessionSelection 52 / DshSettingsPolicy 18 / AaBridgePolicy 62 / AaDispatchFlow 25 / DshSelectionDraft 152 / DshDraftContract 90，各 exit 0 | 隔离输出 `C:\Users\duriea\AppData\Local\Temp\task5-v3-forcefix-IsXM4u` |
| 载荷来源门禁 | exit 0（standalone native=28、source native=28、payload native=0、1476 成员哈希一致、严格 sidecar） | `scripts/verify_payload_gate.ps1` |
| 装配审计 | 3976 宿主成员字节不变；变化仅 `AndroidManifest.xml`/`classes4.dex`；新增仅载荷 + sidecar；移除仅旧签名 | `assembly-report.json` |
| 源码复审 | 模型推理 subtitle 丢失、选择期间关闭失步、标题断言冲突均已修复并独立复审关闭 | 见下节文档 |

> 注意：历史文档中"676 项"为手算笔误，**以逐类日志为准（Contract v3 为 669 项）**。

### 3.2 产物台账（勿混用不同轮的包）

| 产物 | SHA-256 | 用途/状态 |
|---|---|---|
| `artifacts/embedded-aa-ui-final-20261029/TurboIO-AA-ui-aligned-signed.apk` | `48bd2c96…f95d4` ← **提示修复版** | 已安装；89 项 Gradle 测试零失败 |
| `artifacts/embedded-aa-ui-affordance-20261029/TurboIO-AA-ui-affordance-signed.apk` | `48bd2c96ef6ea1bae6e98bef56f03e81864ff67c42a2634e5c6c804bb0cf95d4` | 提示修复版（同一包的另一命名） |
| `artifacts/embedded-model-baseurl-20261029/TurboIO-AA-model-baseurl-cleartext-signed.apk` | `2057194c…59e7` | 明文 HTTP + API 基础地址补全版，已安装 |
| `artifacts/embedded-aa-v3-20261029c/TurboIO-AA-v3-signed.apk` | `89253D8A719D671C4122E45F294D51854B3613814EA0D39A714F6A4CDFE2C1B9`（446,756,157 B） | **Contract v3 最新候选，尚未确认安装** |
| `artifacts/embedded-aa-v3-20261029c/TurboIO-AA-embedded-unsigned.apk` | 报告 `outputSha256=1bd89094…cc6d` | v3 未签名 |
| `artifacts/embedded-aa-v3-20261029c/TurboIO-AA-embedded-aligned-unsigned.apk` | 446,408,511 B | v3 对齐版 |
| `artifacts/embedded-aa-v3-20261029c/TurboIO-AA-embedded-unsigned.apk.report.json` | — | source `d39312a2…5a24b0`、payload `74d168a9…9041`、sidecar `e84871f7…dbe`、addon `bb113063…046e`；changed=[Manifest, classes4.dex]、added=[2 assets]、unchangedMemberCount=3976；`installed:false`、`runtimeValidated:false` |
| `artifacts/embedded-aa-ui-final-20261029/TurboIO-AA-ui-aligned-signed.apk` | `f981333f…d27f`（unsigned） | UI 终版（被后续版本取代） |

v3 目录另含 `addon.jar`、`lead_audit.py`、`sources.txt`、`takeover_ui.xml`、`takeover_screen.png`（19:00 抓取，说明该轮已做真机 UI 抓取）。

### 3.3 已完成真机验证（分轮、分包）

**提示修复版（`48bd2c96…`）**
- 冷启动 PID 存活、crash buffer 无记录
- 展开调度配置后，5 个未满足父级的子卡均显示"请先选择电脑设备"，点击被禁卡不打开弹窗、不报错、不折叠
- 根卡"电脑设备"仍可加载 3 项真实候选
- 滚动可见「刷新配置」「保存并启用」
- 用户完成六卡选择并保存后，lead 只读回读确认：点「刷新配置」后六卡恢复可读名称（电脑设备=主机名、项目="Turbo-IO" **不含路径**、DSH运行时="DSH / dsh · running"、Session="集成AA并优化工作台界面 / sess_dsh_6f324ae3d7727ef52eb62aa4 · running"、模型与推理="难题给强模型… · routing group · Remote magpie" + High、执行权限="danger-full-access"）

**明文/基础地址版（`2057194c…`）**
- 设置页显示"API 基础地址（如 https://domain.name/v1/）"；知识库入口与文案全部移除（改为"搜索与工具"）
- 地址以 `http://100.120.224.94:8080/v1` 保存并回读一致
- 应用内探针（仪表盘 → AI 生成 · 使用自有模型 → 生成布局）：修复前立即 `IOException`，修复后返回内容、诊断 `last_error=none`

**根因（已实证）**：`Cleartext HTTP traffic to 100.120.224.94 not permitted`。宿主 `targetSdkVersion=36` 且无 `usesCleartextTraffic`/`networkSecurityConfig`。网关本身正常（curl 返回 401 预期鉴权错误）；此前观察到的 502 来自本机 Mihomo 系统代理，非网关故障。

---

## 4. 未完成事项（按优先级）

### P0 — 阻塞真实任务闭环，必须先做

#### 4.1 回环风险：禁止向当前助手会话派发任务

在绑定会话 `sess_dsh_6f324ae3d7727ef52eb62aa4`（标题"集成AA并优化工作台界面"）详情页，发现其历史消息包含 lead 上一轮助手输出原文，且其下有一条"执行了 1 条指令"活动行 —— 与"此会话即当前助手对话所在会话"一致。若向其派发，消息会注入本对话。

- 状态：**已停止派发，未发送任何任务、未改动配置**
- 需要：**用户明确确认**目标会话是否安全（是否为非本助手会话）。用户此前选择"发起只读型测试任务"时并不知道这一前提
- 证据：`task-detail-open.xml` 节点 21/22/26

#### 4.2 Contract v3 候选的签名与安装验证

- `TurboIO-AA-v3-signed.apk`（`89253D8A…C1B9`）已签名产出，但**报告 `installed:false`、`runtimeValidated:false`**
- 需补：apksigner verify 记录、设备端 `base.apk` SHA-256 比对、`adb install -r --no-streaming` 返回码、`lastUpdateTime`、`userId=10271` 不变、未卸载未清数据
- 安装涉及华为二级安全提示与**用户本人身份验证**（禁止脚本代填/绕过）
- 本会话环境**无 adb**（PATH 无、`artifacts/tools/android-sdk` 只有 build-tools/platforms，无 platform-tools），需在新会话先解决 adb 定位

#### 4.3 真实任务链路端到端（发送 → 进度 → 结果 → 续聊）

- 未发起真实任务、未验证进度与结果查询、未验证同会话续聊、未验证账号切换后迟到结果丢弃
- 必须先解决 4.1 的会话选择，再执行

### P1 — 已知遗留缺陷（可修复，未修）

#### 4.4 冷启动引导降级：已保存绑定显示为裸 ID

草稿引导期一次目录失败会把已保存值降级为裸 ID（`conn_7NFcgnwVILr2kg`、`rti_NlmB64Derg8WgEKv`、`sess_dsh_6f324ae3d7727ef52eb62aa4`、`dsh:model:…`、`dsh:permission:…`）并残留一条通用错误（"Could not load sessions. Refresh to try again." / "候选加载失败，已保留当前选择。"），需用户手动点「刷新配置」才恢复可读名称。

- 关键：**已保存绑定未被清除或改写**，功能不丢，仅首次体验不一致
- 建议修复：引导失败时自动重试一次，或保留上次成功名称

### P2 — 安全口径待用户确认

#### 4.5 执行权限档位 = `danger-full-access`

该档位允许远端无限制执行。需向用户确认这是否为其本意；如需收敛，在执行权限卡改选更受限档位后重新保存并回读。

### P3 — 未验证但已知存在的限制

- **语音端到端**：明文开关与地址补全走同一 `HttpURLConnection` 栈，但"流式回答 + 镜片显示 + 插话/收尾"未实测。诊断 `requests=0` 表明本轮没有真实 SSE 流式请求发生（`mode=2` 已选，但需眼镜语音触发 ASR→NLP）
- **旋转 / 进程重建下的草稿恢复**：未验证
- **隔离资源 Context 与 Compose 生命周期的完整设备验收**：部分证据已有，仍需完整设备验收
- **约 20 MB 未压缩 DEX 是否采用 R8**：待决定
- **明文开关是全局放宽**：`usesCleartextTraffic=1` 允许该 App 对任意域名用 http，凭据明文传输。仅建议在信任的内网/Tailscale 环境使用；若网关支持 HTTPS，改回 https 即可去掉此开关
- **输入法自动补尾**：会在 URL 末尾自动补 `/` 再补逗号（曾写成 `.../v1/,`）。手填地址后须回读确认

---

## 5. 工作区现状（⚠️ 新会话必须知道）

### 5.1 未提交的源码改动（HEAD `3f3159c` 之后，共 +762/-163）

修改（11 个）：
`CardAI.java`、`CardUI.java`、`ChatPolicy.java`、`DshClient.java`、`DshDirectory.java`、`DshSettings.java`、`EditorialUI.java`、`ToolClient.java`、`TurboAddon.java`、`tests/ChatPolicyTest.java`、`tests/DshClientTest.java`

新增未跟踪源码/测试（13 个 Java + 8 个 Python）：
`AaBridgePolicy.java`、`AaEmbeddedActivity.java`、`AaHostBridge.java`、`AaPayloadLoader.java`、`DshSelectionDraft.java`、`ToolRoundTrace.java`；
测试：`AaBridgePolicyTest.java`、`AaDispatchFlowTest.java`、`DshDraftContractTest.java`、`DshSelectionDraftTest.java`、`DshSessionSelectionTest.java`、`DshSettingsPolicyTest.java`、`ToolRoundTraceTest.java`、`ChatPolicyTest.java`

**这些改动尚未提交，也没有 stash（`git stash list` 为空）。不要丢弃或重置。**

### 5.2 未跟踪的构建/审计资产

`android-addon/build-embedded-windows.ps1`、`android-addon/cleartext_manifest.py`、`android-addon/embed_manifest.py`、`android-addon/package-embedded.py`、`android-addon/docs/EMBEDDED_AA_INTEGRATION.md`、`android-addon/embedded-v3-lead-build2.log.err`

### 5.3 文档（已更新，与当前代码同步）

- `docs/AA_WORKBENCH_UI_MIGRATION.md` — 六卡迁移与冻结契约、验证状态（17:40 最新）
- `docs/AA_UI_SIGN_INSTALL_20261029.md` — 签名安装与真机检查、遗留缺陷、回环风险（14:03）
- `docs/RAYNEO_CLEARTEXT_FIX_20261029.md` — 明文 HTTP 根因与修复验证（16:10）
- `docs/AGENTS_ANYWHERE_EMBEDDING.md` — 嵌入契约 v1 与分层验证（10/26）
- `docs/AA_OAUTH_CALLBACK_FIX_20261027.md`、`docs/AA_WORKBENCH_CRASH_FIX_20261027.md`

> 约定：**旧 OAuth/Context 修复记录按各自产物引用**（contextfix 签名包 `a10aa845…`、oauthfix 签名包 `656ad6d0…`），不得与 `48bd2c96…` / `89253D8A…` 混同。

### 5.4 需清理的杂项

- `%SystemDrive%/` 目录（仓库根下）：Windows 缓存被字面量 `%SystemDrive%` 路径污染产生（3 个 `cversions.2.db` 缓存文件），属垃圾，删除即可
- `Agents-Anywhere/` 是**独立 Git 仓库**（子仓库，未纳入 Turbo-IO 的 git 索引）。当前状态：HEAD `e5c0179d`，有未提交改动（`android/.gitignore`、`android/README.md`、`android/app/build.gradle.kts` 被修改；`android/app/src/embeddedDebug/`、`android/app/src/testEmbeddedDebug/`、`android/scripts/` 未跟踪）。**这是 AA 侧 embed buildType 的实现，不要覆盖或重置**

---

## 6. 新会话建议的第一步

1. 确认 adb 位置（`adb.exe` 不在 PATH，`artifacts/tools/android-sdk` 无 platform-tools）
2. 向用户确认 4.1 的目标会话（**派发前必须确认，否则消息会注入本对话**）与 4.5 的权限档位意图
3. 核验 `TurboIO-AA-v3-signed.apk`（`89253D8A…C1B9`）的签名与设备安装状态，补齐 4.2 证据
4. 在确认安全会话后执行 4.3 真实任务往返
5. 修 4.4 引导降级缺陷（有权限可派子代理）
6. 清理 `%SystemDrive%/`；把源码改动提交到 `android` 分支

---

## 7. 本次交接的已知不确定项

- 原会话 `session-0b1795ed-4587-4c73-be68-25baf025a463` 读取被拒（`session target is outside the caller workspace`），**无法核对 lead 在 v3 轮之后是否还有其他进展**。若新会话能访问该会话，请优先补齐 v3 轮之后的实际进度
- v3 候选是否已安装到设备、`takeover_ui.xml`/`takeover_screen.png` 对应哪一轮，均未在文档中记录（文档最新为 17:40，v3 产物为 18:11–19:00）
- `AA_UI_SIGN_INSTALL_20261029.md` 中 `48bd2c96…` 出现两次，分别标为 `embedded-aa-ui-final`（unsigned `f981333f…`）与 `embedded-aa-ui-affordance`（signed），命名对应关系需在新会话核对
