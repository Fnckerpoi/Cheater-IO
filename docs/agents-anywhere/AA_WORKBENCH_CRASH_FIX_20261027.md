# AA 工作台启动闪退修复与设备验证

用户报告点击 AA 工作台闪退后，从连接设备 YAL-AL10 的 crash buffer 取得 2026-10-27 09:04:27 堆栈：进程 `com.rayneo.venus.pub`，`java.lang.StackOverflowError`，重复 `Context.registerComponentCallbacks(Context.java:617)`，上游为 Compose AndroidCompositionLocals 的 image vector cache 注册路径。

## 修复

`AaPayloadLoader.PayloadContext` 保留 `getApplicationContext()` 返回载荷 Context，以保证隔离资源/类加载来源；新增 `registerComponentCallbacks` 和 `unregisterComponentCallbacks`，转发至基底 Context 的真实 Application，避免框架默认实现再次取得自身并无限递归。没有放宽 Manifest exported、哈希校验或派发守卫。

## 产物身份

- 目录：`artifacts/embedded-aa-contextfix-20261027/`。
- 已签名 APK：`TurboIO-AA-contextfix-signed.apk`。
- SHA-256：`a10aa84595f991498ca38556e8917082550785947188aa437e674be23255c0a7`。
- 签名证书 SHA-256：`1b070381fc578193779539456722e096b21fe447959000952dcd488842d8f8c3`。
- 安装目标：`com.rayneo.venus.pub`，versionName 1.0.5。此包与原手机上的 `com.turboio.rayneo107.research`（1.0.7）并存；签名相同不代表包名相同，不能把本次安装说成覆盖了 1.0.7。
- 载荷未改：SHA-256 `ab2cb7e393393dba46bc03d363162f16a839eed1c11e1d5f1a5df1ae1a225030`。

## 本轮证据

1. `build-embedded-windows.ps1` 在新目录构建，javac/JAR/D8/装配/zipalign 检查 exit 0；3976 个宿主成员字节不变。
2. apksigner sign 和 verify exit 0；adb `install -r --no-streaming` 返回 Success，exit 0，未卸载或清数据。
3. 设备 `sha256sum` 读回已安装 base.apk 与上述签名 APK 哈希一致，lastUpdateTime 为 2026-10-27 09:12:57。
4. `am force-stop com.rayneo.venus.pub` 后冷启动 MainActivity，通过真实应用内 Turbo IO → AA 工作台按钮进入。Compose 页面可见 Sessions、DSH Settings、New task 及未授权提示；前台为 AaEmbeddedActivity，进程 PID 30677 存活。
5. 返回并再次进入两次，PID 保持 30677；本轮进程 crash buffer 行数为 0。
6. 从工作台打开 DSH Settings 成功，页面显示尚未通过网页授权登录。没有替用户登录、保存配置或派发真实任务。

截图与日志：`artifacts/aa-install-validation-20261027/` 中的 `crash-before.txt`、`crash-after.txt`、`cold-host.png`、`settings.png`、`workbench-fixed.png`、`workbench-reentry.png`、`dsh-settings.png`。

## 验收边界

本机冷启动进入 AA、返回与重复进入、打开 DSH 设置已验证；不能据此证明登录后所有资源/原生依赖、旋转恢复或真实任务发送查询续聊均可用。下一步需用户在新包的 DSH Settings 完成网页授权，再核对精确 Connector/Project/Runtime 与会话配置后继续任务链路验证。原 1.0.7 包登录状态没有被迁移到新包。
