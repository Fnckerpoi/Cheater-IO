# Schema observer — offline module only

This module is **not connected to a hook, Probe, LabGate, packager, engine, DB or phone**. There is no constructor, automatic activation, FFI call, VM call or write operation. `TIOSchemaContextInit` is disabled by default. Integration and runtime authorization are separate scopes.

## Machine/source-bound view, not a Rust ABI

The only supported identity is IsarPlus 1.2.6 engine SHA-256 `728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88`, audited C ARM64 `isar_open_instance` entry VA `0x1e530`, sixth integer/pointer argument (entry X5). The exact-machine proof supports reading two separate 8-byte little-endian fields at schema-object +8 (byte pointer) and +16 (byte length). **It does not establish generic Rust String layout.** +0, capacity, name, path, key pointees and constructor inputs are never read.

Proof: task42 `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/tio-schema-role-static-efgegcpg/REPORT.md`, especially source `instance.rs` 42–65 and bounded machine dataflow. Engine +8/+16 may only be used after an independent caller verifies the exact artifact/load identity, isolated public-sample lab identity, audited role/ABI and original-entry lifetime. Context fields are explicit **caller attestations**, not this module independently certifying runtime identity. No boolean constitutes dynamic authorization. Context must be immutable while in use, including enable/identity/time configuration; the active guard prevents same-context concurrent/reentrant capture. The bounded-copy callback itself must validate full readable mappings, handle concurrent unmap safely, and fail on a short copy; the module does not provide a production memory reader.

Key argument must be NULL before any callback. Default-off, identity/hash/role/ABI/lifetime failures, TTL expiration (monotonic caller seconds, maximum120, expiry at exact endpoint), event cap (maximum16), null/unaligned object or overflowing fields fail closed. Successful calls make exactly three copy requests: +8/8 bytes, +16/8 bytes, payload/exact length. Maximum payload65536, never strlen. No opaque pointer is retained. Owned copy must be consumed and `TIOSchemaOwnedBytesRelease` called **before invoking the original consuming operation**; no cross-original deferred processing is permitted by this contract. Release wipes the owned byte allocation. Failed payload copies are wiped/freed, never returned partially.

## Strict UTF-8 / JSON metadata subset

`TIOSchemaDecodeMetadata` only accepts the owned byte copy. It validates strict round-trippable UTF-8, complete JSON, nesting≤8, duplicate JSON member rejection (including escaped duplicate keys), root array≤64 collections, ≤256 properties/collection and≤128 indexes/collection, totals≤1024 properties/512 indexes, names≤256 UTF-16 units, version unsigned8 and strict JSON booleans. Unknown keys/types reject. Counts/type-frequency metadata only is returned: no collection/property names, payload, parser error strings, key/error/record contents, collection IDs or DocIDs.

Fixed public source evidence supplied by task45, repository `ahmtydn/isar_plus`, commit `99a68d4f6d4b6c1d075deffd8c5ac6a9d5c40604`:
- `packages/isar_core/src/core/schema.rs`, SHA `d6d92965dd32b435e4d3ae029d6c145ebd87a152cf4c516e291e70cea5d066d5`: array parsing38–43, collection7–18, verification128–205, property228–235, index248–253.
- `packages/isar_plus/lib/src/isar_schema.dart`, SHA `be3132937384096b05b56241b3100670e372ed4596a75f759fb0d8dc51f58410`: canonical collection toJson49–56, property118–124 and 19 public coreName values172–228.
- Existing local source directory: `/var/folders/tr/v96j2p5d123ch_4v42d66mbc0000gn/T/isar126-schema-shape-4jr7z52w/`.

Collection keys: `name`, optional/default `idName`, `embedded`, required `properties`, default `indexes`, optional `version`. Properties: nullable/missing `name`, required `type`, optional `target`; Object/ObjectList target must resolve to a same-array embedded collection. Index keys are name/properties/unique/hash, all required; target/type/index restrictions follow the cited source. Duplicate names reject.

Counts-only output is a safe parsing foundation, **not successful schema recovery** and not enough to construct a DB writer. A later separately reviewed scope would need validated canonical field names/types/index definitions; this module deliberately does not export those strings or claim actual schema capture.

This is deliberately a **bounded observer policy subset**, not the entire engine-accepted language. Dart `enumMap` may contain unconstrained nested dynamic values; this module currently rejects it rather than retaining/dumping arbitrary contents. Budget excess/unsupported legitimate metadata is not proof that the engine schema is invalid. Rust serde may ignore unknown keys, but observer policy rejects them. `observer_policy_supported=true` means this subset accepted; `actual_manuscript_schema_known=false` remains unconditional. Canonical public protocol fixtures are synthesized from the cited source, **not an official manuscript DB sample or an engine execution test**.

## Tests and SDK compilation

Run from this directory:

```sh
bash test-schema.sh
```

Real host C tests inject bounded mapped memory copies; zero-read tests cover key-present/default-off/identity/hash/role/ABI/lifetime/TTL/event failures. Positive read traces prove only the two8B fields and exact payload are requested. Overflow, short copy, unreadable ranges, length0/>64K, max64K and reentrancy are exercised. Real Objective-C Foundation tests cover valid source-shaped fixtures and malformed/trailing JSON, invalid UTF-8, unknown keys/types, duplicate member/name, enumMap rejection, depth/count/byte budgets and metadata redaction. Both run with AddressSanitizer+UBSan. macOS leak detection is unsupported by this toolchain, so `detect_leaks=0` is explicit; this is not a leak-check claim.

The script compiles, but does not link/deploy, C and Objective-C iOS15 ARM64 objects with the installed iPhoneOS SDK. No signing, package mutation, device execution or engine import is performed. Artifacts/logs are under this directory's `build/` only. StageA source/package remain untouched.

## R1 review correction and timing

Initial implementation froze at epoch1792042190 within task43 self-bound deadline1792042347, but independent review found two blocking gaps, so the original15-minute result was **NO-GO**, not accepted completion. Lead separately authorized a narrow R1 correction, exact start1792042352/deadline1792042652: typed JSON rejection statuses and per-context monotonic clock rollback. No new schema fields, hook or export feature was added.

`TIOSchemaJSONLimitExceeded` means observer byte/depth/collection/property/index/name budgets exceeded, not invalid engine schema. `TIOSchemaJSONUnsupportedPolicy` covers unaccepted shapes/keys/types including legitimate but unsupported enumMap; `TIOSchemaJSONDuplicateKey` distinguishes duplicate JSON members; invalid UTF-8 and malformed syntax have separate fixed statuses. No Foundation error string/raw input escapes.

The active-guard-protected last_seen_seconds rejects clock rollback before any bounded-copy callback, including a150-second successful call followed by101. ContextInit is one-shot construction only; reinitializing/replacing a context can reset its per-context advisory TTL/event budget. This standalone module does **not** enforce a global process-wide cap or irreversible arming. A later integration controller must enforce authenticated identity, stable lifetime, one-shot arming and anti-recreation policy; caller attestations and limits are not proof of runtime authorization.

## task49 — explicit canonical schema projection (new scope)

The accepted task43/46 counts-only baseline remains preserved under `build/task49-baseline/`; its manifest SHA-256 is `1099d01299c1fbe951dc3f8bec6cfcf1fd6d6049e774a01698c99c10f2a7ca66`. That baseline passed its own review; this is a separate new feature requiring separate GO.

`TIOSchemaDecodeMetadata` is unchanged and still returns counts only, never field names. New explicit opt-in `TIOSchemaDecodeProjection` returns `schema` plus `observer_policy_supported=true` and `actual_manuscript_schema_known=false`. It copies the already-owned entry-time bytes once, runs the existing strict UTF-8/JSON/schema validator against that snapshot, then projects only the validated allowlist: collection name/idName/embedded/version/properties; property name/type/target; index name/properties/unique/hash. Optional missing fields remain missing and explicit null remains null; arrays preserve validated order. It does not invent IDs, defaults, fields or record contents.

All schema names in the new projection reject C0 control characters; existing name-length/type/duplicate/key/type/depth/count budgets remain unchanged. Legitimate schema property names such as `password` or `body` are allowed as **names only**; values/records/password members outside the schema allowlist are rejected. enumMap and unknown members remain unsupported rather than being silently removed. The projected immutable value is serialized and rejected if the entire envelope exceeds65536 bytes; returned data is reparsed immutable and independent from input memory. There is no callback retention, file/network/UI export or opaque Rust-object retention. Process the entry-time owned copy and release it before calling the consuming original operation.

This interface enables future, separately authorized canonical field-contract handling. It is not proof of a live manuscript schema, correct database ownership, record format, DocID mapping or transaction safety. It does not activate anything or integrate a hook. The task49 public protocol fixture is derived from the same fixed task45 source, not captured from a device/database. `SchemaProjectionTests.m` verifies canonical equality, nullable/missing semantics, all nested schema structures, legitimate password field names, immutable output, old counts redaction, C0/unknown/record/enumMap/UTF-8/duplicate/trailing/budget rejection. Ordinary and ASAN/UBSan runs are appended to `test-schema.sh`; the existing iOS SDK object compile includes the new interface.
