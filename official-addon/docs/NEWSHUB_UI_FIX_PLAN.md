# NewsHub 新闻入口与实验模块清理修复

## 本轮范围

用户实测已交付的 `newshub-clean-20261014-135831` 包没有 NewsHub 新闻入口，而且“日报测试”悬浮仍存在。修复两项既定要求：NewsHub 与 TinyFish 在新闻模块中同级可达；移除“日报测试/导入验证”实验页面与其活跃加载模块。用户布局决定已确认，不重新设计来源协议或自动导入机制。

原包 SHA256：`c3ce2e6ca4758744f75bab85d0180664020dfb81a6a2400a5bd955f24e9ac5ed`。不覆盖或删除原包，保留复现证据。

## 已核实的错误链

- 原包 Runner 仍加载 `TurboIOAIHotDailyStage.dylib` 与 `TurboIOOfficialImportTrial.dylib`，位于保留原 addon 的第157序号之后（158、159）。
- 原包 Stage 的 SHA256 为 `7ee752d3f79aa09b5c3c14fccd3a186a4a535c62e2415fe0c78b651d0c8b3224`，与先前阶段包完全相同，仍存在 `_AIHotStageLoad` 构造函数及实际安装实现。改本地 `AIHotDailyStageUI.m` 不会更改已打包的旧模块。
- `build-newshub.sh` 没有编译 `NewsReader.m`；原 addon SHA256 `7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503` 未变。新行和新 import 没进入旧 addon，不能作为入口已交付的证据。
- 实际导航由 `TIOCreateResearchTabs` 建立四个根页面，新闻导航根实例为 `TIONewsReader`，该实例本身承载 TinyFish 阅读流程；新增的 `TIONewsHubPanel` 类存在，不代表有任何入口调用它。

调度者已运行实际 ZIP/Runner 依赖检查，断言不应加载 Stage 在旧包得到 RED/exit1；两个执行者分别核对了源码调用链、实际 ObjC 类/函数与模块构造函数。字符串检索只是辅助证据，不作为 UI GREEN。

## 实现任务

1. `news-ui-repair`：独立导航 Adapter 只在已验证的研究新闻 Tab 建同级 TinyFish/NewsHub 菜单，复用旧 Reader，不重复定义其 ObjC 类。继承的 UIKit Method 不可直接全局替换；验证精确本类 override 与幂等安装。Hub 打开/返回均测试，取消旧浮动 NewsHub 入口和启动联网副作用。仅撤回前任未入包的无效 NewsReader 增量，不丢弃其他修改。
2. `news-package-repair`：独立 NewsHub build/package，移除两个旧实验模块、标识和安全可移除的末端 Runner 加载项；保留其余 load commands、UUID、序号以及原 addon/官方框架字节。新增独立 `TurboIONewsHub.dylib` 和无秘密 build marker；源冻结后真实重编，不将旧试验打包标识当新产品标识。旧实验 UI 源按精确路径与依赖检查清理，不保留空 Install 函数冒充移除。

## 验收分层

- 旧包 RED：实际包加载 Stage，新闻路由无新菜单的代码调用链。
- 模块测试：真实生产导航 Adapter 对相同四根布局进行替换，两行可点，原 Reader 实例不变、其他三个 Tab 不动；重复安装不重复包装，不修改通用 UIKit 方法。
- 隔离 UI 宿主：使用同一生产 Adapter 和真实 Hub 面板，专用模拟器/测试偏好域、无真实凭据或网络，展示新闻菜单、TinyFish 页面、NewsHub 页面及返回路径。宿主不是官方真机，因此单独标记证据范围。
- 最终 IPA：验证 SHA256/CRC、所有成员变化白名单、完整源清单冻结、独立模块/入口、无 Stage/Trial 实验类和加载依赖，原 addon、HTTP/导航和官方框架字节保持。
- 最后由用户确认 iPhone 实际新闻菜单和无实验悬浮。未见设备结果，不称真机已通过。

## 明确非本轮验收

不把以上修复宣称为全部 RSS/MCP/TinyFish 业务、内容最新性、每日成功判定、官方自动触发/自动保存或去重完成。前任对文件名导致“类型不支持”的断言缺少官方解析器证据；自造 native FilePicker 请求也不自动拥有官方 Dart 导入调用者。不得借修 UI 再重复这些未经验证的承诺。

## 状态

已完成修复及两层复核，旧135831不满足验收，新候选为 `build/newshub-final-20261014-144020/TurboIO-NewsHub-unsigned.ipa`（相对official-addon）。大小425,925,533 bytes，SHA256 `76c7f41a2ba39a56269a8984a5bd50593002f17f84b0e84343a8929eacc4db99`。

调度者实际独立验收：12个包边界测试PASS；旧实际IPA审计RED（4个实验成员及2个加载依赖）；最终IPA审计 `ACTUAL_PACKAGE_GREEN`，所有9,815保留成员SHA/模式一致、Runner改1/删4/增2、源不变与CRC通过；四个旧UI源文件不存在。不是以执行者自述代替验收。

调度者另在专用Simulator `563E7743-E620-4783-A8D7-6805CE0E51DA` 从同一生产导航Adapter和实际ResearchUI/NewsReader/HubPanel重新编译并运行隔离宿主，取得 `NEWS_UI_ALL_PASS: 31 assertions`；无RED标记。日志 `/tmp/tio-news-ui-lead.6EsEG7/`，已结束宿主并关闭专用模拟器。执行者另完成production guard拒绝4断言与half-add失败回滚4断言。两次截图均仅为隔离宿主，不是iPhone实测。

尚待用户覆盖签名安装后核对新闻Tab的两个同级入口、打开/返回以及实验悬浮消失；不扩大为所有新闻业务、文件兼容或无人值守官方导入已通过。
