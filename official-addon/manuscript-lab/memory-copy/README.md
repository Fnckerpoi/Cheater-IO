# Own-process bounded memory copy (task48)

## Contract

`TIOMemoryCopySelf` matches the `TIOSchemaBoundedCopy` callback signature without importing or editing the reader. Pass NULL context. It calls the SDK-supported `vm_read_overwrite(mach_task_self(), ...)` exactly once for one validated range, never a caller-supplied process/task port. Length is 1..65536, checked for address/destination overflow and output/source overlap. On complete success returns true and sets copied=length. On a failed or short OS read returns false, clears the entire admitted destination range (including any partial bytes), and leaves copied=0. Argument rejection resets copied where provided but does not touch destination. NULL copied is rejected.

The caller must own stable valid writable destination[length] and copied storage, mutually disjoint and not overlapping the source. This does not make an arbitrary destination pointer safe; output storage is the trusted caller precondition. Source addresses may be inaccessible/unmapped and are never directly dereferenced. Static assertions enforce pointer/size fit in the public VM API types. Non-NULL context is refused, not interpreted as a task port.

macOS supports mach_vm_read_overwrite, but the actual iPhoneOS26.2 SDK rejects mach/mach_vm.h as unsupported. The implementation therefore uses public vm_read_overwrite declared by mach/mach.h on both tested platforms; it does not manually declare a private API. Production arm64 object undefined imports are vm_read_overwrite, mach_task_self_, and compiler memset checking. No Dart VM API, engine, filesystem, logging, constructor, automatic read loop, networking or hook is present.

## Authorization boundary

This is only a copy mechanism, not proof that an address is schema. The reader/controller must first validate exact host and engine identity, the specific schema argument role, key=NULL, lab isolation, TTL and run-wide budgets, then supply the one known range. Do not read keys, name/path, records, other processes or arbitrary memory. Reconstructing an adapter cannot enforce a global budget; that belongs to the controller. No observer is installed or connected by these files.

Kernel-mediated access avoids a userspace vm_region-check then memcpy fault race, but does NOT promise an atomic or consistent snapshot across concurrent source mutation/unmapping. The trusted output storage must not concurrently disappear. No concurrent-race safety claim is made.

## Tests

Run `bash official-addon/manuscript-lab/memory-copy/test-memory-copy.sh` from any directory. Outputs go to a fresh /tmp/tio-memory-copy-check.* directory; the script does not remove or overwrite other tasks artifacts.

Real own-memory tests cover an 8-byte public field, a 64KiB malloc buffer, canaries, zero/over-limit/NULL/source and destination overflow rejection, non-NULL context and overlapping source/output rejection, cross-PROT_NONE boundary, wholly protected source and already unmapped source. mprotect/munmap touch only the test-owned mapping. No App/engine/DB/phone memory is read.

A separately linked fake VM syscall tests deterministic success-with-short-count, error-after-partial-write and oversized reported count, checking full cleanup/copied0/canaries. It does not fake public adapter behavior: the production implementation runs unchanged apart from compiler symbol substitution for the OS collaborator. This simulation is not evidence that the real kernel produced a short read. Production does not contain the substitute.

The script runs ordinary, AddressSanitizer and UndefinedBehaviorSanitizer tests, then compiles (does not run) the production file for arm64 iOS15 with the available iPhoneOS SDK and prints imports.

## PDCA / result

RED1 missing implementation failed link on TIOMemoryCopySelf. GREEN1 real 8-byte copy passed. RED2 the minimal implementation accepted zero length, causing the new boundary test to abort; guard and scrubbing implementation made real mapped/protected/unmapped tests pass. Deterministic partial-cleanup tests pass. Normal+ASAN+UBSAN and supported-API arm64 compilation pass. The first test-script run exposed Bash3 empty-array nounset handling and was fixed locally; the first iOS compile exposed the unsupported mach_vm header and led to the public vm_read_overwrite choice, not a private declaration.

No signing, packaging, target runtime, deployment, schema capture, database read/write or official-save success is asserted. Integration is a separate scope.
