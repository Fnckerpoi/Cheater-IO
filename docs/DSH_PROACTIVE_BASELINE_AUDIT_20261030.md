# DSH 主动回报前置基线审查

审查时间：2026-10-30（Asia/Shanghai）。关联任务：t-mvv2pacl-ssfkpn。性质：源码、既有产物记录与现有测试的只读审查；未修改产品源码、未安装或操作设备，未实施主动回报功能。

## 结论与待决策项

建议首版采用已存在的“单会话槽＋完整显式目标链路”候选，再实现单目标单轮主动回报；多目标锚存储留待独立修复和验收。理由是该候选符合已批准的阶段一边界，且同时包含宿主和 AA 的目标透传与守卫。它仍是候选，不能直接称为已验收版本。

当前 android 提交已有阶段二多目标锚实现，与设计阶段划分不一致，且存在会影响本轮身份及结果归属的源码问题。另一种选择是保留该实现并先修复下列问题，但这会把主动回报前置范围扩展为阶段二锚存储修复。

本次授权为对照审查，未将审查自动扩展为替换、合并或修复基线。采用哪一条路线需用户决定；不得直接在其他任务遗留工作树上继续写入或盲目覆盖主工作区。

## 基线身份

| 项目 | 当前主工作区 | 单槽候选 |
| --- | --- | --- |
| Turbo-IO | android，53c07cfb7ea416c0ba2314f02c10e2063bf9a654 | .agents/worktrees/alias-stage1-rebuild，b63e23a6f7e392bdb01b6c7b74bb4da3e70bf47d 加未提交改动 |
| 会话模式 | DshAnchorStore 按目标停放/恢复会话锚 | 单槽，换目标清除旧 session/message/fingerprint |
| AA 显式目标链路 | AaHostBridge 不透传 target，快照无 targets | 宿主候选与 Agents-Anywhere/.agents/worktrees/aa-explicit-target 配套 |

单槽树有四个修改 Java 文件：AaHostBridge、DshSettings、ToolClient、TurboAddon，以及未跟踪的 DshAliasRegistry 和对应测试；不能仅凭 HEAD 重建候选。

本次实算两仓十份源码 SHA256，全部匹配历史 source-hashes.json。清单位置：C:/Users/duriea/AppData/Local/Temp/turboio-aa-target-20261030-final/source-hashes.json。匹配包含宿主上述六份及 AA EmbeddedWorkbench.kt、WorkbenchPolicy.kt、ConfigurationState.kt、WorkbenchPolicyTest.kt。该结论确认源码快照对应关系，不构成独立的 APK 可复现构建证明。

## 主工作区主要发现

以下为直接源码行为或基于代码的并发交错推演，尚未动态复现；现有测试通过不能排除这些问题。

1. **损坏锚文档可能被空表覆盖。** android-addon/src/com/turboio/addon/ToolClient.java:93-99 解析失败返回空表，只有调用方提供 damaged 数组时才报告损坏；279、325、348、377 的四处调用均传 null。切换路径 279-306 从该表构造并写回文档，可能丢掉原有锚及未知送达记录。应保留损坏证据并拒绝相关写入。
2. **结束停放目标可能写回过时整表。** ToolClient.java:366-382 在锁外复制表并删除行，锁内只复核 fingerprint 和 unknownDelivery，再持久化旧快照。其他行的新写入可能被覆盖，同 fingerprint 下更新的 session/message 也可能被删除。应在同一锁内从最新文档修改，并复核完整行身份或版本。
3. **停放状态查询没有对真实行做最终复核。** ToolClient.java:391-407 将行复制到临时 MapStateStore 后网络查询；DshClient.java:389-406 的本地三元组检查只看到副本。请求期间真实持久行改变时，旧结果仍可能返回。需在返回/展示前复核账户、目标、真实 session/message 及代次。
4. **切换分两份偏好文件提交。** ToolClient.java:298、306 先从停放表删除 incoming 行并提交，312-317 再提交活动绑定。崩溃或第二次写入失败会留下部分状态，可能丢失 incoming 的本地跟踪。不能称为跨文件崩溃事务原子性。
5. **显式目标路径不完整。** AaHostBridge.java:39 仅构造 task 参数；17-21 的 revision 不含别名文档或选择代次，76-85 的 snapshot 不含 targets。与具备目标选择的 AA 候选不能直接视为同一协议组合。
6. **目标解析与操作存在切换间隙。** ToolClient.java:237 判断 binding 后返回，545-547 的结束操作另行加锁，556-559 的派发另取配置。无额外 guard 的语音路径若遇到并发切换，缺少贯穿最终操作的所选目标复核。这是静态交错风险，未实测误路由。

条件性发现：parkedMode 是实例可变字段，但已查到 AaHostBridge、DshSettings 各调用新建 ToolClient，TurboAddon.java:252 在一轮内创建后于 325-341 顺序调用。尚无证据证明同一实例被并发复用，故不将“状态查询变成结束操作”列为已确认故障。

## 单槽候选的依据与限制

候选 ToolClient.java:177-198 在一次活动配置提交中清除旧锚，推进 selectionGeneration，并通过 OperationGuard 固定路由快照。候选 AaHostBridge 包含 target 透传、别名快照及操作/本地 commit 前目标校验，AA 配套候选消费这些字段。

这避开了停放表读写路径，但不代表不存在其他风险：历史交接明确保留跨偏好文件失败/崩溃边界，远端接管在锁外执行，本地最终拒绝不能回滚已发生的远端动作。迁移时还需保留当前较新主线改动，逐文件比较并独立构建，不能用旧 APK 全量替换作为实现方法。

## 本次验证

使用本地 WebStorm JBR javac/java，Android 36 android.jar；编译参数 -encoding UTF-8 -source 8 -target 8。脚本：C:/Users/duriea/AppData/Local/Temp/turboio-baseline-audit-20261030.ps1。进度任务 baseline-audit-java；最终后台作业 pwsh-5d006f3928f64730，exit 0。

| 基线 | 套件 | 结果 |
| --- | --- | --- |
| 主工作区 | DshClientTest / DshAliasRegistryTest / DshAnchorStoreTest | 240 / 107 / 80，共 427 checks，通过 |
| 单槽候选 | DshAliasRegistryTest / AaBridgePolicyTest / AaDispatchFlowTest / DshClientTest | 215 / 69 / 25 / 240 |
| 单槽候选 | DshAuthTest / DshDirectoryTest / DshDraftContractTest / DshSelectionDraftTest / DshSessionSelectionTest / DshSettingsPolicyTest | 22 / 30 / 90 / 152 / 52 / 18；十套共 913 checks，通过 |

初次进度命令因 pwsh 不在 PATH 退出，改用现有 powershell.exe。首次主线编译漏带 ToolClient.DirectoryCheck 所需源码和 Android 依赖，出现 10 个编译错误；补齐 classpath/sourcepath 后通过。未修改产品代码或测试断言。编译保留既有 source/target 8、deprecated、unchecked 警告。

两棵树 git diff --check 均 exit 0。主树原有 .memsearch/.index.pid 变化；候选原有改动保留。此次没有重跑 Kotlin、完整 APK 构建、网络或设备测试。

## 历史设备证据不能混用

| 候选 | 签名 APK SHA256 | 已有证据及边界 |
| --- | --- | --- |
| 单槽＋显式目标 | 0e27c47ceb6c85c51c66859be964d5d3268ee355446fe64b3e8317143bffb505 | Temp/turboio-aa-target-20261030-final/device-verification/device-install-verification.md 记录安装 Success、exit 0、设备哈希一致；仅零别名 UI，无双别名或 Send/Progress/End 真实业务 |
| 主提交 53c07cf＋AA 7d799bc7 | df4e144f4265d77dbfe6703a7b8050b94a3d538fbd409cd8feb72f000bf66dac | artifacts/model-device-retest-20261030-2145/REPORT.md 及同目录记录安装成功、设备哈希一致和语音 ASR/SSE；不证明 AA 多目标路由或停放锚安全 |

单槽 aligned unsigned 包 ac52372ed656d0ccf763e2f7828a36dd2b3dc5ec261bcf606eb3b70f3f2b888d 不等于签名包。旧 aa-target-progress.md 与 review-handoff.md 的“未安装”被后续设备记录超越。更旧版本真实往返不能迁移为上述候选的业务验收。本次未回读设备当前安装身份。

## 主动回报后续约束

DshClient 当前按 clientMessageId 定位本轮；有后续用户消息时保守拒绝归因；只有本轮 assistant done 且 runtime idle 才返回 completed（completionEvidence=assistant_done_and_idle）。首版应保留该语义，以账户/目标/会话/本轮消息与选择代次约束监听和展示，区分等待审批、失败、未知送达及无结果空闲。基线确定后再写出监听、去重、停止及显示交付契约并实现。

主动回报三项验收清单仍未满足；本报告完成的是前置审查，不应据此将实现卡移至 in_review 或 done。
