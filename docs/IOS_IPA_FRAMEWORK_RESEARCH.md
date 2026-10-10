# iOS IPA、App 包与内嵌 framework：取得路径与边界

这是**通用研究说明**，不是 Turbo IO 所用原厂 IPA 或 7 个 framework 的来源鉴定；仅凭仓库内出现二进制，不能证明它们从哪一版应用、哪台设备或哪种导出流程取得。请只处理自己开发、获权使用或经权利人许可的材料，不公开转发第三方安装包或绕过保护。

## 先分清四种东西

| 对象 | 合法、可复现的取得方式 | 能得到什么、不能推断什么 |
| --- | --- | --- |
| 自己的 iOS 应用 | 在 Xcode 选设备目标，**Product → Archive**；Organizer 选归档、**Distribute App → Debugging → Export**。Apple 说明导出目录含 **.ipa** 文件。[Xcode：向已注册设备分发](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices) | 可检查自己构建和签名的分发产物；不是“从 App Store 下载回原始开发包”。 |
| 已有权访问的 .ipa / .app | 在副本上检查包结构：`unzip -l MyApp.ipa`；`unzip -q MyApp.ipa -d inspected`；`ls inspected/Payload/*.app/Frameworks/`。 | 常见 IPA 展开后有 `Payload/MyApp.app`；应用包含资源、可执行文件及可能存在的内嵌框架，**不是源码仓库**。路径为示意，具体名称应按实际包检查。Apple 将 bundle 定义为包含代码和资源的标准层级目录，并规定 iOS 内嵌框架位置是 app 根目录的 `Frameworks/`。[Apple：bundle 内容位置](https://developer.apple.com/documentation/bundleresources/placing-content-in-a-bundle) |
| 自己设备上的 App Store 安装应用 | 用自己的 Apple 账户正常安装；如需研究、移植或分发原厂组件，应先向权利人取得明确授权或官方 SDK/源码。Apple 所述 Configurator / Device Hub 在这里是**安装已导出测试包到已注册设备**的路径，不能由此推出“可导出商店安装应用的未加密 IPA”。[Xcode：安装测试包](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices#Install-the-app-on-registered-devices) | 安装在设备上 ≠ 拥有可再分发的 .ipa，也 ≠ 拥有源代码。对 App Store/FairPlay 保护的构建，即使拿到包的文件层，也不能把“解压文件”直接等同于取得可用的未加密 Mach-O；不要提供砸壳、绕过 DRM 或共享已解密包的教程。**证据边界：本轮核对的 Apple 文档没有证明所有商店安装包、所有版本或其中每个 framework 都被 FairPlay 加密；须逐个样本核验，不能一概而论。** |
| .framework | 自己的 Xcode 构建产物、权利人公开 SDK / XCFramework，或获许可包内的 `MyApp.app/Frameworks/Foo.framework`。Apple 明确区分**以源码提供的 Swift package**和**以二进制提供的 XCFramework**。[Apple：分发二进制框架](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages) | framework 是 bundle，可含 Mach-O、Info.plist、资源及接口文件；有头文件/Swift 模块接口也不等于有实现源码。是否允许复用、修改和再分发，应查该组件的许可和授权。 |

## 推荐的实操顺序

1. **开发者本人**：保留 Xcode 项目与 .xcarchive，按上述 Apple 教程导出自己的 IPA；在复制出的包上列目录并确认 `Payload/*.app/Frameworks/` 是否存在。若无框架，不应假设它被“藏起来”：依赖可能静态链接、由系统提供，或未随该构建嵌入。[Apple：bundle 内容位置](https://developer.apple.com/documentation/bundleresources/placing-content-in-a-bundle)
2. **仅持有第三方已安装应用**：优先请求官方 SDK、公开源码或书面许可；不要把备份、Configurator 安装操作或普通 ZIP 解包当成获取第三方原始开发 IPA / 解密代码的保证。iOS 对应用及动态库实行签名验证，修改文件后也不能假设原签名仍有效。[Apple Platform Security：应用代码签名](https://support.apple.com/guide/security/app-code-signing-process-sec7c917bf14/web)
3. **要验证具体来源**：记录权利人交付记录、版本/build、bundle ID、框架名称、SHA-256、签名与许可证；技术相似或同名只能作为线索，不能代替来源证明。对 Turbo IO，现有公开说明仅把 7 个 framework 作为二进制依赖及哈希清单记录，且明确不冒充完成内部许可/秘密审计：[本仓库源码发布说明](SOURCE_RELEASE.md)。

> 参考入口（官方教程）：[Xcode：归档与发布总览](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)；[Xcode：向已注册设备导出 .ipa](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)；[Apple：bundle 位置表](https://developer.apple.com/documentation/bundleresources/placing-content-in-a-bundle)；[Apple：二进制框架与源码包区别](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages)。
