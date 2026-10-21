# 1.0.7 共存研究包安装与首次启动测试

## 已执行

用户明确授权安装测试。设备YAL-AL10 / Android API29。准备独立包com.turboio.rayneo107.research，label TurboIO 1.0.7 Research，版本1.0.7(230)。

签名APK：artifacts/android-107-install/research-20261021-113236/TurboIO-1.0.7-research-coexist-signed.apk

大小429686965字节，SHA256：5a691367c3a03877c7d3099085fe5b0ab6b839ec13ab8b922cfe31b69e68abaa。研究证书SHA256：1b070381fc578193779539456722e096b21fe447959000952dcd488842d8f8c3。

独立zipalign/apksigner验证通过；共存仅40处Manifest身份属性变化，10authority/8自定义permission与官方隔离，DEX/native不变。

## 设备结果

- adb install --no-streaming 实际Success。新UID10266，独立dataDir。
- 显式启动 com.turboio.rayneo107.research/com.rayneo.venus.MainActivity：Status ok，COLD，TotalTime2419ms。
- 进程PID27556持续存活，MainActivity为resumed。限定进程AndroidRuntime/libc/TurboIO错误日志无致命异常。不能外推长期稳定性。
- 首屏UI观察为短信验证码页。未读取/填写验证码，未执行登录、配对、授权或OTA。页面上账号文本不记录在本报告。
- 推送SDK日志出现 PUSH_10102 / illegal package，研究包推送身份未获厂商认可，此项未通过。

## 官方包保留状态发生变化，尚待核实

安装前官方包UID10265，版本1.0.7(230)。安装并首次启动研究包后，official-package-after.txt与before基线一致；官方安装路径仍存在。

后续最终复查中，pm path不再返回官方包；pm list packages及-u也只列研究包。系统包变更列表先后记录官方包seq173和研究包seq174，但本轮有界日志未建立变化来源。不能继续声称最终官方包和数据仍保留，也不能据此认定由研究包导致。

本轮工具命令未执行adb uninstall、pm clear、官方App数据导出或恢复。官方包最终状态变化后停止进一步交互，不擅自重新安装/恢复。

设备证据在artifacts/android-107-install/device-baseline/：official-package-before.txt、official-package-after.txt、research-launch.txt。first-window.xml包含首屏个人账号相关文本，仅保留本地忽略目录，不作公开交付。

## 用户验收确认（2026-10-21）

用户明确回复"可行，已完成验收"，即确认 1.0.7 研究包在本机可安装、可启动并可用。该确认来自用户实机观察，覆盖账号登录后的实际使用；本轮工具侧独立复测只验证到：研究包仍安装（UID 10266，1.0.7/230，独立 dataDir，进程存活）。

官方包 com.rayneo.venus.pub 在最终复查中未出现在 pm list 中，用户确认未提出异议；本记录不据此认定官方包或数据已被保留，如需恢复官方包应由用户自行重装并登录。

## 未验收

官方账号登录、眼镜连接、ASR/NLP、导航、卡片、后台、推送服务和实验OTA均未验收；共存身份对厂商服务仍可能有限制。下一步需用户核实手机官方App是否被手动移除，并自行处理验证码登录，再开始明确的功能验收。
