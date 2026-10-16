# NewsHub 直接稿库写入：隔离验证计划

## 新授权与不变的验收

用户明确允许直接写官方数据库实现入库，撤销此前禁止DB直写的用户限制；但仅数据库调用返回成功不能计为官方入库。用户确认先在无私人稿件的隔离副本／稿库中用公开样稿验证，首次验证前不修改日常稿库，正式环境另定备份、迁移和回滚。

固定组合：Strix OS 1.0.4.12 + 官方iOS1.0.5（201），工作基线d257d2e。不升级1.0.6、不刷固件、不自动签名或安装，不读私钥、账号、私人稿件或其他App容器。最终仍须无需逐篇点击、无页面跳转，官方列表可见、正文可打开、重启持久、重复与未知结果不盲增。

本文替代旧报告中“禁止DB直写”的当前路线限制，不改写旧报告的历史观察。官方Dart源码不是本路线必需前置；真实数据库格式／引擎及其安全Interface仍需证明。

## 已取得事实

### 目标身份

既有1.0.5历史包经只读核定：App成员SHA256 `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`。仅提取两个存储引擎成员，未执行引擎或App、未读手机数据库。

- `isar_plus_flutter_libs.framework/isar_plus_flutter_libs`：2675840 bytes，SHA256 `728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88`。
- `sqflite_darwin.framework/sqflite_darwin`：272640 bytes，SHA256 `5e9880de65045f1600e355e0f20e235638c4752a617fbbd28ded46fc7d0725df`，链接系统SQLite。不能由两者存在推断官方稿库实际选哪个。

### 原生引擎Interface候选

task36从Isar的isar_version实际12B指令与只读字符串确认核心版本1.2.6，而不是framework包装版本1.0.0。官方pub.dev isar_plus1.2.6源码archive SHA256 `ff5fff21fbe3a5f9307cc47af087f890273c288ad6f75992e1582fa85e7b5456`；生成bindings SHA256 `60271d2db5813a68083fcde5e95047ce862cf1fbcfbd63bbaf83221bd22fcd80`，其中101个isar_*符号全部在目标导出中，version/open/txn有限静态代码与生成原型一致。

已有事务、JSON导入、查询、JSON读回、ID、copy/close等真实导出。**这不是调用授权或完整ABI认证**：CString是opaque对象、不能当UTF8 char*；open ratio是float；import_json接Txn**而非Txn*，只返回数量。所有权、线程、事务消耗／替换、error/buffer生命周期、copy一致性尚在专项源码核验。没有实际稿库schema、实例路径、backend、加密状态或官方记录ID证据。不凭框架名宣称加密或未加密。

证据与bindings：`/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/db-engine-static-105-l783jv66/`，task36-abi-report.md、task36-bindings.dart、task36-export-match.json。

## 实施分层与停止条件

### A. 独立实验包与安全门禁

新模块 `official-addon/manuscript-lab/`，不修改原NewsHub/主打包器。不以历史修改包删除Addon后伪装clean输入。原包精确1.0.5（201）/UUID/App与engine SHA验证，未知嵌套可执行、旧Addon、共享资源或未定义输入立即拒绝。

独立run-id Bundle ID、显示名、容器；签名计划必须新建最小权限，拒共享AppGroups、iCloud、其他App固定Keychain组和wildcard，不能clone旧profile。post-sign entitlement元数据校验不等于密码学签名／安装验收。输入与输出新目录且不覆盖。Native最小门禁先默认关闭采集／写入，不含StageTrial、调度器、原生导入Hook或假saved/docId。

此层即使编译和预检通过，也不是用户应安装的功能包；必须有真实采集或验证功能并完成后续审核才提供设备测试候选。

### B. 匹配版本的最小schema／路径观测

先核实Rust/C ABI所有权和签名，再设计有限的native观察：仅原有调用配置、schema、实例身份与路径metadata，不读取数据库记录或密钥，不改变原操作行为。不能重解释opaque CString为未经证明的Rust内存布局。若只有符号没有prototype，停止，不调用。

只在新空白lab容器和公开样稿会话内工作，路径必须canonical、在已选自身子根且无symlink；共享容器／外部路径／已有不明数据即停，不删除数据来伪装空白。签名隔离仍须运行时确认，不声称它消除了原App所有硬编码行为。设备启动前明确要求离线、不登录，未授权不自动启动。

若有encryption-key参数，仅记录是否存在，不输出或导出密钥；优先在已合法打开的实例上操作，不索取个人凭据。

### C. 真实公开样稿样本

唯一标记的合成标题／正文、无损UTF8、实际文件<=1,000,000 bytes。允许一次官方正常手动保存作为取证，不算最终自动化。先确定目标collection初始为空，再仅导出这个公开样稿的必要记录、schema与关联公开文件。发现不明记录或归属混杂就停，不dump全库。

优先已证明的引擎read事务／一致导出；不能把活跃mmap/SQLite主文件复制出来而漏WAL。copy接口仅存在不等于一致热备；close／quiescence未证明时不得强关原实例或用kill代替正常关闭。

### D. 引擎级写入与一致性

只在隔离副本中，通过已证引擎事务／JSON等Interface建立真实记录；ID、索引、字段类型、正文格式与路径均由实际schema和样本证明，不猜字段或拼未知数据库字节。清楚区分borrowed原实例和自己拥有的handle，未证明close/abort所有权前不关闭原Dart实例。

正文写入与DB提交建立明确的故障恢复次序；提交失败、正文缺失、超时、崩溃后未知结果、重复请求、并发close均有明确状态和回滚／对账。不能仅import count>0计成功，必须取得真实ID并独立读回对应内容。

### E. 官方验收与NewsHub接回

一篇公开稿由新增写入流程创建，官方列表可见、打开内容完全一致、重启仍在；重复导入不重建，无官方导入按钮点击或页面跳转。此闭环通过后，才接每来源独立成稿、配置时间和10分钟明确失败重试。正式日常稿库迁移单独获得备份／回滚确认。

## 原包获取的明确授权

用户先选择核对公开Release，再明确选择下载并校验约353MB原包：

- Release https://github.com/Turbo1123/Turbo-IO/releases/tag/rayneo-ios-1.0.5-201
- GitHub release392447066／asset576894447
- RayNeo_AI_1.0.5_201_decrypted_original.ipa，353131781 bytes
- 完整SHA256 `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a`

仓库README与GitHubAPI元数据相符；这不是本地下载完成证明。task38负责独立新输入目录下载、全hash／ZIP／版本和成员身份核验，不签名、安装或执行。实际通过后再交stage A预检，来源不符立即停止，不换镜像。

## 当前并行任务

- task37：stage A代码与12项测试、实际最小arm64编译已完成，自检交付仍为输入缺失、无DB/采集的compile-only状态。
- task38：原包下载与校验。
- task39：stage A独立代码／测试／隔离门禁复审。
- task40：IsarPlus1.2.6生命周期补证。

不将这些阶段的测试通过当作官方DB写入或无UI自动入库已完成。


## 实际阶段结果与下一证据

- task38原始公开IPA已完成下载，调度者独立重算353131781bytes及完整SHA39d12aff...966a通过，版本／App／Isar／sqflite身份一致；输入在 `official-addon/build/manuscript-lab-input-20261015-original105/`，未修改。
- task37/39 Stage A两轮有限返工后独审GO：固定整包信任根→exact全成员、approvedprobe精确哈希、TIO namespace误报修复且共享能力仍拒；调度者独立复跑22项测试PASS。
- 实际载体 `official-addon/manuscript-lab/build/stage-a-unsigned-public-20261015/ManuscriptLab105-UNSIGNED-STAGE-A-DISABLED.ipa`，351470051bytes，SHA256 `59e58e26b93bfe1b3aa4d4cf450ece68273b16e13fc1107b3cb4a244a60d2c85`，已独立重算。status=UNSIGNED_STAGE_A_DISABLED_NOT_DEVICE_READY，独立BundleID com.duriea.turboio.manuscriptlab105.public-20261015；device_ready/db_access_enabled/runtime_isolation_proven全false。**暂不让用户安装这版无采集功能的载体作为功能测试**。
- 输出2639个ZIP文件：2635原文件内容与mode不变，改变仅Runner/Info、新增2项probe与关闭marker，移除仅89签名文件；旧156dependency ordinals保留、probe追加157。未签名／安装／执行App／访问DB。

### 原生writer仍有Dart runtime约束

task40固定官方源码表明txn_begin/commit/copy/close经过isar_pause_isolate→当前isolate/exit/callback/enter，不能从任意native/GCD/UI线程直接调用。import_json失败消耗旧Txn且不恢复，不能对旧指针abort，count不是DocID；copy一致性也未证明。报告 `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/isar126-ownership-source-t93_fgve/ownership-report.md`。

因此“绕Dart”只能指不必任意调用Dart业务函数，不意味着完全不受runtime线程约束。task41复用真实原Dart→FFI上下文是潜在策略，仍缺current-isolate核验、锁／重入／事务与错误通道隔离。仅可另审observer-only原调用透传，不先插入独立事务；无原宿主调用时WAITING_HOST_CONTEXT，不保证后台到点执行。

### Schema-only观察的特定机器证据

task42对exact Isar引擎SHA728997...8b88仅读open前1536B：X5 schema→X24→对象+8 byteptr/+16 len，后续字节与长度检查及serde SliceRead／IsarSchema Deserialize对应固定Rust instance.rs42–59。这不是普遍RustString布局保证。报告 `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/tio-schema-role-static-efgegcpg/REPORT.md`。

未来schema观察候选必须先keyarg NULL、exact lab/engine/ABI、合法映射两字段与payload、len<=64KiB且strictUTF8/schemaJSON；仅已知schema槽，不泛缓存isar_string构造器，不读key／records；原调用消耗对象前有界读取且exact-once透传、参数返回不变。hook及运行时采集尚未实现／验收，也未得实际稿库backend/schema/collection／公开样稿ID。下一阶段先实现并独审有限观察与隔离验证，能真正采集后才交需用户签装的设备候选，不将Stage A称采集或DB写入完成。
