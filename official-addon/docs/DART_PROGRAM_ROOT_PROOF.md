# 官方序列化根定义与严格边界诊断

## 固定官方文本身份（不是目标根值）

Dart SDK官方3.11.5 annotated tag `370c59090ec78a6ab56f610e4bb4fc907e16db5d` 指向commit `c70f78e7d682c158c15ca0c26c729b3ccb932284`。旧路径clustered_snapshot.cc在该commit为404；实际路径由固定commit runtime/vm contents元数据定位为app_snapshot.cc。未用main或第三方源码填空。

- https://raw.githubusercontent.com/dart-lang/sdk/c70f78e7d682c158c15ca0c26c729b3ccb932284/runtime/vm/app_snapshot.cc ，366520 bytes，SHA256 `d0bb85440e6c9030728087769a409af9a217f958d839337d0828db529e1a645a`。
- 同commit `runtime/vm/object_store.h`，41460 bytes，SHA256 `ea8ca3b539e708709de9afa9cb79677c789b6f93336bd8906b09eaa80a2f08af`。

本地只读文本在 `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/dart3115-source25-r9ismja2/`。调度者独立核验两文件SHA和宏列表：261字段，global_object_pool ordinal161，slow_tts_stub ordinal241。这里只是该固定源码的字段顺序证明，官方tag与目标snapshot token的源码绝对同源尚未单独证明。

## 官方字段定义的关键纠正

`app_snapshot.cc:3062–3089` 在DART_PRECOMPILED_RUNTIME/FullAOT分支不读取Code.object_pool ref，而将其置null，注释明确单一global pool。后续依次owner、exception_handlers、pc_descriptors、catch_entry、inlined_id_to_function、code_source_map，共6refs。因此不能将解析器之前丢弃的Code其他refs猜作pool。

`ProgramDeserializationRoots::ReadRoots:7215–7221` 从ObjectStore.from到to_snapshot逐ReadRef，无前置root计数；其后才是staticfield n/refs、shared n/refs和dispatchtable。ObjectStore宏44–309无内部#if，FullAOT以slow_tts_stub结束，因此该源版本共242个root refs，global pool是第162个；不得做root_start+161*8，因为ReadRefId实际序列化编码须另证。

## 当前解析器的真实缺口

- modern_parse_cluster_meta设置keep_partial_fill=true，首个fill失败仍可能返回metadata；返回true不是全cluster解析成功。
- modern_resync_fill_record可以在原位置后逐字节尝试到0x20000字节寻找能skip的候选，当前成功状态未附“猜测重同步”标记。
- alloc对未知predefinedCID存在generic/SIMPLEfallback。即使后续fill看似全成功，也不自动得到可信root起点。
- DEBUG sectionmarkers在官方全cluster ReadFill后会影响游标；未确认协议前不声明root_start_available。
- 工具synthetic PP选首个可重建ObjectPool，没有通过Programroot验证，因此不能把生成的anal.gp/x27当运行时对象地址。

## 本轮严格诊断授权

用户问题 `strict_snapshot_diagnostic_stage` 明确批准：20分钟、隔离副本只加strict-status、不改布局/命名算法。开始epoch1792025016（UTC2026-10-15T00:43:36Z），截止1792026216（01:03:36Z）。新根 `/Users/duriea/Tools/REA/r2flutter-strict-status-20261015-084336`，task28实施、task29独审，旧68源与原工具保留。

必须测试：partial不假成功、unknown alloc/fill拒、resync需要即停不猜位置、CWS8合法RODATA零fill、counts/ref范围/游标边界、旧non-strict模式不变。即使allfill精确完成，DEBUG framing未认证也不输出root_cursor或root_ref。

独审GO后，调度者已在该绝对截止内执行唯一一次45秒只读诊断，CPU35秒、softRSS512MiB、输出4MiB，隔离env且目标SHA前后不变。实际2.793秒、exit1（诊断设计的未认证退出，不是崩溃）、采样峰值118747136bytes、73samples。工具SHA `7bb78a93a9725d576de9f37f7e64831e8a68d3372465647d71f859bdd171d179`。

结果：strict_complete=false/all_clusters_exact=false/root_start_available=false/terminal_root_cursor=null；total_clusters3596、allocated_clusters7、filled_clusters0、allocated_objects235924、expected_objects389648、last_exact_fill_end=null；first_failure为allocation/unsupported_allocation/CID7/cluster_index7/stream_offset23159042。证据在新隔离根 `results/lead-strict/{execution.json,stdout.json,stderr.log}`。

该结果只证明本严格认证路径在CID7分配段停止；CID7在当前布局为Function，不是官方Function格式不兼容的证明。独立source复核确定：modern_is_simple_alloc_cid显式名单未含cids->function，normal路径Function落入默认SIMPLE；strict因为没有显式认证，先于读取该cluster的allocation而拒绝。filled0因算法先完成全部alloc再进行fill，不能解释为7个cluster均无fill或target无根。

调度者随后仅读已下载官方同一文件核实直接helper：FunctionDeserializationCluster::ReadAlloc:1886–1888调用ReadAllocFixedSize(d,Function::InstanceSize())；ReadAllocFixedSize:942–950仅ReadUnsigned count后循环Allocate固定instance_size/AssignRef，不从stream读取额外逐项分配字段。可据此为该官方源码版本设计Function固定大小分配的认证测试；仍须对照现有stream ReadUnsigned语义与目标版本约束，不据此直接放行其他CID或根读取。

本轮唯一target诊断已用完，没有修改后追加重试，没有把failurecursor当root起点。该正面源证据应与严格门禁缺口区分，避免把安全拒绝误报成解析器无能力。

原工具/上游源码不变；公开真实walker测试覆盖strict未知alloc/fill/partial/resync拒、合法CWS8RODATA零fill、counts/ref/bounds和rootnull，旧filter/window回归与CLI冲突测试通过。仍未读取任何root/PP值，未执行App，无新IPA。

## 后续单项固定大小认证与实际诊断

用户通过 `certified_allocation_followup` 明确批准15分钟新阶段（start1792026028，deadline1792026928；UTC01:00:28–01:15:28）。只在新隔离副本 `/Users/duriea/Tools/REA/r2flutter-alloc-certification-20261015-090028` 按官方helper证据增加Function一项资格，补丁仅1文件3行，条件为exacthash映射3.11.5且CID等于当前profile.function；不硬编码7、不扩其他版本或generic类型。公开真实Function fixture RED→GREEN（CWS4/8，旧2.17/unknownhash仍拒）、strict/filter/window回归与CLI互斥均经独立复核通过。旧源码/pinned68项不变。

调度者在审阅GO后实际执行一次 `-J`，2.587秒/exit1为预期诊断，采样峰值110936064bytes，targetSHA前后f569...c23a不变；新工具SHA256 `2192b52d1779dfe125fcc399ef5981b998c5f2148644cf476cb5de419424ebc5`。状态由allocated_clusters7前进到9，allocated_objects261324/389648，filled_clusters0；新first_failure allocation/unsupported_allocation/CID45/cluster_index9/offset23159053。root_start_available=false、terminal_root_cursor=null、all_clusters_exact=false仍保留。证据新根 `results/lead-certified-strict/`。

这证明已证规则使认证门禁前移，不等于Function fill、目标根或官方save/read已通过。独立即时source核对确认CID45在当前3.11系profile对应INSTANCE，走显式MODERN_ALLOC_INSTANCE，不是SIMPLE默认分支；strict明确拒绝尚未认证的generic Instance，因此仍为认证门禁而非已读出数据损坏。未在新CID上自动扩名单，最多第二次诊断预算未用于重复同一结果。

调度者进一步只读官方已下载app_snapshot.cc:4594–4604确认Instance不是Function同一count-only规则：ReadUnsigned count后还有Read<int32_t> next_field_offset_in_words和instance_size_in_words；ReadFill:4607–4644另外读取unboxed bitmap并根据word位置选择32bit-word或ReadRef。必须另核signed/unsigned stream编码、尺寸与边界、不压缩CWS8字段位置和填充规则，不能借Function固定大小证明直接放行INSTANCE。本阶段没有修改后重跑。
