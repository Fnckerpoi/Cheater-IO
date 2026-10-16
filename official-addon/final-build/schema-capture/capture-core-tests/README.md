# Task93: hidden one-time typed-original binding seam

## Interface and limits

`CaptureInternal.h` alone declares:

```c
__attribute__((visibility("hidden")))
bool TIOCaptureBindOriginalInternal(TIOCaptureOpen original);
```

It is a same-final-dylib link seam, **not** a dynamically exported API, installer, external initializer, proof-token consumer, or capability grant. It takes only the existing exact eleven-argument typed function pointer; no home/path/proof/token fields are accepted. The final runtime adapter must independently verify that this pointer is the qualified loaded Isar entry and equals the exact `RTLD_DEFAULT` (-2) lookup-name result before calling. The seam does not itself establish either fact.

NULL fails. Atomic compare/exchange reserves Original once; every rebind including the same pointer fails. Original is retained for process lifetime. Only after the original pointer is stored does state transition to WAITING. This does not mark schema observed, written, or saved. Production has no test fixture grant, schema observe, or file exporter enabled by this change.

Default is still statically `BLOCKED_EARLY_RUNTIME`. The old constructor that only reset the same initial state was removed to avoid clobbering a binding performed earlier by a future same-image constructor; no replacement constructor or runtime discovery was added. Forward-before-bind retains its previous fail-fast behavior. After bind, the existing exact ABI/f32/out/status/errno/original-exactly-once forwarding remains unchanged. Native exceptions still propagate unchanged.

`Capture.h`, schema policy, exporter, source schema, resolver, adapter, task86 frozen source/build receipts, and all runtime/package files were not modified. The existing TESTING-only `TIOCaptureFixtureBind` retains its own public-fixture home/clock setup and now ends by calling this exact hidden CAS seam. Test-only fixture identity is not a production grant.

## Verification

```sh
python3 official-addon/final-build/schema-capture/capture-core-tests/check.py \
  --out /private/tmp/<new-canonical-output>
```

The runner freezes every compile source/dependency first and compiles only that private snapshot. Initial test RED was missing `CaptureInternal.h` (exit 1), log `/private/tmp/task93-bind-red.log`; after implementation the same typed bind/forward test passes.

Verified output `/private/tmp/task93-capture-bind-r1`, overall exit **0**:

- Normal, AddressSanitizer, UndefinedBehaviorSanitizer: NULL/rebind refusal, typed forwarding, original once, exact float negative-zero bits, output/status/errno contracts.
- Same three modes: existing unmodified schema-policy tests and AttackMatrix file/symlink/mode tests.
- Same three modes: own freshly linked fake engine original, nonnull same key twice with **zero copies**, eligible null-key one shot with three bounded copies, subsequent calls forward without another copy.
- Same three modes: original Objective-C exception propagates, original called exactly once.
- Two independently linked production dylibs: pure state getter returns blocked; `nm -gU` and real `dlsym` cannot expose internal bind, forward, or test-only fixture grant. The sole dynamic export is `_TIOCaptureGetState`; imports contain no resolver/ObjC/file operations.
- iOS arm64 production dylib link: same hidden-export restrictions pass. No iOS runtime executed.

Sources:

- Capture.h unchanged SHA256 `70e4beb0e2b980c372ce6784b585b0a2d0a20cd114fa0b1ec220689646cae02f`
- Capture.c SHA256 `b84328e818f5071f9673d8c2cb7d9e50cfa36f4e702ef59b0c6dd8729178feac`
- CaptureInternal.h SHA256 `7835adf59552c690435cb39a071c2f5c25278de50199b3f3f3065cd1ff146293`
- New frozen source manifest SHA256 `8a83ef385f4e376c27007d2756ae46f8a2c9d450ecd72494d4e636eb47f187f8`

The frozen runner is `source/final-build/schema-capture/capture-core-tests/check.py` below that output root and can produce a new independent output without live source dependencies. `exact-core.diff` records the minimal comparison with the old unmodified task86 source snapshot. This README is supplemental and outside the frozen test manifest.

The previous task86 build script/receipts are deliberately unchanged; the new task93 runner explicitly includes the new internal header in its snapshot. Do not treat an old build receipt as review approval for this source revision.

## Not proven

No runtime selector/slot lookup or mutation, loaded real Isar identity, capture bridge installation, production capture capability, actual schema knowledge, official save, IPA, App/DB/phone access, signing, user data reads, or network operations were performed or proven. A future unified adapter must consume this seam under a new review. The production build remains blocked by default and exports no ability to activate it externally.
