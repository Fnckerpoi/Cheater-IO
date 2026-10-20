# SCADA 事实字幕：真机验收操作手册

## 目标与判定边界

本手册覆盖 Turbo-IO Android 扩展中的单设备 ESP32-C3/BMP280 BLE 事实字幕。RSSI 需现场标定为约 1 米近距代理，不代表精确测距。设备退出或最后一条近距有效读数达到 10 秒时，软件应停止接受/发送旧事实并提交眼镜 type 3 关闭请求；这不构成镜片在 10 秒内物理清屏的硬实时保证。Android 调度、深睡、主线程阻塞、断连及 SDK 已排队帧均可能延迟实际清屏。

读数只经过 BLE 认证、字段检查、防重放和新鲜度门控，不调用 AI、不录音、不走云端字幕或转写。用户必须主动启动扫描。退出状态分为 requested、uncertain 和 user-confirmed；SDK 提交成功不等于镜片已清除。

## 当前环境门禁

仓库环境记录 artifacts/android-tmig1/ENVIRONMENT-EVIDENCE.md 显示：

- ADB 手机在线，Android 10/API 29，RayNeo 宿主 1.0.5 (201)。此前未安装、卸载、清数据或刷写固件。
- 已安装 TurboIO 签名证书摘要是 0E88A8EFCCD903455B0BC77B7DCAAFB146CAA14BD7F31A6A660B8497D94FEF8E，但仓库没有匹配私钥或 keystore。不得用未知签名覆盖同包应用或卸载现有应用。
- 既有打包脚本要求固定原始宿主 APK SHA-256 770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3；现有记录未找到该 APK。未核验第三方 APK 不能替代。
- Android SDK platform/build-tools 已提取，但适用 SDK 许可尚未接受，许可目录未创建。当前 javac + android.jar 源码编译不需要运行 SDK build-tools；不得把它说成 APK 构建或设备验收。
- ADB 手机在线不代表 BMP280 传感器或眼镜链路已准备。须确认目标传感器供电、凭据通过安全渠道配置、眼镜已连上且无其他显示任务。

解除安装门禁：由设备所有者提供匹配应用签名的安全构建环境及正确原始 APK，按适用流程接受 SDK 许可后再组装/签名。签名私钥、AES key 和 nonce prefix 不写入仓库或验收日志。无法恢复签名时，改用经审查、可复原的独立测试设备/包身份；不要覆盖或卸载用户现有 App。

## 无设备写入的自动检查

在 Turbo-IO 根目录运行 focused 测试；产物仅放入临时目录：

    & ./android-addon/tests/scadacaption/RunTests.ps1 -JavaBin 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin'
    if ($LASTEXITCODE -ne 0) { throw 'SCADA facts-caption tests failed' }

当前基线为七组全部通过：packet codec、replay guard、proximity、durability/time 24 项、caption/controller 200 项、NavCore 20 项、NavSessionPolicy 15 项；runner 还检查时间戳转换、字幕专用接线、replay 初始化提交和 cloud socket 隔离。测试会变化，实际打印和退出码为准。

全部 Android 扩展 Java 源码类型编译：

    $classes = Join-Path $env:TEMP ('turboio-scada-javac-' + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $classes | Out-Null
    $sources = @(Get-ChildItem android-addon/src/com/turboio/addon/*.java | ForEach-Object FullName)
    & 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin/javac.exe' -encoding UTF-8 -source 8 -target 8 -cp artifacts/tools/android-sdk/platforms/android-36/android.jar -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Android Java source compilation failed' }
    "compiled $($sources.Count) Java sources"

JDK 25 对 Java 8 source/target 会有兼容性警告。退出码 0 仅代表源编译通过，不代表 D8、APK 组装、签名、安装或端到端验证。可运行 git diff --check -- android-addon 检查空白错误。工作树已有其他未提交和未跟踪内容，禁止为验收清理它们。

## 设备准备清单

每轮建立唯一证据目录，例如 artifacts/scada-caption-acceptance/2026-10-19-run-01/。不要保存明文密钥、nonce、令牌、账号隐私或个人通知。

- 记录手机型号、Android/API、TurboIO APK 版本/哈希/签名证书摘要、RayNeo 宿主版本、眼镜型号/固件、ESP32 板卡/固件和 BMP280 型号。
- 通过获准、可回退的安装路径安装测试 APK；保存安装前后 package/version/signature 身份。确认不会覆盖无关生产数据。
- 确认 BLE、附近设备/定位、通知权限，以及 App 后台活动用户设置；初始保持扫描关闭。
- 确认 ESP32 传感器供电、测量稳定；使用安全渠道配置同一实验凭据。日志只记配置成功和匿名短指纹，不记密钥。
- 确认眼镜已连接，镜片无导航、云字幕、测试显示或其他任务。
- 准备卷尺和固定标记。固定手机/传感器位置与天线方向，记录人体遮挡、室内/室外和多径条件。
- 录像镜片，同时采集 Android elapsed realtime 单调时间、RSSI、counter、near、scan generation、匿名 owner/epoch/SID、type 5 提交、SDK 回调、type 3 请求及状态。墙上时钟不用于计算超时。

没有真实 BMP280 或获准的可控传感器源时，停止设备链路测试；模拟读数不能证明 BLE、RSSI 或镜片链路。

## RSSI 标定步骤

1. 确认眼镜显示通道空闲，用户手动启动 SCADA 扫描。
2. 在 0.5 m、1.0 m、1.5 m 和目标场景退出边界采样；每个距离至少记录 30 个 RSSI 样本，并至少重复 3 轮。固定朝向与环境，记录遮挡变化。
3. 报告中展示中位数和离散范围，不要挑选单个最强样本。选择进入阈值与回差，使目标约 1 m 场景稳定进入、越过退出边界后稳定离开。
4. 保存原始样本、距离/天线布置照片或草图、阈值和选择理由。更改阈值后重做完整进出场测试。

通过条件：多轮约 1 m 样本达到预先定义的进入稳定性；退出区域能稳定离开；进入需 3 个连续有效合格包；RSSI 低于“进入阈值减回差”才离开，等于边界保持 near。默认 -70 dBm 不得直接写成一米值。无法找到满足现场要求的阈值时，标定失败，需调整天线/摆位或产品要求。

## 字幕设备实验矩阵

除并发项目外，每项先确认眼镜显示通道空闲。每项记录预期、实际、判定和证据编号。

| ID | 操作 | 通过判据 |
| --- | --- | --- |
| A1 未启动 | 打开页面等待 | 不扫描、不出现 SCADA 字幕或旧缓存 |
| A2 权限/凭据失败 | 拒权限或未配置凭据后启动 | 不发布事实，状态明确失败，无活跃扫描/会话泄漏 |
| A3 近距进入 | 配置有效，位于标定近距 | 仅连续三包认证且防重放通过后显示 |
| A4 数值正确 | 对比 BMP280 可信读数 | 温度 °C、气压 Pa 和数值正确；新 counter 持续更新 |
| A5 RSSI 退出 | near 后移动越过退出边界 | 同一转换停止接受新事实并提交 type 3；记录镜片实际消失时延 |
| A6 静默过期 | near 后停止传感器广播 | 最后近距采集时刻 +10 秒后调度执行时，拒绝过期文字并提交关闭请求；另测物理清屏时延，不判为硬实时 SLA |
| A7 远距/伪包/重放 | 远距或协议规定的无效、旧 counter 测试 | 不续期、不恢复字幕 |
| A8 重新靠近 | 退出/过期后再进入 | 仅新鲜连续三包重入；旧缓存不恢复 |
| A9 手动停止 | 显示期间点 Stop 或通知停止 | 停止扫描/本地发送、释放 BLE lease、请求自身 owner 关闭 |
| A10 通道竞争 | 导航、测试、CloudCaption 准备/活跃、卡片传输时启动 | 返回 busy；既有 owner 的 SID、更新、停止不被改变 |
| A11 断连/撤权 | 断开眼镜或撤 BLE 权限 | 显示 requested/uncertain 等真实状态；不虚报物理清除，不自动恢复旧值 |
| A12 熄屏/省电 | 获准地短时熄屏并连续记录 | 报告到期、关闭请求、镜片消失的各自时间分布，不把单次结果当系统保证 |
| A13 零 AI | 覆盖完整 SCADA 运行窗口 | 无录音、CloudCaption websocket/HTTP、模型请求；显示内容仅来自 BLE 事实 |

清除证据用语：requested 表示已提交关闭请求；uncertain 表示拒绝、超时或断连导致结果未知；user-confirmed 表示操作员实际看到镜片退出后点了人工确认。人工确认只覆盖该次观察，不等于协议自动确认。

## 验收判据

**本地软件通过：** focused 测试与 84 个源码编译通过；覆盖 9,999 ms 与 10,000 ms 边界、扫描轮次/采样时间顺序、发送前 expiry、near exit/stale 重置、owner 隔离、type 3 非自动确认、A→B→A 与清凭据后保留 replay 高水位、损坏状态拒绝启动。

**设备功能通过：** 目标手机、ESP32-C3/BMP280 和 RayNeo 眼镜上实测 RSSI 标定、数值/单位、退出/静默后不再发新事实并提交 close、重新进入需新鲜三包、并发 owner 不互扰、零云/音频/AI。镜片实际退出须有录像或现场签核和关联日志。

**物理 10 秒清屏 SLA 尚不可判通过：** 缺少 type 3 设备回执语义和在途帧顺序保证时，只能判“软件按到期任务拒发旧事实并请求关闭”。若产品要求最后近距读数后镜片必须在 10 秒内物理清空，须用多轮真机和断连/省电场景定义并验证可接受时限，且取得协议/固件保证；现有 Android 调度不是硬实时。

任一失败时保留原日志，不覆盖前轮证据。分类为代码回归、权限/配置、安装签名、BLE、RSSI、SDK/协议或系统调度，并写明复现步骤与下一步。

## 证据目录建议

    artifacts/scada-caption-acceptance/YYYY-MM-DD-run-NN/
      README.md              操作人、版本、范围、结论
      preflight.json         package/version/cert hash、权限和连接状态（脱敏）
      calibration.csv        elapsed_ms,distance_m,rssi_dbm,orientation,obstruction,round
      events.ndjson          elapsed_ns,counter,rssi,near,scan_epoch,owner_tag,sid_hash,event
      result-matrix.csv      A1-A13 预期/实际/结果/证据引用
      photos/                距离、设备和天线布置
      lens-video/            显示、退出、静默及镜片消失录像
      hashes.sha256          本轮证据文件哈希

只写短期匿名标识与 SID hash，不写完整蓝牙地址或 token；不录密钥、录音、账号或个人通知。排除旁观者和隐私信息。每轮独立建目录并标明冷启动、同凭据重入或跨凭据 replay 场景。

## 当前验收状态与待办

截至 2026-10-19，独立源码复审提出的五项缺陷已修复，针对性回归和独立复审确认在所审代码路径内闭环：旧无 SID type 4 被忽略；type 5 SDK 提交前重验会话与期限；初始化标记/计数器缺损 fail-closed；SCADA 扫描输出仅为事实字幕；云事件队列按 socket 隔离。最新本地验证七组全部通过：字幕 200 checks、持久化/时间 24 checks、源码接线检查通过；85 个 Android Java 源码对 Android 36 API 编译通过，diff check 通过。独立复审没有重跑测试，采用上述验证证据，且仅覆盖五项补丁，不证明设备行为。

仍需注意：无 SID type 4 现在被忽略；若眼镜固件依赖此类事件自动结束字幕，须在眼镜上验证。type 3 不能撤销已交入 SDK 队列的帧，也不是镜片物理清屏确认。ADB 检测到一台 Android 10/API 29 手机；仍未安装、卸载、清数据或刷写设备。未做 ESP32/BMP280 近距实验、RSSI 标定、眼镜字幕录像或物理退出验证。

目前阻止端到端安装验收的证据：环境记录指出匹配当前已安装 TurboIO APK 的签名私钥不在仓库内；目标版本固定哈希原始宿主 APK 未找到；Android SDK 许可尚未接受。解除这些门禁前，不执行 D8/SDK build-tools/打包/签名/安装。手册不替代用户或设备所有者对签名环境、SDK 许可和测试安装路径的授权。

- [ ] 获取与现有 TurboIO 安装匹配的签名构建环境和正确原始宿主 APK；否则采用经审查的独立测试设备/包身份。
- [ ] 按适用流程接受 Android SDK 许可；随后才运行 SDK build-tools、D8、APK 组装和签名。
- [ ] 确认传感器、眼镜及可安全复原的测试设备齐备，并确认用户数据备份与回退方式。
- [ ] 执行 RSSI 标定和 A1-A13，保存脱敏日志与镜片录像。
- [ ] 独立审阅证据，区分源码/本地测试、设备功能和实际物理清屏时延三个判定层级。
