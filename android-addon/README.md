# Turbo IO Android · 1.0.5 完整插件与实验 OTA

**2026-09-27：公开1.0.5（201）扩展源码、去除网页正文适配的完整APK，以及实刷 TAP1-TEST-01 固件。** 非官方、非商业研究发行；不是稳定版，也不是独立 Android 蓝牙 SDK。

## 下载与安装

**[下载 APK / 固件 / SHA-256](https://github.com/Turbo1123/Turbo-IO/releases/tag/android-105-guard07-tap1-test01) → [逐步安装与刷写教程](docs/INSTALL_AND_FLASH.md)**

**GUARD-07 刷完必须收尾：** 眼镜自然回首页后，在插件升级页底部完成「已自然回首页：允许只读回查」及「已检查显示TEST：解除结果保护」。确认`LOCKED / 授权0 / 结果保护未开始`后再测日常功能；否则手机会继续拦截官方业务。更新未结束或状态不明时不可释放。[必做步骤](docs/INSTALL_AND_FLASH.md#4-必做自然重启--只读回查--释放保护)

不必自己合并插件；公开APK已包含扩展。APK与官方签名不同，签名冲突时请先导出旧App数据，不能无损覆盖。固件是高风险实验用品，不懂刷机排错请勿尝试；不保证回滚救砖。

## 包含与不包含

| 模块 | 公开范围 |
| --- | --- |
| 模型 / Tools / TTS | 自定义模型、搜索工具、本机/可配置云端语音；无个人服务Key |
| 导航 / 音乐 / 番茄 | 原生通信、播放/歌词/封面、导航和计时控制；需匹配眼镜固件与自己的服务配置 |
| 微信读书 | 书架、统计、封面处理、TXT和内置原创测试书；**不含网页正文和Cookie入口** |
| 仪表盘 / 应用广场 | 卡片、四槽小应用、20个离线模板与开发入口 |
| 新闻 / 云端字幕 / 后台 | 配置与实现公开，服务端可用性、长期后台行为和不同机型需另行验证；无离线翻译 |
| 固件升级 | GUARD-07：固定候选、只下载、单次授权、官方升级路径、2分钟预检、人工收尾 |

微信读书网页适配的参考项目是 **AGPL-3.0 开源代码，不是闭源**。当前组合未解决许可证兼容，故源码和APK均移除该部分；开发者需在遵守许可和服务权限前提下自行适配，详见[边界说明](docs/INSTALL_AND_FLASH.md#微信读书为什么不含网页正文)。本机私用配置、Cookie、签名私钥和账号均不发布。

**微信读书 / 网易云音乐的登录、会员与服务权限需用户自行处理。** 不提供账号、代登录、Cookie 获取或风控绕过；登录成功不等于本项目已支持微信读书网页正文。普通用户请继续使用官方 App / 原厂固件，不要为这些功能盲目刷机。

Fold3 / Android15 本轮已分别获得音乐、阅读测试文本、应用示例、导航和番茄相关验收；原厂天气、云端服务、长时后台、物理按键全覆盖及故障恢复不能视为全量通过。此次OTA用户实测成功的APK是GUARD-07私用构建；公开APK移除上述正文适配和专属字幕地址，OTA源码不变，重新编译不等于再次真机刷写。

## SCADA BLE 传感器实验

在 Turbo IO 的 SCADA 传感器卡片中填入实验设备密钥（16 字节，32 位十六进制）和设备前缀（8 字节，16 位十六进制），保存后显式启动扫描。设备身份由已配置前缀派生；扩展不读取 BLE MAC 地址。数据包格式、固定 AES-GCM 向量和字段定义见 [SCADA 协议说明](../docs/ESP32_SCADA_PROTOCOL.md)。

RSSI 阈值和迟滞用于判定设备接近/离开，因此扫描结果用于推断物理接近状态。Manifest 不设置 **neverForLocation**。Android 12（API 31）及以上启动时会同时请求“附近设备扫描”、精确和近似位置信息权限（Android 12 要求精确权限与近似权限一起请求）；近距判断需要精确权限，请在系统提示中选择“精确”。Android 11 及更早版本需要位置权限。首次权限提示结束后，再点一次“启动扫描”。Android 10–11 在屏幕关闭时还会请求后台位置权限；Android 12 及以上由前台启动、声明 location 类型的持续通知服务维持前台位置访问，服务必须在应用界面可见时启动。Android 13 及以上还会请求通知权限。

Android 14 及以上要求 location 前台服务类型和对应声明；打包器会保留宿主服务原有类型位并补齐所需类型。请保持系统“位置信息”开启。停止扫描可在卡片中操作，也可使用前台服务通知中的停止操作。扫描仅在收到通过认证、计数器检查且达到近距阈值的读数后发布卡片；退出阈值使用 RSSI 迟滞。10 秒没有达标近距读数时标记过期，30 秒后移除；持续收到弱信号包不会延长近距读数的有效期。

未设置 **neverForLocation** 是为了保留位置相关 BLE 广告结果；Android 文档提示该标志可能过滤部分信标。当前主机没有 Android SDK、设备或测试信标，因此 Android 版本/OEM 的扫描过滤、屏幕关闭持续性和真实设备权限提示尚未实测。

重放保护会对每个通过认证的新计数器执行一次同步 SharedPreferences 提交，设备按约 500 毫秒刷新时约为每秒两次写入。对短时实验 PoC，这个写入率可接受：持久化失败时读数会被拒收，计数器可在进程重启后继续拒绝旧包；写入发生在工作线程。连续长期运行的闪存写放大和延迟尚未测量，生产用途应先基于设备证据评估；批量提交会改变进程崩溃后的重放边界，因此当前不改变语义。

## 源码构建

依赖 JDK、Node、Python3、apktool、Android SDK platform36/build-tools36.0.0、C编译器。Python SDK依赖按 `dashboard-service/README.md` 安装。原厂输入必须是1.0.5（201），SHA-256：

`770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3`

原厂来源：[雷鸟 AI Android 官方下载页](https://rayneo.cn/commonPage/venus/appDownload/index_m.html)。网站可能已更新，摘要不匹配就停止，不能仅改白名单适配新版本。

```sh
# 仓库根运行；先准备本Release固件和匹配的构建依赖。
mkdir -p official-addon/build/app-ap-20260927-test-01
# 将 TurboIO-TAP1-TEST-01.zip 复制并重命名为：
# official-addon/build/app-ap-20260927-test-01/StrixOS-1.0.4.12-TAP1-TEST-01-CANDIDATE-NOT-APPROVED.zip

export ANDROID_SDK_ROOT=/path/to/Android/sdk
export TURBO_SDK_PYTHON=/path/to/python-with-dashboard-sdk-dependencies
# 先在一个新的独立目录解包官方原 APK，供原生来源测试读取；不要复用旧补丁目录。
apktool d --no-res -o /path/to/new-official-fixture /path/to/RayNeo_AI_1.0.5.apk
export TURBO_OFFICIAL_LIBAPP=/path/to/new-official-fixture/lib/arm64-v8a/libapp.so
bash android-addon/build.sh
node android-addon/package.mjs /path/to/RayNeo_AI_1.0.5.apk
# package 命令输出唯一 work 路径，收据记录输入/输出哈希与工具版本。
# 如果单独复现既有 native OTA source 实验，从该 work/host 提取库：
# 如果单独复现原生来源实验，将 <id> 替换成 package 命令输出的 work 目录名。
node android-addon/official-ota-source.mjs "android-addon/build/turbo-build-<id>/host/lib/arm64-v8a/libapp.so" android-addon/build/ota-route
python3 android-addon/package-official-ota-preparation.py --guarded \
 /path/to/RayNeo_AI_1.0.5.apk \
 android-addon/build/TurboIO-RayNeo-1.0.5-unsigned.apk \
 android-addon/build/ota-route/libapp.so \
 android-addon/build/TurboIO-GUARD07-unsigned.apk
```

之后用 Android SDK 的 `zipalign` 和 `apksigner` 及**自己的签名**签署。保持同一个密钥才能覆盖自己以前签名的版本，密钥不要提交仓库。未加 `--guarded` 的准备包不允许升级，不要和正式实验门禁混淆。

宿主版本外置 profile、审计命令和未审计 APK 准入规则见[Android 宿主维护指南](docs/HOST_UPGRADE_MAINTENANCE.md)与[分阶段执行计划](docs/HOST_UPGRADE_PLAN.md)。

可运行 `python3 android-addon/tests/PrivateArtifactAudit.py /path/to/signed.apk` 检查扩展DEX与允许资源、凭据模式及测试替身；不代替人工审查。私有数据存在手机沙盒，不应从手机导出沙盒重新打包。

[安装教程](docs/INSTALL_AND_FLASH.md) · [固件范围与摘要](../firmware-research/strix-1.0.4.12/tap1/README.md) · [开发者SDK](../docs/DEVELOPER_ECOSYSTEM.md) · [1.0.4历史记录（不是本版流程）](docs/ANDROID_104_HISTORY.md)
