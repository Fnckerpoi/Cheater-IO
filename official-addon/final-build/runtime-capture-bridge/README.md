# Task88 partial — real linker lazy-state gate rejects; production blocked

## Outcome (not GREEN)

The new self-owned Darwin dylib fixture uses a real linker-generated `__DATA,__la_symbol_ptr` section, not a mmap surrogate. Runner directly links OwnFlutter, OwnEngine and OwnAdapter. OwnFlutter's actual function calls dlsym through that import. Task88's intended positive assertion is **still RED134**: the constructor refuses to install because the actual slot value is not the independently derived initial lazy-helper address. No intercepted first open or schema file is claimed. No sanitizer PASS is claimed for this task88 integration.

Frozen output: `build/frozen-task88/`.

- Source freeze1792060911.
- Source manifest SHA `eaa231f908ac927d531b53473c24aacb9621b0d336da78710b12ed9c9be99622`.
- Production ARM64 dylib SHA `620b18403914f7481b1e10396fa6ac809151546e4bd579dea315cab3202457fd`.
- Source manifest covers the new task88 files; it does not pretend the fixture was compiled from private snapshots. The existing dependency sources are referenced read-only from accepted task86 and memory-copy/SchemaView modules. A future full reproducible recipe must additionally pin/copy those dependency inputs before a successful integration can be accepted.
- Production status `BLOCKED_BUNDLE_POLICY_NO_APPROVED_RUNTIME_CAPABILITY`; fixture status `BLOCKED_LIVE_SLOT_STATE`.
- iOS ARM64 SDK compile succeeded; iOS runtime not executed.
- No official slot was read/written. No official App/engine/DB/phone or signing/packaging operation occurred.

## Actual specific failing gate

`build/precise-red.log` / frozen copy records:

```
OWN_STATUS=2 initial=1048736f8 installed=0
OWN_ACTUAL_SLOT=7ff8059b5f9c EXPECTED=1048736f8 VM_QUERY=0 PROT=3
Assertion failed: actual own linker slot must be connected before first FFI lookup
```

Addresses are only transient addresses in an entirely self-owned local fixture, not exported app metadata. Current-task VM query succeeded and established RW/non-executable. UUID/path lookup found the intended self dylib, and the expected helper was computed from its actual slide plus raw helperVA0x6f8. The loaded slot instead held another pointer. It was **not** reclassified as a trusted real resolver, called, overwritten or forced back to a helper. The initializer takes the fail-closed branch before any capture binding or CAS. Its value classification (already resolved vs another system startup state) has not been proven; the evidence only establishes the equality mismatch. Before-main existence alone is insufficient to predict an exact initial lazy slot on this host.

## Independently regenerated raw Flutter static facts

`LazyPolicy.py` parses the actual64-bit Mach-O segment/section, LC_SYMTAB/LC_DYSYMTAB, indirect table and string table. It requires exactly one ordinary lazy `_dlsym` pointer, reads the actual8-byte initial value and proves that value lies in `__stub_helper`, then records the12 helper bytes and their SHA. This is static provenance, never runtime privilege.

The clean original IPA whole SHA was rerun and matched `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a`. The retained extracted Flutter member was rehashed as `488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0` before using these facts:

- UUID4c4c44a255553144a15f431dd0ac6446.
- Unique `_dlsym` lazy slot VA/fileoff0x8341d8.
- Actual initial8-byte pointer value0x6741a4.
- Target falls inside the actual __stub_helper section.
- Helper12 bytes `5000001848ffff17a1070000`.
- Helper12-byte SHA `b3458ba6adb343cbdf86c50524f99524a7bad71b3477d688243af21e6bbdcd40`.

The analogous self dylib parsed to slotVA0x2000 and helperVA0x6f8. Those self values, rather than the Flutter offsets, are the only ones used by the TESTING live fixture. None of the raw Flutter code is executed.

## Implementation and limits

Production Adapter.c has only private atomic state/constructor bookkeeping and a status getter. It does not call image attestation, read Info.plist, bind a resolver, read/write slots or produce files. No production capability factory, external ImageProof/bool input, test home, HTTP or preference setter exists. The system is blocked before those operations because an approved final isolated Info.plist hash, a fully loader-safe stack identity verifier, lifetime/ABA proof and a supported actual startup slot state have not been established.

The macro-only fixture attempts a single CAS only if actual current VM permissions and exact raw-helper equality hold. The selector prototype has a tail passthrough for non-DEFAULT handles and a DEFAULT returned-pointer comparison; it is not an accepted production selector and no task88 query ABI/dlerror safety or full negative matrix is claimed. Fixture capture would use task86's TESTING binding only after eligibility; that setup is explicitly excluded from production. Its host clock and caller-provided public home are fixture data, not a device capability. The prototype also does not establish Runner UUID/Info hash/Isar code identity or approved system resolver address; those remaining checks are why it cannot become production-enabled by setting a macro.

Task72's existing verifier calls realpath/heap-backed helpers, so it cannot simply be hidden in the requested heap-free loader constructor. Task86 has no production original/root-binding interface; defining its TESTING factory in a ship module would manufacture authority and was not done. Future work needs a reviewed loader-safe fixed policy and private session integration, not a truthy proof struct or broad slot reclassification.

## Reproduction anchors

Static proof: `python3 LazyPolicy.py <retained-raw-member>`.

Own library creation uses clang dynamiclib with `-Wl,-no_fixup_chains` to preserve a true ordinary lazy pointer table. `build/lazy-section.log`, `own-lazy-proof.json`, `official-static-proof.json`, `red.log`, `current-red.log` and `precise-red.log` preserve the actual sequence. `FixturePolicy.h` freezes this one self-build's exact path/UUID/slot/helper values, so a fresh independently linked self fixture must regenerate it; it is not portable policy. The frozen executable's LC_LOAD paths still point to its original build-directory dylibs, which are preserved; an independent rebuild must relink fresh paths rather than pretend copied binaries are standalone.

The production dylib was compiled with no TESTING flags using the iPhoneOS SDK, target arm64-apple-ios15.0, Adapter.c only. No claim of module capture functionality follows from its successful compilation. Prior task86 schema/file/ABI and task84 constructor-order PASS remain historical evidence in their own accepted domains and are not task88 integration GREEN.

This is a concrete static-and-live-state blocked R&D result for independent review. No IPA or actual observer-enabled status is delivered.