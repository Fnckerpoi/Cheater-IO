# REA 逆向官方提词器导入：确认范围与证据计划

## 当前阻断修正

独立task20复审复现两个trial生命周期缺陷，旧`official-import-trial-20261014-094800`包已永久标superseded不可使用：同对象清理后新fixture可沿用旧A授权；晚B文件可能提交11秒后被清理；新A摘要也可能留旧证据。task18已先RED后GREEN修复，task20独立stdin重编断言以及调度者独编Core/Host tests均通过：新fixture/newA废弃旧授权/profile/summary并失效旧generation，B session-spent保留，提交lease至少延长3600秒，nativepending未知即使过期或用户反馈也禁止cleanup。

修正版已重新实际编译并独立核验，唯一路径 `build/official-import-trial-20261014-101800-r6/TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa`（相对official-addon），425,936,693 bytes，SHA256 `6062ab1770702b7298881486489597a55a5c6e18d78e599040c05b32abd5f051`。调度者独立逐成员复核9817baseline原字节保持、仅Runner改+add2/remove0、ZIPCRC、iOS15arm64、production无测试符号、六生产源freeze（Core SHA fcd947ec1c80c1e8a1a341f87aa4601c676b06c427c302437de21cb2bb850d48）全部通过。旧IPA未修改/覆盖。没有新的A/B真机结果，R6修复和打包不代表官方保存或真实新闻入库成功。

当前人机交接：用户已明确选择“尚未安装或尚未测试”R6。等待该修正版的公开稿A实际参数/回包/官方消费摘要；A通过且用户确认后才进入一次B。该门槛不能用更多模拟测试或重新扫描代替，亦不自动操作手机。收到用户新指令/反馈后继续，当前不推进真实AIHOT入库或09:00自动化。


## 用户确认

用户希望使用已安装的 `reverse-engineer-anything` skill，通过逆向找到官方提词器文稿导入接口，再由 TurboIO 导入新闻稿；不是继续 TXT 导出和手动文件选择。用户确认静态优先、必要时仅公开稿的短时真机验证，并同意独立安装 Ghidra12.1.x 使用 REA CLI。最终范围已明确确认理解一致。

允许安装根目录 `/Users/duriea/Tools/REA/`，只使用现有 Node22 与 Java21、进程级环境，不安装 Hopper/新Java、不改全局profile/其他Agent配置。task16 已安装官方 Ghidra12.1.4（ZIP SHA256 `ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db`），用已有 Xcode/make 构建随包官方 Mac decompiler，未下载其他依赖。REA CLI6.4.0 scoped provider 验证通过，调度者又在独立目录实际复跑 arm64公开fixture的函数分析、伪代码与关闭会话；得到负数返回-7、否则count*3+11的真实输出，Evidence `ev_ed59310a4dcb39bdbe3ee7f9ebf275f7eb4e344de4696c41144b4c8717d81ad8` / `ev_a67ddbf87266eefd65ac664df644329740988c530b3d3e5aa5300bd776e04250`。

当前 DSH 未注册 REA MCP；分析成员通过本地 stdio 子进程使用实际 tool catalog 和独立持久会话，不修改客户端配置。工具安装及小型 fixture 成功不等于大型 Dart AOT 或官方入库接口已恢复。实际目标分析仍在进行，不能依此宣称已找到接口。

## 基线与已有验收

- 当前已用户真机验证的阶段IPA：`build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`（相对official-addon）。
- SHA256：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`，宿主research1.0.5（201）。
- 用户确认签名安装、手动取日报、预览、TXT保存、官方手动导入及眼镜阅读1–6顺利完成。这是用户实测反馈，不重复要求同项人工测试。
- 官方App.framework/App与原基线字节相同，SHA256 `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`；既有三轮只读Dart调查报告可复用候选和版本身份，不能再把字符串名称当可调用接口。
- 之前RayneoNet消息仅眼镜transport，原诊断包因无法证明保存观察效用而停止。REA新工作不恢复该无效路线，不猜DB直写。

## 问题与所需证据

| 问题 | 必须获得的证据 |
|---|---|
| 官方导入入口在哪里 | 真实method/function/selector/调用边界及参数，而非仅名称 |
| 文本如何进入导入流程 | 标题/正文/path/bytes编码、允许格式、取消和错误结果 |
| 如何保存到官方文稿库 | import消费者到file/meta保存的真实调用链；程序可触发性需另证 |
| 入库成功如何确认 | 官方稳定稿件ID、列表或正文回读、重启保持；眼镜ACK不能代替 |
| 重复及结果未知怎么处理 | 真实去重/查询/对账方式，提交未知不盲创建 |
| TurboIO能否直接调用 | 公开测试稿的实际程序创建与官方回读，不用UI手动入库冒充自动入口 |

## 分工与边界

1. task16（official-probe-package-integrator）：官方来源下载/校验与独立Ghidra安装，REA真实arm64 Mach-O样例分析验证，提供实际路径/版本/环境/Evidence给其他成员；不改App或Agent配置。
2. task14（dart-manuscript-contract-inspector）：REA官方Dart导入/保存/回读调用链。引擎就绪前只做无需引擎的artifact/identity，之后真实decompile/xrefs/calls，记录常量地址与函数地址区别。
3. task15（official-manuscript-inspector）：独立原生文件导入桥及返回契约。file_picker只证明文件结果，不自动证明文稿持久化；与Dart消费者证据组合闭环。
4. 调度者复审Evidence、范围与未知；闭环后才派发接入实现、测试、独立打包和真机验收。

引擎为12.1.x已获授权，具体release和下载SHA仍须核实。安装任务的共享write_scopes只能workspace相对路径，外部安装目录授权记录在task16描述，不把参数拒绝视为不能安装或绕过文件策略。

## 安全与判定

- 不改原IPA、现有已验阶段代码或私人稿件/凭据；分析文件和临时数据库独立保存。
- 不在未经独立范围设计时开启全量长期hook、下载手机容器或安装未知解析器。
- 静态Analysis不能当运行观察；反编译pseudocode不是原源码，xref零不能证明功能不存在。
- 仅通过实际命令返回的工具和provider状态声明能力，保存完整Evidence、ID、限制和搜索边界；不以doctor/程序启动通过当反编译成功。
- Native调用或文件picker返回不等于官方入库成功；Dart地址恢复也不等于可从ObjC安全调用。
- 若需真机验证，限定公开稿/真实候选边界/短时停止；观察与写入结果分开，先公开稿再新闻稿。
- 新接口版本仍不启用09:00定时发送等后续目标；完整每日自动化要求保留。

## 交付门槛

任务不能仅以新工具安装或更多字符串报告结束并宣称实现；必须交付真实入口契约与可验证的程序入库，或给出具体剩余未知和最小下一材料。若入口已验证，正常编码工具接入“导入官方提词器”，真实编译新IPA且保持旧HTTP/导航，提供公开稿创建/回读/重启/重复与未知状态验收记录。

## 本轮实际结果与用户批准的接入试验

- task15 已用真实REA/Ghidra恢复 FilePickerPlugin 官方method channel、dispatch、单pending callback、文件选择/结果序列化与正常 handleResult 清除pending的调用链。完整Evidence见 `REA_TELEPROMPTER_NATIVE.md`；调度者已直接复核register、handleResult、FileInfo.toData及dispatch/resolvePick原始伪代码。原生结果是文件信息array，不是文稿保存回执。
- task14 默认大型App启动两次330秒超时；唯一额外预算使用真实进程级 `REA_GHIDRA_STARTUP_TIMEOUT_MS=780000`，单session、首错误停止，786.768秒内收敛，后续xref未提交。Dart函数/保存契约仍未知，不能说格式unsupported或接口不存在。owned分析进程均结束，源hash未变；不继续第4次巨大全import。
- 用户明确批准“观察＋一次功能接入试验”：phaseA用一份唯一公开TXT原样观察官方真实请求及原始回包；phaseA参数/形状/公共hash匹配且用户确认官方消费后，phaseB仅一次向官方已有请求提供TurboIO生成公开稿。
- phaseB不是自建DartFuture或裸callback：候选必须走原handler建立pending/flags和正常handleResult完成与清除路径，检查runtimeABI/host身份/单文件/本次上下文，未知或不匹配则不介入。原子清理、取消/重入/迟到与hook恢复要有自检证据；没有相应证明不能部署。
- 此试验可免去系统文件选择，但官方流程若仍要求保存由用户确认；不称无UI官方CRUD，不把native提供成功作为入库成功。用户官方列表回读/保存/重启结果独立记录，最终每日自动化要求不变。
- task18（official-manuscript-inspector）：新独立OfficialImportTrial Core/Host/UI侧挂模块及Tests，默认关闭、显式短时arm、脱敏摘要，phaseB必须通过phaseA及用户门禁，不9点/不麦克风/不读私稿/不直接写库。允许只对本次严格匹配公开临时URL读hash，无目录枚举，不持久化原路径或正文日志。
- task19（official-probe-package-integrator）：新build/package/tests/操作文档和独立IPA，在最新实测stage输入上再新增sidecar；保留旧addon与日报stage原字节，不覆盖/安装/启动/签名。待真实source通过review后实际arm64链接和ZIP/成员差异/SHA验证，不能交空包。

新试验源码和IPA已实际完成，尚未得到用户真机接入成功证明。唯一交付：`build/official-import-trial-20261014-094800/TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa`（相对official-addon），425,936,192 bytes，SHA256 `787689ebd9dbe65b96fcf56fc13f671b93846252bcd7f8df158bed6330e23027`。

调度者独立跑Core/Host两个合成测试、8项新打包测试及旧Source209检查均通过；独立逐成员复核9817旧成员字节一致，仅Runner尾部新增依赖，新增trial dyLib+标识，移除0。ZIP CRC、thin arm64/iOS15、旧依赖顺序/UUID、原stagebase hash不变均核验通过。旧addon、日报stage、file_picker、App/Flutter字节保持。新sidecar139128bytes，SHA256 `330fe6cbc7b72ad8286e577408ea1501ed0993bb84a07d20257d397518f5bd55`，生产未定义testing宏且不包含测试绑定符号。

部署/操作详见 `OFFICIAL_IMPORT_TRIAL_TEST.md`。本版本A-B必须同一次App会话、不在A后清理/重启；B可新增唯一第二份同名公开fixture，仅用户确认后试一次，不自动处理覆盖/冲突或重试。phaseB仅此7392公开稿，不代表真实AIHOT新闻已入库、无UI保存API、稳定DocID或每日自动化完成。下一步待用户原样A参数/消费摘要及一次B官方编辑器/保存/列表回读结果，再决定新闻接入。
