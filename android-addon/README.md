# Cheater-IO · Android 宿主扩展

本分支维护 Android 宿主扩展、SCADA 和 TMG 系列固件实验。打包器按 APK 构建号与摘要选择宿主配置，支持范围见 [Android 宿主维护指南](docs/HOST_UPGRADE_MAINTENANCE.md)。

[上游 TVP2 发布记录](../docs/TVP2_TAR2.md)记录了贡献者使用雷鸟 AI 1.0.7 实测的菜单、遥控拍照、TDC1 和 TDG1 诊断。对应结论限于该版本组合。
## 包含与不包含

| 模块 | 公开范围 |
| --- | --- |
| 模型 / Tools / TTS | 自定义模型、搜索工具、本机/可配置云端语音；无个人服务Key |
| 导航 / 音乐 / 番茄 | 原生通信、播放/歌词/封面、导航和计时控制；需 TVP2 与自己的服务配置 |
| 微信读书 | 书架、统计、封面处理、TXT和内置原创测试书；**不含网页正文和Cookie入口** |
| 仪表盘 / 应用广场 | 卡片、四槽小应用、20个离线模板与开发入口；功能取决于眼镜固件提供匹配的兼容层和运行时 |
| 云端字幕 / 后台 | 配置与实现公开，服务端可用性、长期后台行为和不同机型需另行验证；无离线翻译 |
| 固件升级 | 固定候选、官方升级路径及预检；具体版本和授权门禁以对应固件配置为准 |

新增能力的重点待验项：64 KiB TIMG 文件通道、眼镜日志接收目录与 taskId、官方待办回执及发送队列钩子、模型服务的 `parallel_tool_calls` 支持。清晰投屏仅前台运行，目标帧率不等于实际帧率；视频按目标时间抽取普通帧，短按暂停会冻结播放位置。没有新增 AirPlay 接收或 Android 录屏前台服务。

微信读书网页适配的参考项目是 **AGPL-3.0 开源代码，不是闭源**。当前组合未解决许可证兼容，故源码移除该部分；开发者需在遵守许可和服务权限前提下自行适配，详见[边界说明](docs/INSTALL_AND_FLASH.md#微信读书为什么不含网页正文)。本机私用配置、Cookie、签名私钥和账号均不发布。

**微信读书 / 网易云音乐的登录、会员与服务权限需用户自行处理。** 不提供账号、代登录、Cookie 获取或风控绕过。普通用户请继续使用官方 App / 原厂固件，不要为这些功能盲目刷机。

## 固定输入

| 输入 | SHA-256 |
| --- | --- |
| 雷鸟 AI 1.0.5（201）APK | `770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3` |
| 雷鸟 AI 1.0.7（230）APK | `30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91` |
| 雷鸟 AI 1.0.20（309）APK | `05089c96fff1117a0fb9fe8bdc170ba5067bae3c91f8005bbc713440eac09673` |
| [TVP2 ZIP](https://github.com/Turbo1123/Turbo-IO/releases/tag/tvp2-tar2-20261023) `StrixOS-1.0.5.5-TurboIO-TVP2-CANDIDATE-NOT-APPROVED.zip` | `154f0f8e68a69b8fb93f869e423081ee43969bf09c5174e8dbeac192c1047c11` |

原厂来源：[雷鸟 AI Android 官方下载页](https://rayneo.cn/commonPage/venus/appDownload/index_m.html)。摘要不匹配就停止，不能仅改白名单适配新版本。

## 新闻模块技术留存

当前 Android 源码已移除新闻阅读与新闻提词模块。其控制消息、文件传输、双回执确认及其他业务实验方法保留在[新闻推送通道技术参考](docs/NEWS_PUSH_CHANNEL_REFERENCE.md)。已发布的历史 APK 不受源码变更影响。

## SCADA BLE 传感器实验

在 Turbo IO 的 SCADA 传感器卡片中填入实验设备密钥（16 字节，32 位十六进制）和设备前缀（8 字节，16 位十六进制），保存后显式启动扫描。设备身份由已配置前缀派生；扩展不读取 BLE MAC 地址。数据包格式、固定 AES-GCM 向量和字段定义见 [SCADA 协议说明](../docs/scada/ESP32_SCADA_PROTOCOL.md)。

RSSI 阈值和迟滞用于判定设备接近/离开，因此扫描结果用于推断物理接近状态。Manifest 不设置 **neverForLocation**。Android 12（API 31）及以上启动时会同时请求“附近设备扫描”、精确和近似位置信息权限（Android 12 要求精确权限与近似权限一起请求）；近距判断需要精确权限，请在系统提示中选择“精确”。Android 11 及更早版本需要位置权限。首次权限提示结束后，再点一次“启动扫描”。Android 10–11 在屏幕关闭时还会请求后台位置权限；Android 12 及以上由前台启动、声明 location 类型的持续通知服务维持前台位置访问，服务必须在应用界面可见时启动。Android 13 及以上还会请求通知权限。

Android 14 及以上要求 location 前台服务类型和对应声明；打包器会保留宿主服务原有类型位并补齐所需类型。请保持系统“位置信息”开启。停止扫描可在卡片中操作，也可使用前台服务通知中的停止操作。扫描仅在收到通过认证、计数器检查且达到近距阈值的读数后发布卡片；退出阈值使用 RSSI 迟滞。10 秒没有达标近距读数时标记过期，30 秒后移除；持续收到弱信号包不会延长近距读数的有效期。

未设置 **neverForLocation** 是为了保留位置相关 BLE 广告结果；Android 文档提示该标志可能过滤部分信标。当前主机没有 Android SDK、设备或测试信标，因此 Android 版本/OEM 的扫描过滤、屏幕关闭持续性和真实设备权限提示尚未实测。

重放保护会对每个通过认证的新计数器执行一次同步 SharedPreferences 提交，设备按约 500 毫秒刷新时约为每秒两次写入。对短时实验 PoC，这个写入率可接受：持久化失败时读数会被拒收，计数器可在进程重启后继续拒绝旧包；写入发生在工作线程。连续长期运行的闪存写放大和延迟尚未测量，生产用途应先基于设备证据评估；批量提交会改变进程崩溃后的重放边界，因此当前不改变语义。

## 源码构建

依赖 JDK、Node、Python3、apktool（2.12.1）、Android SDK `platforms;android-36` 与 `build-tools;36.0.0`、C 编译器（与上游一致用 clang；Debian GCC 14 会因 `-Werror=misleading-indentation` 拒绝研究源码）。Python SDK依赖按 `dashboard-service/README.md` 安装。

```sh
# 在仓库根目录运行，准备匹配的 Android SDK、官方 APK 和固件输入。
export ANDROID_SDK_ROOT=/path/to/Android/sdk
export TURBO_SDK_PYTHON=/path/to/python-with-dashboard-sdk-dependencies
# 在独立目录解包官方 APK，供原生来源测试使用。
apktool d --no-res -o /path/to/new-official-fixture /path/to/RayNeo_AI.apk
export TURBO_OFFICIAL_LIBAPP=/path/to/new-official-fixture/lib/arm64-v8a/libapp.so
bash android-addon/build.sh
node android-addon/package.mjs /path/to/RayNeo_AI.apk
# 按输出的 work 目录和宿主版本生成对应 OTA 来源。
node android-addon/official-ota-source.mjs "android-addon/build/turbo-build-<id>/host/lib/arm64-v8a/libapp.so" android-addon/build/ota-route
```

多 DEX 宿主（如 1.0.20）的 hook 不一定在 `classes2.dex`，需追加 `--receipt android-addon/build/turbo-build-<id>/package-report-<version>.json`，脚本按收据中经核对的 `hookDex` 校验基包。`tmig1_windows.py patch` 默认读取 `<work>/package-report-<version>.json`，跨目录调用时同样用 `--receipt` 指定。

之后用 Android SDK 的 `zipalign` 和 `apksigner` 及**自己的签名**签署。保持同一个密钥才能覆盖自己以前签名的版本，密钥不要提交仓库。未加 `--guarded` 的准备包不允许升级，不要和正式实验门禁混淆。

宿主版本外置 profile、审计命令和未审计 APK 准入规则见[Android 宿主维护指南](docs/HOST_UPGRADE_MAINTENANCE.md)与[分阶段执行计划](docs/HOST_UPGRADE_PLAN.md)。

可运行 `python3 android-addon/tests/PrivateArtifactAudit.py /path/to/signed.apk` 检查扩展DEX与允许资源、凭据模式及测试替身；不代替人工审查。私有数据存在手机沙盒，不应从手机导出沙盒重新打包。

[TVP2 固件与刷写说明](../docs/TVP2_TAR2.md) · [TVP2 固件源码](../firmware-research/strix-1.0.5.5-tvp2/README.md) · [开发者SDK](../docs/DEVELOPER_ECOSYSTEM.md) · [1.0.5 / TAP1 历史安装教程（不是本版流程）](docs/INSTALL_AND_FLASH.md) · [1.0.4历史记录](docs/ANDROID_104_HISTORY.md)
