# AA UI 版本签名安装与设备检查

## 已安装版本

用户明确授权签名安装本轮 UI 候选。输入 unsigned APK SHA-256 为 f981333f2405fc78a0dda201217034c18369f53d3a06bafd234dc16e682d5eca。签名后文件为 artifacts/embedded-aa-ui-final-20261029/TurboIO-AA-ui-aligned-signed.apk（446719293 字节），SHA-256 b2dbec76d3ea4e9c01f34defb8c41b5d7e52b4b4e5052ddc03470bbbc75ffe22；apksigner sign/verify exit 0，证书 SHA-256 1b070381fc578193779539456722e096b21fe447959000952dcd488842d8f8c3。

adb install -r --no-streaming 返回 Success，install_exit=0；后续设备 sha256sum 与本地签名 APK 一致。包名 com.rayneo.venus.pub（1.0.5）；设备 firstInstallTime=2026-10-27 08:32:59、lastUpdateTime=2026-10-29 11:19:31。未卸载或清数据。不能仅凭覆盖安装保证所有配置原样：实际可见账户已登录、会话列表和电脑候选加载成功，但未直接读取私有配置（run-as 拒绝 package not debuggable）。

## 已验证范围

- 强制停止后启动 MainActivity，经真实 Turbo IO 入口进入 AA 工作台，PID 28718 存活，检查时 crash buffer 未见所筛选的应用崩溃记录。
- 工作台为深色背景、圆角卡片与黄绿色强调色。展开后可见六卡，向下滚动可见执行权限与保存并启用。
- 电脑设备候选弹窗成功加载三项真实候选；没有选择候选或保存配置，没有派发任务。
- Agent-Anywhere设置更名生效；页内显示已登录账户状态、任务同步和恢复操作，不再显示六张配置卡。
- 截图位于 artifacts/embedded-aa-ui-final-20261029/device-workbench.png、device-cards-expanded.png、device-sixth-card.png、device-settings-page.png。

## 验收发现与修复中事项

当尚未选择电脑/运行时等父级时，可点击子级候选卡，宿主拒绝 INVALID_TARGET，而页面只显示笼统失败。该可操作提示缺陷已交 AA 成员修复：禁用未满足父级的卡并明确提示先选父级、映射 INVALID_TARGET、提供刷新配置入口并清理普通旧错误。

先前称“折叠导致错误”的推断不能作为根因：后续操作复用了滚动前的坐标，实际页面已滚动，点击可能落在不同卡片。可靠证据是未满足父级的子级请求被拒绝及通用文案，不是已证明折叠生命周期故障。

## 提示修复版安装与复测（已完成）

修复版真实 Gradle：89 项零失败（ConfigurationStateTest 22、WorkbenchPolicyTest 9），来源门禁 exit 0；独立新目录装配与逐成员审计 exit 0（3976 成员字节不变、变化仅 Manifest/classes4.dex、新增仅两 asset、剥离仅三签名项、payload 7406c2d4…c201 且零原生库、Context 回调与两 Activity 导出策略保留）。

签名包 `artifacts/embedded-aa-ui-affordance-20261029/TurboIO-AA-ui-affordance-signed.apk`，446,723,389 字节，SHA-256 `48bd2c96ef6ea1bae6e98bef56f03e81864ff67c42a2634e5c6c804bb0cf95d4`；apksigner sign/verify exit 0，证书 `1b070381…d8f8c3`。安装过程中华为弹出二级安全提示与本人身份验证：先按真实 bounds 点击“继续安装”与风险确认复选框，再从设备拉取 base.apk 核验其证书与本轮签名一致后，由用户本人完成系统身份验证。安装任务 pwsh-10949234ac5c45f3 返回 Success、exit 0；设备 `lastUpdateTime=2026-10-29 11:57:52`、`userId=10271` 不变，设备端 `sha256sum` 与本地签名包逐字符一致。未卸载、未清数据、未绕过身份验证、未重发安装。

冷启动复测（force-stop 后进入，PID 5066，crash 行为 0）：

- 展开调度配置后，未满足父级的项目/DSH运行时/Session/模型与推理/执行权限五卡均显示“请先选择电脑设备”，点击被禁卡不打开候选弹窗、不产生错误、面板保持展开，修复前缺陷已消除。
- 根卡“电脑设备”仍可加载真实候选（3 项），无错误提示；关闭候选未误改选择（仍为“请选择”）。
- 滚动可见「刷新配置」「保存并启用」及“选择只修改草稿，保存成功后才影响任务目标。”提示。
- 证据：verify-cards-expanded.xml/png、verify-disabled-tap.xml、verify-root-chooser.xml、verify-scrolled.xml/png。

## 用户完成配置后的只读回读

用户在手机上完成六卡选择并保存后，lead 仅做只读回读（force-stop 冷启动 → Turbo IO → AA 工作台 → 展开调度配置 → 点「刷新配置」与列表 Refresh），未再改动任何选择、未派发任务。

- 首次冷启动展开时，六卡显示为裸 ID（conn_7NFcgnwVILr2kg、rti_NlmB64Derg8WgEKv、sess_dsh_6f324ae3d7727ef52eb62aa4、dsh:model:…、dsh:permission:…），并出现 “Could not load sessions. Refresh to try again.” 与“候选加载失败，已保留当前选择。”。这与宿主引导期某级目录请求失败一致；关键点是**已保存绑定未被清除或改写**（值仍为原 ID），符合“失败保留绑定”的契约。
- 点「刷新配置」后全部恢复为可读名称：电脑设备显示主机名、项目显示 “Turbo-IO”（**不含路径**）、DSH运行时 “DSH / dsh · running”、Session “集成AA并优化工作台界面 / sess_dsh_6f324ae3d7727ef52eb62aa4 · running”、模型与推理 “难题给强模型，简单的给便宜模型 · routing group · Remote magpie”（推理档位 High 单独成行）、执行权限 “danger-full-access”。会话列表错误同时消失。
- 证据：rb-cards-top.xml、rb-cards-bottom.xml、rc-after-refresh.xml、rc-after-listrefresh.xml、readback-final-top.xml/png、saved-config-readback.png；PID 12925 全程存活，crash 行为 0。

## 遗留缺陷与风险

- **冷启动引导降级**：草稿引导期一次目录失败会把已保存值降级为裸 ID 显示并残留一条通用错误，需用户手动「刷新配置」才恢复可读。功能不丢，但首次体验不一致，属可修复缺陷（建议引导失败时自动重试一次或保留上次成功名称）。
- **执行权限为 danger-full-access**：该档位允许远端无限制执行，请确认这是你的本意；如需收敛，可在执行权限卡改选更受限档位后重新保存。

## 真实任务派发前的回环风险（未派发）

准备按用户选择发起只读测试任务时，进入绑定会话 `sess_dsh_6f324ae3d7727ef52eb62aa4`（标题“集成AA并优化工作台界面”）详情页，发现其历史消息中包含 lead 上一轮的助手输出原文（“已绑定会话行可见，且页面显示「已绑定会话」提示。进入该会话详情，确认发送/查询控件是否按绑定状态启用。”），其下另有一条“执行了 1 条指令”活动行。证据：task-detail-open.xml 节点 21/22/26。

该现象与“此会话即当前助手对话所在会话”一致：若如此，再向它派发会把消息注入本对话。用户此前选择“发起只读型测试任务”时并不知道这一前提，因此**已停止派发，未发送任何任务、未改动配置**，等待用户确认目标会话是否安全。此为未验证项，不构成端到端验收。

## 当前边界

配置已保存并回读确认生效；未发起真实任务、未验证语音端到端、未验证旋转/进程重建下的草稿恢复。旧 OAuth/Context 修复记录按各自产物引用（contextfix 签名包 a10aa845…、oauthfix 签名包 656ad6d0…），不得与本轮 `48bd2c96…` 混同。
