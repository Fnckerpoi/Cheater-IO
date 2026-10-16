# 原厂 iOS 1.0.6(218) 去签名 IPA：真机输入验证

执行时间：2026-10-16 · 仓库 HEAD `0510d1c`（已合并上游 `0aefa12`，tag `rayneo-ios-1.0.6-218`）
输入来源：`https://github.com/Turbo1123/Turbo-IO/releases/download/rayneo-ios-1.0.6-218/RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa`
本机存放：`official-addon/.build/ios-inputs/`（被 `.gitignore` 的 `**/.build/` 排除，不入库）

## 结论摘要

官方 `official-apps/ios/1.0.6/` 声明的**全部可机检断言均被独立复算命中**：IPA 哈希、ZIP 成员数、95 个未加密 Mach-O、Runner 哈希与 UUID、App.framework/App 哈希、两条升级查询字符串的精确改写位置与长度守恒。上游 `package.test.mjs` 在带真实输入时 7/7 通过。**这只证明输入身份与打包门禁，不证明真机可用**：本次没有签名、没有安装、没有刷机、没有设备读写。

## 1. 下载与哈希校验（已执行）

```
curl -L --retry 3 → 353,545,727 bytes（42 s，与官方声明逐字节一致）
shasum -a 256 RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa
  f20d23c2e90c9f6effb73463491725d2a359af14b1afa1bc06cf68f61bf2a852
shasum -a 256 -c official-apps/ios/1.0.6/SHA256SUMS → 同一哈希，匹配
```

## 2. 解包与镜像清点（已执行）

| 断言 | 官方声明 | 实测 |
| --- | --- | --- |
| ZIP 成员 | 2705 | `unzip -l` → 2705 files；解包后 `find -type f` → 2705 |
| Mach-O 数量（`Payload/Runner.app` 递归） | 95 | Python 按 magic `0xfeedfacf` 遍历 → **95** |
| 加密镜像（`cryptid 1`） | 0 | `otool -l` 逐个扫描 95 个 → **0 个加密** |
| 依赖里出现 `TurboIOPrivateAddon` | 无 | 95 个 `otool -L` 汇总 → **0 次命中** |
| `Payload/Runner.app/Runner` SHA-256 | `d5982356…bd44` | `d59823568be232b1742f7414a6ebae4718f053e1a251577808041b032306bd44` ✅ |
| `Runner` LC_UUID | `98a03b57e8163ba19778e55fabe8d762` | `dwarfdump --uuid` → `98A03B57-E816-3BA1-9778-E55FABE8D762 (arm64)` ✅ |
| `Frameworks/App.framework/App` SHA-256 | `57d06449…af884` | `57d064491c46a21420fd73c969ebfb390707556861526e62db58633514caf884` ✅ |

`host-compatibility.mjs` 的 `inspectedHosts` 已含 `{version:'1.0.6',build:'218',uuid:'98a03b57…'}`，与实测 Runner UUID 一致 —— 这份 IPA 就是该矩阵所指的宿主。

## 3. 上游打包门禁测试（已执行）

```sh
export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH"   # 沙箱 PATH 里没有 node，需显式指定

# 不带真实输入（可选测试早退）
node --test official-addon/tmig1-edition/package.test.mjs
  # tests 7  # pass 7  # fail 0  # duration_ms 383.73

# 带真实 IPA 输入
TMIG1_TEST_UNSIGNED_APP="$PWD/official-addon/.build/ios-inputs/unpacked/Payload/Runner.app" \
TMIG1_TEST_UNSIGNED_HOST_IMAGE="$PWD/official-addon/.build/ios-inputs/unpacked/Payload/Runner.app/Frameworks/App.framework/App" \
node --test official-addon/tmig1-edition/package.test.mjs
  # tests 7  # pass 7  # fail 0  # duration_ms 7018.32
```

两次都是 7/7 通过，但带真实输入那次耗时 18 倍，且 `ok 7 - optional exact real-input acceptance without signing, installation or device IO` 出现在输出中 —— 说明 `optional unsigned stock Release input is exactly pinned…` 与 `optional original bundle has 95 unencrypted images…` 两条确实执行了，不是早退。

## 4. 独立复算 `patch()`（已执行）

```
inputPins = 2 → ["50d91ba9…0845", "57d06449…af884"]
input sha = 57d06449…af884            # 命中第二枚 pin（本份去签名版）
report.inputSHA = 57d06449…af884
report.outputSHA = 76bfa5478823b31f83e236e2ab7ae654d829c7792d9740b9bb2eaeef87b74fc5
changes = 2:
  offset 41185344, 38 B  "/xrlauncherapi/v1/signApi/gray/upgrade"   → "http://127.0.0.1:18794/g/xxxxxxxA78p2M"
  offset 32098976, 40 B  "/xrlauncherapi/v1/signApi/gray/upgrade"   → "http://127.0.0.1:18794/g/xxxxxxxxxxCCXhB"
output.length == input.length            true
instructionsChanged false · objectHeadersChanged false · deviceIO false
把 offset 4096 处改 1 字节 → throw "Wrong host image"
输入换成非宿主字节 → 拒绝
```

`route-hash.mjs:9` 的 `originals` 就是上面两条原厂升级查询字符串；改写保持长度与哈希槽一致（`assert(result.length===original.length && stringHash(result)===target)`）。

## 5. 本次没有做的事（不可当作已验证）

- 未签名、未安装、未启动、未刷机、未做任何设备读写（`deviceIO: false`）。
- 未运行 `tmig1-edition/build.sh`（它会签名）。
- 未验证这份宿主与本机 NewsHub 插件在真机上的加载与行为。
- 未下载/校验 Strix OS 1.0.5.5 固件包本身（其身份见 `firmware-research/strix-1.0.5.5/README.md`：ZIP 8,824,204 B / SHA `f1d3616c…`，AP 7,373,064 B / SHA `30522ac7…`）。

## 6. 复现命令

```sh
cd official-addon/.build/ios-inputs
shasum -a 256 RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa
unzip -q -o RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa -d unpacked
```

## 7. 发现一处上游测试与上游发布物不一致（未修改上游测试）

带真实固件输入再跑一次（`TMIG1_TEST_HOST_IMAGE` + `TMIG1_TEST_FIRMWARE`）：

```
# tests 7  # pass 6  # fail 1
not ok 7 - optional exact real-input acceptance without signing, installation or device IO
+ actual   '76bfa5478823b31f83e236e2ab7ae654d829c7792d9740b9bb2eaeef87b74fc5'
- expected '532bdfc1971668df113434de54f69cede8bab3f61c7d82d028d62c54f5ca5940'
```

失败点是 `package.test.mjs:69` 里硬编码的**补丁输出哈希**常量。其余断言（输入命中 pin、`changes.length===2`、`objectHeadersChanged===false`、`validateResearchPair` 通过、改字节被拒）都在同一测试的后续行上，本次未被执行到，但已由第 4 节独立复算逐条命中。

排除「随机性导致」的可能：

```
对同一输入连续调用 patch() 4 次 → 输出哈希 4 次全为 76bfa547…，replacement 字符串也完全相同
all_equal true · matches_test_constant false
```

即：`patch()` 对这份 IPA 是确定的，`532bdfc1…` 不可能由它产生。

同时校验了固件侧身份（已执行）：

```
下载 StrixOS-1.0.5.5-TurboIO-TMIG1-CANDIDATE-NOT-APPROVED.zip → 8,824,204 bytes
shasum -a 256 → f1d3616cfde5d128a9b60baab420559e38d19300838b0b87ceb1010f2ac5c940
与 release 的 SHA256SUMS 一致，也与 package.mjs:25 的 validateResearchPair 常量一致
仓库内 firmware-research/strix-1.0.5.5/stock/Strix-OS-1.0.5.5-original.zip = 8,740,749 bytes
（与 release 资产同尺寸；其 SHA 见该目录 SHA256SUMS：2927917f…）
```

**待你决定的处理方向**（我没有改上游测试）：
1. 把该常量改成本次实测值 `76bfa547…`（本地偏离上游一行）；
2. 或让该断言接受两枚 pin 各自对应的输出（上游 `inputPins` 允许签名保留版与去签名版两种输入，硬编码单一输出常量本身与之矛盾）；
3. 或把该不一致回报上游，本地保持原样、在本文件留下记录。
