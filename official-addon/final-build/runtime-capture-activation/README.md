# Task103 — main dependency retention is not first-FFI or loader authority

## Result

This new module verifies an actual main-image strong dependency graph, runs task102's fixed loaded identity/slot/system-resolver proof via a task-local adapter, and demonstrates a concrete target-cache counterexample. It does not activate capture. Final state is **BLOCKED_LOADER_CONTEXT_FIRST_FFI_UNOBSERVABLE**, default production still lacks a valid Lab policy and blocks at identity.

Strong LC_LOAD dependencies from the process main image support retaining those images while the main process lives. They do **not** prove that Flutter has not already resolved/cached the open function, that no concurrent writer/ABA exists, that loader APIs are safe under every supported iOS initializer context, or that an actual signed isolated container was authenticated. Those missing facts are not fabricated as boolean grants. There is no unsafe enable flag or install branch after the stop.

No official GOT/App/engine/DB/phone/prefs/Keychain/network/sign/IPA operation occurs. All live slot changes described below occur only in self-owned host fixtures after main, as a cache counterexample; production code has no CAS, bind, Start or file output.

## Actual RED and limited GREEN

The first new module only recorded constructor execution. The new actual Runner linked six own dylibs; main required identity, direct dependency graph and current system slot proof. That assertion failed exit134, preserved in build/red.log. New code closes those exact proof requirements, not a capture-ready assertion.

The six self libraries are OwnImage (Flutter-like ordinary lazy linker table), OwnActivation (this C constructor), OwnIsar (public typed open fixture), OwnA/B/C (extra real main dependencies). Policy generation occurs after linking each actual self Runner; only OwnActivation is then relinked with immutable expected main/image UUID/text/embeddedInfo/slot policy so Runner identity remains unchanged.

The constructor calls a task-local copy of task102's identity pipeline; imported View/SystemResolver/Policy/Generator algorithms remain byte-identical to the accepted task102 input. The copied Bootstrap implementation is transformed only from automatic constructor into a hidden internal callable helper plus hidden status getter. Those transformed copies are separately hashed in this task snapshot, not mislabeled as unmodified upstream. Original task102 files/receipt are untouched.

After main/Info/text/slot/system export proof, Activation.c parses live main LC_LOAD_DYLIB commands and requires exactly one strong absolute dependency for each of the six exact self-image paths and exactly one actually loaded matching path. Weak/reexport dependencies do not count. Omitting OwnC from the actual link produces BlockedDependencyGraph even though the generated main identity is otherwise valid. The graph comparison uses paths under the exact loaded main's containing directory; these synthetic paths are not production @rpath resolution support.

If all graph/identity/resolved-slot checks pass, the final production path still stops at BlockedLoaderContext. No actual target lookup is induced by the activation constructor.

## Critical actual target-cache test

The early fixture is not an unrelated malloc/version lookup. OwnImage's real earlier C constructor calls:

`dlsym(RTLD_DEFAULT, "isar_open_instance")`

through its actual linker slot and caches the returned typed function pointer. Main then confirms the earlier lookup count is exactly1 and the cache is nonnull. Even with all exact identity, main strong dependencies and current system resolver proofs passing, the bootstrap did not have a generic way to infer that this target pointer had already escaped.

The host test then finds the one self lazy slot currently equal to the system import, atomically replaces it with a local spy, and invokes the **already cached target function**. The original OwnIsar function returns0x5a, writes out0x201 and increments its count exactly once; spy call count remains0. The test restores only the still-owned spy slot using a compare/exchange. This is an actual self-process counterexample, not a fabricated lookup-observed flag. No schema capture event is reported.

Normal fresh process has lookup count0/cacheNULL, while the early process has count1/cache set, but both present the same main graph, image identity and resolved resolver slot at bootstrap. Therefore those static/live slot checks alone cannot prove pre-first-FFI timing. Task103 does not generalize fixture lookup counters into a production authority API; no such public counter exists in the real Flutter engine.

## Tests and boundaries

`bash build-activation.sh` takes a private source snapshot and executes normal/early/missing-dependency cases in normal, ASAN and UBSan: nine fresh processes. Main graph/identity proof passes in normal and early; missing strong dependency rejects. The early actual target-cache bypass test passes under each mode. ASAN detect_leaks=0 is toolchain-limited, not a leak-test claim. Host is x86_64; iOS ARM64 is compiled only.

There is no new actual positive slot-install/capture-file test in task103. Prior task100 has its separately reviewed own integration/file evidence, but it cannot erase this earlier-target cache risk. No claim is made that this task tests every wrong UUID/hash/VM/special-handle/duplicate scenario anew; task102/90 evidence retains its own scope. No positive official runtime policy, actual NSBundle identity, authenticated HOME/container root, Isar full policy or iOS call-context safety is implemented here.

The only exported production symbol is TIOActivationGetStatus. Internal identity helpers are hidden. System resolver proof uses imported dlsym **address**, not a dlsym invocation; no Foundation/ObjC/logging or explicit heap call is made by the constructor source. Transitive OS helper implementation safety under loader lock remains unknown, not declared safe merely from no explicit malloc. Default PolicyDisabled blocks before runtime identity operations. Imported dormant verifier code still has system imports and is not described as a no-import dylib.

## Freeze

Start1792067043;20-minute deadline1792068243. Source freeze1792067456 (about6minutes53seconds), output:

`build/activation-n9XQqC/`

- Source manifest SHA `f353f479fb1a7a618112bc2774976b23de12675a1607be7adf087033bd9841f7`.
- ARM64 module SHA `04d2a8d376339e47f370dd70f0bb280d10fc90bb38f1eb1a1ac0b8674667add2`.
- Receipt slot_installed/capture_started/file_created/official_slot_read/write=false, ios_runtime=false.
- Evidence: normal.log, normal-early.log, normal-missing.log and equivalent ASAN/UBSan logs, generated exact policies, source-manifest.json, receipt.json, build/red.log and build/final-build.log.

Independent review is separate. This is a precise new retention/timing result with a real target-cache bypass demonstration and an honest blocked activation path—not an active capture IPA or the final NewsHub saved-ingestion result.