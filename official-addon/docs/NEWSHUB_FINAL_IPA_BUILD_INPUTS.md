# Final NewsHub IPA assembly inputs — recipe, not package approval

Task64 source-only calibration at HEAD `d257d2e`, fixed official iOS **1.0.5 (201)** / Strix **1.0.4.12**. No build, package, signing, installation, App/DB access, new module approval or runtime claim was performed. Host `uname -m` is **x86_64**: host tests do not establish arm64 execution merely because an iOS arm64 dylib links.

## 1. The clean input is not a historical NewsHub IPA

| Input | Required identity / use |
|---|---|
| Public clean original | `official-addon/build/manuscript-lab-input-20261015-original105/RayNeo_AI_1.0.5_201_decrypted_original.ipa`, 353131781 bytes, SHA `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a` |
| Original Runner | SHA `b27f3581bea7a945560bce501823a9697fc878ab54286a330fa199bb85b828aa`, UUID `748FD301-DA60-3095-A244-9BD0558FACA5`, 156 original dependency ordinals |
| Original AOT App | SHA `f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a` |
| Original Isar / sqflite frameworks | SHA `728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88` / `5e9880de65045f1600e355e0f20e235638c4752a617fbbd28ded46fc7d0725df` |

Use the sealed whole-IPA → safe ZIP → exact **2726 files / 403 subdirectories / modes** authentication (`manuscript-lab/lab.py:72–83,229–254`). An arbitrary extracted tree cannot self-certify; never remove old Addon/Stage/Trial load commands to fake a clean original.

**Do not run historical `package-newshub.py` against this raw input.** It pins the modified 135831 IPA `c3ce…c5ed`, modified Runner `3af9…50d3`, old Addon `7923…b503`, and assumes 159 dependencies ending in Addon/Stage/Trial (`package-newshub.py:20–27,160–175,282–301`). That cleanup recipe is valid only for its historical input. Neither 1.0.6 TMIG1 nor the rejected Stage B `b44fd…ca8f` is a final assembly trust root. Preserve their rejection/history evidence, StageA f663 module and 59e58 IPA unchanged.

## 2. Proposed builtModules and actual present compile inputs

This is a concrete future manifest plan, **not a declaration that these modules have approved final builds**.

| Module | Present input facts | Assembly requirement |
|---|---|---|
| Host service/UI Addon, future new reviewed build | `official-addon/build.sh:113` includes `Addon.m`, `Core.m`, `Profile.m`, `WebSearch.m`, `NewsCore.m`, `NewsReader.m`, `ResearchUI.m`, `NewsPresentation.m`, Knowledge/TTS/other original modules; optional HTTP policy at lines72–85 | Preserve original TinyFish reader + user-configured HTTP/model behavior through a separately frozen/reviewed build; do not link a duplicate `TIONewsReader` into NewsHub. Do not copy a mixed historical experimental Addon into an isolated evidence package. |
| `TurboIONewsHub.dylib` | `build-newshub.sh:7–14`: **AIHotDaily.m, TIONewsSource.m, TIONewsScheduler.m, TIOImportQueue.m, TIONewsHubPanel.m, TIONewsHubEntry.m, NewsHubNavigation.m**; install name `@rpath/TurboIONewsHub.dylib`, arm64/iOS15, strict undefined symbols, no adhoc signature (lines44–48) | Source, scheduler and queue must truly appear in the frozen manifest. Scheduling reconstruction task63 may change their interfaces; integrate its frozen outputs, not an old binary merely named NewsHub. |
| Optional intermediate schema controller | Current StageB policy names `TurboIOStageBController.dylib` (`package-stage-b.py:18–24`) | Only an independently approved module/source/receipt triple may be registered by explicit Lead decision. Current approval table is empty. No resolver callback, fake capability or constant SHA/VA attestation can turn a linked controller into a real entry. |
| Final headless manuscript bridge, **not implemented/accepted** | Current queue has no verified backend | Its contract must come from actual schema/backend/path/ID/body/lifecycle evidence and runtime entry/context proof. Do not invent a filename, DB path, saved ID or adapter module name as if it exists. |

`TIONewsHubEntry.m:4–8` queues navigation installation from its constructor. This is a UI entry, not official persistence. `NewsHubNavigation.m:86–121` requires **old Addon UUID `3F635A04…0945` and bundle `com.duriea.turboio.research`**. A new isolated run ID will not pass unchanged. `Addon.m:43–46,526,544` likewise gates its compiled target Bundle ID. These guards need a narrow, separately reviewed final-test identity policy and real newly built image identity; never remove them or claim old byte-identical Addon compatibility with a new bundle.

## 3. Preserve TinyFish / HTTP functionality, not private configuration

- Original Addon configures `TIONewsConfigure` and embeds the existing reader (`Addon.m:502–506,554`; `NewsReader.h:7–9`). `WebSearch.m:115–129,166–170` implements the genuine model + TinyFish request chain with explicit user configuration and redirect refusal. Reuse that interface/implementation, not fake NewsHub Search content.
- Current NewsHub Search deliberately reports unsupported (`TIONewsSource.m:323–330`). Keeping the separate TinyFish page working does **not** mean NewsHub Search is integrated. Final acceptance must exercise a real shared fetch adapter and sources, not silently relabel the unsupported source as successful.
- Existing local HTTP build option is restricted to embedded `com.duriea.turboio.research` (`build.sh:72–85`). Therefore a unique test Bundle ID plus retained HTTP capability needs an explicitly reviewed scoped build-policy change, not a stock-bundle build, a broad bypass or copying the old binary and guessing it activates.
- Do not copy daily preferences, endpoint credentials, Keychain groups, bootstrap JSON, cookies, model keys or private manuscripts. A clean isolated final-test UI must accept fresh explicit user configuration later. Module absence of network imports does not disable the entire official App network; offline behavior/container isolation remain runtime checks, not proven properties here.

## 4. Concrete assembly sequence and double approval gates

1. **Authenticate raw input** from the fixed whole SHA, exact file/directory/mode inventory and original Runner/App/engine identities. New output only; no overwrite/delete of prior outputs. No 1.0.6 or historical mixed package fallback.
2. **Choose an explicit fresh test run identity**, e.g. the already defined `com.duriea.turboio.manuscriptlab105.<run>` with a clearly experimental display name. Never overwrite daily official/research installation. Ensure future Addon/UI guards explicitly recognize only that approved identity. Installation collision and fresh-container checks require a later authorized device task; source inspection cannot prove them.
3. **Freeze actual final modules and dependency seams**: original service Addon with existing TinyFish/HTTP chain; seven-source NewsHub including task63 scheduler/queue; bridge/controller only if needed and separately approved. Keep original classes defined exactly once. Do not automatically include old Stage, Trial, all old experimental sources or a default-off schema module as a substitute for a real writer.
4. **Gate A — source/binary approval**: independent tests/review + exact module SHA, complete source-manifest SHA and trusted review receipt identity. A module-adjacent self-written JSON cannot authorize itself. StageA f663 approval is not transferable to another module. Current `package-stage-b.py:24,36–50` remains empty/default denied.
5. **Append final loads to raw Runner** while preserving original ordinals **1–156**, all original non-signature load-command bytes, UUID, size and nonallowlisted body/binding bytes. Derive the appended module order from the real approved module manifest; do not copy old 157–159 assumptions or mutate binding ordinals. StageB's currently audited single-module append is a pattern, not an implemented multi-module final packager (`package-stage-b.py:60–86`).
6. **Write explicit isolated Info/markers**. Reverse-check the archive's Bundle/display/run ID, exact required load commands, module bytes and disabled/active flags; exact retained member SHA/mode + exact allowed member changes + ZIP CRC + source immutability. Recheck original framework bytes. `package-stage-b.py:112–142` provides the existing reverse-check boundary, not final module approval.
7. **Gate B — runtime/business acceptance**: real resolver/caller/handle identity and semantics, valid context, schema role, container path and official persistence contract. `module_linked` is static; `entry_installed`, `observation_active`, `official_saved`, `device_ready` cannot be inferred from it, marker enabled flags, host fake callbacks or a file name. Missing runtime evidence stays **BLOCKED_RUNTIME_ENTRY**.
8. **Signing is a future separately authorized action**. Keep minimal exact main entitlements and nested empty capabilities (`lab.py:13–29`); no inherited AppGroups/shared Keychain/iCloud. Validate post-sign extracted metadata and unchanged immutable code identity under the actual signing pipeline. No existing certificate/profile/phone capability is assumed.

## 5. Acceptance checklist: final NewsHub is more than Stage B

- [ ] Frozen manifest actually compiles scheduler + sources + queue + UI, with the original TinyFish/HTTP model service chain preserved and no duplicate reader class.
- [ ] Task63 schedule behavior, retries, source-level dedup and restart catch-up have integration tests; foreground `NSTimer` is not proof of background execution.
- [ ] NewsHub Search calls a real fetch/model adapter or remains explicitly unsupported; no fixed-template news.
- [ ] Genuine schema/ID/body/file relation and transaction/ownership/isolate contracts are established on an empty isolated public sample, not guessed from framework names.
- [ ] Official save acknowledgement and official list/full-text readback after restart drive saved counts; generated/native-done counts are separate. Current `TIOImportQueue.m:59–79` truthfully returns **saved=0 / headless unavailable / arm false**. Keep that until the bridge is actually verified.
- [ ] Do not choose a database path, background save path, copy/WAL backup recipe, private record selector or cleanup TTL before real schema/path and consumer lifetime are established. Current temporary queue files are not official durable records.
- [ ] Fresh isolated public-sample evidence package and complete final automatic NewsHub IPA have separate acceptance reports. StageA/default-off controller success is intermediate evidence, **not the final requested product**.
- [ ] Final approved integration has no per-item chooser/navigation requirement; actual official persistence and zero private-data access are verified in their own authorized scope.
- [ ] Raw-source/module approvals, exact archive postcheck, post-sign identity/entitlements and later authorized hardware acceptance all pass before any device-ready claim.

**Next dependencies, not extra packages:** task62 resolver semantics/runtime-entry design; task63 scheduler/source/queue frozen contract; an explicit service Addon/UI test-Bundle compatibility design; actual isolated schema/official persistence evidence. Build-source and mock integration work can proceed while these are blocked, but no new 352 MB archive or hash registration is justified solely to make progress appear complete.
