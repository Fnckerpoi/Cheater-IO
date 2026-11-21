# 有问必答知识库答案稳定性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpower-subagent-driven-development or superpower-executing-plans. Steps use checkbox syntax for tracking.

**Goal:** 让「有问必答」在 Cortrix 命中可用证据后，优先等到完整的知识库答案，再通过官方回调投递眼镜。

**Architecture:** Android 通过现有回调挂起原提示，用同一个绝对截止时间控制检索、生成和回注。模型成功时替换 answer，模型失败但证据可用时展示有标记的证据摘录。生成速度由后端单独测量和优化，手机以完整答案为交付单位。

**Tech Stack:** Android Java 8、Cortrix MCP Streamable HTTP、Chat Completions JSON、现有 PowerShell/JVM 测试与 ADB 验收。

**Spec:** 本计划落实用户要求，允许更长的等待来保障回复稳定性，实时性的优化交由后端负责。本文中的等待预算是首版计划值，真机数据用于复核。

## 实测基线

- 2026-11-21 14:42–14:46（UTC+08:00），YAL-AL10 上运行下拉版 APK 1.0.20/309。有问必答五轮均发送 namespaces=["obsidian-knowledge"]，Cortrix 均返回 HTTP 200、两条结果、覆盖率 1.0，耗时 184–780 ms。返回证据的业务相关性仍需用真机答案核验。[脱敏真机日志](../../../artifacts/hint-kb-device-acceptance-20261121/live-qa-1442-1446-redacted.log)
- 第 1、2、3、5 轮在命中后 2237–2240 ms 放行厂商原回答，与 2200 ms 模型期限高度吻合。现有日志未记录模型响应头和正文阶段。第 4 轮命中后 1522 ms 收到新提示，旧轮没有投递记录，与序号防串题逻辑一致。
- 雷鸟 AI 助手对照的两轮从最后一次检索命中到最终投递分别为 9.139 s 和 15.708 s。这是普通对话链路耗时，可用于选择首轮实验预算；主动提示的非流式生成延迟需单独测量。

## Global Constraints

- 首版 Android 预算为检索最多 **15 s**、重生成最多 **25 s**、单轮总看门狗 **42 s**。总时限从收到提示开始，包含排队、检索、模型和回注。阶段预算取各自上限与总剩余时间的较小值。25 s 为首轮留出的等待时间，42 s 在两阶段上限之外留 2 s 给调度和回注。
- 维持原厂 voiceAssistant 业务 13 / AssistantType 5。通过主线程单次回注原对象，仅更新 answer 并保留其他字段。保持普通雷鸟 AI 助手的独立对话逻辑和现有选库行为，Cortrix 仍显式传 rerank=false。
- 新提示取代未投递的旧提示。generation、sequence、revision 变化或宿主修改了原提示，旧结果随即失效。缓存只存成功生成且当前配置有效的答案，适用既有问题与 revision 校验。
- 回注文本保持 120 字显示预算，移除控制字符，裁剪保持完整 Unicode 码点。日志只记录阶段、ID、状态、耗时和计数。
- Android 与后端分开验收。保留当前工作树的选库功能及相关测试，按文件审查差异。构建使用独立批次目录并核对安装包身份。固件与新数据通道在此次计划范围外。

## Review Focus

- 模型在 2.2–25 s 内返回完整答案，客户端应仍能回注，见 Task 1。
- 命中两条证据后模型超时或返回无效内容，眼镜收到明确标记的可用证据，见 Task 2。
- 新提示进入、配置变更或旧请求晚到时，眼镜收到当前提示对应的答案，见 Task 1 与 Task 4。
- 网关迟迟未发响应头和响应头已到但正文延迟，两种情况可从日志定位，见 Task 2 与 Task 3。
- 选库失败、完整空结果和部分命中仍可区分，见 Task 2 与 Task 4。

### Task 1 — 延长客户端等待并统一截止时间

**文件**

- [ProactiveHintAugmentor.java](../../../android-addon/src/com/turboio/addon/ProactiveHintAugmentor.java)
- [TurboAddon.java](../../../android-addon/src/com/turboio/addon/TurboAddon.java)
- [HintCoordinatorTest.java](../../../android-addon/tests/HintCoordinatorTest.java)
- [RunProactiveHintTests.ps1](../../../android-addon/tests/RunProactiveHintTests.ps1) 和 [RunHintCoordinatorTests.ps1](../../../android-addon/tests/RunHintCoordinatorTests.ps1)

**接口** 沿用 interceptAndRegenerateAsync(Object hint, McpConnectionStore.Snapshot config, long generation, long sequence, Regenerator regenerator, Delivery delivery) 和 Regenerator.regenerate(String question, String cloudAnswer, KnowledgeRetrieval.Result result, long budgetMs)。在协调器集中定义 RETRIEVAL_BUDGET_MS=15000L、MODEL_BUDGET_MS=25000L、TOTAL_BUDGET_MS=42000L。生产接线使用 android.os.SystemClock.elapsedRealtime，JVM 夹具保留可控时钟。

- [ ] 在 HintCoordinatorTest 增加 slowModelWithinBudget。令检索耗时 1000 ms，模型耗时 16000 ms，断言最终 answer 为模型输出且只回注一次。运行 RunHintCoordinatorTests.ps1，确认原预算会放行原回答。
- [ ] 更新 watchdog 与 stale 用例，验证 42000 ms 触发单次回调、42001 ms 的模型晚到结果无效。用假时钟模拟工作线程排队 36000 ms、检索 1000 ms，断言模型仅获得 5000 ms。连续提示与 revision 变更都断言旧结果无投递和缓存。
- [ ] 实现三段统一预算，从进入回调时计算绝对时限。主线程只负责挂起、时限检查与最终回注，网络继续在工作线程。旧任务在各阶段检查是否仍有效。延长等待期间在测试中观察共用 NETWORK 的两条线程是否阻塞普通对话，出现阻塞时先隔离有问必答执行队列并限制为最新一轮待办。
- [ ] 运行 RunHintCoordinatorTests.ps1，验证慢响应可回注、总时限生效、回退只发生一次且无串题。

### Task 2 — 区分模型故障与检索状态，命中时保留证据

**文件**

- [ProactiveHintRegenerator.java](../../../android-addon/src/com/turboio/addon/ProactiveHintRegenerator.java)
- [ProactiveHintAugmentor.java](../../../android-addon/src/com/turboio/addon/ProactiveHintAugmentor.java)
- [HintCoordinatorTest.java](../../../android-addon/tests/HintCoordinatorTest.java) 和 [ProactiveHintAugmentorTest.java](../../../android-addon/tests/ProactiveHintAugmentorTest.java)

**接口** 保留现有非流式 regenerate 签名。新增包内方法 static String evidenceFallback(KnowledgeRetrieval.Result result)，仅格式化当前轮实际返回的证据。复用已有日志出口和模型响应后处理。

- [ ] 补模型 HTTP 夹具，分别模拟响应头超时、响应体超时、HTTP 401/503、空回答和无效 JSON。对每个故障断言独立诊断和单次投递。覆盖完整空结果、全库失败、部分命中、长摘录和补充平面字符。运行测试确认当前模型异常只会回退原回答。
- [ ] 模型请求记录 seq、请求是否发出、响应头耗时、HTTP 状态、响应体完成耗时、端到端耗时、失败阶段与网关已返回的请求 ID。区分 timeout、http_error、invalid_response 和 empty_completion。上传和 JSON 解析计入模型预算。等待结束后记录生成成功、证据摘录、厂商原答或旧序号丢弃四种投递结局。日志使用现有 trace，序号通过已有协调入口传给诊断。
- [ ] 检索命中且当前轮仍有效时，优先投递完整模型答案；模型无可用答案时，从首条非空证据提取短摘录并加「知识库摘录」标签，部分命中时加入失败库标记并遵守显示预算。摘录路径采用返回正文原句，避免把厂商原答混作知识库内容。证据摘录不进入生成缓存。总看门狗先触发时，立即按已有路径放行厂商原回答，晚到结果作废。
- [ ] 保持当前检索状态行为。完整检索且证据为空时显示「未找到相关内容」；覆盖率为 0 且存在失败库时显示失败库及错误码；部分命中仍使用成功库证据；鉴权或网络失败且无证据时放行厂商原回答并单独记因。
- [ ] 在 PowerShell 运行 powershell.exe -NoProfile -ExecutionPolicy Bypass -File android-addon/tests/RunProactiveHintTests.ps1。期望配置、MCP、协调器、真实 HTTP 回环及普通对话回归全部通过。

### Task 3 — 后端建立主动提示的生成延迟基线

**范围** 由模型网关和推理服务的维护者执行。用同款非流式 Chat Completions 请求、现用模型、禁用思考、max_tokens=256 和 120 字提示约束对照。使用批准的无敏感样本模拟实际提示长度，包含本轮真机问题和匹配证据的受控样例。

- [ ] 用客户端记录的请求时间及网关返回 ID 对齐链路。记录网关接收、排队、上游建连、首 token、生成完成、响应发送完毕。若客户端超时前没有响应头或 ID，依本轮起止时间核对服务端记录，报告能确认的阶段。
- [ ] 在同一模型和输入下分别测量冷请求、热请求、单请求以及两请求重叠，每种至少 10 次。记录样本量、成功率、TTFT、完整响应 p50/p95、错误比例和答案质量。首版以完整响应 p95 ≤20 s 作为后端目标，让手机侧 25 s 留出余量。此数是上线目标，当前尚无同口径基线。
- [ ] 根据证据优化耗时最高的阶段。排队或建连慢则处理容量、预热或连接复用；输入或生成慢则核对实际 token 数，再压缩重复证据或调整模型路由。每次改动用同口径数据复测，并核对咖啡机、制冰机、夹爪答案与实际来源一致。
- [ ] 后端交付同一批样本的前后对比和一份可对照的脱敏日志。客户端可先验收稳定性，预算是否需要收紧由后端稳定数据和真机体验共同决定。

### Task 4 — 重建并验收眼镜实际显示

**入口** [TMG4 安卓源集](../../../android-addon/tmg4_sources.py)、现有 1.0.20/309 host-build、APK 签名与审计流程、正式「有问必答」入口。

- [ ] 用原厂输入和独立输出目录构建、签名并覆盖安装。回读 APK 摘要比对本地签名产物，确认眼镜连接与应用启动状态。
- [ ] 在 YAL-AL10 与眼镜上选 obsidian-knowledge。逐一测试咖啡机、制冰机、夹爪品牌。每题等实际投递后再问下一题，逐题对照已知来源以及实际镜片显示。记录 Cortrix 命中、模型结局、回调结局和显示验收，不以回调日志替代实际显示。
- [ ] 再交叉运行默认库、显式库、无效库与两题连续到达这四种对照。确保默认库省略 namespaces，显式库携带所选数组，错误被识别，旧答案不覆盖新答案。用受控慢响应或模型故障实验验证长预算和摘录路径。
- [ ] 三条已知问题各连续复测三次，每轮只投递一次，且实际显示的型号或品牌须与已知资料一致。分别记录模型成功、证据摘录和厂商回退的次数。只展示相关摘录但未回答所问型号或品牌的回合计为降级，不能计入正确答案。普通助手至少完成一轮知识库问答作为回归对照。
- [ ] 验收后更新 [AI_SETUP.md](../../AI_SETUP.md) 中的预算、回退行为和日志说明，提交任务范围内的源码、测试和文档，保留脱敏验收日志。

## 交付顺序

Task 1 → Task 2 → Task 4。后端可并行完成 Task 3 的基线采集，随后以同一版本的真机样例复核。单次提示允许等待更久。对连续到达的不同提示，取舍是优先当前轮，避免眼镜显示过时答案。
