# Agents-Anywhere（AA）模块文档

汇总 Turbo-IO / Cheater-IO 中 Agents-Anywhere 同包原生嵌入、工作台 UI、语音 Runtime 别名路由及眼镜复合看板相关文档。

## 文档清单

| 文档 | 类型 | 说明 |
|---|---|---|
| [AGENTS_ANYWHERE_EMBEDDING.md](AGENTS_ANYWHERE_EMBEDDING.md) | 架构与契约 | AA 原生界面嵌入同一 APK 的范围与契约基线 |
| [AA_WORKBENCH_UI_MIGRATION.md](AA_WORKBENCH_UI_MIGRATION.md) | UI 迁移 | 六张配置卡迁移至可折叠顶部区域与冻结契约 |
| [AA_WORKBENCH_CRASH_FIX_20261027.md](AA_WORKBENCH_CRASH_FIX_20261027.md) | 缺陷修复 | `PayloadContext.registerComponentCallbacks` 栈溢出闪退修复与设备验证 |
| [AA_OAUTH_CALLBACK_FIX_20261027.md](AA_OAUTH_CALLBACK_FIX_20261027.md) | 缺陷修复 | 嵌入版 `agents-anywhere://oauth/callback` 网页授权回跳修复 |
| [AA_UI_SIGN_INSTALL_20261029.md](AA_UI_SIGN_INSTALL_20261029.md) | 签名验收 | AA UI 候选版签名安装记录与设备检查 |
| [AA_VOICE_RUNTIME_ALIAS_ROUTING.md](AA_VOICE_RUNTIME_ALIAS_ROUTING.md) | 设计方案 | 跨设备 Agent Runtime 别名与本地会话锚点路由（`DshAliasRegistry` / `DshAnchorStore` 参考） |
| [AA_CAPTION_COMPOSITE_DASHBOARD_PLAN.md](AA_CAPTION_COMPOSITE_DASHBOARD_PLAN.md) | 方案设计 | 实时字幕与 Agents-Anywhere 复合卡片槽位方案 |
| [HANDOFF_20261029_AA_EMBEDDING.md](HANDOFF_20261029_AA_EMBEDDING.md) | 交接记录 | 2026-10-29 AA 同包嵌入任务交接摘要 |
| [HANDOFF_20261030_AA_CONTINUATION.md](HANDOFF_20261030_AA_CONTINUATION.md) | 交接记录 | 2026-10-30 AA 嵌入续接与审计复核记录 |

## 关联实施计划与宿主文档

- 实施计划：
  - [2026-10-30-aa-voice-alias-rebuild.md](../superpowers/plans/2026-10-30-aa-voice-alias-rebuild.md)
  - [2026-11-01-combined-aa-caption-component.md](../superpowers/plans/2026-11-01-combined-aa-caption-component.md)
  - [2026-11-01-rayneo-homepage-aa-workbench-card.md](../superpowers/plans/2026-11-01-rayneo-homepage-aa-workbench-card.md)
- Android 宿主侧参考：
  - [EMBEDDED_AA_INTEGRATION.md](../../android-addon/docs/EMBEDDED_AA_INTEGRATION.md)
  - [DSH_OAUTH_SETUP.md](../../android-addon/docs/DSH_OAUTH_SETUP.md)
  - [DSH_CLIENT_VALIDATION.md](../../android-addon/docs/DSH_CLIENT_VALIDATION.md)
