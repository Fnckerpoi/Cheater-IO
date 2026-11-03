# OfficialOtaBridge host integration test

Run from the repository root with a JDK and Android 36 platform jar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File android-addon/tests/otabridgehost/run.ps1 -JavaBin "C:/path/to/jdk/bin" -AndroidJar "C:/path/to/android-sdk/platforms/android-36/android.jar" -Output "android-addon/build/otabridgehost"
```

The runner compiles current addon Java sources and this fixture into a fresh class directory on every invocation. The classpath contains only that directory and the supplied Android jar. Inputs, compiler output and 55 assertion results are recorded as command.json, compile.log and test.log under Output. Compile and test exit codes propagate to the caller. There is no prior APK, baseline snapshot or cached-class dependency.

Assertions cover explicit readback and fresh matching replies opening ordinary messages while preserving pending protection and file exclusion. Disconnect, wrong peer, negative/malformed/late replies, backgrounding, active OTA, cancellation, service loss and restart deny or revoke access. OfficialOtaBridge, OfficialOtaGate, OfficialOtaBusinessAccess, OfficialOtaAttempt, NativeTransfer, NavReflect and OtaController use current product source. NativeTransfer has no fixture replacement.

## Test boundaries

- FixtureSdk and HostSymbols supply a synthetic vendor SDK. Actual NavReflect and Bridge allowQueue/observe process reflected SDK traffic.
- Handler/Looper scheduling and Log are inert; SystemClock is deterministic. Android 36 supplies other API signatures.
- OtaService records liveness and finish calls. InMemoryJournal supplies preferences. Setup injects initialized pending state, gate recovery history and an in-process lease.
- Remaining addon sources compile, but their behavior is outside these assertions. The test does not cover Android initialization, persistent restart, UI clicks, Bluetooth/JNI, vendor hook installation, Android service lifecycle, device behavior, firmware boot or APK installation. File gates and transfer exclusion are tested without actual file submission or spool I/O.
