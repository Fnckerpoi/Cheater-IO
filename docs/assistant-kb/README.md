# AI 助手、自有模型与知识库 / 有问必答模块文档

汇总雷鸟 AI 助手自有模型接入、Cortrix MCP 知识库检索、有问必答（Proactive Hint）增强链路及网络策略排查文档与结构图。

## 文档与图示

| 文件 | 类型 | 说明 |
|---|---|---|
| [DSH_PROACTIVE_BASELINE_AUDIT_20261030.md](DSH_PROACTIVE_BASELINE_AUDIT_20261030.md) | 基线审查 | DSH 主动回报前置基线只读审查 |
| [RAYNEO_CLEARTEXT_FIX_20261029.md](RAYNEO_CLEARTEXT_FIX_20261029.md) | 故障排查 | 雷鸟 AI 助手模型接入明文 HTTP 拦截根因与修复验证 |
| [turboio-assistant-model-kb.svg](turboio-assistant-model-kb.svg) | 架构图（SVG） | 雷鸟 AI 助手 → 自有模型 → Cortrix 知识库主链路（源文件：[drawio](turboio-assistant-model-kb.drawio) · 位图：[png](turboio-assistant-model-kb.png)） |
| [turboio-proactive-hint-qa.svg](turboio-proactive-hint-qa.svg) | 架构图（SVG） | 有问必答（厂商主动提示）知识库增强链路（位图：[png](turboio-proactive-hint-qa.png)） |

## 关联实施计划

- [2026-11-16-proactive-hint-knowledge-retrieval.md](../superpowers/plans/2026-11-16-proactive-hint-knowledge-retrieval.md) — 有问必答知识库检索链路
- [2026-11-21-proactive-hint-answer-reliability.md](../superpowers/plans/2026-11-21-proactive-hint-answer-reliability.md) — 有问必答知识库答案稳定性
