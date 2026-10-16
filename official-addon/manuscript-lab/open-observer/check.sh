#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
out=${1:?Usage: check.sh /absolute/new-check-directory}
[[ "$out" = /* && ! -e "$out" ]] || { echo 'New absolute check directory required' >&2; exit 2; }
mkdir -m 700 "$out"
for test in ForwarderTests StateTests; do
 clang -fobjc-arc -Wall -Wextra -Werror -framework Foundation "$root/OpenObserver.m" "$root/$test.m" -o "$out/$test"
 clang -fobjc-arc -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer -framework Foundation "$root/OpenObserver.m" "$root/$test.m" -o "$out/$test-sanitized"
done
"$out/ForwarderTests"
ASAN_OPTIONS=detect_leaks=0 "$out/ForwarderTests-sanitized"
for mode in expiry exceptions budget resolver callbackfailure clockfailure clockback thread context slotplan; do
 "$out/StateTests" "$mode"
 ASAN_OPTIONS=detect_leaks=0 "$out/StateTests-sanitized" "$mode"
done
sdk=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=15.0 -fobjc-arc -fmodules -dynamiclib -Wall -Wextra -Werror \
 -framework Foundation -Wl,-undefined,error -Wl,-no_fixup_chains -Wl,-no_adhoc_codesign \
 -install_name @rpath/TurboIOPassiveOpenObserver.dylib "$root/OpenObserver.m" -o "$out/TurboIOPassiveOpenObserver.dylib"
otool -L "$out/TurboIOPassiveOpenObserver.dylib"
