# 自定义模型如何接替官方问答：普通 V2 源码机制

> 适用对象：[TurboIO 普通 V2 官方 App 扩展](https://github.com/Turbo1123/Turbo-IO/tree/main/official-addon)，按当前本地源码核对。本文不描述独立客户端、实验固件或另一套集成版。用户已验证本地 [Strata](https://github.com/Niko1221/Strata/blob/main/README.zh-CN.md)产生对应请求，镜片文字完整、声音正常；机制说明仍以源码为依据，不把未知的官方后端行为当成实测事实。

## 先给结论，并更正之前的操作说明

**它替换的是“交给官方回调处理的回答内容”，不是替换眼镜的整个语音系统，也不是把官方 App 内部的模型接口地址直接改成你的地址。**

普通 V2 继续使用官方的声音识别、业务事件与眼镜显示通道。扩展接到最终识别文字后，等到本轮符合条件的官方普通聊天结果，拿到当前结果对象；再向你配置的模型发送文字问题，把自有回答放回官方认识的对象结构，通过原回调处理路径输出。

**“第一次必须先完整走完一轮官方回答”不是当前执行代码的硬性要求。** 之前建议先官方完整问答，是为了分层验证官方链路和观察真实回包的保守操作步骤。我曾把这一步的必要性说得过强，应予更正。

准确的技术条件是：**每轮接管都要等到这一轮合格的官方聊天回包，而不是先完成另一轮官方问答，更不是等官方完整答案结束。** 首次已选择自定义模式时，符合条件的首个本轮回包可以在同一次函数调用中建立验证标记、保存本轮对象并开始接管。详见第 6 节。

## 1. 先把三条路线分开

| 路线 | 做什么 | 当前接管是否替换它 |
| --- | --- | --- |
| 声音 → 提问文字 | 官方 [语音识别回调](<../Addon.m#L241-L252>)，即 `onAsrResult:isFinish:sessionId:` | 不替换识别引擎；普通识别事件仍先调用原处理 |
| 文字问题 → 回答文字 | 扩展构造 [兼容模型请求](<../Core.m#L70-L83>)，由配置的服务生成 | 普通聊天符合条件时，改用自有模型输出 |
| 回答文字 → 镜片及朗读 | 原结果回调处理、完成通知，以及扩展的 [TTS](<../VoiceTTS.m#L67-L100>) | 复用原显示处理；开启朗读时走扩展文字朗读路径 |

这里把“识别、生成、呈现”分开，是为了避免一个常见误解：**本地模型生成答案，不等于声音识别也已改成本地，不等于官方服务不再参与。**

## 2. 加载时：先检查宿主，再插入回调分流层

入口是 [Load](<../Addon.m#L529-L564>)。主要做两类事：

1. 检查应用标识、主程序版本/构建/镜像身份以及关键方法的参数签名；不能匹配就不安装这些问答替换。
2. 在允许的宿主里，用 [Objective-C 运行时的 method_setImplementation](https://developer.apple.com/documentation/objectivec/method_setimplementation(_:_:).md)替换方法实现，并保存它返回的旧实现函数指针。

Apple 对该函数的定义是“设置方法实现”，返回值是“旧实现”。因此我们不是把旧处理逻辑丢掉，而是把它留成可调用的入口。

| 官方通知方法 | 扩展新实现 | 保存的原实现 |
| --- | --- | --- |
| `onAsrResult:isFinish:sessionId:` | `AsrHook` | `OriginalAsr` |
| `onNlpResult:` | `NlpHook` | `OriginalNlp` |
| `onResponseComplete` | `CompleteHook` | `OriginalComplete` |

这些变量指向**官方 App 原有方法实现**，不是一个新网络接口，也不是直接向眼镜写入原始蓝牙数据包的函数。

```text
原来：官方事件 → 官方结果处理
现在：官方事件 → 扩展 Hook → 分流决定 → 必要时调用官方原处理
```

另一个与实际使用有关的行为：每次扩展重新加载，都会把回答方式恢复到官方默认，并清除 `verifiedChatDomain` 标记，见 [初始化复位](<../Addon.m#L552-L556>)。所以“进程重启后需要重新确认接管模式”与“必须先完成一个官方完整答案”是两件事。

## 3. 最终识别文字到达：记录本轮问题，不立即请求模型

链路是 [AsrHook → acceptAsr](<../Addon.m#L162-L170>)。对于普通提问，Hook 先调用 `OriginalAsr`，再让扩展观察识别结果。

`acceptAsr` 在自定义或随机字符串模式下处理最终识别结果：

- 跳过空文字、官方默认模式及重复的最终识别事件。
- 取消上一轮扩展自己的请求，增加代次 `_generation`，清空旧的结果模板和输出状态。
- 记录 `_asr`（提问文字）、`_sid`（会话标识）、`_listener`（本轮回调接收者）。
- 等待合格的本轮官方普通聊天回包。

**这一处没有调用模型 API。** 它只是把问题和会话身份准备好。

### 本轮控制状态：不仅有提问文字

下面是解释用的合成示意，不是用户数据、真实内存快照或完整的类定义：

```text
TIOController {
    asr: "请解释天空为什么是蓝色的",
    sid: "synthetic-session-A",
    listener: 当前官方事件接收者,
    responseTemplate: 尚未取得,
    ownsTurn: false,
    generation: 当前代次,
    emitted: "",
    responseDone: false
}
```

它们分别解决的问题：

| 状态 | 作用 |
| --- | --- |
| `_asr` | 向自有模型发送哪一个问题 |
| `_sid`、`_listener` | 识别结果与后续结果是否属于同一轮、同一接收者 |
| `_responseTemplate` | 之后复制哪一个官方结果对象 |
| `_ownsTurn` | 这一轮输出是否由扩展负责 |
| `_generation` | 取消后到达的旧异步输出是否应忽略 |
| `_emitted` | 已输出正文前缀，用于计算下一次差量 |
| `_responseDone` | 是否已收尾，避免再次输出完成 |

参见 [状态定义](<../Addon.m#L113-L134>)、[cancel](<../Addon.m#L147-L149>)、[emitText 的前置保护](<../Addon.m#L171-L183>)。

这里等待元数据的动机是：**声音识别结果是一段文字；而这份实现只允许接管特定类型的业务结果。** 当前官方事件还可能是技能指令、待办、离线结果等，不能把所有识别文字都一律交给普通聊天模型来替换业务。

## 4. 本轮官方回包到达：决定要不要接管

入口是 [NlpHook](<../Addon.m#L262-L266>)，它调用 [receiveNlp](<../Addon.m#L187-L223>)。

### 4.1 它如何认定“普通聊天”

[TIOIsEligibleChat](<../Core.m#L39-L42>)的条件是：

```text
domain  == "chat"
intent  == "chat"
sub     == "workflow"
offline == false
没有 command
```

还要通过当前模式、识别文字、接收者、会话标识等检查。对会话标识的实际实现是：两侧标识均非空且不一致时拒绝；不应把它描述成无论是否为空都会严格逐字相等。

### 4.2 不接管时

`receiveNlp` 返回 `NO`，`NlpHook` 调用原 `OriginalNlp`，保留官方处理路径。官方默认、离线或不符合上述普通聊天条件的结果，不被强行改造成普通模型回答。

官方待办接管检测另有优先路径：若确认官方业务接管本轮，会取消自有回复，把后续确认与完成处理交回官方，见 [业务优先保护](<../Addon.m#L195-L200>)。

### 4.3 接管时

关键动作是：

```objc
_responseTemplate = response;
_ownsTurn = YES;
_started = YES;
```

然后根据模式：

- 模式 `1`：生成合成随机字符串，不请求自有模型，适合测试输出接管。
- 模式 `2`：创建 `TIORequest`，将识别文字、扩展的有限历史交给自有接口。

`receiveNlp` 返回 `YES` 后，Hook 不再将这一条官方 NLP 结果直接交给原处理。后续符合本轮接管条件的官方结果也不会直接输出，避免官方答案和自有答案混杂。

**这个分流只适用于通过检查的本轮结果，不是“屏蔽所有官方事件”。**

## 5. 为什么不能只把模型正文直接交给眼镜

因为当前复用的入口 `OriginalNlp` 接受的是官方的 [RayNeoNlpResultWrapper 结果对象](<../Addon.m#L137-L145>)，不是普通字符串。

一条结果除了正文，还带着“哪个对话、什么类型、是不是结束”等信息。假如只有一句“天空是蓝色的”，原流程不能仅从这句话判断它该放入哪个正在显示的对话，以及何时结束这一轮。

以下是**部分字段的合成 JSON 风格示意**；真正对象是宿主运行时类，字段类型、内部约定不能仅凭这个例子概括：

```json
{
  "domain": "chat",
  "intent": "chat",
  "sub": "workflow",
  "sessionId": "synthetic-session-A",
  "dialogId": "synthetic-dialog-A",
  "round": 1,
  "answer": "一小段回答正文",
  "spoken": "",
  "finished": false,
  "offline": false
}
```

[CopyResponse](<../Addon.m#L137-L145>)并不是把整条原始蓝牙报文逆向生成一次，而是：

1. 找到宿主的结果包装类，检查模板是否属于它。
2. 新建该类对象。
3. 复制已列明的官方上下文字段。
4. 替换本次正文，清空 `spoken`，设置 `finished`。

明确复制的字段为：

```text
sub, dialogId, sessionId, domain, intent, round,
query, spoken, answer, finished, offline, command,
hasNextRound, rawData
```

**这是 14 个列明字段，不是对完整闭源对象的任意深拷贝。** 不应声称它自动理解官方所有字段或私有协议。

可以用一个贴近代码的比喻记住：**保留本轮“信封”，替换里面的回答正文。** 信封是当轮真实官方对象提供的上下文，不是从首次完整答案永久保存的一份万能模板。

## 6. “第一次先走官方”到底是什么意思

这里有三个不同概念，之前的操作指导容易把它们混在一起。

| 概念 | 当前实现是否需要 |
| --- | --- |
| 先完成另一个问题的完整官方答案 | **不是执行硬条件**；作为稳妥验收建议有价值 |
| 本次进程观察到合格的官方普通聊天元数据 | 接管检查需要；但可以在本轮同一回调中完成 |
| 每轮取得本轮官方结果对象 | **需要**；用于当轮正文替换和回填 |

### 决定性的执行顺序

[receiveNlp](<../Addon.m#L201-L210>)中的实际顺序是：

```objc
BOOL eligible = TIOIsEligibleChat(...);

if (eligible)
    [Prefs setObject:@"chat" forKey:@"verifiedChatDomain"];

// 再检查回答模式、listener、ASR、offline 和 session。
NSString *approved = [Prefs stringForKey:@"verifiedChatDomain"];
if (!approved.length || ![domain isEqual:approved] || !eligible)
    return NO;

// 本轮取得模板，进入接管。
_responseTemplate = response;
_ownsTurn = YES;
```

请注意：**先写验证标记，再在同一次回调里读取。** 因此并不要求标记一定来自一个已完整结束的先前问题。

设初始标记为空、用户首次选择自定义模式：

1. 最终识别文字到达，扩展记录问题、会话与接收者。
2. 本轮第一个合格官方聊天回包到达。
3. 它在同一次 `receiveNlp` 中建立 `verifiedChatDomain`，通过相应门禁。
4. 扩展保留本轮模板，启动自有模型并替换输出。

这条路径**由当前源码允许**；本会话没有额外做“首次直接选择自定义、不做任何官方暖场”的独立实机试验，不能把源码结论误写成新实验已通过。

### 那为什么界面仍提示先官方问答

[回答模式选择界面](<../Addon.m#L461-L466>)在没有标记时会弹出警告，但它的操作顺序是：

```text
先保存你选择的模式 → 再弹警告
```

它没有因警告而强制切回官方默认。**警告文本不等于完整的运行门禁说明。** 本文如实描述这一差异，不修改已经跑通的代码。

### 模板是否只学一次

不是。每轮最终识别结果调用 `cancel` 时，会清空 `_responseTemplate`；随后本轮接管才重新保存当前对象，见 [每轮准备](<../Addon.m#L149-L169>)。

`verifiedChatDomain` 仅是“观察过允许的聊天类型”的标记；`responseTemplate` 则是“本轮拿来复制的对象”。它们不是同一个东西。

## 7. 发给自有模型的是什么

[receiveNlp](<../Addon.m#L215-L220>)把 `_asr` 交给 [TIORequest.startQuestion](<../Addon.m#L100-L110>)，再由 [TIOChatRequestWithHistory](<../Core.m#L70-L83>)构造消息。

请求示意中的问题是合成样例，不含真实用户正文：

```json
{
  "model": "qwen3.8-flash-next-iq3_xxs",
  "stream": true,
  "max_tokens": 1024,
  "messages": [
    {"role": "system", "content": "简洁中文、模型标识及用户配置的资料提示词"},
    {"role": "user", "content": "请解释天空为什么是蓝色的"}
  ]
}
```

实际消息还可能包含扩展进程内的最近成功问答，以及允许的工具说明。成功问答按 user/assistant 配对保留，最多 50 条消息（不是 50 轮），见 [历史实现](<../Core.m#L52-L62>)。

[modelRound](<../WebSearch.m#L121-L130>)以 [HTTP POST](<../WebSearch.m#L128-L130>)发送到配置的完整地址，并使用：

```text
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <手机本地填写的 Key>
```

这个模型请求发送的是文字消息，不是眼镜原始音频，也不是把整个官方回包对象直接作为模型请求上传。

对于你授权的研究构建，接口校验允许指定的局域网 HTTP 地址；普通默认构建仍只允许 HTTPS。这是传输配置改动，与回调接管机制是两层不同的事。细节见 [局域网接入说明](<LOCAL_HTTP_RESEARCH.md>)。HTTP 会明文传输认证与对话内容。

## 8. 收到流式回答：累积正文，再计算回填差量

实际网络路径使用 [TIOWebStream](<../WebSearch.m#L45-L78>)解析 [SSE 流式事件](<../WebSearch.m#L49-L70>)。它读取 `choices[0].delta.content`，累积成当前完整回答。

合成事件示例：

```text
data: {"choices":[{"delta":{"content":"天空看起来是蓝色的，"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"主要与光的散射有关。"},"finish_reason":"stop"}]}
```

解析器把正文累积起来，`publish` / `finish` 再把当前全文和是否结束交给更新回调；[emitText](<../Addon.m#L171-L185>)接收它。

为什么模型解析器累计全文，而宿主回调收到差量？因为 `emitText` 通过 [TIOAppendDelta](<../Core.m#L34-L36>)从当前全文中减去已经输出的前缀：

| 次数 | 当前全文 | 已输出 | 本次回填 |
| --- | --- | --- | --- |
| 1 | `天空` | 空 | `天空` |
| 2 | `天空看起来` | `天空` | `看起来` |
| 3 | `天空看起来是蓝色的。` | `天空看起来` | `是蓝色的。` |

若每次都重复回填全文，而宿主按追加方式处理，就可能重复显示先前文字。**当前实现明确要求输出是追加流**；若供应商改写已输出前缀，代码不会假装正常，而是提交错误收尾。

每次正常更新的核心是：

```objc
id wrapper = CopyResponse(_responseTemplate, delta, done);
OriginalNlp(_listener, ..., wrapper);
```

自有模型产出正文，扩展负责包装，原官方回调负责沿已有处理链呈现。不是把 SSE 或模型 JSON 原样发送到镜片。

## 9. 回答正文与“这一轮结束”是两类通知

正文最后一段带 `finished=true` 还不够；当前实现还明确调用 `OriginalComplete`，通知原有流程进行结束处理。

[emitText](<../Addon.m#L184-L185>)正常完成时：

1. 标记 `_responseDone=true`。
2. 将成功的 user/assistant 对加入扩展历史；失败不当作成功历史。
3. 调用 `OriginalComplete`。

同时，[CompleteHook](<../Addon.m#L267-L269>)仍观察官方完成事件，但在本轮由扩展接管时，不直接把对应原完成处理提前转发，避免官方先完成而自有输出还没结束。

结束门禁也并非“只要服务器连接断了就成功”：[流解析器](<../WebSearch.m#L51-L70>)要求合格的 `finish_reason`；单独 `[DONE]` 不被当作正常完成。若服务流提前断开或格式不支持，会走错误收尾。

## 10. 声音如何出现

自有模型主要返回文字，不直接返回眼镜能播放的音频。若开启回答同步朗读，[emitText](<../Addon.m#L184-L184>)将当前完整文字及结束状态交给 `VoiceTTS`。

[appendFullText](<../VoiceTTS.m#L67-L88>)从累计全文取新增文字、做分段并排队。当前 [本机朗读](<../VoiceTTS.m#L90-L100>)使用手机语音合成，并检查蓝牙音频输出；用户也可另行配置云端朗读。

`CopyResponse` 清空 `spoken` 字段，是配合扩展朗读路径减少重复播报的处理。不能从“声音正常”推断该声音一定是本地大模型生成的音频，也不能推断所有音频路由和后台场景都已验收。

## 11. 一次接管的整体路径

下图是一张简化的分流图：重点看官方路径仍在、接管分支替换的是回调输出；详细的文字、状态与对象传递以正文为准。

![本轮回调分流与本地回答回填](<../../Learn/Viz/turboio-callback-takeover.svg>)

把它压成一条阅读路线：

```text
官方识别
  → AsrHook：调用原识别处理
  → acceptAsr：保存本轮问题 / session / listener
  → 本轮官方 NLP 回包
  → receiveNlp：判断普通聊天，保留本轮模板
  → TIORequest / modelRound：请求自有模型
  → TIOWebStream：累计流式正文
  → emitText / CopyResponse：计算差量、复制本轮对象、替换正文
  → OriginalNlp：复用官方呈现路径
  → VoiceTTS：按设置朗读
  → OriginalComplete：本轮收尾
```

## 12. 接管边界、隐私与常见误解

| 说法 | 当前源码支持的准确结论 |
| --- | --- |
| “只改官方模型 URL” | 否，是运行时回调分流加独立模型请求 |
| “识别到文字立刻调用本地模型” | 否，先准备本轮状态，再等待合格官方 NLP 回包 |
| “必须先有一轮完整官方答案” | 不是执行硬条件；首个合格当轮回包可以同轮建立标记并接管 |
| “首次永久学会一套回包模板” | 否，结果模板每轮重新取得 |
| “所有眼镜技能都由本地模型替代” | 否，普通聊天白名单以外保留官方业务路径 |
| “自有模型失败就自动回退官方答案” | 接管后的模型失败会提交错误收尾，不应宣称自动恢复官方同轮答案 |
| “接管后官方服务器请求被取消” | 所见代码没有这个保证；它仍调用原识别处理，抑制的是相关输出和完成回调 |
| “整个语音链路完全离线” | 不成立；官方识别等链路仍保留并可能涉及官方服务 |
| “模型 API 测试通过就等于镜片通过” | 不成立；测试接口与实际语音接管/显示是不同路径，需要分别验收 |

扩展取消的网络请求是它自己的 `TIOWebChatRequest`；不应据此推断取消了官方后台推理、计费或留存。是否有官方服务器继续生成、记录或计费，不能从这些公开扩展文件确定。

## 13. 源码索引

| 需要查什么 | 文件与位置 |
| --- | --- |
| 宿主门禁、初始化模式和 Hook | [Addon.m:529–564](<../Addon.m#L529-L564>) |
| 记录本轮识别与清空旧模板 | [Addon.m:147–170](<../Addon.m#L147-L170>) |
| 接管资格、验证标记、取模板、请求模型 | [Addon.m:187–223](<../Addon.m#L187-L223>) |
| 普通聊天白名单 | [Core.m:39–42](<../Core.m#L39-L42>) |
| 包装对象字段及正文替换 | [Addon.m:137–145](<../Addon.m#L137-L145>) |
| 差量输出、旧代次保护、收尾 | [Addon.m:171–185](<../Addon.m#L171-L185>) |
| Hook 分流与完成处理抑制 | [Addon.m:241–269](<../Addon.m#L241-L269>) |
| API 消息与历史构造 | [Core.m:52–83](<../Core.m#L52-L83>) |
| 实际 POST 与认证头 | [WebSearch.m:108–130](<../WebSearch.m#L108-L130>) |
| SSE 正文与结束事件校验 | [WebSearch.m:45–78](<../WebSearch.m#L45-L78>) |
| 朗读分段、本机合成与音频路由 | [VoiceTTS.m:67–100](<../VoiceTTS.m#L67-L100>) |
| 初次选择模式时的警告 | [Addon.m:461–466](<../Addon.m#L461-L466>) |
| 无语音、无模板的合成 API 测试 | [Addon.m:475–476](<../Addon.m#L475-L476>) |

行号对应本文写作时的本地源码，之后修改可能移动；函数名是更稳定的检索线索。文中合成对象、问题和事件示例不是日志，更不包含用户密钥。

## Sources

- [Apple — method_setImplementation](https://developer.apple.com/documentation/objectivec/method_setimplementation(_:_:).md) — 本轮读取的原始接口定义，明确方法实现替换会返回旧实现。
- [Addon.m](<../Addon.m#L137-L269>) — 本轮已读取的实际控制器与 Hook 链，支撑接管、每轮模板与输出替换结论。
- [Core.m](<../Core.m#L39-L83>) — 本轮已读取的聊天白名单和请求构造，支撑业务范围及有限历史结论。
- [WebSearch.m](<../WebSearch.m#L45-L183>) — 本轮已读取的实际网络与流处理，不把另一个未走到的解析类误当成当前网络路径。
- [VoiceTTS.m](<../VoiceTTS.m#L67-L100>) — 本轮已读取的文字分段、音频路由及本机合成路径。

相关学习资料：[会话记录](<../../Learn/Sessions/2026-10-09%20TurboIO%20问答接管原理.md>)。本轮按用户要求改为完整文档，不继续问答；没有新增理解检查通过成绩。
