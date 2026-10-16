# NewsHub exact navigation identity configuration — task65

This module changes only the navigation identity seam. It does not create a new Addon, change HTTP policy, package/sign/install anything, grant a runtime capability, inspect user configuration or claim device isolation.

## Interface

`NewsHubIdentityPolicy.h` is a pure header-only module with two exact-match functions:

- `TIONewsHubBundleMatches(observedBundle)`
- `TIONewsHubAddonMatches(observedImageBasename, observedUUID)`

Without configuration, the historical production pair remains exactly `com.duriea.turboio.research` and `3F635A04-D0C3-3196-919E-D4B73D7B0945`. Existing `HostCompatibility.h` is unmodified and remains a mandatory, separate observed Runner version/build/UUID check. No version wildcard was added and 1.0.6 is not implicitly accepted.

A future independently frozen build may supply **both** literal Clang definitions:

```text
TIO_NEWS_TARGET_BUNDLE_ID="com.duriea.turboio.manuscriptlab105.<run>"
TIO_NEWS_EXPECTED_ADDON_UUID="<actual-new-approved-uppercase-Mach-O-UUID>"
```

The UUID must come from the separately built/reviewed Addon, not an invented value. Literal values must be recorded in the build manifest. Build tools must pass compiler argument arrays (not interpolate a shell command), validate their manifest inputs and forbid test-mode flags in production. No adjacent marker or mutable preference can alter this module's expected identity.

Apple Clang C11/Objective-C compile-time assertions reject one-sided configuration, official/unknown/wildcard bundles, malformed/run IDs outside lowercase ASCII `[a-z0-9][a-z0-9-]{5,39}`, reserved `official`/`research` runs, embedded NUL/nonliteral length and malformed UUIDs. UUID spelling is exactly 36 uppercase hex characters with canonical dashes. The header uses Apple Clang constant folding for literal-character static assertions; it is not advertised as a portable arbitrary-C-compiler library. Explicit historical research configuration is allowed but remains exact, not an arbitrary research-bundle pattern.

## Navigation Adapter

`NewsHubNavigation.m` now calls the pure functions instead of two historical identity literals. It still obtains Addon UUID **from each real class's loaded Mach-O LC_UUID**, preserves exact image filename/path matching, checks both `TIOResearchTabs` and `TIONewsReader`, and calls the unchanged actual Runner image compatibility check. There is no NSBundle swizzle, path alias, broad class acceptance or user-controlled truth flag.

The existing `TIO_NEWS_UI_VALIDATION=1` branch remains limited to the existing isolated UI fixture bundle; production builders must not define it (or explicitly use 0). That test branch is not evidence for production bundle/image trust. This task compiles that branch but does not run UIKit.

## Real RED → GREEN and tests

`LiteralBaselineTests.c` intentionally reproduces the previous two literal comparisons. Its old research tuple passes and the new explicit lab/new UUID tuple fails (`assert` exit134). This historical RED fixture is not part of the passing suite.

```sh
python3 official-addon/newshub-identity-tests/check.py --out /absolute/new/check-directory
```

The passing suite compiles and executes default, explicit-lab and explicit-research pure policies; rejects invalid compiler configurations; and executes the original HostCompatibility implementation against actual synthetic Mach-O headers for supported 1.0.2/1.0.4/1.0.5 UUIDs and wrong UUID/version/build/null negatives. It then compiles **only NewsHubNavigation.m** for iOS arm64/default, explicit lab and UI validation modes using the installed SDK. It never compiles in-flight scheduler/source/queue modules or executes UIKit/iOS code. Test UUID `11111111-2222-4333-8444-555555555555` is a synthetic literal, not a claimed built Addon identity.

## Still blocked / unchanged

- This is exact identity matching, not a runtime capture grant, verified container isolation, official persistence or device readiness.
- Main `build.sh` still restricts HTTP opt-in to embedded research bundle and still codesigns its own output. Those scripts and `Addon.m` were not modified. A later dedicated no-sign test builder must explicitly address its permitted identity and original `TIO_TARGET_BUNDLE_ID`, preserving HTTP/TinyFish functionality without copying daily credentials.
- `build-newshub.sh`, packagers and StageA f663 policy were not changed or relaxed; no new whole-module approval was registered.
- Actual UI navigation, loaded-image identities for a future new Addon, post-sign code identity and hardware behavior require separate integration acceptance.
- Resolver process-handle/caller/first-lookup evidence is unrelated and remains blocked where unverified. Compiling this identity module does not fix resolver or DB contracts.
