# REA 官方 Dart 稿件导入/保存/回读调查

## 状态

task-14 的有界静态调查已结束，官方 Dart create/read contract 仍 **UNKNOWN**。初轮默认预算失败后，lead批准的唯一15分钟进程级重试也在780000ms启动deadline超时；总耗时786.768s，首error后没有任何下一查询或自动重import。没有取得App目标映射、xref、Dart函数或保存/回读代码证据，不再第4次import。已完成执行无关的 REA 包图和 target identity，并在 task-16 实际 fixture 验收后打开一个 Ghidra native session。两个启动/默认分析尝试各在330000ms provider deadline超时，尚未取得 App 原生映射、xref或反编译结果；**不能称 Dart格式 unsupported，更不能说接口不存在**。会话已 `close_binary`，transport exit0、owned Java/stdio 已退出，原IPA/App散列不变。本轮仅静态调查，不执行 App/Flutter、不访问手机、私稿、密钥，不写官方 DB、不实现接口、不 commit。

## 目标与验收问题

1. SaveDocument / OpenEditor / PinDocument 官方导入动作对应的真实函数与输入参数。
2. 原始 text parse → 内容文件关联 → metadata serializer/database writes 的真实代码/对象池/调用边。
3. 官方列表 readback、稳定 item ID、异步完成/刷新语义。
4. 上述 Dart AOT 逻辑能否由 native 安全触发（须独立证明，不从有函数推出可调用）。

每项最终按回答/部分/未知分层；不以字串、零 xref、泛 native pseudocode 推出官方 API 或接口不存在。

## 不可变 target identity

- 目标：`official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`。
- IPA SHA-256：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`，实测匹配。
- `Payload/Runner.app/Frameworks/App.framework/App` SHA-256：`f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`，实测匹配旧基线。
- 旧 `/tmp/dart-manuscript-contract.Pl3MY1/App` 本轮不存在；只按这一 ZIP entry 物化一次为 `official-addon/build/rea-teleprompter-import/dart/App`。并非重新生成或改写 IPA。
- 原始验证记录：`official-addon/build/rea-teleprompter-import/dart/00-identity.json`。
- 既有 Dart 版本/token/候选词以 `AIHOT_DART_MANUSCRIPT_CONTRACT.md` 为前轮依据；本轮不把其数据偏移当作函数地址。

## 工具与生命周期

- 已缓存 `rea-agents@6.4.0`，命令入口 `/Users/duriea/.npm/_npx/dea9475797100b20/node_modules/rea-agents/scripts/rea.mjs`。
- 已使用 Node：`/Users/duriea/.nvm/versions/node/v22.22.3/bin/node`；未 npx 下载或安装第三方 Dart 解析器。
- CLI `--schema` 默认返回 YAML，不是 JSON；实际 graph workflow 接受 `{ "inventory_evidence": [canonicalEvidence] }`。
- 建立一个本地 stdio MCP 桥接进程，只完成 initialize/tools/list。保存170工具的实际 schema；没有 MCP 配置/注册修改。后续一个 `open_binary` 选择 Ghidra，按实际 `xrefs`、`resolve_containing_procedure`、`procedure_pseudo_code`、`procedure_callees` 等工具名顺序复用同一 session，结束 `close_binary` 后停止桥接。
- `02-provider-availability-before.json`：Ghidra `not_configured`。这是安装前快照；不能表示 task-16 完成后的能力。
- 派生桥接脚本：`dart/mcp_static_bridge.py`。它只通过已有 REA CLI 管理分析进程，不加载目标执行；完整响应写 `dart/responses/`，完整 catalog 为 `08-mcp-tools.json`。

## 初始 Evidence ledger

所有路径以下相对 `official-addon/build/rea-teleprompter-import/dart/`。保留完整 inline/canonical Evidence，不只保存打印摘要。

| 记录 | Evidence ID / 身份 | 观测与边界 |
|---|---|---|
| `01-stage-artifact.json` | `ev_30df82dc1a0720ef08c5c2c810c70c8df155e3a18291a4dcb3c3a3e3dc4d0133` | REA `inspect_artifact`；静态包 inspection，不执行 artifact |
| `04-inventory-evidence.json` | `ev_2a4aae84b03d2078dbdb36cc509052b837f8448efd94b64fa6deb247c093de87` | 完整 inventory 子 Evidence；manifest `agm_d511ff0806e9452e3f047e77ed4801f2b47d9dae3d67a48bf3508816c4ce92a0`；8385 nodes、9819 occurrences、9818 edges |
| `06-apple-graph.json` | `ev_837f8c9fa48189b011da5b30b98da65e48eb0e1edb90a26b9b1de2974efac753` | 根 `Payload/Runner.app`；App/Flutter framework executable candidates 的 path+identity。bundle角色、bridge候选仅 path-convention，不是观测调用 |
| `00-identity.json` | 本地散列检查，非伪造 REA Evidence ID | stage 和 contained App SHA 匹配；旧temp缺失后单次物化 |
| `07-mcp-initialize.json` / `08-mcp-tools.json` | MCP 初始化与完整实际工具 schema | 工具目录没有 Dart-specific named operation；不据此否定泛native反汇编能力 |

REA inventory 明示：ZIP central-directory entries 的 coverage 不覆盖 signing-block/comment；Apple graph 不解析 bundle signing、语言 metadata 或 runtime loading，角色依路径推断。成员总数不作为隐含执行/全语义覆盖。

## 已执行命令

```sh
N=/Users/duriea/.nvm/versions/node/v22.22.3/bin/node
C=/Users/duriea/.npm/_npx/dea9475797100b20/node_modules/rea-agents/scripts/rea.mjs
D=official-addon/build/rea-teleprompter-import/dart
"$N" "$C" inspect-artifact /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa --format json > "$D/01-stage-artifact.json"
"$N" "$C" providers --format json > "$D/02-provider-availability-before.json"
"$N" "$C" project-apple-application-graph "$D/05-apple-graph-input.json" --format json > "$D/06-apple-graph.json"
```

输入graph只取inventory canonical子Evidence，未dump9819成员到会话。包与App散列用 Python 标准库 `zipfile/hashlib` 核对。

## Native deep 分析结果：默认启动预算不足

### 环境和选定 provider（真实观测）

task-16 已验证 Ghidra12.1.4 官方安装及其 bundled-source 构建的 Mac native decompiler，公开 arm64 fixture 得到了真实 function/pseudocode。引用环境 worker 的 fixture Evidence：`ev_ed59310a4dcb39bdbe3ee7f9ebf275f7eb4e344de4696c41144b4c8717d81ad8`、`ev_a67ddbf87266eefd65ac664df644329740988c530b3d3e5aa5300bd776e04250`（见 `REA_ENVIRONMENT.md`）。这是工具能力证明，不是 App Dart 业务反编译证据。

本任务进程环境：

```sh
PATH=/Users/duriea/.nvm/versions/node/v22.22.3/bin:/usr/bin:/bin:/usr/sbin:/sbin
JAVA_HOME=/Users/duriea/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home
GHIDRA_INSTALL_DIR=/Users/duriea/Tools/REA/ghidra_12.1.4_PUBLIC
```

实际 `open_binary` 返回 arm64/mach-o、正确 App SHA。`binary_session` 绑定 Ghidra12.1.4，selection_source=request，profile digest：`20565088524a2d0359b9b96f46ac48e679d774c7b0092c56abc06de8fdbec11c`，run_id `10aa82a1-4e47-4e84-b2cf-12f9529999b0`。profile明示 `import_mode=ephemeral-source-immutable`、`loader=auto-from-header`、`analyzer_preset=ghidra-default`、`function_references=complete-body-and-entry-reference-manager-v2`。open只确认身份和绑定，不能当默认 autoanalysis 已完成。

### 实际请求与结果

| 实际操作 | 请求/响应文件 | 结果及证据类型 |
|---|---|---|
| open_binary(App, provider_id=ghidra) | `10-open.json` | 成功，SHA/format/architecture；MCP结果，无伪造 Evidence ID |
| binary_session(expected_package_version=6.4.0) | `11-session.json` | 真实绑定/profile，未证明分析完成 |
| address_to_file_offset(0x28892e0) | `12-serializer-map.json` | `provider_timeout`, category=timeout, operation=address_to_file_offset, timeout_ms=330000；MCP error不是 canonical成功Evidence |
| read_bytes(0x28892d0,48) | `13-serializer-bytes.json` | 同样 `provider_timeout`, operation=read_bytes, timeout_ms=330000；没有返回目标bytes |
| close_binary({}) | **`14-serializer-xrefs.json`** | 成功 result=null；文件名沿用被队列缓存的原请求名，实际请求内容已改为cleanup并附audit_note，绝不是xref结果 |
| 本地bridge stop | **`15-deserializer-xrefs.json`** | `{closed:true}`，随后background job `bash-167` exit0；不是deserializer xref |

原计划14–23（核心3xref、containing procedure、typed data、精准procedure搜索、typed枚举搜索、一项tiny VM native calibration）的 JSON均保留为 `requests/*.deferred`，**未执行**。所以本轮没有零xref观测、没有真实Function/Code/ObjectPool关联，也没有sample pseudocode；不可把未完成分析的缺失当negative search。

### 成本、队列行为与收敛

服务日志的实际wall-clock duration：首mapping请求 `331828.57ms`；后续read_bytes请求 `331142.53ms`；close `2.41ms`。两次import共约11分钟，不是两个业务函数的执行时间。

首次timeout后，预排队的read_bytes导致provider再次启动import：owned Java PID由2790换3534。记录到首进程4:41时约78.2%CPU/RSS931328KiB；第二进程2:55时约77.8%CPU/RSS861336KiB。这只能证明分析成本和确有第二进程，不能推算完成比例或假称明显推进。未取得Ghidra阶段/函数完成统计。

发现首次error后立即将所有尚未执行的后续probes延后，当前RPC结束后close/stop；没有第三次import。桥接初版预排队不fail-fast，后续重试必须**每次检查response后才延迟提交下一请求**，不能复制这次失误。

`99-final-identity-and-cleanup.json` 保存最终检查：IPA/App SHA与起始一致；原owned PID97736/97737/2790/3534均不在进程列表。`mcp-stderr.log`、所有error/原请求/完整catalog已保留。关闭后不声称可复用一个已分析完成的Ghidra数据库。

## 跨层参考：native picker契约不等于官方Dart保存

另一独立任务真实恢复的 `file_picker.framework` 结果见 `REA_TELEPROMPTER_NATIVE.md`，不是本任务App反编译成果：

- method channel精确值 `miguelruivo.flutter.plugins.filepicker`，原生地址 `0xcbba`；`ev_7e6ab3db1d59da149bb91dc63334f27d36075f9a1782771c3262ffd1aa3e0291`。
- dispatch `0x474c`：`ev_7b79cf6271ae3a5d67b4f27fb3b058101c7425598a8dfb9022d2beb21a54715e`；any/custom走选择器，不是稿件CRUD。
- didPick `0x61f4` → handleResult `0x60d4` → FileInfo toData `0x40fc`：`ev_c79daadefcd752a958edc89369eb34adc6d7f9463d70ea0b87c4d266f41b36c3` / `ev_81407a132d5e3915d2fa977ae78fb2955e0ec93d5d2ec02f95e54d90c6db9653` / `ev_5ec8e3d81f217fa8a1f12c3e992e6c1020ad9c39a57f5b0da2ccb8e0b22d2d04`。
- 返回array of dictionaries：`path`、`identifier`、`name`、`size`、可选`bytes`，没有完整title/body保存契约。callback是当前请求传入的FlutterResult block；自发原生请求只返回其自建callback，不自动得到官方Dart import Future的continuation。

以上地址属于file_picker成员，不属于App Mach-O，不能挪为Dart消费者地址。NSData→Flutter codec/Dart类型、官方parse text、metadata/file write、stable ID、list read仍未证明。

## 原目标逐项验收

| 问题 | 状态 | 当前证据/缺口 |
|---|---|---|
| stage/App不可变身份、包根/组件 | 已回答 | 起始/最终SHA、canonical包图 |
| REA/Ghidra真实可用及App选定profile | 已回答 | 独立publicfixture与实际session绑定；不推出App完成 |
| ImportAction SaveDocument/OpenEditor/PinDocument的真实函数/参数 | 未知 | 候选const名称属于前轮线索；本轮未完成地址映射/函数解析 |
| text parse→metadata serializer/file关联调用链 | 未知 | 无App调用边、Code owner或ObjectPool证据 |
| stable item ID / list read / restart持久语义 | 未知 | 无已恢复schema、回读函数或事务/缓存刷新参数 |
| native可安全触发Dart保存 | 未知 | native picker callback不等于Dart消费者/保存API |
| 工具是否不支持该Dart格式或不存在入口 | 未知 | 当前仅能证明默认启动预算耗尽，不能据此下unsupported/不存在结论 |

**首版自动入官方持久列表仍未满足，禁止依据候选字串/原生picker字段自造数据库字段或直写DB。**

## 实际支持的参数核对与唯一15分钟重试建议

以下只读取实际REA6.4.0安装代码和官方Ghidra12.1.4随包文档，没有修改tool安装、全局env、IDE配置或目标。完整只读source witness（路径、sha、行号、摘录）存 `100-supported-budget-proposal.json`；这不是伪造的REA Evidence ID。

| 参数/机制 | 精确来源 | 覆盖边界 |
|---|---|---|
| `REA_GHIDRA_STARTUP_TIMEOUT_MS` | `dist/config/environment.js:34`、`ghidraStartupTimeout.js:2–12`、`parseConfig.js:63` | 正十进制整数，默认330000ms；绝对deadline覆盖import、analysis、bridge、readiness，不是重写单函数语义 |
| `GHIDRA_HEADLESS_MAXMEM` / `GHIDRA_MAXMEM` | `dist/ghidra/GhidraJavaLaunch.js:47–64`；官方`support/analyzeHeadless:11–17` | -Xmx优先headless、其次全Ghidra、默认2G。仅本process设；这两项不是已观察profile中的语义fields，需另保留resource manifest |
| `REA_GHIDRA_SEED_FILE` | `environment.js:37–40`、`GhidraAnalysisSeeds.js:9–38,47–90,123–161` | 绝对regular文件，format `rea-ghidra-seeds-v1`；每行`0xaddr<TAB>function|code|label[<TAB>name]`；caller-declared seed path/sha/count进入profile，不能当厂商真实函数名 |
| seeds顺序 | `GhidraLauncher.js:278–285` | preScript在默认autoanalysis之前；没有让analysis跳过。apply-report需验证sha/count，未验证Dart entry不可从const地址造function seed |
| 默认import配置 | `GhidraLauncher.js:220–302`、`GhidraAnalysisProfile.js:33–112` | readOnly、deleteProject、默认autoanalysis+bridge postscript，profile `ghidra-default`。未在正常builder发现`-noanalysis`或`-analysisTimeoutPerFile`选项 |
| Ghidra官方`-analysisTimeoutPerFile` | `support/analyzeHeadlessREADME.html:324–331` | 会中断analysis、保留完成的analyzer结果并继续postScript；可为partial，不能绕REA完整readiness gate冒充成功 |

### 已获lead批准并单次执行（失败后结束，最终结果见后附）

1. 只再启动**一个**新owned stdio session，复用同一App SHA，单Ghidra provider。进程级 `REA_GHIDRA_STARTUP_TIMEOUT_MS=780000`（13分钟）；总墙钟 watchdog **900000ms**（15分钟，含极少queries及清理）。必须在启动前传env；不修改包代码或全局profile。
2. 显式 `GHIDRA_HEADLESS_MAXMEM=2G`，保持真实默认，不因为仅约0.9GiB RSS擅自加heap（没有OOM证据）。Java/Node/Ghidra沿已验路径；no seeds，no language override，no nativeAOT扩展。
3. 先只提交一次 serializer `address_to_file_offset(0x28892e0)`；只有默认analysis ready且mapping响应成功，才逐条提交 serializer/deserializer/SaveDocument 三个核心xref。每一步先检查error与remaining总预算，再提交下一条，**不得预排导致重import**。
4. 任一首次error/超时即收敛，finally调用close_binary并关闭transport；不得把错误之后的第二工具请求当自动重试。不成功时不追加默认import；close结果/owned进程/目标hash须再验。
5. xref只有明确指向真实code且函数边界可证时，才在总预算内选择一个函数pseudocode/calls；零xref仅是本provider范围negative，不是Dart无调用。若仅data/object指针不够则保留未知，禁止把const seeds命名为保存函数。
6. 由于timeout/heap不在已观测语义profile字段，单独记录实际process env、deadline、source witness、初终SHA与新run_id；不声称resource参数改变了元数据恢复语义或保证成功。

此方案已获lead批准执行一次；增时没有产生默认analysis-ready结果，更不等于恢复Dart对象池。没有安装第三方Dart解析器或关闭必需分析。以下是实测结束记录，失败后不再第4次导入。

### 唯一获准重试的实际结果

Evidence目录 `dart/retry-15min/` 全部保留；启动脚本 `dart/bounded_retry15.py` 顺序RPC，不使用SDK短默认timeout，总900s含20s清理预留，首error立即finally close。参数由真实process环境传入，不修改global：

```text
REA_GHIDRA_STARTUP_TIMEOUT_MS=780000
GHIDRA_HEADLESS_MAXMEM=2G
provider=ghidra / version=12.1.4 / package=6.4.0
same App SHA=f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a
```

| 结果文件 | 实际结果 |
|---|---|
| `00-run-parameters.json` | process-level预算/env、target、PID、fail-fast policy |
| `03-session.json` | Ghidra12.1.4绑定、语义profile digest仍为`20565088524a2d0359b9b96f46ac48e679d774c7b0092c56abc06de8fdbec11c`；timeout/heap不冒充profile语义差异 |
| `04-serializer-map-request.json` | 实际首请求`address_to_file_offset({address:"0x28892e0"})` |
| `04-serializer-map.json` | `provider_timeout`，category=timeout，`timeout_ms=780000`；error响应，无成功Evidence ID，未返回mapping |
| `stderr.log` | 实际首mapping duration`782462.39ms`；close duration`4.2ms` |
| `resource-observations.jsonl` | 每30s仅owned进程树；约543s同一个Java PID5862，102.1%CPU/RSS1330144KiB。无阶段/完成比例，不据CPU推出快完成 |
| `90-close.json` | `close_binary({})`成功result=null |
| `99-outcome.json` | stopped，first-tool-failure，无后续query/自动reimport；elapsed`786.767985527s`，stdio exit0 |
| `98-final-identity-and-cleanup.json` | 原IPA/App SHA均未变；owned PID5841/5842/5862全退出；queries_after_error=[] |

本次三核心xref均**没有提交**（预置条件mapping成功不成立），因此没有零xref检验，也没有已定位函数可合理反编译。两默认330s尝试加本次780s共三次import，最后一个仅受明确批准；未延长、未第四次import。

最终语义：**REA/Ghidra环境真可用，小fixture及另一成员native picker有真实反编译，但此47MB App在给定默认autoanalysis/启动预算内未ready；Dart契约恢复未回答。** 这仍不是格式unsupported或接口不存在的证明。后续应转最小公开稿消费者证据设计或厂商/匹配符号材料，不重报“只缺strings工具”或凭泛native API搭建入库。

## 下一最小公开稿动态候选边界（只建议，未执行）

若静态仍无法回答，应限定在**官方原有导入UI发起的一次**公开AIHOT TXT选择：官方Dart向上述已证method channel发送的请求参数→同一次result callback→Dart原有Future消费者→正文/标题解析→file/meta保存→官方list项ID与回读。native自行创建picker/callback、把basename当title、把`save`文件导出分支当稿件保存均不构成候选闭环。

最小材料是合成/公开唯一title/body稿件的这条消费者/保存调用证据和重启列表结果（或厂商目标模块源码/匹配Dart Function/Code/对象池符号）。仅以另行明确范围和已授权公开稿流程执行；本task14没有运行任何动态步骤，不扩大到容器扫描、账号/密钥/云端或DB写入。没有消费者地址之前不提出未验证的hook地址或native调用ABI。
