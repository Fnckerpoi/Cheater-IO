# 本地功能线 × 1.0.6(218) / TMIG1 / Strix 1.0.5.5 冲突与兼容审计

生成：2026-10-16 · HEAD `0510d1c`（上游 `0aefa12` 已合并）
基线：A = iOS 1.0.5(201) + Strix 1.0.4.12（既有已验证组合）；B = iOS 1.0.6(218) + Strix 1.0.5.5 / TMIG1（本次用户选定的新交付基线）

## 结论摘要

- **文本冲突：0。** `git merge-tree --write-tree HEAD upstream/main` 产出干净树 `86d0b38d…`，实际 `git merge --no-ff` 无冲突；两侧唯一重叠文件是 `official-addon/README.md` 的一行，已自动合并。工作区未提交的 NewsHub 改动未被合并触及（合并前后 `git status` 的 8 个修改文件不变）。
- **语义/构建冲突：4 条**（C-1、C-2、C-3、C-6），其中 **C-1 是硬阻塞**：已定稿的 NewsHub 产物缺少 TMIG1 打包门禁要求的两个配对符号。
- **仅缺少验证：5 条**（V-1…V-5）。
- **明确无冲突：3 条**（N-1…N-3），包括最容易因名字相似被误判的「route」。
- 计数：矩阵 12 行 = 4 语义冲突 + 5 待验证 + 3 无冲突。

## 冲突矩阵

| # | 功能 | 涉及文件与行号 | A 基线状态 | B 基线状态 | 类型 | 需要重做的验证 |
| --- | --- | --- | --- | --- | --- | --- |
| C-1 | NewsHub 生产插件 × TMIG1 打包门禁 | `official-addon/tmig1-edition/package.mjs:24`（`validateResearchPair` 要求符号 `_CEPhoneSendCards`、`_TFFocusConsume`）；实测 `nm -gU official-addon/final-build/build/final-task73-20261015-0856/TurboIOPrivateAddon.dylib` → 225 个导出符号、**0 个命中**；符号定义只存在于 `official-addon/tmig1-edition/FocusBridge.m:70`、`official-addon/tmig1-edition/context-english-v1/ContextEnglishBridge.m:181` | 不适用（1.0.5 走 `official-addon/package.mjs` 的 FOCUS-04/TLC1 门禁） | **失败**：`throw Error('tmig1_addon_required')`，无法用 `--experimental-ota TMIG1` 打包 | 语义/构建 | 把 `tmig1-edition` 的 FocusBridge + context-english-v1 纳入 NewsHub 编译单元，或明确 NewsHub 包不声明 TMIG1 实验 OTA；随后重跑 `package.test.mjs` 与打包 |
| C-2 | 两套互斥的编译配置：导航/MapKit 与 NewsHub 生产 | `official-addon/final-build/build_modules.py:144` 显式 `-DTIO_AMAP_ENABLED=0 -DTIO_MAPKIT_ENABLED=0 -DTIO_MAPKIT_HUD_ENABLED=0 -DTIO_OTA_RESEARCH_ENABLED=0 -DTIO_NATIVE_NAV=0`；而 `package.mjs:51` 规定含 `OBJC_CLASS_$_AMapNaviWalkManager` 的插件必须提供 map 资源与 `--amap-sdk-root` | 1.0.5 线上包同时含导航与地图（R5/R6/R7 已实刷） | 现有 NewsHub 定稿包**不含**导航/地图；若开启则需 `build/amap-sdk` 资源与 AP 侧配套 | 语义/构建 | 决定交付包是否同时含导航；若含，需重新绑定 amap 资源并复核 1.0.5.5 的 AP 预算（见 C-3） |
| C-3 | 固件侧对象布局与补丁前置字节 | `firmware-research/strix-1.0.5.5/README.md:39,42`（旧版 36 个补丁前置字节在新版同偏移 **0/36 匹配**；原厂菜单对象 220→252 字节、扩展后 284；系统头控状态 64→104 字节、扩展后 136）；`README.md:44`（新增 `bt_controller.bin` 后为 **15 负载 + 1 清单**） | 1.0.4.12 的 AP 补丁已实刷（AP 9,558,216 B < 9,600,000 预算） | 1.0.5.5 的 AP 7,373,064 B、扩展模块 164,416 B、预算余量 2,226,936 B；自定义只改 `nuttx_ap.bin`，其余 14 负载逐字节不变 | 语义/构建 | 任何固件侧改动都要在新 AP 上重做偏移/结构复核，不能整包平移地址；NewsHub 本身是 iOS 侧，不触碰 AP（见「无冲突」N-3） |
| C-4 | 存储稿库身份里的 `105` 命名 | `official-addon/final-build/build_modules.py:35` `'com.duriea.turboio.manuscriptlab105.'+run`，并被 `:155-156` 断言必须以内联字节存在于产物中 | 与 1.0.5 输入、`build/manuscript-lab-input-20261015-original105/` 一致 | 身份字符串与宿主版本无耦合，但命名指向旧基线；改名会改变产物字节 → 现有全部哈希证据变为历史 | 语义（非功能） | 决定：保留旧身份串（沿用证据）或换 `106` 身份（重做全部哈希/receipt） |
| V-1 | 任意公网 IP 的 HTTP 模型入口 | `official-addon/EndpointPolicy.h`（`TIO_LOCAL_HTTP_ENABLED` 编译期开关；仅接受 IP 字面量 host、路径必须以 `/chat/completions` 结尾、拒绝 query/fragment/ userinfo/非规范拼写）；`build_modules.py:146` `-DTIO_LOCAL_HTTP_ENABLED=1` 仅在 `--allow-http` 时 | 已在 1.0.5 上编译并本地测试（`docs/HTTP_NAVIGATION.md` 有构建与测试日志） | 打包器不 gate 它；`package.mjs` 的 `entitlementsFor` 只保留 profile 已授予的权限，不改网络类权限 | 仅缺少验证 | 在 1.0.6 宿主上重跑 `LocalHTTPFlowTests.m`/`LocalHTTPEndpointTests.m` 与真机模型调用（含 Tailscale 入口） |
| V-2 | route-scoped 导航生命周期与中性字幕 | 本地独有：`NavigationRouteSession.*`、`NavigationSession.*`、`NavigationSubtitleHUD.*`、`NavigationMapEntry/BeginRoute/StopPresentation/AutoDisplay/AutoPlan/MapLocation.inc` | R5/R6/R7 已在 1.0.5 + 1.0.4.12 上编译、打包、实刷 | 这些文件**不在** `official-addon/tmig1-edition/` 中（逐项 `-f` 检查 16 个本地独有文件，tmig1 侧全部缺失）；`tmig1-edition` 另有一套 NavigationSubtitleHUD/NavigationTeleHUD | 仅缺少验证 | 决定合并方向（把本地独有实现迁入 tmig1-edition，或反之），再在 1.0.6 上重编译 + 重跑 `navigation-r{5,6,7}-*` 那套回归 |
| V-3 | MapKit 导航与 HUD | `official-addon/docs/MAPKIT_NAVIGATION.md`、`mapkit-amap-http-hud` 构建证据；`package.mjs:51` 的 AMap 资源强制条件 | 已构建、已实刷 | 未验证 | 仅缺少验证 | 与 C-2 一并决定；若保留需重绑 `--amap-sdk-root` 与 1.0.5.5 的 AP 侧 |
| V-4 | 官方稿库直写与入库验收 | `MEMORY` 已允许直写官方 DB；`docs/NEWSHUB_*` 系列 | 未完成（`CURRENT-STATUS.json`：`official_save_verified:false`、`network_services_executed:false`、`runtime_isolation_proven:false`） | 同样未完成；与宿主版本无关，但必须在 B 基线上做 | 仅缺少验证 | 隔离副本 → 直写 → 官方列表可见/可打开/重启持久/多源定时重试 |
| V-5 | 已定稿产物在新基线上的可复现性 | `final-build/CURRENT-STATUS.json`（`native_units:45, hub_units:7, frozen_files:118`）；`final-build/build/final-task73-20261015-0856/` | 已复现（receipt `2b7e34bd…`、source manifest `bc97eea7…`） | 该 recipe 的输入是 1.0.5 时代的冻结源码，未在新基线重跑 | 仅缺少验证 | 若采用 B 基线，必须重跑 recipe 与 `test_recipe.py` 并生成新 receipt |
| N-1 | 「route」一词的两处含义 | 上游 `patch-ios106-route.mjs` / `route-hash.mjs:9` 的 route = 宿主 Dart 二进制里两条**升级查询字符串**（实测 offset 41185344 / 32098976）；本地的 route = 导航路线会话生命周期 | 无交叉 | 无交叉：`grep -r 'signApi\|xrlauncher' official-addon --include='*.m' --include='*.h' --include='*.inc'` → **0 个文件** | 无冲突 | 无需验证 |
| N-2 | 宿主身份矩阵 | `tmig1-edition/host-compatibility.mjs:2-6` 已登记 `1.0.6/218/98a03b57…` | 1.0.5/201/748fd301… 也在矩阵内 | **已实测命中**（见 `IOS_106_INPUT_VALIDATION.md`） | 无冲突 | 无 |
| N-3 | NewsHub 对固件 AP 的影响 | NewsHub 全部为 iOS 侧 `.m` 单元（`build_modules.py`），不产生 `nuttx_ap.bin` 改动 | 无 | 无 | 无冲突 | 若将来加眼镜侧组件，才进入 C-3 的预算与结构复核 |

## 已存在的编译告警（与本次迁移无关，但记录以免误判为新版引入）

`final-build/build/final-task73-20261015-0856/native-compile.log` 中的 deprecation 告警共 4 类，来自 iOS SDK 而非宿主版本：`-[UIWindow windows]`（iOS 15 起弃用，2 处）、`-initWithDocumentTypes:inMode:` 与 `UIDocumentPickerModeImport`（iOS 14 起弃用）、`-undefined error is deprecated`。涉及 `Addon.m`、`HomeTabBridge.m`、`NavigationPlacePicker.m`、`RecordingExportsUI.m`、经 `NavigationUI.m` 包含的头文件。

## 本次未验证的内容

没有真机、没有签名、没有安装、没有刷机、没有网络调用、没有官方稿库读写。上表中「A 基线状态」为已实刷的条目引用的是历史文档与其日志，不是本次重跑。凡标注「仅缺少验证」的行，都不应被读成「兼容」。
