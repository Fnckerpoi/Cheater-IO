# MapKit 第一阶段移植验收记录

日期：2026-10-10。固定来源提交：`6268990646ebd41188cb084bb8ef96d70e94b869`。
当前本地基线 `d0fc5e1` 已包含此前 HTTP IP 模型和高德打包修改；此次不改 Core.m、WebSearch.m、HomeTabBridge.m 或 EndpointPolicy.h，不自动提交、安装或刷机。

## 已验证

| 检查 | 结果 / 范围 |
|---|---|
| 原有 `official-addon/test.sh` 基线 | PASS；需本机 Node22 路径 |
| 完整旧功能与新增模块回归 | PASS；原模型/HTTP/高德测试保留 |
| 导航核心 | PASS；中英动作、道路名误判回归、未知转向、路线投影/回环、局部窗口、偏航时间门限；ASan/UBSan PASS |
| 道路核心 | PASS；模式过滤、JSON/数字/坐标校验、流式响应512KiB上限、HTTP失败、缓存/限流/重试/取消代次；ASan/UBSan PASS |
| HUD 策略 | **38 checks PASS**；仅navigating、弱/旧/重算/mode变化失效、布尔数字拒绝、1Hz/最新帧/前台/所有权/禁用adapter |
| HUD 启用态集成 | **1118 checks PASS**；复用实际session/transport/client/carrier/runtime，模拟Flutter SDK，272个真实有界临时文件提交；不是眼镜实机 |
| 打包与资源 | **17 Node tests PASS**；原打包门禁/高德资源/MapKit构建开关/前台定位声明/ATS保留 |
| 原普通版与高德版设备编译 | PASS |
| 6种MapKit设备构建 | 全部PASS，arm64 / iOS16最低版本，Xcode SDK26.2 |
| MapKit+HUD完整模拟器预览编译 | PASS；含既有代码警告，不等于运行验收 |
| 新导航/道路/HUD严格语法及对象编译 | PASS |
| UIKit线框测试可执行文件 | 编译PASS；运行未完成，见下 |
| `git diff --check` | PASS |

六种设备构建：MapKit-only、MapKit+HUD、MapKit+高德、MapKit+高德+HTTP、MapKit+高德+HTTP+HUD、MapKit+HTTP。构建产物位于 `official-addon/build/mapkit*/TurboIOPrivateAddon.dylib`，构建脚本执行本地ad-hoc代码签名；不是签名IPA，也没有安装到手机。

## 已发现并修复

- TDP路由returnNO仍调用fallback导致重复转发：统一NO不回调、YES恰好回调一次。
- 未被HUD拥有的TDP消息漏过原协议观察：归入共同观察流程，设备切换不会静默遗漏。
- 英文道路名Left/Right被误判为转向：动作限定模式＋几何判断，未知不默认直行。
- 弱定位/过期/重算仍可持有旧指引：UI和HUD策略均失效停止，不自动恢复。
- 长路线第一批点不覆盖当前位置：输出当前位置前100m后450m、最多128点局部窗口。
- SDK文件回执与关闭期限旧代次干扰新会话：代次控制并集成回归。
- 图像含导航位置内容的临时协议文件需保留：文档与HUD启动确认明确告知，未知文件租约不删除。

## 未完成的真实验收

1. **Apple在线搜索/规划、目标地区定位与骑行覆盖**：未执行真实联网路线验收。
2. **OSM实时请求与目标地区坐标对齐**：未执行真实Overpass/实地验收。保守坐标矩形内只模拟、不做OSM叠加或实时指引。
3. **兼容眼镜TDP1能力、传输耗时、镜片可见、实体退出**：未接实机；AP回执不是镜片可见证据。
4. **模拟器UIKit运行/截图**：已有iPhoneAir和独立新建TurboIO-MapKit-Validation模拟器均出现Data Migration Failed；spawn报LaunchdSimError111/Invalid or missing Program/ProgramArguments。不擦除用户模拟器、不据此宣称绘图运行通过。
5. **设备后台/锁屏异常、定位撤权、跨任务冲突**：逻辑测试通过，仍需人工设备验收。第一阶段前台限定。

## 可复测命令

```bash
# 将node加入当前shell PATH，不改全局配置（本机已有路径）
PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" bash official-addon/test.sh
PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" bash official-addon/mapkit-test.sh

TIO_LOCAL_HTTP_ENABLED=1 TIO_AMAP_ENABLED=1 TIO_MAPKIT_ENABLED=1 TIO_MAPKIT_HUD_ENABLED=1 \
bash official-addon/build.sh embedded com.duriea.turboio.research

TIO_MAPKIT_ENABLED=1 TIO_MAPKIT_HUD_ENABLED=1 bash official-addon/preview/build.sh
```

模拟器绘图专用测试：`MapKitWireframeTests.m`，链接`MapKitRoadsCore.m`和`MapKitWireframe.m`以及Foundation/UIKit/CoreGraphics；模拟器恢复可运行后用simctl spawn验证。可设`SIMCTL_CHILD_TIO_MAPKIT_PREVIEW_PATH`保存合成预览，不包含用户位置。

**验收结论：第一阶段源码与构建交付通过；手机联网与镜片可用性待实测。高德保留、不建议现在删除。**
