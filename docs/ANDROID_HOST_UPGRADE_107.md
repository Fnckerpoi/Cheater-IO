# Android 雷鸟宿主 1.0.7 适配与持续升级方案

状态：研究计划，尚未取得或适配官方 Android 1.0.7 APK。

## 当前兼容性基线

- Android 宿主 profile 以 APK 完整 SHA-256 为键；目前记录 1.0.4（195）和 1.0.5（201）。
- 当前打包器只允许 1.0.5 OTA profile。未知摘要会在改动宿主前拒绝；不能将它理解成“支持所有 1.0.5+”。
- 本机正式宿主包 com.rayneo.venus.pub 为 1.0.5（201）；当前展示 SCADA 页面的前台包是独立应用 com.turboio.tmig1.flash，同样报告 1.0.5（201）。版本字符串相同不代表 APK、签名、代码或数据目录相同。
- 当前 1.0.7 profile、ABI、Manifest fixture、构建产物和真机回归均无仓库证据；因此不声称支持 1.0.7，也不应绕开哈希门禁。
- 手机宿主版本与眼镜 Strix OS 版本是两个独立兼容维度。手机 1.0.7 不意味着能刷任一眼镜固件。

## 1.0.7 适配的输入门槛

开始新增 profile 前，先归档并记录：

1. 从有记录的官方渠道取得完整、未修改的 Android 1.0.7 APK。若官方采用 split APK，保存完整 split 集并指出 base APK。
2. 记录获取时间、来源 URL、文件大小、SHA-256、包名、versionName/versionCode、签名证书摘要及验证结果。不得把第三方重签包冒充官方输入。
3. 用固定版本的 apktool 在全新隔离目录解码；记录 apktool 版本、框架输入、解码目录内容摘要及原 APK 摘要，明确绑定二者来源。
4. 保存 APK 成员清单及 DEX、二进制 Manifest、原生库和资产摘要。不得复用来历不明的旧解码目录；打包器每次应验证解码目录确实对应 profile 中的源 APK。
5. 仅记录真实观察到的输入。若当前拿到的是 split、增量包或无法验证来源的 APK，则停止 profile 创建。

## 宿主绑定机制

现有工具链先计算 APK SHA-256，再选择精确 profile；随后按 DEX descriptor 查找宿主类，并要求唯一匹配指定签名、字段和枚举。打包器在 smali 中保存原方法，再加入 wrapper；之后合并 DEX、补丁 Manifest/资源、审计 ZIP 成员并移除旧签名，最后由发布者重新签名。

这些静态声明只证明列出的 ABI 存在，不证明方法在运行时的语义、线程、调用顺序、异常或生命周期仍然相同。1.0.7 必须重新查看每个实际 hook 的方法体和调用点，不能仅对比类名或方法签名。1.0.5 的混淆符号、Dart OTA 偏移和散列不可移植到 1.0.7。

建议把代码边界固定为：

- **协议与业务核心**：SCADA 帧、AES-GCM、重放、RSSI 近距与过期策略保持宿主无关。
- **Android 系统适配**：BLE、权限、前台服务与后台扫描按 Android/API/OEM 验证。
- **雷鸟宿主适配**：Activity、语音回调、事件桥、NativeTransfer、卡片传输和服务菜单由 exact APK profile 选择。
- **眼镜固件 profile**：目标固件字节、型号、源/目标系统版本及 OTA 规则独立选取。手机 profile 不得隐式选择或授权眼镜固件。

## 新宿主 profile 必须核对的 ABI

在 1.0.7 的干净解码产物中，为每一个实际反射或包装依赖逐项记录 descriptor、签名、字段、枚举、方法体与调用点：

- NLP/ASR listener 与完成回调；
- MainActivity 生命周期入口；
- SDK 事件桥、Map/消息字段及线程语义；
- NativeTransfer 的单例、消息构造器、发送/接收和会话 owner；
- Dashboard 配置读写、卡片 upsert/readback 行为；
- 高德接口、服务枚举及打包时引用的资产；
- 每个启用 OTA 功能需要的官方队列、传输和回执调用链。

任何必需 descriptor 多重匹配、缺失、签名不符、方法语义无法确认或原方法无法保留时，都应硬失败并维持该版本 unsupported。

## 每版发布的持续验证门禁

### A. 静态和拒绝测试

- 精确已知 APK 摘要选择唯一 profile；未知摘要、错误版本号、源 APK 与解码目录摘要不符必须拒绝，拒绝发生在任何宿主写入之前。
- 验证每个 DEX descriptor 唯一、方法/字段/枚举签名正确、包装入口和原方法体关系完整。
- 对 Manifest 做语义对比：权限、maxSdkVersion、usesPermissionFlags、service exported 与 foregroundServiceType 均保留/合并正确；对补丁做幂等检查。
- 对比打包前后 ZIP 成员。只允许明确列出的 DEX、Manifest 和资产变化；检查 multidex 冲突、未预期 native library/资产变化、签名记录清除与重签状态。
- 若 1.0.7 OTA route 未独立适配与审计，生成物必须明确标记 OTA disabled，不能借用 1.0.5 的 libapp.so 定位、偏移、CID 或摘要。

### B. 离线构建和签名

在干净工作区，以归档输入重建；记录工具链版本、构建日志、产物 SHA-256、签名证书及 APK 审计报告。升级签名策略前确认 Android 会如何处理包名相同但证书不同的应用、覆盖安装和本地沙盒数据；不要以卸载用户应用作为默认安装步骤。

### C. 真机回归

对独立测试机安装新构建，保存机型、系统/API/OEM、RayNeo 宿主版本、眼镜型号与固件版本。逐项验证：登录和原连接流程、语音事件保留、卡片读/写/回读、并发消息与断连行为、SCADA 权限提示、BLE 低延迟扫描、扫描停止/服务释放、屏幕关闭和重启后的行为、其他官方日常功能。

每个测试证据应含预期、实际、日志/截图、构建摘要。扫描收到合法样本不等于卡片已显示；卡片配置回读也不等于镜片光学渲染成功，两者分开验收。

### D. OTA 单独门禁

普通 App/卡片/SCADA 通过不授权 OTA。OTA 必须有精确 firmware profile、官方协议与目标设备证据、来源与完整包摘要、全成员校验、只读版本/电量/空闲预检、单次授权、正式安装回执、自然重启后的读取和结果收尾。新宿主 Java profile 通过不能作为复用旧 OTA hook 的理由。任何版本降级都须单独证明设备策略和分区/负载兼容；禁止据“没有观察到硬件版本锁”推断任意固件可互刷。

## 支持矩阵规则

每个矩阵行必须同时列出：手机宿主包名与 APK SHA、手机机型/Android API/OEM、眼镜型号、Strix OS 精确版本/固件摘要、功能范围和真实验收证据。缺项记为“未验证”，不得用邻近版本、相同 versionName、另一种手机平台或仅源码构建结果代替。

建议状态：

| 状态 | 条件 |
|---|---|
| Unsupported | 未知 APK hash，或所需接口/运行语义未核实 |
| Static profile | exact APK、ABI、Manifest 与离线构建已核实，尚无设备回归 |
| Device validated | 指定手机/OEM/API、宿主摘要和眼镜固件组合完成所列普通功能测试 |
| OTA validated | 另有该精确组合的受控 OTA 与完整结果收尾证据 |

## 当前 1.0.7 结论与下一步

当前结论：**Android 1.0.7 不受本仓库现有打包器支持；代码没有证明它兼容或不兼容。** 下一步应取得官方 1.0.7 输入 APK 并完成上述来源/摘要登记，再在隔离目录做静态 ABI 与 Manifest 差异表。只有差异表完成后，才决定是否可增加 profile、哪些 features 可以启用，以及 OTA 是否必须继续关闭。

当前 SCADA Sensor 页面位于单独包 com.turboio.tmig1.flash；正式雷鸟宿主为 com.rayneo.venus.pub。若目标是把传感器入口和卡片桥接集成进正式雷鸟宿主，需要完成上述宿主 ABI 适配；不能仅凭独立 SCADA 页面运行就声称正式 App 的 1.0.7 集成完成。
