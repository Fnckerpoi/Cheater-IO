# Stage B packaging refactor — default blocked, no new candidate

Task57 owns only `../package-stage-b.py`, this directory and fresh evidence outputs. It does not modify the sealed StageA lab/approved f663 module, StageA 59e58 IPA, controller owner source, schema reader, open forwarder or memory-copy modules.

## Rejected actual artifact

The historical `../build/stage-b-unsigned-public/ManuscriptLab105-StageB-Observer-unsigned.ipa` SHA `b44fd88ad9f04571e71bbd1f8be77565a904047b1a23e1a6d97fd9b57353ca8f` remains unchanged. Actual RED demonstrates official Bundle ID, missing Stage B load and unsupported enabled marker. The old manifest remains incorrect historical evidence. Rejection sidecars warn **DO NOT SIGN/INSTALL**; no archive is deleted or overwritten.

## Trust roots and default behavior

- Whole public original SHA `39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a`/353131781 bytes → sealed StageA safe ZIP inventory → exact 2726 files, 403 directories and modes. An arbitrary directory cannot self-certify clean. No modified IPA is stripped to fake original provenance.
- Original Runner full SHA `b27f3581bea7a945560bce501823a9697fc878ab54286a330fa199bb85b828aa`, UUID and exactly 156 original dependencies. Header appends one `@executable_path/Frameworks/TurboIOStageBController.dylib` load, removes signature command/blob only, and verifies original commands/ordinals/UUID/size/body/binding bytes. There is no fallback `pass`.
- **`APPROVED_MODULES` is empty.** A module cannot be prepared until independent controller review GO and explicit Lead approval register literal module SHA + receipt SHA + source-manifest SHA in a separately reviewed code change. No CLI switch or adjacent self-authored manifest can approve it. The old StageA f663 approval is not reused or weakened.
- Controller runtime entry is genuinely **NO-GO / BLOCKED_RUNTIME_ENTRY**. Even a future registered module only permits explicitly internal/default-disabled output, not an installation candidate. Static linked bytes do not prove resolver installation, first lookup, caller-sensitive dlsym semantics, valid context or observed schema.

Current actual trusted-input check returns `BLOCKED_UNREVIEWED_MODULE` with all linked/entry/active/device flags false, and creates no output directory. No new Stage B IPA has been produced by this refactor.

## CLI and tests

```sh
python3 -m unittest discover -s official-addon/manuscript-lab/stage-b-packaging -p 'test_*.py' -v
python3 official-addon/manuscript-lab/package-stage-b.py audit --ipa /absolute/rejected-or-internal.ipa --run public-20261015
python3 official-addon/manuscript-lab/package-stage-b.py preflight --original-ipa /absolute/trusted-original105.ipa --app /absolute/authenticated/Runner.app --module /absolute/module.dylib --run public-20261015
# Future internal-only use, after independent review and explicit literal registration:
python3 official-addon/manuscript-lab/package-stage-b.py prepare-internal --original-ipa /absolute/trusted-original105.ipa --app /absolute/authenticated/Runner.app --module /absolute/reviewed-controller.dylib --receipt /absolute/reviewed-receipt.json --run public-20261015 --out /absolute/nonexisting/internal-output
```

Rejection/block reports exit 2. Outputs must be fresh absolute paths without symlink ancestors or input overlap. The refactor never deletes or recursively cleans an output. If a future reviewed internal build fails after creating output, `PARTIAL-UNUSABLE.json` is retained; no final report means unusable. Even a completed internal build retains that historical marker plus its explicit final `INTERNAL_UNSIGNED_BLOCKED_RUNTIME_ENTRY_NOT_FOR_INSTALL` report.

Unsigned construction copies trusted members individually excluding signature resources, sets independent run-id Bundle/display name, preserves official version/analytics data, removes original URL/background/export associations, and adds a disabled marker. Postarchive reverses Info/load/marker checks, verifies exact member differences, every retained member hash/mode, Runner allowlisted changes, module bytes, source immutability and CRC. `accepted`/`structural_valid` in an audit mean only archive structure; status remains runtime-blocked and entry/active/device flags remain false. It is not approval for installation.

Minimal signing plans and externally extracted entitlement metadata checks reuse sealed StageA allowlists (main exact three keys, nested empty dictionary). No profile inheritance, signing key/profile reads, signing or installation occurs here. Unsigned output cannot establish signed entitlements or device container isolation.

## Evidence scope

Tests include actual rejected archive RED, actual trusted original Runner 156→157 append with exact body/ordinal preservation, synthetic wrong bundle/missing load/no module/tampered module, unknown/self-authorized module rejection, wrong original whole hash, symlink/overwrite/output overlap and minimal entitlements. Synthetic archives are explicitly not installable code. Full new production StageB preparation remains unexercised until a module is independently approved; this is stated rather than weakening review gates to manufacture an IPA.

Controller owner now produces a new `TurboIOStageBController.dylib`, not the rejected Observer dylib. Its unreviewed compile output, linked status or allfalse receipt does not grant registration. No runtime capture path, saved DocID, schema export file, DB write, backup or user-ready candidate is claimed. No App/DB/phone/FFI/VM/hook/network operations are performed.
