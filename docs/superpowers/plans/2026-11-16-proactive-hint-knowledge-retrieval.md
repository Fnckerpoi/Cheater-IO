# 有问必答知识库检索链路 Implementation Plan

> **For agentic workers:** Use superpower-subagent-driven-development to implement each task with scoped ownership and integrated review.

**Goal:** 让「有问必答」在把云端提示推到眼镜之前，先检索手机侧 Cortrix 知识库，把证据交给手机模型组织回答；未配置、未命中、超时或鉴权失败时忠实放行云端原回答。检索链路挂接官方回注通道，不新建数据通道。

**Architecture:** 复用既有的有问必答挂起重生成骨架。雷鸟云侧 ASR 的 proactive hint 经宿主 onProactiveHint 回调到达，TurboAddon.dispatchProactiveHint 在串行槽内挂起官方回调，把 hint.text 解析为 question/answer；ProactiveHintAugmentor 以 generation/sequence/revision 三态做幂等与防 stale，总 watchdog 18000ms；工作线程内 KnowledgeRetrieval.retrieve 走 Cortrix MCP Streamable HTTP 检索（预算 15000ms，检索请求显式传 rerank=false 以避开服务端跨命名空间重排的高耗时；CortrixClient 按 endpoint+token 缓存 MCP 会话，会话在 initialize 校验通过后立即落库，warm 检索只发 tools/call，404 会话过期或 keep-alive 连接失效自动重协商一次，同一握手内多请求复用同一条 HttpURLConnection（连接复用），命中后 ProactiveHintRegenerator 以手机模型重生成（预算 2200ms），结果回主线程回写 hint.text，再经 invokeTyped(source, "onProactiveHint", ...) 回注官方，由官方走业务 13 voiceAssistant / AssistantType=5（CaptionMsg）推屏。检索配置与 Bearer Token 来自 McpConnectionStore + SecretStore，模型端点/密钥来自偏好与密钥库。普通对话路径的 knowledge_query 工具调用是并存的另一条链路，两者互不干扰。

**Tech Stack:** Java 8 / Android DEX、org.json、Cortrix MCP（Streamable HTTP，protocolVersion 2025-03-26）、手机模型 Chat Completions API。

**Spec:** 本计划即设计规格；补充参考 docs/AI_SETUP.md、docs/assistant-kb/RAYNEO_CLEARTEXT_FIX_20261029.md、docs/firmware-tmg4/TMG4-MENU-OVERRIDE-MECHANISM.md。

## Global Constraints

- 改动只落在 android-addon/src/com/turboio/addon/ 下知识库与有问必答相关文件；不改官方协议、帧头与推屏通道。
- 有问必答复用官方业务 13（voiceAssistant）+ AssistantType=5（CaptionMsg）通道，不新建通道、不复用业务 19 全屏字幕通道。
- 忠实性：模型仅可引用检索真实返回的 doc_id / source_path / metadata；未配置、未命中、超时、鉴权失败必须放行云端原回答，严禁编造知识库来源。
- 预算硬上限：检索 15000ms、模型 2200ms、总 watchdog 18000ms；任何超时不得阻塞官方回注。检索请求在 CortrixClient.buildQueryPayload 中固定传 rerank=false——服务端默认 rerank=true 的跨命名空间重排实测 5–9.6s，远超预算，是检索必然超时的主因。
- 缓存以 McpConnectionStore 的 revision 为准；revision 变化必须失效缓存，禁止复用旧答案。
- 不引入新依赖；沿用仓库现有 runner（javac -source 8 -target 8 + android.jar + json jar，java --add-modules jdk.httpserver）。
- 原地工作：不提交当前未跟踪的 TMG4 菜单改动，不接设备，不做固件构建。

## Review Focus

- 证据与覆盖信息（coverage_ratio / namespaces_failed / truncated）如实传入模型，模型不被诱导编造来源。
- 回注走主线程单次幂等投递，watchdog 超时后放行云端原回答；原始 hint 被外部改写时不得投递。
- 检索/模型链路与普通对话 knowledge_query 工具链路互不干扰，配置自检可用。
- 遗留死代码不得在新接入中被误用或误导后续维护。
- 检索预算、模型预算、watchdog 超时三类失败路径均有测试覆盖。

## Baseline（已提交，本计划不再重建）

以下能力已在 e61fe8e 及后续提交落地，属既有基础，不在任务中重复实现：

- **检索层**：android-addon/src/com/turboio/addon/KnowledgeRetrieval.java:1-93 定义 Status / Evidence / Result（coverageRatio、namespacesFailed、truncated、revision），retrieve(question,snapshot,budgetMs) 与 retrieve(question,namespaces,snapshot,budgetMs)，probe(endpoint,token,budgetMs)，formatContext(result)。
- **MCP 客户端**：android-addon/src/com/turboio/addon/CortrixClient.java:1-276 单会话 Streamable HTTP，initialize→tools/list（必须发现 cortrix_query）→tools/call，SSE/JSON 双解析，401/403→AUTH_ERROR，超时→TIMEOUT，证据上限 20000 字符并置 truncated。
- **配置与密钥**：android-addon/src/com/turboio/addon/McpConnectionStore.java:1-121 Snapshot(enabled,endpoint,token,revision)，保存/迁移自增 revision，Keystore 存 Token，崩溃恢复 journal。
- **模型重生成**：android-addon/src/com/turboio/addon/ProactiveHintRegenerator.java:1-100，无 key 时 synthesize，有 key 时 POST ChatPolicy.completionEndpoint 并注入证据上下文。
- **挂起协调**：android-addon/src/com/turboio/addon/ProactiveHintAugmentor.java:1-159 的 interceptAndRegenerateAsync、Pending 幂等、watchdog 与缓存。
- **接线与设置**：android-addon/src/com/turboio/addon/TurboAddon.java:150-191 dispatchProactiveHint；McpSettings.java:1-71 设置对话框与自检；EditorialUI.java:76 入口。
- **测试**：HintCoordinatorTest.java、ProactiveHintAugmentorTest.java、CortrixClientTest.java、ToolClientKnowledgeTest.java、ChatRetrievalOrderTest.java 及对应 ps1 runner。

## Task 1：检索结果契约——覆盖信息与证据字段进入模型提示

**Files:** android-addon/src/com/turboio/addon/KnowledgeRetrieval.java, android-addon/src/com/turboio/addon/ProactiveHintRegenerator.java
**Interfaces:** Result / Evidence 结构不变；formatContext(Result) 在命中时追加覆盖行；synthesize(cloudAnswer, result) 携带覆盖注记。ToolClient.formatKnowledgeResult（对话路径）不受影响，继续输出 coverage_ratio / namespaces_failed。

- [ ] 写测试：构造 HIT 的 Result（含 coverageRatio、namespacesFailed、truncated），断言 formatContext 输出包含 coverage_ratio、namespaces_failed、truncated 字段；构造无证据的合成路径，断言 synthesize 文本包含覆盖注记 → 运行确认失败。
- [ ] 实现：formatContext 命中时在证据 JSON 后追加一行覆盖信息（coverage_ratio / namespaces_failed / truncated），未命中时保留「不得编造」提示并带上覆盖失败说明；synthesize 在【知识库证据】后补充覆盖注记。
- [ ] 跑 ProactiveHintTests / HintCoordinatorTests 全量，确认通过后提交。

### Task 2：有问必答模型输出约束与后处理

**Files:** android-addon/src/com/turboio/addon/ProactiveHintRegenerator.java
**Interfaces:** regenerate(question, cloudAnswer, result, budgetMs) 返回值裁剪到眼镜显示预算并清理控制字符，其余签名不变。

- [ ] 写测试：假模型返回超长（>120 字）或含换行/控制字符，断言裁剪到 ≤120 字且控制字符被清理 → 运行确认失败。
- [ ] 实现：在解析 choices[0].message.content 后做长度裁剪与空白/控制字符清理；裁剪后为空按 empty_completion 处理。
- [ ] 跑 ProactiveHintTests / HintCoordinatorTests 全量，确认通过后提交。

### Task 3：端到端挂起重生成链路验证

**Files:** android-addon/tests/HintCoordinatorTest.java, android-addon/tests/RunProactiveHintTests.ps1, android-addon/tests/RunHintCoordinatorTests.ps1
**Interfaces:** 复用从 TurboAddon.java 切出的 ProactiveHintHookHarness 夹具边界（serial / hint 派发段 / invoke 段）。

- [ ] 补测试：以假 MCP 返回命中证据 + 假模型返回，断言投递的 hint.text 含模型答案与证据来源；未命中时投递云端原回答；配置 revision 变化后触发重新检索而非复用缓存 → 运行确认失败。
- [ ] 运行全部有问必答与知识库套件（ProactiveHintTests、HintCoordinatorTests、CortrixTests、McpConnectionStoreTests），确认通过。
- [ ] 提交。

### Task 4：清理遗留死代码与文档

**Files:** android-addon/src/com/turboio/addon/ProactiveHintAugmentor.java, android-addon/src/com/turboio/addon/CortrixClient.java, android-addon/tests/ProactiveHintAugmentorTest.java, docs/AI_SETUP.md
**Interfaces:** 移除仅测试引用、未接入主链路的遗留方法；formatKnowledgeResult 等既有接口不变。

- [ ] grep 全仓确认 augment / process / augmentAsync / augmentHintObject / queryBestEvidenceSync 无主链路调用。
- [ ] 删除上述遗留方法并同步调整 ProactiveHintAugmentorTest，改为覆盖主链路的拦截、缓存、回退与 Unicode 解析。
- [ ] 在 docs/AI_SETUP.md 补充「有问必答接入知识库」的开关、预算、回退语义与自检入口说明。
- [ ] 跑全部知识库套件与 Java 编译验证，确认通过后提交。

## 建议执行顺序

Task 1 → Task 2 → Task 3 → Task 4。Task 1、2 只动提示文本与输出后处理，互不阻塞；Task 3 依赖 1、2 落地后在真实链路上验证；Task 4 收尾。

## 自审与残余风险

- **检索证据被截断**：MAX_EVIDENCE_TOTAL_CHARS=20000 超限置 truncated，模型可见证据可能不完整，已通过覆盖注记告知，风险可控。
- **模型超时/失败**：模型预算 2200ms 内未返回即放行云端原回答，不阻塞推屏；由 HintCoordinatorTest 的 watchdog 与失败回退覆盖。
- **检索预算修正（真机验证发现）**：原预算检索 1800ms / watchdog 4000ms 在真机上必然超时——服务端默认 rerank=true 实测 5–9.6s，检索恒为 TIMEOUT、云端原回答被放行（咖啡机返回 mova、麦克风型号返回未知）。修正为检索 3500ms / watchdog 6000ms，并在 CortrixClient.buildQueryPayload 固定 rerank=false（实测 1.2–2.4s），CortrixClientTest 增加该参数回归断言。二次修正（真机仍超时）：手机走 Tailscale DERP 中继 RTT 320–852ms，每次检索 4 个独立 HTTP 请求的冷握手各付一次 TCP+TLS 成本，必超预算；改为 MCP 会话复用（按 endpoint+token 缓存，会话在 initialize 后立即落库，warm 检索只发 tools/call，404 会话过期或 keep-alive 连接失效自动重协商一次）+ 同一握手内多请求复用同一条 HttpURLConnection（连接复用）、预算检索 15000ms / watchdog 18000ms，并输出 warm/cold 与 init_ms/list_ms/call_ms 分段耗时供现场定位。
- **配置 revision 语义**：revision 只在有效保存/迁移时自增，异常路径为 -1，缓存校验据此失效，需保持该语义不被破坏。
- **未在本计划覆盖**：设备端实际推屏效果、OTA 刷写与菜单验收需现场确认，不属于本计划范围。
