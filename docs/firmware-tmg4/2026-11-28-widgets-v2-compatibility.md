# 仪表盘 widgets_v2 协议与 TMG4.3 兼容性

2026-11-28 · Android 分支 · 雷鸟手机应用 1.0.20（309）· Strix OS 1.0.6.11 基座。本文记录切换到 TMG5 前的 APK 与固件比对。后续配套构建和真机结果见 [TMG5-10611 验收](../firmware-tmg5/2026-11-28-tmg5-10611-delivery.md)。

**当前手机的仪表盘要求眼镜返回 `widgets_v2`，但同一 APK 的 TMG4.3 升级入口选中了一份缺少该扩展的固件。** 本机另有一份同版本号的构建包含扩展，能解码手机生成的示例卡；现装 APK 的固件校验器会拒绝这份新包。由此可以把问题定位到具体配套产物和回执字段。眼镜实际运行哪份 AP、镜片能否显示该卡，仍需设备证据。

## 实物比对

| 对象 | 核验方法与结果 | 对同步的影响 |
| --- | --- | --- |
| 手机现装 APK | 读取 `com.rayneo.venus.pub` 的系统安装路径，在手机计算 base APK 摘要，与上次覆盖安装的签名包逐字节身份相符。版本 1.0.20（309），最后更新时间 17:03:32。安装包 `classes8.dex` 与编译记录的插件 DEX 摘要一致。 | 已排除手机仍装另一份旧 APK 这一解释。 |
| 仓库 TMG4.3-10611 | 包内 AP 包含构建目录的完整模块。ELF 有 `tce_decode`，但布局、消息和配置序列化三个入口仍是原厂字节，三个兼容函数在模块符号表中均缺席，模块也无 `widgets_v2` 字段。 | 有卡片解码器，缺少这套配置生产器。现装 APK 的 TMG4.3 配置固定此包；手机 Download 目录同名 ZIP 实测也是此包。 |
| 当日本地 TMG4.3-10611 兼容构建 | 包内 AP 包含完整新模块，含 `widgets_v2`。逐个解析三个入口的实际分支，目标均与 ELF 对应函数一致；兼容层与解码器源码快照和当前源码一致。 | 包内有生成列表和处理写入的实现。现装 APK 的 DEX 中仅查到旧候选身份；用当前 Java 校验器读取两个真实 ZIP，仓库包通过，新包因大小或摘要不符被拒。 |
| 眼镜实际回执 | 同日只读查询返回旧 `widgets` 列表，1 项，首项为数字，`widgets_v2` 缺失。手机保存的版本回读记录（17:06:36 更新）给出 `OsVersion=Strix OS 1.0.6.11`。 | 这次写入前置条件不成立。两个候选共享基座和版本标签，手机暂存的 ZIP、基座字符串都不能确定眼镜内实际 AP。 |

| SHA-256 核对项 | 整包 | 插件 DEX 或固件 AP |
| --- | --- | --- |
| 手机 APK／上次签名包 | `d76e729521020dae37462157f8fa132b7a7c8e457dd77f536913362ad43e329c` | `f3f1652c2206e92aba20afab5806c5ca18b8a325e2f11d19568eb328cc6f5996` |
| 仓库固件／手机暂存 ZIP | `e6dc87a6f948154a8276e1727f154752b3cf6c9120ee945a4bf1963bfb25d708` | `6531f91553ca33c1ee276a492c23f1b0a3957fe3e627eb0189bd68b3b457b50e` |
| 当日本地兼容固件 | `805ce6238e883c5af3d05b97e5e2e7cd3d7d472ee638534dbbf9e346c3d42269` | `68d062e598652e1e685d695309ab1ac7b0a2e56b63cd07e5023934e06acaaa32` |

仓库候选位于固件项目的 `build/tmg4.3-10611`，当日构建位于本机 `%TEMP%/tmg4-arm-build-candidate`。两者均有独立的实际编译清单、模块 ELF 和离线审计。当前构建配置描述下一次如何构建；制成品的证据来自其自身的 ZIP、模块和入口。手机配置按整包以及成员校验，文件改名不能改变结果。[构建入口](../../firmware-research/strix-1.0.6.11-tmg4/build.py#L119) · [APK 固件身份](../../android-addon/src/com/turboio/addon/OtaProfile.java#L122) · [升级入口](../../android-addon/src/com/turboio/addon/OfficialOtaBridge.java#L187) · [整包校验](../../android-addon/src/com/turboio/addon/OtaPackage.java#L37)

## 一次同步如何经过两端

| 阶段 | 手机 | 眼镜及成功条件 |
| --- | --- | --- |
| 读取配置 | 取得独占传输权，向已连接眼镜发 `{"cmd":"dashboard_config","payload":{"version":1,"value":0}}`。Launcher 信封版本 1，请求 type 18、mode 1。 | 回执 type 19、mode 2。手机匹配设备、非零序号和当前命令，接受 `payload.data` 对象或编码了对象的 JSON 字符串。 |
| 识别卡片 | 从解出的配置取 `widgets_v2`，要求数组不超过 32 行，各行有唯一非空字符串 id 和非空 type。只读页面可展示旧结构；发布、更新和移除在校验失败时停止。 | 兼容层在原厂配置上附加按布局排序的身份行，每行含 `id/type/name`。保留原来的 `widgets`、`simple_mode` 和其他配置。 |
| 发送卡片 | `widget_install` 指定 a2ui 类型、id、名称和字符串化的 extras。extras 包含匹配的 widgetId、名称、createSurface、updateComponents 和一个 root Text，其 text 为 `TCE1:` 加 Base64。 | 兼容层检查归属 ID、两处 surface、catalog、根组件及卡片内容，转换为应用服务接受的消息后异步安装。要求连接已配对、当前无更新或在途修改、注册表可写。新增还须有空位。 |
| 读回和可选排序 | 收到 `code=0` 后重读配置。比较目标行，严格保留其余行内容、相对顺序以及列表之外的字段。新卡按选项置顶或排尾，已有卡保留原位置。 | 需要移动时手机发 `dashboard_update`，data 是完整配置的 JSON 字符串。固件校验原生 widgets、simple_mode、各行 id/type/name 和完整排列。成功应答为 `payload.value=0`；手机再次查询并核对实际顺序。 |
| 更新与删除 | 已有卡回读缺 extras 时标记内容未验证并停止后续排序。新卡可核对新增身份及所需位置，内容仍标记未验证。删除使用 `widget_uninstall`，必须读回目标消失且其他配置保留。 | 固件成功回调后刷新注册表，检查目标出现或消失并保存顺序。接受命令、身份进入列表、内容持久化、镜片实际绘制分别验收。 |

手机每步有 20 秒回执期限，断线或超时不会自动重发修改。界面上的“发包／应用回执”是文件传输计数；“Launcher 收包”还会统计其他 Launcher 消息。本例可定位到已解出的配置对象，是因为读到了字段形状。[界面入口](../../android-addon/src/com/turboio/addon/CardUI.java#L84) · [收发信封](../../android-addon/src/com/turboio/addon/CardEnvelope.java#L8) · [状态机](../../android-addon/src/com/turboio/addon/CardTransport.java#L152) · [其他字段保留规则](../../android-addon/src/com/turboio/addon/CardCodec.java#L34) · [计数器与发送路径](../../android-addon/src/com/turboio/addon/NativeTransfer.java#L28)

## 固件如何产生列表、保存顺序和绘制

1. **组成列表。** 查询时关联原厂 dashboard manager。兼容层从当前布局读最多 6 个原生项，保留各项真实 id、名称与版本，从应用服务枚举最多 26 个受管自定义卡，再按已存顺序合并。原生 1–6 对应 weather、todo、schedule、stock、mail、clock。旧数字列表本身没有自定义应用身份、名称或完整混排顺序，手机无法据此恢复完整配置。[枚举与合并](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/native.c#L63) · [注册表约束](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/compat.c#L38)
2. **附加字段。** 序列化器先取原厂结果。配置版本为 1、控制对象和 manager 有效、注册表就绪且合并成功时，追加 `widgets_v2`；成功合并零项时也可生成空数组。控制对象缺失、应用服务尚未运行或刷新失败时，可直接返回原厂对象。字段缺失和空数组在协议中是不同状态。这也是现场仅凭“缺字段”无法反推出固件身份的原因。[序列化条件](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/native.c#L117) · [查询入口](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/native.c#L196)
3. **持久化顺序。** TDC2 保存有序身份、代次和 CRC，交替写两个存储区，写后回读再采用新代次。名单必须是当前集合的完整排列，原生卡的相对顺序随当前原厂布局。已有存储均损坏时注册表仍可展示，写入会被拒绝。TDC2 用于顺序；旧开发者卡的 TDC1 用于固定数值模板；通用卡用 TCE1 表达内容。[排序持久化](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/compat.c#L58) · [两类卡片内容分流](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/native.c#L178)
4. **从卡片字节到镜片。** 手机生成 8 字节 TCE1 头，含标识、组件数、保留位、小端总长；每个组件用 10 字节描述类型、坐标、尺寸、参数和负载长度。文本、位图、进度条、柱图、折线、分隔线、边框共七种编码，图标与图片共用位图类型。两端上限为 2,048 字节、12 组件、4 位图、2 图表、32,768 像素预算。原生根容器为 256×194，内容四边各留 6 像素，坐标与尺寸按偶数网格。镜片渲染入口再次读取匹配 surface 下 root Text 的内容，解码后创建原生控件；位图按高位在前展开。[手机编码](../../android-addon/src/com/turboio/addon/CardCodec.java#L12) · [固件解码与栅格化](../../firmware-research/strix-1.0.6.11/src/dashboard-editor-v1/editor.c#L10) · [原生绘制](../../firmware-research/strix-1.0.6.11/src/dashboard-editor-v1/editor_native.c#L38)

## 离线复验及功能边界

| 验证 | 结果 | 能证明什么 |
| --- | --- | --- |
| 当前 Android 卡片状态机重新编译运行 | CardFlowTest 52 项断言通过 | 覆盖读、安装、回读、排序、移除、超时和缺 v2 的停止路径；测试使用模拟传输。 |
| 当前 Java 编码器 → 当日固件源码快照的 C 解码器 | 示例卡生成 129 字节，C 解码接受，读出 6 个组件 | 这一份真实示例的字节可以互通。在主机运行了相同解码源码，未运行眼镜调度器或显示驱动。 |
| 同例仅改首段文字 | 插入 U+2028 行分隔符或 U+2029 段落分隔符时，Java 均编码成功，C 均拒绝 | 存在可复现的局部文本兼容差异。发生在内容校验阶段，时间上晚于现场的缺字段故障。 |
| 固件排序／持久化测试 | 原生行保留、完整排列、重启、CRC、截断写入和损坏存储用例通过 | 证明排序库在测试存储上的行为。 |
| 新固件的回读粒度 | 序列化行只含 id、type、name，缺少 extras | 当前手机可以核实身份、成员和顺序，单凭回读无法确认同 ID 的内容更新。 |

[Android 状态机测试](../../android-addon/tests/cardflow/CardFlowTest.java#L11) · [排序测试](../../firmware-research/strix-1.0.6.11/src/dashboard-compat-v1/test_compat.c#L10) · [Android 文字限制](../../android-addon/src/com/turboio/addon/BoundedJson.java#L43) · [固件文字限制](../../firmware-research/strix-1.0.6.11/src/dashboard-editor-v1/editor.c#L4) · [本地静态示例预览](../../dashboard-service/examples/dashboard-demo-card.svg)

## 下一步验收

先为含兼容层的候选建立独立、可追溯的发行身份，按现有整包和成员校验流程与 APK 配套，避免两份同名包混用。设备侧先取得能区分 AP 构建的记录，再做只读查询。若已确认运行兼容构建仍缺字段，查应用服务就绪状态、原生布局枚举和注册表合并。“列表为空”只有在实际回执含空数组时才成立。

得到合法身份列表后，再单独授权发送一张示例卡，核对目标身份、其他卡保留和顺序，并由佩戴者核对镜片上的 CPU 25%、RAM 50% 与两条进度条。本次完成离线与只读核验；实机发卡、内容持久化和镜片显示仍待验收。
