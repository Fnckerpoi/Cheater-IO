#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/capture-XXXXXX")
printf '%s\n' "$OUT" > build/latest-output.txt
BASE=$(cd ../.. && pwd)
python3 - "$OUT" "$BASE" <<'SNAP'
from pathlib import Path
import json,hashlib,sys
out=Path(sys.argv[1]);base=Path(sys.argv[2]);files=['final-build/schema-capture/'+f for f in ['Capture.h','Capture.c','CaptureTests.c','SchemaPolicy.h','SchemaPolicy.c','SchemaPolicyTests.c','OwnEngine.c','LookupCaptureTests.c','OriginalExceptionTests.m','AttackMatrix.py','build-capture.sh']]+['manuscript-lab/'+f for f in ['schema-observer/SchemaView.h','schema-observer/SchemaView.c','memory-copy/MemoryCopy.h','memory-copy/MemoryCopy.c','resolver-bridge/ResolverBridge.h','resolver-bridge/ResolverBridge.c','resolver-bridge/ResolverBridge.S']];entries=[]
for f in files:
 p=base/f;assert p.is_file() and not p.is_symlink();data=p.read_bytes();q=out/'source'/f;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(data);entries.append({'path':f,'sha256':hashlib.sha256(data).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_private_snapshot':True,'production_runtime_capability':False},indent=2))
SNAP
SRC="$OUT/source/final-build/schema-capture"
LAB="$OUT/source/manuscript-lab"
FLAGS=(-std=c11 -Wall -Wextra -Werror -I"$LAB/resolver-bridge")
CORE=("$SRC/Capture.c" "$SRC/SchemaPolicy.c" "$LAB/schema-observer/SchemaView.c" "$LAB/memory-copy/MemoryCopy.c")
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/SchemaPolicy.c" "$SRC/SchemaPolicyTests.c" -o "$OUT/policy-$MODE"
 ASAN_OPTIONS=detect_leaks=0 "$OUT/policy-$MODE" > "$OUT/policy-$MODE.log" 2>&1
 clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_CAPTURE_TESTING "${CORE[@]}" "$SRC/CaptureTests.c" -o "$OUT/capture-$MODE"
 mkdir "$OUT/attacks-$MODE"
 ASAN_OPTIONS=detect_leaks=0 python3 "$SRC/AttackMatrix.py" "$OUT/capture-$MODE" "$OUT/attacks-$MODE" > "$OUT/attacks-$MODE.log" 2>&1
 clang "${FLAGS[@]}" "${SAN[@]}" -dynamiclib "$SRC/OwnEngine.c" -o "$OUT/OwnEngine-$MODE.dylib"
 clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_CAPTURE_TESTING -DTIO_BRIDGE_TESTING "${CORE[@]}" "$LAB/resolver-bridge/ResolverBridge.c" "$LAB/resolver-bridge/ResolverBridge.S" "$SRC/LookupCaptureTests.c" -o "$OUT/lookup-$MODE"
 HOMEFIX=$(mktemp -d "$OUT/lookup-home-XXXXXX")
 ASAN_OPTIONS=detect_leaks=0 "$OUT/lookup-$MODE" "$OUT/OwnEngine-$MODE.dylib" "$HOMEFIX" > "$OUT/lookup-$MODE.log" 2>&1
 clang "${FLAGS[@]}" "${SAN[@]}" -fobjc-arc -framework Foundation -DTIO_CAPTURE_TESTING "${CORE[@]}" "$SRC/OriginalExceptionTests.m" -o "$OUT/exception-$MODE"
 HOMEFIX=$(mktemp -d "$OUT/exception-home-XXXXXX")
 ASAN_OPTIONS=detect_leaks=0 "$OUT/exception-$MODE" "$HOMEFIX" > "$OUT/exception-$MODE.log" 2>&1
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -dynamiclib "$SRC/Capture.c" "$SRC/SchemaPolicy.c" -install_name @rpath/TIOSchemaCaptureBlockedInternal.dylib -o "$OUT/TIOSchemaCaptureBlockedInternal.dylib"
xcrun nm -u "$OUT/TIOSchemaCaptureBlockedInternal.dylib" > "$OUT/production-imports.txt"
xcrun nm -gU "$OUT/TIOSchemaCaptureBlockedInternal.dylib" > "$OUT/production-exports.txt"
python3 - "$OUT" <<'RECEIPT'
from pathlib import Path
import json,hashlib,sys,time,platform
out=Path(sys.argv[1]);raw=(out/'source-manifest.json').read_bytes();m=json.loads(raw)
for e in m['files']:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
imports=(out/'production-imports.txt').read_text();exports=(out/'production-exports.txt').read_text()
for f in ['TIOCaptureFixture','TIOCaptureForward']:assert f not in exports
for f in ['openat','rename','mkdir','dlsym','objc','Foundation','vm_read','NSUser']:assert f not in imports
r={'host_native_isa':platform.machine(),'host_capture_file_fixture_tested':True,'production_status':'BLOCKED_EARLY_RUNTIME_ENTRY','production_eligible_runtime_path':False,'production_file_export_enabled':False,'production_path_authorized':False,'actual_slot_read':False,'actual_slot_write':False,'actual_schema_captured':False,'actual_key_read':False,'db_write_enabled':False,'ios_arm64_compile':True,'ios_runtime_executed':False,'schema_fields':['collection:name,idName,embedded,properties,indexes,version','property:name,type,target','index:name,properties,unique,hash'],'unsupported':['enumMap dynamic metadata','unknown or record/value/password members outside schema structure'],'one_shot':'first eligible null-key attempt terminal, all original calls still forwarded','source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIOSchemaCaptureBlockedInternal.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review_status':'UNREVIEWED_NEW_BUILD'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
RECEIPT
printf 'PURE_C_SCHEMA_CAPTURE_FOUNDATION_PASS\n'
