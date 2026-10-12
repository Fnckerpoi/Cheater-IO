#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -Wno-unused-parameter -framework Foundation A2UIProtocol.m NavigationCore.m SubtitleHUDCore.m NavigationSubtitleHUD.m NavigationSubtitleHUDTests.m -o build/navigation-subtitle-live-tests
./build/navigation-subtitle-live-tests
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -Wno-unused-parameter -framework Foundation A2UIProtocol.m NavigationCore.m NavigationSubtitleHUD.m NavigationSubtitleEntryTests.m -o build/navigation-subtitle-entry-tests
./build/navigation-subtitle-entry-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationRouteSession.m SubtitleHUDCore.m SubtitleHUDLifecycleTests.m -o build/subtitle-lifecycle-tests
./build/subtitle-lifecycle-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationRouteSession.m NavigationRouteSessionTests.m -o build/navigation-route-session-tests
./build/navigation-route-session-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationAutoDisplayTests.m -o build/navigation-auto-display-tests
./build/navigation-auto-display-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation SubtitleHUDCore.m SubtitleRestartTests.m -o build/subtitle-restart-tests
./build/subtitle-restart-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationMapEntryTests.m -o build/navigation-map-entry-tests
./build/navigation-map-entry-tests
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -framework Foundation A2UIProtocol.m NavigationCore.m NavigationMapLocationTests.m -o build/navigation-map-location-tests
./build/navigation-map-location-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationAutoPlanTests.m -o build/navigation-auto-plan-tests
./build/navigation-auto-plan-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationStopPresentationTests.m -o build/navigation-stop-presentation-tests
./build/navigation-stop-presentation-tests
xcrun clang -fobjc-arc -fmodules -framework Foundation NavigationRouteSession.m SubtitleHUDCore.m SubtitleHUDSendTests.m -o build/subtitle-native-send-tests
./build/subtitle-native-send-tests
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -framework Foundation A2UIProtocol.m NavigationCore.m NavigationRouteSession.m SubtitleHUDCore.m NavigationSubtitleHUD.m SubtitleStartupIntegrationTests.m -o build/subtitle-startup-integration-tests
./build/subtitle-startup-integration-tests
xcrun clang -fobjc-arc -fmodules -Wno-incompatible-pointer-types -framework Foundation A2UIProtocol.m NavigationCore.m NavigationSubtitleHUD.m NavigationBeginRouteTests.m -o build/navigation-begin-route-tests
./build/navigation-begin-route-tests
