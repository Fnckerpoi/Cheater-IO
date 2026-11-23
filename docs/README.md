# Cheater-IO / Turbo-IO 文档导航

本目录按模块整理 `docs/` 资料。上游 `Turbo1123/Turbo-IO` 根级入口文档、`screenshots/` 哈希锚定视觉资产与 `superpowers/plans/` 实施计划保留原路径，以兼容 `main` 分支同步及源码/脚本引用；`android` 分支新增的模块文档按功能归入各子目录。

## 模块目录

| 模块 | 目录 / 入口 | 覆盖范围 |
|---|---|---|
| **总览与上游主线** | [docs/](.)（根目录入口） | 架构思路、配置启动、AI 接入、开发生态、源码范围、验证与许可 |
| **Agents-Anywhere（AA）** | [agents-anywhere/](agents-anywhere/README.md) | AA 同包原生嵌入、工作台 UI 迁移、OAuth/闪退修复、语音 Runtime 别名路由、复合看板与交接记录 |
| **AI 助手与知识库 / 有问必答** | [assistant-kb/](assistant-kb/README.md) | 雷鸟 AI 助手自有模型链路、Cortrix 知识库检索与有问必答增强、明文 HTTP 修复与架构图 |
| **Android 宿主适配** | [android-host/](android-host/README.md) | 雷鸟 Android 宿主版本适配、双端迁移计划与宿主升级维护入口 |
| **Strix OS / TMG4 固件定制** | [firmware-tmg4/](firmware-tmg4/README.md) | TMG4-10611 需求与开发记录、菜单覆盖机制、上游显示测试与阅读研究 |
| **ESP32 SCADA 硬件遥测** | [scada/](scada/README.md) | ESP32-C3 + BMP280 BLE 广播协议、AES-GCM 向量与 Android 侧指南 |
| **实施计划归档** | [superpowers/plans/](#实施计划归档superpowersplans) | 按时间归档的专题实施计划 |
| **截图与设计视觉资产** | [screenshots/](screenshots/README.md) · [design/](design/developer-ecosystem-banner-20260926.md) | 界面截图、概念图与审核哈希对应的说明文档 |

---

## 1. 总览与上游主线（保留原路径）

| 文档 | 说明 |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Turbo IO V1 历史实现思路与模块分层 |
| [CONFIGURATION.md](CONFIGURATION.md) | 从源码配置、运行环境与启动说明（`scripts/start.mjs` 引用） |
| [AI_SETUP.md](AI_SETUP.md) | 用 Claude Code / Codex 分平台接入 Turbo IO |
| [AGENT_SKILL.md](AGENT_SKILL.md) | 通过 `skills/turbo-io` 技能引导安装与检查环境 |
| [DEVELOPER_ECOSYSTEM.md](DEVELOPER_ECOSYSTEM.md) | 开发者生态、TCE1 仪表盘与 TAP1 小应用 SDK 指南 |
| [TURBO_IO_DEVELOPER_MANUAL.html](TURBO_IO_DEVELOPER_MANUAL.html) | Turbo-IO 离线 HTML 综合开发手册 |
| [SOURCE_RELEASE.md](SOURCE_RELEASE.md) | 源码发布范围与发行边界 |
| [VALIDATION.md](VALIDATION.md) | 源码交付验证记录 |
| [LICENSING.md](LICENSING.md) | PolyForm Noncommercial 1.0.0 许可与非商业使用边界 |
| [TURBO_DISPLAY_NAVIGATION.md](TURBO_DISPLAY_NAVIGATION.md) | Strix OS `1.0.4.12` Turbo Display 测试工具与原生导航区分 |
| [WEREAD_RESEARCH.md](WEREAD_RESEARCH.md) | 微信读书眼镜端研究思路与参考项目边界 |

---

## 2. 实施计划归档（`superpowers/plans/`）

| 模块 | 计划文档 |
|---|---|
| Agents-Anywhere | [2026-10-30-aa-voice-alias-rebuild.md](superpowers/plans/2026-10-30-aa-voice-alias-rebuild.md) — 雷鸟语音 Runtime 别名路由重建方案 |
| Agents-Anywhere | [2026-11-01-combined-aa-caption-component.md](superpowers/plans/2026-11-01-combined-aa-caption-component.md) — AA 看板与实时字幕复合组件开关 |
| Agents-Anywhere | [2026-11-01-rayneo-homepage-aa-workbench-card.md](superpowers/plans/2026-11-01-rayneo-homepage-aa-workbench-card.md) — 眼镜系统主页常驻 AA 工作台卡片 |
| Android 宿主 / 双端迁移 | [2026-11-01-rayneo-110-dual-migration.md](superpowers/plans/2026-11-01-rayneo-110-dual-migration.md) — 雷鸟 APP 1.0.10 与 Strix OS 双端迁移 |
| Android 宿主 / 双端迁移 | [2026-11-13-rayneo-120-strix-10611-migration.md](superpowers/plans/2026-11-13-rayneo-120-strix-10611-migration.md) — Android 1.0.20 / Strix OS `1.0.6.11` 双端迁移 |
| TMG4 固件定制 | [2026-11-13-tmg4-10611.md](superpowers/plans/2026-11-13-tmg4-10611.md) — TMG4-10611 精简固件实施计划 |
| AI 助手与知识库 | [2026-11-16-proactive-hint-knowledge-retrieval.md](superpowers/plans/2026-11-16-proactive-hint-knowledge-retrieval.md) — 有问必答知识库检索链路 |
| AI 助手与知识库 | [2026-11-21-proactive-hint-answer-reliability.md](superpowers/plans/2026-11-21-proactive-hint-answer-reliability.md) — 有问必答知识库答案稳定性 |

---

## 3. 子项目独立文档目录

仓库中与平台强绑定的文档仍维护在各自子项目内：

- Android 扩展文档：[android-addon/README.md](../android-addon/README.md) · `android-addon/docs/`
- iOS 扩展文档：[official-addon/README.md](../official-addon/README.md) · `official-addon/docs/`
- 固件研究与构建文档：[firmware-research/README.md](../firmware-research/README.md)
