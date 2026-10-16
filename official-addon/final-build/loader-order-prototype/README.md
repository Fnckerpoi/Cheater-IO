# Flutter strong-dependency loader-order prototype — task107

**Verdict: a safe bounded static header transformation exists, and the dependency-order mechanism passes a real own-host loader fixture. This is NOT an approved iOS runtime integration or IPA.** Original inputs, previous outputs, source/approval registries and signed application artifacts are untouched. No codesign, product IPA, App/real Flutter/engine/DB/phone execution or network operation occurred.

## Exact clean Flutter facts and copy-only result

Public original IPA SHA `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a`; its Flutter member SHA `488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0`, size 9150272 bytes.

- Original 50 commands, `sizeofcmds=6872`, end `32+6872=6904`.
- The first file-backed content section starts at 16384. The `__TEXT` segment itself begins at file offset 0 because it contains the header; it must not be confused with usable padding. The 9480 bytes after the old load-command end and before section content are zero.
- `LC_LOAD_DYLIB @loader_path/../TurboIOStageBObserver.dylib` requires 72 bytes. Removing only the 16-byte `LC_CODE_SIGNATURE` command yields net growth 56 bytes, so the new command end is 6960. There is no file offset/VM segment shift.
- Original 36 dependency names and ordinals 1–36 remain in identical order; the new dependency is 37. Every retained command, UUID, file size and every byte outside the strictly allowed header fields/load-command extent is unchanged.
- Original signature data blob at 8905520, length 244752, is **preserved byte-for-byte but no longer referenced by an LC_CODE_SIGNATURE**. No signature is recomputed, validated or claimed. This prototype is an unsigned altered copy and is not ready for installation.
- Bind/rebase/weak/lazy/export regions are individually hash-checked unchanged; because their bytes, original commands and original ordinals do not move, the old binding graph is not renumbered. No new imported symbol binding is added.

Copy-only artifact:

`build/real-flutter-copy/Payload/Runner.app/Frameworks/Flutter.framework/Flutter`

SHA `64ba99fb19cdc788e12d9c379e95358a4c6f0910bb2f5a6ae291179ebd531eee`.

Detailed report: `build/real-flutter-copy/report.json`; original-copy hash/mode is checked unchanged. This directory is **not a complete app or IPA** and intentionally contains no product bootstrap. The system-only proposed bootstrap dependency set used for the structural check is a design condition, not independently observed actual bootstrap identity. A future exact bootstrap module must be reviewed for dependencies and cycles before assembly; a caller-supplied tuple is not runtime proof.

## Actual loader-order A/B, not an otool inference

The own fixture has separate `OrderState`, `BootstrapMarker`, `FlutterFixture` and Runner images. Constructors increment a shared process-local atomic order; Flutter immediately performs its simulated first `dlsym` lookup. No pointer/log touches real engine or application state.

1. **A / genuine RED:** Runner loads unmodified FlutterFixture before loading BootstrapMarker. Flutter's first lookup observes no initialized bootstrap; the assertion fails with exit `-6`. Late bootstrap loading cannot repair that first lookup. Log: `build/host-order-direct/actual-late-red.log`.
2. **B / genuine GREEN:** the same header patcher appends the bootstrap strong dependency to a fresh own FlutterFixture copy. Actual host dyld resolves `@loader_path/../TurboIOStageBObserver.dylib` from the fixture `Frameworks/Flutter.framework/Flutter`, loads the sibling observer and runs its constructor first. Both dynamic-load and **directly linked Runner → Flutter → bootstrap** cases output order `bootstrap=1, flutter=2, first_lookup=3`. Direct graph repeated 20 fresh processes with identical order. Logs: `actual-strong-green.log`, `actual-direct-runner-green.log`, `repeat-20.json`.

Host is x86_64 macOS. The host fixture proves its actual dyld path/initializer ordering for these own images, **not exact iOS dyld behavior**. This is why there is also a separate iOS arm64 linked fixture under `build/ios-linked-fixture/`: Runner LC → `@rpath/Flutter.framework/Flutter`, framework LC → `@loader_path/../TurboIOStageBObserver.dylib`, separate system/own-state dependencies, normal sibling layout and no signatures. All objects/libraries/Runner link for iOS15; **none is executed on iOS**. iOS runtime path/ordering acceptance remains unverified and cannot be manufactured on this host without separately authorized hardware/runtime work.

## Tests and reproduction

```sh
python3 official-addon/final-build/loader-order-prototype/check_fixture.py --out /absolute/new-host-fixture-output
python3 -m unittest discover -s official-addon/final-build/loader-order-prototype -p 'test_*.py' -v
```

The seven transform tests use the settled own host fixture at `build/host-order`; retain that test input or generate it first with the fixture command. Cases cover exact retained commands/dependency order/payload, duplicate dependency, wrong install identity/clean hash, malformed header, nonzero/full padding, circular/unknown bootstrap dependencies, no overwrite and exact sibling resolution. All passed. The source `patch_flutter.py` has no install/signing/IPA or registry action.

## Integration handoff — do not skip these conditions

- Use only a separately approved exact bootstrap module with no Flutter/App/back-edge dependency; review its entire transitive dependency graph. Its constructor must perform only the separately approved early installer work. This prototype adds no real installer.
- An observer appended to Flutter is structurally a prerequisite of Flutter initialization, unlike a separate Runner sibling. But the fixture does not prove that other framework constructors cannot issue earlier FFI lookups or that a prebuilt AOT image has no other initialization sequence. Final whole-graph/runtime evidence still matters.
- New Flutter whole-file SHA is expected after header rewriting; immutable code-section identity/UUID is retained. No existing full-file policy or approval may be silently relaxed or reused. Any future image policy/Info/hash recipe must bind the actual new link output separately and keep raw input provenance.
- Preserve original Flutter ordinals, body/binding bytes, UUID and modes in the final independent reverse check. Reject insufficient padding, existing observer load, malformed input or any circular dependency rather than shifting sections/rebuilding LINKEDIT.
- Bootstrap residency/cached forwarder lifetime, actual qualified DEFAULT lookup, loaded image/real export identity, slot current protection/resolution/CAS safety and capture state are still separate gates. `module_linked` is not `entry_installed`, observed schema, saved manuscript or device readiness.
- No product IPA or candidate is created here. Lead/bootstrap owner must open a new reviewed assembly scope if this structural approach is selected.
