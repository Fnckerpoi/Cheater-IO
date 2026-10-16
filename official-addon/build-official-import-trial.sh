#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
output=${1:?Usage: bash build-official-import-trial.sh /absolute/new-output-directory}
[[ "$output" = /* && ! -e "$output" ]] || { echo 'Use a new absolute output directory; never overwrite' >&2; exit 2; }
for source in OfficialImportTrialCore.m OfficialImportTrialHost.m OfficialImportTrialUI.m; do
  [[ -f "$root/$source" ]] || { echo "Source not ready: $source" >&2; exit 2; }
done
sdk="$(xcrun --sdk iphoneos --show-sdk-path)"
mkdir -m 700 "$output"
python3 - "$root" "$output" <<'PY'
import hashlib, json, sys
from pathlib import Path
root, output = map(Path, sys.argv[1:])
names = [f'OfficialImportTrial{part}.{ext}' for part in ['Core', 'Host', 'UI'] for ext in ['h', 'm']]
with (output / 'build-source-sha256.json').open('x') as f:
    json.dump({n: hashlib.sha256((root / n).read_bytes()).hexdigest() for n in names}, f, indent=2)
    f.write('\n')
PY
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=15.0 \
  -fobjc-arc -fmodules -dynamiclib -Wall -Wextra -Werror -Wno-unused-parameter \
  -framework Foundation -framework UIKit -Wl,-undefined,error -Wl,-no_fixup_chains -Wl,-no_adhoc_codesign \
  -install_name @rpath/TurboIOOfficialImportTrial.dylib \
  "$root/OfficialImportTrialCore.m" "$root/OfficialImportTrialHost.m" "$root/OfficialImportTrialUI.m" \
  -o "$output/TurboIOOfficialImportTrial.dylib"
python3 - "$root" "$output" <<'PY'
import hashlib, json, sys
from pathlib import Path
root, output = map(Path, sys.argv[1:])
freeze = json.loads((output / 'build-source-sha256.json').read_text())
if any(hashlib.sha256((root / n).read_bytes()).hexdigest() != digest for n, digest in freeze.items()):
    raise SystemExit('Sources changed during compilation; reject candidate and rebuild in a new directory')
print('PASS pre/post compile source freeze')
PY
file "$output/TurboIOOfficialImportTrial.dylib"
xcrun nm -g "$output/TurboIOOfficialImportTrial.dylib" | grep _TIOOfficialImportTrialInstallUI
xcrun nm -u "$output/TurboIOOfficialImportTrial.dylib" > "$output/undefined-system-symbols.txt"
otool -L "$output/TurboIOOfficialImportTrial.dylib"
echo 'Linked standalone iOS15 trial only; not signed, installed or device verified'
