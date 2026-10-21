# RayNeo AI 1.0.7 完整离线构建交付

## 实际产物

本轮已对手机提取的 1.0.7（230）APK 完成 javac、D8、apktool 重组、扩展注入与实际 APK 验证。不是仅 fixture 模拟构建。产物尚未签名、未安装、未刷写，运行时功能未验收。

- 原包：`artifacts/android-rayneo-device-107-extract/base.apk`
- 原包 SHA-256：`30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91`
- 官方原包证书 SHA-256：`a5bd909475e5c7441e0ef48ff7ee69efbc594808a60b470a4feb6d30b0340883`（apksigner verify 通过）
- 未签名产物：`artifacts/android-107-offline/final-20261021-111525/package/TurboIO-RayNeo-1.0.7-unsigned.apk`
- 大小：429,331,290 字节
- 产物 SHA-256：`800c657c16ed80d226bc3e49544f9ea635d68d8815b3bb37e5747507acc14856`
- 构建收据：同目录 `package-report-1.0.7.json`
- 本轮 addon DEX SHA-256：`3c8ec04024e713bdf833aba9089014fe4988566ba41fb4f74f482fd9335cacef`

实际命令、86 项源码清单及 tree digest、182 项 class 清单、javac/D8/package 日志保存在 `artifacts/android-107-offline/final-20261021-111525/`。此前 `run-20261021-110632` 是发现编码问题的演练目录，不能当最终交付。

## 本轮修复

- 通用 CLI 增加显式 `--java`、`--apktool`、`--python`、`--dex`、`--output-dir`，不依赖全局 PATH 的默认工具。
- 1.0.7 原包已有 classes4.dex；新增扩展 DEX 使用源 APK 推导的下一个槽 classes5.dex，原厂 classes4 不覆盖。验证器同步推导，不以放宽资源白名单解决冲突。
- Python 组装与校验子进程使用 UTF-8，修复 Windows 默认编码读取中文资源目录失败。
- 修复 NavGlasses、OfficialOtaBridge 和 OtaController 三处遗留旧版 packet 方法 u；统一使用版本映射（1.0.7 为 t）。
- HostSymbols 使用非初始化类加载，以 manager 为版本识别入口并检查完整必要契约。未知、双版本、partial 或类型不符失败关闭；无关旧混淆类 E3.b 不再误判宿主。

## 复现打包

在仓库根目录运行；先按 commands.txt 编译自己的最新 classes.dex，下面显式复用的是本次已生成 DEX。输出应换成新的不存在目录，不能覆盖已验收产物。

```powershell
node android-addon/package.mjs artifacts/android-rayneo-device-107-extract/base.apk `
  --java "C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin/java.exe" `
  --apktool "C:/Users/38271/Documents/GitHub/Turbo-IO/artifacts/tools/apktool/apktool_3.0.3.jar" `
  --python "C:/Users/38271/AppData/Local/Programs/Python/Python312/python.exe" `
  --dex artifacts/android-107-offline/final-20261021-111525/dex/classes.dex `
  --output-dir artifacts/android-107-offline/new-package
```

## 独立验证

- HostSymbolsTest：八组 classpath 场景和六处 packet 调用扫描通过；验证不触发宿主静态初始化。
- PackageCliTest、HostBuildTest、HostProfilesTest、OtaHooksTest 通过。
- ApkAssemblyTest 两项通过，覆盖四原厂 DEX 输入、扩展槽选择、缺扩展及篡改原厂 DEX 拒绝。
- 实际产物 verify_apk.py --public-assets 通过，unexpected_paths=[]；原有成员只有 AndroidManifest.xml 与 classes2.dex 改变。
- 实际产物 PrivateArtifactAudit.py --dex 本轮DEX --source 原APK 通过，162项自有输入无凭据模式命中、公共资源匹配、无测试替身。该检查是有界模式检查，不能替代所有形式的隐私审查。
- 收据 source/addon/output 哈希与实物一致；installed/flashed/runtimeValidated 均 false。

## 后续签名与安装边界

本产物是通用扩展基包，原生 libapp.so 保持官方字节；没有通过旧版 TMIG1 专用准备包链路开启实验 OTA。本段记录 111525 通用基包的历史范围；截至 2026-10-21 的后续适配中，`build-tmig1-windows.ps1` 已支持精确 1.0.5/1.0.7 准入，真实 1.0.7 guarded 合并另有证据，上轮时点完整 PowerShell SDK 链尚未测；后续 PS 5.1 未签名全链已通过，见文末续轮补充。

安装前需选择合法可用的研究签名并完成 zipalign/apksigner 校验。研究签名通常与手机官方证书不同，不能假定 adb install -r 可覆盖。不得为安装此包自动卸载官方 App 或清除其数据；应另行决定共存包方案或备份迁移路径。签名、安装、设备功能及实验固件升级是后续独立验收阶段。

## 后续时点：123500 与 guarded（2026-10-21）

上文 111525 原始交付、哈希及当时未签名/未安装结论原样保留，不以较新产物替换历史证据。较新通用基包为 `artifacts/android-107-offline/final-20261021-123500/package/TurboIO-RayNeo-1.0.7-unsigned.apk`，SHA-256 `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e`；同目录 package-report 收据绑定该摘要及新版 addon DEX。

123500 共存研究签名包 SHA-256 `48798c726894dea30dffd58ad509db577856b327f18328af0ca551fdb9e4de0c`，已独立验证签名、对齐、ZIP、来源链及有界私有审计。旧 device-installed.apk 的摘要为 `5a691367c3a03877c7d3099085fe5b0ab6b839ec13ab8b922cfe31b69e68abaa`；旧设备验收不能套到新版候选。

另有 `artifacts/tmig1-107-verification/retry/guarded-unsigned.apk`，SHA-256 `8f780b17fb499724aa3875a1851b46f8fa6a9acce43c5129985c7dd3702a0647`，只证明真实 107 原生来源准备与 guarded 合并，未签名、未安装、未设备验收。完整 SHA 表、重现命令、测试、拒绝路径、发布草稿及设备前置清单见 [TMIG1_107_ITERATION_ACCEPTANCE.md](TMIG1_107_ITERATION_ACCEPTANCE.md)。本补充不修改阶段发布目录的四个资产。

## 续轮：真实 Windows PowerShell 5.1 全链（未签名）

在 `artifacts/tmig1-107-fullps-20261021-135156` 已完成约 401 秒的真实完整未签名构建，配套 command.json/log/exit.txt 存于 artifacts 同级，exit=0；不是仅 Python 合并。PS 5.1 使用 WriteAllLines + UTF8Encoding(false) 生成源码列表，86 源码、182 class、552300 字节 DEX；encoding-evidence.json 确认无 BOM 和中文路径 roundtrip。

新 base SHA 仍为 `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e`，guarded SHA 为 `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d`；与 retry 旧 guarded 内容成员相同但 marker ZIP 时间戳不同，不能宣称字节级确定构建。仅验 official 源证书，新 guarded 未签名、未安装；PS 7 与 guarded 签名/设备链未验。旧 111525、123500 及 retry 摘要均保留其对应实物。详细收据、独立成员审计和待复核项见 [TMIG1_107_ITERATION_ACCEPTANCE.md](TMIG1_107_ITERATION_ACCEPTANCE.md)。

### 最后独立审查结论

独立审查者复核真实 PS 5.1 完整未签名链 PASS：exit 0、约 401 秒，86 项源码 SHA/源清单、无 BOM、中文路径 roundtrip、逐产物 receipt、official 源证书与 unsigned 出口均确认。证据为 `artifacts/tmig1-107-fullps-20261021-135156/independent-audit.json`、`encoding-evidence.json` 和同级执行日志；base SHA-256 `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e`，guarded SHA-256 `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d`。

新旧 guarded 的容器差异仅 marker DOS timestamp 共 4 字节，全部 payload 和 raw compressed streams 一致；这不是 bit-reproducible 构建，receipt 分别绑定各产物。若未来承诺字节可复现，须另行固定 marker 时间并验收，本轮不改产物。完整 PS 5.1 未签名链已测；PS 7、签名分支仍未测，真实 1.0.5 官方原件正例缺料，设备/Android 107 OTA 仍未验收。

上传续轮最终诊断为 CONNECT 代理 RemoteDisconnected、0 字节发送；远端仍三个已核验小附件、缺 APK，所有上传任务停止，Release 保持私有草稿/prerelease 未公开。四文件发布资产未改。
