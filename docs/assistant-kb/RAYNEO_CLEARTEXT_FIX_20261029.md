# 雷鸟 AI 助手模型接入：明文 HTTP 根因与修复验证（2026-10-29）

## 结论（设备实证）

原报错“响应式流异常”（界面文案为 `网络或流式响应异常`）的直接原因不是地址格式，而是 Android 明文流量策略拦截：

```
last_error=IOException: Cleartext HTTP traffic to 100.120.224.94 not permitted
```

宿主 `targetSdkVersion=36`，`<application>` 既无 `usesCleartextTraffic`，也无 `networkSecurityConfig`，且包内无 network_security_config 资源；因此自有模型地址 `http://100.120.224.94:8080/v1/chat/completions`（明文 HTTP）在请求建立前即被平台拒绝，`TurboAddon` 与 `CardAI` 的 `catch` 把它统一降级为“网络或流式响应异常”。

网关本身正常：PC 侧 `curl --noproxy '*'` 返回 `401 {"error":{"type":"authentication_error"}}`（未带 Key 的预期结果）。此前观察到的 `502` 来自本机 Mihomo 系统代理，不是网关故障，不能作为根因。

## 本轮改动

1. **API 基础地址自动补全**：`ChatPolicy.completionEndpoint` 把 `https://domain.name/v1/`、`https://domain.name/v1` 规范化为 `.../v1/chat/completions`，并兼容已保存的完整地址（幂等，不重复拼接）。模型对话与 AI 卡片生成共用该规则。
2. **删除 Codex 知识库接入**：移除注册、查询、状态、来源与设置入口；`EditorialUI` 入口更名“搜索与工具”。保留 TinyFish 搜索与 Agents-Anywhere/DSH 派发。第三方知识库后续按 MCP 方式接入（本轮未实现 MCP 管理）。
3. **明文开关**：新增 `android-addon/cleartext_manifest.py`，仅在 `<application>` 上追加 `usesCleartextTraffic=1`，不新增权限或组件，幂等且保留其余节点；由 `package-embedded.py` 的 `patch` 链在 OAuth/工作台补丁之后调用。
4. **可观测性**：`TurboAddon.recordError` 记录脱敏后的失败类别与消息（截断 200 字符，凭据与长 token 掩码），诊断面板显示 `last_error=`；`CardUI` 生成失败时显示异常类别，不再只给笼统文案。

## 构建与安装身份

- 源码基线：`android-addon/src/com/turboio/addon` 97 个 Java 文件，`javac -source 8 -target 8` + D8（min-api 29）通过。
- 装配审计（`package-embedded.py`）：宿主 pin `d39312a2…5a24b0` 匹配；`unchangedMemberCount=3976`；变化仅 `AndroidManifest.xml` 与 `classes4.dex`；新增仅两个 AA asset；`payloadSha256=7406c2d4…d4c201`、`sidecarSha256=dd25f8b6…d297822`、`addonSha256=c225c97e…b691f4`。
- 明文属性核验：`aapt dump xmltree` 显示 `usesCleartextTraffic(0x010104ec)=(type 0x12)0x1`；zipalign 校验 exit 0。
- 签名包：`artifacts/embedded-model-baseurl-20261029/TurboIO-AA-model-baseurl-cleartext-signed.apk`，446,719,293 字节，SHA-256 `2057194c0e43f811fcba0a02f560cc98eadcbe4f3785c24ee8021fa4a4ff59e7`；apksigner sign/verify exit 0，证书 `CN=TurboIO RayNeo107 Local Research` / SHA-256 `1b070381…d8f8c3`（与上一轮已装包一致，故为覆盖安装）。
- 安装：`adb install -r --no-streaming` 返回 Success、exit 0；设备 `base.apk` `sha256sum` 与本地签名包逐字符一致；`userId=10271`、`firstInstallTime=2026-10-27 08:32:59` 不变，`lastUpdateTime=2026-10-29 15:53:34`。未卸载、未清数据、未绕过系统身份验证。

## 已验证范围

- 冷启动后 PID 存活，crash buffer 无本应用记录。
- 设置页显示“API 基础地址（如 https://domain.name/v1/）”与补全说明；知识库入口与文案全部消失（UI dump 无“知识库”）。
- 地址按需求改为仅基础形式 `http://100.120.224.94:8080/v1` 并保存、回读一致。
- 应用内探针（仪表盘 → AI 生成 · 使用自有模型 → 生成布局，仅本机预览、不发送眼镜）：修复前立即 `IOException`；修复后返回内容并显示“本地约束校验通过，请预览”，诊断 `last_error=none`。
- AA 工作台仍可进入 `AaEmbeddedActivity`，会话列表与调度配置正常渲染，无崩溃。

## 未验证与遗留风险

- **语音流式链路未端到端验证**：诊断 `requests=0` 表明本轮没有真实 SSE 流式请求发生（`mode=2` 已选，但需眼镜语音触发 ASR→NLP）。明文开关与地址补全走的是同一 `HttpURLConnection` 栈，但“流式回答 + 镜片显示 + 插话/收尾”仍需你在设备上实测。
- **明文开关是全局放宽**：`usesCleartextTraffic=1` 允许该 App 对任意域名使用 http，凭据将以明文传输；仅建议在你信任的内网/Tailscale 环境使用。若网关提供 HTTPS，改回 https 地址即可，无需此开关。
- **回环风险仍在**：AA 工作台会话列表出现标题“雷鸟AI助手接入报错”的会话，疑为本助手当前会话；未向其派发任何任务。派发前需确认目标会话，避免把消息注入本对话。
- 输入法会在 URL 末尾自动补 `/` 后再补逗号，曾把地址写成 `.../v1/,`；本轮已清除并按无尾斜杠形式保存。后续若手填地址请回读确认。
- 构建日志仍有 Java 8 `-source/-target` 过期、deprecation 与 unchecked 警告；编译成功不等于无警告。
