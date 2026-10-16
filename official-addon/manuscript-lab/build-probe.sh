#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
out=${1:?Usage: build-probe.sh /absolute/new-output}
[[ "$out" = /* && ! -e "$out" && ! -L "$out" ]] || { echo 'New absolute output required' >&2; exit 2; }
python3 - "$root" "$out" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]);out=Path(sys.argv[2]);current=out
while current!=current.parent:
    if current.is_symlink():raise SystemExit('Symlink output parent refused')
    current=current.parent
out.mkdir(parents=True,mode=0o700,exist_ok=False)
snapshot=out/'source-snapshot';snapshot.mkdir(mode=0o700)
files=[p for p in root.iterdir() if p.is_file() and p.suffix in ('.py','.m','.c','.h','.sh','.md')]
records=[]
parser=root.parent/'package-aihot-daily-stage.py'
data=parser.read_bytes();(snapshot/'macho_parser.py').write_bytes(data)
records.append(dict(path='../package-aihot-daily-stage.py',snapshot='macho_parser.py',sha256=hashlib.sha256(data).hexdigest()))
for p in sorted(files):
    data=p.read_bytes();(snapshot/p.name).write_bytes(data)
    records.append(dict(path=p.name,sha256=hashlib.sha256(data).hexdigest()))
for r in records:
    if hashlib.sha256((root/r['path']).read_bytes()).hexdigest()!=r['sha256']:raise SystemExit('Source changed during freeze')
(out/'source-manifest.json').write_text(json.dumps(dict(stage='A_DISABLED',source_files=records,compiled_sources=['LabGate.c','ManuscriptLabProbe.m'],db_access_enabled=False),indent=2)+'\n')
PY
sdk=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -arch arm64 -isysroot "$sdk" -miphoneos-version-min=15.0 \
 -fobjc-arc -fmodules -dynamiclib -Wall -Wextra -Werror -Wno-unused-parameter \
 -framework Foundation -Wl,-undefined,error -Wl,-no_fixup_chains -Wl,-no_adhoc_codesign \
 -install_name @rpath/TurboIOManuscriptLabProbe.dylib \
 "$out/source-snapshot/LabGate.c" "$out/source-snapshot/ManuscriptLabProbe.m" -I "$out/source-snapshot" \
 -o "$out/TurboIOManuscriptLabProbe.dylib"
python3 - "$root" "$out" <<'PY'
import sys,importlib.util,json,hashlib
from pathlib import Path
root,out=map(Path,sys.argv[1:]);spec=importlib.util.spec_from_file_location('lab',root/'lab.py');lab=importlib.util.module_from_spec(spec);spec.loader.exec_module(lab)
f=out/'TurboIOManuscriptLabProbe.dylib';lab.validate_probe(f.read_bytes())
p=out/'source-manifest.json';m=json.loads(p.read_text());m.update(dylib_sha256=hashlib.sha256(f.read_bytes()).hexdigest(),status='COMPILE_ONLY_BLOCKED_CLEAN_INPUT_NO_DB_NO_INSTALL',signed=False);p.write_text(json.dumps(m,indent=2)+'\n');print(json.dumps(m,indent=2))
PY
