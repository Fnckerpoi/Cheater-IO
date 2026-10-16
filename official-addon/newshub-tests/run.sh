#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
output=$(mktemp -d /tmp/turboio-news-scheduler.XXXXXX)
xcrun clang -fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter -framework Foundation \
 "$root/AIHotDaily.m" "$root/TIONewsSource.m" "$root/TIONewsScheduler.m" "$root/newshub-tests/TIONewsSchedulerTests.m" \
 -o "$output/scheduler-tests"
"$output/scheduler-tests" | tee "$output/scheduler-tests.log"
sdk=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$sdk" \
 -fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter -fsyntax-only \
 "$root/TIONewsScheduler.m" "$root/TIONewsSource.m"
echo "PASS arm64 iOS15 SDK syntax; no IPA built; evidence=$output"
