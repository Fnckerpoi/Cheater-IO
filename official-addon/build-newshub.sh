#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
output=${1:?Usage: bash build-newshub.sh /absolute/new-output-directory}
[[ "$output" = /* && ! -e "$output" ]] || { echo 'Use a new absolute output directory; never overwrite' >&2; exit 2; }

sources=(
  AIHotDaily.m
  TIONewsSource.m
  TIONewsScheduler.m
  TIOImportQueue.m
  TIONewsHubPanel.m
  TIONewsHubEntry.m
  NewsHubNavigation.m
)
for source in "${sources[@]}"; do
  [[ -f "$root/$source" ]] || { echo "Source not ready: $source" >&2; exit 2; }
done
sdk="$(xcrun --sdk iphoneos --show-sdk-path)"
mkdir -m 700 "$output"
python3 - "$root" "$output" "${sources[@]}" <<'PY'
import hashlib
import json
from pathlib import Path
import sys
root, output = map(Path, sys.argv[1:3])
snapshot = output / 'source-snapshot'
snapshot.mkdir(mode=0o700)
support = {'build-newshub.sh', 'package-newshub.py', 'newshub-package-tests.py', 'package-aihot-daily-stage.py'}
names = set(sys.argv[3:]) | {p.name for p in root.glob('*.h')} | {p.name for p in root.glob('*Tests.m')} | support
records = []
for name in sorted(names):
    data = (root / name).read_bytes()
    (snapshot / name).write_bytes(data)
    records.append(dict(path=name, sha256=hashlib.sha256(data).hexdigest(), compiled=name in sys.argv[3:]))
for record in records:
    if hashlib.sha256((root / record['path']).read_bytes()).hexdigest() != record['sha256']:
        raise SystemExit('Source changed during freeze: ' + record['path'])
(output / 'newshub-source-manifest.json').write_text(json.dumps(dict(sources=records, compiled_sources=sys.argv[3:]), indent=2) + '\n')
PY

frozen="$output/source-snapshot"
echo 'Compiling standalone NewsHub arm64 iOS15; no Stage/Trial/host addon sources'
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=15.0 \
  -fobjc-arc -fmodules -dynamiclib -Wall -Wextra -Werror -Wno-unused-parameter \
  -framework Foundation -framework UIKit -framework CoreGraphics -Wl,-undefined,error -Wl,-no_fixup_chains -Wl,-no_adhoc_codesign \
  -install_name @rpath/TurboIONewsHub.dylib -I "$frozen" \
  "${sources[@]/#/$frozen/}" -o "$output/TurboIONewsHub.dylib"
python3 - "$root" "$output" "$sdk" <<'PY'
import importlib.util
import json
from pathlib import Path
import sys
root, output = map(Path, sys.argv[1:3])
spec = importlib.util.spec_from_file_location('news_package', root / 'package-newshub.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
dylib = output / 'TurboIONewsHub.dylib'
module.validate_module(dylib.read_bytes())
p = output / 'newshub-source-manifest.json'
manifest = json.loads(p.read_text())
manifest.update(dylib_sha256=module.file_sha(dylib), install_name=module.LOAD, iphoneos_sdk=sys.argv[3], minimum_ios='15.0', signed=False, dynamic_lookup=False)
p.write_text(json.dumps(manifest, indent=2) + '\n')
PY
file "$output/TurboIONewsHub.dylib"
xcrun nm -g "$output/TurboIONewsHub.dylib" | grep -E '_TIONewsHubInstallUI|_TIONewsHubNavigationInstall|OBJC_CLASS_\$_TIONewsHubPanel'
otool -L "$output/TurboIONewsHub.dylib"
echo 'Build independent NewsHub dylib SUCCESS!'
