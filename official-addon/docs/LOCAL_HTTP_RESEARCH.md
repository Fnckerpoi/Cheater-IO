# 任意 IP HTTP 模型研究构建（含公网）

2026-10-10 用户明确选择允许任意公网 IP，本机普通 V2 HTTP 研究分支改为支持任意数字 IPv4 / IPv6（含公网，并保留局域网兼容）及可配置端口。默认构建仍只允许 HTTPS；需要 `TIO_LOCAL_HTTP_ENABLED=1` 与 `com.duriea.turboio.research`。以下只是 URL 格式示例，公网段使用文档保留地址，不是实际模型服务：

```text
http://192.168.31.101:8080/v1/chat/completions
http://203.0.113.20:9000/v1/chat/completions
http://[2001:db8::20]:1234/v1/chat/completions
```

HTTP 主机必须为标准数字 IP，不接受域名、非标准 IPv4、IPv6 zone ID。端口 1–65535，省略为 80；路径可为 `/chat/completions`、`/v1/chat/completions` 或其他以 `/chat/completions` 结尾的规范 ASCII 路径，不接受编码、点段、双斜杠、用户名密码、查询或片段。不自动跟随重定向。认证值仍存手机钥匙串；更换地址必须重新输入 Key，不自动带走旧地址凭据。`localhost` 不接受，127.0.0.1 / ::1 指手机自身而非电脑。

**HTTP 会明文传输 Key、问题、个人资料与上下文，可被监听或篡改；公网尤其危险，优先 HTTPS，使用独立可撤销的 Key。** 保存时展示实际目标和风险，取消不保存、不切换模型。新包系统 ATS 策略是整个研究 App 范围，不是只放宽插件，详见网络配置边界。

## 适用范围

- 原厂输入：官方 App 1.0.5 / Build 201；仍检查原始主程序 UUID、加密、架构与加载项，不跳过门禁。
- 普通扩展，不与固件、原生音乐/导航或翻译实验模块混用；不刷眼镜固件。
- 旧固定 IP 版本已有单轮局域网问答成功反馈；新增任意 IP 版本尚未签名安装或做公网真机验收，不外推旧结果。
- 模型 ID 由用户服务决定。本次用户提供 `qwen3.8-flash-next-iq3_xxs`，未验证服务的返回格式或性能；客户端需要流式 `text/event-stream` 与完成标记。

## 构建与测试

从仓库根执行，先保证 [Xcode](https://developer.apple.com/xcode/) 和 Node.js 22.16+ 可用：

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
export PATH=/Users/duriea/.nvm/versions/node/v22.22.3/bin:$PATH
bash official-addon/test.sh
TIO_LOCAL_HTTP_ENABLED=1 bash official-addon/build.sh embedded com.duriea.turboio.research
```

产物：`official-addon/build/local-http/TurboIOPrivateAddon.dylib`。默认 `build/embedded/` 产物不会被这条 opt-in 构建覆盖。

测试覆盖默认 HTTP 拒绝、opt-in 公网/局域网 IPv4 和 IPv6 接受、端口边界、HTTP 域名与畸形 URL 拒绝、空 Key 不发请求、合成 Bearer 头、流式完成、重定向不转发、ATS 优先级与旧库标记拒绝。测试只用模拟传输、文档保留 IP 和合成凭据，不访问真实公网模型。

## 与高德地图导航一起使用

普通 HTTP 研究版现在支持额外链接高德手机导航（不含 TNV1 / OTA / 固件修改）。请按 [HTTP + 高德导航集成与验收](HTTP_NAVIGATION.md) 同时开启两个构建宏；产物隔离在 `build/local-http-navigation`，预合并时必须同时传 `--amap-sdk-root`，不能把仅含 HTTP 的旧 IPA 当作导航版本。导航需要用户自己的 iOS Key，绑定安装后实际 `com.duriea.turboio.research`；不内置 Key，不将位置交给大模型。

## 本机预合并输入

使用 `local_http_package.py` 复用仓库既有合并器，仅创建新的输出，保留源应用与 IPA。需要 Node.js 22.16+；以下输出目录必须不存在：

```bash
python3 official-addon/local_http_package.py \
  --app /Users/duriea/Downloads/TurboIO-ios105-input/Payload/Runner.app \
  --addon /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/local-http/TurboIOPrivateAddon.dylib \
  --node /Users/duriea/.nvm/versions/node/v22.22.3/bin/node \
  --out /absolute/private/new-http-any-ip-output
```

输出 `TurboIO-LocalHTTP-unsigned.ipa` **预先包含扩展，但没有你的有效签名**，不能直接用设备安装命令安装。仅作为 [Sideloadly](https://sideloadly.io/) 的签名输入，不公开分发原厂修改应用或签名材料。

### 网络配置边界

新预合并器设置 `NSAllowsArbitraryLoads=true`，移除 `NSAllowsArbitraryLoadsForMedia`、`NSAllowsArbitraryLoadsInWebContent`、`NSAllowsLocalNetworking`：Apple 文档明确，在现代 iOS 上只要这些键存在（不论 true/false），全局键就会被忽略。已有域名例外保留；已有数字 IP 例外显式允许 HTTP，避免其覆盖全局策略。

**这会放宽整个研究 App 的 ATS，不能声称保留厂商全局策略或仅影响模型插件。** 报告明确写 `vendor_global_ats_policy_preserved:false`、`app_wide_ats_relaxed:true` 和移除的键。客户端对模型入口仍限制数字 IP、端口与规范路径；知识库和转写整理的独立 HTTPS 校验不变。没有取消 HTTPS 默认系统证书信任校验。

新库包含 `turboio-http-any-ip-v1` 标记；打包器拒绝旧固定 IP 或 HTTPS-only 库与错误目标。新版本也保留 AppleDouble 排除及原厂版本/UUID/架构门禁。

## 更新安装

1. 先结束眼镜问答、录音、传输；升级状态不明时不要强停 App。
2. 在 Sideloadly 加载新生成的未签名 IPA，保持与之前相同的 Apple 账号。
3. 取消 `Use automatic bundle ID`，填写 `com.duriea.turboio.research`，保持名称 `TurboIO Research`，不修改版本和机型限制。
4. **取消 `Inject dylibs/frameworks`，清空之前的外部 dylib 条目。包内已合并一份扩展，不再二次注入。** Cydia Substrate、Substitute、Sideload Spoofer 都不启用。
5. 重新签名安装到原研究副本，不先卸载，不操作商店官方 App 或眼镜配对。不保证系统签名错误下也能覆盖；遇到要求删除原版、撤销证书或解除限制时先停止。
6. 自动续签如已启用，需要同账号和同标识登记此次新 IPA；检查其缓存不再使用旧 IPA 或旧注入设置。自动刷新是否实际成功须另行观察，不因安装通过就宣称已验证。

## 2026-10-09 签名安装故障与修正（旧固定 IP 版本历史）

以下路径、摘要与用户反馈仅属于旧固定地址包，不是本次任意 IP 更新产物。不要将旧修正版当成新版本安装。

用户首次安装预合并更新包时遇到 `ApplicationVerificationFailed / 0xe8008001`。本机检查保留的签名产物发现：主应用的资源清单引用缺失的 `._…` 元数据，而扩展动态库自身的签名校验通过。

原版 IPA 的 AppleDouble 条目为 0，第一份更新 IPA 新增 3,132 条，失败签名产物的资源清单引用其中 3,130 项。原因是本机打包工具默认将扩展属性转换为 AppleDouble 文件；签名器将其作为资源封存，但元数据感知的解包器将其还原或丢弃，导致资源清单不一致。这个缺陷由本轮新打包方式引入，不归因于用户的模型地址或密钥。

已将归档方式改为 `ditto --norsrc --noextattr --noacl`，并在交付前断言 IPA 不含 `._*` / `__MACOSX`。测试使用合成应用走“归档 → 解包 → 实际 ad-hoc 签名 → 元数据感知解包 → codesign 校验”的完整本机链路：旧流程因缺失封存资源失败，新流程通过。macOS 本机验签并不替代 iPhone 对最终开发签名和描述文件的安装验证。

**不要继续使用首份更新 IPA。** 改用 [修正版未签名 IPA](/Users/duriea/Downloads/TurboIO-local-http-20261009-clean/TurboIO-LocalHTTP-unsigned.ipa)，保持同账号、同应用标识与关闭额外注入，再由用户重新签名安装。原版输入与旧文件保留供比对，不删除已有研究 App，不撤销证书。新包除元数据归档条目外，所有实际应用文件与首份包逐字节一致。

修正版 SHA-256：`4005f20dd887098845f3a61c12e8d29c827b144bcd4d3df32a40446032108a29`。用户随后确认修正版已可正常打开，说明此次签名安装故障在本机设备上已消除；随后用户纠正连接的 Wi-Fi，确认 API 测试通过；切换自定义模型后，用户确认镜片完整显示回答、声音正常，且 Windows 的 [Strata](https://github.com/Niko1221/Strata/blob/main/README.zh-CN.md)出现对应请求或生成活动。此次单轮局域网模型问答链路通过；连续对话、断网恢复和问答后台稳定性仍未验收。自动刷新配置及一次手动触发后台无线刷新见下方，定时无人值守的到期前刷新仍待观察。

## 手机配置与验收

在研究入口的模型页，找到自有模型的接口配置，填写完整地址、服务实际模型 ID，以及用户自己的 Key。确认明文风险后保存；保存会将模式恢复为官方默认，不自动接管。

1. 局域网用可互通 Wi-Fi；公网用自己的可达服务，确认认证、路由和对应端口，不笼统关闭防火墙。遇局域网权限提示只决定此研究副本的授权。示例保留 IP 不可当真实服务地址。
2. 先到诊断页运行“测试模型接口”，仅提交合成问题，不携带真实历史。失败时根据错误排查服务、认证或流格式；不要用成功弹窗推断镜片成功。
3. 测试通过后再选择自定义接口模式，进行一句不含个人资料的眼镜问答，分别验收文本、声音与退出。
4. 新版本改变 IP / 端口只需在 App 配置页输入新地址、重新填 Key、确认风险、测试并选择模式，不需每次构建。首次从旧版更新仍必须重新预合并、签名安装。

## 自动续签配置及验证（旧固定 IP 包历史，更新后需换缓存）

本机只读检查确认：Sideloadly Daemon 正在运行，登录时启动已配置；研究副本已登记为自动刷新（非 one-off），7 天签名期限，刷新阈值字段为 96 小时。缓存 IPA 的 SHA-256 与上述修正版一致，额外 dylib 注入数为 0，没有登记的刷新错误。未直接修改工具数据库、账号或登录令牌。

用户开启 Finder 的“连接到 Wi-Fi 时显示此 iPhone”后确认拔线仍可发现设备。随后按后台菜单手动触发刷新，用户确认重新安装成功，且模型接口重新测试通过。因此无线发现、后台重新签名安装及刷新后模型配置可用已验证；不能将这一次手动触发等同于已观察到到期前无人值守定时刷新。

维持 Mac 开机、联网、后台续签程序运行，手机通过可互通局域网或 USB 被发现。登录状态失效、休眠或网络隔离可能使刷新失败。保留同 Apple 账号、研究应用标识和修正版缓存，不叠加额外注入。长时间离开 Mac 前可手动刷新；不要删除研究 App 来“续签”。

## 回退

优先恢复此前已验证、同目标标识的包，以同账号签名覆盖安装；旧固定 IP 包仍只允许原地址。若改用 HTTPS-only 研究目标，需以 `com.duriea.turboio.research` 编译并使用匹配的普通打包流程，不能用默认官方目标库凑包，也不能用任意 IP 专用预合并器接收 HTTPS-only 库。不要叠加注入，重要数据先备份。回退不要求刷固件或删除官方 App。

## Sources

- [Apple — NSAllowsArbitraryLoads](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsallowsarbitraryloads) — 本次核对全局放行与细分键的优先级，以及域名例外不受全局键影响。
- [Apple — NSExceptionDomains](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsexceptiondomains.md) — 本轮已读取，支持 iOS 17+ 单 IP 例外，不含端口。
- [Apple — NSAllowsLocalNetworking](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsallowslocalnetworking.md) — 本轮已读取，核对全局键交互后选择不新增宽泛局域网放行。
- [Sideloadly FAQ](https://sideloadly.io/faq) — 前序已读取，参考同账号/标识更新、注入、免费签名与自动刷新；未独立审计厂商登录安全。
