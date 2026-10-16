# Stage B controller — internal, runtime entry BLOCKED

Task56 replaces the prior unsafe StageBObserver implementation. It is **not an installed hook or an observation-ready device candidate**. Current runtime entry is unproven (task58): no authenticated adapter, exact loaded identity/entry/handle/lifetime proof, resolved-slot installation, timing-before-cache proof, or ARM64 resolver-transparency execution test exists. A bundle-name change, copied SHA constant or truthy caller flag cannot manufacture that capability.

## Production surface

`StageBController.h` exposes construction with an immutable typed original, a full11-argument forwarding method, status, capturedProjection, and `arm`. Production `arm` always returns NO; state remains `TIOStageBBlockedRuntimeEntry`. There is no production capability mint, no observation callback registration, no old public `TIOStageBObserverArm` or direct `TIOStageBHandleOpen`, no constructor/global hook, no lookup wrapper, no dlsym/dlerror, no memory reader call and no schema capture path. `TIOStageBProductionStatus` reports BLOCKED_RUNTIME_ENTRY, entry_installed=false, active=false, device_candidate=false and schema_observation_enabled=false.

The typed forward method calls the provided original synchronously exactly once with all original values, restores incoming errno before that call, and does not swallow an exception from the original. It does not by itself prove that the provided pointer is the correct loaded engine function, that its lifetime is valid, or that a real Dart/Flutter entry is connected. An eventual audited adapter must authenticate immutable loaded Runner/Flutter/Isar identity, selected export/ABI, exact role and entry lifetime, original-pointer binding, real caller/thread/handle and key argument, and maintain cached-forwarder/original storage for process lifetime. No such production adapter is shipped here.

No schema file/network/UI export API exists. That is intentional while production cannot activate; arbitrary paths/overwriting NSHome writes from the old implementation were removed rather than retained as a dormant bypass. Explicit canonical new-file export, if later requested, needs a separate reviewed scope (own path, no symlink, atomic new-file semantics), not an every-open side effect.

## Test-only state machine

The only synthetic grant is guarded by `TIO_STAGE_B_TESTING=1`, compiled into host tests only. The production dylib is built without it, and its bytes are checked for absence of test-grant/observe selectors and the legacy public bypass symbols. Synthetic test grants are not live runtime identity evidence.

The test controller has an immutable original and one grant/arm lifecycle. Missing capability/role/lifetime or wrong-thread activation cannot arm. A real nonnull eighth key argument is checked before clock/memory reads and is forwarded unchanged; it is never replaced by zero. Wrong-thread forward calls still invoke original synchronously on that thread but cannot observe. On the first eligible observation attempt the controller transitions terminal before any external callback, preventing reentrancy/rearm/duplicate capture. A failed first attempt also terminates (more conservative than retrying16 events). A successful snapshot is captured at most once. Monotonic caller seconds reject rollback and expiry at120 seconds. The schema reader retains its <=64KiB/<=120-second/<=16-event local constraints, but controller attempts are limited to1.

Own callback exceptions are converted to failed copy before unwinding the C reader, so any staged partial allocation is wiped/freed normally. A final release occurs before invoking original; no opaque schema pointer is saved or used after original consumption. Original exceptions are outside the observer catch. These are host synthetic tests, not ARM64 runtime ABI or production capability proof.

## Red/green evidence

The original bug is reproducible against retained `build/baseline/StageBObserver.m`:

```sh
clang -fobjc-arc -Wall -Wextra -Werror -Wno-unused-parameter -I. -framework Foundation \
  LabGate.c open-observer/OpenObserver.m schema-observer/SchemaView.c \
  schema-observer/SchemaMetadata.m stage-b-controller/build/baseline/StageBObserver.m \
  stage-b-controller/LegacyBypassTests.m -o stage-b-controller/build/legacy-red
stage-b-controller/build/legacy-red
```

Run from manuscript-lab. The old public arm fabricated trust; direct handle reached a spy memory-copy callback without any adapter capability, causing `reads==0` to fail (exit134). The spy returns failure without reading memory; no old file write is reached. Evidence: `stage-b-controller/build/legacy-red.log`. Baseline sources are retained for audit only and never built into the new module.

New verification: `bash build-stage-b.sh`. This creates a fresh directory under stage-b-controller/build, captures a private source snapshot, compiles **that snapshot**, and records exact hashes. It does not overwrite old StageA or old StageB artifacts. Build commands use `set -euo pipefail`, no piped tail hides errors. It runs a production-host blocked test, test-only real own-buffer -> vm_read_overwrite -> SchemaView -> canonical projection -> fake original, and ASAN+UBSan; then compiles an iOS15 ARM64 production dylib. Actual build inputs are separated into production source and host-test sources in source-manifest.json. Only StageBObserver.m is compiled into production, with declarations from direct header inputs; schema/memory implementation is host-test input only.

Tests cover missing capability, role/lifetime refusal, real nonnull-key zero reads, foreign thread zero reads, repeated arm/terminal refusal, clock rollback, TTL endpoint, all11 argument values, several f32 bit patterns (including signed zero/subnormal/NaN), original same thread/status/out slot/errno, release-before-original, own callback exception at first field and payload copy, and original exception propagation. They do not execute any engine, App, phone or database.

## Underlying modules and provenance

Existing schema-observer, memory-copy and open-observer files are not modified by this task. The OpenObserver header supplies the already-audited typed C function signature; unsafe-to-self-attest resolver/arm helper APIs are not used. Task59 re-performed the previously missing independent canonical-projection review and reported GO; prior manually completed task51/52 statuses were not treated as proof. Memory-copy still requires its own actual independent review before any future live integration; host own-buffer tests are not that review or authorization.

The build receipt is an **unreviewed artifact identity**, not a self-approved allowlist or runtime capability. It reports all activation/candidate booleans false. Packaging must keep an independently approved hash/source-manifest/review-receipt anchor and must not infer schema capture from compilation or host tests. StageA f663 and previous IPA59e58 are outside this implementation's write scope; no signing/install/IPA modification was performed.

## Independent review R1

Task60 independently reproduced a TESTING-only arm-clock exception escaping while the controller mutex stayed locked. Lead approved a single narrow correction: clock evaluation now catches its own exception, enters terminal/ever-armed state and releases the mutex in `@finally`. A new real host test first failed with `escapedArmException` (exit134), then passed only after reaching terminal state and invoking the typed original exactly once with zero reads. The check does not infer success from a timeout. Clock callbacks are explicitly bounded and non-reentrant into the same controller; the module does not promise arbitrary blocking callback safety.

R1 source freeze epoch1792047812, output `build/controller-kUim2S/`, source-manifest SHA `13811207a48ab3e7b6d8f2a50801ff49e67747d39eeb54ad0da1fa58748e8491`. Module SHA `4897b832a3c89b17ba3def38cc4723bef62b2505f8f55d7999925877986bcf7c` is byte-for-byte equal to R0 because the change is inside the host TESTING branch plus tests/header comment. `build/r1.patch`, `build/r1-red.log`, `build/r1-build.log` and `build/r1-handoff.json` retain the correction evidence. Production status remains BLOCKED_RUNTIME_ENTRY; review GO, if granted, concerns this internal module and does not enable an entry or authorize device capture.
