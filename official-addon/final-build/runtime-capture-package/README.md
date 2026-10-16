# Task116: unique Lab schema-observation attempt, pre-archive review required

**Current state: PRE_ARCHIVE_REVIEW_REQUIRED. No IPA has been created.** This is an intermediate public schema-capture attempt, not completed NewsHub database saving. User signing/testing must wait for independent source/build and later actual archive review.

## Actual new-run artifacts

Root: `build/capture-20261015-a-prepare/`.

- New bundle/run: `com.duriea.turboio.manuscriptlab105.capture-20261015-a` / `capture-20261015-a`. No previous public-20261015 bundle is relabelled.
- NativeAddon freshly compiled from the reviewed task73 118-file snapshot: SHA `afe7febfe9741f16a3acbdf3054db453d17f90c2d6399cd1d3ba04a2ea0d63e9`, UUID `E88AA2EF-F720-3F5F-8042-7A8302687F05`.
- NewsHub separately freshly compiled with the new exact bundle and real Native UUID: SHA `2c22c7f3f6dc4df9f8fcfa7be72100d8f8dd83f1f58cfbd67f11c6dc9318bb80`, UUID `ABC1313D-2C56-31EA-8606-C76ED080A9DE`.
- Runtime observer built from the complete reviewed R114 32-file source snapshot, not R2 lazy-helper drafts: SHA `a3db5f0c14f31383c6b3dfccdf666364f9daee6e78a5e73a5ba79196b397f244`, UUID `CC971183-6C91-34C8-B9B8-10D80927307C`; only libSystem dependency, no signature.
- Runner SHA `d3b18bf9c73c556cac1c7a7d6bf32c8c883fc6920b41d058c82fdc84b58f8676`; raw UUID unchanged. Original 156 dependency ordinals preserved; Native/Hub appended as 157/158.
- Flutter SHA `64ba99fb19cdc788e12d9c379e95358a4c6f0910bb2f5a6ae291179ebd531eee`; raw UUID and immutable text retained. Original 36 ordinals preserved; strong observer dependency is ordinal37 at `@loader_path/../TurboIOStageBObserver.dylib`. Only signature load command removed; original signature blob remains unreferenced and unchanged. It is not a valid re-signed framework.
- Actual staged Info SHA `ddf5dffc6ed69617a55fb2cafa1de797d12ae7035cf40d8ed02deada28daefc2`, 7937 bytes. UIFileSharing and opening-in-place are enabled only for this exact Lab Info; original URL/background associations are removed.

The whole raw input is pinned SHA `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a`; the sealed original inventory has 2726 files/403 directories. Stage construction skips 89 signature-resource files, changes only Runner/Info/Flutter, adds three dylibs and preserves the other 2634 files and modes exactly. Original source IPA, prior modules/StageA/bad rejected IPA and all other owners' sources are untouched. No data/preferences/Keychain/profile/private Documents JSON is copied. The new Native contains its own per-Lab storage domains and excludes the external Knowledge JSON importer; fresh signed device/container behavior is still unverified.

## Fixed policy is genuinely compiled

`generated-policy/LabPolicy.inc` and `LabConfig.inc` derive from this actual Runner, Info, freshly built Native, patched Flutter and exact raw Isar. The builder checks raw member hashes, original command counts, preserved UUIDs/code, exact Isar open export VA `0x1e530`, unique Flutter dlsym slot `0x8341d8` / raw helper metadata `0x6741a4`, real relative image paths and exact Native/observer load names. The policy is not copied from OwnFlutter/OwnNative/OwnIsar fixtures.

Policy SHA `5f394a76242a6921adf1c428f33b15ce3a05073b2566deebf20a657d3fe91046`; config SHA `ea20529c1032863ae4579be4dedce7d516bc1d0209926da1a8b00632e965531b`. There is no adapter-self-hash cycle: runtime policy binds other fixed images, not its own entire file hash. Adapter source hashes, source-manifest identity, compiler command and the final adapter hash are recorded externally for independent build review, not accepted from an adjacent self-authored policy. The static audit recomputes both includes byte-for-byte and checks corresponding Info/text/UUID/dependency constants occur in the actual compiled observer.

Trust roots are literal independently reviewed receipt/source identities: task73 receipt `2b7e…8914d`, source `bc97…2111e`, recipe `033e…18a19`; R114 source manifest `5b30…55f9fb`, exact Adapter.c `d656…1941`; task107 transform manifest `48497…4d30b`; sealed lab helper/parser have their own exact reviewed pins. The full hashes are in `build_capture.py` and actual receipts. Fixture-generated policies in the reviewed reference snapshot are never included in the new adapter compile command.

## Real build/audit commands (no automatic archive step)

```sh
python3 -m unittest discover -s official-addon/final-build/runtime-capture-package -p 'test_*.py' -v
python3 official-addon/final-build/runtime-capture-package/build_capture.py --out /absolute/new-output --run capture-20261015-a
python3 official-addon/final-build/runtime-capture-package/audit_stage.py --root /absolute/settled-output
```

The builder freezes reviewed sources before compiling, does not patch runtime core sources, never calls codesign, rejects existing/symlink output paths and never recursively deletes an output. It deliberately has **no IPA creation code**: Lead must obtain independent source/build receipt review before separately authorizing the archive step. A generated static policy is not module approval or runtime proof.

Tests reject wrong run, wrong module hash/UUID, circular/unsafe adapter dependencies, overwritten/symlink output, altered raw Runner bytes, wrong actual Lab Info and missing Flutter strong dependency. The full static audit independently compares all original/added/changed members, modes, exact Runner delta, Flutter LC/body/UUID, module identities/dependencies, old/new storage namespaces, generated policy recomputation and actual embedded policy constants. No App/engine/database/iOS code is executed. Native compile warnings remain only previously approved categories; capture adapter is strict Werror and has only the linker deprecation warning.

## Runtime truth and user test limits

`capture_runtime_observation_attempt=true` means code is present to **attempt** the gated observation after loading. `slot_installed=false`, `schema_event=false`, `file_created=false`, `device_ready=false`, `official_save_verified=false`, `db_write_enabled=false`, `final_newshub_complete=false` remain false because no iOS process was run. No output file has been fabricated or packaged.

The adapter constructor only proceeds after actual loaded image/Info/dependency/VM/resolver checks. **Raw lazy helper is unsupported**; if the real current Flutter slot is still its raw helper or is otherwise unqualified, the adapter leaves it unchanged and remains blocked/unobserved. Static code inclusion or strong dependency does not prove successful runtime installation. Start occurs only after the actual qualified exact `isar_open_instance` lookup, not merely on constructor load. R114 requires bounded exact name plus exact returned Isar pointer. Existing cached open pointers may prevent any event.

Nonnull key means no schema read/export. The allowed path is the first validated public schema-only projection via reviewed owned copy/projection into `Documents/ManuscriptLab/captured_schema_projection.json`, no-overwrite mode0600, with no records/body/key export. There is no DB-writing or official-saving path here. Queue remains headless unavailable and scheduler bridge nil. No networking service was exercised; this does not claim the whole official App is offline. Entitlements/signature validation remains a later explicit user-signing review, with exact isolated app identity and no shared AppGroups/Keychain/iCloud inheritance.

Even after a future independent archive review, this can only be an intermediate user-signed Lab capture test, not the final requested automatic-save NewsHub product.
