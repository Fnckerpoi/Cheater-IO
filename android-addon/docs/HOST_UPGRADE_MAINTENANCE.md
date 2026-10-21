# Android 官方宿主版本维护

## 当前支持状态

| 官方版本 | Code | SHA-256 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 1.0.4 | 195 | `ef2e7dd346ca478e13d0f3f1bf31fa61412e44fb9b4ca9cf0d3e864ac608584b` | 历史/撤销 | 仅保留身份记录，不可打包 |
| 1.0.5 | 201 | `770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3` | 已批准静态构建基线 | 不代表本轮新增真机验收 |
| 1.0.7 | 230 | `30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91` | 已批准静态构建基线 | 已提取官方原始 APK，完成符号重定位、Java反射多版本解耦与 36/36 静态审计匹配；未进行新真机刷机验收 |

## 日常命令

从仓库根目录运行；仅使用官方原始 APK，不要把 TurboIO 派生 APK 当作官方输入。

```powershell
node android-addon/audit-host.mjs C:\path\RayNeo_AI.apk --apktool C:\path\apktool_3.0.3.jar --java C:\path\java.exe --json C:\reports\host-audit.json --markdown C:\reports\host-audit.md
node android-addon/tests/HostProfilesTest.mjs
node android-addon/tests/HostBuildTest.mjs
node android-addon/tests/OtaHooksTest.mjs
```

审计未知 APK 会产生绑定输入 SHA-256 的报告，不会添加或批准 profile。退出码 0 仅表示静态基线匹配；任何输入出现静态差异或工具错误均返回非零状态。未知 APK 即使静态匹配，packagingAllowed 仍为 false。输出报告目录应预先存在，报告路径必须为新文件，禁止覆盖原 APK、profile 或已有报告。报告中的 `not-checked` 表示静态宿主符号检查没有覆盖运行时反射、语义、原生库或设备行为。

普通打包仍使用既有调用形式：

```powershell
node android-addon/package.mjs C:\path\RayNeo_AI_1.0.5.apk
```

通用 CLI 现支持 `--java`、`--apktool`、`--python`、`--dex`、`--output-dir` 显式路径；1.0.7 的真实完整离线命令与产物见 [ANDROID_107_OFFLINE_BUILD.md](ANDROID_107_OFFLINE_BUILD.md)。截至 2026-10-21，本轮 `build-tmig1-windows.ps1` 已支持精确 1.0.5（201）/1.0.7（230）准入；真实 1.0.7 guarded 合并与独立审计通过，后续真实 Windows PowerShell 5.1 完整未签名链已退出 0（见下方续轮补充）；新 guarded 签名链与 PS 7 仍未测。它是实验 OTA 准备入口，不能把通用研究包或本轮离线合并当作设备 OTA 验收。两个 JavaScript 打包入口共享 `host-build.mjs` 准入、隔离构建、APK 审核与收据流程；未知、draft、revoked 或 adapter/SHA 不匹配输入均拒绝。

正式输出目录中的按版本命名的 `package-report-<version>.json` 记录输入、profile 摘要、adapter、工具版本、检查项和输出 SHA-256；`installed`、`flashed`、`runtimeValidated` 均为 false。收据是构建追溯记录，不是设备验收证明。

## 新版本接入流程

1. 从官方来源获取 APK，原样保存；记录来源 URL、取得日期、包名、versionName、versionCode、签名证书指纹和 SHA-256。不得将重打包或已签名的 TurboIO APK登记为官方源。
2. 运行差异审计，保存 JSON 与 Markdown 报告。用隔离目录反编译，逐条核对回调、插件事件、OTA 队列/发送符号、字段与常量。报告中的相似符号只用于定位，不代表映射关系已确认。
3. 逐项检查运行时反射依赖（包括 `NavReflect` 使用的宿主字段 `c/b/e`）、Android framework API、原生 `libapp.so` 和外部服务。`official-ota-source.mjs` 有独立 SHA/offset 约束；本宿主审计不替代这些门禁。
4. 若宿主符号、hook 语义或运行时契约改变，新增显式代码 adapter 和针对性测试。profile JSON 本身不能定义可执行补丁；不能只复制旧 profile 并换 SHA。
5. 候选 profile 保持 `draft`。审核 profile 完整性、代码变更与测试证据；由人工审阅后才可改为 `approved`。当前 adapter 允许集合在代码中固定到确切的官方 APK SHA/version/code。撤销旧版本用 `revoked`。
6. 对批准候选先做离线构建与 APK 完整性审查，再安排单独的设备安装/功能/异常路径验收。构建通过不能替代签名、设备、眼镜或 OTA 结果验收。

## 旧 OTA 来源命令的输入路径

普通打包器现将全新宿主解包到 `android-addon/build/turbo-build-*/host`。若执行既有 `official-ota-source.mjs` 原生库实验步骤，应从**该次构建的收据所对应的隔离 work/host** 提取 `lib/arm64-v8a/libapp.so`，不可复用历史 `build/host-105` 目录。该原生库补丁按 `native-source-profiles.json` 选择精确 1.0.5/1.0.7 SHA、宿主身份和偏移；缺失或跨版本混用失败关闭。

`build.sh` 在编译前还运行原生来源测试，因此首次构建前需在新的独立 fixture 目录解包原始官方 1.0.5 APK，并设置 `TURBO_OFFICIAL_LIBAPP` 指向其中的原始 `lib/arm64-v8a/libapp.so`。Bash 用 `export TURBO_OFFICIAL_LIBAPP=/absolute/path/libapp.so`，PowerShell 用 `$env:TURBO_OFFICIAL_LIBAPP='C:\path\libapp.so'`。测试会验证既有精确库哈希；缺料返回失败，不跳过测试。

## 验收范围

fixture 测试覆盖 schema、静态差异、失败关闭、未知 SHA、两入口共用门禁、隔离解包、整批 hook 规划、验证失败不发布、收据哈希与拒绝覆盖。fixture 结果不是实际 APK 构建证据。本地若无原始官方 APK，必须明确记录真实 APK 路径未测；没有设备验证不得宣称对应新版本或固件已验收。

## 2026-10-21 迭代补充

历史静态基线不等于当前设备状态。较新的 final-20261021-123500 通用构建、guarded 衍生物与七阶段证据见 [TMIG1_107_ITERATION_ACCEPTANCE.md](TMIG1_107_ITERATION_ACCEPTANCE.md)。1.0.5 官方原件本轮缺料，不能将 fixture 正例冒充真实 1.0.5 重建。`build.sh` 的完整工具链与原生输入要求应按当前脚本核对，不能通过跳过来源测试弥补缺料。

发布函数使用先收据、后 APK 的两次独占 hardlink，拒绝覆盖，并对可捕获的第二步失败回滚收据。它不是两个文件的崩溃事务：断电、进程强杀或硬链接不支持的文件系统仍需人工核对，不保证跨文件原子提交。已有正式输出不得覆盖，重试使用新目录。

阶段发布远端最终核查：Release 草稿 419182743（tag `android-107-research-stage1-20261021`）保留私有草稿/prerelease，未公开、未删除。远端三个小文件已上传并核验：release-evidence.json（2072 字节，SHA-256 `eb0cd15003ea4db889a59b885b54a2d48b1e12d74d718fd00ab89b446f187eef`）、SHA256SUMS.txt（292 字节，`ad06137f91c05106b36638fcc75e3754741b418609217dfb94b667114a2d39bd`）、RELEASE_NOTES_zh-CN.md（2501 字节，`ba253e2a0edb9214fb0289335dbf3ac8444f2fdf84366bef16ffc629ab82ce05`）。仅 APK 缺失；大文件上传请求停滞，代理/直连均已尝试，最终 curl 超时退出 1。所有上传任务已停止，不能声明发布完成。


## 续轮：真实 PS 5.1 完整未签名链

`artifacts/tmig1-107-fullps-20261021-135156` 已完成真实 PS 5.1 编译、D8、基础打包、guarded 准备及来源证书校验，耗时约 401 秒，配套 `.exit.txt` 为 0。guarded APK SHA-256 `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d`；此前“完整 PS 链未测”仅是上轮时点状态。新产物未签名、未安装；来源证书校验只针对 official 源，不证明新 guarded APK 已签名。PS 7 未运行，独立审查者已复核通过。完整摘要与限制见 [TMIG1_107_ITERATION_ACCEPTANCE.md](TMIG1_107_ITERATION_ACCEPTANCE.md)。

本轮编码兼容修正是 PS source list writer 使用 WriteAllLines 与 UTF8Encoding(false)，证据确认 86 源码项无 BOM、中文路径 roundtrip 成功；该修改只证明本次 PS 5.1 环境。续轮上传诊断为 CONNECT 代理在握手阶段关闭、0 字节发送；仍仅三个小附件、缺 APK、私有草稿未公开，所有上传任务已停止。

### 最后独立审查结论

独立审查者复核真实 PS 5.1 完整未签名链 PASS：exit 0、约 401 秒，86 项源码 SHA/源清单、无 BOM、中文路径 roundtrip、逐产物 receipt、official 源证书与 unsigned 出口均确认。证据为 `artifacts/tmig1-107-fullps-20261021-135156/independent-audit.json`、`encoding-evidence.json` 和同级执行日志；base SHA-256 `b4990f5c308a3535e6af2aa243304ba1be88bf989a59fec4d8fb0d188b696f6e`，guarded SHA-256 `24ce38ff9b59bd5f580f0d11b0496ca60425422153e7456a1bb8b2480992789d`。

新旧 guarded 的容器差异仅 marker DOS timestamp 共 4 字节，全部 payload 和 raw compressed streams 一致；这不是 bit-reproducible 构建，receipt 分别绑定各产物。若未来承诺字节可复现，须另行固定 marker 时间并验收，本轮不改产物。完整 PS 5.1 未签名链已测；PS 7、签名分支仍未测，真实 1.0.5 官方原件正例缺料，设备/Android 107 OTA 仍未验收。

上传续轮最终诊断为 CONNECT 代理 RemoteDisconnected、0 字节发送；远端仍三个已核验小附件、缺 APK，所有上传任务停止，Release 保持私有草稿/prerelease 未公开。四文件发布资产未改。
