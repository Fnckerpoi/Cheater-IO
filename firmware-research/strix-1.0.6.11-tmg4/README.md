# TMG4-10611 固件构建

以 1.0.6.11 原厂 ZIP 为基座，离线生成 AP 精简候选。源补丁和 trampolines 汇编位于本目录；构建仍依赖相邻 `strix-1.0.5.12-tmg3` 的共享源码与资源、OHOS LLVM、patch 和 Python。`probe-build.py` 使用本目录的 `probe-build-inputs.json` 和 `probe-link.ld`。原厂 ZIP 保持原样。

```powershell
python build.py --stock <原厂ZIP> --llvm-bin <OHOS LLVM bin目录> --out <全新输出目录>
python build.py --stock <原厂ZIP> --audit <输出目录中的候选ZIP>
```

审计核对 17 个成员、16 条清单、15 个非 AP 载荷及白名单补丁。构建目录的 audit.json 和 build-inputs.built.json 记录实际包与成员身份。设备上的菜单交互、升级和版本回读另行验收。
