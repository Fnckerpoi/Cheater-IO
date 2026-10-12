# R6 导航地图自动居中到“我的位置”（用户已验收）

**新增自动规划及500ms提交目标见 [R7说明](NAVIGATION_R7.md)。下文保留R6基线，不能当R7性能验收结论。**

用户已确认R5四项真机DoD通过。R6仅调整地图入口和一次性定位，不改变已验收的六入口、重复导航、后台/锁屏持续与结束清理链路，不刷固件。

## 新行为

- 导航详情页删除“开启地图”按钮，保留“我的位置”和“全览”。地图仍在有自己的iOS Key且已同意SDK隐私说明后自动加载。
- 地图加载后，空闲页面自动尝试一次定位并居中。已有定位授权时直接获取；尚未决定时只请求“使用期间”权限，同意后自动继续，无需再次点击。
- 定位必须具有有效坐标、有限且不超过200米的精度、15秒内时间戳。地图接受高德地图回调坐标，不用系统原始坐标冒充高德坐标。
- 定位成功后，非导航状态关闭这次地图位置更新；最多等待15秒，超时提示去开阔处或点“我的位置”重试。拒绝授权时地图仍可浏览、搜索、选点，不虚构位置；不自动申请“始终”定位。
- 自动定位不是规划或开始导航，不启动眼镜字幕/后台路线。已在导航或规划中的页面重入不自动居中、不清掉路线或终点。
- 手动“我的位置”只居中地图，不因查看位置让已规划路线失效。离页/退后台取消尚未开始导航的一次性定位；旧超时/迟到位置或权限撤销后的回调不改变地图。

## 更新输入

- [R6未签名IPA](/Users/duriea/Downloads/TurboIO-http-navigation-r6-20261012/TurboIO-HTTP-Navigation-unsigned.ipa)，已合并扩展与高德资源，使用者自己签名。
- [隔离扩展](../build/navigation-r6-final/TurboIOPrivateAddon.dylib)，已包含在IPA，不额外注入。
- [打包报告](/Users/duriea/Downloads/TurboIO-http-navigation-r6-20261012/local-http-preparation.json)。

Sideloadly同账号、同 `com.duriea.turboio.research`，关闭额外Inject dylibs/frameworks并清空旧条目，覆盖安装、不先卸载研究App/解绑眼镜。自动续签缓存更新。保留既有模型和高德Key，若仍保存不用重建。

最终IPA SHA-256：`c4a3dd0bf0342c96d25cebd25a8541c862578e0fd304d3c7ee2fa3df503e2e78`；扩展SHA-256：`1e4bc1685f7d7b4cb659a546403fe07e4103941634b23e50be5b0f5fb6849a5d`。最终包精确扩展/资源/单注入/六入口+后台RouteStart+HTTP标记及location/蓝牙声明/无私人启动配置和AppleDouble已二次核验。

## 真机验收

1. 显示 `nav-subtitle-v6-auto-location`；更多诊断标题“导航状态 · v8 自动地图/我的位置”。
2. 进导航页没有“开启地图”按钮；地图自动加载后自动居中到手机位置，不用点“我的位置”。
3. 首次定位权限未决定时，允许“使用期间”后自动继续；拒绝时明确提示且不导航。无需为了测试撤销既有权限。
4. 信号差或超时不假装成功，保留“我的位置”手动重试；定位完成不自动规划/开始导航或显示眼镜。
5. 重入已在运行的路线不重置终点/路线，后台/锁屏与结束清理仍正常；同进程第二次开始仍可自动显示。

权限、真实GPS速度和镜片回归需要用户设备确认，本机替身测试不替代真机定位。勿边驾驶、骑车或过马路边测试。

## 自验

[针对性回归](../build/navigation-r6-targeted.log)：生产map-location include测试授权续行/拒绝/精度/时效/坐标/取消/旧超时/许可撤销/运行和已规划路线不变；原导航后台actual include32组策略加取消/续行接线通过；R4/R5字幕/同进程重启/自动地图隐私/路线持有/后台原生发送用例通过。

[SDK构建](../build/navigation-r6-final-build.log)、[UIKit预览](../build/navigation-r6-preview.log)编译成功；实际UIKit六入口测试新增断言导航页无开启地图按钮且只有一个我的位置按钮，PASS报告已读取；离线预览不连接高德或真机GPS。

[打包策略测试](../build/navigation-r6-package-tests.log)15/15通过。全量 [回归日志](../build/navigation-r6-regressions.log)相关高德/HTTP/字幕/定位/底栏通过，整体exit1仍为独立MapKit构建配置缺MapKitSubtitleHUD.m，未修改或掩盖，R6包不包含MapKit。已有编译警告保留。

本次未由助手签名安装或操作固件，R6新增自动定位仍待用户真机确认。
