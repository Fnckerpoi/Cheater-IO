# Task73 — internal unsigned NativeAddon + NewsHub compile recipe

**Status: both real arm64 SDK module links passed; build review pending. No IPA, signing, installation or runtime acceptance.**

## Settled fresh output

`build/final-task73-20261015-0856/` (not the earlier rejected 0841/0843/0854 directories):

| Module | SHA-256 | Actual Mach-O UUID |
|---|---|---|
| TurboIOPrivateAddon.dylib | `cf4dfcb29fec29e583f22555ef47593ee587edb1d986d92eeaeed4c939a6ab42` | `AEFBC764-A565-362B-A8BE-95374D7ADF2C` |
| TurboIONewsHub.dylib | `6f41443b4fd7294edee3842d0e2d0f0a7338881b5d3c139f88f715f72015a05f` | `791FE86B-1863-3D95-AA88-DDC403BC7189` |

- Build receipt: `build-receipt.json`, SHA `2b7e34bd8e82e66957a38d71c8ef56c694b66aa6e67239152881d4f69988914d`.
- Source manifest: `source-manifest.json`, SHA `bc97eea73cf891d39eb44310ad07a40b0c264ed2290d16e7ffb76d105e72111e`.
- Actual recipe: `recipe-snapshot.py`, SHA `033e5f9d294436dbde1ce357f936d1bcdccef6b87ecaf82a71f282958c718a19`.
- Own reverse check: `independent-postcheck.json`. All 118 frozen source/header/include inputs match the source used for compilation and current live files at check time. This is not permission to reread live sources and accept later changes.
- Builder/test freeze: `build/frozen-recipe-task73/source-manifest.json`, SHA `3c7eeb4efd2e543952573c2a1178b2b00b6408763ca1378566dcaa837e7973d7`.

The NativeAddon uses the ordinary main-source 45 translation units, including actual Addon/NewsReader/NewsCore/WebSearch, Profile, Knowledge, TTS and navigation implementations. Main `build.sh` is read only as source-list evidence; it is never executed because it signs. No optional OTA/native firmware/music/translation/AMap/MapKit module is compiled.

The seven NewsHub units are AIHotDaily, Source, Scheduler, Queue, Panel, Entry and Navigation. They are built **separately**, after parsing the freshly produced NativeAddon UUID. Both exact lab bundle and that real UUID are supplied to NewsHub identity macros; synthetic `1111…` UUIDs are not used as an approval.

## Isolation/source evidence

Compilation sets exact target/storage bundle `com.duriea.turboio.manuscriptlab105.public-20261015`, HTTP explicit opt-in, and production test/preview/legacy macros to 0. Storage helper/8-site source changes were provided by their owners and reviewed separately; this recipe does not patch copies or modify runtime sources.

The actual native binary contains the new `.private-addon`, `.profile`, `.knowledge` and `.navigation` domains. The old exact domains/services are absent in UTF-8 and UTF-16; ordinary preference key prefixes are not mistaken for service domains. `.preview` is correctly absent because the preview-only function is excluded in production, and `TIOCreateResearchPreview` is not defined. Lab `TIOImportKnowledgeConnection` disassembles to `ret`; its external `TurboIOKnowledgeConnection.json` path is absent. No private bootstrap/configuration, UserDefaults, Keychain values, signing profiles or credential resources are read or copied.

The header/source hashes include StorageIdentity `ecc6…15a07e`, RecordingTextUI `ecb719…79352`, Scheduler implementation `a1a694…e4883` and header `71d214…6c6a4`, and the reviewed exact NewsHub identity helper/navigation. The receipt records complete hashes, not abbreviated identities.

## Actual checks and warnings

```sh
python3 -m unittest discover -s official-addon/final-build -p 'test_*.py' -v
# Future fresh output only; compiling does not approve these modules:
python3 official-addon/final-build/build_modules.py --run <unique-run-id> --allow-http --out /absolute/new-output
```

Five recipe tests pass. Pure Foundation URL-policy checks (`EndpointPolicyChecks.m`) confirm default HTTPS-only and explicit opt-in arbitrary public IPv4/IPv6 HTTP endpoints, with credentials/invalid ports rejected. These functions receive only public test strings and perform **no network requests**.

The actual modules have exact install names `@rpath/TurboIOPrivateAddon.dylib` and `@rpath/TurboIONewsHub.dylib`, system-only dependencies and no code-signature load command; `-no_adhoc_codesign` is passed. Native defines one Reader class and the actual TIONewsConfigure/TIOWebChatRequest model chain. NewsHub defines Scheduler/Fetcher/Queue and navigation entries but no duplicate Reader class.

Strict `-Werror` remains, with precisely documented existing warnings left visible: deprecated SDK declarations; NavigationPlacePicker atomic accessor; NavigationWorkspace performSelector ARC leak warnings; RecordingExportsUI/Addon signedness comparisons. Existing main unused-parameter/incompatible-pointer flags are retained. No type/header/undefined-symbol errors occur. `native-compile.log` contains those exact categories; `hub-compile.log` has no source warning. The linker only reports its `-undefined error` deprecation. No warning is a runtime behavior test.

The first compile failed on those known old warnings; 0843 then linked but was rejected because RecordingTextUI still used a legacy service. After its owner fixed/reviewed that source, 0854 linked but hit an overstrict builder expectation that a preview-only namespace must appear in production. The recipe now explicitly excludes preview and 0856 rebuilt **both** modules from fresh source; no rejected native artifact was reused. Earlier evidence is preserved, not overwritten. `current-source-block.json` is an initial historical report, not the latest status; `CURRENT-STATUS.json` points to the settled new output.

## Not accepted by this result

- No module approval table or packager was modified. This is an internal compile handoff awaiting independent build review, not a final IPA/candidate or recommendation to install.
- Actual TinyFish/model/HTTP requests, signed entitlements, fresh device containers, UI navigation and hardware were not exercised. These modules are not a proof that the entire official App is offline or isolated from all possible shared state.
- Queue headless availability and arm methods still compile to false; the scheduler has no production save bridge configured. Generated articles are not official-saved records. Official list/full-text persistence is not verified.
- No observer/resolver/image-attestation runtime module is linked into these two artifacts. Runtime entry/context/schema/ownership/backup contracts remain separate blockers. No App, engine, DB, VM or phone was executed/accessed, and no signing software was invoked.
