# 最终交付目标：完整NewsHub自动入库IPA

## 用户已确认的终点

本轮用户明确选择“最终要完整 NewsHub 自动入库 IPA”，并允许中途仅为取得真实稿库契约提供测试包、由用户自行签名安装及反馈；中间取证IPA不计最终完成。新持久goal `goal-ec1f3c58-1dfc-4b22-9fda-bac8e686ac9b` 承载完整目标，不能在源码、默认关闭模块或单纯ZIP构建阶段宣告完成。

## 固定约束

- 官方iOS1.0.5(201) + Strix1.0.4.12；HEADd257d2e合并与用户原未提交文件保留，不升级1.0.6、不刷固件。
- 用户允许匹配引擎直接写官方DB；首轮仅无私人稿的隔离副本与公开样稿。正式日常环境须另行确认备份、迁移和回滚。
- 不自动签装、启动手机或读取私人稿件、偏好和凭据。中间测试产物的用途、能力和未验项须准确，不暗示整个官方App已被断网。
- 最终必须每来源独立成稿、MCP/RSS可配置且保留TinyFish、自定义时间、10分钟明确失败重试；结果未知先对账，不盲目重增。
- 成功只以官方列表出现、打开正文正确、重启持久及幂等证据计，不以native/DB返回、导入计数、落地文件或marker计成功。

## 分层验收路径

1. 真实resolver透明性：自有动态库与实际dlsym证明特殊handle及TLS错误保真，非目标保持原caller语义；实现最小桥而不是再只出拒绝报告。
2. 运行时受限适配：精确loadedidentity/入口/原参数透传、key非空零读、一次性有限schema采集；无真实资格不得自设truthyflags。iOS签名后验证需通过明确中间取证包与反馈完成，不能用host测试替代。
3. 真实稿库契约：backend/schema/路径/加密状态/ID/正文关联均取证，禁止猜字段或拼原始库字节。仅读schema不能外推正文目录或稿件归属。
4. 匹配引擎写入：正确Dart线程／isolate、所有权／事务、正文关联、保存后独立读回；未知结果先对账，重启恢复不盲增。
5. NewsHub整合：每source/day/content job、时间配置、失败600s重试与等待入口状态、应用启动接入、非前台暂停限制及唤醒恢复均验收。iOS任意时间可靠后台执行不能仅由定时器承诺。
6. 最终包：真实依赖与入口、独立身份、原始成员/UUID/ordinal精确差异、最小权限计划、编译来源与完整SHA、运行验收证据；只交未签名版本，不伪造签名安装或官方成功。

## 当前并行任务

- task62：`manuscript-lab/resolver-bridge/` 真实动态库fixture与透明选择桥，host实际x86_64，arm64编译／静态证据与iOS运行证据分开记录。
- task63：仅`TIONewsScheduler.h/.m`与`newshub-tests/`，补WAITING_RUNTIME_CONTEXT、UNKNOWN_NEEDS_RECONCILIATION、经官方证据确认的SAVED契约及恢复／重试，避免“生成完成”被当成入库。
- task64：核对完整IPA模块与可信原包输入，形成实际assembly步骤；不提前登记未审核模块或生成假整合包。

现有安全重构与旧错误包撤回记录见`NEWSHUB_STAGE_B_REFACTOR.md`。被撤回的b44fd88...IPA保持拒绝，不能在最终目标扩展后重新作为可安装版本发出。


## 本轮新增的事实与验收

- task62真实自有动态库RED证明nested C wrapper改变RTLD_NEXT查找：baseline333、nested222；保留原caller的汇编尾转发真实修复。x86_64执行与arm64编译分别记录；没有操作官方slot或执行引擎。
- task66固定IsarPlus1.2.6源证明iOS用DynamicLibrary.process；task69固定Dart3.11.5源进一步证明process存RTLD_DEFAULT（iOS SDK值-2），lookup原样传给dlsym；executableLibrary才dlopen(NULL)。所以NEXT的caller差异不能泛推DEFAULT，ordinary-only也不能声称覆盖iOS路径。下一task71只做精确DEFAULT真实fixture/策略，不放宽所有特殊handle。
- 固定Dart commitc70f78e7d682c158c15ca0c26c729b3ccb932284：ffi_dynamic_library.cc SHA d70338af2195253691f72b589388d22e3d751671a637aa4f4bd920fcfc7fa386，177–183证process；globals.h证APPLE时同时MACOS/iOS条件；utils.cc证先清dlerror再dlsym后取error。公开源不能替代exactFlutter实际handle观测。
- task64发现导航硬绑定旧研究bundle/AddonUUID、HTTP主build限定研究且codesign，不能只改BundleID就称保TinyFish/HTTP。task65精确编译期identity helper已由task70独审GO，保留原版本/实际类镜像/UUID门禁；syntheticUUID不授权实际新Addon。manifest bda1ac2f...aca5c0。
- task63新增保存结果/跨重启对账契约。task68独审先发现持久化失败丢UNKNOWN、来源移除后迟回调仍保存，R1真实RED修复。独审重新运行58checks与arm64语法PASS、重跑两个反例不再增save。Scheduler.m最终SHA a1a694f780f0f79b8406391d83747d2c24009dbcb604a8bbefcf8210bbee4883；header71d214f912a7dcfa3ce115e8ad273e750ed14738a8a8a874c92575942614c6a4。桥nil仍WAITING，完成生成与官方保存分离，unknown先持久再submit、恢复只对账。

以上GO只覆盖模块与接口，不代表官方保存。主队列单实例及现有defaults durability合同不等于多进程CAS或断电认证；完整IPA目标持续进行中。

## 当前实际构建与入口状态（本轮）

- task73新鲜输出 `official-addon/final-build/build/final-task73-20261015-0856/` 已由独立审查有限 GO，仅为两份未签名 arm64 内部 dylib。NativeAddon SHA `cf4dfcb29fec29e583f22555ef47593ee587edb1d986d92eeaeed4c939a6ab42` UUID `AEFBC764-A565-362B-A8BE-95374D7ADF2C`；NewsHub SHA `6f41443b4fd7294edee3842d0e2d0f0a7338881b5d3c139f88f715f72015a05f` UUID `791FE86B-1863-3D95-AA88-DDC403BC7189`。118 inputs/source hashes与两模块实际link identity独审复现。没有IPA；未签名/安装/运行、无联网、无官方DB保存。
- NativeAddon binary scan发现旧官方偏好/Keychain域残留于RecordingTextUI，task79已针对域名最小改写且task80独审GO。task73这轮重新编译时应必须采用新源码哈希与UUID，不得重用0843 partial。
- task75已有自有 mmap 的真实 CAS 安装器样例，task83独审GO限于该测试模块；生产实际Install仍因为镜像寿命/槽当前期望值未被证实而返回`BLOCKED_UNSUPPORTED_LIFETIME`。它不读不写官方Flutter槽，也不能代表IPA入口接通。
- task84现启动独立的loader/bootstrap可行性工程，重点验证原Runner依赖、main前constructor/FFI lookup顺序以及精确Flutter `.la_symbol_ptr`当前lazy/resolved值。会新增严格隔离模块与fixtures，不能用host fixture当设备验证；任何无法证明的加载时序、当前VM写权或引擎入口资格继续阻断，不CAS实际slot。

因此下一次必须审查新的bootstrap能力及其与双模块包的整合，之后才可以谈面向用户的中间取证IPA。现无可安装候选，不应签名或安装前述旧Stage B或只含两个dylib的内部产物。
