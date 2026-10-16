# Task102 — real C constructor identity pipeline; install remains blocked

## Exact outcome

Unlike earlier selector/capture fixtures, this new module actually runs a stack-only loaded-image identity and system-resolver proof from the C constructor itself. The same Bootstrap.c/View.c/SystemResolver.c implementation is compiled against generated fixed policy for self-owned public images, not a runtime caller proof or fake identity callback. Actual constructor execution passes main UUID, complete main __text hash, embedded Info hash, exact direct load dependency, image path/UUID/full __text hash, unique linker _dlsym metadata, actual slot VM/read, and a no-dladdr system export proof.

It **does not install**. Once those checks pass, state is BlockedLifetime. No CAS, resolver routing publication, hidden original bind, capture Start or output file exists in this task. Default production policy is absent, so the shipped ARM64 candidate's constructor remains BlockedIdentityPolicy. No enabled IPA or official entry is claimed.

Start1792065913; source freeze1792066633, about12minutes into the original30minute bound. Output `build/constructor-cN55Zr/`:

- Source manifest SHA `6c2d9f2a5ae0ac1ce1d81a82aace9f49bdeb9eb9c04570186a716f445bd0ecb0`.
- ARM64 dylib SHA `b90b4032413416c7eab0dd81c574661d460065b416f0929f25759d76b1a50bdc`.
- Actual normal constructor: `CTOR_BYTES=3469 STATE=6` (BlockedLifetime).
- ASAN and UBSan constructor variants also reach state6, with different real text sizes due instrumentation.
- No official App/Flutter/Isar/DB/phone/prefs/Keychain/network/sign/IPA operation; official slot read/write=false.

## Real RED → limited GREEN

The initial module only recorded that its constructor ran. A truly linked own Runner and OwnImage loaded it; main required that the **actual constructor** had verified both loaded image identities. That behavior failed exit134, preserved in build/red.log. New identity implementation closes that specific behavior, without changing the separate no-install assertion. Compilation failures are not counted as RED.

The actual own slot is an ordinary linker __DATA,__la_symbol_ptr generated with no chained fixups, not a malloc/mmap stand-in. Its raw helper differs from the current system-resolved pointer. The new constructor proves the current resolver's owner/export without calling dlsym to create a lookup and without calling task90's dladdr path under the loader lock. It then stops at the still-unproven lifetime/first-cache gate rather than forcing a write.

## Interface and state facts

The only public export is TIOProductionBootstrapGetStatus. No public constructor policy setter, caller ImageProof/bool, expected pointer, root path, output dirfd or activation interface exists. View and system-resolver helpers have hidden visibility. Status fields are descriptive, not bearer capabilities. A true main/image/resolver flag cannot independently authorize another module to write a slot.

Default PolicyDisabled.inc has TIO_FIXED_POLICY_PRESENT=0. A future real Lab build must generate its exact immutable main UUID/Info/text/dependency policy and independently review it; this scope does not invent an official bundle hash. MainPolicy/FlutterPolicy are compile-time constants, not external files read at runtime. The generator currently emits self-image policies only. The real production Flutter/Isar constants have **not** been wired into an enabled build, and no Isar entry/capture logic was added here.

## Actual constructor checks

Bootstrap.c reads the current dyld main header/path with bounded current-task copies. It requires a final `/Runner` leaf, verifies its fixed main policy and embedded __TEXT,__info_plist hash, then finds exactly one matching self image basename at the exact relative path. Image-count stability is checked. This is a loaded path comparison, not canonical filesystem/signed-container proof.

View.c does not call malloc/free/realpath/Foundation/ObjC. It copies a bounded Mach-O command region to a stack buffer (max65504bytes), parses commands/segments/sections, rejects malformed ranges/overlapping VM segments/duplicate UUID or text/unsupported chained/auth pointer layout, and hashes complete __text in16384-byte stack chunks through CommonCrypto. Main embedded Info bytes are separately hashed. Required LC_LOAD_DYLIB dependency must occur exactly once; dependency presence is not itself bundle authority.

The fixed self image slot must lie in ordinary8-byte aligned __DATA,__la_symbol_ptr with static RW metadata. Crucially, it also parses live LINKEDIT LC_SYMTAB/LC_DYSYMTAB/indirect/string records, requires exactly one `_dlsym` lazy import with undefined/external/ordinal1 metadata, and requires that import's exact slotVA equal the fixed independently derived VA. Section membership alone is not called slot-symbol proof. Actual VM protection and current pointer read occur only after these checks.

Each image view has an explicit current-task read budget<=64MiB, command count<=512, segment count<=32, pointer section count<=16, <=4096 entries per lazy section and metadata counts<=262144. Hash ranges are<=32MiB and every copy<=64KiB. No pointer callback or external proof provider can change those reads.

## New no-dladdr constructor system resolver proof

SystemResolver.c reuses the **algorithm** of task90's accepted bounded parser, but is a new independently reviewable implementation in this directory. It does not modify or claim to inherit task90's GO. There is no dladdr entry step:

1. Obtain own linker-imported `&dlsym` without calling it. Candidate current value must equal that import.
2. Enumerate bounded current dyld images, bounded-copy paths and require one exact `/usr/lib/system/libdyld.dylib`.
3. Require its single nonzero UUID to be unique across the enumerated loaded images. Current UUID uniqueness is not OS signature/version pinning.
4. Descend actual VM submaps to a RX/non-writable leaf containing the pointer.
5. Validate __TEXT containing the import, one __LINKEDIT, exactly one supported bounded export range, copy<=64KiB trie and parse exact `_dlsym` regular terminal. Reexports/stub-resolvers/ambiguous edges/unsupported ranges reject. Base+exported value must equal the import.
6. Entire proof reads at most32MiB with a stack-owned explicit ReadBudget, not TLS/global heap state. errno is restored.

The implementation imports `_dlsym` **for its address**; it is not correct to call this a no-import module. Its body never calls dlsym or dlerror and it has no dladdr/allocator/Foundation imports. Actual executable/system export proof passes inside self-image constructor execution on this host, not just from a main-thread helper test.

## Why the next gate still blocks

No code assumes that avoiding dladdr proves all remaining loader behavior safe. The dyld getters, Mach MIG stubs, C library memory/string helpers, CommonCrypto SHA calls and their first-use linker binding are exercised by the self fixture but are **not an independent Apple/API guarantee of loader-lock safety on iOS**. The host has not proven actual device loader callback support, no allocator anywhere in transitive OS implementations, absence of reentrancy, pinning, first-FFI cache timing, exclusive writer authority or unload/ABA control. The status therefore keeps loader_lock_safety_proven=false.

If the exact raw helper were seen, it would not be called as a real resolver; lifetime still blocks. If current value is resolved but cannot be proven by the new export parser, state is BlockedResolver. On this real host it is proven and advances only to BlockedLifetime. There is no conditional unsafe-CAS flag hiding after that state.

The self image is only one publicly built OwnImage, not real Flutter+Isar identities. No actual Lab HOME/container root is authenticated or traversed; no dirfd is created, so Start cannot accidentally operate from a weak package marker. Capturing schema remains a later integration step using separately reviewed task97 only after authentic entry/context/dirfd authority is available. Prior task100 self-file PASS is not relabeled as production readiness here.

## Tests and reproducibility

`bash build-bootstrap.sh` copies all new implementation/generator/test inputs to a private snapshot. Per mode (normal, ASAN, UBSan), it links a fresh self Runner with embedded public Info bytes and direct OwnImage dependency, derives the actual Runner/OwnImage policy from their Mach-O files, then relinks **only Bootstrap.dylib**, leaving the executed Runner bytes/UUID unchanged. The same constructor implementation verifies those loaded images; no testing policy factory is compiled. It records exact generated policy variants and all input hashes.

Additional actual tests reject wrong UUID, wrong text hash, a1-byte read budget, address1/unreadable mapping, slideINTPTR_MIN, imported+1, another system function and pointer1. These are bounded wrong-policy/read-address tests, not claims that live system text was modified or a hostile production image was loaded. No callback short-copy injection is used; unreadable reads use real Mach failure. No official slots are touched.

Normal/ASAN/UBSan ctor+negative executables pass. ASAN detect_leaks=0 is platform-limited, not leak detection. iPhoneOS ARM64 is compiled only. Production imports/exports are recorded: only the public status getter exports; internal verification code imports SHA, Mach/dyld and basic C helpers, no heap/ObjC/realpath/dladdr/dlerror, and dlsym only by address. Source-level call constraints are not a transitive runtime certification.

Artifacts: build/red.log, frozen normal/asan/ubsan and negative logs, generated Policy-*.inc/.json, source-manifest.json, imports.txt, receipt.json, build/final-build.log. New builds regenerate exact self UUID/text sizes, so full manifests vary while implementation per-file hashes remain comparable.

Independent review is separate. The meaningful progress is a real no-heap source-level C constructor identity/slot/resolver proof with an exact honest safety stop—not an enabled hook, official schema observation, capture IPA or the final saved-ingestion goal.