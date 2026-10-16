# NewsHub 导入验收失败：生命周期修复与无 UI 契约阻断

## 用户反馈与硬门槛

当前验收包 `build/newshub-final-20261014-144020/TurboIO-NewsHub-unsigned.ipa` 已修复入口，但用户仍需每份进入官方提词器点“导入文稿”；两篇先科技速递后 AIHOT，后者显示“格式不支持”。用户询问是否为 AIHOT 显示标题空格导致。

用户本轮明确选择并确认：**完全无页面跳转、不逐份点击；各来源独立稿；实际 UTF-8 TXT 不超过 1MB；只有真实官方保存和回读才算成功；未知不重复创建。** 未取得真实无 UI 入口时报告阻断，不以 UI 自动点击、直接数据库写入或自建 Flutter callback 冒充完成。

## 已复现的真实代码缺陷

调度者独立执行旧真实 `TIOImportQueue.m` 的公开稿 delayed-read harness，得到9项RED：

- 第一份原生 handleResult 同步返回后文件仍存在，最后一份却因队列 `disarm` 立即删除其目录，下一 runloop 读取为 `NSCocoaErrorDomain:260`。
- 交换顺序：AIHOT大小的公开稿首份可读、科技稿末份失败；去标题空格、交换来源、交换正文均不改变尾项失败。
- 最小单份单字节 ASCII `x` 也在同步返回后消失。
- 这些物理文件本来就是ASCII文件名，显示标题空格不参与 basename。
- UTF-8实际 999999/1000000/1000001 字节均曾被接受，说明旧队列没有字节上限检查。
- 原生返回时 `completedCount` 已加成功，即使后续读取失败；并非官方入库确认。

旧RED报告和完整对照：`/tmp/newshub-lifetime-news-import-lifetime/REPORT.md`；调度者独立旧RED输出 `/tmp/newshub-lifetime-lead-red.log`。所有正文为自编公开fixture，刻意模拟83/3890字符但不是用户实际正文。文件缺失已证实，**官方本地化“格式不支持”是否由此读失败引起仍未取得消费者证据**。没有证据说中文或空格必然导致类型错误。

## 本轮代码保护和独立验收

- Queue无已证headless backend时 `headlessImportAvailable=NO`；生产 arm 拒绝且 trigger不导航、不dismiss、不装hook，不退回逐份手工导入。
- 已供稿的未知文件不在 disarm/clear 后删除；process内尝试预算不靠clear重置；原生supplied与officialSaved分开，未证官方ack不加成功。
- 旧供应路径仅存在显式测试宏 `TIO_IMPORT_QUEUE_LEGACY_TESTING`，在该测试seam检查UTF-8无损、非空、<=1000000bytes保守门槛、basename及symlink安全；该测试通过不是生产无UI入口已实现。
- Panel清楚分开生成、原生交付和官方保存，后台入库未接通时禁用，不再显示“全部已导入”或要求逐篇进入官方页面。
- NewsHub的TinyFish来源先前只是固定模板，未调用真实检索。已删除伪新闻成功路径，未接入时明确unsupported且nil article。独立旧TinyFish页面不变；不能将其现有能力等同NewsHub适配已完成。

调度者实跑：

```sh
bash official-addon/test-queue-lifecycle.sh
```

结果：最终冻结后调度者再次独跑 production 5、显式legacy测试59、symlink根1项全部PASS；两种模式arm64 syntax通过，无IPA。测试覆盖下一runloop文件、disarm/clear、超限不落盘、未知异常预算、重复arm不重复落盘、native不等于save等。调度者另复跑 frozen-old `run-old-red.sh` 为9失败、separate `run-legacy-green.sh` 为0失败；测试宏和case ID隔离差异记录于执行者的 `IMPLEMENTATION.md`，不覆盖原RED日志。

编译执行者随后在 `/tmp/news-compile-validation-JPTtcz/production-build` 用现有7个生产源完成全模块真实arm64/iOS15链接和未更改的modulevalidator校验；production dylib SHA256 `e6e786d7a5b3525f61ac6b38136470d5f60150fe0c3275ce08481006537b7625`。无测试legacy FilePicker入口或Stage/Trial代码、实际headlessAvailable返回NO；Navigation/Entry原SHA不变。此项是可链接/安全关闭证据，不是无UI官方功能成功，未打包IPA。

真实Source `TIONewsSearchUnsupportedTests.m` 独立编译运行PASS，不返回占位稿／假来源。真实Panel隔离UIKit宿主独立重编运行得到 `HEADLESS_UI_ALL_PASS`、无RED，日志 `/tmp/tio-headless-panel-lead.5YJtmk/`；使用spy queue/scheduler确认按钮不arm、不enqueue、不导航、不dismiss，仍可预览稿。专用Simulator已关闭，未触手机。

## 仍未完成的核心能力

详见 `NEWSHUB_HEADLESS_CONTRACT.md`。已有FilePicker路径只能替换真实Dart请求的continuation；自行发一个native请求或开启通用headless Flutter engine不能证明官方库保存。完全无UI的官方 import/save/read入口、合法稳定DocID和对账契约仍UNKNOWN，不是证明不存在。

需要同版官方接入契约，或匹配App SHA的少量Dart import/save/read函数与对象映射，并进一步验证native可调用方式、上下文、成败和读回。原大型AOT分析预算已用完且三次未ready，不以第四次同样全分析或泛监听制造进展。

**本轮没有交付新的“全自动成功版”IPA。** 这些源码保护和测试尚不是用户所要求的无UI官方入库实现；此前已安装包也不会因本地源码更新而自动修复。后续若需交付安全降级包，应明确其仅保留生成预览、禁用未验证入库，不能把该降级当最终需求达标。
