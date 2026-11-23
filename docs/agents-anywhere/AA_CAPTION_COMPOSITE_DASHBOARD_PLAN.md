# 雷鸟 iO 看板复合卡片开发方案：实时字幕 × Agents-Anywhere

> **适用场景：** 本文档为 Windows 开发环境（或下游 Agent）提供完整的架构认知与分步执行清单。将「实时字幕」流与「Agents-Anywhere (DSH)」状态流聚合于眼镜看板主页的同一个卡片槽位（256×194）中显示。
> **基线环境：** Android V2 路线（宿主 RayNeo AI 1.0.5 (201) / Android 15 / Strix 1.0.5.5 / TCE1 卡片协议）。

---

## 1. 核心目标与技术裁决

### 1.1 需求目标
在雷鸟 iO 眼镜的原生看板主页（Dashboard）右侧卡片槽位内，实现类似官方单卡槽分区的**复合信息卡片（Composite Card）**：
- **上半区**：展示手机麦克风采集的最新「实时字幕 / 本地翻译」句子（流式或短句刷新）。
- **下半区**：展示「Agents-Anywhere（AA / 电脑端 DSH）」的当前任务状态或返回结果摘要（如 `Nova 待命`、`DSH 执行中 · 编译通过`、`会话已锁定` 等）。

### 1.2 固件定制裁决（关键结论）
* **结论：不需要定制或重新编译 AP 固件。**
* **技术依据：**
  1. 眼镜底座（StrixOS 1.0.5.5 / TCE1 运行时）的卡片引擎本质是一个 **256×194 像素的绝对坐标平面画布**（见 `firmware-research/strix-1.0.5.5-tmg2/src/dashboard-editor-v1/editor.c` 与 `editor_native.c`）。
  2. 固件支持单卡最多 12 个定位组件，涵盖 `frame`（边框）、`divider`（分隔线）、`text`（文字）、`icon`（矢量图标）、`progress`（进度条）等基础原语。
  3. 截图中“两个组件在一个显示框内”，在固件眼里**只是 1 张拥有 2 个矩形边框和对应文字图标的普通卡片**。
  4. 真正的改动全部收敛在 **Android 端（`android-addon/`）的数据流聚合与蓝牙更新逻辑**。

---

## 2. 界面排版与 TCE1 协议规格设计

在 256×194 的卡片有效区域内，采用 50/50 纵向分割方案，严格遵守 TCE1 校验约束：
- 坐标必须全为偶数（`x, y, w, h % 2 == 0`）。
- 边距保留 6px 安全区（`x >= 6, y >= 6, x + w <= 250, y + h <= 188`）。
- 文本高度约束：`h >= font + 4`，宽度 `w >= 16`，字体限 `14, 16, 18, 20, 24, 28`。

### 2.1 布局几何与组件清单（共 8 个组件，限额 12 个）

```
(0,0)┌─────────────────────────────────────────────────────────┐(256,0)
     │ 6px 安全边距                                              │
     │  ┌───────────────────────────────────────────────────┐  │
     │  │ [图标 16x16]  实时字幕 (font 16)                    │  │ 上半区：字幕卡
     │  │                                                   │  │ frame: (6, 6, 238, 88)
     │  │  "看 设计稿能先出一版吗？" (font 16, 最长 96B)    │  │
     │  └───────────────────────────────────────────────────┘  │
     │  ────────────────── (y=96 可选细分割线) ──────────────── │
     │  ┌───────────────────────────────────────────────────┐  │
     │  │ [图标 16x16]  Agents-Anywhere (font 16)           │  │ 下半区：AA 状态卡
     │  │                                                   │  │ frame: (6, 98, 238, 88)
     │  │  "Nova 待命" 或 "DSH: 单元测试全部通过"            │  │
     │  └───────────────────────────────────────────────────┘  │
(0,194)└─────────────────────────────────────────────────────────┘(256,194)
```

| 组件 ID | 类型 (`kind`) | 坐标 $(x, y)$ | 尺寸 $(w, h)$ | 属性参数 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `sub_box` | `frame` | $(6, 6)$ | $(238, 88)$ | - | 上半区外边框 |
| `sub_icon` | `icon` | $(12, 12)$ | $(16, 16)$ | `icon: 0` | 字幕/语音小图标 |
| `sub_title` | `text` | $(34, 10)$ | $(200, 20)$ | `font: 16, align: "left"` | 标题固定文字："实时字幕" |
| `sub_body` | `text` | $(12, 34)$ | $(224, 52)$ | `font: 16, align: "left"` | 动态字幕内容，自动换行截断（$\le 96$ 字节） |
| `aa_box` | `frame` | $(6, 98)$ | $(238, 88)$ | - | 下半区外边框 |
| `aa_icon` | `icon` | $(12, 104)$ | $(16, 16)$ | `icon: 1` | 助手/星形小图标 |
| `aa_title` | `text` | $(34, 102)$ | $(200, 20)$ | `font: 16, align: "left"` | 标题文字："Agents-Anywhere" 或当前别名 |
| `aa_body` | `text` | $(12, 126)$ | $(224, 52)$ | `font: 16, align: "left"` | 动态 Agent 状态或执行结果（$\le 96$ 字节） |

*注：资源消耗评估：组件数 8 $\le 12$；像素预算 $16\times 16 \times 2 = 512 \le 32768$ 字节；封装后字节码 $\approx 280 \le 2048$ 字节。完全在现有 TCE1 与 Android `CardCodec` 验证门禁内。*

---

## 3. 架构设计：Android 端数据聚合与显示仲裁

### 3.1 核心痛点与冲突消除
目前 `android-addon` 存在两套互不相同的眼镜显示机制：
1. **全屏独占式字幕通道（`NavGlasses`）**：
   - 依赖 BLE 业务通道 19 (`AI_SUBTITLE`)，发起 `send(7, ...)` 打开全屏文字显示。
   - 特点：独占屏幕，会遮挡原生看板，且退出需复杂的租赁与清理确认（`ClearState`）。
2. **看板卡片通道（`CardCodec` / `A2UIProtocol`）**：
   - 通过 `cmd: "widget_install"` 安装到系统的 `widgets_v2` 看板槽位。
   - 特点：常驻于表盘右侧，不阻断系统轮播菜单，由手机按需下发 `dashboard_update` 或全卡刷新。

**关键改造原则：**
- **不可让两个线程分别向眼镜发送显示指令。**
- 新增 `CompositeCardController` 作为单点汇聚器（Single Point of Truth），手机后台将字幕文本和 AA 状态汇聚为一张卡片，统一走卡片通道刷新。

### 3.2 数据流时序拓扑

```
[麦克风/云端 ASR] ──────────┐ (语音事件)
                           ▼
                  CloudCaption.java
                           │ onTranscript(text)
                           ▼
             ┌───────────────────────────────┐
             │ CompositeCardController.java  │ <──── 节流防抖 (800ms)
             │  - 最新字幕: mLatestCaption   │ <──── 状态对比与去重
             │  - 最新状态: mLatestAaStatus  │
             └───────────────┬───────────────┘
                             ▲
                             │ onStatusChanged(status)
                  AaHostBridge / DshClient
[远端 DSH / AA Server] ──────┘ (任务/会话事件)
                             │
                             ▼ 构建复合 CardDocument
                      CardCodec.java (TCE1 编码)
                             │
                             ▼ 蓝牙分发 (widget_install / update)
                     NativeTransfer / 官方蓝牙
                             │
                             ▼ 渲染显示
                   [雷鸟 iO 镜片右侧看板槽位]
```

---

## 4. 分步实施执行清单（Windows 开发机）

### 任务 1：定义复合卡片契约与离线单元测试
* [ ] **1.1 新增模板生成器**：
  在 `android-addon/src/com/turboio/addon/CardCodec.java` 中增加复合卡片专用构建方法：
  ```java
  public static Map<String, Object> compositeCard(String id, String captionText, String aaStatusText, String aaTitle)
  ```
  内部自动裁切文本至 UTF-8 96 字节以内，过滤控制字符，填充 8 个固定位置组件。
* [ ] **1.2 编写离线 JUnit 测试**：
  在 `android-addon/tests/` 下新建 `CompositeCardTest.java`：
  - 测试 1：空文本、超长中文（>96 字节截断）、特殊符号时的合法性。
  - 测试 2：调用 `CardCodec.encode()`，断言字节流以 `TCE1` 开头、长度 $\le 2048$、组件数 $= 8$。
  - 测试 3：调用 `CardCodec.install()`，验证外层包装为符合 StrixOS 规范的 `a2ui` JSON 协议。

### 任务 2：实现 Android 端状态聚合器（`CompositeCardController`）
* [ ] **2.1 新建单例控制器**：
  创建 `android-addon/src/com/turboio/addon/CompositeCardController.java`：
  - 维护内部状态：`captionText`、`aaTitle`、`aaStatus`、`lastSentHash`、`isCardInstalled`。
  - 引入 `Handler` 防抖机制（`DEBOUNCE_MS = 800`）：连续的流式字幕输入只更新内存，等待 800ms 静默期或每 1.5 秒强制刷新一次，避免频繁 BLE 通信导致蓝牙拥塞。
  - 提供开关：`enableCompositeMode(boolean enable)`。启用时向眼镜安装该卡片，停用时从 `widgets_v2` 卸载。
* [ ] **2.2 对接 `CloudCaption.java`**：
  - 在 `CloudCaption.event()` 中接收到转写文本后，增加分支：若复合看板卡片已激活，将文本推给 `CompositeCardController.updateCaption(text)`，同时**跳过** `NavGlasses.offer()` 的全屏独占通道。
* [ ] **2.3 对接 `AaHostBridge.java` 与 `ToolClient.java`**：
  - 监听 DSH 任务派发状态：当状态转移为 `submitted`、`running`、`completed`、`failed` 时，提取简要摘要（如 `ToolClient` 的最新返回值或错误码）。
  - 调用 `CompositeCardController.updateAaStatus(status, title)`。

### 任务 3：UI 设置入口与用户偏好持久化
* [ ] **3.1 新增控制开关**：
  在 `android-addon/src/com/turboio/addon/EditorialUI.java` 的“设置”或“看板”页中，增加「复合看板：实时字幕 + AA」开关。
* [ ] **3.2 偏好保存与互斥**：
  保存在 `SharedPreferences ("turboio_dashboard_composite")`。
  互斥逻辑：当该模式开启时，提示用户“眼镜主页将显示复合卡片；若需要全屏大字字幕，请在设置中切回独占模式”。

---

## 5. 编译、验证与交付门禁（Windows 命令）

在 Windows 开发电脑的 PowerShell 终端中执行以下验证步骤：

### 5.1 纯 Java 离线编译与测试（无需手机与眼镜）
```powershell
# 1. 确保使用 JDK 17
$JavaHome = "C:\Program Files\Java\jdk-17"  # 根据本机实际路径调整
$env:PATH = "$JavaHome\bin;$env:PATH"

# 2. 编译并运行复合卡片协议测试
cd \path\to\Cheater-IO
javac -encoding UTF-8 -cp "artifacts/tools/android-sdk/platforms/android-36/android.jar;android-addon/src;android-addon/tests" `
  android-addon/src/com/turboio/addon/BoundedJson.java `
  android-addon/src/com/turboio/addon/CardCodec.java `
  android-addon/tests/CompositeCardTest.java

java -cp "android-addon/src;android-addon/tests;artifacts/tools/android-sdk/platforms/android-36/android.jar" `
  CompositeCardTest
```
*门禁要求：* 控制台输出 `PASS: Composite card layout and TCE1 limits verified`，退出码为 0。

### 5.2 宿主完整打包与装配
```powershell
# 运行既有的 Windows 嵌入装配脚本
.\android-addon\build-embedded-windows.ps1 `
  -PayloadApk "Agents-Anywhere\android\app\build\outputs\embedded\aa-embedded-payload.apk" `
  -PayloadSidecar "Agents-Anywhere\android\app\build\outputs\embedded\payload.json" `
  -OutputDirectory "build-output-composite"
```
*门禁要求：*
1. `zipalign -c` 检查通过。
2. 生成的 APK 包含最新的 `classes4.dex`。

---

## 6. 避坑指南与未验证边界声明

1. **文字溢出与乱码：**
   TCE1 原生解码器对 UTF-8 的严格校验（见 `editor.c` 里的 `utf8()` 函数）会拒绝非法的多字节序列。中文必须严格保证按 UTF-8 字符边界截断，不能直接按裸 byte 强行切断，否则会导致固件整卡解析失败返回 `-1`。
2. **高频更新导致蓝牙丢包：**
   语音识别（ASR）可能每 100ms 吐出一个字，绝对不可将每个字直接触发一次蓝牙卡片刷新。**必须在 Android 端实施 800ms~1500ms 的防抖合并**，只在短暂停顿或标点出现时才推送到眼镜。
3. **固件休眠竞争：**
   眼镜息屏时，向其发送卡片更新会被底座丢弃或返回等待。需在 Android 侧检查连接状态与眼镜活跃状态，避免在息屏时堆积大量重复任务。
4. **验证范围声明：**
   本文档规划的代码改动在现有架构中具备完备的协议依据；但在真实设备上，需依赖特定的 Android 手机蓝牙协议栈与 RayNeo 固件版本协同工作。首次推送真机前，需在手机端「我的仪表盘」中人工确认卡片预览效果。
