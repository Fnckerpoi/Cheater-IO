# task86 — pure C schema-capture core; real runtime eligibility BLOCKED

## Precise outcome

This module implements and tests a real typed schema observer/export seam, but **does not yet implement an authenticated production runtime-entry capability**. It is not an active schema-capture IPA candidate. The production receipt explicitly reports `production_eligible_runtime_path=false`, `production_path_authorized=false`, `production_file_export_enabled=false`, actual slot read/write=false and actual schema captured=false.

The production constructor performs only an atomic store of `TIOCaptureBlockedEarlyRuntime`. It does not call Foundation/UIKit/ObjC, allocate, inspect a slot, resolve a target or create files. There is no public production bind/arm/trust-boolean/ImageProof interface. The internal typed open wrapper is hidden from production exports and cannot be reached through a production resolver qualification factory. No test setup can activate a real production entry. The file/root-binding path exists only under `TIO_CAPTURE_TESTING`; this distinction is validated in the ARM64 module exports/imports.

Why the actual runtime path is still blocked: a loader-safe, authenticated isolated bundle/container identity; exact Runner/Flutter/Isar loaded code identity; verified current startup slot value and real system resolver binding; mapping lifetime/ABA control; installation before the real first FFI open cache; and a private capability connecting those proofs are not established by the current constructor. Existing image-attestation uses allocation/filesystem helpers, so it was not silently placed under the loader lock. Task84's real constructor A/B evidence specifically proves that "before main" is not sufficient if Flutter's own constructor looked up FFI earlier. Disk lazy-helper0x6741a4 is not treated as callable or as a runtime ticket.

No official App/engine/Flutter GOT, DB, phone, Keychain/prefs or actual private records are accessed during development. Previously reviewed modules and package sources are not changed. No IPA is emitted by this module.

## Implemented capture interface and lifetime

`Capture.h` defines the exact11-argument typed open forwarder. In self-owned tests, the actual eighth key argument is checked before clock/memory calls. A nonnull key causes zero schema-copy reads; name/path/key pointees are never read. An immutable original is bound once during single-threaded host fixture setup. First eligible null-key attempt atomically consumes the process-local terminal latch; whether invalid/expired/export-failed or successful, further attempts do not recapture. The original is still invoked exactly once on every call with unchanged arguments/out/status/errno. TTL120 seconds and rollback reject before copy; test setup cannot rearm/reset the latch.

Only schema role X5 uses the exact engine-specific +8 byte pointer and +16 length (two8-byte fields) established by task42 for engine SHA728997...8b88. This is not generic Rust String layout. The existing bounded-copy/SchemaView module supplies exactly two field copies and one exact payload copy, <=65536 bytes. There is no strlen on engine memory, no capacity/+0 read and no all-string capture. Parsed and projected owned allocations are wiped/freed before the original operation may consume/free the schema. The own-engine dylib test actually frees both schema buffer and object in its original, so ASAN exercises the lifetime rather than only checking a boolean.

Observer code here is pure C and does not catch the original's exception. A separate Objective-C host harness throws from the original after capture; it propagates intact with original-call count1. No Foundation or Objective-C object is used in the observer's implementation; Foundation is used only by that exception test executable.

## Strict C schema policy and canonical metadata

`SchemaPolicy.c` is a bounded JSON parser and schema-only allowlist validator, not a general records importer. Input <=64KiB, AST nodes<4096, nesting<=8, collections<=64, properties<=256 per collection and1024 total, indexes<=128 per collection and512 total, names<=256 UTF-16 units. Strict UTF-8 rejects overlong encodings, surrogate code points, incomplete sequences and code points aboveU+10FFFF; JSON escapes normalize before duplicate-key/name checks. C0 characters are rejected in all schema strings. Trailing data, malformed JSON, duplicate escaped keys, unknown keys/types and dynamic enumMap reject with fixed state only—no input fragments or engine/parser errors are logged.

Accepted metadata fields are exactly the fixed task45 schema protocol subset:
- Collection: name, idName, embedded, properties, indexes, version.
- Property: name, type, target.
- Index: name, properties, unique, hash.

Optional missing/null values preserve their semantics; no artificial id/DocID/record values are invented. Type names are the19 public Dart coreName values from fixed IsarPlus1.2.6 source. Object targets must resolve to an embedded collection in the same schema array; embedded indexes, duplicate names and unsupported index types reject. Legitimate property names `password`/`body` remain permitted as names only, whereas `password`, `records`, `value` or arbitrary dynamic data in non-schema positions reject. enumMap is legitimate engine metadata but unsupported by this observer policy, not declared invalid engine data.

Canonical serialization only emits validated allowlist nodes in a fixed key order with normalized strings, preserving array order and optional fields. It does not dump original input text. Output envelope is `{schema:[...], actual_manuscript_schema_known:false, observer_policy_supported:true}`. Every byte is schema metadata; no name/path/key/record parameter or opaque pointer/address is exported. Complete envelope size must remain<=65536 bytes.

Primary evidence reused read-only from task45: repository ahmtydn/isar_plus commit99a68d4f6d4b6c1d075deffd8c5ac6a9d5c40604, schema.rs SHA d6d92965dd32b435e4d3ae029d6c145ebd87a152cf4c516e291e70cea5d066d5 (collection7–18, array38–43, validation128–205, property228–235, index248–253), isar_schema.dart SHA be3132937384096b05b56241b3100670e372ed4596a75f759fb0d8dc51f58410 (toJson49–56/118–124 and type172–228). This new C implementation still requires its own independent review; reusing source evidence does not reuse an earlier implementation's GO.

## Explicit new-file export policy

Only a host test factory accepts a caller-created canonical public fixture home directory. This is **not production sandbox authority**; production has no caller-directory input or authorized home-root getter. A future live adapter must derive a trusted reviewed app-container directory from authenticated bundle/lifetime identity rather than accepting caller paths/flags. No arbitrary path is exposed by the production module.

Within the trusted test root, Documents and ManuscriptLab are opened one component at a time with dirfd+O_DIRECTORY+O_NOFOLLOW. New directories are0700 and owned by current UID. The only leaf names are fixed compile-time constants; no key, collection name, schema name or external path component becomes a pathname. A pending file is exclusively created O_EXCL|O_NOFOLLOW0600, written with a short-write loop, fsynced, then atomically renamed with RENAME_EXCL. Existing final/pending files or symlinks are not overwritten or removed. An unsuccessful own-created pending file is removed, and final never appears partially. The terminal latch prevents retrying after an export failure or success. The held directory descriptor is process-lifetime fixture state, not an authorization to write real app data.

The interface's test-only root is a NUL-terminated C string; embedded-NUL bytes after the terminator cannot be represented by that interface and are not claimed detected. The production path is blocked and never accepts such a caller string. All constructed descendants/leaf names are fixed and contain no NUL. A hostile same-process writer with authority to rename held-directory entries cannot be fully excluded by filesystem calls alone; the contract is a reviewed isolated container with no untrusted concurrent directory mutation, not a global filesystem integrity guarantee.

## Real tests and reproduce

Run `bash build-capture.sh`. It creates a fresh private source snapshot and hashes every actual input. In normal, ASAN and UBSan modes it executes:
1. Strict C policy positives/negatives and deterministic canonical serialization.
2.13 fresh process capture/path attacks: public canonical success, invalid/oversize UTF8/JSON/escaped duplicate, TTL/rollback, preexisting final file/symlink, pending file/symlink and parent symlink. Outside public sentinel bytes remain unchanged; successful output is valid JSON and0600.
3. A real self-owned `OwnEngine.dylib` exporting isar_open_instance, actual RTLD_DEFAULT lookup through task71's qualified fixture bridge to the typed capture wrapper; real key zero-copy, exactly once original, actual original consumption/free, one output only, expiry still forwards cached wrapper.
4. Original Objective-C exception propagation after owned cleanup/capture.
5. Production ARM64 SDK compilation, hidden/unavailable fixture binding and typed wrapper exports, no production file/dlsym/VM/ObjC imports.

The own public-schema fixtures are source-derived test data, **not an official manuscript database or observed runtime schema**. ASAN uses detect_leaks=0 because this platform lacks leak detection; no leak-test claim is made. Host is x86_64; iOS ARM64 is compile-only.

## Frozen handoff

Start1792058290; self-deadline1792059790. Freeze1792058897, output `build/capture-amRYHK/`:
- Source manifest SHA83871b0c992ce3b42b1425a739745f9ad7a2bb5bb2df235bca2600244aef68f0.
- Production iOS module SHA2ff4e8cc8e128570b85b7addf95ac8e3488818a0c29268b654069f8149519636.
- Evidence: build/red.log, build/green.log, frozen per-mode policy/attack/lookup/exception logs, source-manifest.json, production-imports/exports and receipt.json; build/final-build.log summarizes the full pass.

Independent review is separate. This is a tested internal capture/export core with an honest blocked production entry—not a false-ready interim capture IPA, final NewsHub ingestion success, DB write or official saved-record proof. The missing constructor/runtime capability must be closed explicitly before packaging can advertise observation enabled.

## Independent review R1 — private directory mode

Task87 independently found that R0 accepted a preexisting ManuscriptLab directory with0755 permissions even though the new file was0600. Lead authorized a single narrow five-minute R1 correction (exact fix deadline1792059646). The dedicated directory is now accepted only if owned by the current UID and **exact0700** (including rejection of special/group bits); an existing0755/0770 directory is rejected, never chmodded. The parent Documents directory may be0755 under its separate owner/directory policy. Test-only fixture home binding also requires exact0700/owner/canonical path. Production path authorization remains unavailable and blocked.

The R0 artifact is retained unchanged at build/capture-amRYHK. Actual R0 mode regression produced RED; new full normal/ASAN/UBSan runs each require parent0755 unchanged, dedicated0755/0770 rejected without sentinel or mode changes, and precreated0700 allowed. No parser, export shape, bridge, slot policy or production capability changed.

R1 freeze1792059507, output build/capture-IS0qjL, source-manifest SHA6e5de8ac644d3867c0767aab27464e659768a77f1362d86abc14ea2361ea9444. Production module SHA remains2ff4e8cc8e128570b85b7addf95ac8e3488818a0c29268b654069f8149519636 and is byte-for-byte equal to R0 (corrected path code is TESTING-only). Evidence: build/r1.patch, r1-mode-red.log, r1-build.log, r1-handoff.json and frozen per-mode attack logs. Independent R1 review is separate; no observation-enabled IPA is claimed.
