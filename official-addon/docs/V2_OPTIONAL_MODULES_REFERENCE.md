# Turbo-IO V2 可选模块参考

本文件作为 [V2 图文操作手册](V2_USER_GUIDE.md)的可选模块附录，归纳仓库 README 与对应源码中明确记录的入口、开关、依赖、操作和验收边界。它是静态资料汇编，不代表本次已构建、签名、安装、联网、刷写或在设备上复测。

## 先看平台与固件边界

- **iOS 与 Android 是两套不同集成。** iOS 使用 `official-addon/` 的 Objective-C 动态库和 `official-addon/package.mjs`；Android 使用仓库根目录的 `android-addon/` APK 工具链。这里所说的 Android README 实际路径是 [`android-addon/README.md`](../../android-addon/README.md)，仓库不存在 `official-addon/android-addon/README.md`。不要把 Android APK 步骤、宿主版本或固件门禁套到 iOS，反之亦然。[iOS 总说明](../README.md) · [Android README](../../android-addon/README.md)
- **普通构建不启用固件实验入口，也不自动刷固件。** TNV1、TMU1、TFP1 都要求显式编译门禁及打包目标；Android GUARD-07/TAP1 也有独立保护流程。知识库、本地翻译和 iOS 导航后台生命周期修复本身不要求刷眼镜固件。[iOS build.sh](../build.sh) · [FOCUS build.sh](../focus-edition/build.sh) · [Android README](../../android-addon/README.md) · [导航后台说明](NAVIGATION_BACKGROUND.md)
- **固件包不可混用。** TNV1、TMU1、TWR1、TFP1/FOCUS-04、TAP1-TEST-01 是不同候选及门禁；不得用另一个版本的宏、包参数、授权码或 ZIP 替代。仅 AP 内容有变化不代表 OTA 只写 AP，也不代表可救砖。下载后只按各自 README 的固定发布文件和摘要核验；校验通过不等于已获准安装。[TNV1](../../firmware-research/strix-1.0.4.12/native-navigation/README.md) · [TMU1](../../firmware-research/strix-1.0.4.12/native-navigation/music/README.md) · [TWR1](../../firmware-research/strix-1.0.4.12/native-navigation/weread/README.md) · [FOCUS-04/TFP1 配套说明](../focus-edition/README.md) · [TFP1 精确包校验](../package.mjs)
- **精确配对先于功能预期。** 当前资料明确给出的 iOS 研究组合主要是官方雷鸟 AI iOS 1.0.5（201）与 Strix OS 1.0.4.12；TNV1 还明确限定雷鸟 iO / RayNeo iO。TMU1/TWR1 README 给出固件基线和功能包名，但没有写出精确眼镜型号；FOCUS-04 配套 README 固定 iOS 宿主与 TFP1 固件，但没有给出精确眼镜型号；Android README 固定 Android 官方 App 1.0.5（201），但没有给出 TAP1 的精确眼镜型号。不能据此推断任意 RayNeo 设备可刷。未满足对应 README 的精确宿主、固件和设备条件时，停在只读源码或离线校验阶段。[iOS 版本门槛](../README.md) · [TNV1 适配范围](../../firmware-research/strix-1.0.4.12/native-navigation/README.md) · [FOCUS-04 配套范围](../focus-edition/README.md) · [Android 版本门槛](../../android-addon/README.md)

## Codex 只读知识库参考服务

**平台与入口。** Mac 本机 Node 服务供 iOS V2 手机通过自有 HTTPS 反向代理访问；入口为「TurboIO → 模型 → 知识库与来源 → 连接配置」。服务只监听 `127.0.0.1`，需要使用者自备 TLS 代理/VPN 转发 `/api/turbo-knowledge/` 并保留 Bearer 鉴权。[知识库 README](../knowledge-bridge/README.md) · [服务实现](../knowledge-bridge/server.mjs)

**依赖与配置。** Node.js 22.16+、本机已安装且已登录的 Codex。先用 `node official-addon/knowledge-bridge/server.mjs --init /absolute/new-private-knowledge` 初始化，再在私有目录编辑 `config.json`：指定 Codex 可执行文件绝对路径、专用空 `cwd`、自行授权的 `roots.projects` / `roots.learning` / `roots.wechat`、私有任务目录及初始化生成的令牌文件。初始来源目录为空；不会自动扫描桌面、微信数据库或维护者资料。`roots.wechat` 只读用户自行归档的 JSON/JSONL 副本，不提供微信数据库提取或解密。[配置及归档格式](../knowledge-bridge/README.md)

**操作及成功判据。** 启动 `node official-addon/knowledge-bridge/server.mjs --config /absolute/new-private-knowledge/config.json`；手机填写代理后的 `https://.../api/turbo-knowledge` 和专用令牌，再打开知识库工具。接口层的可观察判据是：`GET /sources` 返回来源状态；`POST /query` 返回 202 与任务编号；随后按编号查询到 `completed`、答案、结果及覆盖提示。`/sources` 不证明 Codex 已登录或模型可用，接口任务完成也不证明答案已显示到眼镜。[API 与任务状态](../knowledge-bridge/README.md) · [Codex 执行器](../knowledge-bridge/turbo-knowledge-codex.mjs)

**边界。** 这是**只读 Codex** 检索参考实现：独立临时只读会话、限定检索工具、最多 4 次检索；默认关闭 shell、联网及其他集成。片段会发送给 Codex 对应模型服务，因此不是全本地推理。它不是多租户安全沙箱，也不适合不可信公网用户。Claude Code、Hermes、WorkBuddy、OpenClaw 的菜单项**不调用本服务，也未在此参考实现中接入各自执行器**，不得宣称它们已由 Codex 执行器代办。当前没有 APNs、事件投递箱、多用户权限或通用写 API；手机取消也不等于撤销 Mac 已接受任务。[安全默认值与未实现项](../knowledge-bridge/README.md) · [V2 Agents / 知识库边界](../README.md)

## iOS 本地翻译与英语离线字幕（OFFLINE-ENGLISH-05）

**平台与入口。** iPhone 前台独立字幕页「TurboIO → 资料 → Apple / Hy-MT2 本地翻译」；处理发生在手机，不在眼镜运行大模型。此路线明确不需要刷固件或服务 Key。[翻译 README](../local-translation/README.md)

**依赖、宏及参数。** Apple Silicon Mac、CMake、Python 3.10+、Node.js、完整 Xcode；资料记录使用 iOS 27 SDK / Swift 6.4、Swift 6 语言模式，最低目标 iOS 26。固定 `llama.cpp` 与 `FluidAudio` 提交分别为 `1e411d8f5a1e23525fa3265dfb4bd76265465397`、`eabcd9e36dab48f1f7180165396d84b9688650e0`；模型下载约 462 MB + 224 MB，连同转换原件需大于 1 GB 空间。`build.py` 参数为 `--llama`、`--fluid`、`--out`，输出目录必须不存在；再以 `TIO_LOCAL_TRANSLATION=1 bash official-addon/build.sh embedded` 显式加入宿主入口。只设置此宏时产物在 `official-addon/build/local-translation/`。打包时 `--translation-module-dir` 与 `--translation-models` 必须成对提供，并传入自己的 `--app`、`--profile`、`--identity`、`--device`、`--out`。[构建脚本](../local-translation/build.py) · [宿主开关](../build.sh) · [资源配对校验](../local-translation/package-resources.mjs) · [宿主及签名限制](../README.md)

**操作及成功判据。** 按翻译 README 在新目录准备两项固定依赖和许可确认后的模型；构建模块及宿主入口；准备 Apple 系统语言包或使用随包校验的 Hy-MT2 模型；由使用者用自己的证书打包并在兼容 iPhone 验收。手机先测短句，再选「仅英文」或翻译引擎、麦克风/HFP 输入及是否镜片同步；主动开始并允许麦克风。手机端应先见英语识别，再见带“预译”标识的译文；镜片同步以本次 SID 成功回执和实际文字显示分别判断。退出/退后台/中断/输入路由变化会停止。[操作步骤](../local-translation/README.md)

**限制与未验收。** Parakeet 是英语离线 ASR，不是中文 ASR；320 ms 是模型块，不是端到端延迟。快速预译会修正，不等于同传；不含眼镜定向原始收音、多语种离线 ASR、后台无限录音、云端兜底或自动保存。私用前身的反馈不能视为本次公开包完整真机验收；文档记录的组合探针曾在 Apple 翻译阶段返回 `CaptionFailure`，AirPods/其他蓝牙输入、温升、真实镜片延迟等仍需单独验收。[限制与验证记录](../local-translation/README.md)

## iOS 导航后台行为修复（无需刷固件）

**入口与依赖。** 这是普通 iOS 手机端导航生命周期修复，不是 TNV1 固件，也不含其 OTA 入口或固件。按导航教程自行准备高德 iOS Key/SDK 并合并签名；真实后台定位需要宿主 `Info.plist` 已有 `UIBackgroundModes` 的 `location`。打包器不会自动添加此能力，也不能整组覆盖宿主原有蓝牙/配件模式。[后台行为说明](NAVIGATION_BACKGROUND.md) · [普通 iOS 构建脚本](../build.sh)

**宏与操作。** 使用高德导航时，普通构建的显式依赖是 `TIO_AMAP_ENABLED=1`，SDK 路径可由 `TIO_AMAP_SDK_ROOT` 指定；该宏本身不启用 TNV1 OTA。前台选实时路线、规划并点开始，按系统提示授予“使用 App 期间”定位。开始时由本实现持有的高德引擎设置 `allowsBackgroundLocationUpdates=YES`、`pausesLocationUpdatesAutomatically=NO`，停止时恢复；没有自动申请“始终”权限。模拟导航只用最多 25 秒系统短时任务，耗尽会停止；不要强退 App。[后台步骤](NAVIGATION_BACKGROUND.md) · [高德构建选项](../build.sh)

**成功判据与边界。** 32 组策略组合及生命周期主机测试覆盖后台保留、权限拒绝、模拟额度、前台恢复、过期回调、用户停止及权限撤销；这些不替代真实 iPhone。资料记录的私用 iPhone Air 初步反馈不等于公开包、所有显示通道或所有机型验收。应分别检查真实路线的锁屏/前后台往返、停止后定位释放、低电量与蓝牙断连恢复；系统仍可能暂停或终止应用。后台手机导航运行也不代表旧字幕通道能后台持续显示。[验证边界](NAVIGATION_BACKGROUND.md)

## iOS TNV1 原生导航与 TDP1 显示测试

**入口、开关与依赖。** 目标是雷鸟 iO / RayNeo iO、Strix OS 1.0.4.12，配套官方 iOS 1.0.5（201）。普通 iOS 构建不启用。`TIO_NATIVE_NAV=1` 要求 `TIO_OTA_RESEARCH_ENABLED=1` 且 `embedded` 模式；导航接入可选高德 `TIO_AMAP_ENABLED=1` 和固定 SDK。TNV1 打包必须显式使用 `--experimental-ota TNV1` 和配套 `--firmware`；打包器验证模块符号及 TNV1 ZIP 哈希，不接受别的目标。[TNV1 发布说明](../../firmware-research/strix-1.0.4.12/native-navigation/README.md) · [构建门禁](../build.sh) · [打包校验](../package.mjs)

**操作与判据。** 先取得并只读核验发布的原字节 `StrixOS-1.0.4.12-TurboNavigation-TNV1-EXPERIMENTAL.zip`（README 所列 SHA-256：`e2a76fdcf0d3d07d766a7329d2d93b32350cb8309b73fc9c73726498fafddecb`），再构建匹配的手机扩展并按精确 `TNV1` 目标打包。设备端体验判据是第九项独立导航页能由手机启动、接收语义路线快照并显示本地绘制指引；第八项 TDP1 仅为 512×128 灰度整图/局部更新测试工具。固件 README 记录了固定版本设备实测和 AP 复现，但不是对新构建、所有路线模式或设备的验收。[发布摘要及验证边界](../../firmware-research/strix-1.0.4.12/native-navigation/README.md)

**未验收边界。** TDP1 不是视频投屏或任意尺寸图片引擎；路线示意图不是地图底图。长时锁屏、全部地区/导航模式、低电量/抢占/断连、断电恢复和各类第三方改版未全部验收。只改 AP 不等于整包 OTA 只写 AP；原厂回滚不保证救砖。不要将 R3、ANIM60 或其他候选与 TNV1 门禁混用。[TNV1 发布说明](../../firmware-research/strix-1.0.4.12/native-navigation/README.md)

## iOS TMU1 网易云音乐

**入口、开关与依赖。** 目标为 Strix OS 1.0.4.12 + 官方 iOS 1.0.5（201）；TMU1 README 未明确眼镜硬件型号，须在评估具体设备时另核对，不从“研究眼镜”推断兼容。`TIO_MUSIC=1` 要求同时设 `TIO_NATIVE_NAV=1` 和 `TIO_OTA_RESEARCH_ENABLED=1`；选用地图导航时再准备高德 SDK 和 `TIO_AMAP_ENABLED=1`，只测音乐可省略。构建输出 `official-addon/build/music/TurboIOPrivateAddon.dylib`。打包需 `--experimental-ota TMU1`、精确 `--firmware`，以及与签名、Bundle ID、设备相符的个人参数；参数/编译符号与 ZIP 不匹配会被拒绝。[TMU1 README](../../firmware-research/strix-1.0.4.12/native-navigation/music/README.md) · [iOS 构建门禁](../build.sh) · [打包器](../package.mjs)

**操作及成功判据。** 只读校验 `StrixOS-1.0.4.12-TurboMusic-TMU1-EXPERIMENTAL.zip`，README 固定 SHA-256 为 `307a0d41aa76b3ed91a8fcc07b09329d76d78a51584c9954c7896d64b1ad9a81`。匹配的手机扩展在用户手动完成该目标的受控升级后，先用本机校验曲，再验证手机搜索/播放控制与镜片同步；可观察项为左侧封面、右侧五行歌词、暂停/继续/切歌和进度对照。资料记录同一音乐实现曾在私用 iPhone Air/研究眼镜获得这些反馈，不代表所有自行签名组合通过。[下载校验与实测范围](../../firmware-research/strix-1.0.4.12/native-navigation/music/README.md)

**限制。** 旋钮轻触可能误切歌（已知未修复）；扫码登录未完成验收；会员、区域、版权限制不绕过；强杀后的自动唤醒、后台、语音抢占、长时断连等未全部验证。文件传输成功不等于镜片渲染成功。Android/HarmonyOS 尚未接入 TMU1 手机播放器，因此不得把这套 iOS 构建或 OTA 参数复用到 Android。[TMU1 边界](../../firmware-research/strix-1.0.4.12/native-navigation/music/README.md)

## TWR1 微信读书手机模块与固件

**平台与集成状态。** TWR1 的原生阅读固件以 Strix OS 1.0.4.12 为基线，README 未明确眼镜硬件型号；公开手机 `phone/` 目录是独立源码模块，不是 V2 默认菜单/构建的一部分，也未接入主仓库 TWR1 OTA 打包门禁。当前 `official-addon/package.mjs` 的固件目标列表不含 TWR1。不得用 TMU1/TNV1 参数凑包；没有完成自己的匹配手机集成时不要为期待阅读同步而刷固件。[TWR1 README](../../firmware-research/strix-1.0.4.12/native-navigation/weread/README.md) · [打包器支持目标](../package.mjs)

**参数、依赖与操作。** 固件只读验证命令为 `python3 firmware-research/strix-1.0.4.12/native-navigation/weread/verify.py <精确 TWR1 ZIP>`；源码复现使用 `build.py --stock <固定原厂 ZIP> --llvm <OHOS LLVM 15.0.4 bin> --out <新目录>`，并需要 macOS/Xcode CLI、Node.js、Python 及仓库指定依赖。手机端集成需要主项目 `ProtocolContext` 与 `../music/phone` 依赖：接入 `TWReaderController()`、使用自己的书架 Key 或导入有权使用的 TXT/EPUB、把事件交给 `TWReaderConsume(event)`，并在 OTA/语音/互斥入口等待暂停确认。完成移植后按 README 的合成短文、四本分页、返回、断线与缓冲回收项验收。[TWR1 构建与手机接口](../../firmware-research/strix-1.0.4.12/native-navigation/weread/README.md)

**成功判据与边界。** 固件 ZIP SHA-256 为 `a7a1e98dd22bd45a87bba5f8c19862d5fa3a5f0d5877ed4330cca8face75575c`，AP SHA-256 为 `66c88f54c12ad129d8d568261e399350201df47e0ce9904cf8440e03e1f90ab4`；校验通过只证明归档与清单符合该发布值，不证明手机已集成。资料确认精确固件曾完成用户升级，但封面全量显示、正文跨章、长时阅读及旧功能回归尚未全部验收；长按旋钮返回书架存在未修复问题。书架/统计 Key 不等于章节全文；不含网页正文、Cookie 或自动取文。[TWR1 验收边界](../../firmware-research/strix-1.0.4.12/native-navigation/weread/README.md)

## FOCUS-04 独立 iOS 集成版与 TFP1

**独立源码与普通版底栏。** FOCUS-04 有自己的 `official-addon/focus-edition/` 源码目录、`focus-edition/build.sh`、资源与产物；该脚本先切换到自身目录，再编译本地 `HomeTabLayout.m`、`HomeTabBridge.m` 等文件。当前普通版底栏源文件是 `official-addon/HomeTabBridge.m`，FOCUS-04 使用 `official-addon/focus-edition/HomeTabBridge.m`；前者的改动不会自动进入后者或改变 FOCUS-04 构建。检查/修改 FOCUS-04 时必须看该目录本身，不能从普通版底栏代码推断 FOCUS-04 已变更，也不能同时加载旧 addon dylib。[FOCUS-04 README](../focus-edition/README.md) · [独立构建脚本](../focus-edition/build.sh) · [普通版底栏源码](../HomeTabBridge.m) · [FOCUS-04 底栏源码](../focus-edition/HomeTabBridge.m)

**目标、宏与参数。** 配套宿主固定为雷鸟 AI iOS 1.0.5（201）；固件基线为 Strix OS 1.0.4.12，候选为 FOCUS-04/TFP1。限定 README 未说明该固件对应的精确眼镜型号，应以专门发布材料确认，不能从宿主版本或固件代号外推。README 示例显式设置 `TIO_AMAP_ENABLED=1`、`TIO_AMAP_SDK_ROOT`、`TIO_OTA_FLASH_ENABLED=1`、`TIO_IMAGE_RX_LAB=1`、`TIO_IMAGE_RX_WIDE=1`、`TIO_DISPLAY_PHONE=1`、`TIO_DISPLAY_FLASH=1`，使用独立 `focus-edition/build.sh embedded com.rayneo.venus.pub`；这些宏受彼此门禁约束，且不是授权刷写。打包用 `official-addon/package.mjs`，明确提供 `--experimental-ota TFP1`、匹配的 `--firmware`、`--bundle`、个人签名/设备参数及 `--amap-sdk-root`。可选本地翻译时另成对提供 `--translation-module-dir` / `--translation-models`。[构建与打包参数](../focus-edition/README.md) · [宏约束](../focus-edition/build.sh) · [TFP1 哈希校验](../package.mjs)

**操作及成功判据。** 先按 `focus-edition/README.md` 核验 `StrixOS-1.0.4.12-TurboFocus-TFP1-FOCUS04-EXPERIMENTAL.zip`，再构建独立目录 dylib，并按 README 参数由使用者本地打包。TFP1 哈希由打包源码固定校验：`ad5054e3d7bda90e94d293bea882bd8dd5a125bcc8f42c13a59b2e149313c9e3`；构建宏不等于刷写授权。功能验收按 README 核对首页、四张功能卡、底栏、音乐/阅读/新闻/模型/TTS/导航/诊断及番茄入口；公开版整包没有再次安装到用户设备做回归。[FOCUS-04 操作与边界](../focus-edition/README.md) · [TFP1 精确哈希校验](../package.mjs)

**边界。** 不和 TWR1、TMU1、TNV1 的固件包或手机门禁互换。默认 Key/Cookie/个人配置为空。功能模拟回归不等于多设备、长时续航、所有休眠/提醒条件或公开整包真机验收。[FOCUS-04 限制](../focus-edition/README.md) · [TFP1 配对验证](../package.mjs)

## Android V2 插件与 TAP1-TEST-01

**平台与入口。** Android 方案独立在仓库根目录 `android-addon/`，适配官方 Android App 1.0.5（201），不是 iOS 的 `.dylib`/签名合并流程。README 说明公开 APK 已包含扩展；APK 的应用签名不同，不能保证覆盖安装。音乐、阅读、导航等能力仍依赖各自匹配设备固件与使用者配置。[Android README](../../android-addon/README.md) · [主机版本校验](../../android-addon/package.mjs)

**依赖与参数。** 源码构建需要 JDK、Node、Python 3、apktool、Android SDK platform 36 / build-tools 36.0.0、C 编译器及 README 指定的 Python SDK 依赖；输入 APK 必须是 1.0.5（201），README 给出输入 SHA-256 `770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3`。流程使用 `ANDROID_SDK_ROOT`、可选 `TURBO_SDK_PYTHON`，先由 apktool 解包，再运行 `android-addon/build.sh`、`android-addon/package.mjs <官方 APK>`。生成物仍需使用自己的 `zipalign`/`apksigner` 签名；签名冲突前先自行备份数据。`package.mjs` 的返回状态标明 `installed:false`、`nonRootValidated:false`，所以打包报告不是设备验收。[Android 构建说明](../../android-addon/README.md) · [APK 打包实现](../../android-addon/package.mjs)

**TAP1/门禁路线及成功判据。** README 另列 TAP1-TEST-01 固件候选、GUARD-07 OTA 流程；它不是普通 APK 安装的必然步骤。若使用者另行研究该 OTA，只能按 Android 文档引用的精确候选名与校验流程，不能用 iOS 的 TNV1/TMU1/TFP1 门禁。GUARD-07 更新后必须自然回首页、完成只读回查和显示 TEST 检查，确认 `LOCKED / 授权0 / 结果保护未开始` 后才可测日常功能；状态不明或更新未结束不得释放保护。Android README 本身没有给出 TAP1 对应的精确眼镜型号，需先以其专门安装文档和固件范围材料核对，不能从 Fold3 / Android 15 的手机反馈推断眼镜兼容性。[Android README](../../android-addon/README.md) · [源码 OTA 路由范围](../../android-addon/official-ota-source.mjs) · [GUARD-07 保护状态实现](../../android-addon/package-official-ota-preparation.py)

**验收边界。** 文档记录 Fold3 / Android 15 对音乐、阅读测试文本、应用示例、导航和番茄的分别验收，并明确原厂天气、云端服务、长时后台、物理按键全覆盖、故障恢复未全量通过。用户实测刷写成功的是 GUARD-07 私用构建；公开 APK 删除网页正文适配和专属字幕地址，重新编译不等于重新真机刷写。公开 Android 路线不包含微信读书网页正文或 Cookie 入口。[Android 实测与不包含项](../../android-addon/README.md)

## 本次资料核查范围

本文的结论和引用依据限定为本次整理范围内的知识库、本地翻译、FOCUS-04、Android、TNV1/TMU1/TWR1 与导航后台 README，以及为核对宏、参数和固件摘要而追溯的对应源码；未读取任何 `build/` 目录内文件或私密配置。本次未执行命令示例、自动化测试、构建、签名、安装、联网下载或固件操作。关于限定材料没有明确给出的设备型号、行为或验收，不在此作推断。
