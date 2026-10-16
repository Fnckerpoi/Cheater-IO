# Strict per-run storage identity seam (task74)

This is a header-only compile-time storage-name seam, not runtime container isolation or Keychain entitlement proof.

## Configuration

Default (no new macro): exact historical suites/services remain unchanged:

- Addon/TTS: `io.turboio.official-private-addon`
- Profile: `io.turboio.official-profile`
- Knowledge client/UI/Keychain: `io.turboio.official-knowledge`
- Preview: `io.turboio.research.preview`
- Navigation Keychain service: `io.turboio.navigation`

An isolated candidate must compile with both:

```text
-DTIO_STORAGE_LAB_BUNDLE_ID="com.duriea.turboio.manuscriptlab106.<run>"
-DTIO_TARGET_BUNDLE_ID="com.duriea.turboio.manuscriptlab106.<run>"
```

They must match exactly. Run IDs are lowercase ASCII alphanumeric/hyphen, 6–40 characters, starting alphanumeric, excluding `official` and `research`. A lab target missing isolated storage identity fails compilation. The five derived names append `.private-addon`, `.profile`, `.knowledge`, `.preview`, `.navigation` to the exact bundle. This is not wildcard acceptance or authorization of a similarly named installed app.

Header APIs: `TIOStorageAddonDomain`, `TIOStorageProfileDomain`, `TIOStorageKnowledgeDomain`, `TIOStoragePreviewDomain`, `TIOStorageNavigationDomain`, compile constant `TIO_STORAGE_IS_LAB`.

The lab branch of `TIOImportKnowledgeConnection` returns immediately: no old Documents JSON read/parse/delete. Default importer body and empty `ImportPrivateBootstrap` body remain byte-identical. This does not disable a user's future manual configuration inside the isolated app.

## Test command and evidence

```sh
python3 official-addon/storage-identity-tests/check.py --out /private/tmp/<new-canonical-output>
```

The output must be a new absolute path with no symlink ancestors; macOS `/tmp` aliases `/private/tmp` and is intentionally rejected. Tests create their own public fixture source and native binaries only. They do not open NSUserDefaults or call any `SecItem` API. Actual KeyQuery definitions are extracted and exercised as dictionaries. The actual lab importer is compiled standalone and called with a forbidden-home trap; preprocessing also verifies file-read/parser/delete symbols are absent from the active importer body. Real modules undergo SDK arm64 syntax-only compilation, never loading their constructors.

Observed run: `/private/tmp/task74-storage-independent-20261015-1`, exit **0**:

- Default plus two lab runs: five exact names and cross-run noncollision PASS.
- Sixteen invalid macro configurations: compiler rejection PASS.
- Actual Addon/Knowledge/Navigation query definitions: default/lab PASS.
- Lab importer execution and no-read/parse/delete preprocessor checks PASS.
- Seven real modules × two configurations: 14 SDK arm64 syntax checks PASS.

Initial RED: `StorageIdentityTests.m` could not compile before the new header existed (exit 1); log `/private/tmp/task74-storage-red.log`. No runtime user-data probe was used as a RED condition.

## Exact source scope / freeze

Seven existing `.m` files receive only header imports and the listed literal substitutions, plus the authorized importer lab guard:
`Addon.m`, `Profile.m`, `KnowledgeClient.m`, `KnowledgeUI.m`, `VoiceTTSCore.m`, `VoiceTTS.m`, `NavigationUI.m`.

Pre-existing dirty source was captured before editing under `build/before-task74`; no reset or wholesale baseline replacement. Candidate source/tests are under `build/frozen-task74/source-snapshot/official-addon`, manifest `build/frozen-task74/source-manifest.json` SHA256 `87e2b168e9b0ca1ab728d4d208281f47f4a3f991e679ef2df7c68773d854a68f`; exact seven-module before/after diff in `build/frozen-task74/exact-scope.diff` (152 lines including context).

Header SHA256 `ecc6e8ae2618212487b60f391bbecf48b508b0c26645b32cc18a2c0dbd15a07e`. All ten frozen candidate source/test hashes match working counterparts after tests. README is supplemental and not part of that already-declared manifest. The SDK suite uses existing read-only adjacent module headers in the working source tree; candidate snapshot is a bounded change artifact, not a self-contained SDK or unsigned package recipe.

No signing/install, UIKit runtime, App/DB/phone/network execution, user preferences/Keychain access, private bootstrap copy, or package change. Header+source compilation does **not** prove signed per-run container isolation, actual shared-keychain separation, runtime capability, official import/save, or final IPA acceptance. Those remain distinct review/build/runtime gates.
