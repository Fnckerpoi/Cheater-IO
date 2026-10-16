# 官方文件导入原生桥：REA/Ghidra 静态契约

## 结论

已真实反编译 `file_picker` 原生 dispatch、文件选择 callback 和结果序列化；**它只返回文件信息，不是官方稿件保存接口**。必须有官方 Dart import消费者→解析→保存metadata/文件→列表回读证据才能闭环。TurboIO自行调用picker并提供callback不会自动得到官方Dart导入Future的continuation。

调查仅静态，不执行目标/手机、不hook、不读账号/容器、不改DB、不实现、不签包、不commit。用户此前手工TXT导入+眼镜阅读成功为lead提供的独立背景，不是本次工具观察。

## 身份与方法

- stage IPA：`official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`。
- IPA SHA256实际匹配：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`。
- 唯一成员：`Payload/Runner.app/Frameworks/file_picker.framework/file_picker`。
- 成员SHA256：`9e4baee5172d451872912107f6c5f633ba51a620b2323b55f8c64d6b3b48480a`，161616 bytes，arm64，UUID `53BB7A4E-6F63-3184-9996-86307D8DB52F`，cryptid=0。
- REA6.4.0 / Ghidra12.1.4，等待task16真实fixture验收后才启动独立stdio session。实际tools/list核对schema，全部native请求顺序执行，close_binary成功、bridge exit0。没有重新import巨大全App。
- evidence与完整warnings/schema/请求/响应在 `official-addon/build/rea-teleprompter-import/native/`；地址为该成员静态Mach-O地址，不是ASLR运行地址。

## Evidence索引

| 相对 native/ 的文件路径 / 方法 | 地址 | Evidence ID |
|---|---|---|
| artifact.json | — | ev_0a6f1c5a9929d1af3c54850a2fcf9ca1832ee60c3d1bbb004714a74365a22601 |
| macho.json | — | ev_339e87b5ba7baea4f3c3d4ba781d3647c72710dbbb39f87527b387e87b22583b |
| `responses/02-dispatch.json`: typed ObjC metadata | — | ev_cb52611117dbf948d76aaa7c007d1edd667e4a526e4b90bd3608f3f1adcc7366 |
| `responses/03-register.json`: registerWithRegistrar: | 0x43ec | ev_6bf55fb24e2f6b19a7e989bb73eb2ac6beb93922f5cd0d6de46c696e56bf59ac |
| `responses/04-dispatch.json`: handleMethodCall:result: | 0x474c | ev_7b79cf6271ae3a5d67b4f27fb3b058101c7425598a8dfb9022d2beb21a54715e |
| `responses/05-pick.json`: resolvePickDocumentWithMultiPick:pickDirectory: | 0x5404 | ev_809477d034ff5dc9f870830766f0724a723ec57d6f888dcd69851f003b35585c |
| `responses/06-didpick.json`: documentPicker:didPickDocumentsAtURLs: | 0x61f4 | ev_c79daadefcd752a958edc89369eb34adc6d7f9463d70ea0b87c4d266f41b36c3 |
| `responses/07-result.json`: handleResult: | 0x60d4 | ev_81407a132d5e3915d2fa977ae78fb2955e0ec93d5d2ec02f95e54d90c6db9653 |
| `responses/08-fileinfo.json`: FileInfo toData | 0x40fc | ev_5ec8e3d81f217fa8a1f12c3e992e6c1020ad9c39a57f5b0da2ccb8e0b22d2d04 |
| `responses/09-cancel.json`: documentPickerWasCancelled: | 0x7af8 | ev_012d427f33483cede2a72027df76554e72beaf6745af6bfc1c86625f193f435c |
| `responses/10-resolveinfo.json`: resolveFileInfo:withData: | 0x827c | ev_0cab33282625acde5aac856c2286593e6b4ab0cbfda29a6fb8e99daa51138c3b |
| `responses/15-fileinit.json`: initWithPath:andUrl:andName:andSize:andData: | 0x4000 | ev_637e5b403602c2404f75c7917e664431f5c37428dc723474ffc3939afe946edb |
| `responses/16-type.json`: resolveType:withAllowedExtensions: | 0x7fc0 | ev_60d3baa93ecb68de0831d25d286ac14ed393728302637aa95839570b0967de69 |
| `responses/18-dispatch-analysis.json`: function dossier | 0x474c–0x4fcf | ev_5e58de494fbcbb1003edcaf9f8113c8df60a4d5ae48c7ed3bb3082202a9b64cd |
| `responses/19-channel.json`: exact channel strings | 0xcbba/0xcbe1 | ev_7e6ab3db1d59da149bb91dc63334f27d36075f9a1782771c3262ffd1aa3e0291 |
| `responses/20-keys.json`: exact keys/error strings | response中列明 | ev_563a3e6d8edda1a6ee2e01d41c2f71d0086a44aa09ea629a7670925a26bcec32 |

Artifact graph manifest：`agm_980f8d812c8b446aee517aec4201b3d70795cf9307e39cb2486023538744ccc0`。`dispatch-graph.json` 为function dossier实际边的投影，非官方源码图；`evidence-ledger.json`保留所有IDs、参数和limitations；其中 `file` 值是相对 `native/responses/` 的文件名（例如 `03-register.json` 对应 `native/responses/03-register.json`），非 native 根目录。

## 已观察调用链

```text
registerWithRegistrar: 0x43ec
  method channel "miguelruivo.flutter.plugins.filepicker"
  event channel "miguelruivo.flutter.plugins.filepickerevent"
  addMethodCallDelegate(plugin, channel), setStreamHandler(plugin)
handleMethodCall:result: 0x474c
  any/custom -> resolveType:withAllowedExtensions: 0x7fc0
  + allowedUniformTypeIdentifiers去重合并
  -> resolvePickDocumentWithMultiPick:pickDirectory: 0x5404
  -> UIDocumentPicker(mode=0, delegate=同一plugin)
documentPicker:didPickDocumentsAtURLs: 0x61f4
  -> 临时目录/basename -> handleResult: 0x60d4
  -> resolveFileInfo:withData: 0x827c
  -> FileInfo init... 0x4000 -> toData 0x40fc
  -> 调已存FlutterResult block(array)，清除pending result
```

真实callee证据：dispatch→0x5404：`ev_2c61e20efa0728683ec77c353538d868f5ee13b95be27700f61536d0f833656b`；didPick→0x60d4：`ev_72a95bfcf3b8c545079947b7ff9620274369b1782d39f84e8c0fc4938ba69f43`。handleResult callers为0x61f4/0x66b4/0x7784：`ev_404299065d53457622d458f39376da022a38747cb9079485c634498b75678812`。didPick entry xrefs仅0xa578/0x18dfa：`ev_947a82e8c5c6dbdff7fbeefb3b2af1166df0705371c91509d7614052e023f60c`，这只是framework内metadata/symbol引用，不证明App Dart caller。

## 请求/结果真实契约

Dispatch方法分支：clear、dir、any、custom、video、image、media、audio、save。没有恢复到官方稿件CRUD分支。custom分派用contains判断，但resolveType helper要求等于custom；不能据此自造其他method。

文件选择arguments：
- `allowMultipleSelection`：boolValue→allowsMultipleSelection。
- `compressionQuality`：intValue，>0设allowCompression，媒体共用设置。
- `withData`：boolValue→loadDataToMemory，控制是否读NSData。
- `allowedExtensions`：custom扩展名转UTType；逐项dummy.<extension>取pathExtension→UTTypeCreatePreferredIdentifierForTag，排除dyn.结果。
- `allowedUniformTypeIdentifiers`：只接收NSArray，过滤非空NSString，与扩展名转换结果NSMutableOrderedSet去重合并。

这只是通用过滤能力；官方Dart实际调用方法和扩展名仍UNKNOWN。

`save`另读取fileName/fileType/initialDirectory/bytes并调用0x5020 `saveFileWithName:fileType:initialDirectory:bytes:`。这是**文件导出**，不是官方稿件保存。

文件选择结果是array，每个dictionary由FileInfo.toData的setValue:forKey:构造：

| key | 实际来源/类型 |
|---|---|
| path | URL.path，NSString，import模式为临时路径 |
| identifier | NSURL.absoluteString |
| name | path.lastPathComponent，NSString，不是已验证稿件title |
| size | 文件属性fileSize→numberWithLongLong，NSNumber |
| bytes | withData真时dataWithContentsOfFile→NSData；否则nil不产生该键 |

没有title/body字段、文本解码、DOC/PDF解析或稿件索引保存。NSData到Flutter codec/Dart Uint8List的实际跨framework转换此次未确认，不能把原生类型当作已验证的Dart消费契约。

## 错误/取消/重复语义

- instance offset0x10保存单个pending FlutterResult。已有pending时第二次调用获FlutterError(`multiple_request`,`Cancelled by a second request`,nil)；该分支没有清除第一次callback，文案不证明第一次已取消。
- 无有效type获FlutterError(`Unsupported file extension`,扩展名提示,nil)，清除pending。
- 未知method获FlutterMethodNotImplemented，清除pending。
- documentPickerWasCancelled:调用pending(nil)、清除、dismiss；没有看见pending=nil防护，不应离开正常生命周期直接调用。
- didPick mode0把选中文件移至NSTemporaryDirectory()/basename；同名临时文件先remove后move，失败NSLog，只成功URL入array。**临时文件覆盖不等于稿件去重/覆盖**。
- dir mode1和save/export返回首URL.path字符串，与文件选择array形状不同。
- 官方标题映射、正文解析、文件寿命、保存事务、重复稿件、取消UI语义以及列表刷新/重启持久化：UNKNOWN。

## 原生接入候选与必须补的证据

观察：原生selector可恢复，callback是本次调用带来的block，不是“新增稿件”广播。推论：TurboIO自发调handleMethodCall:result:或picker helper仅能启动picker并收到自己callback，**不会自动获得官方Dart import调用者continuation**。直接伪调didPick还依赖pending/controller/mode状态，不是安全的无UI入库API。

有条件候选：若Dart worker证实官方import action→picker Future→结果解码→正文/标题解析→meta/file save→列表refresh/read，才能评估在该**已有官方调用者**的continuation中交付公开AIHOT TXT；依然须匹配codec、生命周期和错误路径。若另发现官方显式导入接口，需另证真实参数与成功回读。本framework未找到它。无caller证据时保留已实测手工TXT路径；自动入库不能宣布可用。

仅计划、未执行的最小动态验证：另获授权后用户用公开AIHOT唯一文件名/标题，从官方导入UI进入；限定观察该一次picker请求参数、结果shape及其Dart消费者的save/list回读。先取消一次确认无新列表行，成功一次确认列表和重启，再同名重复核验去重。禁止账号/token/私人稿、全局容器或DB写入；网络token捕捉不是必要步骤。

## 局限和复现

前检实际CLIhelp+execution-free inspect-artifact/inspect-macho；深度采用独立MCP实际catalog：open_binary(provider_id=ghidra)→typed dispatch metadata→精确address pseudocode/callees/callers/xrefs→analyze_function→search_strings→close_binary。原stageIPA不变。

伪代码不是官方源码：Ghidra ObjC retain/release injection、didPick type propagation not settling、某些runtime thunk terminal flow被Listing排除，原因未知，完整warnings留在Evidence。dispatch多出的param_5是反编译签名不确定性，不能编造真实第五参数；typed metadata明确selector handleMethodCall:result:。跨framework/Flutter AOT动态caller coverage不完整，本调查未分析App消费者。

任务验收：native选择参数/回调/字段已答；取消、重入、临时同名处理已答；官方Dart保存消费者、标题映射、官方重复/持久列表回读未答，交由Dart worker继续。本报告不承诺自动入库。
