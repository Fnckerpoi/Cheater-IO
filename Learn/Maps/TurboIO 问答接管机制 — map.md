---
type: map
subject: software engineering
state: paused
opened: 2026-10-09
frontier: 已按源码整理三条种子概念；节点问答已停止，没有新增掌握判断
next: 按用户要求交付机制资料；不自动恢复问答或安排复习
nodes:
  N1: 结果回包保留本轮信息
  N2: 替换输出不等于替换官方语音链路
  N3: 流式正文与结束信号不同
graph:
  goal: 说明普通聊天接管的依赖、输出路径与证据边界
  foundations:
    - F1 | 普通识别路径仍调用官方原识别回调
    - F2 | 只接管合格普通聊天且监听对象与会话须匹配
  edges:
    - F1 -> N1
    - F2 -> N1
    - N1 -> N2
    - N1 -> N3
    - N2 -> G
    - N3 -> G
tags: [learn, moc]
updated: 2026-10-09
---

# 问答接管机制图谱

本图整理 [TurboIO 的普通扩展接管路径](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L137-L269>)，不把源码核查标为学习者已理解，也不把源码推断标为新增实机验证。来源会话：[问答接管原理](</Users/duriea/Documents/GitHub/Turbo-IO/Learn/Sessions/2026-10-09 TurboIO 问答接管原理.md>)（[[2026-10-09 TurboIO 问答接管原理]]）。

## 内容依赖

这里的边表达机制解释的依赖，不表达已通过的学习路径，也不是工具调用顺序图。

1. [结果回包保留本轮信息](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L137-L222>) → [[结果回包保留本轮信息]]：根部概念。当轮合格回包提供结构和输出上下文。
2. [替换输出不等于替换官方语音链路](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L241-L269>) → [[替换输出不等于替换官方语音链路]]：依赖第一条，区分结果接管和整条语音／网络链路。
3. [流式正文与结束信号不同](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L171-L185>) → [[流式正文与结束信号不同]]：依赖第一条，把正文差量和收尾分别放回当轮输出流程；不强造第二条到第三条的依赖。

## 按源码执行顺序定位

| 环节 | 原文入口与承重事实 |
| --- | --- |
| 挂接 | [初始化与 method_setImplementation](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L554-L561>)：初始化复位模式和验证标记；版本／方法签名通过后替换回调并保存原实现。 |
| 保存识别 | [AsrHook](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L241-L252>) 正常路径先调用原识别回调，再进入 [acceptAsr](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L162-L170>)；有效且未重复的最终文字先清旧模板，再保存本轮问题、会话和监听对象。 |
| 门禁与接管 | [receiveNlp](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L187-L222>) 在合格聊天时先写验证标记，再于同回调读取；当前监听对象、会话和 [TIOIsEligibleChat](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Core.m#L39-L42>) 范围通过后，保存模板、取得输出权，自定义模型模式启动本轮请求。 |
| 自有请求 | [消息构造](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Core.m#L70-L83>) 加入配置的模型、历史和当前问题；[modelRound](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/WebSearch.m#L121-L130>) 发送到所配置接口。 |
| 解析输出 | [流式解析器](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/WebSearch.m#L49-L70>) 区分正文、工具调用和有效结束原因；[emitText](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L171-L185>) 经 [CopyResponse](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L137-L145>) 回填正文差量并调用原结果回调，最终再调用原完成回调。 |

## 边界与更正

- 首次不必先完成一次独立、完整的官方回答。验证标记的写入和读取可在同一次合格回调中发生；但每轮有效且未重复的最终识别会清旧模板，每轮仍等待当轮官方聊天回包。这是源码结论，不是新实机实验。
- 接管的是合格聊天输出，并非全部官方结果；命令、离线回包与不匹配的监听对象／会话不能据此一概替换。
- 所读源码没有展示取消官方服务器网络请求；不宣称离线、免上传或官方侧停止计算。
- 结果复制列表为 14 个列明字段，以当前源码显式列表为准；不是对完整闭源对象的任意深拷贝。
- [工具注册与请求配置](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/WebSearch.m#L108-L130>) 展示条件性注册的公开搜索、待办创建和知识检索；其中知识检索提示词明确说明会提交到桌面端执行只读检索。因此不声称工具不存在、没有脚本运行能力，或所有工具都只生成文字。这里也没有核对每个工具后端的实际执行实现，不能据这些片段扩展宣称任意脚本执行。

## Frontier

探测仅确认学习者知道声音识别由官方负责；对回包所需信息回答不知道。用户拒绝继续节点检查，要求直接整理机制资料；三条概念均保持种子状态，没有新增通过成绩、提取日志或到期承诺。[[复习队列]] 只记录未提取状态，不安排复习。

## 交付位置

- [完整机制文档](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/docs/MODEL_TAKEOVER_MECHANISM.md>) — 已完成并核对，沿源码执行顺序解释当轮状态、结构复制、流式输出及收尾。
- [回调分流图](</Users/duriea/Documents/GitHub/Turbo-IO/Learn/Viz/turboio-callback-takeover.svg>) — 只表达源码结构，不表达学习者掌握状态。
- [[🌱 学习索引]] — 入口及交接说明。

## Sources

- [控制器与回调](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L137-L269>) — 已读：模板、接管、识别和输出收尾。
- [加载门禁](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Addon.m#L554-L561>) — 已读：初始化复位与回调挂接。
- [聊天条件及请求结构](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/Core.m#L39-L83>) — 已读：合格聊天和自有消息构造。
- [流式解析](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/WebSearch.m#L49-L70>)、[工具注册与发送](</Users/duriea/Documents/GitHub/Turbo-IO/official-addon/WebSearch.m#L108-L130>) — 已读：正文与结束、条件性工具及网络请求。

## 验证说明

依赖结构存于属性，未另造已渲染图。当前管理员工具只有文件操作，无法运行现有图谱校验或绘图脚本；写入范围也不包括生成图目录。因此仅进行源码、字段、节点和链接的静态核对，脚本校验与重绘留给有权限的导师，不据此声称库的工具不存在。
