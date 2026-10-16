#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
output=${1:?Use a new absolute output path}
phase=${2:-green}
[[ "$output" = /* && ! -e "$output" ]] || { echo 'Output must be a new absolute path' >&2; exit 2; }
mkdir -p "$output/NewsUIValidation.app"
python3 - "$output/NewsUIValidation.app/Info.plist" <<'PY'
import plistlib,sys
with open(sys.argv[1],'wb') as f:
 plistlib.dump({'CFBundleIdentifier':'io.turboio.news-ui-validation','CFBundleExecutable':'NewsUIValidation','CFBundleName':'News UI Validation','CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1.0','LSRequiresIPhoneOS':True,'UIDeviceFamily':[1,2],'UILaunchScreen':{}},f)
PY
sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
sources=(ResearchUI.m NewsCore.m NewsPresentation.m NewsReader.m news-ui-preview/TeleFixture.m news-ui-preview/Main.m)
flags=(-DTIO_NEWS_UI_VALIDATION=1)
if [[ "$phase" == reject ]]; then flags=(-DTIO_NEWS_UI_VALIDATION=0); fi
if [[ "$phase" == partial-failure ]]; then flags+=(-DTIO_NEWS_TEST_FAIL_ANIMATED=1); fi
if [[ "$phase" == red ]]; then flags+=(-DTIO_NEWS_RED=1); else
 sources+=(NewsHubNavigation.m AIHotDaily.m TIONewsSource.m TIONewsScheduler.m TIOImportQueue.m TIONewsHubPanel.m)
fi
xcrun --sdk iphonesimulator clang -target x86_64-apple-ios16.0-simulator -isysroot "$sdk" -I "$root" \
 -fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter -Wno-deprecated-declarations \
 -framework UIKit -framework Foundation -framework CoreGraphics "${flags[@]}" "${sources[@]/#/$root/}" \
 -o "$output/NewsUIValidation.app/NewsUIValidation"
codesign --force --sign - "$output/NewsUIValidation.app"
echo "$output/NewsUIValidation.app"
