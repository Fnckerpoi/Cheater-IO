# Dart 指令表查询窗口：受控扩展与验收记录

## 既有结果

原固定r2flutter工具在用户单次软RSS授权下只读 `-j -l 20 -i`，3.625s/exit0，96次采样峰值162103296bytes，App SHA前后 f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a 不变。

前20项均index0–19、kind=stub、func.<address>合成回退名。独立只读MachO核对：20项地址均在__text VA0x4000..0x158fae0且20/20匹配LC_FUNCTION_STARTS；不提升为Dartowner或可调用业务API。tablelength111178、first_entry_with_code90623，差20555与headerit_len一致，但该关系并非loader的强制校验，所以仅作一致性佐证。

源码核验 `-f -l20` 并不等于skipstub：先extract含stub的20entry再过滤；随后无条件类方法恢复并可能二次扫描。因此不以这个命令冒充低成本业务窗口。

## 新授权

用户通过 `dart_code_window_validation_consent` 明确同意：在隔离副本增加输出起点窗口，不改快照布局和命名算法；无目标fixture验证后最多3次目标只读诊断，每次wall45s/CPU35s/软RSS512MiB/输出4MiB，无猜名/强制版本/AAA/目标执行或稿库操作。

新共同绝对预算start1791966172（UTC2026-10-14T08:22:52Z），deadline1791966772（08:32:52Z），不每次重置。

唯一新根 `/Users/duriea/Tools/REA/r2flutter-code-window-20261014-162252`，原固定root只读保留。task13实现、task14独立审阅，lead在GO后独占目标运行。没有全局安装/配置改动、新下载或原App/IPA覆盖。

## 需要证明

- 默认start0不改变原始输出顺序/索引；窗口起点直接传入真实IT解码循环，不是先截前20再slice。
- 绝对IT index与code_index关系、pc_offset/address/stackmap字段保持，不重排或捏造名字。
- 新窗口limit1..20，非法/负数/溢出参数在加载前拒绝；越表边界正确拒绝或有明确空结果语义。
- 测试必须至少一次运行真实解码器读自造公开IT结构；仅纯helper测试不够。
- 比较全部原构建源码摘要，改动仅批准的窗口/CLI接口及相应fixture，不改layout/CWS/ObjectPool/命名/启发式算法或插件/脚本策略。
- 前置解析成本仍不由20条输出限制，外部softRSS/CPU/wall/output监督保留。任何metadata/data-image/heuristic名字均为候选，可信owner和官方create/save/read需要独立证据。

## 当前状态

补丁及独立审阅已完成：固定副本仅5个文件差异，新增 `-w <absoluteITindex>` 配合 `-i`；真实fixed/varint/linear IT decoder公开fixture由执行者和独立审阅者运行通过，含delta前项累加、越界/溢出、limit0/21拒绝和索引保持。新binary为隔离新根 `source/bin/r2flutter`，SHA256 `0e292757358a5332d9120d242ce7e3ae1cee1b6f65446301cf032263ffa833b2`。原68固定源码未改变。

审阅者在epoch1791966694给条件GO，受共同deadline1791966772约束。调度者随后执行有同一绝对deadline的监督命令，启动门禁已到期，实际输出 `SKIP deadline gate 0`；baseline0和90623两个目标窗口均未启动。空的 `results/lead-runs/` 不是目标结果。不把fixture通过或工具编译当成App业务映射通过，也未偷偷续跑或扩大预算。

用户随后两次直接要求继续任务，调度者在该续行下明确本轮仅续跑既定baseline0与90623两个窗口（独立3分钟总预算，每次45s/CPU35s/软RSS512MiB/output4MiB），不安装或重编。实际两次分别3.710s/3.700s、exit0，RSS采样峰值162922496/162398208bytes，目标SHA前后均f5691e...c23a。新根 `results/lead-resumed-20261014-084156/authorization.json` 记录该续行边界；两个子目录各有execution/stdout/stderr。

baseline0与原未修改工具的完整JSON严格相同。90623窗口返回20个kind=code、code_index0..19的条目，含stub.AllocateFlutterErrorStub、method._ErrorDiagnostic...、method.DiagnosticsProperty...、method._StringBase...、method.ArgumentError...及合成func地址名；显示已越过前20个stub，但仍主要为基础库，未命中官方提词器业务。候选名称来源需单独按decoder/resolver校验，不能当trusted owner或native可调用接口。

没有新IPA，官方无UI保存契约仍未恢复。后续只读复核本结果可继续；新的目标执行或工具过滤接口应有明确边界，不以source名看似业务就补写虚假调用。
