#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
scratch="$(mktemp -d /tmp/tio-queue-lifecycle.XXXXXX)"
cleanup() {
    if [[ "$scratch" == /tmp/tio-queue-lifecycle.* && -d "$scratch" && ! -L "$scratch" ]]; then
        rm -rf -- "$scratch"
    fi
}
trap cleanup EXIT
mkdir "$scratch/temp" "$scratch/target"
ln -s "$scratch/target" "$scratch/root-link"
python3 - "$root" "$scratch" <<'PY'
from pathlib import Path
import sys
root, scratch = map(Path, sys.argv[1:])
source = (root / 'TIONewsSource.m').read_text()
(scratch / 'ArticleModel.m').write_text(source[:source.index('@implementation TIONewsSourceConfig')])
PY
flags=(-fobjc-arc -fblocks -fmodules -Wall -Wextra -Werror -Wno-deprecated-declarations -framework Foundation -I "$root")
xcrun clang "${flags[@]}" "$root/TIOImportQueue.m" "$scratch/ArticleModel.m" "$root/QueueLifecycleTests.m" -o "$scratch/production-tests"
TMPDIR="$scratch/temp/" "$scratch/production-tests"
xcrun clang "${flags[@]}" -DTIO_IMPORT_QUEUE_LEGACY_TESTING=1 "$root/TIOImportQueue.m" "$scratch/ArticleModel.m" "$root/QueueLifecycleTests.m" -o "$scratch/legacy-tests"
TMPDIR="$scratch/temp/" "$scratch/legacy-tests"
TMPDIR="$scratch/root-link/" "$scratch/legacy-tests" --symlink-root
sdk="$(xcrun --sdk iphoneos --show-sdk-path)"
for mode in 0 1; do
    xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=16.0 -fobjc-arc -fblocks -fmodules -Wall -Wextra -Werror -DTIO_IMPORT_QUEUE_LEGACY_TESTING="$mode" -fsyntax-only -I "$root" "$root/TIOImportQueue.m" "$root/QueueLifecycleTests.m"
done
echo 'PASS arm64 queue/test syntax in production and test-only modes; no IPA built'
