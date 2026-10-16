# NewsHub 完全无页面跳转的官方文稿契约调查

## 裁决与范围

**UNKNOWN：尚未查证可由 native addon 安全调用、完全无页面跳转的官方文稿导入／保存／读取接口；自动入官方稿库验收未通过。** 这不是接口不存在、Dart 格式不受支持或所有授权技术路线不可实现的证明。

本调查只读检查既有报告、当前 trial 源码、指定 IPA 成员身份与包内公开 Flutter headers／Info.plist。没有启动 App 或 headless isolate、手机操作、运行监听、数据库读取或写入、私稿／凭据访问、新探针、工具安装、全局设置修改或第四次 App Ghidra 分析。本文经 lead 后续批准新建；不修改生产代码、IPA 或其他 worker 文件。

最新要求以真实官方文稿库为目标：多源各自独立稿；不得把眼镜业务20缓存、传输回执、自造 ID 或 native 自建 request 的 callback 当成官方业务保存。支持 TXT／DOCX／PDF <=1MB 属于任务／用户已提供的官方 UI 能力背景，本轮没有重新执行格式验证，也不将其扩展为 headless API 契约。

## 1. 本轮直接核验的目标身份

指定包：`official-addon/build/newshub-final-20261014-144020/TurboIO-NewsHub-unsigned.ipa`。

| 对象 | 字节数 | SHA-256 |
|---|---:|---|
| 指定 IPA | 本轮未记录 | `76c7f41a2ba39a56269a8984a5bd50593002f17f84b0e84343a8929eacc4db99` |
| `Payload/Runner.app/Frameworks/App.framework/App` | 47568560 | `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a` |
| `Payload/Runner.app/Frameworks/file_picker.framework/file_picker` | 161616 | `9e4baee5172d451872912107f6c5f633ba51a620b2323b55f8c64d6b3b48480a` |

方法：Python 标准库流式计算 IPA SHA；`zipfile` 只读指定成员后计算成员 SHA，`plistlib` 解析指定 Info.plist；headers 直接读取 ZIP member，未提取或执行二进制。上述为本轮本地直接观测，**不是伪造的 REA Evidence ID**。

App SHA 与 `REA_TELEPROMPTER_DART.md:18–23` 的旧 stage App 完全相同；file_picker SHA 与 `REA_TELEPROMPTER_NATIVE.md:11–16` 完全相同。因此可复用这两个官方成员的已有静态证据；不把不同 IPA 整体、addon 集成或真机运行状态宣称相同。

### 可复核命令（只读）

```sh
python3 - <<'PY'
import hashlib, plistlib, zipfile
p = 'official-addon/build/newshub-final-20261014-144020/TurboIO-NewsHub-unsigned.ipa'
h = hashlib.sha256()
with open(p, 'rb') as f:
    for b in iter(lambda: f.read(1024 * 1024), b''):
        h.update(b)
print('IPA_SHA256', h.hexdigest())
with zipfile.ZipFile(p) as z:
    for n in ['Payload/Runner.app/Frameworks/App.framework/App',
              'Payload/Runner.app/Frameworks/file_picker.framework/file_picker']:
        b = z.read(n)
        print(n, len(b), hashlib.sha256(b).hexdigest())
    info = plistlib.loads(z.read('Payload/Runner.app/Info.plist'))
    for k in ['CFBundleIdentifier', 'CFBundleShortVersionString', 'CFBundleVersion',
              'CFBundleURLTypes', 'CFBundleDocumentTypes',
              'LSSupportsOpeningDocumentsInPlace', 'UIFileSharingEnabled']:
        print(k, repr(info.get(k, 'ABSENT')))
PY
```

## 2. 已知／未知与证据位置

所有 header 行号指最新指定 IPA 内成员的文本行，而非仓库中别版本 headers。共同前缀：`Payload/Runner.app/Frameworks/Flutter.framework/Headers/`。

| 问题 | 已核实事实与来源 | 仍不能推出 |
|---|---|---|
| native picker 是什么 | `REA_TELEPROMPTER_NATIVE.md:43–60,77–87,101–103`：选择结果为 path／identifier／name／size／可选 bytes，回调属于该次请求；`save` 为文件导出（:75），不是稿库保存。dispatch `0x474c` Evidence `ev_7b79cf6271ae3a5d67b4f27fb3b058101c7425598a8dfb9022d2beb21a54715e`；handleResult `0x60d4` Evidence `ev_81407a132d5e3915d2fa977ae78fb2955e0ec93d5d2ec02f95e54d90c6db9653`；FileInfo toData `0x40fc` Evidence `ev_5ec8e3d81f217fa8a1f12c3e992e6c1020ad9c39a57f5b0da2ccb8e0b22d2d04`。 | 自发 `handleMethodCall:result:` 获得的是自己的 callback，不是官方 Dart import Future continuation；URL identifier 不是官方 DocID。 |
| 当前 trial 能做什么 | `OfficialImportTrialHost.m:66–83` 包装并透传原 callback；`:85–99` 供文件前要求 Inside、Current==self、plugin pending result==CurrentContinuation，随后调用真实 handleResult。 | 可以主动发起无 UI 的官方导入／业务保存。现有试验只支持已存在官方请求的 continuation 替换。 |
| native → Dart 消息方向 | 包内 `FlutterBinaryMessenger.h:60–79`：sendOnChannel 向 Flutter side 发送消息；`:81–94` 注册接收 Flutter side 消息的 native handler。包内 `FlutterChannels.h:297–325`：invokeMethod 调 Flutter method；`:327–334` 接收 Flutter side method call。 | 同名 native plugin method 自动成为 Dart 侧入口；任意猜测 channel／method 可调用官方稿库。尚无官方 Dart 侧 save/read handler 注册证据。 |
| 通用 headless 能力 | 包内 `FlutterEngine.h:75–77`、`:314–317` 明示引擎可以无 ViewController 运行。`:179–189`、`:214–226`、`:253–269` 明示 entrypoint 是 top-level 函数，非 main 须 `@pragma(vm:entry-point)` 防 tree-shaking；首次建新 isolate，后续 run 调用立即无效。 | 同版 App 保留可用 manuscript headless entrypoint、正确 libraryURI、初始化／存储上下文或安全共享官方稿库能力。存在通用引擎 API 不等于可直接调用任意 Dart AOT 实例 save 方法。**不自启动另一个 headless main 来试错。** |
| 系统声明入口 | 指定 `Payload/Runner.app/Info.plist`：bundle=`com.duriea.turboio.research`、版本1.0.5(201)；URL types 为微信、Google 登录、`rayneo.venus.app` 与 umeng integration scheme；CFBundleDocumentTypes absent；LSSupportsOpeningDocumentsInPlace=true；UIFileSharingEnabled=false。 | scheme 内有无导航的导入 payload、保存回执或读回契约；document type 缺失亦不证明业务接口不存在。不能据 `rayneo.venus.app` 自造 deep link，导航 route 本身也不符合最新要求。 |
| Dart 业务函数／xref | `REA_TELEPROMPTER_DART.md:83–88,165–174`：两次330s和一次780s默认 App 分析均未 ready；mapping／xref／业务 pseudocode 未取得，会话已关闭。 | 存在可复用的完成分析数据库、零 xref、目标接口不存在或工具不支持 Dart。**不重跑第四次大 Ghidra 分析。** |
| 官方 create/save/read | `AIHOT_DART_MANUSCRIPT_CONTRACT.md:184–191,199–203`：字符串候选不构成函数、参数、stable ID、metadata/content 关联或 native 可调用契约。 | parse／title 映射、commit／failure边界、合法 DocID 来源、异步完成、列表／正文回读、刷新／重启、重复与未知结果对账；均 UNKNOWN。**不直接 DB write，也不以包内 isar／sqflite／shared_preferences 存在猜 schema。** |

用户已报告7392稿显示与手工保存列表正常，是手工业务成功背景；没有程序可见的官方保存回执或无 UI create 契约，不能升级验收层级。业务20 did／taskId、眼镜收稿 ACK 与 addon 文件缓存同样不提供官方稿库 CRUD 证明（`REA_IMPORT_DYNAMIC_PLAN.md:78–84`）。

## 3. 旧 UI 路线不适用最新要求

`REA_IMPORT_DYNAMIC_PLAN.md:12–16,62–67,90–94` 明确依赖用户从官方 UI 发起请求／保存，且未识别 Dart save 边界。`OFFICIAL_IMPORT_TRIAL_TEST.md:13–17,45–47,55–62` 的 Phase B 免系统文件选择仍依赖用户发起官方 import 请求、可能手工保存；USER_CONFIRMED_SAVE 不是 API receipt。

因此这些旧方案只能作为此前 continuation／黑盒消费背景，**不满足完全无页面跳转**。不再让用户重复 picker＋业务20、编辑器展示或手工保存来冒充发现新入口；也不以 UI 自动点击、Navigator route 或 deep link 绕过此要求。

## 4. 最小下一材料与观察效用门槛

下一步是取得下面替代项之一，不要求全源码，也不要求全数据库：

1. **同版厂商官方接入契约或少量目标源码**：真实导入／save／read 操作与调用方向、参数／codec、异步成功与异常、合法官方 ID 来源、标题正文读回、刷新／持久性、重复提交及未知结果对账；须明确可由 addon 安全触发且不发生页面跳转。公共契约若已闭环，无须内库 schema。
2. **匹配此 App SHA 的局部 Dart Function–Code–ObjectPool 映射／可信符号材料**：只需 import／parse／save／read 路径及真实调用边界。函数名字或地址存在不够，还须独立证明 native 可调用方式、接收对象／isolate上下文、ABI、生命周期和官方 readback。

只有先取得一个真实且技术可观察的 consumer／save／read 返回边界，才值得另行授权短时动态验证。建议效用门槛：指定准确 App 实例、一个唯一公开稿 manifest、最多一次事务且 <=120秒；只记录允许字段的 key／type／length／hash、原回调原样透传、从已证契约取得合法 ID 并限定该公开项读回；不采 private SQL、headers 内容、全 channels／全文件 I/O 或账号信息。超时即停观察，不伪造取消或成功、不自动重试；跨重启 ID 对账另需安全且可信的契约，不能拿每会话随机 HMAC 当稳定 ID。

**当前尚无该真实边界，故动态观察的技术可行性仍 UNKNOWN，本轮不实施。** 即便未来定点观察需一次 UI 动作才能取样，也必须明确它是另行授权的证据采样，不是已满足最终无跳转需求；不得将旧 UI 计划默认续用。

## 5. 交付与停止状态

- 已完成：既有证据收敛、最新官方成员 sameSHA 核验、包内公开 Flutter 入口方向／headless限制与系统元数据核验。
- 未达成：无导航官方 create／save／read、安全 native 接入、合法稳定 ID、持久回读与未知结果对账契约。
- 本轮停止：不扩展逆向、不新建探针、不装工具、不触设备／DB／私稿、不重跑大 App 分析。
- 任务完成表示**只读调查完成并交回 UNKNOWN**，不表示无 UI 官方入库功能完成。
