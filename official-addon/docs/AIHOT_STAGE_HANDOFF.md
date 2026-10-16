# AIHOT 每日日报：阶段交接与未完成门槛

## 交付结论

完整每日自动推送 **尚未完成，不可按自动化成品验收**；官方自动入库、日调度与发送状态账本仍未交付。用户随后明确选择先交可安装的阶段实测包，现已实际编译并复核手动获取/预览/TXT导出版本：`build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`（路径相对 official-addon）。详见 [阶段安装说明](AIHOT_DAILY_STAGE_TEST.md)。阶段包不启用官方自动入库或09:00自动发送，也不能以私有缓存、传稿或合成测试替代最终官方列表要求。

用户已实际验证官方 UI 支持 1 MB 内 txt/docx/pdf；公开测试稿能手动导入并重启保留，正文正确。这项人工验证已通过，不需重复。缺口是程序可安全触发的官方保存与回读契约。

## 已完成范围

| 范围 | 状态 | 证据/限制 |
|---|---|---|
| 基线 IPA | 只读核验通过 | 1.0.5（201）、根版 `manual-hud-v2`；原包 SHA256 未变 |
| AIHOT 采集/转稿 | 独立实现并复审 | 直接官方按日期 JSON，RSS 地址仅同源元数据；不是通用 RSS/MCP 框架 |
| 模拟与编译 | 最终复跑 209 checks / 0 failures | macOS Foundation 离线执行、iOS arm64 syntax；不代表真机网络/官方入库/播放 |
| 受控观察 collector | 离线安全组件通过 | 默认关闭、120秒/16摘要；未接线，不能在手机使用 |
| 诊断测试包 | 效用门槛未通过，已停止 | 已知原生通道仅 transport，未证明观察到官方编辑/保存；没有新 IPA/dylib/UI |
| Dart 静态调查 | 三轮收敛为接入证据不足 | snapshot 身份与候选名称可核对，未恢复目标函数/参数/schema/官方 ID/回读 |
| 每日调度与双重去重 | 待入库门槛通过 | 尚未实现09:00、恢复补发、10分钟重试、官方入库与发送完成账本 |

正确端点 `https://aihot.news/openapi-v1.json` 的追加复核确认 nullable canonical、required original 与可选 attribution 合同。空刊 lead.title/leadParagraph 为普通 string，无固定标题枚举；原固定安静文案依赖已由实现成员修正，经独立 reviewer 与调度者分别复跑为 209 checks / 0 failures。替代标题的有效空刊可读，空/null/错误类型导语仍拒绝，指令样式文字不执行；iOS arm64 strict syntax 通过。

独立证据复审确认手动与自动、零引用与无调用、合成测试与设备验收均需分开。文档已收敛后续材料为候选路线择一闭环，不要求完整逆向材料或先穷尽全部静态路线。尚未取得官方 save/read 操作与稳定ID，完整每日推送不可验收。

## 已确认且不能丢失的产品要求

- AIHOT 官方日报、眼镜文字，不新增语音或通用 MCP 管理。
- 必须先进入官方提词器持久列表，一期不重复创建。
- 北京时间每天09:00，App运行时尝试、恢复后补发当日；不承诺系统挂起时精准执行。
- 未发布、未连接等确定失败每10分钟重试，遵守更长 Retry-After，不抢占其他会话。
- 同次完整收稿和开始阅读确认成功后持久完成，当天不重复自动发送。
- 已提交结果未知时待确认，不盲目重试创建或播放；跨日不追播旧稿，亦不忽略可能占用眼镜的旧未知会话。
- 保留当前手动新闻和导航；不能悄悄切换 focus 或覆盖原 IPA。

## 代码与复现入口

- `../AIHotDaily.h` / `.m` / `Tests.m`：独立 Foundation adapter，网络/日期/来源/大小/ETag/取消处理，无日调度或官方入库。
- `../OfficialManuscriptProbe.h` / `.m` / `Tests.m`：离线 collector，完整公共 fixture 限定；当前同次标题/正文匹配仅是这条观察路线的安全规则，不是所有接入路线的必要条件。

```sh
bash official-addon/test-aihot-daily.sh
bash official-addon/test-official-manuscript-probe.sh
```

原 root 新闻基线三组已独立执行通过：NewsCoreTests、NewsPresentationTests、NewsTeleprompterTests；都是模拟，不是新功能设备验收。

## 调查与决策记录

- [需求、任务与验收](AIHOT_DAILY_DELIVERY_PLAN.md)
- [官方入库可行性与观察边界](AIHOT_OFFICIAL_IMPORT_FEASIBILITY.md)
- [为何停止无效诊断包](AIHOT_OFFICIAL_IMPORT_PROBE_TEST.md)
- [三轮 Dart 契约调查](AIHOT_DART_MANUSCRIPT_CONTRACT.md)

证据名称、零引用与工具缺口不能推导官方能力不存在。用户的硬要求是自动保存并可回读，不必限定为 Flutter CRUD channel；可行候选应按其真实契约分别验收。

## 重启实施所需的最小闭环证据

按候选路线择一取得材料，不要求一次提供完整源码、数据库结构、所有符号与解析器：

1. 官方或获授权的创建/保存及回读接口，能由本 addon 触发，并返回稳定稿件标识。
2. 或目标模块源码/匹配静态解析输出，足以核对 save/read/ID 调用及编码；仅 Dart 函数地址仍不足。
3. 或另行明确授权的短时动态验证，只针对公开合成稿、真实候选 save/read 边界及限定次数。跨消息内容关联、跨重启 ID 对账或一次新建验证必须独立设计与授权；不读私人稿件，不扩大为全量 hook。

每条路线至少需核对 title/body 输入编码、成功/失败/异步结果、稳定 item ID、官方列表/正文回读、重启保持、重复提交与未知结果对账。手动编辑已有稿不能替代自动创建验证，眼镜 ACK 不替代官方入库回执。

## 完整性与风险

基线 IPA SHA256：`de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24`。工作区阶段文件未 commit；源 IPA、旧 addon、当前导航与模型配置未修改。阶段实测新IPA为425,901,987 bytes，SHA256 `554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`。调度者独立验ZIP CRC、9815未改成员字节、Runner依赖/UUID与thin arm64 iOS15，原包hash未变；复跑Source209/StageCore47/Package14全部通过。新包须用户沿用原签名工具重新签名安装；真机入口、网络、系统TXT分享、官方手动导入和旧功能回归尚未验。旧110220/111120候选不可安装。

公开 AIHOT API 的技术可访问性和保留署名不等于公开再分发/商用许可；外部商业使用需按服务方条款取得授权。暂存二进制调查文件在 `/tmp` 不是长期保存保证，报告保留有界证据与命令。
