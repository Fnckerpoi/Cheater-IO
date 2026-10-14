# TMIG1 / 1.0.5.5 static ABI review

This is approval for a controlled experimental candidate, not real-glasses
acceptance, a guarantee of boot, or a promise of rollback. Use the exact candidate
and phone package recorded by the independent audit. No fixed address delta was
applied to the old image.

## Bindings and objects

There are 134 native aliases. 120 retain their masked instruction prefix at the
new callgraph-selected location; this is identity evidence, not proof of every
execution path. The 14 changed prefixes were reviewed separately:

| Binding | New stock evidence / adaptation |
|---|---|
| `stock_ctor`, `nav_ensure_menu` | Real new constructor and allocation executed. Stock allocation is 252 bytes, our 32-byte tail begins after it; allocation becomes 284. Constructor retains the new embedded carousel. |
| `native_snap`, `native_unregister_wheel` | New embedded controller at +0xa0; float position +0xb0. Use the new native methods, not old spring implementations. Item count changes 7 to 16 at the new constructor call. |
| `nav_set_state` | Native transitions still take `(manager, state)`, use current +0x1c, preserve +0x20 previous-state publication. Full new transition function retained. |
| `fm_local_time` | New local timestamp reads timezone +0x10, not old +8. Both direct native call and custom English day calculation adapted; new function executed in ARM tests. |
| `tgr_ota_status` | Getter now reads SysStateMonitor +0x4c. The new getter is retained; not replaced with an old offset. |
| `dots_stock_notify` | New original connection notification retained, including native subscription and input reset paths; custom wrapper calls it first. |
| `native_label_text` | Native text setter retains `(label, text)` and new allocation/wrapping behavior. New label class identity/call sites checked; custom wrappers do not copy the old setter. |
| `stream_queue_send` | Same `(module, message)` forwarding contract; valid module range expands to 1..18 from 1..15. Existing custom Launcher module stays in range; mode-0 deep copy retained. |
| `tdp_rnlink_send` | Same four argument payload send path. New errors are full integers (-22/-107), not an old signed-byte coercion. Callers test success against zero. |
| `ce_ext_106b61f0`, `ce_ext_106b60c4`, `ce_ext_106b6028` | New stock idle function directly calls this string constructor, service lookup and destructor trio. The wrapper uses exactly that trio and the same `AssistantService` literal. A new-stock string/empty-map execution was checked. Old symbol labels here are aliases, not reliable demangled identities. |

Other reviewed layout changes:

- SysStateMonitor stock size 64 → 104; our tail starts at 104 and total allocation
  is 136. Callback vector changes +0x34/+0x38 → +0x5c/+0x60; quiet flag +0x58.
- Head notify entry now saves r9. Replay shim preserves the complete new
  displaced prologue and uses new literal/vtable addresses.
- New stock head start/stop bodies, new BTH start/stop bodies and new ACK locations
  were executed with synthetic scheduler/RPC/sensor boundaries. Active start -4,
  idle stop -5 and creation failure -1 remain distinct.
- Named semaphore veneers were checked in the new image; no old global heap or
  UI singleton address remains in the 69 linked C units.
- Launcher, MonitorManager, LVGL globals, render-dispatch identities and LVGL
  allocation counters were individually cross-referenced to new code.
- Actual new input type/group/driver getters, raw-wheel fan-out and simulated
  detent writes were exercised with the linked remote module.

## Kept unchanged

Protocol contents, data limits, bounded buffers, rendering ownership and input
filters are inherited from the currently tested private TDO1/TEL1 revision. All
69 linked C units are present. Large animation frames remain removed: one frame
is linked. No new functionality is added in this migration.

All 14 non-AP payloads are byte-identical to **1.0.5.5 official originals**. That
does not mean they equal 1.0.4.12: the new official BTH/APC1/resources are included,
and the full OTA can write them. All original burn metadata is retained.

## Required physical acceptance

New cold boot; four-item menu and all custom entries; phone-to-glasses replies;
head-up wake across several sleep cycles; buttons/Watch Crown; recording and
all-day LifeLog coexistence; music, reader, navigation, Focus, dashboard and four
app slots; Dots/English; disconnect/reconnect and renderer retirement. No emulator
test validates BLE timing, sensor availability, display appearance, battery/power
loss recovery or real flash rollback.
