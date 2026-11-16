# TMG4 固件菜单覆盖机制调研

> **范围：** 官方 Strix OS 1.0.6.11 基座（`strix-1.0.6.11-tmg4`）上，自定义菜单如何叠加在原厂轮播菜单之上。
> **状态：** 调研结论。依据仓库内源码、构建脚本、构建产物与设备验收记录核对；未涉及真机刷写验证的部分已在文末标注。
> **关联：** `TMG4-10611-requirements.md`（验收项 F2）、`TMG4-10611-development.md`

## 1. 原厂菜单没有源码，只有二进制

官方固件以 OTA 包里的 `nuttx_ap.bin` 形式交付，仓库中不存在原厂菜单的 C 源码。`firmware-research/` 下所有 `.c` 文件都是本项目自写的代码。

构建入口 `firmware-research/strix-1.0.6.11-tmg4/build.py` 用两条哈希钉子固定基座：

- `build.py:39` 校验输入 ZIP 的 SHA-256 等于 `build-inputs.json` 的 `stockZIP`；
- `build.py:61` 校验 ZIP 内 `nuttx_ap.bin` 的 SHA-256 等于 `stockAP`。

**官方换任何版本，构建在第一步就抛异常退出，不会静默产出一个基于错误基座的包。** 这是整条覆盖链成立的前提——所有偏移、地址、项数，都只对当前这一个基座有意义。

## 2. 覆盖手段：函数入口劫持（inline hook）+ 叠加渲染

不修改原厂绘制代码，只在原厂函数入口写跳转，让它先进入我们的代码，再由我们的代码决定是否交回原厂逻辑。

### 2.1 我们自己的代码作为一个独立模块塞进镜像空隙

`build.py:126` 把编译出的 `module.bin` 追加到 AP 镜像中，链接脚本 `link.ld` 末尾指定起始地址：

```
. = 0x1092a000;
```

即 `build-inputs.json` 的 `moduleVA`。这段区域在原厂镜像里是空的。`audit()` 会断言这段空隙全为 0、模块外无多余字节改动。

### 2.2 原厂入口被覆盖成跳转

以"显示菜单"为例，`build-inputs.json` 中有这条补丁：

```json
{"offset":"0x515200","role":"Entry trampoline show","before":"90b583b0","kind":"B.W","symbol":"m8_hook_show"}
```

- 原厂函数内存地址 = `FLASH(0x10190000) + 0x515200 = 0x106a5200`；
- 构建时把其开头 4 字节 `90 b5 83 b0` 覆盖为 `B.W m8_hook_show`，直接跳到我们的函数；
- `before` 字段在 `build.py:133` 被断言比对，原厂镜像不符即失败，不会改错位置。

补丁表共 34 条，覆盖构造、显示、隐藏、销毁、按键、滚轮、名称、帧更新、指示器等入口。

### 2.3 通过 trampoline 仍能调回原厂原逻辑

`trampolines.S` 里 `stock_show` 这样的桩保留被覆盖掉的原始 4 字节，然后跳到原函数第 5 个字节继续执行：

```asm
stock_show:
  .byte 0x90,0xb5,0x83,0xb0   ; 被覆盖的原始前 4 字节，原样搬过来
  b.w resume_show             ; 跳到原函数第 5 字节继续执行
```

`link.ld` 中 `resume_show = 0x106a5205`，正好是 `0x106a5200 + 4`（加 Thumb 标志位）。同理 `resume_hide = 0x106a5239` 对应 `0x515234`。

因此我们的 hook 里写 `stock_show(app)` 就是执行原厂原逻辑，之后还能接自己的 `fm_show(app)`：

```c
void m8_hook_show(void *app){ retire(app); stock_show(app); ...; fm_show(app); }
```

桩用 `push/pop r1-r3` 保留寄存器，因为原厂调用方依赖超出 AAPCS 约定的整单元寄存器知识。

### 2.4 直接调用原厂函数与读写原厂对象

`link.ld` 前半段是原厂函数地址表，例如：

```
tio_lv_obj_create_ex = 0x10567a5d;
native_label_text    = 0x104e6369;
stream_timer_create  = 0x104db611;
```

原厂菜单本身用 LVGL 实现，所以我们的代码能直接调原厂的 LVGL 函数，在原厂菜单父容器 `ptr(app,4)` 下创建自己的控件树。

原厂菜单对象的字段靠偏移直接读写，偏移记录在 `firmware_profile.h`：

| 字段 | 偏移 |
|---|---|
| 父容器 | `app + 4` |
| 各菜单行（8 个） | `app + 0xc + 4*i` |
| 选中下标 | `app + 0x98` |
| 滚动帧号 | `app + 0xa0` |

补丁 `"300-byte menu allocation"` 把分配函数换成 `port_allocate_menu`，在原厂对象尾部多留出 `M8NativeTail` 字段，用于挂我们自己的槽位。

## 3. 索引是唯一的桥

### 3.1 原厂轮播驱动下标，我们只读

下标来自原厂轮播选中字 `app + 0x98`，由 `fm_sync()` 每帧读取，交给我们的渲染器：

```c
// overrides/focus-v1/focus_menu_native.c
int index = (int)*(uint32_t *)((uint8_t *)app + 0x98);
if (index < 0 || index >= FM_ITEMS) return;
```

### 3.2 帧到下标的映射由我们重写

`m8_hook_frame()` 与 `m8_hook_label_frame()` 拦截原厂的帧更新，按我们自己的映射把帧位置译码为下标并写回：

```c
// overrides/music-runtime-v1/menu10.c
int m8_impl_frame_start(int index){ return 15 + 30*clamp(index,0,9); }   // 每项 30 帧
```

项数本身也改了：补丁 `"New stock carousel item count 8 to 14"` 在 `0x5144b0` 处把 `0821`（`movs r1,#8`）改成 `0e21`（`#14`）。原厂轮播由此认为有 14 项，下标 8–13 才存在。

### 3.3 下标分两段，绑定方式不同

| 下标 | 名称 | 绑定方式 |
|---|---|---|
| 0–7 | 录音 实时字幕 … 勿扰模式 | 交回原厂：`stock_event()` / `stock_refresh_label()` |
| 8+ | 显示测试 我的应用 … | 我们自己的显式分支：`m8_hook_event()` 里逐个 `if(index==N)` 调 launcher |

原厂段（0–7）的**功能由原厂索引决定，标签由 `names[]` 决定**，两者靠"下标相等"隐式约定。自定义段（8+）的绑定是写死的显式分支，不会错位。

## 4. 布局：独立渲染层，与原厂轮播视觉解耦

原厂轮播的动画、绘制、控件全部被隐藏，我们在上面盖一层自己的网格、动画和双缓冲，但**下标语义保持一致**。

### 4.1 几何：固定 3 列 × 2/3 行

`overrides/focus-v1/focus_menu.c:11-14`：

```c
static unsigned per(const FMView *v) { return v->rows * 3; }
static unsigned icon_size(const FMView *v) { return v->rows == 3 ? 32 : 40; }
static int center(unsigned cell) { return 92 + 178 * (int)(cell % 3); }
static int top(const FMView *v, unsigned cell) {
    return (v->rows == 3 ? 27 : 44) + (int)(cell / 3) * (v->rows == 3 ? 64 : 86);
}
```

| 行数 | 每页格数 | 图标尺寸 | 行间距 |
|---|---|---|---|
| 3 行 | 9 | 32×32 | 64 px |
| 2 行 | 6 | 40×40 | 86 px |

行数由 `fm_menu_rows()` 从持久存储读，`fm_relayout()` 可动态切换。

### 4.2 下标直接映射到网格格

`render()` 不做任何重排，原厂下标 N 就是网格第 N 格：

```c
for (unsigned i = 0; i < FM_CELLS; i++) {
    unsigned index = (unsigned)v->selected / cells * cells + i;
    bool show = i < cells && index < FM_ITEMS;
    v->api.place(v->api.ctx, v->icons[i],
        center(i) - (int)size / 2 + offset, top(v, i), size, size);
    v->api.place(v->api.ctx, v->labels[i],
        center(i) - 80 + offset, top(v, i) + (int)size + 3, 160, 20);
    v->api.text_static(v->api.ctx, v->labels[i], names[index]);
}
```

尾页不满格由 `show` 判定吸收，`FM_CELLS`（控件数组上限）与项数解耦。

### 4.3 动画自实现，不用原厂轮播动画

`sample()` 做三次方缓出插值，跨页 120 ms、同页移动 160 ms，框架块滑位 + 选中格权重 0→256。

### 4.4 双缓冲，写/交换只在渲染器空闲时发生

`FMView` 预分配两组像素 `pixels[2][FM_CELLS][FM_ICON*FM_ICON]`，每帧写一组、显示一组，提交后才切换 `bank`。`fm_view_update()` 与 `fm_view_step()` 入口都检查 `api->idle()`。

### 4.5 原厂控件被隐藏

`hide_legacy()` 隐藏原厂的行、图标与自定义槽位，避免两套 UI 叠在一起显示。

## 5. 覆盖链路总览

```
原厂轮播滚动
  → m8_hook_frame / m8_hook_label_frame 拦截帧
    → 帧号译码为下标，写回 app+0x98
      → fm_sync() 读取下标，检查空闲与布局行数
        → hide_legacy() 隐藏原厂控件
        → fm_view_update() → render()
          ├─ 网格坐标 center()/top()
          ├─ 双缓冲渲染图标 + 标签
          ├─ 动画插值 frame_x/frame_y/weights
          └─ 页码指示器
      → 用户点击 → m8_hook_event()
        ├─ 下标 < 8：原厂项 → stock_event()
        └─ 下标 ≥ 8：自定义项 → 对应 launcher
```

**设计要点：** 只借用原厂的索引状态机（帧→下标），不借用它的视觉表现。原厂段功能行为不变，自定义段完全由我们控制。

## 6. `names[]` 与功能绑定的关系

`names[]`（`overrides/focus-v1/focus_menu.c:8`）只决定**画什么字**，全文只有三处引用（`:8` 定义、`:9` 静态断言、`:74` 唯一使用点）：

```c
v->api.text_static(v->api.ctx, v->labels[i], names[index]);
```

真正的功能绑定是同一整数下标走 `menu10.c` 里的另一张表（见第 3.3 节）。图标由 `map[]`（`focus_menu.c:42`）单独置换到图标素材槽位。

**因此三张表（`names[]`、`map[]`、功能绑定表）各说各话，靠下标相等隐式对齐，没有任何断言。**

## 7. 已核实的防护与缺口

### 已有的防护

- **基座钉死**（`build.py:39`、`build.py:61`）：换 ZIP 或换 AP 直接拒绝构建。
- **补丁前字节断言**（`build.py:133`）：原厂镜像不符即失败。
- **字节级审计**（`build.py:172-183`）：出货 AP 与原厂 AP 逐字节比对，除 34 条白名单补丁外必须完全相同。
- **`_Static_assert`**：`names[]` 长度与 `FM_ITEMS` 一致、`FMControl` 不超 32 KiB、`TNNavSlot` 字段偏移不重叠。

### 缺口

- **8 个出货 override 文件未被清单钉住。** `build.py:86` 对有 `overrides/` 对应文件的行跳过 sha 校验，71 项清单行中只有 63 项生效。这些行的 manifest sha256 描述的是**打补丁后的基座内容**，不是实际出货的 override 内容。清单审计通过 ≠ 出货文件可复现。
- **下标→功能无断言。** `names[]`、`map[]`、功能绑定表之间没有交叉校验。任何一处插入/删除项，两边都不报错，只会静默错位。
- **原厂段顺序靠人工核对。** 该顺序来源是首次真机观察后硬编码（`strix-1.0.4.12/native-navigation/USAGE.md:24` 记录"核对原七个菜单"）。代码不从原厂二进制自动解析菜单字符串，官方换顺序后必须人工更新。
- **宿主测试未接入。** `tests/menu_render_test.c` 用 `#include TMG4_MENU_SOURCE`，该宏全仓无定义，编译不过；补丁后的 `dots-v1/dots_menu_test.c` 仍断言 18 项，与出货 14 项不符，会失败。两者都不在 CI 范围内（`.github/workflows/` 仅覆盖 `official-addon/research/**` 与 dashboard-service）。
- **真机无眼镜端菜单证据。** `artifacts/tmg4-device-acceptance-20261114/` 中 `connection-result.md` 明确"当前镜片菜单及功能仍待刷写后人工核对"；现有 XML dump 均为手机侧 `uiautomator` 输出，无眼镜端菜单截图。

## 8. 未确认项

**原厂函数地址与结构体偏移的来源过程未在仓库中找到记录。** `link.ld`、`firmware_profile.h`、`metadata/symbols.json`（含 flash 基址 `0x10190000`、段表、入口点）记录了结果，但没有逆向过程文档。用何种工具（IDA / Ghidra / 其他）反汇编得出，属于推断，未核实。

**核实方法：** 用反汇编工具打开原厂 `nuttx_ap.bin`，基址设 `0x10190000`，跳到 `0x106a5200`，检查开头 4 字节是否为 `90 b5 83 b0`。对上即说明入口劫持的定位正确。

## 9. 备注：工作区状态

调研期间 `overrides/focus-v1/focus_menu.c` 正从 14 项改为 10 项（新增"有问必答"、删除 4 项自定义），`map[]`、`FM_ITEMS`、`m8_impl_frame_start` 上限、`m8_hook_label_frame` 循环次数等随之改动。本文档描述的是机制本身，不绑定具体项数；改动任一项数时，第 3、4 节涉及的常量需同步核对。
