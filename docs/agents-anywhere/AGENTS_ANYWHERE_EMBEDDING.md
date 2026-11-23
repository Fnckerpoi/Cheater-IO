# Agents-Anywhere 原生嵌入集成

## 已确认范围

用户确认：将 Agents-Anywhere 原生 Android 界面编入 Turbo-IO 同一个 APK；两个项目保持独立，并保留各自独立构建能力。雷鸟助手首期支持发送任务、查询进度和结果、同一会话续聊。Server 与电脑端 Connector 保持现有远端部署。取消任务及审批不属于本轮新增语音能力。

## 当前基线

- Turbo-IO 当前 Android 模块为 Java 附加模块，经 D8 和宿主 APK 重打包交付，并非 Gradle 应用。
- Agents-Anywhere 位于根目录下独立 Git 仓库，调查基线为 c8af4cac74ab98e2a15fb9aab205d011c346d0ab。其 Android 项目当前为 Kotlin/Compose 的单一 application 模块。
- Turbo-IO 已有 DshClient/ToolClient 的发送、查询和续聊代码；本次基线包含用户已有未提交修改，不能覆盖。
- 既有 unknown_delivery 保护、目标/消息归属校验及会话绑定不得因集成放宽。

## 实施与验收任务

1. 冻结嵌入构建产物、资源与 Manifest 合并、宿主入口、账号与会话桥接契约。
2. 分别实现 Agents-Anywhere 嵌入构建和 Turbo-IO 宿主适配，双方通过明确接口协作。
3. 保留独立应用构建，并验证新的联编路径；独占执行共享产物构建。
4. 覆盖发送、查询、续聊及未知送达、身份变化和迟到回调的聚焦回归。
5. 复审源码、记录构建命令/退出码/产物哈希；分别报告本地检查、APK 构建、设备启动和远端任务链路证据。

## 已冻结技术契约 v1

- 独立构建目标 `:app:assembleEmbeddedDebug`，保留原 `assembleDebug`；载荷打入 `assets/turboio/aa/aa-embedded-payload.apk`，不单独安装。
- 宿主 framework Activity 使用隔离类加载器和专属资源 Context，避免和宿主混淆后的 Kotlin/AndroidX 冲突。此路线仍需资源与生命周期运行验证。
- `com.agentsanywhere.embedded.PayloadEntry` 提供静态 `create(Activity, Context, Bundle, Object): View`、`lifecycle(View, String, Bundle)`、`dispose(View)`。创建时完成 ON_CREATE；宿主转发 start/resume/pause/stop/save/destroy，dispose 幂等。
- 宿主桥 `call(String, Bundle): Bundle` 提供 auth、selection 和 dsh 操作。凭据只在进程内传递，不进入 Intent、日志或 AA 独立持久化。
- 宿主唯一管理身份与语音会话绑定；浏览页面不改变绑定，显式选择才提交。发送/状态查询经过原有 DshClient 守卫。
- AA 负责载荷构建与原生界面；Turbo-IO 负责加载器、Activity、桥和独立装配审计。原 PUBLIC 仅更新 classes4.dex 的审计保持不变。
- 独立嵌入装配验证载荷 hash/ABI，并核对宿主原资源和其他未授权 ZIP 成员保持原样。

## 交付与分层验证记录

### 1. 产物清单与身份

- 宿主底座：`artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk`（SHA-256 `d39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0`）。
- 独立版 APK：`Agents-Anywhere/android/app/build/outputs/apk/debug/app-debug.apk`（48,637,977 字节，SHA-256 `a83e1766d049dfc4cba5132d81f11d473d6df4536de138b5a33462aa904386ba`，含 28 个原生库，独立构建能力完全保留）。
- 嵌入载荷 APK（终版，含会话守卫修复）：`Agents-Anywhere/android/app/build/outputs/embedded/aa-embedded-payload.apk`（20,395,723 字节，SHA-256 `ab2cb7e393393dba46bc03d363162f16a839eed1c11e1d5f1a5df1ae1a225030`，0 个原生库，1476 成员与原始 embeddedDebug APK 逐哈希一致）。
- 载荷校验清单：`Agents-Anywhere/android/app/build/outputs/embedded/payload.json`（SHA-256 `a3e10b44bfa5c97f4737457ea59cbc37a7a69ab8adf2ec7e134d411ab7341f44`，四字段与载荷哈希匹配）。
- 宿主附加 DEX（终版）：661,080 字节，SHA-256 `fb92be756c0812444ae611906aa9d276e21740947a7c87b1f6b0e25b64fe213d`。
- 联编未对齐 APK（终版）：`artifacts/embedded-aa-bridgefix-20261026/TurboIO-AA-embedded-unsigned.apk`（446,323,212 字节，SHA-256 `988ea1adfd22cab1e27acf822eec36c9b6bef6f6de4f966fabc8a20e3a8b5468`）。
- 联编对齐 APK（终版，zipalign -P 16）：`artifacts/embedded-aa-bridgefix-20261026/TurboIO-AA-embedded-aligned-unsigned.apk`（446,325,323 字节，SHA-256 `d6233749526c70ad13fdd8e46b3c4d7c0423e25c8a971fe553505a599832d27f`）。
- 装配报告：`artifacts/embedded-aa-bridgefix-20261026/assembly-report.json`（宿主 3976 成员逐字节一致、resources.arsc 一致、Manifest 仅新增未导出 AaEmbeddedActivity、APK 内 classes4.dex 与构建 DEX 逐字节相等、zipalignVerified=true）。
- 旧候选（已被终版取代，保留作对照）：`artifacts/embedded-aa-build-20261026/`（载荷 `adbf9f81…b600`，不含会话守卫修复，不用于验收）。

### 2. 验证层级与证据

| 层级 | 判据与命令 | 结果 | 证据说明 |
|---|---|---|---|
| 宿主 Java 编译 | `javac -source 8 -target 8 -cp .../android.jar` 全部 production 类 | 通过（exit 0） | 宿主新增 AaPayloadLoader、AaEmbeddedActivity、AaHostBridge、AaBridgePolicy 与入口接入 |
| 宿主桥策略测试 | `java AaBridgePolicyTest` | 47 项通过（exit 0） | 覆盖 STALE_AUTH、STALE_SELECTION、会话归属校验、newSession 显式空 anchor、unknown_delivery 判定 |
| 宿主派发流测试 | `java AaDispatchFlowTest`（真实 DshClient + 模拟 Transport） | 25 项通过（exit 0） | 浏览 B 拒发绑定 A（HTTP 0 次）；正常提交后 revision 更新可继续 status 与续聊；guard 在 DshClient.LOCK 内、HTTP 在锁外 |
| AA 载荷行为测试 | `:app:testEmbeddedDebugUnitTest --tests WorkbenchPolicyTest` | 9 项通过（JUnit XML：failures=0 errors=0） | generation=0 重启可用、浏览 B 不可发 A、精确父级/catalog、合法 revision 变化接受、epoch 迟到保护、草稿清除条件、newSession 显式契约 |
| 载荷来源门禁 | `scripts/verify_payload_gate.ps1` | 通过（exit 0） | 独立版 native=28、原始 embedded native=28、载荷 native=0、1476 成员逐哈希一致、sidecar 严格四字段；篡改 classes.dex 的反向用例被拒绝（exit 1） |
| 嵌入装配测试 | `python EmbeddedAssemblyTest.py` | 3 项通过（exit 0） | 覆盖底座哈希强校验、META-INF 签名剥离、非法输入拦截 |
| Manifest 注入校验 | `python embed_manifest.py` 双向解析比对 | 通过（exit 0） | 宿主 800 节点全保留，新增 2 节点（AaEmbeddedActivity），exported=false，注入幂等 |
| AA 载荷构建 | `:app:embedPayloadSidecar` | 通过（exit 0） | Temurin 17 构建；ZIP 重写剔除 lib/*.so，排除 28 个原生库；依赖内部是否触发 JNI 加载尚未运行验证 |
| AA 独立构建 | `:app:assembleDebug` | 通过（exit 0） | 产物包含 28 个原生库，确认本轮独立构建保留原生库；未对终端、编辑器和扫码作运行验收 |
| 最终 APK 装配 | `package-embedded.py` | 通过（exit 0） | 3976 个宿主成员逐字节一致；resources.arsc 双向哈希一致；变化仅 classes4.dex、AndroidManifest.xml 及 2 个 asset |
| ZIP 对齐检查 | `zipalign -P 16 -f 4` 与 `zipalign -c -P 16 4` | 通过（exit 0） | 通过未压缩原生库的 16 KiB ZIP 对齐检查；不证明 ELF 对齐或设备兼容性 |

### 3. 关键设计决定

- **隔离载荷而非资源合并**：宿主打包流程使用 `apktool --no-res` 并原样保留宿主 `resources.arsc` 与 `res/`，Windows 下无法整体重解包再打包（存在大小写重名成员）。因此不合并资源表，而是把原生界面作为独立 APK 载荷放进 assets，用隔离类加载器与专属资源 Context 运行。
- **载荷零原生库**：宿主与载荷存在原生依赖加载和兼容风险；本轮未证明宿主实际加载了哪些库。嵌入变体在复制阶段重写归档剔除 `lib/*.so`；因 AGP 8 的 `android.packaging` 即使写在 buildType 闭包内也作用于全部变体，排除配置一律不写入 Gradle，改为归档重写，独立版因此完整保留 28 个原生库。
- **宿主唯一认证与派发权威**：凭据只在进程内经宿主桥传递，不进入 Intent、日志或载荷持久化；发送、状态查询与续聊均经过既有 DshClient 守卫。
- **绑定与浏览分离**：页面浏览会话不改变语音绑定，只有显式选择才提交，且提交前在锁内重验配置版本与身份世代，页面过期操作返回 `STALE_SELECTION` 或 `STALE_AUTH` 而非误发。
- **原 PUBLIC 更新流程不变**：仅更新 `classes4.dex` 的严格审计与固定底座校验保持原样；嵌入装配走独立入口与独立审计。

### 4. 明确未验证层级（不可替代）

- **未签名**：产物为 unsigned APK，生产安装需使用对应 Keystore 执行 `apksigner`，本轮未接触证书凭据。
- **未在真机上安装**：宿主基于 `PackageManager.getResourcesForApplication` 的隔离资源上下文与 ComposeView 生命周期在特定 OEM 设备上的兼容性，需要真机验证。
- **未向远端发起真实任务**：首期确认的发送、状态查询与续聊链路已接入，并受 `expectedRevision`、`expectedAuthGeneration` 及既有 `unknown_delivery` 保护，但本轮未向真实 Connector 发送测试任务。

## 状态

实现、聚焦回归（宿主 47+25 项、AA 9 项）、独立构建、宿主联编与最终复审已完成，终版产物位于 `artifacts/embedded-aa-bridgefix-20261026/`。会话守卫修复已包含在终版载荷与 DEX 中：浏览会话与语音绑定不一致时拒绝发送；正常提交后的 revision 变化被接受并刷新快照，可继续查询与续聊；generation=0 重启状态可用；设置关闭后触发页面刷新。以上为逻辑层证据。签名、设备启动、隔离资源上下文与真实 DSH 任务往返仍未验证，不能据此宣称工作台在设备上可用。
