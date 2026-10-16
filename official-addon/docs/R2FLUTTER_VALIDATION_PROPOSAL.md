# Dart AOT 专用解析候选：隔离验证提案（尚未批准安装）

## 目的与当前结论

目标是继续恢复官方提词器 import/parse/save/read 的真实 Dart 业务调用链，而不是将已有 FilePicker 回调替换称为无UI官方入库。用户硬要求：完全无页面跳转、不逐篇点击、每源独立稿、未知不重复写入、不直接写数据库、不伪造合法稿件ID。

当前 REA 6.4.0 实际 tools/list 有170项，无专用Dart Function/Code/ObjectPool decoder。open_binary没有关闭默认analysis或限定范围参数；现有 Ghidra launcher启动默认自动分析。因此不能通过通用instruction查询声称已经恢复Dart对象，也不再重复三次失败的全App Ghidra路线。

## 候选及固定来源

候选：radareorg/r2flutter。文档审阅固定到commit `05c0616b3782422978cd0befc2b30af1c59ca41a`，后续下载必须独立验证该commit身份，不能默认用floating main：

- README： https://github.com/radareorg/r2flutter/blob/05c0616b3782422978cd0befc2b30af1c59ca41a/README.md
- 支持矩阵： https://github.com/radareorg/r2flutter/blob/05c0616b3782422978cd0befc2b30af1c59ca41a/doc/support.md

已从primary文档核实：接受iOS .app及Mach-O64/fat arm64；版本表列Dart3.11.5及snapshot hash `78da37fed6bf1489361a312568249f3f`，与本地目标已有快照证据匹配。README明确它是metadata/analysis aid，不是Dart源码反编译器；仅make后存在standalone `bin/r2flutter`，无需执行user-install或sudo install。

重要限制：现代OBJECT_HEADER naming scanner仅支持CWS4 compressed pointers。本目标features含`no-compressed-pointers`，属于full-width路径；其精确Function-owner/Code/ObjectPool关联恢复效果、该样本覆盖和运行成本仍UNKNOWN。支持版本表和header解析成功不能提升为业务函数映射成功。禁止opt-in namepool顺序猜名，以及强制版本以制造表面匹配。

Blutter upstream明确当前仅Android libapp.so arm64，iOS binary仍TODO；macOS host可构建不能推出接受iOS输入，因此不作为当前直接安装推荐。

## 本机只读前置检查

本轮执行`command -v`：r2/radare2/r2pm/pkg-config/cmake/meson均不在当前PATH；clang和make分别存在于/usr/bin/clang、/usr/bin/make，xcrun version72。此检查不等于遍历全机证明未安装，只能说明当前PATH不可直接使用。未下载、编译或执行候选代码。

README要求radare2 >=6.2.2（或6.2.1 git build）及development headers，并从r2 -H取得编译flags。具体radare2来源commit/release、静态/共享依赖、构建是否离线及额外工具需求仍须在授权后先审阅确定，不能提前声称只有一个小工具依赖。

## 拟授权范围（尚未执行）

仅在用户明确同意后：

1. 创建 `/Users/duriea/Tools/REA/r2flutter-validation-<timestamp>/`，radare2和r2flutter源、prefix、build、日志和结果均置于该目录。只进程级PATH/library配置，不改shell rc、Homebrew、全局MCP、user plugin或系统目录；不用sudo/user-install。
2. 审阅固定源的loader/CLI/Makefile及full-width代码路径、依赖与网络行为，记录精确来源和digest。若需要额外未列出的工具或改变权限则停下说明，不扩大安装。
3. 尽可能先用明确同Dart/iOS/full-width的公开自有fixture验证真实Function-Code-owner关联。无法取得该fixture不伪称覆盖，也不把header成功当通过。
4. 对本地原始App SHA `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`，只读解析，不启动目标/isolate，不上传IPA或二进制；先一次header核对，单次<=45秒。只有full-width路径有意义才增加一次明确预算的元数据/指令表检查；输出limit不是计算成本limit，先读代码确认。
5. 禁用-AAA全自动分析、namepool猜名、动态执行/手机attach/Frida、任何DB读写、私人稿件和凭据。总验证预算设定为20分钟，编译或解析超时立即记录停止，不反复扩大同一预算。
6. 只有真实metadata/object引用与Code owner关系和有效__text地址相互印证，才进入官方import/save/read局部研究；恢复名字不等于native可调用，更不等于官方保存闭环。

## 退出与交付

不兼容则记录具体feature/失败位置并停止UNKNOWN，不发“全自动版”IPA，不覆盖当前App/包或遗留文件。兼容也只说明下一步逆向入口可用，仍需业务参数/对象上下文/合法ID与官方回读证据。

用户已通过问题 `r2flutter_isolated_validation_consent` 明确选择“同意隔离安装与有界只读验证”。授权开始UTC `2026-10-14T07:47:50Z`（epoch1791964070），共同绝对截止 `2026-10-14T08:07:50Z`（epoch1791965270）；不能每次操作重置20分钟。唯一新根 `/Users/duriea/Tools/REA/r2flutter-validation-20261014-154750/`，构建与独立源码审阅分派task10/task9。目标解析仅在审阅通过后另由lead批准执行，单次45秒上限。此授权不包含手机操作、运行目标App、全局安装或上传IPA。
