# AIHOT 官方提词器入库：可行性门槛与受控观察接口

## 结论：门槛未通过

截至本次交付，**没有验证出可由原生 addon 调用的官方稿件创建/读取/去重接口**。没有官方列表新稳定 ID 的动态回执，也没有通过程序完成官方入库。以下 Foundation 组件仅是未接线的安全观察器，不是可用真机探针，更不是自动导入器。未修改 Addon、构建脚本、focus、原 IPA、官方数据库或私人稿件。

北京 09:00 活跃时执行/恢复补发、明确失败 10 分钟后重试、当日完成不重复，均必须依赖后续证实的官方保存与对账接口。现在不能用传稿 ACK、私有缓存、合成测试 ID 或静态 AOT 名称宣称达成；保存状态未知时不能盲重试。

## 事实 / 候选 / 未知

### 已知事实及证据归属

- lead 指定基线：`/Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa`，1.0.5(201)，`com.duriea.turboio.research`。`manual-hud-v2` 存在、focus archive 符号不存在属于 **lead 已核验信息**，本 worker 不再次泛扫 AOT。
- 用户截图/陈述：官方「创建文稿→导入」支持 1MB 内 txt/docx/pdf；公开测试稿已导入，重启后列表仍在、正文正确。属于 **用户报告的手工路径成功**，不是 worker 设备实测，也未提供程序可见的列表稳定 ID。
- `NewsTeleprompter.m:63-71` 构造 `FlutterMethodCall`，通过插件 `handleMethodCall:result:` 发 `rayneonet_sendMessage`；`96-116` 观察业务20传稿模板；`123-129` 把 addon 生成的传稿文件放在私有 addon 缓存。上述是传稿/眼镜会话操作，不是官方 Dart 文稿列表 CRUD。
- `NewsTeleprompter.m:88-93` 只观察发送文件的结果；`108-113` 的 `did` 和 `100-105` 的 `taskId` 是传稿关联字段。**本次不把它们当作官方文稿列表 ID**。
- `NewsCaption.m:14-20,22-36` 同样是业务19字幕收发，回执不证明官方稿库保存。
- `TodoRuntime.m:67-84` 的既有 `MethodHook` 用 `Get(call,@"method")` / `Get(call,@"arguments")` 取参数，最后调用原函数；`91-94` 的 `Send` 得到真实 channel 字符串，使用 `FlutterStandardMethodCodec.sharedInstance` / `decodeEnvelope:`。`185-188` 安装的类和 selector 分别如下。

| 现有入口 | 方向 | 可以看到 | 不可以推断 |
|---|---|---|---|
| `rayneo_venus_sdk_plugin.RayneoNetPluginBridge` / `handleMethodCall:result:` | Dart → native plugin | `FlutterMethodCall.method/arguments` | 入口本身没有真实 channel 名称；不是官方稿库 CRUD |
| `FlutterEngine` / `sendOnChannel:message:binaryReply:` | native → Dart | channel、编码消息、reply block；现代码仅处理名称含 rayneonet 的 channel | 不可把事件回执当作稿库保存回执；并不观察所有 Dart 内部操作 |

### 候选（不是事实）

前轮提供的 `create_document_import`、`SaveDocument`、`TeleprompterMetaDatabase`、`CachedTeleprompterMetaSchema`、`teleprompterManuscriptAbsPathProvider` 和 Dart `Teleprompters` 内部操作名，仅说明 AOT 中存在相关文字/逻辑线索。不能据此发明 Objective-C selector、Flutter channel 名称、参数字段、数据库表或文件路径。

可能存在官方导入、原生文件选择/解析桥，或文稿保存全部在 Dart 内执行。当前没有确认其中任何一条的调用契约。文件选择器返回路径也不等于保存成功。不能在原生入口上发送猜测方法来试错。

### 未知且阻断自动入库

1. 能由扩展安全触发的真实官方创建/保存操作、调用方向、参数编码与回调契约；不要求它必然表现为 Flutter CRUD channel。
2. 保存是否穿过已调查的 native channel；若只在 Dart AOT 内完成，本观察器完全不可见。
3. 列表稳定 ID 获取、标题正文对账、重启后同一 ID 的证明路径。
4. 同版官方去重语义、保存异步完成/迟到回执和不确定结果的对账方式。
5. 在已调查的原生观察、Dart 静态路径与当前授权内，尚不能验证或交付可靠自动入库；未判定所有获授权技术路径不可实现。厂商导入接口、官方扩展回调、目标 Dart save/read 接入或另行明确授权的官方 UI 自动化均只能作为待证候选，分别按真实证据验收。

## 本次组件及安全边界

文件：`OfficialManuscriptProbe.h` / `.m` / `Tests.m`。

- 纯 Foundation；无 constructor、swizzle、runtime hook、Flutter 注册、网络、数据库、稿件文件读取或写入。没有自动安装能力。
- `init` 使用单调 `systemUptime`；测试可注入 clock。默认关闭，只在显式一次 `armForPublicTestWithUserConfirmation:YES` 后有效，每实例禁止再次启用。
- 120 秒逻辑有效期、最多16条摘要；到期限/计数、时钟倒退或非有限值后停止。每次 active/observe 检查有效期；组件不安装常驻定时器。集成层必须在 UI 超时主动 `stop` 并释放探针/临时入口。
- 参数仅短暂借用，不留存原对象。最多深度4/64节点/每容器64项；完整 public title 与全文 body 必须同时精确匹配，仅 CRLF/CR 换行归一。**7392 单独出现不会识别稿件**。
- 匹配完整公共稿的调用可形成 seed。只有 seed 绑定的真实 ID 可以关联后续 metadata；该路径允许数字/bool/null、与 ID 完全相等的字符串等无正文值，任何其他字符串（包括私稿正文/令牌）均拒绝关联。未知 enum 字符串会被保守漏记，不放宽成全活动采集。
- 不从猜测字段提取 ID：调用方只能传已由真实官方契约证实的列表 ID。没证实就传 `nil`；记录明确 `id_session_hmac_sha256="none"`。不能把 synthetic ID、`did`、`taskId` 或任意传稿 UUID 填到生产列表 ID。
- 摘要仅 channel/method、顶层安全 ASCII 键名、fixture 匹配 bool、关联 bool、公共 fixture SHA256、会话随机盐 HMAC-SHA256 ID；不含参数值/正文/原始 ID/token/路径。channel/method 必须由实际 wire 身份提供，不能使用用户数据。channel 未知时以明确哨兵 `none` 表示未知，不能编造实际通道名。
- ID 随机会话盐只在内存中存在，stop 清除；**跨进程/重启的摘要不同，不能用于证明重启稳定 ID**。后续需另有用户授权的官方 ID 对账/验证路径，不能因此保留跨重启盐或原始私有 ID。
- `records` 最多16条只读摘要，stop 后仍可交付这些安全摘要；集成不可导出其它原始日志。
- `forwardChannel:method:arguments:stableID:originalOperation:` 吞掉的仅是观察器内部异常，失败即关闭；originalOperation 精确调用一次，原操作异常继续抛出。原始参数、返回/异步 callback 由调用方闭包原样传递，不吞回复、不改保存逻辑。

## 公开 fixture：动态测试必须明确复制全文

截图排版可能有不同换行，下一次授权测试统一复制以下内容；不推断以前导入稿的隐含排版。

标题：`TurboIO 日报入库测试`

```text
这是一份公开合成测试稿，不是真实新闻。
用于验证官方提词器稿件的保存、重新打开和持久化。
校验码：7392。
```

测试源常量是公开文本，测试程序使用的 `synthetic/test` / `syntheticSave` / `synthetic-list-id` 只存在于 synthetic tests，绝不是发现出的官方 API。

## 接线说明（尚未实施）

接口：

```objc
TIOOfficialManuscriptProbe *probe = [TIOOfficialManuscriptProbe new];
// 仅一次用户明确操作触发；不可在app启动/生命周期恢复时自动arm。
[probe armForPublicTestWithUserConfirmation:YES];
// channel来自真实wire；尚未知则 "none"；stableID未证实必须nil。
[probe forwardChannel:channel method:method arguments:decodedArguments stableID:nil
    originalOperation:^{ /* 原调用和原reply原样执行一次 */ }];
// 超时、用户停止、切后台时均关闭；收集仅probe.records。
[probe stop];
```

这段是 **接线声明**，不是已集成的产品代码。方法观察入口可以复用 `TodoRuntime.m:74-84` 的受控参数取得步骤，但其业务20/传稿事件通常不带正文，无法 seed；不能为此读 `filePath` 的私稿文件。反向 engine 入口可提供真实 channel，但当前只解码 rayneonet envelope，不能保证存在官方保存回调。

集成人员需要另外授权的 Addon/UI/构建边界：一次显式测试按钮、120s状态/停止按钮、只对指定实例/消息入口追加短时观察、相同原 callback 透传、仅摘要出口；不要新装全局永久 hook 或记录全部 channel 活动。没有已知目标入口时应先请求一个最小范围的候选入口授权，不能把权限扩大为全 Flutter/全 app 抓包。

**目前没有可靠 save class/selector/channel 可给出。** 精确列出的 Rayneo/engine selector 仅提供现有 transport 观察点，不提供官方入库能力。

## 下一次动态验证：最小用户步骤与可见结果

本 worker 没有安装新 app、启动 app/私人会话、下载/扫描容器、读取账号/密钥、连接云端或访问实际设备数据。`devicectl` 仅查看 help。

1. lead 先审批单独的探针接线/测试包范围；本次不修改已有 IPA。告诉用户观察仅针对这一份公开 fixture，120秒/16条且不保存正文。
2. 用户主动打开官方稿件 UI。公开测试稿若已存在，**不要静默重新创建同名副本**；先确认是否授权创建一次第二条或使用既有测试稿编辑/保存。需要「新稳定ID」验收时必须明确授权一次新建并记录安全前后差异。
3. 用户点击一次观察开关，复制上述标题和正文，经官方创建文稿→导入/保存操作一次；不开始眼镜传稿/私人语音会话。
4. 停止观察。只取得 `probe.records` 的最多16摘要，用户核对官方列表出现、打开正文正确；如果没有记录，报告「未观察到匹配的平台消息」，不能报告「未保存」或「不存在通道」。
5. 只有真实官方契约给出列表 ID、并完成标题/全文匹配，才能称「新ID已对账」。未获得列表 ID 的结果仍为 `unknown`，不能用传稿 ACK 补齐。
6. 用户重启 app，再次看到相同测试稿且全文正确；同一稳定 ID 需要独立、已授权且可信的官方对账证据。当前会话 HMAC 无法做跨重启关联，用户看到标题不能单独替代稳定ID证明。
7. 形成分开的结果：`public fixture channel observed`、`official new ID confirmed`、`title/body confirmed`、`restart persistence confirmed`。四者不能互相替代。任一官方入库证据缺失，门槛保持未通过，停止自动补发/未知重试。

## 可复现证据命令与测试

只读证据（所有路径以根工作区为基准）：

```sh
sed -n '63,116p' official-addon/NewsTeleprompter.m
sed -n '14,36p' official-addon/NewsCaption.m
sed -n '67,94p' official-addon/TodoRuntime.m
sed -n '185,188p' official-addon/TodoRuntime.m
xcrun devicectl help device info
xcrun devicectl help device process
```

CLI help 显示 `device info` 有 details/lockState 等元数据入口，也有 files/apps/processes 等更广入口，本次均未调用；`device process` 有 launch/resume/signal 等操作，不等于现成无敏感日志出口。`--log-output` 是 devicectl 自身日志，不是官方 Dart 保存回执；不要为取日志启动 app 或扫容器。

只读基线直接 host 导出符号检查曾执行：

```sh
xcrun nm -g /Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/Payload/Runner.app/Runner \
  | grep -E 'Flutter|Document|Teleprompter|Manuscript' | head -45
```

该 bounded 输出只有插件/Flutter 相关导出（如 FlutterTts/Taudio），**截断的符号列表不能证明全包不存在 CRUD**，本次也没有据此做缺失推断或继续扩扫。

独立测试（不改 `test.sh` / `build`）：

```sh
cd official-addon
out=$(mktemp -d /tmp/tio-official-probe.XXXXXX)
xcrun clang -fobjc-arc -fmodules -Wall -Wextra -Werror -framework Foundation \
  OfficialManuscriptProbe.m OfficialManuscriptProbeTests.m -o "$out/official-probe-tests"
"$out/official-probe-tests"
```

自检覆盖默认关闭/无授权不启用/一次授权/完整公开稿匹配/换行归一/仅码拒绝/其他标题拒绝/参数及原ID脱敏/会话盐差异/seed ID保守metadata关联/私稿替换拒绝/递归和条数限额/自动过期/时钟异常/原操作透传一次/观察异常隔离/原异常不吞。实际：Foundation `-Wall -Wextra -Werror` 编译与全部断言 PASS；连续20次重复 PASS；`xcrun --sdk iphoneos clang -target arm64-apple-ios13.0 -isysroot "$(xcrun --sdk iphoneos --show-sdk-path)" -fobjc-arc -fmodules -Wall -Wextra -Werror -fsyntax-only OfficialManuscriptProbe.m` PASS。最终 macOS executable：`/tmp/tio-official-probe.ZOzsXe/official-probe-tests`。四个新增文件 `git diff --no-index --check /dev/null <file>` 无空白警告（新增 diff 返回1属有差异）；未修改其他模块。**不将 synthetic PASS 当作官方入库 PASS**。
