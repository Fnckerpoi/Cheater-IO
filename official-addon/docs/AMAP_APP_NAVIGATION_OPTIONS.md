# iOS 高德官方 App → 眼镜导航：公开接口与可行路径

## 结论先行

**在“不使用高德轻量出行／Link 等专用合作 SDK，只唤起 iOS 高德官方 App”的限定下，本次未发现可供普通第三方应用连续订阅该 App 转向、路口距离、重算路线等数据的公开 API 证据。** 官方 `iosamap://navi` 和 `iosamap://path` 文档描述的是导航／路线页面唤起，不是实时导航事件订阅。[S1]

这不是“高德 App 绝对没有回传接口”：**高德官网链接的 AIoT 智能眼镜 SDK 文档和一手示例仓库，确实公开描述了 iOS Link 授权、App IPC 链路、来自高德 APP 的导航数据监听与结构化数据回传。** 但这些需要专用 SDK 产物和授权，官网要求联系高德获取依赖包；不能当成无需 SDK、无需合作的普通 URI 能力。[S3]

对 Turbo-IO，实用优先级是：

1. **需要近期可控的连续眼镜提示：沿用传统 AMapNaviKit，在自己的导航会话中取实时数据，再经已授权的眼镜传输／显示通道发送。** 不需要先接入轻量出行专用 SDK，但这不是同步高德官方 App 的导航。[S2]
2. **必须以高德官方 App 的同一条导航为数据源：走高德／眼镜厂商的 Link 或相关合作接入。** 先确认资格、授权、SDK 包、支持版本和后台行为，不把 URI 误当成回调接口。[S3]
3. **仅需要打开成熟导航：使用官方 URI 作为降级入口。** 不承诺打开后眼镜仍能同步转向／距离。[S1][S4]
4. **屏幕广播只列为有明确同意、非行车的有限实验**，不是结构化导航 API，也不是推荐的产品主链路。[S6]

## 1. 研究范围与仓库事实

- 有界独立研究，只读取公开一手资料和仓库文本；未安装 SDK／环境，未登录、未运行真机实验，未修改实现或提交 Git。
- 写入前通过 glob 确认目标文档不存在，`why_check` 返回 no history，因而无历史轮次需要 `why_file`。
- [setup-amap.mjs](<../setup-amap.mjs#L9-L27>) 固定的是 **AMapNaviKit 11.3.100、AMapFoundationKit 1.9.4、AMapSearchKit 9.8.1**。
- [NavigationUI.m](<../NavigationUI.m#L17-L35>) 导入传统 AMapNaviKit，声明 Drive／Ride／Walk 的 DataRepresentable 协议。**这些证据不支持“项目已经使用轻量出行／智能眼镜专用 SDK”这一说法。**
- 源码链接采用相对此文档目录的路径。既有用户教程中“高德手机导航”等简称不能据此推导为“高德官方 App 回传”。

## 2. 官方 URI：能启动，未发现连续导航回调

### 导航唤起

官方示例（展示参数，不是可直接照抄的生产目的地）：[S1]

```text
iosamap://navi?sourceApplication=applicationName&poiname=方恒时尚中心&poiid=B0FFI206UL&lat=39.968286&lon=116.340805&dev=1&style=2
```

- 从用户当前位置到传入终点开始导航；`sourceApplication`、`lat`、`lon`、`dev` 为文档所列必填参数。
- `dev=0` 表示已进行国测加密、不再偏移；`dev=1` 表示需要国测加密。须明确坐标来源，不重复偏移，也不把任意坐标都标成 `dev=0`。
- 文档仍列 `style`，同时明确 iOS 高德 7.7.4 起不支持该参数，以用户本地偏好为准。不要承诺 URL 可强制覆盖策略。

### 路线规划唤起

```text
iosamap://path?sourceApplication=TurboIO&dlat=39.98848272&dlon=116.47560823&dname=目的地&dev=0&t=2
```

这是用于解释字段的简化示例；中文／特殊字符应通过系统 URL 构造工具编码，真实坐标需核实。`t=0/1/2/3` 对应驾车／公交／步行／骑行；省略起点坐标和名称时使用“我的位置”。路线页也提示部分偏好参数已不再支持，应实测目标 App 版本。[S1]

**接口边界：**实际读取的两个页面参数表中未发现导航信息订阅地址、事件流、转向／距离回调协议。`sourceApplication` 是来源应用名称，不是实时回调 URL。Apple `UIApplication.open` 的 completion 仅返回“URL 是否成功打开”的布尔值；它不代表算路成功，更不包含后续导航数据。自定义 URL scheme 只有在双方明确实现协议时才能交换约定数据，不能因为注册了自己的 scheme 就自动取得另一 App 的导航状态。[S1][S4]

## 3. 传统 AMapNaviKit 与眼镜／轻量出行合作链路

| 路径 | 导航运行在哪里 | 已读一手证据 | 不应混淆的地方 |
| --- | --- | --- | --- |
| 普通 URI | 高德官方 App | `iosamap://navi`／`path` 参数与唤起效果 | 未发现普通 URI 连续回调证据 |
| 传统 iOS 导航 SDK | 集成 SDK 的应用内 | DataRepresentable、`updateNaviInfo`、`updateTurnIconImage`；骑行／步行对应协议 | 这是自己会话的导航，不是高德 App 会话监听 |
| AIoT／Agent／Link 合作接入 | 可选 App 链路或 SDK 链路 | Link 授权／连接、App 来源的导航监听、回传消息 | 专用 SDK、授权与依赖包不能省略；不能等同于当前 AMapNaviKit |
| RTOS 地图／眼镜侧 SDK | 眼镜等设备侧 | 官网介绍地图渲染、轨迹导航、转向／距离回调 | 设备渲染能力不证明普通手机 App 可裸读取高德官方 App |

### 传统 SDK 已公开支持所需语义数据

高德《导航实时数据获取》明确称其是 HUD 和智能硬件解决方案：驾车经 `AMapNaviDriveDataRepresentable` 实时透出数据，骑行／步行使用对应协议；通过 `addDataRepresentative:` 注册，再接收 `updateNaviInfo:` 与 `updateTurnIconImage:turnIconType:` 等回调。[S2]

因此可以设计“本应用算路／导航 → 转向与距离标准化 → 眼镜显示”。是否成功运行还取决于 Key／Bundle ID、隐私同意、定位权限、网络、后台模式、SDK 与眼镜通道的真机验收。文档有能力不等于当前构建已通过在线鉴权和长期后台测试。

### 官方 App 合作回传有公开文档证据，但不满足无专用 SDK 条件

官网《AIoT智能眼镜SDK SKILL》介绍 Android／iOS LLM Agent SDK 和 LinkClient 与高德 APP 通信；iOS 示例明确涉及授权、建联、发送导航命令。官网说明 **依赖对应 SDK 产物，需联系高德相关同学获取**。其链接的一手仓库进一步给出：[S3]

- `AMapLinkManager` 建立 App IPC 链路，`AMapAuthorizationManager` 完成授权；仅 App 链路模式需要这些。
- `addNaviDataListener` 接收导航更新，来源枚举 `AMap=2` 指高德 APP；`UpdateNaviInfo` 为行中导航信息更新；另有 App 链路类型化监听。
- `AMapLinkClientObserverManager` 接收 App 回传消息，命令 `cmd:5` 用于获取导航结构化数据。

**证据止于公开文档描述。** 本次没有取得 SDK 包／合作权限，没有验证每个转向／路口距离字段、频率、模式或任意第三方都能接入，也没有证明可完全不接入专用 SDK 来复现同样链路。`cmd:5` 不是普通 `iosamap` URI 参数，更不应拿示例 JSON 去猜测或探测未授权接口。

“轻量出行”“轻量版地图 SDK”“RTOS 地图 SDK”“Agent／Link SDK”不应仅凭名字相似互换：本次检索未建立它们所有产品名的一一对应关系。官网智能眼镜解决方案确有咨询入口，但静态正文不足以证明所有合作细节；以官方 AIoT 文档和联系人确认的具体包名、头文件、版本、授权范围为准。[S3]

## 4. iOS 边界：sandbox、通知、屏幕广播

### Sandbox 与 URL scheme

Apple 明确规定第三方 App 受 sandbox 隔离，访问自身之外信息只能使用系统明确提供的服务。不能将“已打开高德”理解为获得其进程、存储、内部导航状态或后台运行权。系统 `open` 会将处理 URL 的另一 App 带到前台，其成功回调不是数据订阅。[S4]

### 第三方通知

`UNUserNotificationCenter` 官方定义是管理 **your app or app extension** 的通知；`current()` 与 delivered／pending 查询都限定本应用。它不是类似 Android 全局通知监听服务，不能据此订阅高德 App 的通知来持续解析路线。[S5]

还要区分硬件系统级通知转发与普通 App 的通知访问权限：即便某眼镜已有系统通知显示能力，也不能在未核实硬件协议、授权和高德实际通知内容前承诺转向／距离连续可用。本次未对硬件通知服务做专项验收，不把它列为已证实导航接口。

### ReplayKit：有限的像素实验，不是导航数据接口

- ReplayKit 文档描述屏幕视频与 App／麦克风音频；`RPScreenRecorder` 的定位是录制本应用。不能把本应用录屏 API 描述为自动抓取其他 App 的接口。
- 系统广播选择器让用户选择广播 provider；`RPBroadcastSampleHandler` 的输入是视频／音频 `CMSampleBuffer`，不是高德导航语义对象。
- 若研究跨 App 系统屏幕广播，需要用户显式启动、Broadcast Upload Extension 和目标设备／iOS 版本实测；本次资料不证明高德每个导航画面或锁屏期间都能稳定捕获。[S6]

允许的有限实验是：静止环境中，由用户主动开启广播，观察切到高德后是否能取得画面；必要时仅本地裁剪／OCR。风险包括系统终止、锁屏／画面遮挡、保护内容、App UI 改版、方向／距离识别错误、延迟、耗电和无关通知／位置隐私泄露。必须设置识别置信度、过期清空、停止按钮，不后台偷录，不默认上传完整屏幕，不用于行车决策。**本次不建议实现，只列可验证假设。**

## 5. 方案选择与实际建议

| 方案 | 连续转向／距离 | 成本与风险 | 推荐用途 |
| --- | --- | --- | --- |
| URI 唤起官方 App | 本次未发现公开回调证据 | 低成本；前台切换、安装／版本、坐标和策略参数 | 快速打开导航／失败降级 |
| 现有传统 SDK 自行导航 | 官方公开回调支持；工程需真机验收 | Key、隐私、定位／后台、重算／断流、设备传输与显示 | **近期连续眼镜提示首选** |
| 高德／眼镜厂商合作 Link 链路 | 官方文档已有 App 来源监听与回传证据 | 需专用依赖包／授权；商业资格、版本、频率、后台未知 | **官方 App 同会话同步的正式方向** |
| ReplayKit 屏幕广播 + 本地解析 | 仅像素推断，稳定性未证实 | 操作复杂、隐私／耗电／识别误差、扩展生命周期 | 非驾驶、有边界的技术实验 |

建议把产品按钮明确区分为“本应用导航并推送眼镜”和“打开高德 App”；选择后者时不要继续显示旧 SDK 会话提示冒充高德 App 的实时数据。若用户坚持官方 App 是唯一导航源，下一步不是增加 URI 参数，而是向高德／厂商核对以下问题：

1. iOS 合作产品的准确名称是否为轻量出行、Agent／Link 或其他包；普通开发者／个人研究是否可授权。
2. 能否监听用户在官方 App 手动开启的任意导航，还是必须由授权链路启动。
3. 步行／骑行／驾车各自的转向、路口距离、下一道路、路线 ID、重算、到达／结束字段及更新频率。
4. 最低高德 App／iOS 版本、锁屏／切后台断线与重连规则、网络和定位要求。
5. 数据向第三方眼镜转发的许可、隐私与安全展示要求。

不建议私有 API、越狱、注入高德进程、读取其容器或绕过授权来替代正式接口。现有 SDK 路线与任何实验显示仍应采取失联／过期清空和安全提示，不能视为安全认证导航设备。

## 6. 一手来源与读取记录

以下均实际读取 URL 正文；搜索结果只作入口，不作结论证据。约六组主要来源（同一产品页的关联规范合并列出）：

- **[S1] 高德 iOS URI 导航／路线：** [导航](https://lbs.amap.com/api/amap-mobile/guide/ios/navi)；[路线规划](https://lbs.amap.com/api/amap-mobile/guide/ios/route)。页面标注更新分别为 2024-08-08、2025-10-15。读取参数表、坐标处理与版本限制；未发现普通 URI 持续导航回调协议。
- **[S2] 传统 iOS 导航 SDK：** [导航实时数据获取](https://lbs.amap.com/api/ios-navi-sdk/guide/navigation-map/navi-info)（2026-03-06）；补读[概述](https://lbs.amap.com/api/ios-navi-sdk/summary)确认产品范围与隐私初始化要求。
- **[S3] 高德 AIoT／眼镜合作：** [AIoT智能眼镜SDK SKILL](https://lbs.amap.com/api/skill/developer-skills/aiot-skills)（2026-03-10）；官网链接仓库的 [iOS IPC／授权](https://raw.githubusercontent.com/amap-demo/amap-sdk-skills/master/ios-llm-agent-sdk/api/link-client.md)、[导航数据监听](https://raw.githubusercontent.com/amap-demo/amap-sdk-skills/master/ios-llm-agent-sdk/api/navi-data-listener.md)、[数据传输](https://raw.githubusercontent.com/amap-demo/amap-sdk-skills/master/ios-llm-agent-sdk/api/data-transfer.md)。补读[智能眼镜解决方案](https://lbs.amap.com/solution/smartglasses) HTML，确认官方咨询入口；动态产品正文未充分提取，不以搜索摘要代替。
- **[S4] Apple 应用隔离／URL：** [Security of runtime process](https://support.apple.com/guide/security/security-of-runtime-process-sec15bfe098e/web)；[UIApplication.open](https://developer.apple.com/documentation/uikit/uiapplication/open(_:options:completionhandler:))。
- **[S5] Apple 通知范围：** [UNUserNotificationCenter](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter)。
- **[S6] Apple ReplayKit：** [框架概述](https://developer.apple.com/documentation/replaykit)、[系统广播选择器](https://developer.apple.com/documentation/replaykit/rpsystembroadcastpickerview)、[广播采样处理器](https://developer.apple.com/documentation/replaykit/rpbroadcastsamplehandler)。当前网页对部分类型显示未来版本弃用标记；任何实验须以实际目标 SDK／OS 的 availability 为准，本次不推导已有设备已不可用。

限制：未执行合作鉴权、实际 App 间 IPC、通知转发或 ReplayKit 实验。结论只覆盖已读公开文档；“未发现公开证据”不能外推为“绝对不存在”。
