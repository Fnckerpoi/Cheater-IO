# OTA test inputs

The 1.0.5.12 official ZIP and repaired TMG3 ZIP are assets of Release `strix-1.0.5.12-tmg3-20261103`. The SHA-256 identities are listed in the release notes and `OtaProfile.java`. Keep the archives outside the source tree and pass their paths to package tests.

`RepairedTmg3OtaTest` takes three arguments: repaired TMG3 ZIP, official 127 ZIP, and the historical rejected TMG3 candidate ZIP. `Stock127OtaTest` takes the official ZIP and that historical candidate. The third/second archive is a negative identity fixture from the earlier failed attempt; it is not included in this Release and must never be used as the current firmware. Tests that require it need the separately retained research fixture.

`OfficialOtaBusinessAccessTest`, `OfficialOtaAttemptTest`, `OfficialOtaAuthorizationTest`, `OfficialOtaPreflightPollTest` and `OtaRecoveryGuardTest` are plain-JVM policy tests. Compile them with their product dependencies into a temporary class directory.

The portable actual-Bridge host fixture is in `otabridgehost/`; its README documents the Android/SDK boundary doubles and the JDK/Android SDK arguments. It compiles current sources, without any previous APK build classes.

APK assembly tests that require a local original host APK skip the relevant fixture-dependent cases when the input is absent. Original host APKs, private signing material and build outputs are not part of the source commit.
