# Typed passive Isar open forwarder — NOT installed, NOT device ready

Task47 is an independent module only. StageA code, its approved f663 native SHA/59e58 IPA, packagers and schema-observer sources are untouched. No real resolver slot/text is changed; no App/engine/database/phone is invoked. Fake-original host harness calls are not engine acceptance.

## Exact interface and bounded state

`TIOOpenFunction` matches generated 1.2.6 `isar_open_instance`: `uint8_t(I**, uint32_t, CString*, CString*, bool, CString*, uint32_t, CString*, uint32_t, uint32_t, float)`. CString is opaque: this module never reads/frees/mutates it. All eleven values, the original out-slot pointer, float32 bits, thread and original status are forwarded unchanged, exactly once per invocation.

- Bind original function and optional callback/clock once, before publishing any wrapper. There is one static process-lifetime state; no rebinding, cross-instance swap or unload/free API. A cached wrapper stays valid after disable and still forwards the immutable original. Calling an unpublished/unbound wrapper is an invariant violation and aborts rather than fabricating an Isar status.
- No constructor, autoarm or real resolver installation. Unverified handle/module/export/name ownership/resolver semantics return the injected resolver's result unchanged. Name comparison happens only after an integration caller asserts an already-owned readable NUL-terminated name. All integration trust flags default false. The API neither verifies module hashes nor proves VM rights, lazy-slot resolution or caller identity itself.
- The injected resolver is called exactly once. Own checks occur before it; no clock/callback/Objective-C/error API occurs after it. Its pointer and errno are retained on pass-through. **Actual `dlsym`/`dlerror` transparency is not runtime-proven**; `resolver_semantics_verified` must remain false for real integration until separately established. This module never calls `dlsym` or `dlerror`.
- Observation requires explicit single-use confirmation/arm, TTL 1–120 seconds, cap 1–16, monotonic injected milliseconds and a separately verified per-thread context. Default TLS context is false; setting it is an integration attestation, not proof of a Dart isolate. Future integration must scope and clear it for each independently verified boundary. A different thread does not inherit it. Expiry, backward clock, disable or observer failure is terminal; repeated arm cannot reset time/count. Recursion forwards the original but does not observe again.
- Nonnull encryption key (including unreadable sentinel address) means zero observer calls. The key pointer/pointee is never passed to the callback or inspected. Other opaque name/path/out pointers are not passed to the observer. Observation contains only the borrowed schema pointer, numeric ID and backend boolean for the synchronous pre-original callback; the callback must not read opaque Rust memory or retain that pointer. This module itself never reads schema or logs pointer/string/record values.
- Own callback false/NSException and own clock NSException stop observation but never suppress the original call. The `@catch` scopes cover only own observer/clock. Original invocation sits outside those catches; original NSException propagates, and `@finally` only restores TLS recursion depth. Native faults, Rust panic/abort or arbitrary corrupt pointers are NOT caught or made safe.
- Callback/clock/context storage must remain alive for the process lifetime. Clock must be monotonic, bounded, nonblocking and nonreentrant; it runs under the state mutex. Callback is outside the mutex, synchronous and bounded. Disable prevents new reservations; an already-reserved callback may finish while another thread disables. No asynchronous work is queued and no schema pointer is retained by the adapter.

`TIOOpenSlotPlan` is a pure predicate over caller-supplied observed/expected ownership and resolved/writable/aligned facts. Tests apply C atomics only to a local fake slot to demonstrate expected-value CAS refusal on ownership change. The module performs **no actual CAS/write/restore of a process resolver slot**, no `vm_protect`, no GOT patch, no text hook. Static slot facts are not runtime permission.

## Verification

```sh
bash official-addon/manuscript-lab/open-observer/check.sh /absolute/new/check-output
```

This builds and executes real fake-original Objective-C harnesses normally and under ASAN+UBSAN, then builds the module for arm64 iOS15 using the real SDK. It does not execute the iOS dylib. Leak checking is disabled for system Objective-C runtime noise; address/undefined behavior checks remain enabled. The only linker warning is the existing SDK deprecation of `-undefined error`, still strict unresolved-symbol handling.

Exact-call tests cover 11 arguments, out-slot identity, byte-identical float -0/NaN payload/subnormal/1/-infinity, status and same-thread execution. State scenarios cover disabled/untrusted default, every resolver trust flag, unknown symbol/export/null result, key nonnull, context false/different thread, recursive callback, callback false/NSException, original NSException propagation, clock exception/backward/expiry, cap16, no rearm, cached forwarder after disable and abstract slot ownership.

## Provenance and remaining blocks

ABI evidence: `task36-bindings.dart:634–650` in the pre-existing `db-engine-static-105-l783jv66` scratch; engine 1.2.6 source/ownership evidence is the task40 `isar126-ownership-source-t93_fgve/ownership-report.md`. These establish typed arguments, not runtime safety. The wrapper calls only its injected original; it never actively opens an engine, starts a transaction, copies/closes a DB, calls Dart API/isolate functions, reads errors or installs an observer.

Runtime host/module/real-export identity, original resolver/lazy-slot ownership, signing-dependent identities, VM rights, valid context and actual public schema role remain separate integration gates. This module has no schema reader integration, actual hook or ready collection edition. It must not be folded into StageA by relaxing the approved f663 hash. No per-item automated import or saved DocID is claimed.
