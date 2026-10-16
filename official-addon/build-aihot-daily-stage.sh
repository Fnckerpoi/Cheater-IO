#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
output=${1:?Usage: bash build-aihot-daily-stage.sh /absolute/new-output-directory}
[[ "$output" = /* && ! -e "$output" ]] || { echo 'Use a new absolute output directory; no overwrites' >&2; exit 2; }
for source in AIHotDaily.m AIHotDailyStageCore.m AIHotDailyStageUI.m; do
  [[ -f "$root/$source" ]] || { echo "Source not ready: $source" >&2; exit 2; }
done
sdk="$(xcrun --sdk iphoneos --show-sdk-path)"
mkdir -m 700 "$output"
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=15.0 \
  -fobjc-arc -fmodules -dynamiclib -Wall -Wextra -Werror -Wno-unused-parameter \
  '-DTIO_AIHOT_STAGE_BUNDLE_ID="com.duriea.turboio.research"' \
  -framework Foundation -framework UIKit -Wl,-undefined,error -Wl,-no_fixup_chains -Wl,-no_adhoc_codesign \
  -install_name @rpath/TurboIOAIHotDailyStage.dylib \
  "$root/AIHotDaily.m" "$root/AIHotDailyStageCore.m" "$root/AIHotDailyStageUI.m" \
  -o "$output/TurboIOAIHotDailyStage.dylib"
file "$output/TurboIOAIHotDailyStage.dylib"
xcrun nm -u "$output/TurboIOAIHotDailyStage.dylib" > "$output/undefined-system-symbols.txt"
otool -L "$output/TurboIOAIHotDailyStage.dylib"
echo 'Linked stage dylib only; not signed or installed'
