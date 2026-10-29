# 嵌入版网页授权不回跳修复

## 现场与根因

用户报告网页授权完成但停在浏览器。截图为 Opening Agents Anywhere…；手机 query-activities 对 agents-anywhere://oauth/callback 返回 No activities found。嵌入装配 package-embedded.py 原仅调用 embed_manifest.patch，新增 AA 工作台 Activity，却未调用已有 dsh_manifest.patch 声明 DshAuthCallbackActivity。此事实不代表服务器授权失败，也不是旧包抢占回调的证据。

## 修复

嵌入装配按序执行 dsh_manifest.patch 与 embed_manifest.patch。回调过滤器保持精确 scheme=agents-anywhere、host=oauth、path=/callback、VIEW/DEFAULT/BROWSABLE；仅回调 Activity 导出，AA 工作台仍非导出。未修改服务器、PKCE、state、换码与一次消费校验。新增回归测试检查实际装配路径包含唯一精确回调声明及重复执行幂等性。

## 本轮验证

- EmbeddedAssemblyTest.py 共 4 项通过；构建/装配/zipalign exit 0。
- 修复版：artifacts/embedded-aa-oauthfix-20261027/TurboIO-AA-oauthfix-signed.apk。
- APK SHA-256：656ad6d08be89c18b1e720baf61f56588ecb7995318c0eaa8e733763a441347d。
- 保留前轮 Context 递归修复；载荷仍为 ab2cb7e393393dba46bc03d363162f16a839eed1c11e1d5f1a5df1ae1a225030。
- 签名证书 SHA-256：1b070381fc578193779539456722e096b21fe447959000952dcd488842d8f8c3。
- adb install -r --no-streaming 返回 Success，进程 exit 0；未卸载、清数据或迁移旧包凭据。
- 设备已安装 base.apk 的 sha256sum 与签名候选完全一致。
- 安装后系统查询精确回调返回唯一 Activity：com.rayneo.venus.pub/com.turboio.addon.DshAuthCallbackActivity。修复前无处理者，修复后一个处理者。
- 已返回 Turbo IO 并打开电脑端 DSH 设置，等待用户重新发起授权。

## 未验证

更新会终止原进程，旧浏览器授权轮次可能过期；不重放旧code或伪造回调。需要用户从新包电脑端 DSH 页面重新点击网页授权登录，完成同轮回跳与账户保存验证。当前只证明回调入口注册与路由修复，不代表实际授权换码、登录持久化、任务派发已通过。
