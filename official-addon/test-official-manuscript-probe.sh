#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
temp_root="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
bin="$(mktemp "$temp_root/official-manuscript-probe-tests.XXXXXX")"
trap 'if [[ "$bin" = "$temp_root"/official-manuscript-probe-tests.* && -f "$bin" ]]; then rm -- "$bin"; fi' EXIT
xcrun clang -fobjc-arc -fmodules -Wall -Wextra -Werror -framework Foundation \
  "$root/OfficialManuscriptProbe.m" "$root/OfficialManuscriptProbeTests.m" -o "$bin"
"$bin"
sdk="$(xcrun --sdk iphoneos --show-sdk-path)"
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=16.0 \
  -fobjc-arc -fmodules -Wall -Wextra -Werror -fsyntax-only \
  "$root/OfficialManuscriptProbe.m" "$root/OfficialManuscriptProbeTests.m"
echo 'PASS arm64 collector/test syntax (not a linked host or IPA)'
