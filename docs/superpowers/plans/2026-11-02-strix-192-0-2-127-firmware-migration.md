# Strix OS 1.0.5.12 固件增强迁移实施方案

> **状态**：设计完成，实施阶段
> **目标**：基于官方原厂 `Strix-OS-1.0.5.12-官方原厂.zip`，将 Turbo-IO/Cheater-IO 的固件增强特性（菜单注入、主动汇报、实时字幕、HUD 状态传输）完整迁移至 1.0.5.12 AP 固件，产出可审计、可验证的 1.0.5.12 候选 OTA 包。

---

## 一、官方原厂固件基线核验

1. **输入文件**：
   - 路径：`C:/Users/duriea/Downloads/Strix-OS-1.0.5.12-官方原厂.zip`
   - 大小：`8,769,953` 字节
   - SHA-256：`b7bfd3cc73528e3e9995ad227dc877c8334d43dc8051436e2d2ccff2ca0b4133`
   - 结构：包含 1 个清单 `OtaFileInfo.json` 与 15 个载荷镜像，100% 通过大小与 MD5 强断言。

2. **跨版本载荷拓扑对比（1.0.5.12 vs 1.0.5.5 官方原厂）**：
   - **11 个完全相同载荷**：`bt_controller.bin` (625,364 B), `cb_fw_venus.bin` (114,592 B), `fac_test_img.bin` (1,821,552 B), `images.bin` (383,431 B), `lotties.bin` (651,481 B), `nuttx_audio.bin` (1,461,920 B), `ota_installer_progress.bin` (5,616 B), `pil_algo_up_demo_nand.dll` (504,388 B), `pil_algo_wakeup_dll_nand.dll` (287,560 B), `rives.bin` (689,431 B), `smf.json` (23,510 B)。
   - **4 个差异载荷**：
     * `nuttx_ap.bin` (127: 7,313,036 B vs 203: 7,208,648 B，增加 104,388 B)
     * `nuttx_apc1.bin` (127: 1,435,740 B vs 203: 1,423,532 B)
     * `nuttx_bth.bin` (127: 1,194,132 B vs 203: 1,189,216 B)
     * `pil_algo_vad_demo.dll` (127: 433,900 B vs 203: 433,900 B)

---

## 二、AP 二进制架构与补丁重定位（34 处 Hook/Trampoline）

底座同为 Bestechnic BES2700 / Cortex-M33 NuttX (`nx_bestbsp_ap`)，内部标称 `Strix OS 1.0.5`。7 个引导描述符完全匹配。

### 1. 补丁偏移映射族

TMG2 的 34 处补丁在 1.0.5.12 中全部重定位完毕：
1. **UI / 菜单系统组（平移 +0x1C598 / +116,120 字节）**：
   - 补丁 00 (show): `0x4da940` -> `0x4f6ed8` (`90b583b0`)
   - 补丁 01 (hide): `0x4da974` -> `0x4f6f0c` (`b0b582b0`)
   - 补丁 02 (destroy): `0x4d99fc` -> `0x4f5f94` (`f0b50446`)
   - 补丁 03 (wheel): `0x4da6ae` -> `0x4f6c46` (`2de9f041`)
   - 补丁 04 (event): `0x4daa40` -> `0x4f6fd8` (`b0b582b0`)
   - 补丁 07 (names): `0x4da1fc` -> `0x4f6794` (`2de9f04f`)
   - 补丁 08 (dots): `0x4d9cc8` -> `0x4f6260` (`2de9f047`)
   - 补丁 09 (delete_dots): `0x4d9ca6` -> `0x4f623e` (`98b50446`)
   - 补丁 10 (lottie): `0x4da068` -> `0x4f6600` (`b0b584b0`)
   - 补丁 11 (app_id): `0x4d9c48` -> `0x4f61e0` (`80b500af`)
   - 补丁 12 (refresh_label): `0x4da02a` -> `0x4f65c2` (`2de9f041`)
   - 补丁 13 (vm_event): `0x4d8d8c` -> `0x4f5324` (`f0b585b0`)
   - 补丁 14 (frame_start): `0x4d984a` -> `0x4f5de2` (`0628a8bf`)
   - 补丁 15 (frame_index): `0x4d9860` -> `0x4f5df8` (`1e22a0f10f03`)
   - 补丁 16 (label_frame): `0x4da42c` -> `0x4f69c4` (`2de9f04f`)
   - 补丁 17 (label_index): `0x4da780` -> `0x4f6d18` (`0629a8bf`)
   - 补丁 18 (indicator): `0x4d9e54` -> `0x4f63ec` (`2de9f04f`)
   - 补丁 19 (frame): `0x4da148` -> `0x4f66e0` (`b0b50446`)
   - 补丁 20 (render_slide): `0x4da17e` -> `0x4f6716` (`80b5b3ee0e7a`)
   - 补丁 23 (item count 7 to 16): `0x4d9bec` -> `0x4f6184` (`0721`)

2. **通信 / 消息处理组（平移 +0x19A60 / +105,056 字节）**：
   - 补丁 05 (message): `0x3ec04c` -> `0x405aac` (`b0b50c46`)
   - 补丁 24 (file cb reg): `0x3da186` -> `0x3f3be6` (`00241f48204b84610360`)
   - 补丁 25 (head state size): `0x3e8e34` -> `0x402894` (`6820` -> `8820`)
   - 补丁 26 (head state ctor): `0x3e8e3e` -> `0x40289e` (`f4f7a7f9`)
   - 补丁 27 (ce_serial_destroy): `0x3dc59c` -> `0x3f5ffc` (`98b50b4b`)
   - 补丁 28 (ce_head_notify): `0x3dd9bc` -> `0x3f741c` (`2de9f043`)
   - 补丁 31 (ce_serial_publish): `0x3e8e42` -> `0x4028a2` (`a069a661`)
   - 补丁 32 (ce_serial_retire): `0x3e8904` -> `0x402364` (`a069a361`)
   - 补丁 33 (dots_link_notify): `0x3eb452` -> `0x404eb2` (`eff7b7fa`)

3. **菜单构造指令组（平移 +0x1C59C）**：
   - 补丁 21 (284B alloc): `0x4d6cdc` -> `0x4f3278` (`fc204ef043fe`)
   - 补丁 22 (tail init): `0x4d6d06` -> `0x4f32a2` (`02f04bff`)

4. **文本渲染组（平移 +0x18BF8）**：
   - 补丁 06 (dc_text): `0x5a12b0` -> `0x5b9ea8` (`f0b5c3b0`)

5. **底层串口服务组（平移 +0x13C0）**：
   - 补丁 29 (serial start): `0x358d74` -> `0x35a134` (`b0b5204a`)
   - 补丁 30 (serial stop): `0x358e18` -> `0x35a1d8` (`b0b5204a`)

### 2. 外部符号链接（link.ld）
- 127 AP 原生导出表位于 `0x256e90`（627 项核心 POSIX/NuttX 接口）。
- LVGL 与 RayNeo AppListView / BLE / 串口符号基于函数特征与文字池交叉定位。

### 3. 增强模块追加位置
- 原厂 AP 长度：`7,313,036` 字节。
- 末尾 8 字节保留（NuttX footer）。
- 模块起始位置：`0x108896c0`（64 字节对齐）。

---

## 三、实施与离线审计步骤

1. 创建 1.0.5.12 独立构建树：`firmware-research/strix-1.0.5.12-tmg3/`
2. 依据新偏移生成 `build-inputs.json`、`trampolines.S`、`link.ld`
3. 复用经过验证的 OHOS clang 15 工具链编译模块 ELF 并打入 AP
4. 运行完整离线审计验证器，断言 34 处补丁、启动描述符及未变更载荷
5. 更新 Android 宿主 OTA 配置文件（`OtaProfile.java`），接入 1.0.5.12 候选 Profile 与 `01.00.04.0012` 查询码匹配。
