# 仅公开稿的最小官方导入动态验证计划

> 状态：**规划，未执行**。本文件不授权 attach、hook、手机运行、probe实现或签包。Dart静态调查先完成；只有仍缺指定证据、观察技术与效用通过review、用户确认范围后，才可另立实现/执行任务。不能通过原生自建callback假装自动保存。

## 1. 目的、已有结果与执行门槛

用户此前阶段1–6已实测：stage签名安装、取得AIHOT稿、预览、导出TXT、官方手工导入及眼镜阅读成功。**不重复这些功能测试**。本计划仅补“官方import调用者如何消费原生选择结果并保存”的证据缺口，最多一次未来带观察的公开稿成功导入；取消/重复等额外动作不是默认步骤。

输入身份以 [REA_TELEPROMPTER_NATIVE.md](REA_TELEPROMPTER_NATIVE.md) 的stageIPA SHA与file_picker SHA为准。若安装包/官方组件与其不同，先声明不匹配，不沿用静态地址；地址只用于核验函数，不能直接作为ASLR运行地址。

执行前全部门槛：
1. [待用户操作] 确认仅这一份公开AIHOT TXT、官方提词器导入入口和短时观察；明确何时开始/结束。用户继续手动选择和保存，观察者不代写稿库。
2. [UNKNOWN] 技术方证明可在该准确App实例观察真实FilePickerPlugin请求/回调及至少一个新增消费者事实；调试授权、设备attach能力尚未验证，本任务未attach/launch，也未调用REA runtime工具。runtime guide描述的是浏览器/Electron，不能推定支持iOS Flutter消费追踪。 **执行效用硬门槛：先指出可新增的官方consumer事实和获取位置；如果只能再看picker成功、用户保存UI或重启黑盒，则不执行本轮动作。重做已过阶段1–6不能验证自动create能力。**
3. [UNKNOWN] 静态Dart结果是否足以替代动态；若足够即不执行本计划。当前budget timeout不等于接口不存在。
4. [KNOWN/约束] 单会话、最长120秒picker事务；超时停用采集但不取消官方操作、不伪造callback。用户暂停可提前结束；重启后不恢复观察。
5. [UNKNOWN] 没有明确可观察Dart save边界时，必须提前告知可能只得到native证据。仅两个传稿hook、Native picker成功不足以自动入库闭环；不得包装成可保证成功的probe。

## 2. 分层证据与状态

| 层 | KNOWN | UNKNOWN / 本轮要补 | 待用户操作 |
|---|---|---|---|
| L0 公共源文件 | AIHOT可公开、可导TXT；固定manifest由用户确认 | 本次导入文件是否就是该manifest对象 | 用户确认并选唯一公开文件 |
| L1 native请求 | FilePickerPlugin.handleMethodCall:result: 0x474c，channel miguelruivo.flutter.plugins.filepicker | 官方Dart真实method=any/custom、filter、withData、multiple值 | 从官方导入UI发起一次 |
| L2 native回包 | array of path/identifier/name/size/optional NSData bytes，取消nil | 此次实际值类型/shape，是否匹配公共hash | 用户选文件或自愿取消 |
| L3 Flutter/Dart消费 | native callback绑定当前真实请求，并不广播入库 | 实际codec、decode字段、正文/标题映射、已有官方consumer continuation | 选择后原流程继续，不人为重放 |
| L4 官方保存 | AOT字符串有metadata/file/list候选，非动态接口证明 | save commit/failure边界、合法DocID、刷新/回读 | 按官方UI保存；展示唯一公开标题详情 |
| L5 重启/重复 | 以前手工路径可用；临时同名文件remove/move不等于稿件去重 | 本次公开项重启是否存在；官方重复策略 | 如确需，仅用户重启回读；重复另授权 |
| L6 准备/传稿 | 现有addon源码可见业务20/文件传输观察 | 与刚保存官方项的映射，字段是否是真DocID | 仅用户已计划读此公开项时顺带观察，不增加读镜测试 |

L1–L2成功不能声称L4成功；L6播放/收稿成功也不能替代官方列表保存或重启回读。

## 3. 公共稿匹配：只摘要，不获取其他稿件

在用户已导出的公开TXT上先建立manifest（不是读App容器）：随机run标签、唯一basename、字节数、原始文件SHA256、UTF-8编码/BOM与换行事实、预期公开标题、首尾公开定位标记。不在实际正文中自动加标签；使用原文件现有公开内容或用户自选唯一名字。

只借用（borrow）当前请求args，禁止深拷贝/持久化整个dictionary、stack、URL或任意payload。真正开始日志的规则：
- L1参数先在当前短事务内暂存allowlist的值/shape，不输出。实际回包只有**单文件**、basename与manifest精确匹配，且可验证原始字节SHA256/长度一致，才将L1/L2公开摘要落盘。多选、未知类型、私人路径或不匹配即不摘要并停用该事务。
- withData=true且真实回包含bytes：仅对这个候选公开对象计算hash/长度，结果匹配后输出hash及类型，不输出原始bytes。NSData→Dart Uint8List不能预先假定，记录各端实际类型。
- withData=false：不遍历容器；默认不读返回path。先请求用户确认该公开选择；若精确单一文件hash确认确不可替代，需**单独批准只读该次返回的一个公开临时URL**，仅做常规文件身份/大小/上限校验和流式hash，不列目录、不跟随symlink、不搜索其他文件；无法获得许可则标UNVERIFIED_SOURCE而非强关联。
- path/identifier只输出类型、存在性及“是否是同一公开文件”的布尔，不输出完整路径/URL。picker.identifier来自URL.absoluteString，**不是官方DocID**。
- 原始TXT hash与官方解析后文本hash分列。如官方改换行/去BOM/裁剪，不能自行猜归一化使hash相等；只在真实consumer映射有证据或用户公开编辑器回读后记录解析转换。公开标题与正文对应关系未知时保留UNKNOWN。
- 取消没有选中公共文件，最多记录用户此次授权事务的CANCELLED事件；丢弃暂存args，不保留未匹配文件名、路径、字节、其他列表标题。

有限日志仅run标签、相对时间、target identity、selector/channel、已公开匹配的method与allowlist参数、key/type/length/hash、终态及匹配布尔。禁止账号、deviceId、token、账号配置、私人稿正文或列表全屏；截图只取公开测试行/公开编辑器区域，避免其他标题入镜。日志保存时间由用户批准，建议review后删除独立日志；本计划不执行删除。

## 4. callback不干预与生命周期

观察点是**官方真实Dart调用者**发起的FilePickerPlugin.handleMethodCall:result:及其原有result，不是TurboIO自调picker、新建请求或主动invoke didPick。

状态仅观察：DISARMED → ARMED_ONE_PUBLIC_TRANSACTION → REQUEST_BORROWED → PUBLIC_MATCHED/CANCELLED/UNMATCHED/ERROR/TIMEOUT → DISARMED。全局禁止长期采集；最多一个pending，原始App调用照常进行。

- 原method/args对象原样交原实现；观察器不修改过滤条件、withData、多选、method或error。
- 不抢/覆盖plugin pending result（native已知offset0x10只是证据，不作为写入地址）。若未来允许包装原callback，先验证block ABI/retain生命周期；每次真实原callback调用只透传一次**同一个结果对象**，不copy/改造/补字段、不提前完成、不丢错误。观察摘要不得延迟原透传。
- 观察异常、日志故障、hash失败走fail-open：原callback仍按原语义执行，采集停用。不得把观察异常转换成FlutterError。
- 不人工调用callback，不伪造成功/取消，不自动重试。若观察到原实现异常重复回调，停止摘要并保留原程序行为，不通过observer重复调用或人为吞掉回调来“修复”。
- 复入：原生已知第二请求返回multiple_request，但未清首callback。观察器不另发请求、不主动制造复入；若自然发生，标REENTRANT_UNRESOLVED、丢弃未匹配采集并停用，不解释为第一个请求被取消。
- timeout只停用observer；迟到原callback仍需原样一次透传，不能因为采集结束而释放掉所需原block或替官方取消。如何安全卸载wrapper有待实现review，未验证不得部署。
- 观察结束后恢复原转发链、清除短期借用引用；不写metadata/file/list、不影响用户导入。

## 5. 最小未来一次动作和验收

1. [待用户操作] 用户确认manifest公开对象，准备仅该TXT，不显示私人列表内容；在官方导入入口即将进入时短时arm。
2. [UNKNOWN→观测] 真实请求保存allowlist：method、allowedExtensions、allowedUniformTypeIdentifiers、allowMultipleSelection、withData及字段类型；native验证这些确由官方调用者传入，而不是观察者构造。
3. [待用户操作] 用户仅选择该公开文件。观察器精确匹配后出L1/L2摘要，原callback原样透传。
4. [UNKNOWN→观测] 只有已证实可用的定点consumer/codec观察才能记录Dart实际解码：收到哪个key、对应类型、原path读取或bytes消费、成功解析后的公开正文hash与标题来源。不得泛hook所有channels/文件I/O/所有Dart函数。编辑器出现公开文本仅为“官方UI消费成功”证据，不是runtime codec类型的证明。
5. [待用户操作] 用户在官方UI保存。如果确有已识别save边界，限定该run记录调用、成功/失败、合法返回ID来源与唯一公开列表回读；没有该边界则只记录用户保存动作、公开行和编辑器详情，以UI持久化黑盒证据命名，不称API闭环。
6. [待用户操作，必要时] 用户强退重启并仅回读刚保存的公开项；观察器已关闭，不跨重启采集。公开标题+正文/长度一致证明这个样本持久，不证明全部事务/自动接口。
7. [可选，非默认] 用户原本要阅读此稿才观察L6；不额外传稿、不复制此前阶段1–6测试。完成即disarm，不持续等业务20消息。

验收输出逐项PASS/UNKNOWN：官方method/filter/request；公共hash关联；native回包shape；codec消费；Dart解析映射；save边界；官方列表回读；重启回读。native success标NATIVE_PICK_SUCCEEDED，不能命名MANUSCRIPT_SAVED。只一次成功样本不能把取消/重复标PASS。

## 6. 取消、错误和重复：如何验证而不扩scope

静态已知取消nil、multiple_request、Unsupported file extension、unknown method not implemented；无需默认重跑故意错误用例。用户自然取消则只收该授权取消终态，放弃成功样本，本次不自动再试。

官方重复策略不能从picker临时同名remove/move推断。要验证需要在本次用户操作结果公开呈现提示，或最小追加一次**同一公开稿**的用户手工导入：观察官方是否新行、覆盖、拒绝/改名；只比较该公开项前后，不能遍历全稿库。此额外动作需另授权，不自动执行。无此材料写UNKNOWN_DUPLICATE，不以唯一文件名样本推断幂等。

## 7. 业务20层：仅准备/传稿，不冒充存储主键

直接相关根版本源码证据（对应stage的root manual-hud-v2，不引用focus能力）：`official-addon/NewsTeleprompter.m:88–93`为文件回执shape，`:96–118`为rayneonet_sendFile的taskId/filePath关联及rayneonet_sendMessage/businessId20的envelope.type、json did/action/scroll/total观察，`:129–136`自有prepare生成UUID did并Send(2,j)。`official-addon/TodoRuntime.m:74–84`转交现有观察器，sendFile回调在调用original(response)前异步交TIONewsTeleObserveFileResult；`:86–88`转交incoming事件。这些是root addon源码观察候选，**不是stage官方业务20的完整反编译合同，也不证明官方稿库save**。

若此公开稿本来要发送眼镜且另授权观察：只对已精确匹配公开正文的这次传稿关联run标签、type/action/total与校验，taskId/did至多给run内不逆映射标签或相等布尔，不收deviceId、不记完整filePath。不把FNV/checksum等同SHA256；不能因total相等即关联正文，必须已批准的公开bytes一致或用户明确同一公开项上下文。

`picker.identifier` = file URL；`did`/`taskId` = 当前传稿候选标识。**二者都不是已验证官方DocID**。只有真实Dart保存返回/metadata契约明确ID来源与该公开项关联才可称DocID；否则UNKNOWN_ID_MAPPING。眼镜file result/code7/播放确认只证明传输层，不证明save/list/restart。

## 8. 若save/consumer不可观察：最小追加材料与停止条件

按最小量请求，而非读全DB或泛hook：
1. 公开测试行及其详情的用户裁剪截图/屏录、保存后重启同一公开项回读（黑盒持久证据，不是API实现）。
2. 官方Dart exact import/save函数地址与可用符号、局部对象池/函数体证据，或该版本对应的少量源代码/开发方文档；只需该路径，不必整个源码。
3. 获授权且技术可行时，在已识别的**一个consumer/save返回边界**观察公开稿run的参数shape与结果，不采全DB、事务日志或账号请求。未识别边界前不得执行泛观察碰运气。
4. 正文转换不明时用户从该公开编辑器复制/导出**这一份公开稿**以比对hash；不是容器枚举。

若只能观察picker+业务20，结果必须停在“native选择/传稿路径已证实，官方save接入UNKNOWN”。不为抓网络token而扩展权限，不直接写库，不自造DocID，不主动调用callback。技术可行性/隐私过滤或once-forward保证缺一项，则不实现、不执行。

## 9. 证据记录与移交

未来每次操作记录准确target digest、工具/version、时间范围、用户授权scope、公开manifest digest、观察点、原回调保真自检、结束/timeout/取消与清理证据；REA使用真实工具schema，附Evidence ID、limitations、未知项，不能伪造runtime Evidence。未经执行当前所有runtime结果均UNKNOWN。

Native主依据：`REA_TELEPROMPTER_NATIVE.md`索引的responses/04-dispatch、06-didpick、07-result、08-fileinfo、09-cancel、10-resolveinfo、19-channel/20-keys（真实Evidence ID已在该文档）。REA runtime guide只提供“静态与运行证据分离、范围限定、敏感数据缺省排除”的原则，不能据它宣称iOS attach已准备好。

本任务只生成该计划文档，不运行手机、不抢Dart静态会话、不写probe代码；是否执行、具体方案与追加授权由lead/用户再确认。
