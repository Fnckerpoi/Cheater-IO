# AIHOT 日报阶段实测 IPA：确认范围与交付计划

## 用户最新确认

用户明确选择“先交付阶段实测 IPA”，纠正此前只有采集代码、尚无可安装包的交付缺口。当前优先目标是实际编译、生成、复核并交付安装包，不再继续调试器附加。

此前设备只读前置检查确认有线连接、paired、Developer Mode enabled、DDI available，以及已安装 research 1.0.5（201）；没有执行 LLDB 附加，没有启动 App 或读取稿件。它不代替阶段包安装验证，也不证明当前签名允许调试。

## 当前阶段与最终目标的区别

| 阶段包要交付 | 当前明确不启用 |
|---|---|
| 手动获取 AIHOT 当日日报 | 官方稿件库自动创建/保存 |
| 手机全文、来源和阅读稿预览 | 每天09:00自动发送 |
| UTF-8 TXT 导出供官方手动导入 | 10分钟自动重试/双重去重 |
| 保留现有 HTTP/模型/导航/手动新闻 | 设备监听、调试探针、TTS或麦克风 |

最终要求不变：必须自动进入官方提词器持久列表，再按北京时间09:00和防重复规则发送。该最终入库接入仍未验证，阶段包不能冒充成品或把手动导入当作自动完成。

## 基线与保护

- 输入：用户指定 `TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa`，1.0.5（201）。
- 原 IPA SHA256：`de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24`。
- 原扩展 SHA256：`7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503`。
- 新增独立 sidecar dylib，不替换原 `TurboIOPrivateAddon.dylib` 字节，不重建原导航/HTTP扩展，不切 focus。
- 不覆盖原包，不自动重签、安装、启动或附加调试器；用户沿用已有签名方式处理新 unsigned IPA。
- 不要求删除现有 App，避免本机稿件丢失；签名工具与应用标识变化造成的数据保留风险须注明。
- 用户私用基线包及派生包不公开上传；不读取包旁的私人模型配置文件。

## 并行任务书

### task-12：日报阶段界面

新增 `AIHotDailyStageUI` 与最小 `AIHotDailyStageCore`/Tests，独立链接已复审 `AIHotDaily`。自包含 UIKit 入口显示“日报测试”及阶段限制；仅用户点击时联网。取消、忙状态、离开与迟到回调须安全处理，不拿旧刊冒充当天。显示来源并生成安全 UTF-8 TXT，系统分享/保存到文件后由用户在官方入口手动导入。

禁止改旧 App 业务、发送眼镜控制、全局 swizzle/调试 hooks、读取私人稿库。导出内容与路径策略在真实实现后记录，不用设计描述冒充已完成。

### task-13：编译与打包

新增独立 build/package/test 脚本和操作说明。可与 UI 同时准备脚本及测试，必须等真实源完成再实际 arm64 链接 `TurboIOAIHotDailyStage.dylib`。Runner 新增 `@rpath` 依赖，保留既有依赖与原模块。若负载命令空间不足必须可靠处理或明确失败，不截断二进制。

输出新目录 `official-addon/build/aihot-daily-stage-<timestamp>/`，生成 unsigned IPA、验证 manifest 与 SHA256。不能以 syntax-only、ZIP 文件存在或模拟 UI 代替真实 dylib/IPA 编译。

## 验收

1. 源模块 209 离线检查通过（加两份公开 JSON 为211）；阶段 helper 有独立测试，UIKit arm64 实际链接通过。
2. 当天识别/未发布/错误/取消/导出边界明确；不自动发起日报请求或眼镜操作，不采集私人数据。
3. 阶段入口与原研究入口互不遮挡；可进入、退出、预览和导出。真机未验处明确标注。
4. 新 dylib 与 Runner 依赖/架构/旧依赖/UUID/签名清理核对；原扩展及其他未改成员字节保持一致。
5. IPA ZIP CRC、差异白名单、SHA256、大小与真实输出路径已验证；源 IPA 散列不变。
6. 调度者读关键实现和打包改动、复跑测试，独立核验产物后交付；不凭 worker 陈述宣布完成。
7. 用户步骤简短明确：已有签名方式安装新包 → 日报测试 → 获取 → 查看 → 导出TXT → 官方手动导入。新包中未完成的最终功能明确不可用。

## 当前状态

阶段实测包已实际编译、打包并通过调度者独立复核。唯一交付路径：

`official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`

- 大小：425,901,987 bytes（约406.17 MiB）。
- IPA SHA256：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`。
- 新 sidecar：193,376 bytes，SHA256 `7ee752d3f79aa09b5c3c14fccd3a186a4a535c62e2415fe0c78b651d0c8b3224`；thin arm64 / iOS15，实际动态链接成功。
- 独立复跑：Source209检查、StageCore47检查、Package14测试全部通过。
- 调度者独立逐成员复核：新增2、修改1（仅Runner）、移除178（签名资源）、其余9815成员字节一致，ZIP CRC全部通过。原Addon/App.framework/Flutter.framework散列与基线一致，原IPA散列未变。
- 分享生命周期有界返工通过；最终包绑定冻结源码，旧110220/111120候选均标superseded不可用于安装。
- 同目录有操作说明、manifest和源码散列记录。用户用已有工具重新签名后安装，不删除旧App、不改bundle/version/build。

代码/构建/打包已验，不宣称已在手机成功运行。手机入口、获取、取消、系统保存TXT、官方手动导入与原导航/HTTP回归由用户下一步实测；官方自动入库与每日自动发送仍未完成。
