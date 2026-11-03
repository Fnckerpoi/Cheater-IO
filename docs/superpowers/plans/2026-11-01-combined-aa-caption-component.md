# AA 看板与实时字幕统一 HUD 开关实施计划

> 状态：供用户审阅；本文件是实施计划，不代表功能已实现。目标分支：android。

## 目标

在 Turbo-IO 的 Agent-Anywhere 设置中提供一个“眼镜显示 AA 看板与实时字幕”统一开关，默认开启。启用时，将实时 ASR 字幕和 Agents-Anywhere 状态/结果合成到眼镜固件唯一的文本流；关闭时停止该复合组件的眼镜显示。手机端 AA 监听、看板状态和结果仍正常更新。

根分支当前只有 ASR 分发和 AA 设置/状态页，尚无复合 HUD 模型或显示协调器。计划将复合 HUD 前置实现一并纳入，不依赖脏 worktree 先行合并。位于 .agents/worktrees/dsh-proactive-report 的现有代码和计划仅用于理解接口约束，实施时须在 android 根分支实现并逐项复核，不能将未提交 worktree 文件视为既有基线。

## 已定设计与硬约束

- UI 只提供一个组合开关，不拆为字幕、AA 看板两个组件，也不声称有独立镜片硬件区域。固件只有一个 Type 7/5/3 文本流。
- 首次读取默认值为开启；用户修改写入 turboio_settings SharedPreferences 的独立布尔键。键建议 dsh_composite_hud_enabled，勿复用字幕同步或 AA 启用键。
- 单帧最多 5 行、300 UTF-16 代码单元、每行最多 100 字符；ASR 占 1–2 行且总量最多 120 字符，AA 与分隔符使用剩余预算。按 Unicode 码点截断，清理控制符和嵌入换行。
- AA 显示协调器是唯一 lease/owner。ASR 只能在已存在且有效的 AA 授权显示路径中更新同一 owner/epoch/SID；ASR 回调不得自行 reserve、claim、reopen 或开始眼镜通道。
- 锁顺序 display -> DshClient.LOCK -> core；不反向持锁、不在 core 锁内回调上层/调用外部接口。遵守 NavGlasses 对 NativeNavigation、ReaderBridge、MusicBridge、NativeTransfer 的互斥。
- 保持 GLASSES_IDLE_MS=600000L、REOPEN_DELAY_MS=8000L、NavGlasses 700ms 更新节流；不得以设置开关绕过停止确认、uncertain 状态或 producer guard。
- 关闭开关：递增 generation/取消待处理字幕结算，使旧 ASR、旧 timer 无法再发帧；只请求清理由本 display owner+epoch 持有的 HUD lease，按现有 clear/stop 确认流程处理。不得强制释放其他来源或把“请求关闭”说成“已确认关闭”。不停止 AA 手机监听、不清空 AA 结果，不取消远端任务。
- 重新开启：持久化开启后恢复组合帧生成；当前已有 AA 授权显示会话时可按原 owner 流程继续，未有 AA 授权或 lease 时保持手机端可用，等待 AA 的显式显示授权路径；ASR 本身仍不能开通道。

## 当前根分支接缝

- android-addon/src/com/turboio/addon/TurboAddon.java:120-126：dispatchAsr 串行调用官方 ASR、语音处理及 onAsr；复合 HUD 转发应接在原调用链上并保持顺序/原始官方回调。
- android-addon/src/com/turboio/addon/TurboAddon.java:140-152：现有 onAsr 负责语音请求状态，不等同于显示订阅；新增显示转发须保持职责隔离，检查组合开关和活动显示实例。
- android-addon/src/com/turboio/addon/DshSettings.java:204-248：Screen 构造和 AA 同步卡片；设置项放在该 AA 设置页中，避免另造第二个字幕开关。
- android-addon/src/com/turboio/addon/NavGlasses.java:42-58：已有 owner/epoch reserve/begin/offer/stop/confirmIdle API；保留其所有权与清理语义，除非集成验收证明接口有缺口。
- 根分支没有 DshDashboardModel、DshProactiveDisplay、DshCompositeHudModel、复合 HUD 测试或相应 runner。创建集成 runner/测试目录应沿用仓库现有 javac/PowerShell 习惯，不假设 worktree 中脚本已在根分支。

## 实施任务

### 1. 新增纯 Java 组合格式与字幕状态模型

文件：
- 新建 android-addon/src/com/turboio/addon/DshDashboardModel.java（若复核发现根分支已有等价模型则扩展既有模型，禁止复制职责重叠的看板状态机）。
- 新建 android-addon/src/com/turboio/addon/DshCompositeHudModel.java。
- 新建 android-addon/tests/DshDashboardModelTest.java 和 android-addon/tests/DshCompositeHudModelTest.java。

步骤及验收：
- 定义可注入单调时间的纯 Java 输入/快照契约；模型不得依赖 Android、Handler、偏好存储或 NavGlasses。
- 看板快照承载状态、结果摘要、过期/陈旧标记；纯快照不得因 compose/render 改变 observation/freshness 时间。
- 组合模型处理 ASR IDLE/STREAMING/FINALIZED、会话 token 和结算时间；部分识别替换前一部分，最终文本在 5000ms 内保留、到期只清 ASR。
- 格式化结果符合上述行数/字符预算；边界 emoji/代理对不得切裂；对空字幕、空看板、长结果、控制字符、旧会话回调有确定行为。
- 测试覆盖状态转换、4999/5000ms 边界、陈旧会话拒绝、刷新快照无时间副作用、AA 结果保留、ASR-only 清除、Unicode 和最坏输入预算。

### 2. 根分支加入唯一显示协调器与 AA 生命周期

文件：
- 新建 android-addon/src/com/turboio/addon/DshProactiveReport.java（若 AA 轮询/采纳层在根分支已有等价实现，扩展而非复制）。
- 新建 android-addon/src/com/turboio/addon/DshProactiveDisplay.java。
- 新建 android-addon/tests/proactivedisplay/DshProactiveDisplayTest.java 及所需 fake ports/调度器。
- 新建或扩展 android-addon/tests/run-proactive-integration.ps1。

步骤及验收：
- AA 的有效轮次/票据是输出眼镜看板的授权依据；提供手机端监听和报告，但只有 AA 显式显示路径可创建 lease。
- display 协调器独占 owner、epoch、SID、dashboard/composite 模型及 generation。看板更新和 ASR 更新都合成最新的两段并更新同一 lease。
- 对开始/停止、轮次变化、结果完成、过期、uncertain lease、用户确认关闭实施 display -> host -> core 顺序与 epoch/ticket 验证。
- 关闭 HUD 组件时，撤销 HUD 发帧资格并请求其当前 lease 正常清理；保留 AA 轮询、状态、手机结果及其 listener。状态若仍在 stopping/uncertain，UI/状态不声称镜片已清屏，也不重开。
- 集成测试断言 AA 状态变化保留字幕、ASR 更新保留 AA 结果、结算只移除字幕、owner/epoch/SID 不变且不多开 Type 7、旧票据/旧 timer 不写屏、竞用/uncertain 时不触碰别的 owner。

### 3. 在 ASR 串行分发链路接入 HUD

文件：
- 修改 android-addon/src/com/turboio/addon/TurboAddon.java。
- 修改 android-addon/tests/AaDispatchFlowTest.java 或新建 android-addon/tests/DshAsrHudDispatchTest.java。

步骤及验收：
- 在 dispatchAsr 的原始 ASR handler 成功后将文本、finished、session 转交给已存在的 HUD display；按现有主线程/serial 规则转发。
- 复用调用方/应用上下文已有 display 单例访问，不因 ASR 创建 DshProactiveDisplay，不从 TurboAddon 调 NavGlasses。
- 关闭开关、空回调、ASR handler 异常、mode/bypass 及旧 session 均不能造成眼镜写入；官方 ASR 回调和 VoiceSpeech 路径维持原顺序/行为。

### 4. 加入默认开启的统一设置开关

文件：
- 修改 android-addon/src/com/turboio/addon/DshSettings.java。
- 新建纯 Java android-addon/src/com/turboio/addon/DshCompositeHudSettings.java（若需要隔离默认值/键读写以便 JVM 测试）；新建 android-addon/tests/DshCompositeHudSettingsTest.java。

步骤及验收：
- AA 设置页添加唯一清晰命名的组合开关，说明它控制“眼镜上的 AA 状态/结果与实时字幕合并显示”；默认值 true，写入独立 key。
- 开关状态变化先持久化并检查提交结果，再以 generation 命令通知 display；旧回调不能覆盖新设置。设置页重开正确还原值；不存在时为开启。
- 关闭仅影响眼镜组合 HUD；AA 状态、手机端结果、远端任务与 ASR 官方处理均继续。
- 复开不擅自抢占空闲/被占用通道，不自动重放过期 ASR；等待有效 AA 显示授权后更新组合内容。

### 5. 回归、打包和边界复核

文件：
- 新建/更新 android-addon/tests/run-proactive-integration.ps1。
- 必要时更新根 android-addon/tests/RunDshTests.ps1 以纳入纯 Java suite。

验收：
- 所有纯 Java model/settings suites 和 AA/HUD integration suite 通过；既有 AaDispatchFlowTest、AA 状态/会话测试通过。
- 验证 toggle 缺省开启、显式关闭持久化、重启/重开设置恢复、关闭时 phone AA 仍运行、重新开启须 AA 授权、同 lease 更新、过期字幕清除、所有互斥生产者保护。
- 静态审查无锁逆序、display 外第二 owner、ASR 自开通道、开关关闭误停 AA 监听、uncertain clear 被报告成成功、单帧超出预算。
- 构建 APK 并检查其源/产物对应关系。只有获取到设备镜片读回/截图证据后，才可声称眼镜实际布局、字幕可读性或与厂商 UI 共存通过；纯软件测试和 SDK ACK 不足以证明光学表现。

## 建议执行顺序

任务 1（模型/格式）→ 任务 2（AA 显示协调器）→ 任务 3（ASR 接入）→ 任务 4（设置开关）→ 任务 5（回归与产物核对）。各阶段先添加失败判例，再实现并运行对应 suite。不得并行修改共同的显示协调器/ASR 链路。

## 自审与残余风险

- 计划以用户已选的单一组合开关、默认开启为产品决策；没有拆分开关或独立硬件区域。
- 实施基线清楚地区分根分支与 worktree；AA/HUD 类若复核发现等价实现，实施者应复用，不能重复新增。
- 关闭行为只指定停止本组件写屏并遵守 owned lease 的现有清理确认；实施前应从实际 NavGlasses/显示状态机实现确定精确 clear API 与 uncertain 后恢复条件，不能设计新的无确认释放捷径。
- 物理镜片布局/厂商 UI 共存仍需设备证据；无设备时软件交付结论应限定为 payload/协议级。
