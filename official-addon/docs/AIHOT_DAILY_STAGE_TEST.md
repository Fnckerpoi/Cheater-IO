# AIHOT 日报阶段测试 IPA

## 阶段范围

这是实际功能阶段包，不是官方保存诊断探针，也不是最终自动化成品。

已提供：用户手动获取 AIHOT 当日日报；手机查看全文、日期、来源链接及提词 reading 预览；明确操作后导出 UTF-8 TXT，供官方提词器**手动导入**。

**官方自动入库、09:00 自动发、去重都未实现且禁用。** 不宣称已验证官方保存接口；阶段功能不替代最终需求。不自动获取、不自动导出、不发眼镜消息、不改官方 DB，也不删除现有文稿。

## 安装与一次实测

1. 使用产物目录中的 `TurboIO-AIHOT-Daily-Stage-unsigned.ipa`，沿用你已经使用的签名安装方式，包含嵌套 frameworks/dylibs 的签名后再签 app。**不要先删除现有 App**，以免本地稿件丢失；同 bundle 覆盖更新是否保留数据以你现有签名流程为准，重要稿件先用现有安全方式备份。
2. 保持原 research bundle `com.duriea.turboio.research`，不要在签名工具中改 bundle/version/build。阶段入口严格匹配 1.0.5 / 201；修改身份会使入口关闭或破坏原扩展兼容。
3. 打开 App 后，点击左侧上方独立入口 **“日报测试”**，面板标题 **“AIHOT 日报 · 阶段测试”**。
4. 点手动获取按钮，等待结果；获取中可以取消。取消后不得把迟到响应显示成成功，也不自动重试。
5. 查看当日日期、完整正文、来源、reading 预览。过期、缺日报、抓取失败等错误应明确显示，不能用昨天内容冒充今天。
6. 确认正文后选导出 TXT，通过 iOS 分享菜单保存到“文件”；再进入官方提词器，沿用其文件导入流程手工导入。导出不等于官方入库成功，官方列表显示与回读须你自行确认。不得借此要求删除任何私人稿件。

未签名包不能直接安装；本任务没有自动重签、安装、启动、attach debugger 或访问手机容器。

## 基线保护与签名处理

指定输入原 IPA SHA256：

`de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24`

原 `TurboIOPrivateAddon.dylib` SHA256：

`7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503`

不重编、不替换旧 addon，导航/HTTP 修复及所有原 dependencies 保留。Info.plist、bundle、version、build 不修改。

只在原 Runner 的验证过的空 header padding 中新增 `@rpath/TurboIOAIHotDailyStage.dylib` load command；保持 Runner UUID、所有旧依赖顺序（保持 bind ordinal）、所有其他旧 load commands 原字节及非 header/原签名 blob 区域原字节。Runner 原 `LC_CODE_SIGNATURE` 移除，失效签名 blob 零填，文件尺寸不变，未截断或搬动 section 数据；必须之后由用户签名流程生成新签名。

新 module 是 thin arm64 iOS15、`@rpath/TurboIOAIHotDailyStage.dylib`、独立 Foundation/UIKit sidecar；编译禁用 adhoc signing，没有 undefined dynamic lookup。它不链接旧 addon，也不复制官方 probe 到包中。

只移除 ZIP `_CodeSignature` seal resources 和 `embedded.mobileprovision`。**旧 addon/App.framework/Flutter.framework 以及其余嵌套 Mach-O 的签名 load commands/blob 保留原字节**；因此这是待重新签名的 IPA，不是已有效签名的安装包。原 CodeResources 已移除，用户已有签名流程必须重新签嵌套代码并生成新 seal；不得把“字节保留”误认为旧签名仍有效。

新增 `TurboIOAIHotDailyStage.json` 仅包含阶段名称与三个 disabled 标志，不包含配置凭据。其他所有未改 ZIP members 均逐成员 SHA256 比较，包含原资源和依赖；不解释或输出其私有配置内容。原 IPA 不上传、不改动。

完整 added/changed/removed 路径、核心 binary hash、新包 SHA256/大小、原包未变与 ZIP CRC 结果记录于独立产物目录的 `stage-package-manifest.json`。未输出冗余的全部 unchanged 清单，只报告计数。

## 可复现构建与验证

先由 source worker 确认三源 ready，然后真实链接，而不是仅 syntax-only：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/aihot-daily-stage-package-test.py
bash official-addon/build-aihot-daily-stage.sh /absolute/new/build-directory
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/package-aihot-daily-stage.py \
  --source /Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa \
  --dylib /absolute/new/build-directory/TurboIOAIHotDailyStage.dylib \
  --out /absolute/new/package-directory
```

打包安全单测包含 ZipSlip/绝对路径/重复 entry/符号链接与特殊文件/加密 ZIP、新输出不覆盖、权限保留；Mach-O FAT/非 arm64/无 padding/非空 padding/错误 command 与 signature bounds、UUID、完整旧 commands/dependency 顺序及非 header 字节保护；完整 synthetic ZIP pipeline 验证 allowed member diff、原 opaque 资源字节、可执行权限、manifest、CRC。

## 本次最终产物与现场执行

唯一最终包（先前 `110220` / `111120` 候选均有 superseded 标识，不用于交付）：

`official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`

- 大小：**425,901,987 bytes**（约 406.17 MiB）。
- SHA256：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`。
- Sidecar：193,376 bytes；SHA256 `7ee752d3f79aa09b5c3c14fccd3a186a4a535c62e2415fe0c78b651d0c8b3224`。
- Runner UUID `748FD301-DA60-3095-A244-9BD0558FACA5` 保持；旧依赖顺序全保留且全部基线依赖解析通过。
- Member diff：**新增 2、修改 1（仅 Runner）、移除 178（签名 seal/profile）、9815 原成员字节一致**。具体路径与 hash 见同目录 manifest。
- 原 addon SHA256 不变；App.framework/App `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`、Flutter.framework/Flutter `488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0` 均与基线一致。
- 原 IPA SHA256 仍与上述指定输入一致；输出 ZIP CRC 全通过；Runner/new dylib 权限均 `100755`。

实际执行的最终 build/pack 命令（项目根目录）：

```sh
bash official-addon/build-aihot-daily-stage.sh /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620-link
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/package-aihot-daily-stage.py \
  --source /Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa \
  --dylib /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620-link/TurboIOAIHotDailyStage.dylib \
  --out /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620
```

本轮检查：package tests **14 tests PASS**；`bash test-aihot-daily.sh` **209 checks / 0 failures**；独立 Foundation 编译执行 StageCoreTests **47 checks / 0 failures**；真实 arm64 iOS15 dynamiclib 链接成功（无编译 warning/error，ld 仅提示显式 `-undefined error` 选项已 deprecated，不使用 dynamic_lookup）；`otool -L` 仅 Foundation/UIKit/CoreFoundation/libobjc/libSystem；`LC_BUILD_VERSION` platform=2/minos=15.0；`nm` 存在 `_TIOAIHotDailyStageInstall`；编译后 `日报测试` UTF-16 literal 与入口 accessibilityIdentifier 均核验存在；无 `LC_CODE_SIGNATURE` 的新 sidecar。

最终链接使用冻结源码：Daily.m `0e5dbd31e6504c5184a0283c4cd4b4592188248170318756edc78e4903997df1`；StageCore.m `173980b4867e15a23313da22a2e814154e16ee51954b62130e050226d2e26c12`；StageUI.m `267be3df1034de606b5bcce6dbf018899b5a954eeef65647829ab6e77699982e`。分享 TXT 生命周期修复包含在此冻结源码中；切后台取消取稿但不删除仍被系统 activity 消费的指定 TXT，completion 只清理其自身 lease。

## 用户真机验收反馈

用户明确反馈：“按照验证指引1-6步骤，顺利完成。”本会话交付指引包含签名安装、打开“日报测试”、获取当日日报、全文/阅读稿/来源预览、TXT保存到文件、官方手动导入及准备提词与眼镜阅读。上述阶段手动链路记为**用户真机实测通过**；证据来源是用户反馈，不是调度者设备日志或自动化观测。不再要求重复验证这条已通过的手动链路。

该反馈不覆盖专项失败/边缘分支：取消与迟到回调、分享中切后台、跨日/未发布/服务端错误、横屏与极端字体布局，以及旧导航/HTTP的独立回归仍未收到逐项实测结果。没有由步骤1–6推断重启后新日报保持或这些边缘场景全部通过。

**官方自动入库、09:00自动发送、10分钟失败重试及持久完成防重复仍未实现。** 手动导入和阅读成功不等于程序已经具备官方保存/回读接口，亦不作为最终自动投递验收。
