# AIHOT 官方提词器稿件契约：有界 Dart AOT 静态调查

## 结论与状态

**Blocked：本次未恢复官方 Teleprompters 保存/回读的真实可调用契约。不能据此实现自动写入官方持久列表。**

三轮检查确认了公开二进制、Dart snapshot 身份、候选名称和静态工具能力，但没有建立 `Dart Function → Code → entry point → 调用者/对象池 → 参数/序列化 → 列表回读` 的证据链。没有目标函数地址、目标代码引用或真实 schema。因此下文名称只能作为候选线索，不能当作 API、数据库字段或可原生调用入口。

首版“AIHOT 日报自动进入官方提词器持久列表”仍未满足。任务书提供的“用户手动 txt 导入、重启后保留”是既有人工验收结果，不是本次静态检查再次验证的事实。RayneoNet 已有 native hooks 缺少完整 title/body，不提供官方保存契约；本任务未重启 task8 诊断打包。

## 范围与约束

- 输入：`/Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa`。
- SHA-256 实测：`de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24`，匹配任务书。
- 主包 `Info.plist`：`CFBundleShortVersionString=1.0.5`、`CFBundleVersion=201`、`CFBundleExecutable=Runner`。
- 只提取公开 `App.framework/App` 与 `Flutter.framework/Flutter` 至新目录 `/tmp/dart-manuscript-contract.Pl3MY1`；源 IPA 未修改。
- 唯一仓库写入为本报告；未改源码、Plan 文档或已有报告，未 commit。
- 未安装或启动 App，未加载目标 dylib 执行，未动态 hook，未读取手机容器、私人稿件、密钥或云端，未写官方数据库，未下载/执行第三方工具，未安装依赖。
- 三轮后停止；临时目录保留公开二进制、`round2.py`、`round2.log`、`macho_metadata.json`、`round3.py`、`round3.log`、`round3_metadata.json`，不包含用户数据。临时文件不是长期材料保证。

## 第一轮：工具与输入身份

### 命令

```sh
IPA='/Users/duriea/Downloads/TurboIO-http-navigation-r7-stopfix-20261012/TurboIO-HTTP-Navigation-unsigned.ipa'
for t in python3 otool nm llvm-objdump objdump lldb dwarfdump flutter dart blutter radare2 r2 rizin rz ghidra analyzeHeadless hopper jtool2; do
  printf '%s: ' "$t"; command -v "$t" || true
done
for t in llvm-objdump otool nm lldb dwarfdump; do xcrun --find "$t" 2>/dev/null || true; done
T=$(mktemp -d /tmp/dart-manuscript-contract.XXXXXX)
```

同轮使用 Python 标准库 `zipfile/hashlib/plistlib/importlib.util`：计算输入散列，只按 `Payload/.../Frameworks/App.framework/App` 和 `Flutter.framework/Flutter` 的唯一 ZIP entry 提取，读取主包 plist；检查四个 Python 模块的 `find_spec`。没有导入/执行目标 App 或 Flutter。

### 有界结果

| 检查 | 结果 |
|---|---|
| Python | `/usr/bin/python3` 可用 |
| Mach-O/ARM64 工具 | `otool`、`nm`、`objdump`、`lldb`、`dwarfdump` 可定位 |
| LLVM 反汇编器 | `/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/llvm-objdump` |
| Dart 专用工具 | `dart/flutter/blutter` 不在 PATH；未安装或下载 |
| 其他反编译工具 | 所检查的 radare2/rizin/Ghidra/Hopper/jtool2 命令不在 PATH；不声称整台机器绝无其他安装 |
| Python 模块 | `capstone/lief/elftools/blutter` 均 `find_spec=False` |
| ZIP 中符号附件 | 对 `.dSYM/.symbols/.debug/.map` 后缀的限定检查没有匹配；不代表已遍历所有可能调试材料格式 |
| App 提取大小/散列 | 47,568,560 bytes；`f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a` |
| Flutter 提取大小/散列 | 9,150,272 bytes；`488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0` |

本环境不是“只有 strings”：LLVM 可以读 Mach-O/反汇编 ARM64。缺口是能针对本版本恢复 Dart 对象、对象池、Function/Code 关联的工具与匹配符号，而非缺少通用反汇编命令。

## 第二轮：Mach-O、snapshot 与精确候选

### 证据命令与方法

```sh
python3 /tmp/dart-manuscript-contract.Pl3MY1/round2.py \
  | tee /tmp/dart-manuscript-contract.Pl3MY1/round2.log
xcrun llvm-objdump --help \
  | grep -E 'start-address|stop-address|disassemble|no-show-raw-insn|macho|symbolize-operands' \
  | head -16
```

`round2.py` 用标准库解析 `mach_header_64` 和指定 load commands：`LC_SEGMENT_64`、`LC_SYMTAB`、`LC_FUNCTION_STARTS`、`LC_UUID`、`LC_ENCRYPTION_INFO[_64]`。按节映射导出 snapshot 符号，读取 header 的 4-byte magic、8-byte length、8-byte kind、32-byte version token、终止于 NUL 的 feature 字符串。只搜索任务书列出的八个名字，每个最多十二个命中；这不是泛扫字符串。Flutter 只用限定版本模式匹配。

### App Mach-O 硬证据

- `MH_MAGIC_64=0xfeedfacf`，CPU `0x100000c`（arm64），filetype `6`（dylib）。
- UUID：`4cc4051a8cf2375cb74556ce5dfe6c47`。
- `__TEXT,__text`：VA/file offset `0x4000`，size `0x158bae0`，终点 `0x158fae0`。
- `__TEXT,__const`：VA/file offset `0x158fb00`，size `0x1683e30`。
- 给定候选全部位于 `__const`，不是 `__text` 中的函数入口。在该 Mach-O 这两个节的 VA 与 file offset 恰好相等；这不使字节偏移变成函数地址，运行时地址还涉及映射/slide。
- `LC_ENCRYPTION_INFO_64`：`cryptoff=16384`、`cryptsize=46202880`、`cryptid=0`。没有该命令指示的 FairPlay 加密；未证明不存在任何自定义保护。不得把后述 image header 的 `udf/<unknown>` 误报成加壳或加密。
- `LC_SYMTAB` 仅四个符号：

| 导出符号 | unslid VA / file offset | 含义边界 |
|---|---:|---|
| `_kDartVmSnapshotInstructions` | `0x4000` | VM instruction image 基址，不是目标业务函数 |
| `_kDartIsolateSnapshotInstructions` | `0xe800` | isolate instruction image 基址，不是目标业务函数 |
| `_kDartVmSnapshotData` | `0x158fb00` | VM snapshot data 起点 |
| `_kDartIsolateSnapshotData` | `0x1599340` | isolate snapshot data 起点 |

符号表没有保存/回读/serializer 的具名函数，属于目标 Dart 函数符号缺失；不声称所有元数据都被剥除。

### AOT/运行时版本硬证据

两份 data snapshot 的 header 均为：

```text
magic = 0xdcdcf5f5
kind(raw) = 3
version token = 78da37fed6bf1489361a312568249f3f
features = product no-code_comments dwarf_stack_traces_mode dedup_instructions no-asan no-msan no-tsan no-shared_data arm64 ios no-compressed-pointers
VM snapshot length(raw) = 5682
isolate snapshot length(raw) = 4504630
```

Flutter 二进制在 file offset `0x6aecff` 的限定版本匹配为：

```text
3.11.5 (stable) (Wed Apr 15 00:36:32 2026 -0700) on "ios_arm64"
```

这是同包引擎包含的运行时版本字符串与 App snapshot token 的独立观测，**尚未独立核对 token 对应的 Dart 源码 revision 或证明任一外部解析器兼容**。App/Flutter 的 framework plist 均只写 `1.0`，不能用它判断 Dart 版本。`no-compressed-pointers` 仅是 feature 硬证据，不等于 snapshot 对象引用均为裸绝对地址。

### 候选完整词与旧偏移修正

第二轮使用可打印边界暂取完整词；第三轮进一步检查 prefix 中可能的 Smi length，去掉打印填充混入的尾字符。最终有界结果：

| 候选 | 内容起点（不是函数地址） | prefix 可能的长度 | 读取内容 |
|---|---:|---:|---|
| database 源文件 URI | `0x2711630` | 102 | `package:venus_flutter_app/features/teleprompter_page/data/repositories/teleprompter_meta_database.dart` |
| database 类型名 | `0x2828140` | 24 | `TeleprompterMetaDatabase` |
| schema 名 | `0x2817e40` | 28 | `CachedTeleprompterMetaSchema` |
| serializer 名 | `0x28892e0` | 31 | `serializeCachedTeleprompterMeta` |
| deserializer 名 | `0x1decd00` | 33 | `deserializeCachedTeleprompterMeta` |
| mixin 名 | `0x1cfa210` | 96 | `_MixinApplication101&_$Teleprompters&_MetaDataPersistence&_FileOperation&_ItemOperation@18353248` |
| 路径 provider 名 | `0x2592ed0` | 37 | `teleprompterManuscriptAbsPathProvider` |
| SaveDocument 枚举候选 | `0x28119f0` | 50 | `TeleprompterScriptEditImportActionTypeSaveDocument` |

修正事项：

1. `teleprompter_meta_database.dart` 子串命中在 `0x2711677`，`0x2711630` 是整个 package URI 起点。
2. 裸 `find('serializeCachedTeleprompterMeta')` 也会命中 `deserializeCachedTeleprompterMeta` 的 `+2`（`0x1decd02`），以及 `deserializeCachedTeleprompterMetaProp` 的 `+2`（`0x269af22`）；它们不是 serializer 本身。serializer 候选为 `0x28892e0`。
3. `SaveDocument` 子串命中 `0x2811a16`，`0x28119f0` 是上述长枚举词起点；不能推断存在名为 `SaveDocument` 的保存函数。
4. `Teleprompters` 子串在 mixin 中命中 `0x1cfa227`；mixin 起点 `0x1cfa210`。第二轮可打印边界多包含尾随 `r`，第三轮 possible-length 读取不含该填充字符。
5. `TeleprompterMetaDatabaseInterface/Family/Provider`、`init:CachedTeleprompterMetaSchema` 也有精确子串命中，但名字不证明实现、初始化顺序或可调用性。

## 第三轮：有限引用检查与反汇编能力检验

### 命令与覆盖

```sh
python3 /tmp/dart-manuscript-contract.Pl3MY1/round3.py \
  | tee /tmp/dart-manuscript-contract.Pl3MY1/round3.log
```

`round3.py` 做且仅做以下几项：

1. 解码 `LC_FUNCTION_STARTS` 的 ULEB128 增量表，定位静态边界；不将边界与 Dart 名称绑定。
2. 对表中八个候选读取前 16 bytes，按两项 little-endian uint64 显示原始 prefix；第二项为偶数且 `(value >> 1)` 在 `1..511` 时读取该可能的 Smi 长度。**没有验证 class ID、tags 布局、对象类型或 Code owner**，这是可核对的局部结构一致性，不是完整 Dart 对象反序列化。
3. 每个候选检查三种假设地址的 LE uint64 原样出现：`payload`、`payload-16`、`payload-15`。每种最多输出八个命中；后两者仅候选 prefix/tagged 形式，未认定为真实 Dart 对象指针。
4. 遍历唯一 `__text` 节，限定匹配 ARM64 `ADRP + ADD-immediate`：同基寄存器、ADRP 后最多三条指令，目标仅这 24 个假设地址。没有完整反汇编全库、没有通用数据流分析、没有 Dart 对象池解码。
5. 对两个导出 instruction-image 基址各最多 96 bytes 调用 LLVM，确认通用工具读文件能力。没有 App/Flutter 执行。

### 有界输出

```text
LC_FUNCTION_STARTS dataoff=46219384 datasize=152920
decoded count=111353
first: 0x4000 0x4080 0x408c 0x40c0 0x40fc 0x4130 0x4254 0x42c0
last: 0x158fa54
24 groups ABSOLUTE64_CANDIDATE: []
DIRECT_ADRP_ADD_CANDIDATES: []  count=0
```

代表性 prefix 与 possible-length：

```text
serialize @0x28892e0: 72e30500a12c83373e00000000000000 => possible length 31
deserialize @0x1decd00: 72e40500e1d6a00f4200000000000000 => possible length 33
mixin @0x1cfa210: 72e70500f848143bc000000000000000 => possible length 96
```

两个实际反汇编命令均 exit code 0：

```sh
/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/llvm-objdump \
  --disassemble --start-address=16384 --stop-address=16480 --no-show-raw-insn \
  /tmp/dart-manuscript-contract.Pl3MY1/App
/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/llvm-objdump \
  --disassemble --start-address=59392 --stop-address=59488 --no-show-raw-insn \
  /tmp/dart-manuscript-contract.Pl3MY1/App
```

有界输出是 image 基址标签，包含 `udf`、`<unknown>` 和零省略。例如 `0x4000: udf #0xa800`、`0x4008: udf #0x40`；isolate 基址 `0xe800` 也包含 header/未知值。**这些是 instruction image 起始区的输出，不是已恢复的 Dart 业务函数反汇编。** `LC_FUNCTION_STARTS` 恰有该边界也不赋予它业务函数身份。

零引用结果不证明目标没有调用者。Dart snapshot 编码引用、对象池索引、tag、Code/Function owner、相对地址和寄存器数据流均未解析；通用 ARM64 反汇编器不自动补足这些。继续重复字符串或同类裸指针扫描不会弥补缺口，故三轮结束。

## 真实已知与仍未知

| 问题 | 本次能证明的内容 | 尚未恢复/不能推断 |
|---|---|---|
| 官方保存入口 | SaveDocument 长枚举名称存在 | 函数地址、调用者、参数、接收对象、异步返回/异常、调用能力 |
| metadata 序列化 | serializer/deserializer/schema 名称存在 | 字段、类型、编码、数据库引擎、schema version、nullable/default、迁移 |
| 内容文件关联 | absolute-path provider 名称存在 | 实际文件目录/扩展名、命名、编码、与metadata的关联键、写入与回读顺序 |
| 官方列表 ID | Teleprompters/database/mixin 名称存在 | 官方 item ID、数据库主键/自增/UUID规则、去重键、排序、缓存刷新 |
| 持久回读链 | persistence/file/item mixin 名称同处复合名称 | 真实调用边、初始化恢复流程、原子性、跨用户作用域、重启列表如何构建 |
| native 可调用性 | snapshot exports 可定位 | 任何可从 ObjC/native 安全调用的官方接口；Dart AOT 中存在函数也不保证可直接 native 调用 |

**未取得真实 schema/code 引用，不能提供 SQLite 字段猜测、DB 直写、路径硬编码或“必能调用”的承诺。**

## 阻断原因与最小下一材料

当前 blocker 是缺少匹配版本的 Dart 静态元数据恢复与具名函数映射，不是已证实的加密，也不是“只有 strings”。最小优先级：

以下是候选路线的替代项，不要求用户一次提供全部逆向材料，也不要求先证明所有静态路线都不可能才能选择动态验证：

1. **优先厂商契约或目标源码**：选择一份可验证的官方保存与回读契约即可；至少覆盖真实操作与调用边界、title/body 编码、成功/异常/异步结果、稳定 item ID、官方列表或读回内容、必要的刷新/重启保持、重复提交与未知结果对账。若公共接口已闭环，不需要内库 schema、目录或 serializer。还须证明能由本 addon 安全触发，而非仅证明手动 UI 可操作。
2. **静态分析路线**：取得与 App UUID/snapshot token 匹配、来源可信且能核对 save/read/ID 的 Function-Code-ObjectPool 映射输出。可以使用已确认兼容的解析输出或目标符号材料，不强制同时拥有全套 symbols 与解析器；native dSYM 不能自动替代 Dart 映射。现有四个 snapshot 导出和通用 LLVM 输出不足，不据版本字串猜 revision 下载执行工具。
3. **独立动态验证路线**：可另行请求用户明确授权，只针对既有公开 fixture、真实候选 save/read 调用边界、限定次数与短时停止条件，验证编码、返回 item ID 与官方回读。当前授权不含这类操作，本任务没有执行。跨阶段标题/正文关联、跨重启 ID 对账需要新的安全设计，不能沿用当前会话 HMAC 作为稳定 ID 证明；需要一次新建验证时另行授权，编辑已有稿不能冒充自动 create。获得证据不自动授权 DB 直写、全量 hook 或集成。

如果没有上述材料，合理交付状态仍是 **blocked：保持官方自动入列表作为未达成验收项，不打无效诊断包，不将手动导入冒充自动入口**。

## 证据层级与复核

- 一级原始来源：指定 IPA 的公开 ZIP entries、Mach-O load commands/symbol table、snapshot header、限定命中 payload、Flutter 版本 marker。
- 命令、脚本及有界 stdout：临时目录中的 `round2.py/.log`、`round3.py/.log`；脚本仅 Python 标准库解析与本机 LLVM 子进程，未执行目标代码。
- 本报告是调查结果，不是恢复出的 ABI/schema；不存在可引用的目标业务函数地址或调用边。
- 完成后只需检查本报告语法/路径和报告范围，不再增加第四轮二进制调查。
