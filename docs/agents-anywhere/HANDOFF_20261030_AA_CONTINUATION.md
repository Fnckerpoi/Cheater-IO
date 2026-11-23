# AA 嵌入续接记录（2026-10-30）

## 源码与提交

- Turbo-IO android 分支：`7c41354`，宿主嵌入、测试及原交接文档。
- Agents-Anywhere 独立仓库分支 `fix/apk/embedded-workbench-bootstrap`：`d6103e1ab7f486019ce52852d5db585c23ec5371`，包含完整嵌入源码、测试及门禁；其父提交 `3887f05e` 仅包含面板，不能单独作为完整交付。两仓库没有建立 Git 子模块关联，Turbo-IO 的状态仍显示未跟踪的 `Agents-Anywhere/`。
- 引导加载对归属匹配的 `DIRECTORY_FAILED` 最多重试一次；不触发保存，过期响应丢弃，持续失败保留未验证选择。

## 验证证据与范围

- AA JDK 17：嵌入端测试 XML 共 53 项（14 + 30 + 9），失败、错误、跳过均为 0；`:app:assembleEmbeddedDebug` 及独立 `:app:assembleDebug`、`:app:embedPayloadSidecar` 成功。最终构建日志位于 `Agents-Anywhere/android/build/bootstrap-verification-final.log` 和 `embedded-commit-verification.log`。存在 AGP/SDK36 与 D8 元数据警告。
- 子代理执行的载荷门禁报告通过：保留成员 1476 项哈希一致，native 由 28 项剔除至 0，sidecar 校验通过。嵌入 APK SHA-256：`D8CED9C1B206B97877883F005CD37BFBAC02830A277B7A2E0CE007E2C474ECB2`。
- 本轮更新前设备安装的旧 v3 候选 `89253D8A719D671C4122E45F294D51854B3613814EA0D39A714F6A4CDFE2C1B9` 与设备 base.apk 哈希一致，签名 v3 验证通过；包版本 1.0.5/201，userId 10271，lastUpdateTime 2026-10-29 18:38:13。历史安装命令返回码及安装前后数据连续性未取得，不能由当前包身份推断。
- 在上述旧 v3 包上新建独立测试会话 `aa_c37d1d65a3ef7f26_sess__LfWx5fMnG1M0Q`，首条消息完成并返回回答；同一会话续聊 `reply received thanks` 提交后查询为 Completed，返回 “You’re welcome! Glad the reply came through.”。这是旧 v3 的业务证据，不覆盖本次新修复。

## 最新状态与待完成项

- 新载荷已同包组装、审计和签名：候选 SHA-256 `adcda2d906730a4fcc1de3c935fb00bcf90baf8c111e701896d0ecba2f67a67a`，设备 base.apk 哈希一致。YAL-AL10 冷启动成功，可见账号、环境和绑定与安装前一致；原安装进程退出码缺失，完整应用存储连续性未验证。设备报告见 `artifacts/aa-device-acceptance-20261030-010000/REPORT.md`。真机受控目录失败注入尚未进行。
- 冷启动后会话列表首次加载失败，手动刷新后恢复。只读排查：`EmbeddedWorkbench.kt` 的 `reload` 捕获了非取消异常后显示该英文提示，但未记录异常或阶段；发生时的 main logcat 已覆盖，因此无法确定请求、解析或后续处理的具体原因。配置目录成功，不可归因于配置重试。后续可在无运行任务及待发草稿时受控复现，或增加不含凭据、URL、异常正文及响应正文的阶段和状态码诊断。
- 原通用 PrivateArtifactAudit 因工作区资源换行差异而失败。只读三方复核确认固定 PUBLIC、005400 候选及固定 Git 提交的全部 35 项允许资源字节一致，3 项文本工作区受 CRLF 转换影响。该复核不改写历史失败。随后仅对 3 项文本配置精确 LF 检出规则，并显式 UTF-8 读取 catalog；主代理独立执行原 ReaderDemoTest 和原 PrivateArtifactAudit（候选专属 DEX、utf8=0）均 exit 0，检查 173 项输入，模式命中为空。原始字节比较断言未改变，三项单字节篡改负向测试由子代理验证均拒绝。详见 android-addon/docs/PUBLIC_ASSET_AUDIT_WINDOWS.md；不将其扩展为完整 AA 安全审计。
- 后续诊断提交 `7d799bc7d2cb37af23f6718c683b01f670a8e466` 已独立核对（AA 工作区干净）：失败日志只记录固定阶段/分类、可用 HTTP 状态码、耗时。119 项单元测试零失败、嵌入构建成功。诊断版尚未生成新的宿主封装或安装，手机仍运行上述 `adcda2d9…` 候选；根因仍未确认。
- 验证账号切换后迟到结果丢弃、旋转及进程重建；保留未知送达和身份校验守卫。
- 安装后验收时测试会话为设备绑定目标，执行权限为用户确认保留的 `danger-full-access`。随后用户说明继续开发指令来自雷鸟 IO；当前会话可能正由设备使用，不自动重启或向当前会话派发测试。
- 原交接建议的安装历史返回码与安装前数据基线缺失；如需声明覆盖安装和数据保留，须另行安排可比基线与记录。
