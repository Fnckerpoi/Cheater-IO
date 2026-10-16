#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
temp_root="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
bin="$(mktemp "$temp_root/aihot-daily-tests.XXXXXX")"
# The temporary target is an existing absolute file created above, not a repository path.
trap 'if [[ "$bin" = "$temp_root"/aihot-daily-tests.* && -f "$bin" ]]; then rm -- "$bin"; fi' EXIT
xcrun clang -fobjc-arc -fmodules -Wall -Wextra -Werror -framework Foundation \
  "$root/AIHotDaily.m" "$root/AIHotDailyTests.m" -o "$bin"
"$bin" "$@"
