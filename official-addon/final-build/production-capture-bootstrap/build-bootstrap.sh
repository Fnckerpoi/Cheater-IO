#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/constructor-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
mkdir "$OUT/source"
for F in Bootstrap.h Bootstrap.c View.h View.c Policy.h PolicyDisabled.inc SystemResolver.c SystemResolver.h Runner.c NegativeTests.c OwnImage.c GeneratePolicy.py build-bootstrap.sh; do cp "$F" "$OUT/source/$F"; done
SRC="$OUT/source"
FLAGS=(-std=c11 -Wall -Wextra -Werror)
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 ROOT="$OUT/public-$MODE.app"
 mkdir -m 700 "$ROOT"
 clang "${FLAGS[@]}" -O1 -dynamiclib -Wl,-no_fixup_chains "$SRC/OwnImage.c" -install_name "$ROOT/OwnImage.dylib" -o "$ROOT/OwnImage.dylib"
 printf '%s' '<plist><dict><key>CFBundleIdentifier</key><string>local.public.constructor.lab</string></dict></plist>' >"$ROOT/InfoFixture.plist"
 compile_bootstrap(){ clang "${FLAGS[@]}" "${SAN[@]}" -dynamiclib "$SRC/Bootstrap.c" "$SRC/View.c" "$SRC/SystemResolver.c" -install_name "$ROOT/Bootstrap.dylib" -o "$ROOT/Bootstrap.dylib" "$@"; }
 compile_bootstrap
 clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/Runner.c" "$ROOT/OwnImage.dylib" "$ROOT/Bootstrap.dylib" -Wl,-sectcreate,__TEXT,__info_plist,"$ROOT/InfoFixture.plist" -o "$ROOT/Runner"
 python3 "$SRC/GeneratePolicy.py" "$ROOT/Runner" "$ROOT/OwnImage.dylib" "$SRC/Policy-$MODE.inc"
 compile_bootstrap -DTIO_BOOTSTRAP_POLICY_HEADER="\"Policy-$MODE.inc\""
 ASAN_OPTIONS=detect_leaks=0 "$ROOT/Runner" >"$OUT/$MODE.log" 2>&1
 clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_BOOTSTRAP_POLICY_HEADER="\"Policy-$MODE.inc\"" "$SRC/NegativeTests.c" "$SRC/View.c" "$SRC/SystemResolver.c" "$ROOT/OwnImage.dylib" -o "$ROOT/NegativeTests"
 ASAN_OPTIONS=detect_leaks=0 "$ROOT/NegativeTests" >"$OUT/negative-$MODE.log" 2>&1
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" "${FLAGS[@]}" -dynamiclib "$SRC/Bootstrap.c" "$SRC/View.c" "$SRC/SystemResolver.c" -install_name @rpath/TIOProductionBootstrapBlocked.dylib -o "$OUT/TIOProductionBootstrapBlocked.dylib"
xcrun nm -u "$OUT/TIOProductionBootstrapBlocked.dylib" >"$OUT/imports.txt"
python3 - "$OUT" <<'PY'
from pathlib import Path
import json,hashlib,sys,time
out=Path(sys.argv[1]);entries=[]
for p in sorted((out/'source').iterdir()):entries.append({'path':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
raw=json.dumps({'files':entries,'actual_compiled_private_snapshot':True},indent=2).encode();(out/'source-manifest.json').write_bytes(raw)
r={'production_status':'BLOCKED_IDENTITY_POLICY','own_fixture_status':'IDENTITY_VERIFIED_BLOCKED_RESOLVER_OR_LIFETIME','production_slot_read':False,'production_slot_write':False,'capture_started':False,'actual_schema_file':False,'constructor_uses_heap':False,'constructor_calls_dladdr_dlsym_dlerror':False,'loader_lock_safety_proven':False,'ios_arm64_compile':True,'ios_runtime':False,'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIOProductionBootstrapBlocked.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review':'UNREVIEWED'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
PY
