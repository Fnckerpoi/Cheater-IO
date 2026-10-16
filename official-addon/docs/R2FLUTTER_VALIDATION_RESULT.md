# 隔离 Dart AOT 解析验证结果

## 授权与执行边界

用户通过 `r2flutter_isolated_validation_consent` 明确批准隔离构建及有界只读验证。开始 `2026-10-14T07:47:50Z`，共同绝对截止 `2026-10-14T08:07:50Z`，不是每次操作重新20分钟。

根目录 `/Users/duriea/Tools/REA/r2flutter-validation-20261014-154750/`。没有全局安装、user-install、shell rc或MCP修改；未操作手机、执行目标App、访问稿库/凭据或上传IPA。工具源码、构建、prefix、临时HOME/XDG/TMP和结果均隔离在该根目录。r2flutter是新补充解析工具，不冒称它已注册成为REA MCP。

## 构建与身份

- r2flutter固定commit `05c0616b3782422978cd0befc2b30af1c59ca41a`、tree `b09e2fd037d0c3fdcb2804c18ec1e30d1691d8de`，版本输出0.3.6。
- 整仓archive因大型样本有界90秒下载未完成，该partial未用于构建。按同commit/tree选择68个构建所需文件，逐Git blob SHA-1与SHA256核验；完整tree和excluded列表保留。调度者独立复核68项通过。
- radare2官方6.2.2 release源码与官方资产digest硬匹配，使用已有clang/make、仓内已带依赖，本地prefix构建成功。禁用动态插件装载/相关脚本引擎的build配置完整见构建报告。
- r2flutter可执行SHA256：`7f0f08c24c57b05f99d869b6bdd60b7c1e3209757ea4cf2b3fee742be5991a95`。
- radare2可执行SHA256：`e080908999a8c9387e0c6324a707571daa305bf76ae18f11400d3030ff0de443`。
- 构建者核验25个可执行/动态库依赖仅隔离prefix及Apple系统路径；228个prefix常规文件摘要保留。9个owned构建进程组均已退出。

详细报告：隔离根的 `results/BUILD_REPORT.md`、`r2flutter-source-manifest.json`、`prefix-file-manifest.json` 和 `dynamic-library-whitelist.json`。

## 源码审阅与限定运行

独立审阅记录：CLI `-jH`通过r_core_new/read-only bin loader，不是纯header字节读；固定目标known snapshot hash命中后跳过structural layout probe。radare2 6.2.2该初始化只遍历静态core插件，动态loadlibs未调用；R2_NOPLUGINS=1有源码依据。rc/project未加载，cmd.load默认空、bin.libs默认false。目标旁边与canonical位置App.dSYM均已确认不存在。

`-i`在应用20条输出限制前仍进行图及data-image扫描，不能把-l20称为局部计算。初审NO-GO；在真实header计数已取得后，审阅者给一次受限诊断的条件GO，但任何名字/地址仍仅metadata/data-image/heuristic，不是trustedowner、可调用API或保存契约。

## 调度者实际header验证：成功

一次调用隔离工具 `-jH` 读取已有物化App，wall45秒、CPU35秒、单文件输出4MiB、文件描述符256、空HOME/XDG/TMP/cwd、环境白名单且R2_NOPLUGINS=1，仅显式设置owned prefix/lib。

结果：3.503秒、exit0、无timeout，JSON字段：

```json
{"kind":3,"hash":"78da37fed6bf1489361a312568249f3f","cws":8,"dart_version":"3.11.5","version_source":"exact-hash","tag_style":"OBJECT_HEADER","cluster":{"base":1185,"objs":389648,"clusters":3596,"it_len":20555,"it_off":64,"total":4504634}}
```

实际App输入 `/Users/duriea/Documents/GitHub/Turbo-IO/official-addon/build/rea-teleprompter-import/dart/App`，SHA256前后均 `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a`。stderr有重定位未应用警告，未为抹掉warning擅自添加参数。

证据：隔离根 `results/lead-header/{execution.json,stdout.json,stderr.log}`。

此结果证明实际工具识别同版App header及CWS8布局，不证明Function-Code-owner/ObjectPool映射正确。

## 一次指令表诊断尝试：没有启动解析器

在审阅者基于新header计数条件批准后，准备一次 `-j -l 20 -i`，附wall45秒、CPU35秒、地址空间2GiB、输出4MiB和process-group结束边界。

`subprocess.Popen`在preexec阶段失败：`Exception occurred in preexec_fn`，returncode=null。随后独立小Python（不启动parser/目标）核验：CPU/FSIZE/NOFILE均可设；本机设置RLIMIT_AS=2GiB报 `ValueError: current limit exceeds maximum limit`。因此该次**未执行parser**，不是解析器不兼容、目标格式不支持或扫描超时。没有降低限制续跑，也没有以新目录重试来掩盖。

证据：隔离根 `results/lead-instruction/execution.json`，stdout/stderr为空；SHA前后仍匹配。

## 剩余缺口与下一门槛

- 无真实full-width fixture成功映射回归；Function-owner与Code/ObjectPool关系仍UNKNOWN。
- 限定指令表诊断未运行，macOS可执行的等价内存约束/隔离监督方案须先验证，不直接取消保护强跑。
- 无页面跳转的官方import/save/read入口、业务对象/isolate上下文、合法稳定稿件ID、保存完成与读回契约仍未取得。
- 不用header成功或工具构建成功称接口已恢复；没有新功能IPA。

首轮验证在原预算内收束，未自动延长20分钟授权。

## 新授权：软RSS监督下的一次指令表诊断

用户随后通过 `r2flutter_soft_rss_scan_consent` 明确接受新单次运行：wall45秒、CPU35秒、输出每文件<=4MiB，约25ms采样本进程RSS，超过512MiB终止，明确不是hardcap。此前自有小fixture实测watchdog能停进程但有overshoot；macOS原生setrlimit AS/DATA/RSS的拒绝已用C/Python复核，未取消所有监督强跑。

调度者实际执行固定工具 `-j -l 20 -i <same App>`，环境仍白名单、空HOME/XDG/TMP/cwd、R2_NOPLUGINS1及owned prefix动态库，目标未执行，未加猜名或全分析参数。

结果：3.625秒、exit0，stop_reason completed；96次RSS采样，observed peak 162103296 bytes，阈值536870912 bytes，monitor_errors0；App SHA前后仍完全相同。不能将采样峰值说成保证瞬时硬上限。

指令表返回length111178、first_entry_with_code90623、canonical_stack_map_entries_offset2355229；前20项index0–19均kind=stub、name形如func.0000e880，并没有官方业务函数owner/name。20条地址为59520至64768，详细偏移原样保留，不以地址存在推导native可调用。

新证据目录：隔离根 `results/lead-instruction-soft-rss-approved/{execution.json,stdout.json,stderr.log}`。stderr仍仅重定位未应用warning；未擅自改运行参数抹警告。

这次是实际成功的只读指令表诊断，不是前一preexec失败的重述；但仍未形成可信业务Function-owner/Code/ObjectPool关联，更不是已发现无UI官方保存接口。下一步只读复核现有结果与工具参数，新的目标运行仍需明确预算与许可。
