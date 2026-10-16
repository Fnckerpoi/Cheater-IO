# Scheduler / save-result contract

Run `bash official-addon/newshub-tests/run.sh`. This compiles the real scheduler and source model, injects public synthetic article fixtures and a test-only fake bridge, and uses randomly named `NSUserDefaults` suites. It does not instantiate the production singleton or read real application preferences. No bridge in this test is evidence of a working iOS save implementation.

## Runtime seam

`initWithDefaults:clock:fetch:bridge:` isolates time, generation and official persistence. Production `sharedScheduler` retains the real fetcher but has **no save bridge**: generated jobs enter `WAITING_RUNTIME_CONTEXT`, and official saved count stays zero. `saveBridge` is for an independently verified runtime adapter, not a placeholder success callback. Existing callers keep the same source configuration, generation, timer and delegate APIs. Invoke scheduler operations on the main queue; asynchronous fetch/bridge results are serialized onto that queue.

A job is keyed by source ID and the actual calendar day in the configured IANA timezone; its idempotency key also includes SHA-256 of the exact UTF-8 body. Duplicate source IDs are collapsed. Force checks cannot change a pending or confirmed job's payload, bypass ten-minute failure backoff, or resubmit an unknown save. Generation success and `completed.date` still mean only that enabled source articles were generated, not officially saved.

Before submitting a save, the scheduler writes `UNKNOWN_NEEDS_RECONCILIATION` and the attempt time to its defaults journal and requires synchronization to succeed. A request without an authoritative result, a missing callback, stale/mismatched readback, and runtime-unavailable replies **after submission** stay unknown. The next eligible action is reconciliation, never blind save. A lost callback can be reconciled after ten minutes, including after restart. An older unknown job blocks a new-day job for the same source until reconciliation resolves it.

Only a nonblank official ID, exact body readback, and verification timestamp at or after the attempted request and no more than five seconds in the future produce `SAVED_CONFIRMED`. The adapter is responsible for obtaining these from the real official reader, not fabricating them or inferring them from a native callback, import count or local file. `TIONewsSaveDefinitelyNotWritten` must mean an authoritative proof that no write occurred; ordinary timeouts/errors cannot use that disposition. Only this result permits another save after 600 seconds with the same idempotency key.

Schedule calculations use `NSCalendar`, including next-valid-time spring DST handling and same-day deduplication through repeated fall hours. Retry timestamps are wall-clock epochs, not process uptime. A timer checks while the app is active; a foreground caller can use `checkScheduledAtWake`. iOS suspension is not an on-time execution guarantee. This task does not auto-start the entry or connect the official runtime bridge.

## Evidence and limitations

The initial RED executable exited 42 because the existing scheduler lacked the isolated result/reconciliation seam. A second behavioral RED reproduced next-day bypass of an older unknown job; that regression is now green. The suite covers absent context, pending-before-send persistence, same-suite scheduler reconstruction, same-process timeout, stale callback fencing, readback evidence, exact 600-second retry, independent source failure, duplicate source IDs, DST, UTF-8 size bounds and local persistence failure.

The journal deliberately retains unresolved and confirmed records; retention/migration policy is not added here. RSS/MCP implementations, NewsHub Search integration, Queue, UI, actual Dart context acquisition, real official save/readback, and final IPA assembly are outside this change. Until a verified runtime adapter is attached, production waits rather than claiming saved success.
