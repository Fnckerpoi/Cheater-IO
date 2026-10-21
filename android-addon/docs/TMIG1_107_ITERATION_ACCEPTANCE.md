# TMIG1 / Android 1.0.7 迭代验收记录

记录时点：2026-10-21。本记录区分通用研究 APK、guarded OTA 准备 APK 与眼镜固件；离线通过不代表设备或 OTA 验收。

## 七阶段实际状态

| 阶段 | 实际状态与证据 | 缺口 |
| --- | --- | --- |
| 0 输入冻结 | 官方 Android 1.0.7（230）原包及 Strix OS 1.0.5.5 ZIP 已核对哈希、身份与 ZIP 清单 | APK 来源记录为手机提取；完整获取时间/URL/split 集记录不足；105 官方原件本轮缺料 |
| 1 宿主契约复核 | audit-107-matched 为 36 匹配、0 缺失/改变/歧义；Java 多版本与精确 profile 门禁存在 | 4 项动态/原生依赖未由静态审计覆盖，不代表运行语义全验 |
| 2 原生与模块适配 | 真实 107 libapp 两个序列化 URL 槽位补丁及 guarded 合并通过；收据绑定来源和基包 | Android 107 TMIG1 设备协议与端到端 OTA 未验收 |
| 3 隔离构建审计 | 111525 历史完整构建、新版 123500 通用基包和 retry guarded 实物均存在；独立哈希/成员审计通过 | PS 5.1 完整未签名链后续已退出 0；guarded 签名链/PS 7 未测，105 真实重建缺料 |
| 4 安装前门禁 | 新版通用共存包签名、对齐、ZIP、白名单资产与有界审计通过；身份、证书、数据保全仍须安装前确认 | guarded APK 未签名；本次没有设备动作 |
| 5 受控设备验收 | 旧研究包有有限安装/启动记录及用户反馈 | 精确新版 48798… 候选和 8f780… guarded 均无本轮设备验收；旧 iOS 固件反馈不得外推 |
| 6 发布维护闭环 | 4 项白名单资产全量上传并经远端 digest 逐项核验；已发布预发行版（Prerelease） | 发布链接 https://github.com/Fnckerpoi/Turbo-IO/releases/tag/android-107-research-stage1-20261021；仅供离线研究候选，真机 OTA 未刷写验收 |

发布最终状态由主代理远端复查确认：草稿 419182743 保持私有草稿/prerelease，未公开、未删除。远端三个小文件已上传并核验：release-evidence.json（2072 字节，SHA-256 `eb0cd15003ea4db889a59b885b54a2d48b1e12d74d718fd00ab89b446f187eef`）、SHA256SUMS.txt（292 字节，`ad06137f91c05106b36638fcc75e3754741b418609217dfb94b667114a2d39bd`）、RELEASE_NOTES_zh-CN.md（2501 字节，`ba253e2a0edb9214fb0289335dbf3ac8444f2fdf84366bef16ffc629ab82ce05`）。仅 APK 缺失；大文件上传请求停滞，代理/直连均已尝试，最终 curl 超时退出 1。所有上传任务已停止，不能声明发布完成。本地四文件目录为 `artifacts/releases/android-107-research-stage1`。

## 精确基线和实物摘要

以下均为 SHA-256，路径相对仓库根目录。guarded 四项摘要在本次文档核查中重新计算，与收据一致。

| 对象/路径 | SHA-256 |
| --- | --- |
| source：artifacts/android-rayneo-device-107-extract/base.apk | `30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91` |
| base：artifacts/android-107-offline/final-20261021-123500/package/TurboIO-RayNeo-1.0.7-unsigned.apk | `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e` |
| native 原始：artifacts/tmig1-107-verification/retry/libapp-107.so | `a35159a82c0916f62ed6c4da271a40217300cc6678b29c54f2591d9fb592a45c` |
| native 补丁：artifacts/tmig1-107-verification/retry/libapp-guarded.so | `48ffd34352bf23eca928ad9c2f576347c4965a5af5ca05a35c1f1ed2ffc9fd50` |
| output：artifacts/tmig1-107-verification/retry/guarded-unsigned.apk | `8f780b17fb499724aa3875a1851b46f8fa6a9acce43c5129985c7dd3702a0647` |
| receipt：artifacts/tmig1-107-verification/retry/guarded-unsigned.audit.json | `78d1dbc0c89ce85b3c42ea76c0fe983c5e633c77aad49b505bcc33427eb0548d` |
| 新版通用共存签名 APK：artifacts/android-107-offline/final-20261021-123500/TurboIO-RayNeo-1.0.7-research-coexist-signed.apk | `48798c726894dea30dffd58ad509db577856b327f18328af0ca551fdb9e4de0c` |
| 官方固件：firmware-research/strix-1.0.5.5/stock/Strix-OS-1.0.5.5-original.zip | `2927917f976ef3c9b183253e2342a7a0c85a3e454e973876100f3158da5ec367` |
| TMIG1 固件：artifacts/android-tmig1/StrixOS-1.0.5.5-TurboIO-TMIG1-CANDIDATE-NOT-APPROVED.zip | `f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940` |

原包身份 `com.rayneo.venus.pub` / 1.0.7（230），官方证书 SHA-256 `a5bd909475e5c7441e0ef48ff7ee69efbc594808a60b470a4feb6d30b0340883`。共存研究包为 `com.turboio.rayneo107.research` / 1.0.7（230），minSdk 29、targetSdk 36，研究证书 `1b070381fc578193779539456722e096b21fe447959000952dcd488842d8f8c3`。guarded 保留来源身份且尚未签名，不等于共存研究包。

收据 mode 为 OFFICIAL-OTA-GUARD-02，标记 assets/turboio/official-ota-guarded.txt；changesOnlyTwoSerializedURLs / allOtherPayloadsByteIdentical 为 true；upgradeSendingEnabled=false，oneTimeAuthorizationAvailable=true，installed/deviceValidated=false。可提供授权机制不代表授权已发生或设备安全性已验收。

## 重现入口与独立检查

从仓库根目录运行。以下是复现参考，本次文档任务没有重新构建或操作设备。工具变量应指向本地已核验 Java/Node/Python/SDK；输出必须使用新的不存在路径。

```powershell
# 使用现有真实 source/base，创建新的独立准备目录；不得复用验收目录
New-Item -ItemType Directory artifacts/tmig1-107-new-run
python -B -X utf8 android-addon/tmig1_windows.py patch artifacts/android-rayneo-device-107-extract/base.apk artifacts/android-107-offline/final-20261021-123500/package/TurboIO-RayNeo-1.0.7-unsigned.apk artifacts/tmig1-107-new-run node

# 独立回归；原生来源测试需要真实库，不以 fixture 代替
# Windows 多库输入用分号分隔；本例只复核真实 107，不代表 105 已复测
$env:TURBO_NATIVE_INPUTS = (Resolve-Path artifacts/tmig1-107-verification/libapp-107.so).Path
python -B -X utf8 android-addon/tests/Tmig1PreparationTest.py
python -B -O -X utf8 android-addon/tests/Tmig1PreparationTest.py
node android-addon/tests/HostProfilesTest.mjs
node android-addon/tests/HostBuildTest.mjs
node android-addon/tests/OtaHooksTest.mjs

# 真实通用基包的只读内容审计
python -B -X utf8 android-addon/verify_apk.py artifacts/android-rayneo-device-107-extract/base.apk artifacts/android-107-offline/final-20261021-123500/package/TurboIO-RayNeo-1.0.7-unsigned.apk --public-assets
python -B -X utf8 android-addon/tests/PrivateArtifactAudit.py artifacts/android-107-offline/final-20261021-123500/TurboIO-RayNeo-1.0.7-research-coexist-signed.apk --dex artifacts/android-107-offline/final-20261021-123500/dex/classes.dex --source artifacts/android-rayneo-device-107-extract/base.apk
```

完整 PS 入口参数（此前参考；后续实际 PS 5.1 未签名执行证据见文末）：

```powershell
& android-addon/build-tmig1-windows.ps1 -SourceApk artifacts/android-rayneo-device-107-extract/base.apk -JavaHome $JavaHome -AndroidJar $AndroidJar -BuildTools $BuildTools -ApktoolJar $ApktoolJar -Python $PythonExe -Node $NodeExe -OutputDirectory artifacts/tmig1-107-new-ps-run
```

不传 KeyStore 时签名延后，不自动生成密钥。PS 首先运行 admit-tmig1.mjs，精确支持 105/107；107 成功合并来自真实原包/基包与 tmig1_windows.py patch，本项历史单独合并证据不等于整段 PS 执行成功；后续完整 PS 5.1 未签名链另有实际执行证据。通用编译命令与历史 111525 工具链见 [ANDROID_107_OFFLINE_BUILD.md](ANDROID_107_OFFLINE_BUILD.md)。

主代理在本轮独立重跑 Tmig1PreparationTest（三项）、HostProfilesTest、HostBuildTest 与 diff-check，均退出 0。Preparation 包括真实原生来源/两处槽位及槽外字节不变、错误 profile/截断/篡改拒绝、逐 payload 字节篡改、重复 marker 与损坏 ZIP 拒绝（没有独立额外成员负例，不据此声称已测试）、未知 CLI 与覆盖拒绝。HostBuild 包含 ENOSPC 收据写失败、竞争已有收据、第二个 APK hardlink 失败回滚、审计失败不发布、验证后候选篡改拒绝。其 build runner 是 fixture，不能标成真实构建。

独立审查者另反馈：真实 107 的 `os.link` 发布失败注入通过；Preparation 普通模式与 `-O` 优化模式各 3 项通过，PUBLIC 回归 5 项通过。这是独立审查层的执行回报，与上面的主代理及本文作者核查分开记录；PUBLIC 旧基线回归不代表 107 实机或 OTA 验收。

`artifacts/tmig1-107-verification/ps-unknown.log` 记录未知 SHA 在准入阶段拒绝；`real-preparation.log` 记录旧执行因已有 staged native 文件拒绝，`retry/real-preparation.log` 对应新的独立目录成功内容审计。当前 helper 对同字节 staged 库可复用，对不同字节拒绝；不能覆盖既有 APK。

本轮另对通用 123500 基包重跑 verify_apk，unexpected_paths=[]、native/资源保留；共存签名 APK 有界审计 162 输入无已知凭据命中，签名/对齐/CRC通过。审计不覆盖全部第三方内容、未知凭据格式或所有隐私风险。历史研究包有 PUSH_10102 / illegal package，厂商推送身份限制仍在。

发布采用两次独占 hardlink：先收据、后 APK；可捕获异常回滚不等于崩溃事务，进程强杀/断电可能留孤立收据，不支持硬链接的文件系统也不保证可用。不得宣传跨文件原子事务或跨磁盘可靠提交。

## 设备验收前检查清单（待执行）

- 确认具体 APK 摘要、包名/version、原包/研究包证书、设备型号和固件身份，确认目标对象是通用包还是 guarded 包。
- 先确认既有安装与数据保全/恢复路线；禁止默认卸载、清数据或假设不同证书可覆盖，独立确认官方包最终状态。
- 记录电量、电源、存储空间和蓝牙/USB/网络连接；采用对应官方协议要求，不臆定安全阈值。
- 分别确认安装、启动、连接、业务、OTA 的明确授权和单次授权状态；在保护默认开启时先验拒绝，再安排授权路径。
- 在受控范围内观察流程自然返回首页/完成态，不以强退、强制重启或人工跳转冒充成功收尾。
- 自然重启/完成后只读回查版本、身份、安装回执、连接和结果；传输完成不等于固件生效。
- 检查授权消费与保护恢复；任何“解除保护”都须单独授权并验证影响范围，验收后关闭临时许可，恢复默认拒绝。
- 各业务及异常路径分别留证据；未执行项明确未验，不能用历史 iOS 1.0.6（218）实刷反馈证明 Android 107 OTA。

本次仅维护文档，没有执行以上设备动作，没有更改四文件发布资产。安装历史与限制见 [ANDROID_107_INSTALL_TEST.md](ANDROID_107_INSTALL_TEST.md)，持续维护入口见 [HOST_UPGRADE_MAINTENANCE.md](HOST_UPGRADE_MAINTENANCE.md)。

## 后续完整 PS 5.1 未签名执行证据

旧章节保留对应时点。后续真实运行目录为 `artifacts/tmig1-107-fullps-20261021-135156`，耗时约 401 秒；记录为 `artifacts/tmig1-107-fullps-20261021-135156.command.json`、同名 `.log` 与 `.exit.txt`（0）。日志末尾明确 Signing deferred，没有生成密钥或执行设备动作。本次文档复核重新计算 guarded 与 receipt 摘要，均与报告相符。独立审查者已复核通过，结论仅覆盖本次 PS 5.1 完整未签名链。

| 全链对象 | SHA-256 |
| --- | --- |
| source | `30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91` |
| base-unsigned.apk | `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e` |
| 原始 native | `a35159a82c0916f62ed6c4da271a40217300cc6678b29c54f2591d9fb592a45c` |
| libapp-guarded.so | `48ffd34352bf23eca928ad9c2f576347c4965a5af5ca05a35c1f1ed2ffc9fd50` |
| guarded-unsigned.apk | `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d` |
| guarded-unsigned.audit.json | `5c4f60c7f9d07edf2a7ba08b9ceb7fe871ae88f8e9a8a91bc0612230c7af4800` |

主代理独立 CRC/逐成员审计通过，base 4043 项到 guarded 4044 项：共有 payload 仅 lib/arm64-v8a/libapp.so 改变，新增 assets/turboio/official-ota-guarded.txt，其他 payload 字节一致；目录内 independent-audit.json 明确 receiptHashesMatch/zipIntegrityPassed=true，signed/installed=false。来源证书只验 official 源，不能把 source-certificates.txt 当新 guarded 的签名证明。

86 项源码、182 class、552300 字节 DEX；addon SHA 为 `ff7b9ab01255e06e5eb3f7151a6737d2f295d4176f3669dbd015667c510a816c`。encoding-evidence.json 记录 PowerShell 5.1.26100.9711、sourcesNoBom/noBom/unicodeRoundTrip=true；源码列表 writer 改为 System.IO.File.WriteAllLines + UTF8Encoding(false)，兼容 PS 5.1。PS 7 未运行，不外推该环境。

全链新 guarded 与 retry 旧 `8f780…` 摘要不同，主代理逐字节定位为 marker ZIP 时间戳共 4 字节差异（13:58:26 对 13:23:24）；载荷相同不等于 ZIP 字节确定，不能宣传字节级确定构建。保留两个产物各自哈希，不覆盖历史收据。

续轮上传诊断：CONNECT 代理在握手阶段关闭，0 字节发送；最终远端仍三个小附件，APK 缺失，全部上传任务停止，草稿未公开。阶段发布的四文件保持不变，不把新 unsigned guarded 加进发布资产，也不外推为已签名或已设备验收。

### 最后独立审查结论

独立审查者复核真实 PS 5.1 完整未签名链 PASS：exit 0、约 401 秒，86 项源码 SHA/源清单、无 BOM、中文路径 roundtrip、逐产物 receipt、official 源证书与 unsigned 出口均确认。证据为 `artifacts/tmig1-107-fullps-20261021-135156/independent-audit.json`、`encoding-evidence.json` 和同级执行日志；base SHA-256 `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e`，guarded SHA-256 `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d`。

新旧 guarded 的容器差异仅 marker DOS timestamp 共 4 字节，全部 payload 和 raw compressed streams 一致；这不是 bit-reproducible 构建，receipt 分别绑定各产物。若未来承诺字节可复现，须另行固定 marker 时间并验收，本轮不改产物。完整 PS 5.1 未签名链已测；PS 7、签名分支仍未测，真实 1.0.5 官方原件正例缺料，设备/Android 107 OTA 仍未验收。

上传续轮最终诊断为 CONNECT 代理 RemoteDisconnected、0 字节发送；远端仍三个已核验小附件、缺 APK，所有上传任务停止，Release 保持私有草稿/prerelease 未公开。四文件发布资产未改。

