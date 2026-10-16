# Task97: hidden descriptor-only capture activation

This is a new reviewed scope after task93, not retroactive approval of task86/93 receipts. New internal ABI:

```c
__attribute__((visibility("hidden")))
bool TIOCaptureStartInternal(int trusted_dirfd);
```

It is declared only in `CaptureInternal.h`. No path, proof/token, trust bool, expiry, rearm, or user configuration is accepted. No constructor or public initializer/button/getter activates it. The unchanged one-time typed original binder must already have succeeded. Binding alone still only establishes typed forwarding; observing remains inactive until StartInternal succeeds.

Reporting clarification accepted by lead: successful bind yields enum `TIOCaptureWaiting` but reporting status `bind_waiting_start_blocked` (Active=false); successful Start yields `capture_active_waiting_first_open` (same WAITING enum, Active=true). WAITING alone is never evidence of activation. See `StartStatusSupplement.md` for the exact state/reporting table; this clarification does not amend frozen sources, receipts, or manifest hashes.

## Exact trust boundary

The future same-final-DSO runtime bootstrap **must** verify the actual Runner/container and traverse its own `Documents/ManuscriptLab` using descriptor-relative no-follow opens before supplying this descriptor. This module cannot recover or prove an fd's historical symlink traversal or container origin.

Darwin experimentally returns `F_GETFL == 0` for an O_RDONLY directory originally opened with O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC; these open-time flags cannot honestly be attested from F_GETFL. The gate instead validates FD_CLOEXEC, directory type, euid ownership, exact 0700 mode; obtains its own fd with `openat(trusted_dirfd, ".", O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC)`; then checks the reopened object's owner/mode/dev/inode against the input. A source-fd replacement race that resolves to a different inode is refused. This verifies the currently referenced object only. A qualified upstream traversal is a hard caller precondition, not an authority carried by the integer fd.

Start uses a process one-shot CAS and monotonic fixed 120-second budget, installs internally owned descriptor/context, and publishes Active only after all initialization. It returns WAITING, never WRITTEN/saved. The private schema observer fields retain the fixed task86 policy; they are internal adapter assumptions, not runtime identity verification. If the trusted caller cannot establish provenance it must not call this hidden function.

## Capture behavior

Production is inert when loaded: no activation, lookup, UI, file access or capture occurs just by linking/loading. The module now contains the schema-only copy/export implementation, but it is guarded by Active and no public grant exists. Existing typed original forwarding always occurs exactly once, including when inactive, key-rejected, terminal, or observation fails; incoming errno is restored before original, and original output/status/errno/exception propagate.

Activated nonnull keys perform no schema copy and produce no file. The first eligible null-key event consumes the one-shot budget. Fixed schema-only output name remains `captured_schema_projection.json`; creation is descriptor-relative 0600 O_EXCL/O_NOFOLLOW with a pending file, fsync and RENAME_EXCL atomic commit. Existing paths are never overwritten, no directories are chmodded, unsafe output directory mode/owner is checked again at export. No records, keys, private prefs or user files are sampled by the tests.

The old `TIO_CAPTURE_TESTING` fixture home/clock setup remains test-only for the unmodified task86 path/stat/schema regression matrix. Production Start accepts only the final already-qualified directory fd, not a Home path.

## Verified output and freeze

```sh
python3 official-addon/final-build/schema-capture/capture-core-tests/start-check.py \
  --out /private/tmp/<new-canonical-output>
```

Output `/private/tmp/task97-capture-start-r1`, total exit 0. Normal/ASAN/UBSAN each pass:

- Start before bind, invalid/regular fd, missing CLOEXEC, unsafe0755/0770 rejected.
- Upstream symlink opened O_DIRECTORY|O_NOFOLLOW fails; no historical-fd claim.
- Fstat fault injection verifies wrong euid and reopen inode mismatch rejected (these are syscall-boundary injected failures, not a claim of creating a real foreign-owned directory).
- Valid0700 Start transitions WAITING with no output before an Open; duplicate Start refused.
- Production-like no-testing core + exact fake typed original verifies all eleven arguments, f32 bits, out/status/errno and exact one invocation per call.
- Nonnull key causes no output; null-key public schema creates exact0600 output; subsequent calls cannot recopy or overwrite.
- Old schema policy, file/symlink/mode attack matrix, own-engine zero-copy keyed and one-shot tests, original exception propagation all pass unchanged.

Two independent production host dylibs default to BLOCKED. Real nm/dlsym expose only GetState, not bind/start/forward/test-grant. The production link explicitly uses `-Wl,-exported_symbol,_TIOCaptureGetState` so linked policy/memory helpers are not accidentally exported; future unified builder must preserve this export constraint. iOS arm64 production link passes the same export check without executing iOS runtime.

Frozen source-only artifact:

`capture-core-tests/build/frozen-task97/source-manifest.json`

SHA256 `9645043e00402fb0b2cc6919fe60fb58b6f767fb7046a9c6d65c421a67c01d1e`.

- Capture.c `6c18e2769b48159ae3c12357a5a9da3ab1608d680d62135173b4a5b613046e16`
- CaptureInternal.h `9a3b04204165df00a029e9a7ddf59d1f39f095a5eefd3ee147f37b949ecde974`
- Capture.h unchanged `70e4beb0e2b980c372ce6784b585b0a2d0a20cd114fa0b1ec220689646cae02f`

Frozen runner: `source/final-build/schema-capture/capture-core-tests/start-check.py` beneath that artifact, with all compile inputs included; can run from snapshot without live includes. Exact task93-to-task97 diff is `exact-task93-task97.diff` in artifact root. This supplemental README is not part of the frozen input manifest.

No runtime bridge or installer was added/modified. No actual container/Runner identity, real Isar entry, schema, official save, DB access, App/phone execution, signing, IPA or network activity is proven. A future adapter and unified module still require independent review; internal hidden activation is a trusted-caller boundary, not an external capability credential.
