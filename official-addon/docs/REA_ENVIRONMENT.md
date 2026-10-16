# REA 6.4.0 / Ghidra 12.1.4 独立环境核验

## 状态与授权边界

**Ghidra provider 已通过真实自有 arm64 Mach-O 静态分析与伪代码验收。** 这不是“CLI启动成功即能反编译”的结论，也不表示 Dart AOT/官方稿件契约已经恢复。

task-16 只安装工具并验证最小公开 fixture。不读取源 IPA/App/Flutter、不执行目标、不访问手机/容器/私稿/凭据；不安装 Java、Hopper、Gradle 或其他工具；不修改 shell profile、IDE/client registration、DSH配置或 skill。用户已授权安装路径 `/Users/duriea/Tools/REA/`；Mac 缺失 native decompiler 的 bundled-source 编译步骤经 lead 明确确认后执行。

## 官方来源与完整性

- 官方 repository：`NationalSecurityAgency/ghidra`。
- Release：`Ghidra_12.1.4_build`，非 draft / 非 prerelease。
- Metadata：`https://api.github.com/repos/NationalSecurityAgency/ghidra/releases/tags/Ghidra_12.1.4_build`。
- Asset：`ghidra_12.1.4_PUBLIC_20260921.zip`。
- URL：`https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_12.1.4_build/ghidra_12.1.4_PUBLIC_20260921.zip`。
- 官方 API SHA256 digest 与实下载 SHA256 一致：`ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db`。
- 大小：569,649,598 bytes；CRC 全通过。
- 安装目录：`/Users/duriea/Tools/REA/ghidra_12.1.4_PUBLIC`，原目录不存在、未覆盖其他安装。
- 安装前检查 ZIP 无重复路径、路径穿越、绝对路径、反斜杠、symlink/special file；所有 destination resolve 均在批准的唯一 install root 内。
- 安装 receipt：`/Users/duriea/Tools/REA/ghidra-12.1.4-install-receipt.json`。

REA 本地缓存实际 package version 为 **6.4.0**，不是仅凭 npm tag 推断。实际 `dist/ghidra/GhidraInstallationPolicy.js` 声明 `SUPPORTED_GHIDRA_VERSION="12.1.4"`、兼容 release line `12.1.x`。

## Mac native component

官方 ZIP 仅提供 Linux/Windows native decompiler，没有 `mac_x86_64/decompile`。首次 scoped 环境检查如实记录 `provider.available=false / executable_missing`，未提前宣称就绪。

官方发行包 `docs/GettingStarted.md:193–242` 明确 macOS 是 user-built native platform；bundled `Ghidra/Features/Decompiler/src/decompile/cpp/Makefile` 的 Darwin 分支使用 x86_64，`ghidra_opt` 仅编译链接 C++ 源。使用已经存在的 Xcode compiler 和 `/usr/bin/make`，没有 Gradle或依赖下载：

```sh
# workdir 为此绝对目录，不运行 install_ghidraopt 或广泛 install/clean
/Users/duriea/Tools/REA/ghidra_12.1.4_PUBLIC/Ghidra/Features/Decompiler/src/decompile/cpp
/usr/bin/make -j2 ghidra_opt
```

只将已确认绝对来源 `.../src/decompile/cpp/ghidra_opt` 复制到原不存在的：

`/Users/duriea/Tools/REA/ghidra_12.1.4_PUBLIC/Ghidra/Features/Decompiler/os/mac_x86_64/decompile`

- 实测 `file`：Mach-O 64-bit executable **x86_64**。
- 大小：3,504,480 bytes。
- SHA256：`9d4e37d00cf6547d44f2c3fd1444f26b72c2cfa3e61fec0ca77dd6c784934679`。
- `ghidra-native-build.log` / `ghidra-native-build-receipt.json` 位于项目 `official-addon/build/rea-tools/`。
- 不运行 Makefile 默认外部 `GHIDRA_BIN` install，不删除源、对象或已有目录，不改 Gatekeeper/quarantine。

## 每次命令的环境（只 process env）

```sh
export PATH=/Users/duriea/.nvm/versions/node/v22.22.3/bin:$PATH
export npm_config_registry=https://registry.npmjs.org
export JAVA_HOME=/Users/duriea/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home
export GHIDRA_INSTALL_DIR=/Users/duriea/Tools/REA/ghidra_12.1.4_PUBLIC
```

已测：Node 22.22.3、Microsoft full JDK 21.0.10+7（64-bit）、x86_64 macOS 15.8.1。registry 必须前置，否则用户已有 npmmirror 配置可能在 offline 下 ENOTCACHED；本任务不修改该全局配置。

实际运行：

```sh
npx --offline -y rea-agents@6.4.0 doctor --provider ghidra --json
```

`doctor-ghidra-ready.json`：scope `healthy=true`、Ghidra available=true/version12.1.4，headless、native executable/header、Java均 PASS。`environment_healthy=false` 仍如实保留：audit-wide 中 Hopper/IDA未安装、旧client/skill registration 不对齐；本任务无需且未“修复”这些无关环境。选定 Ghidra CLI/自有 stdio session 不需要修改客户端或重启本会话。

## 真实 fixture Evidence

只编译自有 `public-arm64-fixture.c`，未执行 fixture dylib：

```c
int rea_public_fixture_score(int count) {
    if (count < 0) return -7;
    return count * 3 + 11;
}
int rea_public_fixture_caller(int count) {
    return rea_public_fixture_score(count) + 5;
}
```

`public-arm64-fixture.dylib`：thin arm64、iOS15、SHA256 `aae00ee8895cac94e88739c482141a17c763795289366876f0604e2dfa8ed897`。

在唯一自有 stdio native session 中，实际 tools/list 后 `open_binary(provider_id="ghidra")` → `binary_overview` → `analyze_function` → `procedure_pseudo_code` → `binary_session` → `close_binary`，最终关闭 client/transport。入口和伪代码结果不是 fixture 源文本回显，而是 Ghidra Mach-O import/默认 auto-analysis/decompiler 返回：

| Evidence | 结论 |
| --- | --- |
| `ev_ed59310a4dcb39bdbe3ee7f9ebf275f7eb4e344de4696c41144b4c8717d81ad8` | Ghidra12.1.4 `analyze_function(0x4000)`；函数68bytes，17条arm64 assembly，恢复负数分支-7与 `param_1*3+0xb`，caller `0x4044`，call xref `0x4058→0x4000` |
| `ev_a67ddbf87266eefd65ac664df644329740988c530b3d3e5aa5300bd776e04250` | `procedure_pseudo_code(0x4000)` 同样返回完整C伪代码，真实provider=ghidra12.1.4 |

完整 inline Evidence 保存在 `fixture-function-verified.json` / `fixture-pseudocode-verified.json`；overview、session、close、实际 schemas 与 logs 在同目录。

首次使用无前导 `_` 的符号名查询失败（`Unknown Ghidra procedure name`），其证据 `fixture-function.json` 保留，不冒称成功；实际 Mach-O symbol 是 `_rea_public_fixture_score`，按 nm及text段确证的地址 `0x4000` 纠正后成功。两次各自 owned session 都关闭；成功 receipt 为 `fixture-close-verified.json`。原 fixture SHA在验证后再次核验不变。

### Limitations

- 伪代码不是原始源代码；函数signature database可能仍为undefined，反编译输出是类型恢复推断。
- 默认 auto-analysis 完成才提供结果；不完整分析、外部/无body函数、decompiler failure要保留 unknown/error。
- 临时 Ghidra Program/project 在 close 后删除；annotation仅会话metadata，原native输入不写。
- 取消活动request会终止临时database；下一次查询可能重新import。
- 这次仅自有小 Mach-O 测通，不证明大型 Flutter AOT、ObjC dispatch 或任意具体目标必然能恢复完整原语言契约。

## 已验证 CLI 与持久批量方式

实际 help/schema 采用6.4.0，不照未注册工具名猜测：

```sh
npx --offline -y rea-agents@6.4.0 analyze /absolute/native --provider ghidra --format json
npx --offline -y rea-agents@6.4.0 function /absolute/native 0xADDRESS --provider ghidra --format json
npx --offline -y rea-agents@6.4.0 decompile /absolute/native 0xADDRESS --provider ghidra --format json
```

本轮另实际运行上述 `decompile` CLI，target为该自有 fixture、procedure=`0x4000`，exit0，返回 `ev_a67ddbf87266eefd65ac664df644329740988c530b3d3e5aa5300bd776e04250` 与相同真实伪代码；完整CLI Evidence在 `fixture-cli-decompile.json`，不是仅 help 或 MCP启动检查。CLI自动关闭其临时session。

CLI每条分析命令 acquire/close 自己的 session，重复调用可能重新分析大目标；snapshot是证据状态，不可假称存活数据库。用于多轮xref/decompile时，各worker应使用自己独立的持久 stdio subprocess（不是注册IDE/client）：

```sh
node /Users/duriea/.npm/_npx/af752b90495482d7/node_modules/rea-agents/scripts/rea.mjs --mcp
```

项目 `build/rea-tools/verify-native-fixture.mjs` 通过已经缓存的 `@modelcontextprotocol/client@2.3.1` 示例展示 Client + StdioClientTransport、actual tools/list、串行调用、timeout与finally关闭。worker需自己的输出文件和target，不重跑这个 `wx` fixture脚本覆盖证据。

实际 native inputs：

- `open_binary {path:"/absolute/native",provider_id:"ghidra"}`。
- `analyze_function {procedure:"0x..."}`。
- `procedure_pseudo_code {procedure:"0x..."}`，不是 `read_pseudo_code`。
- `procedure_callers` / `procedure_callees {procedure:"0x..."}`。
- `xrefs {address:"0x..."}`。
- `binary_session {}` 查看target/provider/availability。
- `close_binary {}`，随后 client.close + transport.close。

传绝对路径。更多tool必须通过各session实际 tools/list schema，不使用虚构参数。不要为复用会话同时对同一database并行写/读；任务结束必须close owned session。

已将**真实ready证据、准确env、CLI与持久批量方式**同时发给 lead、task14与task15成员。
