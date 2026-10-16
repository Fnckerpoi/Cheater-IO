#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/adapter-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
python3 - "$OUT" <<'PY'
from pathlib import Path
import json,hashlib,sys
r=Path.cwd();out=Path(sys.argv[1]);s=out/'source/final-build/production-capture-adapter';s.mkdir(parents=True);files=[]
for f in ['Adapter.h','Adapter.c','AdapterConfig.h','AdapterDisabled.inc','Selector.S','GenerateTrust.py','build-adapter.sh','Runner.c','OwnFlutter.c','OwnNative.c','OwnIsar.c']:
 p=r/f;q=s/f;q.write_bytes(p.read_bytes());files.append(q)
for directory in ['Vendor','FrozenBridge']:
 for p in (r/directory).iterdir():
  q=s/directory/p.name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(p.read_bytes());files.append(q)
for p in (r/'Capture').iterdir():
 q=out/'source/final-build/schema-capture'/p.name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(p.read_bytes());files.append(q)
for p in (r/'manuscript-lab').rglob('*'):
 if p.is_file():
  q=out/'source/manuscript-lab'/p.relative_to(r/'manuscript-lab');q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(p.read_bytes());files.append(q)
(out/'source-manifest.json').write_text(json.dumps({'files':[{'path':str(p.relative_to(out/'source')),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}for p in files],'actual_private_snapshot':True},indent=2))
PY
SRC="$OUT/source/final-build/production-capture-adapter";CAP="$OUT/source/final-build/schema-capture";LAB="$OUT/source/manuscript-lab"
FLAGS=(-std=c11 -Wall -Wextra -Werror -I"$CAP")
CORE=("$SRC/Adapter.c" "$SRC/Selector.S" "$SRC/Vendor/View.c" "$SRC/Vendor/SystemResolver.c" "$SRC/FrozenBridge/ResolverBridge.c" "$SRC/FrozenBridge/ResolverBridge.S" "$CAP/Capture.c" "$CAP/SchemaPolicy.c" "$LAB/schema-observer/SchemaView.c" "$LAB/memory-copy/MemoryCopy.c")
for MODE in normal; do
 SAN=(-fno-common)
 ROOT="$OUT/public-$MODE.app";mkdir -m700 "$ROOT" "$ROOT/Frameworks";HOMEFIX="$OUT/data-$MODE";mkdir -m700 "$HOMEFIX"
 python3 - "$ROOT/Info.plist" <<'PLIST'
import plistlib,sys
open(sys.argv[1],'wb').write(plistlib.dumps({'CFBundleIdentifier':'local.public.capture.observer','CFBundleShortVersionString':'1.0.5','CFBundleVersion':'201'}))
PLIST
 clang "${FLAGS[@]}" -dynamiclib "$SRC/OwnNative.c" -install_name "$ROOT/Frameworks/OwnNative.dylib" -o "$ROOT/Frameworks/OwnNative.dylib"
 clang "${FLAGS[@]}" -dynamiclib "$SRC/OwnIsar.c" -install_name "$ROOT/Frameworks/OwnIsar.dylib" -o "$ROOT/Frameworks/OwnIsar.dylib"
 compile_observer(){ clang "${FLAGS[@]}" "${SAN[@]}" -dynamiclib "${CORE[@]}" -install_name "$ROOT/Frameworks/Observer.dylib" -Wl,-exported_symbol,_TIOStageBCaptureAdapterGetStatus -o "$ROOT/Frameworks/Observer.dylib" "$@"; }
 compile_observer
 clang "${FLAGS[@]}" -O1 -dynamiclib -Wl,-no_fixup_chains "$SRC/OwnFlutter.c" -lSystem -Wl,-needed_library,"$ROOT/Frameworks/Observer.dylib" -Wl,-needed_library,"$ROOT/Frameworks/OwnIsar.dylib" -install_name "$ROOT/Frameworks/OwnFlutter.dylib" -o "$ROOT/Frameworks/OwnFlutter.dylib"
 # Exact loader-relative strong dependency, not appended last on Runner.
 install_name_tool -change "$ROOT/Frameworks/Observer.dylib" @loader_path/Observer.dylib "$ROOT/Frameworks/OwnFlutter.dylib"
 clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/Runner.c" "$ROOT/Frameworks/OwnFlutter.dylib" "$ROOT/Frameworks/OwnNative.dylib" "$ROOT/Frameworks/Observer.dylib" "$ROOT/Frameworks/OwnIsar.dylib" -o "$ROOT/Runner"
 python3 "$SRC/GenerateTrust.py" "$ROOT/Runner" "$ROOT/Frameworks/OwnFlutter.dylib" "$ROOT/Frameworks/OwnNative.dylib" "$ROOT/Frameworks/OwnIsar.dylib" "$ROOT/Info.plist" "$SRC/Policy-$MODE.inc"
 compile_observer -DTIO_BOOTSTRAP_POLICY_HEADER="\"../Policy-$MODE.inc\"" -DTIO_ADAPTER_CONFIG_HEADER="\"Config-Policy-$MODE.inc\""
 HOME="$HOMEFIX" "$ROOT/Runner" >"$OUT/$MODE.log" 2>&1
done
printf '%s\n' "$OUT"
