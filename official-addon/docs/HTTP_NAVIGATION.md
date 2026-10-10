# HTTP 模型研究版 + 高德导航：集成、安装与真机验收

适用：官方 iOS App **1.0.5 / Build 201** 的普通 V2 研究副本，目标 `com.duriea.turboio.research`。保留用户已跑通的 HTTP IP 模型入口，不修改眼镜固件，不启用 TNV1、OTA、音乐或翻译实验模块。导航不是经过安全认证的行车设备，勿边驾驶、骑行或过马路边测试。

## 1. 这次补齐了什么

- 同一份扩展同时编入 HTTP 模型支持与高德地图、地点搜索、步行／骑行／驾车引擎；三个出行方式使用各自 SDK 算路接口。
- 独立输出目录 `build/local-http-navigation`，不覆盖原 `build/local-http` 动态库。
- 未签名预合并器检查导航构建标记与 `--amap-sdk-root` 成对提供，预检、复制并在 IPA 中验证三份资源；缺 SDK 资源、同名冲突或资源符号链接即停止。
- 高德 Key 设置和详细诊断显示安装后的实际 Bundle ID，避免误绑原官方应用；Key 只存手机钥匙串，不需要提供给助手。
- SDK 初始化是进程级状态：退出再进入导航页也不能在当前进程中热换 Key；需要结束 App 后重新打开，在开启地图前配置。
- 地图开启前检查资源是否存在；模型 URL、Key、HTTP 明文确认和拒绝重定向策略沿用现有研究版。

源码入口：[构建脚本](../build.sh)、[HTTP 预合并器](../local_http_package.py)、[导航页面](../NavigationUI.m)、[导航后台策略](NAVIGATION_BACKGROUND.md)。

## 2. 固定依赖与下载

本轮核对的官方版本：**Navi 11.3.100 + Foundation 1.9.4 + Search 9.8.1**。原脚本的全量 URL 已更新，原 11.2.100 哈希失配时先停止；核对官方页面与包内版本后才更新固定哈希，未关闭校验。

| 官方压缩包 | 固定 SHA-256 |
| --- | --- |
| `AMap_iOS_Navi_ALL.zip` | `8264377eb68d414c046be96585957250cda15a7eabd16553842f53f98764f4b8` |
| `search-9.8.1.zip` | `d11e0b418319c74abf5228611f678b58130eac791e267d6d6e3e7ec9f41b2007` |

来源：[高德官方导航 SDK 下载](https://developer.amap.com/api/ios-navi-sdk/download)、[地图／搜索 SDK 下载](https://lbs.amap.com/api/ios-sdk/download)。阅读官方 SDK 许可及隐私说明后，在仓库根目录执行：

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
export PATH=/Users/duriea/.nvm/versions/node/v22.22.3/bin:$PATH
node official-addon/setup-amap.mjs --accept-sdk-terms
```

已下载且验证的 SDK 在本机忽略目录 `official-addon/build/amap-sdk`；脚本拒绝覆盖它。SDK 更新应另行核对版本、哈希和 API，不直接套用新包。SDK 不上传仓库，用户 Key 不编入动态库。

## 3. 构建同一个版本

```bash
TIO_AMAP_ENABLED=1 TIO_LOCAL_HTTP_ENABLED=1 \
  bash official-addon/build.sh embedded com.duriea.turboio.research
```

输出 `official-addon/build/local-http-navigation/TurboIOPrivateAddon.dylib`。只开启一个宏会少一个能力；默认官方目标也不适合更新你的 HTTP 研究副本。高德代码静态链接进扩展，不额外注入第二套 AMap framework。

HTTP 仍是整个研究 App 的 ATS 放宽，不是仅对模型插件放宽；它会明文传输模型 Key 和对话，优先用 HTTPS。高德初始化仍指定 HTTPS，导航坐标不交给模型。

## 4. 生成本机未签名更新输入

使用你有权研究的原始输入，不用上次已注入或带私人配置的包。输出目录必须全新，原应用与旧 IPA 保留：

```bash
python3 official-addon/local_http_package.py \
  --app /Users/duriea/Downloads/TurboIO-ios105-input/Payload/Runner.app \
  --addon /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/local-http-navigation/TurboIOPrivateAddon.dylib \
  --amap-sdk-root /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/amap-sdk \
  --node /Users/duriea/.nvm/versions/node/v22.22.3/bin/node \
  --out /absolute/private/new-http-navigation-output
```

输出 `TurboIO-HTTP-Navigation-unsigned.ipa` 与 `local-http-preparation.json`。报告记录 SDK／资源、Key 目标、后台声明、源文件保持与 SHA-256；`online_navigation_verified:false` 是有意保留的真实验收边界。未签名输入不等于可直接安装的应用。

当前本机原厂输入已有 `UIBackgroundModes.location`，预合并器保留原来的音频、蓝牙、配件等所有模式。其他输入若确实缺少该项，可显式加 `--navigation-background`；不传不会偷偷新增。该参数不授予系统定位权限，也不申请“始终”定位。

资源为 `AMap.bundle`、`AMapNavi.bundle`、`AMapSearch.bundle`；预合并器保留 AppleDouble 排除修复、原始 UUID／版本／加密／架构门禁、只注入一份扩展、禁止固件实验混用。

## 5. 用 Sideloadly 覆盖更新

1. 先结束眼镜问答、录音、导航等任务。保持之前的 Apple 账号，不先卸载研究 App，不改官方商店 App 或眼镜配对。
2. 在 Sideloadly 载入此次新未签名 IPA。
3. 取消 `Use automatic bundle ID`，保持 `com.duriea.turboio.research` 与 `TurboIO Research`，不改版本与机型限制。
4. **关闭 `Inject dylibs/frameworks` 并清空旧外部动态库条目**：包内已经有一份扩展。不要再注入 AMap SDK、Substrate、Substitute 或 Spoofer。
5. 由使用者签名并覆盖安装。若工具要求删除原版、撤销证书或绕过限制，停止核对，不为导航清掉已验证的模型配置。
6. 自动续签应登记这份新 IPA；不要让旧缓存下次刷新把 SDK 覆盖回去。

同账号和同 Bundle ID 是保留配置的必要条件，不能保证每个签名工具都保留钥匙串／沙盒。安装通过后仍要独立验收启动、模型接口与导航。详见 [HTTP 更新说明](LOCAL_HTTP_RESEARCH.md)。

## 6. 配置自己的 iOS Key

1. 打开[高德开发者控制台](https://console.amap.com/dev/key)，为自己的应用创建 **iOS 平台** Key。Web 服务／JS API Key 不能替代。
2. 绑定 `com.duriea.turboio.research`，核对账号的地图、搜索和导航服务权限／额度。不要绑定 `com.rayneo.venus.pub` 后用于研究副本。
3. 安装后进入 **TurboIO → 资料 → 步行／骑行／驾车导航 → 更多 → 高德 Key 设置**。弹窗显示实际 Bundle ID；如果与你设置的不一致，先核对签名工具，不盲试密钥。
4. 在手机上输入 Key 并保存，不把 Key 发到聊天或提交仓库。保存成功只证明本机写入，不证明高德鉴权成功。
5. 点击 **开启地图**，阅读并自主同意 App 内隐私说明，才创建 SDK 实例。下载 SDK 的许可确认不替代这一手机定位隐私确认。

换 Key：结束 App 再打开，在首次开启地图之前修改。只关闭导航页不等于重启 SDK。

## 7. 真机逐项验收

先在室内静止完成模拟测试，再在安全场景测试手机实时导航；不能用编译通过或夹具效果代替真实服务。

| 步骤 | 操作 | 通过判据 |
| --- | --- | --- |
| 更新基线 | 打开研究副本，更多 → 详细诊断 | 实际 Bundle ID 正确，SDK 为 11.3.100，三份资源已打包；不显示未链接 SDK |
| 地图鉴权 | 保存 iOS Key，同意隐私，开启地图 | 加载真实高德底图；不是离线占位图 |
| 搜索服务 | 输入公开地点，主动点“搜索地点”，选择结果 | 有真实结果并选中终点；地图成功不代表搜索也已开通 |
| 步行模拟 | 更多 → 北京演示路线，选步行，规划 → 开始模拟 | 显示路线、距离、时间，指令随模拟更新；未规划时开始按钮不可用 |
| 骑行／驾车 | 停止，再各自规划并开始模拟 | 使用对应引擎；失败报告服务／网络／算路错误，不退回步行 |
| 更换路线 | 已规划但未开始时改起点／终点／方式 | 旧路线失效，必须重新规划 |
| 手机实时 | 选实时导航，授权使用期间定位与精确位置，重新规划并开始 | 从当前位置算路，导航指令、距离有变化；不自动开启眼镜显示 |
| 后台定位 | 实时导航运行中锁屏至少2分钟，再回前台 | 本次路线仍在；不能据此宣称所有系统／机型长期后台可靠 |
| 正常停止 | 点击结束导航，或退出导航页面 | 本扩展导航和后台定位停止，重新进入不自动恢复旧路线 |
| 模型回归 | 测试模型接口，再作一句无个人资料的眼镜问答 | HTTP IP 配置可用，文字／声音链路仍通过 |

拒绝定位时应提示，不崩溃；模拟不为保活启动真实 GPS。模拟退后台只有最多25秒系统短时额度，不能用它验证长期实时后台。

### 眼镜显示单独验收

本次不刷 TNV1 固件，沿用普通字幕研究通道：先开始真实高德**模拟**，等手机新鲜回调后，在镜片无录音／智记／提词／字幕／问答任务时点“开启眼镜字幕显示”。必须等待新会话 ACK。当前仍仅前台模拟开放，有 4分钟／80帧／15秒断流保护；手机实时导航可用不等于眼镜已支持安全实路指引。不要取消计时或循环重开规避保护。

## 8. 排错与回退

- **未链接 SDK**：安装的仍是旧 HTTP-only 包；只填 Key 不会增加二进制功能。
- **缺少高德资源**：必须用配套 `--amap-sdk-root` 预合并；不要只把新动态库注入旧 IPA。
- **地图空白／鉴权或算路失败**：核对安装后的 Bundle ID、iOS Key 类型、服务权限、网络及诊断错误码；不回显 Key。
- **搜索无结果**：主动提交搜索；城市限制可能过窄，搜索权限与地图权限分别核对。
- **旧引擎占用**：先结束其他导航；本扩展不强行销毁宿主拥有的 SDK 单例。
- **安装签名错误**：使用排除 macOS 元数据的新预合并输入，不删除原应用来碰运气。
- **模型不可用**：先独立跑模型测试；SDK 资源和高德 Key 不会自动修正模型网络／流格式。

回退使用保留的同标识旧 HTTP 包，以同账号签名覆盖；不叠加注入、不删配对、不刷固件。测试反馈只提供步骤、模式、错误码与脱敏截图，勿上传完整沙盒、私人位置、账号或 Key。

## 9. 本轮本机交付与自验结果（2026-10-10）

- [本机未签名更新 IPA](/Users/duriea/Downloads/TurboIO-http-navigation-20261010/TurboIO-HTTP-Navigation-unsigned.ipa)，约408 MiB，仅作用户自己的 Sideloadly 签名输入。
- SHA-256：`c6fb2e01a213b2a264ff70092b48be34269ecc0e4d664734983c2bd0fe58e47b`。
- [预合并报告](/Users/duriea/Downloads/TurboIO-http-navigation-20261010/local-http-preparation.json)：源程序与源配置保持不变，SDK资源齐全，不含用户Key或私人启动配置，无安装或固件写入。
- [真实 SDK arm64 构建日志](../build/http-navigation-build.log)：成功（exit 0）；动态库 ad-hoc 签名校验通过。编译仍有既有弃用接口／动态 selector 与第三方头文件注释警告，不宣称零警告。
- [全量回归日志](../build/http-navigation-tests.log)：全部通过（exit 0），含10项 Python 打包回归；另15项 Node 资源／合并／签名策略测试通过。
- [UIKit 预览编译日志](../build/http-navigation-preview-build.log)：成功（exit 0）；启动已有 iPhone Air 模拟器，三模式／规划门禁／地图选点与搜索交互三份自检报告均 PASS。这些是离线夹具，不是高德在线验证。
- 最终 IPA 二次检查：单份扩展，三份资源分别含188／6585／13个文件，保留location与蓝牙声明、HTTP策略，未打包额外AMap framework，无AppleDouble元数据。
- **未签名安装到真实 iPhone，未填Key，未验证在线鉴权／算路／真实眼镜显示；由用户按第5—7节继续。**

## 10. 自验命令与边界

```bash
bash official-addon/test.sh
node --test official-addon/amap-resources.test.mjs official-addon/package.test.mjs official-addon/macho-embed.test.mjs
bash official-addon/preview/build.sh
```

主机测试含三模式六个算路入口 mock、字幕节流与会话退出、定位后台策略、HTTP URL／流式／重定向、资源预检与签名元数据回归。真实 SDK arm64 编译与未签名 IPA 检查确认接入和打包；模拟器不链接高德，不能证明在线地图／搜索／算路。**最终 iPhone 安装、Key 鉴权、三种在线路线与眼镜显示仍须用户逐项实测。**
