# Teleprompters add 工作体与 ObjectPool 绑定缺口

## 输入与结论边界

物化App `official-addon/build/rea-teleprompter-import/dart/App`，SHA256 `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`。来自此前同SHA官方IPA，未执行目标、未操作手机或稿库。本报告是对已取得离线切片和源码报告的收敛，不是新解析扫描或官方保存成功证据。

## 完整工作体已取得

前轮task20最终已检查完整 `0x1273b74..0x1273dc4` 的592B，不再是仅读128B。另两callee分别 `0x1274d68` 96B、`0x12747f4` 144B，共832B。证据目录：

`/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/dart-add-fullbody-icuj0z71/`

调度者已直接读 `body.asm`（154行）核对以下观察：

- 保存receiver与参数，转换原x2、调用 `0x1274d68`，结果经runtime-helper形状的调用留存。
- 经 `0x12644c4` 分配一个候选元数据对象，向多处字段写入原实参、转换属性、整数值与其他对象；该分配方法名仍来自候选图，不能据此认证完整类型布局。
- 对捕获对象写字段，PP偏移 `0x348c8` / `0x348d0` 装载闭包候选，通过 `0x12747f4` 等继续传递。
- `0x12747f4` 将捕获的参数与PP `0x34a38` 对应闭包候选交给 `0x11c825c`。目前只确认callback接收边，不命名为Isar事务、写库成功或提交回执。
- 多次调用和尾部形状符合async/Future风格推论，但runtime-helper语义未独立确认。没有可信参数类型、返回codec、稳定DocID、保存完成或列表读回证据。

预算说明：task20有两次工具调用返回间隔超过原180s elapsed预算，执行者已披露并停止追加。代码读取量832B在字节预算内，不能冒称时间也在预算内。

## PP静态绑定检查（task21）

该任务在只读范围内没有认可任何PP base候选，实际slot/object payload读取为0，不是凭偏移读某个文件地址。

直接MachO元数据只导出4个snapshot符号：IsolateData0x1599340、IsolateInstructions0xe800、VmData0x158fb00、VmInstructions0x4000。没有导出的PP/ObjectPool根。DATA为zerofill，不是可直接读取的文件pool。

已核固定工具源实现：

- `dart_pool_xrefs.c` 的PP base从配置 `anal.gp` 取得，没有base时跳过解引用；默认配置不是已观测runtime x27。
- `dart_pool_parse.c` 的PP JSON明示kind=`synthetic`、source=`object_pool_fill`，注释称runtime PP不序列化、静态重建ObjectPool image。
- 其生成r2命令可创建malloc映射并设置anal.gp/x27，这些是静态分析辅助操作，未执行，也不是发现了实际运行地址。
- `dart_pool_modern.c` 构建该image，当前选择首个可以成功重建的ObjectPool；尚未通过Code或快照根引用证明它就是目标函数使用的全局池。
- 序列化池项包含bits/type/behavior和ref/raw编码，并不是App文件中连续的LE64 slot数组。

因此 `0x348c8`、`0x34a38`、`0x348d0`、`0x92b0` 等只保留为PP相对偏移，未知raw object、类型、引用id和所属pool。不将它们直接加到snapshot文件基址上读取。

## 当前有界下一步

用户明确要求继续后，本轮只读检查两项：

1. task22：现有decoder的Code fill或roots是否读取后丢弃了可保留的ObjectPool ref；若没有则明确具体解析缺口，不再采用first-pool启发式。
2. task23：release Flutter实际暴露的native→Dart入口与对象/isolate调用前置条件。发现业务函数地址不自动得到合法native调用桥。

两项均不运行目标、不重新启动parser/Ghidra、不新增安装，不读写稿库/凭据。必须先有可信对象引用和调用上下文，才值得继续恢复官方save/read契约；不能用UI点击、DB直写或自建callback填空。当前无新IPA，完全无UI自动入库仍未完成。
