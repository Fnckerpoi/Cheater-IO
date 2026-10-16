> Current follow-up task71: see **PROCESS_POLICY.md**. The historical ordinary-only iOS-policy mismatch below has now been addressed at the precise RTLD_DEFAULT native-fixture policy seam, not by qualifying all special handles. Production remains pure passthrough with no official runtime capability; task62/67 baseline evidence below is preserved, not retroactively altered. New sourcefreeze2735/source8a5757...deb3a and outputbridge-h1gdhy are separate review inputs.

# Native resolver bridge foundation — task62

This is implemented, executable native engineering evidence, **not an official runtime hook**. No Runner/Flutter/Isar App or engine, database, phone, private symbol API, real import slot or signing/install pipeline is touched. Only self-owned Mach-O dynamic libraries and normal system dlsym are executed. Existing controller/packager/other modules remain unchanged.

## What actually became executable

`Caller.c`, `Naive.c` and `Second.c` form a real dependency graph. libCaller depends on libNaive and libSecond; libNaive also depends on libSecond. All export a deliberately repeated fixture name. From the same caller-function site, direct dlsym(RTLD_NEXT, bridge_collision) resolves the libNaive export returning333. A conventional non-tail C wrapper calls real dlsym from its own image; this resolves libSecond's repeated export returning222. `CallerSensitiveTests.c` asserts transparency and therefore genuinely fails (exit134) on the ordinary wrapper. This is not a fake function-pointer comparison or an assertion invented around an empty lookup. Initial fixture load-order without dependency edges produced two missing lookups and was corrected before using it as RED evidence.

`ResolverBridge.S` replaces that nested call with an indirect tail jump to a normally imported, explicitly bound real resolver. The original caller's native return address/SP survives; no extra resolver, name read, log, allocation, identity lookup, clock or dlerror call occurs along production query dispatch. The real-resolver pointer is hidden and immutable after one successful bind. Binding must happen before any bridge pointer is published; unbound bridge invocation is outside contract. No constructor or automatic publication exists. No resolver or cached pointer is unbound/unloaded/reset on expiry.

Native x86_64 GREEN tests repeat RTLD_NEXT, DEFAULT, NULL, ordinary own-dlopen handles, unsupported symbol names, seeded/unseeded dlerror state and errno comparison. Four native threads test resolver/error behavior. `Recursive.c` additionally uses actual Mach-O `.symbol_resolver` exports: dyld invokes the fixture symbol resolver, which recursively performs real nested dlsym through the bridge while the outer lookup is active. Both direct and bridged outer lookups produce the same resolved function and two real resolver invocations. This is not merely a recursive fake C callback standing in for dyld.

## Qualification remains deliberately narrow

Production has **no qualification factory or official adapter capability**. Its query path is pure passthrough and status is `UNOBSERVED_NO_OFFICIAL_RUNTIME_CAPABILITY`; selected official backend is none. It never captures key/schema or returns a fabricated official event.

`TIO_BRIDGE_TESTING=1` adds an explicit fixture-only ordinary-handle experiment. Before publication, test-only TestOpenFixture opens one caller-owned fixture path with real dlopen and stores the issued handle; TestQualify accepts only that exact issued handle, rejecting arbitrary non-special pointer values before lookup. Test setup supplies immutable owned pointer to the exact literal isar_open_instance, the genuine export returned by normal setup-time dlsym and an explicit typed substitute. Special handles/NULL cannot be qualified. Query assembly first tests active/handle/name-pointer equality without dereferencing the name. Unknown pointers (even equal text at a different pointer), non-target symbols, special handles and unqualified paths tail-forward. Only a matched ordinary fixture handle/name goes to a C path that makes one genuine resolver call, compares the returned pointer to the expected fixture export, and substitutes on equality. No API/clock/error/identity calls occur after real resolver return. The substitute exercises all11 original arguments including f32 bit patterns, out/status and a real nonnull key pointer to an actual self-owned fixture export. The resolver itself does not receive or inspect key/schema; this is argument-preservation evidence, not a live observation/privacy-capability proof.

Fixture expiry atomically clears the active flag; later lookups purely forward while previously returned typed forwarder pointers remain valid for process lifetime. Setup/qualification is single-threaded before concurrent publication, fields are immutable, and only active changes afterwards. In-flight qualified lookup at the expiry boundary may finish its prior decision; this is not a hard asynchronous revocation guarantee. No code unload or synthetic resolver lookup is used to manufacture an observation.

## Important new source fact: iOS uses process lookup

Task66 obtained fixed public IsarPlus1.2.6 source at commit `99a68d4f6d4b6c1d075deffd8c5ac6a9d5c40604`: `packages/isar_plus/lib/src/native/native.dart`22–23 selects `DynamicLibrary.process()` on iOS, not an ordinary framework dlopen handle. Source SHA `1ae1f701543daa61acc72f67052ac0453db797f22440dd0ca1746f5f3024ea19`; retained source `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/isar126-loader-source-vcjg86fy/native.dart`. It is source evidence, not the exact App AOT/runtime handle value.

Therefore the ordinary-handle qualification experiment **does not cover the publicly expected iOS loading path**. The receipt reports `UNSUPPORTED_HANDLE_POLICY_PENDING_VENDOR_HANDLE_PROOF`; no READY/qualified official entry is claimed. The word process is not assumed to imply a particular Darwin handle value, and RTLD_DEFAULT is not equated with RTLD_NEXT. A next separately approved scope must establish vendor DynamicLibrary.process-to-handle semantics for the exact Flutter/Dart version, verify that exact handle's real dlsym behavior, then authenticate loaded identity/entry/export/caller and installation-before-first-open-cache timing. The bridge does not silently widen special-handle qualification to chase a green result.

## Architecture and ABI evidence boundary

Actual host `uname -m` is **x86_64**. Native execution is x86_64 only. `RegisterSentinels.S` plus `RegisterSentinelTests.c` compares direct resolver versus bridged call from the identical call site, proving preservation of six integer argument registers, AL, all128 bits of xmm0–7, exact SP and the actual caller return-PC; the result register also matches. These are assembly sentinels around real bridge dispatch. They do not prove an iOS ARM64 execution result.

ARM64 source and iPhoneOS15 SDK compilation/static disassembly are included. The production bridge consists of exactly:

```
adrp x16, real-resolver-page
add  x16, x16, real-resolver-offset
ldar x16, [x16]
br   x16
```

Only ABI scratch x16 changes. There is no BL/BLR, stack-frame modification, LR change or x0–x8/v0–v7 modification. The fixture-only selector uses scratch x16/x17 and tail-branches; register saving/helper calls are avoided for passthrough. ARM64 sentinel source is compiled for a future same-architecture test, but is **not executed here**. ARM64e/PAC, iOS runtime slot protections, dyld races, actual FFI caller and device execution remain unproven. No QEMU/new SDK/software is installed.

## Reproduce and review

Run `bash build-bridge.sh` from this directory. Every run creates a new `build/bridge-XXXXXX`, copies exact sources to a private snapshot, compiles that snapshot and retains a hash manifest. No old output is deleted or overwritten. The script asserts the real naive-wrapper RED, then requires production/qualified/native-register/real-reentrant-dyld GREEN and ASAN+UBSan. It builds host and iOS ARM64 production dylibs, compiles fixture ARM64 and sentinel objects, writes disassemblies/imports/exports and a receipt. Build errors stop the script rather than being hidden by a log-tail pipeline.

Final owner freeze epoch1792051723:
- Output `build/bridge-8AeEuh/`.
- Source manifest SHA `079b030b6ad34e9c61042cdf4cf34ef919c22076933b70b14b7eb0f71bf40672`.
- Host dylib SHA `e64ff076755448bda039a1e22adcc499165727138ce04218a3b681d770b6f39a`.
- iOS ARM64 dylib SHA `d49e1d567ac6df42dc9675fd1563390c99407df13a22c9d00e2a8b330c857ea4`.

Key evidence: naive-red.log, production-test.log, qualified-test.log, register-test.log, recursive-test.log, qualified-asan.log, arm64-passthrough.asm, arm64-fixture-resolve.asm, host-bridge.asm, ios-imports.txt and receipt.json in that frozen output. Independent task67 review is separate. Receipt `UNREVIEWED_NEW_BUILD` is not a capability or allowlist approval. This engineering foundation advances the resolver transparency seam; it does not deliver the final NewsHub auto-ingestion IPA, an installed official entry or an actual schema/record transaction.


## R1 independent review correction

Task67 reproduced that the original test-only qualification factory could accept an arbitrary non-special handle pointer. Production lacked that factory and was unaffected, but claiming the test factory had certified an ordinary handle was too strong. A new actual negative test first failed on handle0x1234 without performing any lookup against it; `TestOpenFixture` now registers one real dlopen-issued self-owned fixture handle, and TestQualify requires that identity. Setup is single-threaded and caller-owned fixture path only; this is not an official runtime capability or process-handle policy. R1 native/ASAN/register/recursive/SDK script passed. Production host/iOS bytes stayed unchanged; new source manifest SHA186e646745eeca7b4754215a6d823eb58a6d6b964b9a46a0c8993339eaa716c6, freeze1792052013. The prior freeze079b...0672 is retained as superseded review input, not the final source anchor.


Final separate R1 approval added an independent five-minute bounded correction window after the original task62 self-deadline1792052063. Final R1 freeze1792052093; source SHA366bac95a6fd7bc317ae8ab44b845faf37b0f0c767fe9952ce0246ab6575c740 supersedes intermediate186e...16c6. The final fixture rejects0x1234, a different real but unissued Caller handle, DEFAULT/NEXT/NULL and repeat issuance; the actual issued Second handle succeeds. All native/ASAN/ARM64 SDK checks pass. No claim is made that this additional R1 final validation completed within the original25-minute bound. Exact output/path and original-vs-R1 timings: build/r1-handoff.json; minimal source delta: build/r1.patch.
