# Early runtime bootstrap — task84, internal engineering foundation

This new directory does not modify any accepted controller, resolver, image-attestation, slot-installer, NativeAddon source or package. No official App/Flutter/Isar engine/DB/phone executes, no signing/install occurs and no official import slot is read or written.

## Production truth

The production module has a pure-C constructor. It records constructor execution, registers `_dyld_register_func_for_add_image` and counts callbacks with C atomics. The callback only counts images; a callback is **not** an identity proof, event observation, installation or schema result. No Foundation, UIKit, Objective-C, allocator, logging, dlopen/dlsym, memory-copy API or CAS is called in production's constructor/callback path. The final iOS dylib's only undefined symbol is `_dyld_register_func_for_add_image` (Mach-O nm prints the imported C underscore form).

State remains `TIOBootstrapBlockedIdentity`, representing **BLOCKED_EARLY_RUNTIME_ENTRY**. Runner identity and bundle identity remain false, slot_installed=false and event_observed=false. The code intentionally does not promote a static Runner dependency graph or before-main execution to a runtime install capability. There is no production test-policy factory, install request, raw-proof struct argument, loader slot scan or actual hook.

Why blocked: exact live isolated bundle identity without unsafe getter/loader recursion, exact loaded Runner/Flutter/Isar code identity, safe current lazy/system resolver eligibility, mapping lifetime/ABA control, callback timing before FFI's first cached open lookup and matching caller/handle/export remain prerequisites. Existing task72's output value struct is not a bearer credential. Disk lazy-helper0x6741a4 is not called, cached or used as a resolver. No forced target dlsym is used to manufacture an observed event.

## Real self-owned loader scenarios

`FixtureFlutter.c` is a self-owned dynamic library, not the official Flutter engine. Its constructor allocates an owned RW mmap slot initialized to its ordinary system dlsym import. `Bootstrap.c` is linked as a separate NativeFixture dylib depending directly on FlutterFixture. RunnerFixture directly depends on both. The own Native fixture invokes real dyld add-image registration in its constructor.

A first linked Runner yields an actual Mach-O UUID. Build tooling parses that own UUID, the own FlutterFixture UUID and records exact output paths, direct dependency names and a fixed embedded `__TEXT,__info_plist` fixture bundle string. It then relinks only NativeFixture with this generated test-only policy, leaving the executed Runner UUID unchanged. At runtime the TESTING constructor reads the actual main dyld header, validates its UUID/path, direct LC_LOAD_DYLIB entries and exact embedded plist bytes; checks the exact own Flutter image UUID and duplicate basename count; then checks actual current-task VM mapping RW/non-executable/8-byte alignment. This is not a caller-passed `verified=true` scenario, but it is still synthetic fixture identity, not an approved production lab identity.

### Scenario A — earlier Flutter constructor wins

In mode `early`, FlutterFixture's constructor performs its simulated FFI dlsym through the owned slot before NativeFixture's constructor runs. NativeFixture observes that lookup already happened, returns BlockedEarlyLookup and leaves the slot unchanged. The test requires exactly one prior lookup. **All constructors being before main does not imply NativeAddon was before Flutter's FFI lookup.** This negative is the important limitation exposed by the real loader sequence.

### Scenario B — Native fixture before first main FFI

In mode `normal`, FlutterFixture has mapped the slot but has not yet looked up anything. NativeFixture validates the own main image/bundle/direct dependencies/Flutter image and actual slot permissions, binds the already-imported real dlsym once to task71's accepted transparent bridge, and performs a single CAS on the owned slot. Main then makes its first simulated FFI lookup through the installed slot and receives the same true system malloc export. No target engine function is looked up or executed. State Installed is **fixture-only** and event_observed remains false.

Other fresh-process cases cover readonly slot, wrong identity, wrong header condition, unexpected slot value, actual second loaded same-basename dylib, and a competing atomic mutation that wins before installation. Each requires no install and an unchanged original slot or preserved competitor pointer. TESTING modes are supplied by environment only in the self-owned executable; production has no getenv or testing branches. The wrong-identity/header modes are negative fault injections, not claimed as complete malformed-header fuzzing. The duplicate mode actually dlopens a second self-owned image of the same basename; it is not just a boolean duplicate claim.

Constructor and simulated FFI run on the same native loader/main thread in the fixture, checked with pthread identity and a thread-local constructor marker. This is host x86_64 execution, not iOS ARM64 runtime proof. The production ARM64 dylib is SDK-compiled only. No claim of arbitrary-thread callback allocation safety or live-map pinning is made.

## RED/GREEN and reproducibility

Initial real Runner->FlutterFixture+NativeFixture linkage had a no-install Native constructor. Main asserted that its own slot had been connected before first main FFI lookup; this genuinely failed exit134. `build/red.log` preserves that original RED. The new actual constructor/own-slot path closes that specific test, while the separate early-FFI case remains blocked as required.

Run `bash build-bootstrap.sh` from this directory. It creates a new private source snapshot, self-owned dylibs/executable, generated own identity policy and immutable hash manifest. `TIO_BOOTSTRAP_SANITIZE=asan ASAN_OPTIONS=detect_leaks=0 bash build-bootstrap.sh` and `TIO_BOOTSTRAP_SANITIZE=ubsan bash build-bootstrap.sh` execute all eight fresh-process cases under sanitizers; ASAN leak detection is unsupported on this macOS toolchain and is not claimed. No test uses official binary modules.

The script compiles the production source to an iOS15 ARM64 dylib, inspects imports/exports and fails on Foundation/ObjC/alloc/dlsym/mprotect/vm_read dependencies. New self-owned output paths/Runner UUID mean generated fixture-policy hashes differ per rebuild; manifests bind every generated header/plist/source input actually compiled. A separate core-source digest excludes only explicitly generated own-fixture inputs so independent reviewers can compare implementation identity across fresh builds.

## Frozen handoff

Owner start1792057174, self-deadline1792058374. Source freeze1792057682; no source edits after that freeze.

- Output: `build/bootstrap-IrbuQL/`.
- Source manifest SHA: `4d9283783bcc08f515a233ac45f5dc4f816d166a88f257cec0f2028631144084`.
- Core implementation source SHA: `a4546e4c8499efbda1bb47cffbac451ebbcddcc6eadc2c57c7567b7e1ac2630d`.
- Production iOS dylib SHA: `40a2effcb0750bb331f90a2f64412623353b91f9240f649a5eeb192a8ec855a2`.
- Evidence: `build/final-normal.log`, `build/final-asan.log`, `build/final-ubsan.log`, frozen output's per-case logs, `production-imports.txt`, `fixture-policy.json`, `source-manifest.json`, `receipt.json`, and `build/handoff.json`.

Independent review is a separate step. Successful own-loader ordering and fixture CAS are meaningful engineering evidence, but they do not activate an official entry, deliver schema capture, commit a DB transaction or complete the user's final NewsHub automatic-ingestion IPA goal. Next integration must close the listed runtime prerequisites without reviving truthy attestations or broad rebinding.
