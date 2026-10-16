# 官方公共稿保存探针：效用门槛未通过，未生成测试 IPA

## 结论与边界

本任务允许基于指定 HTTP/导航 IPA 制作**独立未签名诊断包**，但前提是存在真实、有用、能严格匹配公共 fixture 的观察入口。本次有界核验没有找到这样的公开稿编辑/保存入口，因此停止 host/UI/dylib/IPA 制作；不把已知眼镜 transport 包装成官方保存观察器。

**没有新 IPA、没有独立 dylib、没有安装或设备动态验收，也不能证明官方入库。** `OfficialManuscriptProbe` 仍是离线 collector；新增测试不是已经接线的 host，不证明后台停止、IMP 恢复、reply 透传或多 swizzler 互操作。未使用的 host header 草稿已删除。

没有修改原 IPA、旧 addon、导航/HTTP/原扩展源代码；没有下载容器、读取 filePath 指向的稿件、访问云端、写官方 DB、自动创建副本或发眼镜消息。未读取 `local-http-preparation`。

## 精确输入与完整性

输入：`/Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa`

| 项目 | 实测 |
| --- | --- |
| 大小 | 427,510,198 bytes |
| IPA SHA256 | `de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24` |
| bundle/version/build | `com.duriea.turboio.research` / `1.0.5` / `201` |
| Runner UUID | `748FD301-DA60-3095-A244-9BD0558FACA5` |
| Runner SHA256 | `ac5ffe33484f4c51dd17a9e361f7103072f459096037e47d3f2867a940db11b0` |
| 原 TurboIOPrivateAddon.dylib SHA256 | `7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503` |
| ZIP entries | 9994 |

这里只报告原包；因没有生成派生包，不存在“新包成员差异”“新增 Runner 依赖”或“签名清理验证”。完整原包 hash 与 lead 给定值一致。另运行 Python `zipfile.ZipFile(...).testzip()`，结果 `None`（所有原 ZIP 成员 CRC 验证通过）。

## 两个既有真实观察点为什么不足

- `rayneo_venus_sdk_plugin.RayneoNetPluginBridge.handleMethodCall:result:`：Dart → native，没有可由该方法取得的真实 channel，应标 `none`。它承载 RayneoNet transport，不是已经确认的稿件 CRUD。
- `FlutterEngine.sendOnChannel:message:binaryReply:`：native → Dart，可以取得真实 channel。`decodeEnvelope:` 仅用于实际 MethodCodec envelope，不能当作所有 binary message 的通用解码器；成功解码 transport 也不等于看到 Dart 内部保存。

`NewsTeleprompter.m:99–109,129–133,220–222` 与 `NewsTeleprompterTests.m:28,34–40` 表明现有业务 20 形状为：

- prepare JSON：`action` / `did` / `scroll` / `total` / `checksum` 等元数据，封装进二进制 payload；
- sendFile：`deviceId` / `taskId` / `filePath`，正文通过独立文件传输；
- decoded incoming envelope：`type` / `json` 等 transport 结构。

这些形状没有同次观察中的**完整公共标题 + 完整正文**；`did`、`taskId` 只是现有 transport 标识，没有证据是官方 document ID，必须保持 `stableID=nil`。不能为了 seed 去读取 `filePath` 或弱化正文匹配。

用户授权的“仅编辑现有公共稿并保存一次”也不包含开始提词/眼镜传稿，因此既有 business 20 hook 可能完全不触发。Dart 内文件与 metadata 分离持久化只是当前调查线索，不是已经证明的实现；不能将它当作一个已发现的 save selector。

## 有界候选核验

仅检查原包中以下已知候选 framework 的 binary 字符串，不读取任意手机数据，也不增加全插件扫描/监听：

| 候选 | 确实存在 | 未能证明 |
| --- | --- | --- |
| `file_picker.framework/file_picker` | `FilePickerPlugin`、`handleMethodCall:result:`、`saveFileWithName:fileType:initialDirectory:bytes:` | 此通用文件 picker/导出功能是否参与现有稿编辑保存；完整标题和完整正文是否同时出现 |
| `sqflite_darwin.framework/sqflite_darwin` | `SqflitePlugin`、`handleMethodCall:result:`、`insert`/`execute`/`batch` | 官方提词器是否使用此插件；正文和 metadata 是否同次提交 |
| `shared_preferences_foundation.framework/shared_preferences_foundation` | `LegacySharedPreferencesPlugin`、`SharedPreferencesPlugin` | 稿件保存是否经过此插件；是否存在完整 fixture |

官方稿件调查已有 AOT 线索包括 `Teleprompters` 的 `_MetaDataPersistence` / `_FileOperation` / `_ItemOperation`、`teleprompter_meta_database.dart`、`CachedTeleprompterMetaSchema`、`serializeCachedTeleprompterMeta`、`TeleprompterScriptEditImportActionTypeSaveDocument`。这些是静态字符串线索，不是可调用 API。没有 native save 路径证据时，不能把任意通用 plugin hook 当作可靠保存观察器。

## 离线评估与验证命令

```sh
bash official-addon/test-official-manuscript-probe.sh
otool -l /tmp/tio-probe-baseline-runner | grep -A3 LC_UUID
shasum -a 256 /Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa
```

脚本实际运行：macOS Foundation collector synthetic tests（`-Wall -Wextra -Werror`），以及 iPhoneOS arm64 collector/test `-fsyntax-only`。这不是 arm64 linked host/dylib 测试。

实测输出：

```text
PASS reachability: known business20/file/envelope shapes do not seed public fixture; synthetic positive control does
PASS official probe: disabled, consent, exact fixture, redaction, correlation, bounds, expiry, passthrough
PASS arm64 collector/test syntax (not a linked host or IPA)
```

新增 reachability case 用真实源码结构 + 合成非设备值构造 business20 二进制 packet、sendFile 参数、decoded transport metadata envelope，验证三者均不能 seed；分别传 title metadata 与 body bytes 也不 seed；**仅 synthetic 正向对照**直接提供完整 title/body 时匹配。没有将 synthetic positiveControl 称为真实官方 save 方法。

原 collector 测试继续验证 default-off、一次 consent、120秒 monotonic 期限、16条上限、完整 fixture、脱敏、观察异常不阻断原操作及原操作一次透传。host 级后台/恢复 IMP/原 reply 透传尚未实现、未验收。

## 最小下一证据，不扩大成全量捕获

以下前置条件**只限定复用当前 collector 与现有授权的诊断包路线**：需先证明一个真实候选入口、具体用户动作和有界参数形状，使公共标题及完整正文可在同次观察中到达此 collector。它不是所有可行接入路线必须满足的 SDK 规则；另行授权的跨消息安全关联、官方接口或目标 Dart save/read 路线应分别设计与验收，不能据当前规则否定它们。

1. 优先通过厂商支持或官方扩展能力获取可验证的官方稿件保存/读取接入点，包含真实 API、参数和持久化语义；不转向通用文件 picker/导出。
2. 如果没有官方接入，另立受控 Dart 持久操作调查任务：先确定保存与读取边界、metadata/content 关系，再对仅公共 fixture 的观测方案取得独立授权。本任务不扩权、不进行这项调查。
3. 若 metadata 与内容确实分开，应先设计新的只对公共 fixture 的安全关联协议并重新审查，不能偷偷弱化“完整标题+正文”规则。无需重复用户已经完成的手动保存测试。

当前**没有可签名安装的产物**，请不要安装旧包并期待出现此诊断面板。获得有效接线证据后才给出一次“用已有签名安装流程处理新的 unsigned IPA”的具体产物步骤；无需重复既有签名教程。

## 后续公共稿验收约束（尚未执行）

标题：`TurboIO 日报入库测试`

```text
这是一份公开合成测试稿，不是真实新闻。
用于验证官方提词器稿件的保存、重新打开和持久化。
校验码：7392。
```

未来有效包应默认关闭、显示风险及全文、用户一次明确授权 120 秒/最多 16 条，停止/过期/切后台解除短时观察；只编辑当前已有公共稿，不新建副本，不要求删除任何私人稿件。若为触发 dirty 状态修改单个空格，应在保存前回退到完整 fixture（否则严格匹配将拒绝该变体）。单独查看/导出脱敏摘要，未知 stableID 保持 nil。

**零记录只表示未观察到允许的完整 fixture 消息，不表示没保存、保存失败或未入库。** 重新打开仍显示公共稿，以及重启后仍在官方列表，都需用户自行验证；本轮没有安装验证、没有官方入库证明。
