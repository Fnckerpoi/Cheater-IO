# 官方导入接入试验：公开稿观察 + 一次确认接入

> **旧094800包禁止使用；只选R6修复版。** task20独立复审复现跨fixture授权/保存状态污染与晚提交lease不足，旧包已永久保留SUPERSEDED标记。R6源码经RED/GREEN、实施者自审、task20及lead独立复测通过后，在全新 `official-import-trial-20261014-101800-r6` 目录编译打包；不覆盖旧IPA。此前ZIP/成员字节测试仅证明打包，不代表旧源码门禁正确。已实测“日报测试”stagebase不受影响；R6真实官方消费/保存仍未真机验证。


## 实验范围，不是已验证官方 CRUD

本实验在用户已经实测的 AIHOT 日报阶段包上**再加独立 sidecar**，不替换原 addon 或“日报测试”模块。旧 HTTP/导航、AIHOT手动获取/全文来源/reading/TXT导出保持原字节。

新模块两阶段：

1. **Phase A：公开 fixture 原样观察。** 用户主动授权短时观察，走官方现有导入请求并选择明确公开合成 fixture。探针只认已核验的候选参数形状及自身公开文件；不得采集私人稿件、任意路径内容、账号或其他活动。完整原调用/结果仍交官方链处理。
2. **Phase B：一次功能接入。** 必须先完成 A 实际参数 profile 匹配、公开结果验证，并由用户确认官方已消费公开内容，才允许一次新的显式授权。候选仅向**同一官方现有 import 请求**供应公开 fixture 文件，不通过数据库或猜测 Dart地址创建稿。不需要再在系统 picker 里选文件，但可能仍需用户点官方保存。

启动只添加入口，**新 trial hooks 默认关闭**；没有隐式 arm、9点自动导入/发稿、批量导入、后台CRUD或去重。停止/过期/切后台按临时观察窗口规则结束；未知参数/签名/插件身份或phaseA失败一律不启用B。

这次初版 Phase B 只测试同一份公开7392 fixture，**不把 AIHOT 每日报文自动灌入官方库**，不替代最终需求。用户消费/保存反馈不等于代码拿到官方 docID，transport taskId/did 也不是 docID证据。界面显示“已返回/用户确认”不能冒称“保存成功”。

## 安装

沿用你已有的 unsigned IPA签名安装方式，重新签嵌套代码后签 app。**不要先删除现有 App**，避免本地稿件丢失；保留原 bundle `com.duriea.turboio.research` / 版本1.0.5 / build201。不要让签名工具修改身份；重要文稿先用已有安全方式备份。

本任务未自动重签、安装、启动、attach debugger，也未读取手机容器。新实验面板布局、官方请求匹配及保存/回读都尚未真机验证。

## 公共 fixture 与验收步骤

请只使用明确公开的合成文件/稿件；不要选择任何私人文稿，不需要删除私人稿件。

这是本次 trial 模块生成的**新公开 fixture**，不是先前无效保存探针的固定正文，不要混选旧文件。入口为 **“导入验证”**，面板标题 **“导入验证 · 公开7392单稿试验”**；已有 **“日报测试”** 保留。

```text
TurboIO 公开导入验证 7392
这是一份不含个人资料的公开测试稿。
第一段：官方文件导入返回之后，请核对标题与正文。
第二段：这不是保存成功回执。若出现编辑器，请由用户保存并核对官方稿件列表。
尾部标记 7392 END
```

初版只用模块自己创建的随机临时目录/公开 TXT。正文不能混用日报、私人文稿或近似变体。若 UI 提示 profile/返回结构不匹配，停止并导出允许的摘要；不要为了促成成功重试私人文件或放宽规则。

**R6修复版规则（独立源码复审通过）**：创建新fixture或重新授权A会清除旧profile、消费/保存确认及旧请求/返回摘要，不能沿用上一次A授权给新文件。清理自身文件后也不能复用旧A；B已消费的一次会话预算不能靠重新创建/清理fixture重置。同一App会话最多一次B实际提交，不自动重试。此规则只适用于R6新编译包，不意味着旧094800包已经修好。

**A→B 必须在同一次 App 会话完成**：profile与公开fixture关系仅在内存，不强退/重启App，也不要在A后点击清理。关闭实验面板可以继续回官方操作，但后台仍会停止当前观察窗口；未完成A时必须保持B关闭。重启后的持久化检查放到B完成、结果核对之后。

Phase A：点击 **“生成/查看公开TXT”** → **“共享TXT：A阶段保存到文件”**，把本次随机文件保存到“文件” → **“A：授权一次原样观察（120秒）”** 并明确同意 → **“关闭面板（仍可回官方操作）”**，进入官方导入流程，只选择该新公开 TXT → 回面板查看摘要、在官方编辑器核对7392全文 → 有真实A匹配后才点 **“我已核对公开标题/正文进入官方编辑器”** 并人工确认。观察为120秒窗口，过期或切后台结束；若系统动作导致后台停止，这是未观察到，不要改为长期监听。

Phase B：A实际profile及人工消费确认成立后，点 **“B：仅本公开7392稿免选TXT试验”** 并明确同意 → 关闭面板 → 用户发起相同官方导入请求 → 模块尝试供应自己的公开 fixture，不再让用户选文件 → 如官方仍有保存按钮，用户自己保存。最多一次功能供应，失败不自动重试，不改其他稿件；UI显示了B按钮不等于未完成A时允许接入。B重新返回公开结果后，也须在编辑器核对全文，并再点 **“我已核对公开标题/正文进入官方编辑器”**：B开始会清除先前A的消费/保存确认，不能沿用旧确认。随后用户在官方保存、核对官方列表/标题/正文后才点 **“我已保存并核对官方列表/标题/正文”**，此值只是USER_CONFIRMED_SAVE，不是官方API回执。

停止与摘要查看/导出均为显式动作；发生不匹配/未知结果，请点 **“停止观察/不取消官方操作”**，随后 **“导出脱敏摘要”** 给出实测反馈。摘要不输出raw参数、正文、私有路径、原始identifier/docID；只含允许的参数profile和自身公开fixture的随机basename/size/SHA，不是私人文件元数据。公开TXT作为用户主动分享的测试文件与摘要分开。

**R6公共文件规则**：**“完成并清理自身公开文件”** 只处理模块自身随机临时目录/文件，不清理用户“文件”副本或官方库。B公共文件lease从**实际提交时**至少重新保留3600秒，不沿用fixture创建时剩余TTL。没有用户保存确认时，不可提前删除；即使TTL已过，只要native pending清除仍未知，仍保守保留公共文件、拒绝清理，不自动sweep，也不将超时当保存失败或假成功。

在pending已确认清除且lease等条件允许后，才可显式清理；未知结果不重试功能供应。不要在A后清理使同一次B的fixture失效。清理被拒绝时导出摘要反馈，不手工扫描/清理任意临时目录。R6源码已获独立复审通过，实测只选下述新R6包；**旧094800包禁止继续使用**。入口在normal keyWindow左侧safeArea.top+124（旧“日报测试”为+80），不用Alert overlay遮挡系统picker；长按可隐藏入口并停止观察。

**单独检查官方保存与回读**：确认官方编辑器全文、保存后官方列表；B完成并核对结果后，才做重新打开/重启持久检查。B可能通过官方新稿界面创建**第二份同名公开7392试验稿**：本轮尚未实现去重，仅在用户阅读风险并明确确认B后，允许这一次公开副本实验。不要额外手工发起第三次导入或自动重试；不得覆盖/删除已有稿件，更不能拿私人稿测试覆盖。若官方出现覆盖/同名冲突提示，用户取消并导出摘要反馈，不自动处理冲突。不把公开编辑器显示或用户反馈映射为runtimeDocID。

## 零记录与失败的正确解释

- 未捕获：只说明窗口内未观察到允许的匹配，可能是未走目标入口、窗口停止、参数形状不符，不代表保存失败。
- 有 profile：只证明候选官方请求及公开结果匹配，不证明独立官方CRUD接口。
- B 接入返回：只证明候选供应结果，不代表官方保存/去重完成。
- 官方列表/回读确认仍需独立用户检查；实验包不宣称无人值守自动入库成功。

## 打包保全与复现

精确源 stagebase：

`official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa`

SHA256：`554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`

原 Runner SHA256：`6939401111098233c70374cd68bcc82c287882e3820d4d6ce8195051ff37d920`；UUID `748FD301-DA60-3095-A244-9BD0558FACA5`。

保留旧 addon `7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503` 与旧stage sidecar `7ee752d3f79aa09b5c3c14fccd3a186a4a535c62e2415fe0c78b651d0c8b3224` 原字节；Info.plist、全部旧 load commands/依赖顺序与 bind ordinal 不变。只在经验证空 header padding **尾部**追加 `@rpath/TurboIOOfficialImportTrial.dylib`；Runner其他字节与尺寸均保持。输入Runner已无签名command，不再改变其旧签名区域。

新增 trial dylib及无凭据阶段标识；必要签名seal/profile仅按路径白名单清理（此 unsigned stage输入预计无需移除）。其他所有 ZIP members逐SHA验证byte-identical，签名仍须用户现有流程生成。原嵌套binary签名loadcommands/blob不清改，不把旧签名视为有效新包签名。

新packager明确使用**stagebase专属**输入hash/guard/loadname/marker，不修改旧packager的LOAD全局以绕过门槛；仅复用已测pure MachO parser。源worker确认ready/冻结后才真实三源arm64 iOS15链接；新build目录记录六份h/m pre/postcompile SHA，源在编译期间改变即拒收候选。

```sh
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/official-import-trial-package-tests.py
bash official-addon/build-official-import-trial.sh /absolute/new-link-directory
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/package-official-import-trial.py \
  --source /absolute/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa \
  --dylib /absolute/new-link-directory/TurboIOOfficialImportTrial.dylib \
  --out /absolute/new-trial-package-directory
```

Package tests覆盖exactbase fail-before-output、unsigned/iOS15/installName/entry/systemdependency、FAT/非arm64/坏commands、无padding/非空padding/既存trial/缺stage/有签名拒绝、ZIP路径穿越/重复/符号链接/特殊文件/加密、notoverwrite、完整synthetic pipeline的权限/allowed member diff/oldaddon+stage+App+Flutter+opaque资源字节/manifest/CRC。实际基线所有Runner @rpath/@executable_path依赖均解析通过。

## 已撤回旧候选的打包记录（禁止安装，替代包待修复复审）

产物：

`official-addon/build/official-import-trial-20261014-094800/TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa`

- 大小：**425,936,192 bytes**。
- SHA256：`787689ebd9dbe65b96fcf56fc13f671b93846252bcd7f8df158bed6330e23027`。
- 新sidecar：139,128 bytes；SHA256 `330fe6cbc7b72ad8286e577408ea1501ed0993bb84a07d20257d397518f5bd55`。
- Member diff：**新增2、修改1仅Runner、移除0、9817原成员字节一致**。旧stage/addon/App/Flutter/所有旧依赖保持；完整manifest是同目录 `official-import-trial-manifest.json`。
- Runner新SHA：`3af9f3f15136aa4578146d8d3602f6c83fe0d33ca945525dd06844248e4f50d3`，原UUID/所有旧commands/dependencies顺序保持，新依赖仅追加尾部。
- 源stagebase完整SHA始末均 `554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863`；ZIP CRC全通过，Runner/newdylib mode `100755`。
- 生产无 `TIO_IMPORT_TRIAL_TESTING`，`nm` 证实没有 `TIOImportTrialTestBind`，有 `_TIOOfficialImportTrialInstallUI`；启动只UI/no新增activehooks。

Lead批准并收到sourceworker六源冻结后，现场真实执行：

```sh
bash official-addon/build-official-import-trial.sh /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-094800-link
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/package-official-import-trial.py \
  --source /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa \
  --dylib /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-094800-link/TurboIOOfficialImportTrial.dylib \
  --out /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-094800
```

实际结果：production thinarm64 dynamiclib链接成功、`LC_BUILD_VERSION` platform2/minos15.0，installName准确；Foundation/UIKit/CoreFoundation/libobjc/libSystem共5个系统依赖，无oldaddon/oldstage链接、无dynamic_lookup、无新LC_CODE_SIGNATURE。没有编译warning/error；ld仅提示显式 `-undefined error` 已deprecated（非链接失败）。UTF16“导入验证”文字实际在binary内。六源pre/postcompile以及交付前现值SHA均一致，`build-source-sha256.json`保留完整绑定。

独立再次执行 package **8 tests PASS**，以及如下Foundation tests两个**PASS**（Host测试宏只用于单独test executable，不用于生产dylib）：

```sh
xcrun clang -fobjc-arc -fblocks -Wall -Wextra -Werror -framework Foundation \
  official-addon/OfficialImportTrialCore.m official-addon/OfficialImportTrialCoreTests.m \
  -o /tmp/tio-import-core-tests-integrator-20261014
/tmp/tio-import-core-tests-integrator-20261014
xcrun clang -fobjc-arc -fblocks -Wall -Wextra -Werror -DTIO_IMPORT_TRIAL_TESTING=1 -framework Foundation \
  official-addon/OfficialImportTrialCore.m official-addon/OfficialImportTrialHost.m official-addon/OfficialImportTrialHostTests.m \
  -o /tmp/tio-import-host-tests-integrator-20261014
/tmp/tio-import-host-tests-integrator-20261014
```

Core覆盖严格profile/未知参数/公共hash/未A与未confirm拒B/超时与reentry/取消/脱敏/symlink/lease/不声称DocID或saveAPI；Host mock覆盖原参数透传、正常官方continuation/handleResult清pending、helper旁路仅一次、错误回包及异常不假成功、busy未知fallback、late/stalegeneration、IMPrestore/later-swizzlerdrift。真机是否匹配实际官方入口、picker/分享/临时copy时序、官方消费/保存/列表回读、旧导航HTTP回归都**未验证**，synthetic通过不是设备成功。

上述 `official-import-trial-20261014-094800` 已标为 **SUPERSEDED-NOT-FOR-INSTALL**，不再是可交付最终包。旧IPA保留仅用于复现/完整性证据，其SHA未修改；旁附标记说明task20两个源码安全门禁缺陷。旧Core/Host tests PASS不足以证明跨fixture授权及晚提交lease正确，8个package tests仍只证明打包边界。此旧记录保留作历史证据，批准的R6替代包及新增回归证据见下一节。未commit、重签、安装、启动或手机attach。此前日报stagebase不受影响；公开fixture实验也不代表最终AIHOT新闻自动入库集成完成。

## R6唯一当前产物、修复回归与真实重编

**只用这个新目录，不使用旧094800包：**

`official-addon/build/official-import-trial-20261014-101800-r6/TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa`

- 大小：**425,936,693 bytes**。
- SHA256：`6062ab1770702b7298881486489597a55a5c6e18d78e599040c05b32abd5f051`。
- 新trial dylib：139,352 bytes，SHA256 `6f0a72dfd82f25a7340ce63564bc44cb09db2b3875e8a6be22bb265c8c56b208`。
- `official-import-trial-manifest.json`：added2、changed1仅Runner尾追加同一个trial加载command、removed0、9817原members byte-identical，ZIP CRC PASS；stagebase `554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863` 始末不变，旧addon/stage/App/Flutter hashes仍与上述基线一致。
- production entry `_TIOOfficialImportTrialInstallUI`，thinarm64/LC_BUILD_VERSION platform2/minos15.0、5个系统dependencies，无TestingBind、无LC_CODE_SIGNATURE；启动onlyUI，不自动arm。不是仅syntax-only验证。

独立修复验收来源：task20 targeted复测 **POST_FIX_NEW_FIXTURE_BYPASS=0**、新A摘要清理、提交起 **FULL_HOUR**、pendingUNKNOWN仍保护文件、sessionSPENT预算保持均PASS；lead另外独立编执行Core/Host PASS，才批准R6冻结及新包制作。旧测试曾未覆盖这些门禁，因此旧包保留撤回标记，不因新测试PASS而重新启用旧包。

R6源码冻结SHA：Core.m `fcd947ec1c80c1e8a1a341f87aa4601c676b06c427c302437de21cb2bb850d48`；CoreTests.m `5c94f896c18a94c23b5a68010e89ecd84c21c5319e4695f675fb6b656d26de07`；HostTests.m `dec01003c6f7c61a10efeabf4d9a20542c8e547009ce2275f44c0cd2db0db7d1`。Host/UI生产源保持上一轮冻结不变；全部六源记录于新目录 `build-source-sha256.json`，实编pre/post及交付前现值逐SHA一致。

现场新目录真实执行（从原stagebase，不以旧trial为base）：

```sh
bash official-addon/build-official-import-trial.sh /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-101800-r6-link
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/package-official-import-trial.py \
  --source /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/aihot-daily-stage-20261013-111620/TurboIO-AIHOT-Daily-Stage-unsigned.ipa \
  --dylib /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-101800-r6-link/TurboIOOfficialImportTrial.dylib \
  --out /Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/official-import-trial-20261014-101800-r6
PYTHONDONTWRITEBYTECODE=1 python3 official-addon/official-import-trial-package-tests.py
```

链接exit0且sourcefreezePASS（无编译warning/error；ld仍仅提示显式-undefined error选项deprecated）。重新8package tests PASS；包装执行者也独立运行新增R6 Core/Host Foundation-Werror tests两个PASS，输出 `/tmp/tio-import-core-tests-integrator-r6-20261014` 与 `/tmp/tio-import-host-tests-integrator-r6-20261014`（命令与上节相同，仅使用R6新源及新test执行文件名）。R6 IPA完整file SHA另行复核一致。未签名、安装、启动、phone attach，也未真机验证新A/B。

## 用户反馈与下一步门槛

当前未收到新试验包的A/B真机结果。此前“步骤1–6顺利完成”仅针对旧AIHOT阶段包，不移植为本试验通过。

请在同一次App会话里完成A/B后提供脱敏摘要，并用以下字段说明实际行为（不需要私人稿件或完整列表截图）：

```text
试验包安装与“导入验证”入口：成功 / 失败
A结束状态：粘贴实际state
A：官方编辑器中的7392全文正确：是 / 否 / 未进入
B结束状态：粘贴实际state
B：是否仍弹出系统文件选择器：是 / 否
B：官方编辑器/正文是否正确：是 / 否 / 未进入
B：是否需要手动点保存：是 / 否 / 不确定
B：官方列表能否找到并重新打开此公开稿：是 / 否
完成B后重启回读：正确 / 不正确 / 尚未检查
```

A未匹配或未确认消费时，不进入B；出现任何未知/不匹配时只停止并导出摘要，不重复创建或选择私人文件。收到真实profile和行为结果后，先核对候选接口实际消费，再决定是否接入新闻稿；没有用户反馈不能把代码、反编译或打包测试升级成官方入库成功。
