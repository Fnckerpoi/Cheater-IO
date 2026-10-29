# AA 内嵌工作台集成（宿主侧）

同一 APK 内嵌 Agents-Anywhere 原生界面。宿主保留 DSH 身份、授权与选中会话的唯一权威；
AA 载荷只读浏览目录与历史，发送与查询必须经宿主桥，以保留派发预算、提交保护与本轮消息归属。

## 产物与载荷契约

| 项 | 值 |
|---|---|
| 载荷 APK | `assets/turboio/aa/aa-embedded-payload.apk` |
| 载荷清单 | `assets/turboio/aa/payload.json` |
| 清单字段 | 精确四项：`schemaVersion:1`、`abiVersion:1`、`entry`、`sha256` |
| entry | `com.agentsanywhere.embedded.PayloadEntry` |

宿主在提取前校验清单字段与 `sha256`，提取后重新计算哈希；载荷以只读文件加载。
清单缺失时宿主不显示“AA 工作台”入口。

## 宿主入口

- Activity：`com.turboio.addon.AaEmbeddedActivity`（二进制 Manifest 内新增，`exported=false`）
- 加载器：`AaPayloadLoader`，使用 `getPackageArchiveInfo` + `getResourcesForApplication` 公开 API
- 类加载：`DexClassLoader`，父加载器为平台 `Context.class.getClassLoader()`，不与宿主混淆类混用
- 载荷 Context：覆盖 `getResources`、`getAssets`、`getTheme`、`getClassLoader`、`getApplicationContext`、
  `getApplicationInfo`、`getPackageName`、`getPackageCodePath`、`getPackageResourcePath`

## 反射 ABI（v1）

```
create(Activity, Context, Bundle, Object) -> View
lifecycle(View, String event, Bundle) -> void     // start/resume/pause/stop/save/destroy
dispose(View) -> void
```

## 桥操作（`call(String, Bundle) -> Bundle`）

| 操作 | 说明 |
|---|---|
| `auth.snapshot` | 登录状态、`userId`、`origin`、`authGeneration`、`revision`、令牌（仅内存） |
| `selection.snapshot` | 目标与选中会话快照，同一 `revision` 指纹语义 |
| `selection.commit` | 联网核验目录归属后按 `expectedRevision`/`expectedAuthGeneration` 原子保存 |
| `auth.login` / `auth.logout` / `selection.open` | 打开宿主配置界面，由用户明确操作 |
| `dsh.dispatch` / `dsh.status` / `dsh.end_session` | 经宿主 `ToolClient` 执行 |

结果统一为 `ok`、`errorCode`、`status`、`json`、`message`；派发类操作额外回传
`requestRevision`、`requestAuthGeneration` 与当前 `revision`、`authGeneration`，供 UI 丢弃迟到结果。

错误码：`AUTH_REQUIRED`、`STALE_AUTH`、`STALE_SELECTION`、`DELIVERY_UNKNOWN`、
`INVALID_TARGET`、`UNSUPPORTED_OPERATION`、`WORKER_REQUIRED`、`REQUEST_FAILED`、`HOST_CLOSED`。

## 唯一完整构建命令

```powershell
.\android-addon\build-embedded-windows.ps1 `
  -PayloadApk <aa-embedded-payload.apk> `
  -PayloadSidecar <payload.json> `
  -OutputDirectory <新的输出目录>
```

该脚本依次执行：宿主 Java 编译（Java 8 目标，android-36）→ D8（min-api 29）→
`package-embedded.py` 装配 → `zipalign -P 16 -f 4` → `zipalign -c` 校验。

装配只改 `classes4.dex` 与 `AndroidManifest.xml`，新增两个 asset 成员；其余 ZIP 成员逐一比对不变。
签名与安装是独立步骤，不在上述脚本内。

既有 `build-tmig1-windows.ps1` / `package-tmig1-windows.mjs` 的 PUBLIC 单点更新审计未放宽，
两套入口互不影响。

## 未验证项

- 载荷资源 Context、Compose 生命周期与宿主 Activity 的真机/模拟器运行未验证；本机无安装授权。
- 宿主与载荷的真实 DSH 往返、账号切换与迟到回调丢弃仅经静态与纯 JVM 门禁验证。

## 会话关联修订

所有 dsh 操作必须传 `expectedRevision`、`expectedAuthGeneration`、`expectedSessionId`；详情页操作只允许浏览会话等于宿主绑定会话。宿主在配置捕获、pending 保存前及 status 会话快照读取时于同一个 DSH 锁中检查；网络始终锁外。generation 0 是有效的进程初始世代。

明确新任务入口可传 `newSession:true` 和空 `expectedSessionId`，仅当宿主存储会话同为空且完整目标已验证时创建；已有绑定不允许隐式替换。status/end 空会话拒绝。

返回 `requestRevision`、`requestAuthGeneration`、`requestSessionId` 对应请求，`revision`、`authGeneration`、`sessionId` 对应执行后状态。合法派发/结束/绑定会改变修订号，UI 不得将请求修订与执行后修订直接要求相等；应重新读取身份快照并与返回的执行后状态匹配。`accepted` 仅 submitted（绑定提交成功另为 true），`completed` 仅 completed，`unknownDelivery` 仅 unknown_delivery；未知送达不算成功。

selection.commit 完整字段：`connectorId/projectId/runtimeType/runtimeId/model/permission/sessionId/expectedRevision/expectedAuthGeneration/expectedSessionId`。model 和 permission 必须是目录返回的精确 selectionId。首次明确绑定可使用空 expectedSessionId，但必须与宿主原存储一致。

宿主设置 Dialog 关闭后向载荷发送 `lifecycle(root,"refresh",Bundle)`；载荷只递增刷新信号，不伪造 resume。
