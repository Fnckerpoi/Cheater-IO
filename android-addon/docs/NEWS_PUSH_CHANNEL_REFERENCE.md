# 新闻模块推送通道技术参考

新闻业务已从 Android 扩展中移除；本页保留“手机准备文本 → 眼镜提词器收稿 → 显示控制”的机制，供其他业务实验选型。它利用宿主已建立的眼镜连接，不是云端通知服务。

源码基线：`android` 分支提交 `e61fe8ea37a97e4789f5b768f85f82f01fbf165d`，整理日期 2026-11-15。以下新闻行为均为该基线源码事实；不代表本轮完成真机验收。

## 1. 从哪里继续研究

在仓库根目录可直接查阅删除前实现，无需恢复模块：

```powershell
git show e61fe8ea37a97e4789f5b768f85f82f01fbf165d:android-addon/src/com/turboio/addon/NewsTele.java
git show e61fe8ea37a97e4789f5b768f85f82f01fbf165d:android-addon/src/com/turboio/addon/NewsUI.java
```

| 证据 | 实现事实 | 后续实验用途 |
| --- | --- | --- |
| 基线 `NewsUI.show/save` | 主动搜索或手填文本、本地保存、用户确认后发送 | 替换内容来源，例如工单或作业指导书 |
| 基线 `NewsTele.start/config/send/receive` | TELEPROMPTER 控制消息、UUID 会话、准备/收稿/开始状态 | 研究长文本分阶段投递 |
| [NativeTransfer](../src/com/turboio/addon/NativeTransfer.java) | 独占租约、文件暂存、SDK 文件事件、连接绑定 | 复用共享文件通道和互斥条件 |
| [TransferGate](../src/com/turboio/addon/TransferGate.java) | 文件成功与应用 ACK 同时成立才确认 | 分离传输成功和业务成功 |
| [OtaFrame](../src/com/turboio/addon/OtaFrame.java)、[DisplayCodec](../src/com/turboio/addon/DisplayCodec.java) | RNLink envelope 编码/解码 | 保留 wire 格式，按业务选择消息类型 |
| [HostSymbols](../src/com/turboio/addon/HostSymbols.java)、[HostBusiness](../src/com/turboio/addon/HostBusiness.java) | 宿主反射契约、业务名/数字映射 | 宿主升级时重新核对 ABI |
| [CardTransport](../src/com/turboio/addon/CardTransport.java) | LAUNCHER 卡片查询、定向修改和回读 | 短状态卡片场景优先参考 |

## 2. 内容与通道分层

新闻来源层调用 `ToolClient.call("web_search", {query: 主题 + 最新新闻 + 日期}, SecretStore.get(...))`，将 results 中的 title、snippet、url 拼成带来源的文本。这个步骤不调用独立新闻服务，也不以模型生成内容冒充检索结果。手填稿件可以跳过搜索。

稿件层使用 UTF-8，限制 Java String 长度不超过 12000、字节数 1–48000。`news-library` 最多存 100 个文件，以时间戳和 UUID 命名，通过 AtomicFile 保存；读取最多 48000 字节。`turboio_news` 保存主题与速度，默认速度 360。业务移除不主动删除手机上既有稿件与偏好。

投递层完全可以接收其他来源的文本，但新来源是否允许发送、何时发送、内容是否过期，需要新业务自己决定。新闻模块原本依赖用户确认，不包含云端订阅、定时推送或开机自动恢复。

## 3. 控制消息与文件通道

控制消息使用宿主 SDK 的 `TELEPROMPTER`（business 20）与 `NORMAL` 优先级。`HostSymbols` 提供 manager、packet、sendPacket 等名称；通过 `NavReflect` 调用。SDK 回调有错误时进入结果不确定状态，提交成功本身不等于眼镜显示。

RNLink envelope 字段：field 1 为 version=1，field 2 为消息 type，field 3 为 JSON UTF-8 字节；可选 field 4 为 binary。新闻控制调用 `OtaFrame.encode(type, json, null)`，仅复用 envelope 编码器，不走 OTA 升级业务。`DisplayCodec.packet` 只允许 3/5/7，不能直接代替该控制编码器。

正文不装进控制 JSON。以 `did` 作为文件名，通过 `NativeTransfer.sendOwned(did, did, bytes, 1, 0, 60000, callback)` 发送；宿主小文件类型为 `SmallCore`。

准备配置为：

```json
{
  "action": 1, "did": "12345678-1234-4234-8234-123456789abc", "total": 5,
  "scroll": 2, "speed": 360, "countdown": 3, "gear": 3,
  "depth": 1, "size": 18, "width": 492, "leading": 4,
  "pageOffset": 0, "highLightOffset": 0
}
```

上例对应 UTF-8 正文 `hello`（5 字节）；实验时重新生成 did，并按实际正文计算 total。开始消息复用准备配置，另加 `code:1` 与 `checksum`。checksum 为正文 UTF-8 的 32 位 FNV-1a：初值 2166136261，每字节先异或无符号字节值，再乘 16777619 并截断到 32 位，输出 8 位小写十六进制。该字段由既有提词器协议定义。

## 4. 分阶段投递与回执

1. 生成新 UUID 作为 did/消息租约 owner，记录唯一已连接眼镜 peer。获取 `acquireMessages` 成功后进入 prepare。已有 OTA、显示、文件任务、外部传输或隔离期会阻止获取租约。
2. 发送 type=2、action=1 的准备配置，等待最多 15 秒。
3. 收到匹配 peer、did 的 type=2、action=2、code=1 后开始传文件。若回包含 total，必须与字节长度一致；首次就收到 code=7 会失败。
4. 文件传输阶段等待两种证据：SDK `fileShareSuccess`，以及提词器 type=2、action=2、code=7。后者在核对 peer/did 后转为 `NativeTransfer.ack(peer,1,0,0)`。
5. `TransferGate` 收齐提交 taskId、正确文件成功事件和应用 ACK 后进入 CONFIRMED。两种回执允许乱序；仅有其中一种不启动显示。
6. 进入 ready 后发送 type=3 的开始配置，等待 type=3、action=2、code=1，才标记 playing。
7. 暂停/继续/退出均等待业务响应；显式退出确认后才释放消息租约。

`request=1, session=0` 是旧适配层提供给共享 gate 的内部关联值，不是眼镜回包中的独立会话隔离机制。真正业务关联还依赖 peer、did、单一租约和 native taskId；新业务不能只复制这两个常量就声称具备多会话隔离。

| type | 基线处理 | 关键字段/动作 |
| --- | --- | --- |
| 2 | 准备、收稿确认 | action=1 请求；action=2，code=1 允许传稿、code=7 收稿完成 |
| 3 | 开始 | 完整配置加 checksum/code；仅 ready 允许本地主动开始 |
| 4 | 暂停 | action=1、did、offset=0、code=1、isCompleted=false |
| 5 | 继续 | action=1、did |
| 6 | 退出 | action=1、did；收到眼镜退出请求须回 action=2、code=1，再清理 |
| 7 | 调速 | action=1、did、scroll=2、speed；旧 UI 未单独暴露调速按钮 |
| 8 | 眼镜请求响应 | 对匹配 did 的 action=1 回 action=2、code=1；源码未命名其业务含义 |
| 9 | 触发 stop | 基线在同 peer 的 envelope type=9 时即请求退出，早于 did 检查；含义未确定 |

除了 type=9 的上述例外，接收逻辑核对 did。眼镜主动发送 type=3/4/5、action=1 时，手机返回对应 action=2、code=1 并更新播放状态。

## 5. 超时、后台与资源生命周期

- 新闻状态机每 250 毫秒检查连接和等待期限；准备 15 秒、文件阶段 60 秒、控制命令 10 秒。共享文件 gate 另有自身 60 秒期限。
- 超时、连接变化、SDK 错误、拒绝码或格式异常会进入 uncertain；停止计时、释放新闻后台租约，但保留 did 与消息租约，不自动重发。
- 用户确认镜片已退出后，只有 uncertain 且 `fileBusy()==false` 才允许 reset；reset 释放消息租约。共享文件异常所设的 60 秒 quarantine 不会被 reset 绕过。
- 文件 gate 按 peer/native taskId/filename/sender 过滤文件事件，按 peer/request/session 过滤应用 ACK；文件传输失败和“确认未齐”是不同状态。
- 暂存位于应用私有 `turboio-spool`，写文件在单线程 IO executor，状态推进回主线程。`mayDeleteSpool()` 条件是已提交且文件成功，不要求业务 ACK 成功；SDK 文件成功后即使业务回执缺失，也可能删除暂存。不要把 UI 的“保留文件”提示当作所有异常都会留文件的保证。
- `BackgroundWork.acquire("新闻",16)` 请求 connected-device 类型前台服务。旧新闻代码没有检查其返回值；获得消息租约不等于获得后台保活。Activity 停止且没有新闻后台租约时会调用 stop；服务“停止全部任务”通过注册的新闻停止回调收尾。
- 进程死亡不会自动恢复新闻 session。新业务需要明确用户如何核实眼镜状态，不能从本地对象消失推断镜片已退出。

## 6. 其他业务场景如何使用这份参考

| 场景 | 可借鉴部分 | 实验选择与边界 |
| --- | --- | --- |
| 作业指导书、培训讲稿、巡检步骤说明 | 准备→长文本文件→收稿→开始→暂停/退出 | 可研究提词器通道；先验证长文本、滚动、眼镜按键及退出 |
| 工单摘要、会议材料 | 内容来源与投递层分离、本地稿件 | 将搜索结果替换为受控业务文本；确认敏感信息展示范围与手动投递时机 |
| 设备告警、任务进度 | 会话关联、占用协调、确认后释放 | 优先评估已有卡片或字幕显示；提词器会占用完整显示会话，不适合直接作为高频弹窗 |
| 小应用配置或批量资源下发 | 文件成功＋应用 ACK 双证据 | 使用对应业务协议，不能给任意二进制套上提词器正文格式 |

短文本卡片可从现存 `CardTransport` 入手：它使用 LAUNCHER（business 15），先查询配置，再定向修改并回读。`NativeTransfer.sendMessage` 固定 LAUNCHER，不会根据 JSON 自动切换到 TELEPROMPTER。后续若重建提词器研究客户端，必须显式实现 business20 发送与接收适配，当前生产代码已移除新闻专属接收分支。

建议实验顺序：

1. 固定宿主版本与固件，核实 `HostSymbols` 所选 ABI；准备一副已连接且空闲的眼镜。
2. 先用纯本地无敏感信息短文本，验证准备、文件成功、收稿、开始、暂停、继续、退出各阶段；逐一记录 peer、did、taskId、type/action/code 和镜片观察。
3. 在离线夹具中覆盖回执乱序、重复回执、旧 did、不同 peer、缺文件成功、缺应用 ACK、拒绝码和连接变化。
4. 再在设备上验证退出后通道可被其他业务使用，以及后台拒绝、切后台、熄屏和进程终止后的实际状态。
5. 最后替换为业务来源，测内容长度、速度和更新频率；设备显示验收与内容来源验收分别记录。

## 7. 本次剔除范围与限制

剔除 Android 新闻页、新闻提词器客户端、初始化与后台停止接线、专属 business20 分发、专属配图及打包项。字幕输入控件迁入公共 EditorialUI；共享 NativeTransfer/TransferGate、宿主业务符号、搜索工具与其他业务保持使用。iOS 历史实现和应用广场的独立新闻摘要模板不属于这个 Android 新闻页模块。

速度 60–1200 为旧客户端允许范围，旧 UI 已提示超过 360 需实机确认。源码没有证明这些速度在所有固件均有效，也没有证明后台长时可靠性或 type=8/9 的完整协议语义。本页提供重建研究所需机制与可定位源码，不构成新业务已获设备兼容性验证的结论。
