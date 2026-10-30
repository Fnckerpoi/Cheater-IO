# 雷鸟 IO 跨设备 Agent Runtime 别名与工具路由实现方案

状态：开发评审稿 v2（未实现）  
日期：2026-10-30  
范围：Turbo-IO 雷鸟语音助手、内嵌 Agents-Anywhere 工作台、现有 DSH 派发与续聊链路  
修订：v2 吸收独立架构分析并经源码逐条复核后，将工具契约从「独立选择工具」改为「单一工具 + target 参数」，新增确定性路由守卫与错误码表（依据见第 2 节）。

## 1. 目标与设计决策

用户在 AA 工作台为不同设备上的 agent runtime 人工设置唯一别名（如「办公室电脑」），随后通过雷鸟 IO 语音「继续办公室电脑，看下昨天任务结果」准确路由到该 runtime 的会话，完成续聊、查询，不误投其他设备。

**决策 D1：别名只做人机选择，稳定身份做执行路由。** 映射为 `alias -> (baseUrl, connectorId, runtimeId)`；`displayName` 仅显示，不作身份（AA 侧 `displayName` 是 JSON 兼容显示字段，非路由键）。

**决策 D2：单一工具 + 可选 `target` 参数，不做每别名一个工具名，也不做独立的 `dsh_select_target`。** 依据：`TurboAddon.request()` 存在硬预算——每轮最多 4 个模型往返（`toolRound<4`）、全轮最多 3 次工具调用（`toolCount>3` 抛 `tool_budget`）、每轮 `dsh_dispatch` 仅 1 次、每模型往返并发工具 ≤2。两步式（先 select 再 dispatch）在「派发+查询」类语音请求中会消耗 2 个工具位和 2 个往返，余量不足；带 `target` 的单次调用在一次工具调用内完成「解析别名 → 绑定 → 动作」。命名空间工具名（如 `dsh_dispatch_office_pc`）被否决：设备名耦合进注册与授权面、随别名改动重注册、放大误触发面。

**决策 D3：确定性路由守卫优于提示词。** 当前账号已配置别名 ≥2 时，`dsh_dispatch` 的 `target` **必填**，缺失即本地拒绝（`TARGET_REQUIRED`），不落任何 HTTP；仅 0–1 个别名时允许省略并沿用当前绑定。这消除了「模型忘记带 target 导致派到当前绑定那台」的隐式误路由，不依赖模型自觉。别名解析采用精确匹配（规范化后全等），不做模糊匹配，避免同音/近音误投；未命中返回 `ALIAS_NOT_FOUND` 并提示用户从已配置别名中确认。

**决策 D4：首版映射保存在 Turbo-IO 本地，不改 AA Server / Connector / `/api/v2` 契约。** 用户已约定别名人工设置且保证唯一；本地注册表即可满足。跨端共享（手机/Web/Desktop 同名）留待后续契约化（第 11 节）。

**决策 D5：首版单目标单会话槽，切换即原子换绑；per-target 会话锚列为阶段二（本场景必需）。** 现有 `DshClient.StateStore` 是单槽（`dsh_session_id / dsh_client_message_id / dsh_unknown_delivery`），其原子性注释明确防范「pending 已清但 sessionId 残留」竞态。多目标自动恢复会话需按 targetFingerprint 分键重构，不能在首版顺手改。

**「tool call 准确匹配」的边界：** 指雷鸟语音模型的 `dsh_dispatch / dsh_status / dsh_end_session` 被确定地路由到指定 runtime。选定远端会话后，该 agent 自己产生的文件、终端等工具调用由其自身 runtime 关联（`callId/parentCallId` 链），不在手机上二次匹配设备；AA 只投影这些工具事件，不做工具路由。

## 2. 已核对现状（源码证据索引）

语音 → 派发主链（Android 生产主线）：

```text
官方 ASR/NLP 回调（TurboAddon.dispatchAsr/dispatchNlp）
  -> ChatPolicy.eligible 资格门禁（chat/chat/workflow）
  -> TurboAddon.request()：SSE Chat Completions + tools（tool_choice=auto）
  -> ToolClient.call() -> DshClient.dispatch()
  -> POST /api/v2/sessions/create-and-start 或 /{anchor}/runtime/messages
  -> AA Server -> Connector RPC -> DshRuntime
```

**硬预算（决定工具形态，务必保留）：**

| 约束 | 位置 | 语义 |
| --- | --- | --- |
| `toolRound<4` | TurboAddon.java:261 | 每轮对话最多 4 个模型往返 |
| `toolCount>3` → tool_budget | TurboAddon.java:327 | 全轮最多 3 次工具调用 |
| `dshDispatches>1` → dispatch_budget | TurboAddon.java:331 | 每轮最多派发 1 次 DSH 任务 |
| `index<0||index>1` | TurboAddon.java:298 | 每模型往返并发工具调用 ≤2 |
| 工具参数预算 | TurboAddon.java:304 | 单次调用参数 ≤4000 字符等 |

**可复用组件：** `DshClient` 纯状态机（目标指纹、`evaluateSessionStatus`、`describePending`、Transport 可注入）；`AaBridgePolicy` 守卫（STALE_AUTH/STALE_SELECTION/unknown_delivery）；`DshSelectionDraft` 六级目录草稿事务；`DshSettings`/ `AaHostBridge` 显式保存与乐观并发（expectedRevision/expectedAuthGeneration）；AA capability 三态协议（supported/available/allowed + unavailableReason）。

**身份事实（已验证）：** runtime 实例 ID `rti_*` 创建后不可变（PATCH 只接受 name；契约拒绝改 runtimeId/runtimeType）；`name_key` = NFKC + casefold + 空白折叠，但唯一约束仅 `UniqueConstraint(connector_id, name_key)`（infra/db/schema.py:114）——**同 connector 内唯一，不保证账户级唯一**，因此别名注册表必须自己做账户级唯一；`displayName` 是兼容显示字段非身份。

**明确不存在的能力（不得假定）：** 无 alias 数据模型或解析 API；无自动 agent 路由器（目标全靠显式绑定）；无通用 tools/list、tools/call 协议（AA 只投影已执行工具事件）；无多目标会话锚（StateStore 单槽）；`recoverSessionId()` 恒返回空串（DshClient.java:475-477）——未知投递后的会话恢复未实现。

## 3. 总体架构与数据流

```text
用户语音「继续办公室电脑，检查昨天任务结果」
  -> 雷鸟模型（携带已配置别名 enum 的工具 schema）
  -> dsh_dispatch(task="检查昨天任务结果", target="办公室电脑")   ← 单次工具调用
  -> TurboIO 宿主：规范化别名 -> 精确命中本地注册表
  -> 读取 AA 目录，核验 connectorId + runtimeId 仍存在、可用、归属本账号
  -> 锁内原子绑定（targetFingerprint + 会话锚 + 选择代次）
  -> DshClient.dispatch()：有锚续聊 /runtime/messages，无锚 /create-and-start
  -> AA Server -> 指定 Connector -> 指定 runtime
  -> dsh_status(target="办公室电脑") 查同锚结果
```

别名解析发生在本地路由层进入动作之前，不依赖工具名、返回文本或模型口述的 ID。模型无法指定任意 URL、connectorId 或 runtimeId——`target` 只能取 enum 中的已配置别名。

## 4. 别名注册表与 AA 工作台

### 4.1 职责分配

| 组件 | 职责 |
| --- | --- |
| AA 工作台 | 展示已发现 runtime；别名编辑、冲突提示、显式保存；展示当前语音目标 |
| Turbo-IO 宿主 | 本地映射唯一读写方；目录身份核验；原子提交绑定与会话状态 |
| ToolClient | 枚举生成、target 解析、动作分发；预算与未知送达守卫 |
| DshClient | 会话创建/续聊/状态/指纹/送达语义，保持不变 |
| AA Server / Connector | 不变 |

AA 界面只产草稿，用户点击保存、宿主校验提交后才生效；页面浏览不改变语音目标。

### 4.2 本地数据结构

独立小型存储（不与 AA 载荷持久化混用）：

```json
{
  "schemaVersion": 1,
  "ownerUserId": "当前AA账号本地用户标识",
  "aliases": [
    {
      "alias": "办公室电脑",
      "aliasKey": "办公室电脑",
      "baseUrl": "已配置AA服务地址",
      "connectorId": "con_xxx",
      "runtimeId": "rti_xxx"
    }
  ]
}
```

规则：

- 规范化对齐 AA 侧 `runtime_instance_name_key`：NFKC + trim + 空白折叠 + casefold，生成 `aliasKey`；长度 1–32 码点，拒绝控制字符与空名。
- **账户级唯一**：`aliasKey` 全表唯一（服务端只保证 connector 内唯一，本地必须补足）；同一 runtime 首版最多一个别名。
- 映射仅在所属 `ownerUserId + baseUrl` 有效；登出/切换账号不暴露、不复用旧映射。
- 只存 ID 与显示文本，不存 token、密钥、响应正文。

### 4.3 保存与失效

保存时读最新目录，验证 `connectorId + runtimeId` 仍匹配且 `runtimeType=dsh`、归属本账号；目录失败、目标消失、身份不符均拒绝保存并保留旧有效配置。

运行时失效返回 `target_unavailable`，保留原绑定供核对；**绝不**按同名 `displayName`、主机名或最近在线设备自动改绑。删除别名不删远端会话；修改当前目标别名后，下一次动作前需重新确认（见 STALE_ALIAS）。

## 5. 工具契约

### 5.1 `dsh_dispatch`（唯一派发入口）

```json
{
  "name": "dsh_dispatch",
  "parameters": {
    "type": "object",
    "properties": {
      "task": {"type": "string", "minLength": 2, "maxLength": 500},
      "target": {
        "type": "string",
        "description": "目标 Agent 电脑别名；配置了多个别名时必填，必须原样取自枚举",
        "enum": ["办公室电脑", "家里电脑"]
      }
    },
    "required": ["task"],
    "additionalProperties": false
  }
}
```

语义：

1. **路由守卫**：已配置别名 ≥2 且 `target` 缺失 → 返回 `TARGET_REQUIRED`，零网络请求。
2. **精确解析**：规范化 `target` 后全等匹配 `aliasKey`；未命中 → `ALIAS_NOT_FOUND`；不模糊、不重试。
3. **身份核验**：读目录核验 `connectorId/runtimeId` 存在、可用、归属本账号与 baseUrl；不匹配 → `TARGET_UNAVAILABLE` 或 `ALIAS_TARGET_RETIRED`。
4. **原子绑定**：锁内比对选择代次与账号世代，一次性提交 `targetFingerprint`（含 alias 解析快照，别名改名/删除后指纹变化 → `STALE_ALIAS`）与会话状态；同目标幂等，不换锚、不重建会话；换目标时原子清除旧锚（远端会话不取消）并按续聊/新建规则派发。
5. **既有守卫全保留**：`unknown_delivery`/存储锁定期间拒绝一切动作；提交前后锁内复验；HTTP 在锁外。

### 5.2 `dsh_status` 与 `dsh_end_session`

均接受可选 `target`，解析与绑定流程同上：

- `dsh_status(target?)`：查询目标当前锚的进度与回复；无锚返回 `session_selected`/无消息状态。结果按 `clientMessageId` 归因，归属不明即 `unknown`。
- `dsh_end_session(target?)`：仅停止本地跟踪（远端不受影响），不清除别名映射；`unknown_delivery` 期间拒绝。

### 5.3 系统提示规则（随别名 enum 注入）

- 用户点名某台电脑时，本次 `dsh_dispatch/dsh_status` 必须带 `target`。
- 未点名且仅 0–1 个别名时可用当前绑定；多别名且未点名 → 只向用户确认目标，不猜测。
- `target` 只能取枚举值；别名未命中不换近似名重试。
- 提交、查询、结束是独立动作；`submitted/running/unknown_delivery/waiting_approval/idle_no_reply` 均非完成，禁止重复派发或编造结果。

### 5.4 状态展示与诊断

AA 首页与语音回执展示「当前 Agent：办公室电脑」及会话状态；模型口述的别名不作归属证据。诊断仅记录本地 operationId、别名、目标指纹摘要（不含明文 token）、工具名、状态码与有限错误分类，复用 `ToolRoundTrace` 脱敏白名单。

## 6. 会话锚与并发

**首版（单槽）：** 一个当前目标 + 一个当前跟踪会话。换目标 = 原子换绑 + 清旧锚；切回旧目标时从会话目录显式选择其会话（`DshDirectory.sessions` 本就按 runtime 过滤）。这保留了 StateStore 的全部原子性不变量。

**阶段二（per-target 会话锚，本场景必需）：** 按 `targetFingerprint` 分键存储 `sessionIdAnchor/clientMessageId/unknownDelivery`，实现「让办公室电脑…」「再让家里电脑…」「继续办公室电脑」多设备连续交互且各自归因。约束：每个键的 pending 三元组仍须一次性读写（沿用 `clearSessionAndPending` 的原子语义）；任一键 `unknown_delivery` 只锁该键，但账号世代变化、存储失败作废全部键（STALE_AUTH）；迟到回调按锚逐键比对。

**通用并发规则：** 新动作产生选择代次，迟到的目录响应/页面保存/工具回调必须匹配代次否则丢弃；锁内只做校验与提交，网络在锁外；提交前锁内复验身份、目录、目标与待提交会话；不自动跨设备重试；模型 tool-call ID 不作 runtime 身份。

## 7. 错误码与失败处理

| 码 | 语义 | 处置 |
| --- | --- | --- |
| `TARGET_REQUIRED` | 多别名下 target 缺失 | 零网络拒绝；提示确认目标 |
| `ALIAS_NOT_FOUND` | 别名未命中（精确匹配失败） | 不派发；提示从已配置别名确认 |
| `STALE_ALIAS` | 解析后别名改名/删除，指纹变化 | 拒发；刷新解析后由用户确认 |
| `ALIAS_TARGET_RETIRED` | 指向已退役/消失 runtime | 拒发；要求重新绑定 |
| `TARGET_UNAVAILABLE` | 离线/启动中/目录超时 | 保留绑定；不改投他机 |
| `NEEDS_CONFIGURATION` | 目标缺项目/模型/权限/会话 | 要求回 AA 显式补齐，不代选 |
| `unknown_delivery` | 送达未确认 | 保持锁定；不重发、不新建、不换绑 |
| 既有族 | `STALE_AUTH / STALE_SELECTION / session_archived|unavailable|deleted|missing / dispatch_budget / tool_budget` | 按现有语义 |

失败结果本身是外部数据：标 `untrusted_external_data`，模型不得据其内容改别名、切设备或重试派发。

## 8. 实施拆分

### 阶段一：本地映射 + target 路由（首个可验收增量）

1. `DshAliasRegistry`（纯 Java）：规范化、账户级唯一、账号/baseUrl 隔离、序列化、异常类型。
2. AA 运行卡/设置页增加「语音别名」编辑与保存，经 `AaHostBridge` 到宿主注册表；不改 AA Server DTO。
3. 抽取 `DshSettings` 的「校验目录并原子保存目标」为共享用例，语音与界面同路，不复制保存逻辑。
4. `ToolClient.specs/call` 为三工具加 `target` 参数与 enum 生成；实现 TARGET_REQUIRED 守卫与解析/绑定流程；工具参数预算（≤4000 字符）内满足。
5. 系统提示、当前目标回显、错误码返回；全部既有守卫回归通过。

### 阶段二：per-target 会话锚（场景必需，紧随其后）

StateStore 分键重构 + 锚原子性负向测试 + 多目标连续语音验收（见 9.2）。在阶段二完成前，「切回某台继续上次那轮」需用户从会话目录选一次。

### 后续：跨端别名契约（不在前两阶段）

`runtime_aliases(user_id, alias_key, connector_id, runtime_id, runtime_type)` + 账户级 `UniqueConstraint(user_id, alias_key)` + `/api/v2/runtimes/aliases` CRUD 与 `voice/resolve-target` 解析接口；别名 revision 并入指纹防改名竞态。跨端接口变更升 MINOR 并同步各端版本；不得把别名塞进 `displayName`。

## 9. 测试与验收

### 9.1 自动测试

- `DshAliasRegistryTest`：NFKC/casefold/空白、空值控制字符、账户级重复别名、重复 runtime、账号与 baseUrl 隔离、损坏数据、持久化失败。
- 路由守卫：≥2 别名缺 target → TARGET_REQUIRED 且 HTTP 0 次；未命中别名 → ALIAS_NOT_FOUND 且不近似重试；指纹变化 → STALE_ALIAS。
- `AaBridgePolicyTest / AaDispatchFlowTest`：草稿不生效、显式保存生效、A/B 绑定指纹正确、换绑清旧锚、同目标幂等、过期目录/账号/配置回调丢弃、unknownDelivery 禁止动作。
- 预算回归：一轮内第二次 `dsh_dispatch` 拒绝（dispatch_budget）；第 4 次工具调用拒绝（tool_budget）——确保新增参数不突破 TurboAddon 硬预算。
- `DshClientTest` 既有归属、迟到响应、session unavailable、unknown_delivery 用例零回归。
- 阶段二补充：per-key 锚原子性、键间隔离（A 的 unknown 不阻塞 B）、账号切换全键作废。

### 9.2 业务验收（两台真实 runtime）

配置「办公室电脑」「家里电脑」两个不同 `rti_*`：

1. 「让办公室电脑执行 X」→ 只在该 runtime 会话出现一次 X。
2. 「让家里电脑执行 Y」→ 不进入办公室电脑，无重复。
3. 「查办公室电脑结果」→ `clientMessageId` 归因正确。
4. 阶段二后：交替续聊两个目标，各自锚不串。
5. 多别名下不带 target 的派发请求被本地拒绝（TARGET_REQUIRED）。
6. 注入离线、目录超时、别名改名、unknown_delivery：均不误投、不重复发送、不自动改绑。
7. 切换账号后旧别名不可见、不可解析。

验收分层记录：源码测试、构建产物、签名安装、真实目录、远端会话与工具结果各自举证；本地通过不替代真机与远端链路证据（历史安装基线缺失问题仍需按既有交接单独补）。

## 10. 安全与隐私

- 只可触达用户显式配置的 runtime；解析校验归属（`_get_owned` 模式），模型无法提交任意 URL/ID，路由工具不是通用代理。
- 别名提及 ≠ 派发授权（`ToolRoundTrace` REMINDER 语义）；`target` 只选目标，不放宽模型与权限配置，不绕过审批。
- 凭据只在进程内经宿主桥传递，不进 Intent、日志、AA 载荷持久化；别名与指纹摘要按需脱敏。
- `task` 与 `target` 均过长度/控制字符/密钥泄漏校验；工具结果与远端回复按不可信数据处理。

## 11. 成功标准与未决项

**成功标准：** 别名配置一次后，语音仅凭别名即准确命中目标 runtime；派发、续聊、查询、结束本地跟踪共用同一身份与锚；多别名下不存在隐式误路由（守卫拒绝而非猜测）；异常时不误投、不自动改绑、不重复提交；既有 AA 协议、预算与安全守卫零回归。

**未决项（需产品/用户确认，不影响评审）：**
1. 别名粒度按用户表述定为 **runtime 实例级**（一台设备可有多个 runtime，各起别名）；若实际想要设备级聚合，需另行定义选择规则。
2. 首版接受「切回旧目标需手动选一次会话」，阶段二才自动恢复；若不可接受，把阶段二并入首发。
3. 别名本地保存、不跨端同步；接受卸载/换机后重新配置。
4. `recoverSessionId()` 空实现意味着未知投递后不自动恢复会话——保持现状（宁可让用户核对），是否需要补齐待定。
5. 语音 ASR 容错（同音字）当前靠精确匹配 + 提示用户确认；若需要模糊消歧，需为眼镜端设计无键盘候选交互，另立需求。
