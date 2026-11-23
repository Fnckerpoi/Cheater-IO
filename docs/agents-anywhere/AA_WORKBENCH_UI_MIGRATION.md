# AA 工作台 UI 与配置迁移

## 已确认的产品决策

- 六张配置卡（电脑设备、项目、DSH 运行时、Session、模型与推理、执行权限）迁至 AA 工作台首页会话列表上方的可折叠区域，以便在同一工作台配置并在浏览时收起。
- 项目卡片和候选项不展示路径；这只影响展示，不改变精确项目标识或实际绑定。
- “电脑端 DSH”更名为“Agent-Anywhere设置”。原页不再重复六张卡，保留账户登录/退出、账户状态及现有任务状态、未确认提交的核对与恢复入口，避免丢失恢复能力。
- 视觉参考 Turbo IO 深色背景、浅色文字、黄绿色强调和圆角卡片。
- 选择只改变草稿，显式“保存并启用”后才生效。保持既有账号身份、级联选择、会话归属、原子保存/读回和未知送达保护；不以页面浏览或刷新自动更换绑定。

## 职责与验收

- 宿主任务 task-5：提供配置候选/草稿和提交桥，迁移旧设置页入口，保持认证及任务状态保护；由宿主成员负责。
- AA 任务 task-6：通过冻结的宿主桥实现工作台 UI、折叠六卡和保存，保持现有会话与任务功能；由 AA 成员负责。
- 宿主拥有每个工作台的单一草稿；冻结操作为 `selection.draft.open/list/choose/save/close`。快照的 `selected[level]` 是包含 id、label、subtitle、available 的 Option Bundle；候选字段为 `options`，成功保存返回 `saved`。请求回显草稿 ID、请求版本、账号世代、级别和请求号，并核验完整父级。显式新建 Session 使用现有 `@new-session` 标识。
- 两侧各自在自己的目录实现，不跨模块同时写入；实际失败不放宽原子保存、身份代次和未知送达守卫。
- 验收分别记录源码复审、级联/状态守卫/路径展示聚焦测试、独立构建与同包联编；签名安装、真机 UI、真实任务往返另行取证，不以离线结果替代。


## 六卡草稿桥接口（已冻结）

- 宿主拥有每个工作台的单一草稿，操作为 `selection.draft.open/list/choose/save/close`。
- 快照 `selected[level]` 为含 id/label/subtitle/available 的 Option Bundle；候选字段 `options`；成功保存返回 `saved`。
- 每个响应回显 draftId、requestDraftRevision、authGeneration、level、requestId 与父级，客户端据此丢弃过期回包；所有 draft 错误（含 HOST_CLOSED、WORKER_REQUIRED）都带原请求归属。
- 级联由宿主唯一负责：电脑变化清其余五级，项目变化仅清 Session，运行时变化清 Session/模型/权限；同 ID 重复选择幂等。
- 保存仅在 DSH 提交锁内执行，原子 commit 后逐字段读回；存储失败或未知送达一律拒绝且不清绑定。
- 显式新建会话使用既有 `@new-session` 标识，保存后写入空锚点；列表缺失或加载失败保留原绑定并标记未验证。
- 项目候选与已选卡片只展示名称，`subtitle` 恒空，路径与可能为路径的 ID 不回显；模型与执行权限保留 `subtitle` 以区分推理档位与权限说明。
- 变更请求进行中禁止关闭候选弹窗；宿主拒绝过期操作后提供显式“重新加载配置”恢复路径，旧草稿由后台清理精确关闭，不影响新面板草稿。

## 本轮验证状态

- 载荷来源：`aa-embedded-payload.apk` 20,439,773 字节，SHA-256 `784caa7583db35046802e3d58b150fff0d8ebe8e6697f3670b0013f01f0e2ce6`；来源门禁 exit 0（standalone native=28、source native=28、payload native=0、1476 成员哈希一致、28 项允许移除、严格 sidecar）。
- AA 聚焦测试：`ConfigurationStateTest` 20 项、`WorkbenchPolicyTest` 9 项，JUnit XML failures=0 errors=0。
- 宿主独立回归（对应上述候选 f981333… 的那一轮）：新账户环境下生产 Java 编译与 8 个测试类共 **576** 项通过，各进程 exit 0（DshClient 194、DshDirectory 30、DshSessionSelection 52、DshSettingsPolicy 18、AaBridgePolicy 47、AaDispatchFlow 25、DshSelectionDraft 120、DshDraftContract 90）。此前记录写作 676 为手算笔误，以逐类日志为准。
- Contract v3（409 解锁、自动 takeover、首页三项保存、selection.catalog、forceBind）宿主回归共 **669** 项：DshClient 240、DshDirectory 30、DshSessionSelection 52、DshSettingsPolicy 18、AaBridgePolicy 62、AaDispatchFlow 25、DshSelectionDraft 152、DshDraftContract 90，各 exit 0；lead 已独立复算合计。该轮复审另发现 commit 最终校验未沿用 forceBind 语义的半提交风险，已退回宿主成员小修（见下）。
- 同包候选：`artifacts/embedded-aa-ui-final-20261029/TurboIO-AA-embedded-aligned-unsigned.apk`，446,374,467 字节，SHA-256 `f981333f2405fc78a0dda201217034c18369f53d3a06bafd234dc16e682d5eca`。
- 装配报告：同目录 `assembly-report.json`；独立审计及来源门禁由 lead 复跑 exit 0。3976 个宿主成员字节不变，变化仅 Manifest/classes4.dex，新增仅载荷与 sidecar，移除仅旧签名。保留 Context callbacks 与 OAuth 回调声明。包名 `com.rayneo.venus.pub`，1.0.5/201，与 1.0.7 研究版独立。
- 本轮修复构建 runner：在脚本正文解析默认底座路径，以真实 Process.ExitCode 验证执行；正向成功、非零退出与缺少程序拒绝、最终 zipalign 检查均通过。
- 载荷 sidecar 文件 SHA-256 为 `5ff26090a3f802cb185a059e620e1ba69171527914d05d98912b0fb7e5535aeb`，内部 payload hash 与本轮一致。
- 审查发现的模型推理 subtitle 丢失、选择期间关闭造成失步已修复并经独立源码复审关闭；标题断言冲突也已修正。签名、安装、实际 Bundle/Parcelable 交互、真机 UI 与真实任务链路仍未验证。
- 早前 `aeab3367…` 载荷因未含 subtitle 与失步恢复修复而作废。

## 当前状态

开发、聚焦回归、独立源码复审及同包候选构建已完成。最新候选与报告位于 `artifacts/embedded-aa-ui-final-20261029/`；旧失败目录与作废载荷记录保留。尚未签名安装本轮 UI 版本，不能据此认定实际六卡目录交互、屏幕布局或真实任务往返已通过。
