# Agents Anywhere embedded workbench

The AA 工作台 shipped inside the TMG addon is a prebuilt payload plus its source.

- Payload: `android-addon/assets/aa/aa-embedded-payload.apk` with sidecar `payload.json` (sha256, entry `com.agentsanywhere.embedded.PayloadEntry`). Both files are tracked in git; `addon_assets.aa_assets()` validates them and `assemble_apk.py` / `verify_apk.py --public-assets` embed them together with the AA activity manifest patch.
- Source: `android-addon/aa-embedded/source/` is the exact tree of the Agents-Anywhere changes on branch `fix/apk/embedded-workbench-bootstrap` (commit 7d799bc7, based on main e5c0179d): `android/app/src/embeddedDebug`, `android/app/src/testEmbeddedDebug`, `android/app/build.gradle.kts`, `android/scripts/verify_payload_gate.ps1`, `android/README.md`.
- Rebuild: apply `source/` over the Agents-Anywhere checkout at e5c0179d, then follow the "Embedded payload build" section in `source/android/README.md` (JDK 17, Android SDK 36). Copy the resulting payload and `payload.json` to `android-addon/assets/aa/`.
- Build: always use `build-tmig1-windows.ps1` from the repository head. Replacing only `classes8.dex` inside an older guarded APK drops the AA assets and manifest entry.
