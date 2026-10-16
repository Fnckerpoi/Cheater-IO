# Manuscript Lab 105 — stage A, disabled by design

**Current status: CLEAN_INPUT_STATIC_PREFLIGHT_ONLY; production prepare awaits independent review GO. No production IPA has been created.**

The authorized public original IPA is now verified with fixed SHA-256 `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a` and size 353131781 bytes. Its complete authenticated app inventory contains 2726 files and 403 subdirectories. Extraction succeeded. The initial generic custom-key check incorrectly matched `PERSONALIZATION`; the separately authorized namespace-only repair now preserves that official key while denying explicit TIO namespaces and shared-capability keys. Actual whole-IPA → full-directory preflight passes; this is still static evidence, not installation or collection acceptance. This implementation never removes previous Addon/NewsHub/Stage/Trial loads to pretend an original was restored. Supply an explicitly authorized clean official **1.0.5 (201)** `Runner.app` only after its provenance and exact identity are reviewed. The fixed target firmware remains Strix 1.0.4.12; this stage does not access glasses.

## What exists

- `lab.py preflight`: first authenticate the fixed complete public original IPA and derive its safe-ZIP full file/directory/mode inventory. Then require the source directory to match that inventory exactly, including Runner body, every executable and resource. Production callers cannot provide their own trusted inventory or hashes. No DB reads.
- `lab.py prepare`: new-directory-only unsigned stage-A output. Exact originals are copied; only Runner header/signature bytes, Info.plist and signature resources may change; adds the independent minimal probe and disabled marker. No signing or installation is implemented.
- `lab.py sign-plan`: generates a minimal explicit-team entitlement dictionary, not a profile clone. AppGroups, iCloud, shared Keychain, push, associated domains and unknown entitlements are not allowed.
- `lab.py check-entitlements`: checks externally supplied **public extracted entitlements metadata only**, not provisioning profiles, keys or certificates. A metadata PASS does not prove signature, device installation or runtime container isolation. Run separately with `--nested` for every framework/dylib; the metadata dictionary must be empty and app capabilities are rejected. Use an independently reviewed signing flow. This stage does not claim to perform signing.
- `LabGate.c` / `ManuscriptLabProbe.m`: own run-id Bundle ID and 1.0.5/201/UUID marker checks; exact canonical app-local `Library/Application Support/ManuscriptLab` or `tmp/ManuscriptLab` path checks. Paths must already exist; no file/directory creation occurs at runtime. All writes and collection return false. Constructor does nothing: no hook, DB function, FFI, network, UI, activation or new access.

The production probe links only Foundation, its actual SDK-transitive CoreFoundation, libobjc and libSystem. It has no original Addon/NewsHub, database engine linkage, native FilePicker import hook or scheduler. This is deliberately **not a usable importer** and shows no false saved count or document ID.

## Commands

From repository root:

```sh
python3 -m unittest discover -s official-addon/manuscript-lab -p 'test_*.py' -v
bash official-addon/manuscript-lab/build-probe.sh /absolute/new/lab-compile-directory
python3 official-addon/manuscript-lab/lab.py extract-original --original-ipa /absolute/verified/original105.ipa --out /absolute/new/original-extraction
python3 official-addon/manuscript-lab/lab.py preflight --original-ipa /absolute/verified/original105.ipa --app /absolute/new/original-extraction/Runner.app --run public-20261014
python3 official-addon/manuscript-lab/lab.py sign-plan --run public-20261014 --team AB12CD34EF
python3 official-addon/manuscript-lab/lab.py check-entitlements --run public-20261014 --team AB12CD34EF --metadata /absolute/public-entitlements.plist
# Future only, after clean-source review; this command never signs or installs:
python3 official-addon/manuscript-lab/lab.py prepare --original-ipa /absolute/verified/original105.ipa --app /absolute/new/original-extraction/Runner.app --run public-20261014 --probe /absolute/new/lab-compile-directory/TurboIOManuscriptLabProbe.dylib --out /absolute/new/lab-output
```

Run IDs must be explicit lowercase ASCII, length 6–40, unique per installation. Result Bundle ID is `com.duriea.turboio.manuscriptlab105.<runid>`, display name `文稿隔离实验105-<runid>`. Never reuse an existing installed test run or either daily official/research Bundle ID. There is no phone discovery or installation command here; the CLI cannot verify whether a Bundle ID already exists on a device, so installation remains unapproved and gated.

The compiler freezes sources/tests/tools before compiling `LabGate.c` and `ManuscriptLabProbe.m` only. Source manifest records hashes. Exact App/Isar/sqflite hashes come from static old-host evidence; framework coexistence does **not** identify which engine or backend stores manuscripts. The official Runner UUID is checked in addition to exact full-file inventory authenticated from the fixed whole-IPA SHA; UUID alone is never accepted as original identity. The probe is bound to reviewed exact SHA `f66325400dab0044257720a9c7b0760f780ea2505b30c6280cbc9b8c1cd351e0`; an adjacent self-authored receipt cannot authorize different code. Any future native source change needs a new explicit reviewed identity. Container/runtime acceptance is still required before installation.

## Test labels and limitations

Synthetic Mach-O fixtures exercise the actual preflight/staging/byte-preservation code but use explicit test-only hash overrides. They always report `SYNTHETIC_*`, never create an IPA, and cannot claim real-host or device acceptance. Production CLI exposes no hash-override/synthetic switch. Synthetic validation is not a replacement for the required clean source.

No official DB schema/path/encryption is assumed. Collection and writing remain disabled even if identity passes. Framework export names do not authorize calls: `isar_copy`, open/transactions/ownership/threads/error buffers and hot-backup consistency require exact-version contracts and isolated validation first. No live DB/mmap/WAL is copied. No personal container is scanned, and no profile/private key is read.

Future sample stage requires a genuinely new empty lab container and verified no shared AppGroups/Keychain/iCloud storage. A single unique public UTF-8 synthetic sample (at most 1 MiB) may then be saved once through official UI solely to learn schema/ID/body relationships. Consistent export requires proven engine backup/read-transaction semantics or verified close/quiescence. Unknown cases block rather than copying a live main DB or omitting WAL. Final direct-write acceptance must independently show official restart/list/full-body readback without per-item UI actions; this stage makes no such claim.

## Limited review rework

Two trust blockers were repaired: unauthenticated extra/modified source members, and unbound external probe text. Tests reject modified Runner text, an extra framework plus LC, extra/missing resources, untrusted caller inventory, wrong original archive, unsafe ZIP paths/symlinks/duplicates, and a one-byte change in actual approved probe code. Source C/Objective-C and disabled behavior are unchanged. Original extraction is authentication evidence only, not permission to create an IPA, sign, install, read DBs, or enable collection. The authorized R2 repair changes generic custom-TIO substring matching to explicit prefix/delimited namespaces. Shared iCloud/AppGroups/Keychain denial is preserved, including ubiquitous/application-groups spellings. Official analytics keys are not deleted or changed. Actual authenticated preflight passes. Unsigned prepare remains gated on independent review GO.
