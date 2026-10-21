# Android 宿主升级维护验收记录

## 结论

已实现外置注册与静态契约、只读 APK 差异审计、普通/Windows 共用失败关闭构建、隔离工作目录和验证后独占发布。当前只批准既有官方 1.0.5（201）精确 SHA；1.0.4 历史记录拒绝构建，V1.0.7 未取得样本、未批准。

本记录验证的是工具与 fixture 回归，不是新 APK 的真实构建、设备或固件验收。

## 独立运行的检查

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| 新增/修改 JS 模块 node --check | 通过 | profile、audit、build、两个入口、native 输入测试模块 |
| node android-addon/tests/HostProfilesTest.mjs | 通过 | schema、重复身份、固定 adapter/契约摘要、缺失/变化/歧义/残留、常量注释负例、默认未知身份、报告保护、源快照 |
| node android-addon/tests/HostBuildTest.mjs | 通过 | 两入口、准入、零补丁写入、隔离目录、verify失败、哈希绑定、拒覆盖、收据与APK发布故障 |
| node android-addon/tests/OfficialOtaInputTest.mjs | 通过 | 显式库路径优先、旧路径兼容、缺料失败 |
| node android-addon/tests/OtaHooksTest.mjs | 通过，14 assertions | 既有 wrapper 行为与精确 ABI 前置检查 |
| git diff --check | 通过 | 空白/差异格式 |
| 两条实际 CLI 输入公开派生 APK | 按预期拒绝 | 均 exit 1，提示 Unsupported APK SHA256；Windows负例目录未创建 |

真实拒绝用样本为 `artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk`，SHA-256 `d39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0`，与发行清单一致。它是 TurboIO 派生包，**不是**官方 profile 正例要求的 `770ba079...720d3ab3`，因此拒绝符合预期。

## 复审返修

独立只读审查发现并返修以下问题，新增针对性回归：

- 基线仅检查格式可能被删减：adapter现绑定完整规范化契约 SHA，加载、审计与准入均检查。
- 目录审计默认假定官方输入：默认 SHA 现为 null，未提供输入身份不能获得准入。
- 常量检查误读注释：限定真实指令或 String 字段初始化。
- 报告覆盖已有 APK/profile：独占创建、拒绝同路径和已有输出，失败清理本次创建报告。
- 审计摘要与解码内容错绑：冻结私有 APK 快照，前后校验哈希与大小。
- 收据复制中断留下半文件：改为同文件系统独占硬链接发布，并测试磁盘错误、并发收据和 APK 链接失败回滚。

## 未完成的环境相关验证

- 未找到官方原始 1.0.5 APK，也未找到 V1.0.7 APK；实际正例反编译/完整 APK 打包未执行。
- 本机 Java 命令不可用。实际只读 APK 审计尝试在启动 apktool 前失败：`spawnSync java ENOENT`，未生成有效审计报告。
- OfficialOtaSourceTest 缺少官方原始 libapp.so fixture，未通过原生来源完整测试。已支持 TURBO_OFFICIAL_LIBAPP；缺料保持失败，不跳过检查。
- ApkAssemblyTest.py 中子进程硬编码 python3；本机 python3 是 WindowsApps 别名，子进程 exit 9009，该现有测试未通过。
- TtsManifestTest.py 返回 OK(skipped=1)；BackgroundManifestTest.py 运行4项、其中1项跳过。不能把跳过项视为通过。
- scripts/check-source.mjs 全库检查尝试超过工具执行预算，未取得完整通过结果。
- 未执行完整 build.sh、签名、安装、手机/眼镜操作或固件刷写。

## 维护限制

当前只有 static-1.0.5 基线与一个显式 adapter，新版本语义改变仍需增加代码 adapter与人工审计。Profile批准字段依赖仓库代码审查，不能作为防篡改认证。审计 CLI exit 0只表达静态匹配，不能替代 packagingAllowed 或设备验收。

发布依赖本地同文件系统硬链接；不支持的文件系统会失败关闭。APK与收据分别原子出现，不能保证两个文件跨进程崩溃时具备事务性。发布失败请检查结果并使用新构建目录重跑，不承诺同一构建结果重试幂等。

维护步骤见 [HOST_UPGRADE_MAINTENANCE.md](HOST_UPGRADE_MAINTENANCE.md)，阶段范围见 [HOST_UPGRADE_PLAN.md](HOST_UPGRADE_PLAN.md)。

## 1.0.7 版本适配与实际基线验收记录（2026-10-21）

1. **样本来源与身份**：
   - 提取自真实连接设备（YAL-AL10），包名 `com.rayneo.venus.pub`，版本 `1.0.7`，versionCode `230`，架构 `arm64-v8a`。
   - 文件路径：`artifacts/android-rayneo-device-107-extract/base.apk`。
   - 文件大小：409,041,471 字节；SHA-256：`30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91`。
2. **符号重定位与基线契约**：
   - 建立 `android-addon/profiles/static-1.0.7.json`（24 项核心契约，标准化摘要 `a2961c06a9109a982386921044551cc982afa935fc67299f1216991dd1a2675c`）。
   - ASR/NLP/Complete 回调重定位至 `Lq8/c$c;`（方法签名与 1.0.5 完全一致）。
   - 通信数据结构与队列重定位至 `LY3/S;` 与 `LY3/V;->o(LY3/S;)V`。
   - 业务单例通信方法重定位至 `LY3/u;->t`（数据包）与 `LY3/u;->u`（文件）。
3. **真实只读审计结果**：
   - 使用 WebStorm JBR OpenJDK 25.0.3 + apktool 3.0.3 对上述真实提取包运行 `audit-host.mjs`。
   - 结果：`staticBaselineMatch: true`，`packagingAllowed: true`，36 项全量匹配（12 类 + 16 方法/字段 + 4 枚举/常量 + 4 手工未检查项标记），0 缺失，0 改变，0 歧义，CLI 退出码 0。报告生成于 `artifacts/android-rayneo-device-107-extract/audit-107-matched.json`。
4. **Java 插件反射解耦与多版本自适应**：
   - 新增 `android-addon/src/com/turboio/addon/HostSymbols.java`，运行时探测 `Y3.u`，自适应解析 1.0.5 与 1.0.7 混淆符号与方法名。
   - 86 个 Java 源码文件通过 Android 36 SDK 与 JDK 8 目标编译。
5. **原生库 OTA 来源重定向验证**：
   - 1.0.7 `libapp.so`（SHA-256: `a35159a82c0916f62ed6c4da271a40217300cc6678b29c54f2591d9fb592a45c`，大小 46,465,968 字节）在偏移 `0x88512e` 与 `0xe1e48` 完美匹配 Dart 序列化升级 URL 布局。
   - `official-ota-source.mjs` 完成多版本注册与无损替换算法验证，`OfficialOtaSourceTest.mjs` 测试通过。
6. **自动化回归套件通过**：
   - `HostProfilesTest.mjs`（双版本契约与防篡改）PASS。
   - `HostBuildTest.mjs`（双版本构建模拟与 hook 规划）PASS。
   - `OtaHooksTest.mjs`（多版本 smali 包装器）PASS。
   - `OfficialOtaInputTest.mjs` 与 `OfficialOtaSourceTest.mjs` PASS。
   - Python 权限清单测试 PASS；`git diff --check` PASS。
7. **剩余限制声明**：
   - 当前工作仅完成 1.0.7 的静态契约提取、离线重定位、Java解耦、Hook适配、只读审计与多版本自动化测试回归。
   - 尚未在新版 1.0.7 上进行重打包签名后的真机安装测试与眼镜端固件刷写验收。
