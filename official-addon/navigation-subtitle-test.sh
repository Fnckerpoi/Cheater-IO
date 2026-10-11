#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -Wno-unused-parameter -framework Foundation A2UIProtocol.m NavigationCore.m SubtitleHUDCore.m NavigationSubtitleHUD.m NavigationSubtitleHUDTests.m -o build/navigation-subtitle-live-tests
./build/navigation-subtitle-live-tests
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -Wno-unused-parameter -framework Foundation A2UIProtocol.m NavigationCore.m NavigationSubtitleHUD.m NavigationSubtitleEntryTests.m -o build/navigation-subtitle-entry-tests
./build/navigation-subtitle-entry-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation SubtitleHUDCore.m SubtitleHUDLifecycleTests.m -o build/subtitle-lifecycle-tests
./build/subtitle-lifecycle-tests
