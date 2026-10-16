# NewsHub 交付基线迁移：1.0.5(201) + Strix 1.0.4.12 → 1.0.6(218) + Strix 1.0.5.5 / TMIG1

生成：2026-10-16 · HEAD `0510d1c`
决策来源：本次会话中你明确选择「把 NewsHub 交付目标切到 1.0.6(218)+1.0.5.5」，撤销此前「继续用 1.0.4.12 + 1.0.5(201)，不迁移」的约束。
配套证据：`IOS_106_INPUT_VALIDATION.md`（真机输入已验证）、`IOS_106_CONFLICT_AUDIT.md`（冲突矩阵）、`BUILD_ARTIFACTS_AUDIT.md`（产物分级清理）。
本文只是方案，未执行任何构建、签名、安装或刷机。

## 1. 迁移前置事实表

| 项 | A：旧基线（已实刷） | B：新基线（本次选定） |
| --- | --- | --- |
| 手机宿主 | 官方 iOS 1.0.5(201)，Bundle `com.rayneo.venus.pub`，Runner UUID `748fd301da603095a2449bd0558faca5` | 官方 iOS 1.0.6(218)，同 Bundle，Runner UUID `98a03b57e8163ba19778e55fabe8d762`（**本机已实测**） |
| 宿主输入文件 | `official-addon/build/manuscript-lab-input-20261015-original105/RayNeo_AI_1.0.5_201_decrypted_original.ipa`（353,131,781 B，仍在盘上） | `official-addon/.build/ios-inputs/RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa`（353,545,727 B / SHA `f20d23c2…`，已下载校验） |
| App.framework 允许哈希 | `50d91ba920f0cc9fa93255f5d0bb647fbc50a4b2a0e34b37c2696fbbdeda0845`（1.0.6 签名保留版） | `57d064491c46a21420fd73c969ebfb390707556861526e62db58633514caf884`（去签名版，**本机已实测命中**） |
| 眼镜固件 | Strix OS 1.0.4.12（AP 9,558,216 B，预算 9,600,000） | Strix OS 1.0.5.5 / TMIG1：ZIP 8,824,204 B / SHA `f1d3616c…`（**本机已下载校验**）；AP 7,373,064 B / SHA `30522ac7…`；15 负载 + 1 清单（新增 `bt_controller.bin`）；自定义只改 `nuttx_ap.bin`，其余 14 负载逐字节不变 |
| 打包器 | 主目录 `official-addon/package.mjs`（FOCUS-04 / TLC1 门禁）——上游明确「不要使用主目录旧 package.mjs 打包本版」 | `official-addon/tmig1-edition/package.mjs` + `patch-ios106-route.mjs` + `host-compatibility.mjs`；`inputPins` 只允许上面两枚哈希；固件/OTA 校验未放宽 |
| NewsHub 编译 recipe | `final-build/build_modules.py`：45 主单元 + 7 NewsHub 单元、118 冻结输入；已定稿运行 `final-build/build/final-task73-20261015-0856/`（dylib SHA `cf4dfcb2…` / `6f41443b…`，receipt `2b7e34bd…`） | 同一 recipe 尚未在 B 基线上跑过；它本身不读宿主字节，但产物要能被 B 的打包器接受 |

## 2. 有序迁移步骤

### 步骤 1 —— 换宿主输入（前置：无）
- 目的：让所有后续验证以已验证的 1.0.6 去签名宿主为输入。
- 位置：输入已在 `official-addon/.build/ios-inputs/unpacked/Payload/Runner.app`（见验证文档第 2 节）。
- **必须改的代码**：两处硬编码了旧 1.0.5 IPA 路径的脚本
  - `official-addon/manuscript-lab/stage-b-packaging/test_packaging.py:71` → `ROOT.parent/'build/manuscript-lab-input-20261015-original105/RayNeo_AI_1.0.5_201_decrypted_original.ipa'`
  - `official-addon/manuscript-lab/image-attestation/test-images.sh:12` → 同一旧路径
- 完成判据：这两处指向 1.0.6 输入（或改为参数传入），且脚本仍能跑通自身断言。
- 退回：不改脚本，改为新建 `build/manuscript-lab-input-20261016-original106/` 并放同名文件；语义上等价但会把「105」命名继续带进证据。

### 步骤 2 —— 解掉硬阻塞 C-1（前置：步骤 1）
- 目的：让 NewsHub 插件通过 TMIG1 配对门禁。
- 事实：`tmig1-edition/package.mjs:24` 要求插件导出 `_CEPhoneSendCards` 与 `_TFFocusConsume`；实测 `nm -gU final-build/build/final-task73-20261015-0856/TurboIOPrivateAddon.dylib` → 225 个导出、**0 命中**；这两个符号只定义在 `tmig1-edition/FocusBridge.m:70` 与 `tmig1-edition/context-english-v1/ContextEnglishBridge.m:181`。
- 两条可选路线（需要你选一条，本文不替你拍板）：
  - **2a 合并源码线**：把 `tmig1-edition` 的 `FocusBridge.m` + `context-english-v1/*.m` 加入 `build_modules.py` 的编译单元集合（45 → 45+n），同时保留其私有 domain 断言与 `.preview` 排除。
  - **2b 放弃 TMIG1 实验 OTA 声明**：NewsHub 包不使用 `--experimental-ota TMIG1`，只用普通 1.0.6 打包路径。代价：拿不到「诊断与实验固件 → TMIG1」的 15 分钟只下载验收，也就不算完成 B 基线的固件侧。
- 完成判据：`nm -gU` 命中两符号（2a）或明确不需要（2b），并且 `node --test official-addon/tmig1-edition/package.test.mjs` 全绿。
- 退回：保留 0856 作为 A 基线历史产物，不再前推。

### 步骤 3 —— 重绑编译 recipe（前置：步骤 2）
- 位置：`official-addon/final-build/build_modules.py`（`:35` 身份串 `com.duriea.turboio.manuscriptlab105.<run>`；`:143-146` 目标宏与 `-DTIO_LOCAL_HTTP_ENABLED`；`:162` `-DTIO_NEWS_EXPECTED_ADDON_UUID` 绑定实际插件 UUID；`:155-156` 断言存储服务字面量必须存在于产物字节中）。
- 要做：重跑冻结清单（118 输入）→ 新 `source-manifest.json` / `recipe-snapshot.py`；决定 C-4 的身份串是否从 `manuscriptlab105` 换成 `…106`（换名会改变产物字节，旧哈希全部转为历史）。
- 保持不变的断言（不可放宽）：`no_signature_commands`、`preview_fixture_excluded`、`legacy_exact_domains_absent`、`new_production_domains_present`、`http_any_ip_optin_marker_present`。
- 完成判据：`python3 official-addon/final-build/test_recipe.py` 通过，生成新 `build-receipt.json`，且 `CURRENT-STATUS.json` 的 `native_units/hub_units/frozen_files` 与实际一致。
- 退回：不换身份串，只重跑 recipe；证据链保持连续但命名停留在 105。

### 步骤 4 —— 打包与门禁（前置：步骤 3）
- 命令骨架（只用 tmig1-edition 的打包器）：
```sh
node official-addon/tmig1-edition/package.mjs \
  --app /absolute/…/1.0.6-unsigned-copy/Payload/Runner.app \
  --addon /absolute/…/TurboIOPrivateAddon.dylib \
  --profile /absolute/…/TurboIOPrivateAddon.plist \
  --identity <profile 内证书指纹> --device <profile 内设备 UDID> \
  --experimental-ota TMIG1 --firmware /absolute/…/StrixOS-1.0.5.5-TurboIO-TMIG1-CANDIDATE-NOT-APPROVED.zip \
  --out /absolute/…/packaged-106
```
- 会被拒绝的情形（已读源码确认）：宿主不是 1.0.6/218 或带 `TIOExperimentalOTAQueryRouting` 键；App.framework 哈希不在两枚 pin 内；固件不是 8,824,204 B / `f1d3616c…`；插件缺两个配对符号（`tmig1_addon_required`）；含 `OBJC_CLASS_$_AMapNaviWalkManager` 却没给 map 资源/`--amap-sdk-root`；输出目录已存在或位于源内；源里含 7 个私有 bootstrap 文件之一；profile 的证书/设备/有效期/`application-identifier` 不匹配。
- 完成判据：打包成功且 `report` 中 `changes.length===2`、`objectHeadersChanged===false`、`deviceIO===false`。
- 退回：不声明 `--experimental-ota`，走普通打包（等同步骤 2b）。

### 步骤 5 —— 签名与安装（由你完成）
- 仓库不签名、不代签、不提供合并 IPA；`tmig1-edition/README.md` 与 `firmware-research/strix-1.0.5.5/README.md:68` 都明确这一点。旧安装器不能强刷。
- 完成判据：应用在你机器上启动，`设置 → 诊断与实验固件 → TMIG1` 候选校验通过（若走 2a）。

### 步骤 6 —— 真机验收（NewsHub 的完成判据）
按你已确认的验收标准，逐项都要成立，不能以「DB 写入成功」或 native 回调代替：
1. 隔离测试副本/稿库（无私人稿件）+ 公开样稿先跑一轮；正式环境前先备份、可回滚。
2. 每来源独立成稿；支持 MCP/RSS 并保留 TinyFish；自定义每日时间与 10 分钟重试。
3. 无页面跳转、无需逐篇点击，进入官方列表。
4. 官方列表可见 → 可打开正文 → 重启后仍持久 → 多源定时重试成功。
- 完成判据：以上 4 项在同一次真机运行中全部成立，并有可复核记录。

## 3. 可沿用的证据 vs 必须重做的证据

| 可直接沿用 | 必须重做 |
| --- | --- |
| 1.0.6 宿主身份（IPA 哈希、2705 成员、95 个未加密 Mach-O、Runner UUID、App.framework 哈希）——本次已实测 | 插件/中枢 dylib 的导出符号与哈希（0856 的 `cf4dfcb2…`、`6f41443b…` 属 A 基线） |
| TMIG1 固件身份（8,824,204 B / `f1d3616c…`）——本次已实测 | recipe 的 118 输入冻结清单与新 receipt |
| `validateResearchPair` 的门禁逻辑（已用真实固件跑通其正/反例） | 打包产物本身（尚无） |
| 1.0.5 基线上已实刷的导航/字幕/MapKit 历史结论（作为设计参考） | 这些结论在新基线上的复现（矩阵 V-1…V-3） |
| NewsHub 的 headless 契约与 UI 契约文档 | `official_save_verified`、`runtime_isolation_proven`、`network_services_executed`（当前均为 false） |

## 4. 当前阻塞项

1. **C-1 未解**：NewsHub 定稿插件不含 TMIG1 配对符号 → 步骤 2 的 2a/2b 必须先选一条。
2. **上游测试与上游发布物不一致**：`package.test.mjs:69` 硬编码输出哈希 `532bdfc1…` 与真实去签名 IPA 的确定输出 `76bfa547…` 不符（见验证文档第 7 节）。在该断言被修正前，「带真实输入全绿」这一判据无法达成。
3. **官方稿库契约不可从仓库证明**：你已确认手上只有当前 Turbo-IO 仓库，没有另行提供的官方 Dart 工程或厂商稿库 SDK；直写官方 DB 的可行性只能在真机隔离副本上取证。
4. **NewsHub 运行时仍未完成**：`CURRENT-STATUS.json` 记录 `runtime_isolation_proven:false`、`network_services_executed:false`、`official_save_verified:false`；`final-build/README.md` 记录 Queue headless/arm 仍为 false、无生产保存桥接。
5. **本地独有导航实现不在 `tmig1-edition` 源码树中**（16 个文件逐项确认缺失）；若交付包要同时含导航与 NewsHub，需先决定合并方向（矩阵 C-2 / V-2）。
6. 工作区仍有未提交改动（8 个 `.m` 修改 + 约 600 个未跟踪文件），迁移改动应在其上分层提交。

## 5. 不可跳过的风险

- 编译通过 ≠ 真机验收；哈希匹配 ≠ 行为正确。
- 去签名 IPA 不是可安装包：它未签名、未安装，只作为打包输入与身份证据。
- 旧补丁前置字节在新版同偏移 0/36 匹配、菜单对象 220→252、头控状态 64→104：任何固件侧移植都不能整包平移地址或只改版本字符串。
- 1.0.5.5 的自定义只允许改 `nuttx_ap.bin`；原样官方 ZIP 不能当自定义包刷入 TMIG1 固定门禁；旧 Android GUARD-07、旧 FOCUS-04 / TLC1 门禁不能直接刷 TMIG1。
- 换存储身份串会让既有全部哈希证据变成历史；不换则命名与基线不一致。这是一个需要明确选择的决定，不是技术细节。

## 6. 与既有文档的不一致点

| 既有文档 | 不一致 |
| --- | --- |
| `final-build/README.md`、`CURRENT-STATUS.json`（A 基线定稿，2026-10-15） | 其输入与产物属旧基线；迁移后需新 recipe 运行，否则「已定稿」仅对 A 成立 |
| `docs/NEWSHUB_FINAL_IPA_OBJECTIVE.md` | 其「有限 GO」建立在 1.0.5 输入上；新基线下该 GO 需重新授予 |
| `docs/NEWSHUB_FINAL_IPA_BUILD_INPUTS.md`、`docs/NEWSHUB_DIRECT_DB_PLAN.md` | 硬引用 `manuscript-lab-input-20261015-original105`；迁移后这些引用变成历史输入说明 |
| `official-apps/ios/1.0.5/README.md`（上游） | 明确 1.0.5(201) 为历史输入、不再重做；与本迁移方向一致，但意味着旧基线证据不再向前推进 |

## 7. 只能标为「未知」的点

- 上游测试常量 `532bdfc1…` 究竟对应哪一份输入（推测是其签名保留版 `50d91ba9…` 的输出，本机没有该文件可验证）。
- 1.0.6 宿主上 NewsHub 的加载与行为（未签名、未安装）。
- 官方稿库直写在新基线上的可行性与持久性（未执行）。
- 若启用 MapKit/AMap，1.0.5.5 的 AP 预算与资源侧是否能容纳（未测量）。
