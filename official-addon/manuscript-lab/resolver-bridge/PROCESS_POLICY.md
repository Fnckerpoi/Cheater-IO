# task71 — exact Darwin RTLD_DEFAULT process policy

This is a new scope after task62+67 GO. Task62 source baseline `366bac95a6fd7bc317ae8ab44b845faf37b0f0c767fe9952ce0246ab6575c740` remains retained under `build/task71-baseline/` and `build/bridge-ZBz7Pb/`. The older ordinary-only unsupported-policy receipt describes that historical scope, not this new policy's native fixture results.

## Fixed vendor evidence and test seam

Task69 independently obtained official Dart SDK3.11.5 commit `c70f78e7d682c158c15ca0c26c729b3ccb932284` sources, retained at `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/dart-process-handle-proof-zesfxi4k/`:

- ffi_dynamic_library.cc177–183 selects `DynamicLibrary::New(RTLD_DEFAULT,false)` in the POSIX/DART_HOST_OS_MACOS process branch, unlike executableLibrary's dlopen(NULL).
- globals.h Apple/iPhone definitions show that this iOS code follows the relevant MACOS conditional; names are not used as guesswork.
- utils.cc359–369 clears dlerror, calls dlsym with that same handle and reads error afterward. A NULL dlsym return need not mean failure.
- Local iPhoneOS SDK dlfcn.h defines distinct NEXT(-1), DEFAULT(-2), SELF(-3), MAIN_ONLY(-5). The new code compares **RTLD_DEFAULT macro equality**, not arbitrary negative values.

These sources motivate a concrete fixture, not a claim of exact App AOT/runtime handle observation. No extra network/source download was performed by this implementation worker.

## Real native fixture before qualification

`ProcessLocal.c`, two differently tagged builds of `ProcessGlobal.c`, two separately loaded `ProcessCaller.c` images and `ProcessNested.c` are real self-owned dylibs. Fresh processes load the LOCAL shadow first, then globalA/globalB in both orders. A real DEFAULT lookup must resolve the first GLOBAL export, not the LOCAL or second GLOBAL shadow. From both caller images, direct dlsym, ordinary nested C dlsym and the tail bridge are compared for exact pointer, errno and full original clear→lookup→errorcheck protocol. The ordinary wrapper is **not presumed broken for DEFAULT** merely because task62 found it broken for NEXT.

`ProcessNull.S` exports an actual absolute symbol at address0. Native dlsym(DEFAULT,process_null_symbol) returns NULL with no dlerror, whereas an unknown symbol returns NULL with a real error. Both caller images compare all three resolver routes on this case as well. Four threads use distinct missing-symbol names to check actual per-thread error state across repeated successful/missing lookups. This is real system loader behavior, not fabricated resolver pointers.

Only after these actual semantics checks does the same test ask for exact DEFAULT qualification. Task62's ordinary-only factory refused it; that assertion produced real task71 RED. The new factory permits this exact macro only after its prebound real resolver returns the precise setup expected export for the exact owned literal. A second GLOBAL same-name symbol as expected is refused. The setup verification is outside live dispatch; the live qualified query still executes one real resolver call then pointer-equality replacement, with no additional lookup/dlerror/log/clock/identity call after the real result. Name pointer identity avoids dereferencing unknown argument strings during query dispatch.

The qualifications remain TESTING-only. Production has no capability mint or activated selector, unchanged assembly and unchanged pure-passthrough C behavior. An unverified live process must not copy a testing qualification into a production trust flag.

## What is specifically refused

NEXT, SELF, MAIN_ONLY, NULL, unknown negative and arbitrary pointer handles, and real but unissued ordinary handles cannot qualify. They continue through the transparent tail path. Test expiry disables substitution without invalidating cached typed forwarder pointers; no bridge/original/own-dylib unload occurs. The wrapped selected self-owned export preserves all11 typed original arguments, nonnull key pointer, f32 bits, out slot/status and exactly one original invocation. This is not a real key/schema read or actual official save.

## Result domain and remaining adapter work

Passing native fixtures establish an **exact RTLD_DEFAULT policy foundation**, replacing the previous blanket ordinary-only mismatch for the public iOS source loading path. It is no longer correct to report that only ordinary-handle policy has been tested. It is still incorrect to report actual iOS process lookup observed or an installed official entry. Host is x86_64; ARM64 is SDK compilation/disassembly only. Production remains pure passthrough, selected official backend none, official slot false, official engine executed false, official runtime UNOBSERVED.

A next independently authorized adapter must bind exact loaded Runner/Flutter/Isar immutable code identity, real DEFAULT lookup caller/name/returned export, actual slot state/protection/race-safe installation and installation-before-first-open-cache timing. No ordinary-handle assumption, wildcard negative-handle qualification or hidden direct Dart call substitutes for those proofs. Existing controller/packager/StageA sources are untouched.

## Reproduce

`bash build-bridge.sh` creates a fresh private source snapshot and runs the original NEXT RED/GREEN and register/reentrant-dyld/ASAN/SDK checks plus the new DEFAULT matrix. The DEFAULT matrix has both global load orders × selected/shadow expected pointer × production/qualified/ASAN executables in separate native processes. Logs are process-{production,qualified,asan}-{AB,BA}-{0,1}.log. The final receipt reports `process_default_fixture_policy=true`, `non_default_special_handles_qualified=false` and `EXACT_RTLD_DEFAULT_NATIVE_FIXTURE_VALIDATED_NOT_OFFICIAL_RUNTIME`. Immutable source manifest and module digests are recorded in that output, not inferred from a mutable post-build working tree.
