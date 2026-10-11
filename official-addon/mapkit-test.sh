#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/mapkit-tests
flags=(-fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter -framework Foundation -I "$PWD/mapkit-navigation")
xcrun clang "${flags[@]}" NavigationSession.m NavigationSessionTests.m -o build/mapkit-tests/session
./build/mapkit-tests/session
for enabled in 0 1; do
  xcrun clang "${flags[@]}" -DTIO_MAPKIT_ENABLED="$enabled" ResearchCatalog.m ResearchCatalogTests.m -o "build/mapkit-tests/catalog-$enabled"
  "./build/mapkit-tests/catalog-$enabled"
done
for core in MapKitNavigationCore MapKitRoadsCore; do
  xcrun clang "${flags[@]}" "mapkit-navigation/$core.m" "mapkit-navigation/${core}Tests.m" -o "build/mapkit-tests/$core"
  "./build/mapkit-tests/$core"
done
xcrun clang "${flags[@]}" -DTIO_MAPKIT_HUD_ENABLED=0 mapkit-navigation/MapKitHUD.m mapkit-navigation/MapKitHUDPolicy.m mapkit-navigation/MapKitHUDPolicyTests.m -o build/mapkit-tests/hud-policy
./build/mapkit-tests/hud-policy
xcrun clang "${flags[@]}" -I "$PWD/focus-edition" -I "$PWD" mapkit-navigation/MapKitHUDRuntimeTests.m mapkit-navigation/MapKitHUDPolicy.m \
  focus-edition/DisplayPhoneSession.m focus-edition/DisplayPhoneTransport.m focus-edition/DisplayReplyObserver.m focus-edition/DisplayDiagnostics.m \
  focus-edition/DisplayDelta.c focus-edition/display_client.c focus-edition/display_carrier.c focus-edition/display_runtime.c -o build/mapkit-tests/hud-runtime
./build/mapkit-tests/hud-runtime
node --test mapkit-build-config.test.mjs mapkit-packaging.test.mjs
