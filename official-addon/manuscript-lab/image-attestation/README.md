# Fixed-image identity attestation (task72)

## Delivered capability

The production API does not accept caller-provided identity booleans or a mutable hash manifest. It selects one of two source-compiled immutable policies. `TIOImageAttestLoaded` independently enumerates current dyld images to bind the nominated header to the exact canonical `.app/Frameworks/...` path and actual slide, rejecting duplicate matching basenames. The copy callback is a trusted transport, not a source of policy. Header/load-command bounds, ARM64/subtype, unique UUID and text section, segment containment/overlap, exact text VA/size and streaming SHA256 are verified. Isar additionally verifies the first64 entry bytes at unslid0x1e530, then derives its loaded entry address. Rechecks header/load-command bytes before success.

`TIOImageAttestStatic` verifies the same content from explicitly supplied static member bytes, but never grants loaded proof or a writable slot address. Static bytes must remain valid for the call. It is an artifact-check seam, not permission to scan live processes.

Per explicit caller budget: at most3 image attempts and64MiB reads, each callback read <=64KiB, bounded commands/segments/sections/table counts. Budget must be serialized by caller and not reset to bypass a controller run limit. No constructor, automatic capture, image loading, hooks, networking, database, Dart VM API or writes are implemented.

## Flutter slot metadata

The decoder checks indirect pointer sections, LC_SYMTAB/LC_DYSYMTAB, nlist undefined/external library ordinal1, exact `_dlsym` name and exactly one matching slot. Symbol metadata reads are confined to declared `__LINKEDIT`, not GOT contents. It requires slot VA0x8341d8, ordinary `__DATA,__la_symbol_ptr`, type7, 8-byte alignment and static R/W segment permissions; rejects authenticated segment/chained-fixup layouts. Only a checked slide+slot address is produced.

`slot_runtime_writable_verified` is always false. No actual slot value, protection, lazy helper state, resolver pointer, mprotect/CAS or other GOT content is read or changed. Static segment permissions are not current VM protection. Loader lookup/caller/handle qualification and first-use timing remain separate.

## Source policy generation

`generate-policy.py exact-public.ipa absent-output-directory` refuses a wrong whole IPA or member SHA before producing output. It parses only Flutter and Isar members, never App AOT or user files. `generated/ImagePolicy.inc` and `generated/policy-provenance.json` derive from exact original1.0.5 input SHA39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a. This provenance output requires independent review; it is not automatic registration or approval.

- Flutter whole488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0; UUID4C4C44A2-5555-3144-A15F-431DD0AC6446; textVA0x4000 size6742540 SHAd056be38c4f5c0bf5fefb043632a1c4409a21681b695a17d521281f7e4854e46.
- Isar whole728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88; UUID9254BAA9-4958-36D7-9490-6BD970AA62B3; textVA0x4000 size1923516 SHAcaa1d02ca9d9a8d94b0ec3ef4db3f0aaddca685a93fb5cef6c7caaa63fc0c740; entry64 SHA391be5094ac5264dc65445fd42e79531fd4bea8463752db6f5366ac81f2c2bfe.

Runtime hashing excludes signature blobs and dyld-mutable DATA. A re-signing/thinning pipeline must still demonstrate these immutable fields are preserved; original whole-file hashes are provenance, not universal signed-file runtime hashes. Runner/bundle/container identity is not verified by this module; this module cannot alone authorize schema capture or a final IPA.

## Test execution

Run `bash official-addon/manuscript-lab/image-attestation/test-images.sh`. Creates only fresh own /tmp fixtures, hashes the exact public IPA and extracts only the two admitted member files. It **never loads or executes the official iOS libraries**. Its only dlopen is an explicitly compiled own public host dylib under a new canonical Fixture.app. Normal/ASAN/UBSAN tests verify real dyld header/slide/path and safe own-memory copying. Separate synthetic ARM64 views cover modified TEXT, wrong UUID, duplicate UUID commands, duplicate nominated image count, malformed command length, segment overlap, noncanonical relative path, invalid slide, short read and read budget rejection. Production policies reject these test identities.

Real fixed Flutter and Isar static members pass the production checker; Flutter slot metadata decoder also passes. iPhoneOS26.2 arm64/iOS15 production object compiles with Werror and has no exported TIOImageTest functions. CommonCrypto uses system APIs; no external dependency installed.

Initial RED was missing public functions; minimal failclosed implementation passed rejection. Positive tests were then added. The initial real host dyld test rejected /tmp because canonical path is /private/tmp; the test was corrected to canonicalize its fixture directory rather than weakening validation. Final normal/ASAN/UBSAN/static-public/arm64 checks pass.

## Explicit remaining limits / integration contract

`TIOImageProof` is a result value, **not an unforgeable bearer credential**. A future installer must call attestation itself or use a separately reviewed opaque issuer; it must not accept a struct with caller-set true flags. No opaque credential registry, resolver eligibility, process-lifetime reservation, unload lock, current VM-region inspection or anti-TOCTOU guarantee is delivered here. Same-basename duplicate loaded images and repeated UUID commands are rejected, but arbitrary differently named images sharing the same UUID are not exhaustively inspected. Dyld image-count recheck detects count changes, not arbitrary unload/reload with unchanged count. Header/text hashing is an observation at the call, not a permanent guarantee after return.

The test-only custom policy APIs are compiled solely under TIO_IMAGE_ATTESTATION_TESTING; they cannot grant production schema/runtime qualification and do not appear in the production object. Own host testing does not certify ARM64 device runtime behavior. Trusted transport/output storage and no concurrent image unload are caller preconditions. All unsupported/failed checks return non-OK and clear proof fields; there is no fallback-true identity path.

This is a useful implemented decoder/text verifier, not a deployed observer, GOT installer, DB importer, scheduler, signature approval or official-save proof. Additional gates require actual lab consent/container identity, handle/caller policy, runtime slot state and lifetime, and independent review of this policy and implementation.
