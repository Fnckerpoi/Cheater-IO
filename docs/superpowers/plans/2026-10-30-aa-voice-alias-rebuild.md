# 雷鸟语音 Runtime 别名路由重建开发方案

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpower-executing-plans or superpower-subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 完成可复验的阶段一交付：人工配置 runtime 别名后准确选择目标；身份变化、目标不明、存储失败或投递未知时安全拒绝。

**Architecture:** 本机注册表按 ownerUserId + baseUrl 隔离；别名解析成 connectorId/runtimeId 与已保存配置快照。锁外读取目录，锁内复核操作身份及代次并原子换绑，随后交给现有 DshClient。首版一个当前绑定、一个会话槽。

**Tech Stack:** Android Java，Java 8 源码兼容，SharedPreferences，现有 DshClient/DshDirectory，main 风格 JVM 测试。

**Spec:** [原 v2 设计](../../AA_VOICE_RUNTIME_ALIAS_ROUTING.md)。本文件重新组织实施与验收；下面明确标出的契约修订建议须经审阅，不自动覆盖原设计。

**状态：** 2026-10-30 方案重建稿。本轮只读生产源码并新建本计划；没有执行源码修复、编译测试、签名安装或真实任务发送。

## 目标与范围

用户在本机为不同电脑上的 runtime 手工设置唯一别名，通过语音点名派发，查询与结束本地跟踪保持明确归属。

沿用已确认边界：runtime 实例级别名，不做设备聚合；别名仅本机保存；不新增服务端 API；阶段一切换目标清旧本地锚，远端会话不取消；切回旧目标要续聊时，先在 AA 显式选择其旧会话。recoverSessionId 保持既有空实现。别名精确匹配，不按同音、前缀、displayName 或最近在线设备猜测。

项目、模型、权限来自用户已保存绑定的快照。当前别名编辑器只命名该绑定，不等于每个别名有独立的六卡配置编辑器。独立编辑与多目标自动恢复会话另列后续阶段。

## 当前源码事实

项目根为当前 Turbo-IO 工作区，分支 android。本轮任务卡读取：t-mvuckbyc-x8qwpv 为 in_review，version 16，7项清单已勾，executions 为空。

| 对象 | 当轮观察 | 影响 |
| --- | --- | --- |
| 原 v2 文档 | 标头仍写“未实现”，正文包含两阶段 | 文档状态落后，不代表当前实现已合格 |
| ToolClient.java | 当前607行，包含 DshAnchorStore、persistAnchors、parkedAction 及恢复锚逻辑 | 与上一轮“已回退阶段二”记录不一致 |
| 未跟踪文件 | DshAnchorStore.java、DshAnchorStoreTest.java 再次存在 | 固定备份、查写入来源后再精确回退 |
| normalizeAlias | lowercase 后补少量特殊字符 | 不能称为完整 Unicode casefold |
| verifyAliasTarget | 存在锁外目录 GET，只返回字符串判定 | 没有把请求时身份与代次带到提交校验 |
| 别名UI保存 | 候选校验在锁外，提交内只复核锁 | 仍需防账号/目标变化及旧整表覆盖 |
| 当前测试 | DirectoryCheck 为纯函数测试 | 未证明真实入口的HTTP次数、存储失败和竞态 |

历史436项通过只属于之前版本。本轮没有重跑，不把历史结果作为当前607行路由的证据。文件再次出现的原因尚未确定，不断言仍有某个代理写入。

## 方案选择

| 选择 | 内容 | 取舍 |
| --- | --- | --- |
| A：收敛阶段一，推荐 | 固定基线、移出阶段二、补规范化与并发/入口测试 | 保留批准范围，能分层验收；跨目标自动恢复仍留后续 |
| B：阶段二并入首发 | 分键 StateStore、逐键 pending、崩溃一致性及跨目标续聊 | 需扩范围与重新设计事务，当前残留不能视为已交付 |
| C：只保留手动绑定 | 不做语音别名切换 | 无法满足点名 runtime 调度目标 |

推荐 A。阶段二源码先备份用于审计，不能边回退边继续接入阶段一。

## Global Constraints

- 原阶段一代码最多5个文件，测试收敛一个文件。不改 AA Kotlin、Gradle、Server/Connector API、契约、lockfile、DshClient，不装新依赖。
- 本计划为用户要求的规划交付，独立于原任务的代码文件预算，不将新增文档伪称为符合原“禁止改docs”的实现结果。
- 编译参数：-encoding UTF-8 -source 8 -target 8 -cp artifacts/tools/android-sdk/platforms/android-36/android.jar。
- 保留预算：模型往返最多4次、工具调用最多3次、dsh_dispatch最多1次、每模型往返并发工具最多2次。
- 网络在 DshClient.LOCK 外；锁内仅复核状态、提交和回读。
- 换绑一次 commit 写目标、配置、标签并清旧 session/message/fingerprint；同目标保锚。unknown_delivery 与存储锁不得清除。
- 日志与报告不含token、密钥、目标指纹明文。不提交、推送、签名安装或发送真实任务，除非另有明确安排。
- 回退前备份并确认来源，不能直接删除来源不明的新文件或覆盖他人改动。

## 工具行为与修订建议

| 条件 | 行为 |
| --- | --- |
| 多别名缺target | 建议三个工具统一 TARGET_REQUIRED，HTTP=0、写入=0 |
| 别名不命中/控制字符/非法参数 | 本地明确拒绝，不近似匹配、不尝试其他目标 |
| 别名文档损坏 | ALIAS_UNAVAILABLE，不能退化为空配置沿用当前绑定 |
| 配置快照缺字段 | NEEDS_CONFIGURATION，HTTP=0，不代选 |
| unknown_delivery或存储锁 | 新派发、换绑、结束拒绝且保留锚；状态查询沿用既有恢复策略，绝不触发重发 |
| 派发到有效别名 | 最新目录核验及提交时身份快照复核通过后才派发 |
| 查询/结束非当前目标 | SESSION_UNSELECTED，HTTP=0，不换绑、不伪称已查询或结束 |
| 退役/离线/目录异常 | ALIAS_TARGET_RETIRED或TARGET_UNAVAILABLE，保留绑定、不改投 |
| 换目标派发 | 清旧本地锚后新建，远端旧会话继续存在 |

明确修订建议：原v2只要求多别名派发带target；建议查询和结束也必填，使schema.required与入口判定一致。这比原v2更严格，需要审阅确认。当前磁盘已有三工具required变化，不代表它已验收。

0–1个别名省略target仍沿用当前绑定，不把唯一别名自动当作新绑定授权。无效task必须在目录请求与换绑之前拒绝。

## Review Focus

1. 目录请求期间改账号、凭据、目标或别名：迟到响应不能提交派发（任务3）。
2. 页面保存与语音切换竞争：不能拼出混合目标（任务3）。
3. commit失败/回读偏差：阻断后续请求，不声称旧值必然完整（任务3/4）。
4. Unicode展开、非BMP及旧aliasKey迁移碰撞：不误投、不覆盖（任务2）。
5. 缺target、损坏文档、无效task、未知投递：真实入口的拒绝路径HTTP=0（任务4）。

## 文件职责与建议接口

| 文件（均位于android-addon） | 职责 |
| --- | --- |
| src/com/turboio/addon/DshAliasRegistry.java | 纯注册表、完整规范化、文档校验与共享纯路由判定 |
| src/com/turboio/addon/DshSettings.java | 已保存绑定的命名、最新目录核验与显式保存 |
| src/com/turboio/addon/ToolClient.java | schema、参数、目录和存储适配、单槽换绑与结果归属 |
| src/com/turboio/addon/TurboAddon.java | 目标确认提示与预算保持 |
| tests/DshAliasRegistryTest.java | 注册表、执行路径、故障与竞态测试统一入口 |

以下为待实现接口，不能当作当前已有：

- DshAliasRegistry.RouteSnapshot：不可变owner/baseUrl/authGeneration/selectionGeneration/aliasRevision及六字段目标快照；不持久化token。
- static RouteDecision evaluateRoute(RouteSnapshot expected, RouteSnapshot current, boolean storageLocked, boolean unknownDelivery)：共享纯判定，产出明确拒绝码或允许状态。
- ToolClient.RouteIo：目录读取与存储适配，生产走HTTP/SharedPreferences，测试走内存实现；真实call必须调用同一核心，不能复制一份仅用于测试。

若5文件无法容纳生产实际消费的测试接缝，或无法从现有账户链路取得可靠世代，先报告具体范围缺口。不能用字符串最终相等假装防住A→B→A的ABA变化。

### Task 1：固定并收敛基线

**Files:** 上述5文件；额外阶段二文件仅做确认来源后的备份回退。

- [ ] 记录git status/diff及源码哈希、长度、修改时间。核查本会话代理与后台任务，停止本会话拥有且仍写入的工作，不停止来源不明进程。
- [ ] 将差异和额外文件复制到仓库外带时间备份，验证备份哈希；再比源码哈希，变化则查来源。
- [ ] 精确移除ToolClient多目标锚逻辑，恢复单槽；只回退确认属于本任务的额外文件，保留其他改动。
- [ ] grep与diff复核阶段二不进入阶段一，代码清单为5文件；既有.memsearch与设计文档单独标注。
- [ ] 全新输出目录编译原四套件，保存源码哈希、命令、退出码及日志，避免旧class污染。

完成条件：可恢复备份、源码稳定，后续证据能对应具体版本。

### Task 2：完整规范化与文档校验

**Files:** DshAliasRegistry.java、DshAliasRegistryTest.java。
**Interfaces:** 保留normalizeAlias(String)、resolveForAction(List<AliasEntry>, String)、serialize/deserializeSnapshot现有签名。

- [ ] 先写失败测试：首尾控制字符、非BMP、NFKC等价、ß/SS、希腊组合字母、Cherokee大小写、32/33码点、展开长度、规范化幂等。
- [ ] 使用固定Unicode版本的完整默认casefold映射，建议Unicode 15.0.0 CaseFolding的C/F映射、排除Turkic T；必要表内嵌注册表并标注来源/版本，不新增运行时依赖。对端Unicode版本差异明示为限制。
- [ ] 统一顺序：原始输入拒控制字符→NFKC→trim/空白折叠→完整casefold→键校验；显示名与规范化键各1–32码点。UI不能按UTF-16单元截断非BMP。
- [ ] 旧文档键迁移发生碰撞时整表拒绝、保留原文档并要求人工确认，不覆盖任何目标。
- [ ] 先确认新增断言能暴露旧实现缺陷，再修复并复跑；保留账户/baseUrl隔离与唯一性回归。

完成条件：不是只补几个特殊字符；规则来源、边界与迁移失败均可复现。

### Task 3：目录快照与原子保存

**Files:** DshAliasRegistry.java、ToolClient.java、DshSettings.java、DshAliasRegistryTest.java。
**Interfaces:** 使用上文RouteSnapshot/evaluateRoute/RouteIo；DshClient远端协议不变。

- [ ] 先写失败测试：目录返回前变用户、凭据、服务地址、当前绑定、别名，旧响应拒绝，dispatch POST=0、绑定写入=0；覆盖ABA世代变化。
- [ ] 锁内捕获账号/凭据身份、选择世代、别名修订及配置快照；先拒损坏文档、存储锁、unknown_delivery、缺字段与无效task，再解锁。
- [ ] 锁外以捕获的身份读实时目录，严格校验结构、connector/runtime/type=dsh与可用性，结果绑定操作快照。没有服务端revision时使用本地请求代次，不虚构远端版本保证。
- [ ] 锁内复核当前世代、配置、文档，拒绝STALE_AUTH/STALE_ALIAS/STALE_SELECTION；UI与语音复用同一纯判定。
- [ ] 同目标保锚；换目标单次commit写目标/配置/标签并清锚，逐字段回读。commit=false或回读不符进入存储失败守卫，不继续网络派发。
- [ ] 把验证快照传至DshClient建立配置及savePending守卫，消除route返回后重读绑定造成改投的窗口；不在锁内联网。
- [ ] UI别名保存前同样读新目录；锁内复核账户、候选与文档修订，防止旧整表覆盖新改动。
- [ ] 测试目录超时、损坏响应、commit失败、回读偏差、幂等、未知投递及页面/语音竞争。

完成条件：目录与最终请求属于同一身份快照；已知不安全状态零HTTP；两种保存入口均防竞态。

### Task 4：三工具入口与集成测试

**Files:** ToolClient.java、TurboAddon.java、DshAliasRegistryTest.java。

- [ ] 按修订建议先测三个工具多别名缺target统一TARGET_REQUIRED，schema与call一致，HTTP=0、写入=0。
- [ ] 参数化覆盖0/1/2/8别名、未知target、任意URL/ID注入、错误类型、多余字段、文档损坏及task控制字符；无效输入在目录与换绑前拒绝。
- [ ] 注入RouteIo记录GET/POST/commit与锁持有状态，断言先目录后一次派发、锁内网络=0，正确runtime/clientMessageId归因。
- [ ] 查询/结束非当前目标SESSION_UNSELECTED并保留当前锚；同目标结束仅停止本地跟踪，远端不受影响。
- [ ] 测试串起解析→目录→原子提交→DshClient，不能只测DirectoryCheck就宣称入口集成完成。
- [ ] 提示词统一目标确认规则，保留全部预算，不增dsh_select_target；回执明确新建/续聊/未查询的真实行为。
- [ ] 内存存储测试与真实SharedPreferences设备回读分别记录，不能互相替代。

完成条件：每类拒绝有HTTP次数和状态证据，成功路径无重复派发、无跨目标回退。

### Task 5：编译、APK及真机验收

**Files:** 不扩生产文件范围，验收日志为独立交付产物。

- [ ] 按下节命令全新目录编译四套件，保存退出码与实际检查数，不预填436。
- [ ] 先读现有构建脚本再选择宿主分支，生成候选APK并记录哈希与签名状态；javac通过不能替代整包构建。
- [ ] 真机安装、真实任务发送在用户明确安排后执行；系统身份验证由本人完成，不重复提交任务。
- [ ] 双runtime验收：配置A/B别名→冷启动回读→A派发一次→B派发一次→查B→查A返回SESSION_UNSELECTED→手动选回A旧会话再续聊；核对远端每条消息只出现一次。
- [ ] 多目标不点名、离线、改名/删除、账号切换、unknown_delivery均不误投，记录脱敏归属证据。
- [ ] 记录源码/产物哈希、时间、观察与未覆盖项；以新证据替换历史清单证据。
- [ ] 执行报告提交若仍无可附着execution，保留工具错误并在评论写同结构报告；保持in_review，done仅用户确认。

## 源码验证命令

在项目根运行。预计超过30秒的执行使用DSH_PROGRESS_CLI包装并留日志。Java使用完整路径，不依赖PATH；输出目录每次新建。

~~~powershell
$aliasJavac = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin/javac.exe'
$aliasJava = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin/java.exe'
$aliasOut = Join-Path $env:TEMP ('turboio-alias-' + [guid]::NewGuid().ToString('N'))
$aliasAndroid = 'artifacts/tools/android-sdk/platforms/android-36/android.jar'
$aliasSources = @(
 'android-addon/src/com/turboio/addon/DshAliasRegistry.java',
 'android-addon/src/com/turboio/addon/ToolClient.java',
 'android-addon/src/com/turboio/addon/DshSettings.java',
 'android-addon/src/com/turboio/addon/TurboAddon.java',
 'android-addon/tests/DshAliasRegistryTest.java',
 'android-addon/tests/AaBridgePolicyTest.java',
 'android-addon/tests/AaDispatchFlowTest.java',
 'android-addon/tests/DshClientTest.java'
)
& $aliasJavac -encoding UTF-8 -source 8 -target 8 -cp $aliasAndroid -sourcepath android-addon/src -d $aliasOut @aliasSources
if ($LASTEXITCODE -ne 0) { throw 'Alias compilation failed' }
foreach ($aliasSuite in @('DshAliasRegistryTest','AaBridgePolicyTest','AaDispatchFlowTest','DshClientTest')) {
 & $aliasJava -cp "$aliasOut;$aliasAndroid" "com.turboio.addon.$aliasSuite"
 if ($LASTEXITCODE -ne 0) { throw "Alias suite failed: $aliasSuite" }
}
~~~

APK候选脚本为android-addon/build-embedded-windows.ps1、build-tmig1-windows.ps1、build-tmig1-public-update-windows.ps1；本轮仅发现路径，尚未阅读内容，不指定具体构建命令或包身份。

## 后续与执行交接

阶段二另设计分键会话锚、逐键unknown_delivery、账号世代全键失效、A/B/A交替续聊及崩溃一致性。不能以先写锚文档、再写绑定的两次commit声称跨文件原子。

建议当前会话直接实施，各任务顺序推进，最后安排一次只读整体复核，避免多个执行者同时写ToolClient。若委派，只委派只读审查。

本轮交付止于方案。审阅重点：采用方案A收敛阶段一；是否接受三工具统一多目标target要求。阶段二、独立六卡编辑器保持后续范围。
