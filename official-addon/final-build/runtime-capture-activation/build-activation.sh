#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/activation-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
mkdir "$OUT/source"
cp Activation.c Activation.h Runner.c OwnFlutter.c OwnOther.c OwnIsar.c build-activation.sh "$OUT/source/"
cp -R Imported102 "$OUT/source/Imported102"
SRC="$OUT/source";I="$SRC/Imported102"
FLAGS=(-std=c11 -Wall -Wextra -Werror)
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 for CASE in full missing; do
 ROOT="$OUT/public-$MODE-$CASE.app";mkdir -m700 "$ROOT"
 clang "${FLAGS[@]}" -O1 -dynamiclib -Wl,-no_fixup_chains "$SRC/OwnFlutter.c" -install_name "$ROOT/OwnImage.dylib" -o "$ROOT/OwnImage.dylib"
 clang "${FLAGS[@]}" -dynamiclib "$SRC/OwnIsar.c" -install_name "$ROOT/OwnIsar.dylib" -o "$ROOT/OwnIsar.dylib"
 for NAME in A B C; do clang "${FLAGS[@]}" -dynamiclib "$SRC/OwnOther.c" -install_name "$ROOT/Own$NAME.dylib" -o "$ROOT/Own$NAME.dylib"; done
 compile(){ clang "${FLAGS[@]}" "${SAN[@]}" -dynamiclib "$SRC/Activation.c" "$I/IdentityAdapter.c" "$I/View.c" "$I/SystemResolver.c" -install_name "$ROOT/OwnActivation.dylib" -Wl,-needed_library,"$ROOT/OwnImage.dylib" -o "$ROOT/OwnActivation.dylib" "$@"; }
 compile
 printf '%s' '<plist><dict><key>CFBundleIdentifier</key><string>local.public.activation.lab</string></dict></plist>' >"$ROOT/PublicInfo.plist"
 EXTRA=("$ROOT/OwnC.dylib")
 if [ "$CASE" = missing ]; then EXTRA=("$ROOT/OwnB.dylib"); fi
 clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/Runner.c" "$ROOT/OwnImage.dylib" "$ROOT/OwnActivation.dylib" "$ROOT/OwnIsar.dylib" "$ROOT/OwnA.dylib" "$ROOT/OwnB.dylib" "${EXTRA[@]}" -Wl,-sectcreate,__TEXT,__info_plist,"$ROOT/PublicInfo.plist" -o "$ROOT/Runner"
 python3 "$I/GeneratePolicy.py" "$ROOT/Runner" "$ROOT/OwnImage.dylib" "$I/Policy-$MODE-$CASE.inc"
 compile -DTIO_BOOTSTRAP_POLICY_HEADER="\"Policy-$MODE-$CASE.inc\""
 if [ "$CASE" = missing ]; then
  ASAN_OPTIONS=detect_leaks=0 "$ROOT/Runner" missing >"$OUT/$MODE-missing.log" 2>&1
 else
  ASAN_OPTIONS=detect_leaks=0 "$ROOT/Runner" >"$OUT/$MODE.log" 2>&1
  ASAN_OPTIONS=detect_leaks=0 TIO_OWN_ORDER=early "$ROOT/Runner" >"$OUT/$MODE-early.log" 2>&1
 fi
 done
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" "${FLAGS[@]}" -dynamiclib "$SRC/Activation.c" "$I/IdentityAdapter.c" "$I/View.c" "$I/SystemResolver.c" -install_name @rpath/TIOActivationBlocked.dylib -o "$OUT/TIOActivationBlocked.dylib"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib,time
out=Path(sys.argv[1]);files=[]
for p in sorted((out/'source').rglob('*')):
 if p.is_file():files.append({'path':str(p.relative_to(out/'source')),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
raw=json.dumps({'files':files,'actual_private_snapshot':True},indent=2).encode();(out/'source-manifest.json').write_bytes(raw)
r={'status':'BLOCKED_LOADER_CONTEXT_FIRST_FFI_UNOBSERVABLE','strong_dependency_graph_verified_on_own_fixture':True,'resolved_slot_verified_on_own_fixture':True,'slot_installed':False,'capture_started':False,'file_created':False,'official_slot_read':False,'official_slot_write':False,'ios_runtime':False,'ios_arm64_compile':True,'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIOActivationBlocked.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review':'UNREVIEWED'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
PY
