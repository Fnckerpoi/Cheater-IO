# task90 — exact imported resolver pointer classification

## Interface and explicit non-authority

`TIOClassifyResolverSlot(uintptr_t current)` is a hidden/internal pure-classification interface returning Unknown or ExpectedResolver. The caller cannot supply an expected resolver, lazy helper, trust flag, image proof or custom copy callback. The implementation obtains its own ordinary linker-imported `&dlsym`; it does **not call dlsym**, inspect a target symbol name, clear or consume dlerror, or inspect/write a GOT slot. The caller supplies only a pointer value. A match is descriptive evidence, not proof that the pointer came from any particular slot, nor permission to write it. The hidden function must be linked into an independently reviewed installer; it is not a public bearer-capability factory.

No lazy-helper acceptance is implemented. In particular, caller-supplied0x6741a4/slide is not part of this interface; task72's current policy has no approved helper field. This scope does not modify task88 or reopen its installation policy. No official App/GOT/engine/DB/phone/IPA/sign operation occurs.

## Actual provenance checks

A candidate must exactly equal this module's imported `dlsym` address. Only then does the implementation inspect it:

1. dladdr returns exact owner path `/usr/lib/system/libdyld.dylib`, symbol name dlsym and exact symbol start address. A caller stub, local/global substitute or same-named private dylib fails.
2. Actual current-task nested VM map leaf is executable and not writable. The outer shared-cache submap can report read-only; this is **not** guessed to mean executable. The implementation descends vm_region_recurse_64 to the actual leaf, bounded to16levels.
3. The owner is a unique current dyld image with the exact system path/base, a nonzero single LC_UUID and that UUID occurs once across the bounded live-image enumeration. Missing/duplicate/unsupported image metadata rejects. The image count must remain stable across the observation.
4. Validated LC_SEGMENT_64 metadata places the import inside loaded __TEXT and identifies one __LINKEDIT range. Exactly one supported export source (LC_DYLD_EXPORTS_TRIE or nonempty LC_DYLD_INFO export range) is accepted, within LINKEDIT bounds and<=64KiB.
5. The export trie is copied through current-task vm_read_overwrite, bounded ULEB parsing follows exactly `_dlsym`, ambiguous matching edges reject, and only a regular non-reexport/non-stub-resolver terminal is accepted. Its base+exported address must equal the actual imported address. Merely having a system-looking dladdr name is not sufficient.

Every source read is bounded; unsupported integer/range/format/VM conditions fail Unknown. errno is restored before returning. The classifier stores no reusable proof or pointer credential.

## New real fact from this host

Host x86_64 initially reported imported dlsym address0x7ff8059b5f9c, exact libdyld owner/symbol and system export trie. That address is also the actual value which caused task88's self-owned initial-helper-only gate to reject. This scope establishes its system export provenance, not that an official Flutter slot was observed or writable.

A newly linked own dylib with an ordinary linker `__la_symbol_ptr` is parsed from its actual LC_SYMTAB/LC_DYSYMTAB/indirect/string metadata. Before its OwnLookup function is ever called, the real slot holds the system imported dlsym address instead of its raw on-disk helper. The classifier accepts the resolved pointer and rejects that raw helper. The fixture slot is read only; no CAS/write is performed.

## True RED/GREEN and tests

The first classifier returned Unknown unconditionally; expecting the actual import to classify ExpectedResolver failed exit134. A later real predicate rejected the outer shared-cache map's read-only protection; bounded diagnosis showed the parent submap was not the code leaf. Correct descent to the real RX leaf, not a widened read-only allowance, made the exact provenance test pass.

`bash build-policy.sh` creates a private source snapshot and a real own dylib, derives a fresh own slot policy from its bytes and runs:
- Exact import, actual resolved self LA slot, unknown0/1, wrong system function and imported+1 rejection; lazy helper rejection; OwnLookup call count remains0.
- Four simultaneous TLS threads, each with a deliberately pending missing-symbol dlerror established by the harness. Classification preserves that exact pending error and errno. Harness dlsym/dlerror calls are intentional test setup/assertions, not classifier implementation calls.
- Normal, ASAN and UBSan host execution. ASAN leak detection is disabled because unsupported on this toolchain; no leak-check claim.
- An actual DYLD_INSERT_LIBRARIES interposer substitutes a counterfeit dlsym whose body traps if invoked. The classifier returns Unknown without calling that counterfeit.
- iPhoneOS ARM64 SDK object compilation and symbol inspection. Public vm_read_overwrite/vm_region_recurse_64 interfaces are used; unsupported mach_vm SDK APIs are not hidden behind declarations.

## Important remaining limitations

This is **not loader-lock safety proof**. dladdr and dyld enumeration are exercised from ordinary host test code, not certified safe under a production initializer. The result assumes a stable live image graph; checking image counts does not pin image lifetimes, prevent unload/reload ABA or guarantee string-pointer lifetime during a hostile concurrent unload. It is not a process-wide mapping capability. iOS ARM64 is compile-only, not execution evidence. A platform with another legitimate system owner path, export format, negative slide, missing UUID or unsupported map is conservatively Unknown; do not add wildcard allowlists without independent evidence.

No bundle/container identity, exact Flutter/Isar identity, slot-location provenance, slot RW permission, first-FFI timing, original-function session binding, capture activation or installation follows from this classification. Those are separate installer checks. Task88 production remains blocked and unchanged.

## Freeze

Start1792061341, twelve-minute deadline1792062061, source freeze1792061789.

- Output: `build/policy-evzYGP/`.
- Source manifest SHA `c2a45648346401b8669dfb7320f31bcc1cf73493b84a6adaa7931414acde1282`.
- ARM64 object SHA `1668ea33dbc2b430bc2eb738826ef4a663f4de2abd6067e81e618541e3e22b27`.
- nm confirms private external `_TIOClassifyResolverSlot` (hidden visibility).
- Receipt status `HOST_RESOLVED_IMPORT_PROVEN_NOT_SLOT_INSTALL_AUTHORITY`; loader_lock_safety_proven=false, ios_runtime=false, official_slot_read/write=false.
- Evidence: source-manifest.json, receipt.json, own-policy.json, normal.log, asan.log, ubsan.log, counterfeit.log, imports.txt and original build/red.log. Generated own-policy values are bound to this self dylib; an independent fresh build intentionally regenerates them.

Independent review is separate. This closes a precise pointer-classification question, not the user's final ingestion IPA or a production slot-install permission.