# 提词器候选名称过滤：实际目标查询结果

## 工具与预算

过滤仅在隔离副本增加查询与实际resolver分支来源标记，未更改快照布局或原命名算法。工具SHA256 `62010c7320a49d44971de3d272452fc888287d98abc84f367e187d09bf36eeef`，路径 `/Users/duriea/Tools/REA/r2flutter-manuscript-filter-20261014-165116/source/bin/r2flutter`。

原20分钟实现与独审预算start1791967876/deadline1791969076。独审首轮发现未知decoder空结果可能exit0，修复后真实fixed/varint/linear拒绝、来源优先级、no-k原JSON、cap/resume、无Dart失败关闭tests均通过，独审最终GO。最初lead-query命令在截止后被门禁拒绝启动（SKIP），未偷偷延长。

用户随后通过 `run_frozen_manuscript_queries_only` 明确同意新3分钟仅执行现成查询，最多4次，每次wall45s/CPU35s/软RSS512MiB/output4MiB，空HOME/XDG/TMP/cwd、R2_NOPLUGINS1，不安装编译或扩大工具。该新阶段完整证据位于隔离root `results/lead-query-resumed-20261014-091611/`，authorization.json记录独立deadline1791969550.8258188。

## 四次真实读取

所有query均 `-j -i -w <start> -l 20 -k teleprompter -k manuscript <same App>`，只读本地App，不执行目标，不读取稿库或凭据。

| Q | start | entries_examined | next_index | hits | elapsed(s) | sampled peak RSS bytes |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 90623 | 4960 | 95583 | 20 | 3.429 | 159498240 |
| 2 | 95583 | 172 | 95755 | 20 | 3.459 | 159883264 |
| 3 | 95755 | 64 | 95819 | 20 | 3.263 | 157626368 |
| 4 | 95819 | 3039 | 98858 | 20 | 3.273 | 161259520 |

四次均exit0、completed、fixed decoder、truncated=true/done=false，目标SHA前后均 `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`。RSS为软采样，不保证瞬时硬上限。共80条hit、页间扫描连续无重，覆盖IT index `[90623,98858)`；总tablelength111178，**尚未覆盖全部代码区，更不能说零遗漏**。

## 值得继续核验的候选

当前全部hit的name_source为 `code_index_map/candidate_graph`，仅表示选中了已恢复候选图名；尚未逐项证明真实Code.owner而非Function反补，更非native可调用函数。80项包含63个allocation命名stub和17个method候选，下面仅列关键少量：

| 名称候选 | IT index | code_index | VA / 说明 |
|---|---:|---:|---|
| AllocateTeleprompterMetaDatabaseProviderStub | 95562 | 4939 | 19285656；对象分配stub，不是DB操作 |
| AllocateTeleprompterManuscriptAbsPathProviderStub | 95771 | 5148 | 19347928；路径provider对象分配 |
| `_MixinApplication101__Teleprompters_MetaDataPersistence_FileOperation_ItemOperation_18353248.dyn:add` | 95775 | 5152 | `0x1273a04`；可能dynamic forwarder，不能直接当save |
| 同mixin `_anon_closure` | 95796 | 5173 | `0x1274be4`；闭包身份和上下文待查 |
| AllocateTeleprompterFileStoreProviderStub | 95806 | 5183 | 19353636；对象分配，不是已恢复文件存储API |
| AllocateTeleprompterRepositoryStub | 95822 | 5199 | 19355712；对象分配，不是入库事务 |
| TeleprompterUiEventHandler `_anon_closure` | 98303 / 98331 / 98332 / 98334 | 7680 / 7708 / 7709 / 7711 | `0x130e5f4`等；UI事件候选而非无UI入口 |

另有display/fileMeta/scene/settings/ble位置的dyn:toJson，不因“序列化”就解释为官方保存或回读。HeadlessLineMapper只是分配stub，名字含headless不等于headless文稿保存入口。

## 结论与继续范围

已从字符串候选进展到具备绝对ITindex、code_index、原生地址和候选来源的业务相关条目。这不是接口闭环；尚无参数/返回codec、业务对象/isolate上下文、稳定DocID或保存读回证明。

当前4次目标查询预算已用尽，不继续新parser/整表扫描。task18独立离线复核80/80候选都在__text并精确匹配LC_FUNCTION_STARTS；index/codeindex/pc关系全匹配；分类63 Allocate、8 toJson、8 closure、1 dyn:add。当前覆盖8235项，尚余12320个code entries未覆盖。

重点 `dyn:add` VA0x1273a04至下一LC函数起点0x1273af8共244字节，精确分段反汇编显示：从x4参数描述符恢复参数，Dart栈帧、对象tag/CID检查；正常路径0x1273ae0直接BL0x1273af8随后返回，x4由PP[x27+0x84250]装载。符合动态调用适配/参数检查forwarder推论，并非可直接当C函数调用的保存API。目标body、PP内容、异步/返回值和文件/db写入未解。

task19已完成直接callee的有界离线切片，实际总读取292B：0x1273af8完整124B、helper0x115a480完整40B、下一工作体0x1273b74前128B。0x1273af8从描述符取参、PP载值并调用helper，把结果作为参数继续BL0x1273b74，尾跳0x1168658；仍符合setup/forwarding推论。helper经线程槽[x26+0x618]间接调用，具体语义未知，未命名InitAsync。工作体0x1273b74的下一函数边界0x1273dc4，共592B，剩余464B尚未检查；没有已识别file/db提交、合法DocID或官方readback证明。

task19报告及原始精确切片：`/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/dart-add-callee-review-gs8ds3b9/{report.md,evidence.json}`。未启动新parser/Ghidra全分析、未执行目标、未猜PPbase或保存回执。后续不通过无限顺藤摸调用链制造已完成印象：下一研究应围绕明确工作体的参数/对象池关联与保存回读证据集中设定范围，不扩为UI自动点击、数据库直写或原生自造callback。
