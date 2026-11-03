# 雷鸟眼镜系统主页常驻 AA 工作台与实时字幕卡片实施计划

> 状态：供用户审阅；本文件是实施计划，目标分支：android。

## 目标

在雷鸟智能眼镜系统主页（Launcher）常驻展示「AA 工作台」卡片组件（TCE1 架构），与眼镜左侧原生的时间、日期、天气、电量并存。
右侧卡片采用上下双区动静分离排版：
- 上半区：动态展示实时 ASR 字幕（无语音时展示最近记录或待命占位，有语音时实时刷新）；
- 下半区：常驻展示「AA工作台」状态，待命时明确显示「AA工作台 · 待命」，有任务时展示任务目标与结果摘要。

在保持手机端 AA 监听和 DSH 任务闭环的同时，通过底层 `widget_install` 协议将组件置顶注册至眼镜主页首位。

---

## 架构与硬件硬约束

1. **Launcher TCE1 原生卡片规约**：
   - 位于主页右侧区域，最大尺寸 256x194，属于 `a2ui` 类型的 `widgets_v2` 首位卡片（Slot 0）。
   - 总组件数 <= 12 个，像素预算 <= 32768，单包大小 <= 2048 字节。
   - 坐标必须全为偶数：`x \in [6, 248]`, `y \in [6, 186]`, `w \in [2, 244]`, `h \in [2, 182]`，且 `x+w <= 250`, `y+h <= 188`。
   - 字体大小必须严格在 [14, 16, 18, 20, 24, 28] 之中，文本框高度必须满足 `h >= font + 4`。
   - 字符按 Unicode 代码点安全截断，防止代理对（Surrogate Pair）拆裂。

2. **通信与传输节流约束**：
   - 基于 `CardTransport` 和 `NativeTransfer.sendMessage`，持有独占消息租约。
   - 蓝牙传输实施 700ms~1000ms 防抖节流，避免高频 ASR 词流冲垮 BLE GATT 通道。
   - 数据未发生实质变更时（内容哈希一致）自动跳过传输。

3. **设置开关与生命周期**：
   - 新增偏好设置项 `dsh_workbench_card_enabled`（默认开启）。
   - 关闭开关时：调用 `CardTransport.remove` 移除眼镜主页卡片，但不中断手机端 AA 监听与后台任务。
   - 开启或连接时：检查眼镜卡片列表，若不存在或版本滞后则自动重新下发。

---

## 实施任务拆解

### 任务 1：纯 Java 卡片布局生成模型 (DshWorkbenchCardModel)
- **文件**：
  - 新建 `android-addon/src/com/turboio/addon/DshWorkbenchCardModel.java`
  - 新建 `android-addon/tests/DshWorkbenchCardModelTest.java`
- **内容**：
  - 输入：ASR 文本/状态、DshDashboardModel.View 任务视图、单调时钟时间戳。
  - 输出：符合 TCE1 规约的 Map 结构（包含标题、状态徽标、分割线、字幕区、工作台摘要区、结果查看指引）。
  - 测试：验证待命态、流式字幕态、任务结果态的坐标偶数性、字体合法性、尺寸溢出截断、不超过 12 个组件、总字节不超过 2048 字节。

### 任务 2：主页卡片状态同步与传输调度 (DshWorkbenchCardSync)
- **文件**：
  - 新建 `android-addon/src/com/turboio/addon/DshWorkbenchCardSync.java`
- **内容**：
  - 作为单例同步协调器，订阅 DSH 任务看板（DshDashboardModel）与实时 ASR（TurboAddon.onAsr）。
  - 实现防抖节流逻辑（最小 700ms 间隔），内容指纹对比防重发。
  - 调用 `CardTransport.publish(doc, top=true)` 下发安装到主页首位。

### 任务 3：宿主入口接入与偏好设置 (TurboAddon & DshSettings)
- **文件**：
  - 修改 `android-addon/src/com/turboio/addon/TurboAddon.java`：在 ASR 分发链中注入卡片同步钩子。
  - 修改 `android-addon/src/com/turboio/addon/DshSettings.java`：在 AA 设置界面增加“眼镜主页 AA 工作台卡片”开关，绑定开关动作与卡片清理。

### 任务 4：离线全量回归与测试 Runner 集成
- **文件**：
  - 修改 `android-addon/tests/run-proactive-integration.ps1`（或新增独立 runner）。
- **内容**：
  - 运行 DshWorkbenchCardModel 完整测试套件，连带现有 HUD、Round、Display 套件全量回归，确保 100% 通过。

### 任务 5：APK 打包构建与交付报告
- **文件**：
  - 执行 `android-addon/build-embedded-windows.ps1` 构建签名 APK。
- **内容**：
  - 产出新版 APK 并校验 SHA-256 哈希与包内 classes/assets 完整性。
  - 整理变更与验证证据，将任务状态推进至 `in_review`，挂载 `waitingForHuman`，等待人工真机光学验收。

---

## 验收准则 (DoD)

1. [软件逻辑] `DshWorkbenchCardModelTest` 包含的所有边界测试、TCE1 编码测试 100% 通过。
2. [规约守卫] 生成的卡片数据严格满足雷鸟 Launcher 硬件限制：组件数 <= 12、像素 <= 32768、体积 <= 2048 字节、坐标偶数对齐。
3. [离线回归] 既有 640+ 项测试用例全量通过，无回归破坏。
4. [构建产物] 成功构建可安装的嵌入式 APK，并就地记录产物哈希。
5. [终审归人] 提交结构化交付报告，任务保持 `in_review` + `waitingForHuman`，保留人工真机光学与佩戴体验最终确认。
