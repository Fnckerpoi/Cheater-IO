# MapKit 导航与线框 HUD · 第一阶段

## 总体范围

普通 V2 新增独立的 **资料 → MapKit 导航 · 实验**。**高德导航 · 实验**保留原入口、Key、资源与功能；两套导航不能同时持有会话，不自动停止另一套。

这是源码移植与本地构建，不是现成安装包，不自动安装、不刷固件。只适用于本仓库非商业研究许可。第一阶段仅前台运行；不是高德完整导航 SDK 的等价替代，也不作为驾车安全决策依据。

## 1. 构建

在仓库根执行，沿用原来的合法宿主 App、副本与签名流程。


a. 仅手机 MapKit（不需要高德 Key）：

```bash
TIO_MAPKIT_ENABLED=1 bash official-addon/build.sh embedded com.rayneo.venus.pub
```

输出：`official-addon/build/mapkit/TurboIOPrivateAddon.dylib`。

b. 同时保留已链接的高德导航（需要原先固定 SDK/资源/自己的 Key）：

```bash
TIO_AMAP_ENABLED=1 TIO_MAPKIT_ENABLED=1 bash official-addon/build.sh embedded com.rayneo.venus.pub
```

输出：`official-addon/build/mapkit-amap/TurboIOPrivateAddon.dylib`。

c. 在你现有任意 IP HTTP 专用研究版中增加两套地图及可选 HUD：

```bash
TIO_LOCAL_HTTP_ENABLED=1 TIO_AMAP_ENABLED=1 TIO_MAPKIT_ENABLED=1 TIO_MAPKIT_HUD_ENABLED=1 \
bash official-addon/build.sh embedded com.duriea.turboio.research
```

输出：`official-addon/build/mapkit-amap-http-hud/TurboIOPrivateAddon.dylib`。HTTP 仍会明文传输 Key 与对话，ATS 放宽规则未改变；优先 HTTPS。

两项 MapKit 开关默认均为 0。仅开启地图时 HUD 明确不可用，不会发送 TDP1。HUD 必须显式开启，不能与 OTA、TNV1、音乐或本地翻译构建混用。TDP1 显示复用少量已存在的文件协议代码，不引入固件写入入口。

签名打包继续使用实际的 `package.mjs --app --addon --profile --identity --device --out` 参数或既有 HTTP unsigned 打包工具，不使用 fork README 的另一套参数。MapKit 构建会补足前台定位说明，不额外授予后台定位。

## 2. 手机使用

1. 进入新页面，先阅读位置发送告知；搜索条件／起终点会交给 Apple，启用道路图时当前位置附近范围交给公共 Overpass 服务。不得用于需要位置保密的场景。
2. 搜索或长按地图选择终点，并为模拟选择独立起点；选择步行／骑行／驾车，规划后再明确开始模拟或前台实时导航。
3. 骑行不可用／当地无路线时报告错误，不静默改成步行；导航停止、页面退出或离开前台后不自动恢复。
4. 看手机 HUD 预览：箭头、距离、指令、路线及可用周边道路。未知转向显示未知，不默认直行。道路名称或实时 ETA 未知时不编造；预计时间标明规划估算来源。
5. 仅用户明确选择的最近目的地保存到独立版本化记录；不自动迁入高德历史，不建设定位轨迹、道路缓存或 HUD 图片历史库。导航内容不交给模型。**开启眼镜 HUD 会在 App 私有目录生成包含导航像素的临时 TDP1 协议文件；为保护 SDK 文件所有权，回执未知的文件不会自动删除，可能跨退出／重启保留。** 这与手机内存预览不同，不应当作零落盘。

### 中国大陆坐标限制（重要）

第一阶段未验证 MapKit 路线、原始定位和 OSM 道路的大陆坐标对齐，不启用未确认的 OSM 叠加，也不将原始 GPS 与未确认坐标路线混合做实时导航。当前采用纬度 18–54、经度 73–135 的保守矩形排除范围，并非精确国界，因此也会限制部分邻近地区；不能据此宣称所有海外地点均支持。可使用同一 MapKit 路线内的模拟与手机地图预览；**日常大陆实时导航继续使用保留的高德页面**。这是显式降级，不应手动跳过检查。海外路径也需要目标地区实测。

## 3. 眼镜 HUD

- 线框地图走 **TDP1 像素显示**，不是 TNV1 原生 Nav，也不是截取 Apple Maps App。眼镜需要已具备兼容 TDP1 能力；原厂固件不能据此假定支持。
- 每次由本人确认眼镜已退出录音、语音、字幕、提词及其他显示任务后，再明确开启 HUD。插件不能权威读取全部官方录音／语音状态，不将未知状态自动推定为空闲。
- 先取得真实固件能力、文件终态与 AP 回执，才发送画面；「SDK 提交」「协议回执」「镜片实际可见」是不同层次。查询未响应或不兼容时停用，保留手机预览，不自动重试或刷机。
- 首帧完整画布需要多包传输。传输过慢、数据超过 15 秒、后台、断连、切设备或冲突均停止；不为了显示而提交过期的可操作导航。
- 保留最新帧，不建立无限蓝牙队列；不删除 SDK 未确认所有权的临时文件。额度耗尽或租约不明确时拒绝继续，不猜测成功。
- 关闭未确认时按页面提示用眼镜实体按钮退出旧画面；回前台／重连不自动恢复，需重新确认并手动开启。

## 来源与结构

- fork：<https://github.com/sattarde/rayneo-ios-english-edition>
- 固定提交：`6268990646ebd41188cb084bb8ef96d70e94b869`，基线父提交 `c8815c0`。
- 导航页面／路线处理参考 fork `focus-edition/NavigationUI.m` 与地图工作区；道路参考 `NavigationRoadNetwork.inc`；线框布局参考 `DisplayPhoneUI.m`。按独立模块重新整合中文、生命周期与输入校验，不整仓覆盖。
- TDP1 协议与回执复用 Turbo1123/Turbo-IO 已有 session/transport/carrier/client/runtime/delta 源码；原作者及 fork 作者权利保持不变，许可证见仓库 LICENSE。
- 新模块位于 `official-addon/mapkit-navigation/`；`NavigationSession` 控制两页互斥；`mapkit-build-config.sh` 管理独立构建选择。

## 验收

自动回归与构建结果另见同目录 MAPKIT_VALIDATION.md。手机实际联网算路、定位、镜片可见与真实操作仍须人工验收，模拟器与测试回执不代替这些结论。

验收顺序：两套入口与模型回归 → 手机模拟与有效定位 → 兼容眼镜能力查询／首帧／持续更新 → 断网、弱定位、后台、断连、切设备、退出。先步行，不在驾车时调试。

**总结：高德保留作为对照与回退，MapKit 与线框 HUD 独立启用。只有目标地区和设备通过实测后，再讨论移除高德及后台能力扩展。**
