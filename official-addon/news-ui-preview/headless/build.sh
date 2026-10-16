#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
output=${1:?Use a new absolute directory}
[[ "$output" = /* && ! -e "$output" ]] || exit 2
mkdir -p "$output/HeadlessUI.app"
python3 - "$output/HeadlessUI.app/Info.plist" <<'PY'
import plistlib,sys
with open(sys.argv[1],'wb') as f:plistlib.dump({'CFBundleIdentifier':'io.turboio.news-headless-ui-validation','CFBundleExecutable':'HeadlessUI','CFBundleName':'Headless UI','CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1.0','LSRequiresIPhoneOS':True,'UIDeviceFamily':[1],'UILaunchScreen':{}},f)
PY
sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
xcrun --sdk iphonesimulator clang -target x86_64-apple-ios16.0-simulator -isysroot "$sdk" -I "$root" -fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter \
 -framework UIKit -framework Foundation -framework CoreGraphics "$root"/{AIHotDaily.m,TIONewsSource.m,TIONewsHubPanel.m,news-ui-preview/headless/Fixtures.m,news-ui-preview/headless/Main.m} -o "$output/HeadlessUI.app/HeadlessUI"
codesign --force --sign - "$output/HeadlessUI.app"
