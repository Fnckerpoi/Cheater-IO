# NewsHub navigation verification host

This host compiles the real `ResearchUI.m`, `NewsReader.m`, `NewsCore.m`, `NewsPresentation.m`, `TIONewsHubPanel.m`, and the production `NewsHubNavigation.m` adapter. It does not compile `Addon.m`, the old official app, or a duplicate production `TIONewsReader` class. `TeleFixture.m` replaces only the hardware-facing transport and aborts if invoked. News fetching and HTTP attempts also abort. The bundle has isolated simulator preferences, with scheduling disabled before Hub initialization.

## Build modes

Use a fresh absolute output directory each time:

```sh
bash official-addon/news-ui-preview/build.sh /tmp/turboio-news-ui-red-NEW red
bash official-addon/news-ui-preview/build.sh /tmp/turboio-news-ui-green-NEW green
bash official-addon/news-ui-preview/build.sh /tmp/turboio-news-ui-reject-NEW reject
bash official-addon/news-ui-preview/build.sh /tmp/turboio-news-ui-partial-NEW partial-failure
```

- `red`: unchanged research news root; must report `NEWS_UI_RED` because no sibling module menu exists.
- `green`: same real roots plus production adapter, under the explicit validation bundle allowance. Must report `NEWS_UI_ALL_PASS: 31 assertions`.
- `reject`: compile the production bundle/image guard, not the validation allowance. Launch with `--reject-host`; must report `NEWS_UI_REJECTION_ALL_PASS` without modifying any roots or UIKit IMPs.
- `partial-failure`: inject failure only at the adapter's second class-local override registration. Launch with `--install-failure`; must report `NEWS_UI_REJECTION_ALL_PASS`, demonstrating rollback of the first class-local IMP and persistent fail-closed installation.

## Run on a dedicated simulator

Only create/use a dedicated validation simulator; do not install this host or the IPA on a phone. Example:

```sh
device=$(xcrun simctl create TurboIO-NewsUI-Validation-NEW \
  com.apple.CoreSimulator.SimDeviceType.iPhone-16e \
  com.apple.CoreSimulator.SimRuntime.iOS-26-3)
xcrun simctl boot "$device"
xcrun simctl bootstatus "$device" -b
xcrun simctl install "$device" /tmp/turboio-news-ui-green-NEW/NewsUIValidation.app
xcrun simctl launch --console "$device" io.turboio.news-ui-validation
```

`simctl launch --console` remains attached after assertions, and its command exit status alone is not a test verdict. Require the appropriate `ALL_PASS` log marker and absence of `NEWS_UI_RED`. After completion, terminate the host and shut down the dedicated simulator.

The green host selects the actual cells through the table delegate, checks destination identity, returns through the actual Hub close selector, and writes four rendered screenshots plus `assertions.txt` to its own `Documents/NewsUIEvidence`. Animations are disabled only in the host for deterministic screenshots. No fetch, source-edit, scheduling, or import controls are pressed.

## Verified scope

- Existing news root upgrades to two sibling cells: TinyFish and NewsHub.
- Both future tab setter paths and repeated installation remain idempotent.
- The original three sibling tabs retain their navigation instances.
- TinyFish retains the original reader object and real reader UI.
- NewsHub pushes the real Hub panel and closes back to the news root, not the official app.
- Generic `UITabBarController` and both UIKit setter IMPs remain unchanged.
- Wrong reader class and production host rejection fail closed.
- Partial registration failure restores the adapter's own plain setter IMP.

This proves simulator navigation behavior, not loading the arm64 IPA on a phone, fetching sources, or official Flutter/import continuation behavior. The final IPA must separately preserve the inspected old addon, contain only the new module, and match the production image guards.
