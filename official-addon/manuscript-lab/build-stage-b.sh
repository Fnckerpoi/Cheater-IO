#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p stage-b-controller/build
OUT=$(mktemp -d "$PWD/stage-b-controller/build/controller-XXXXXX")
printf '%s\n' "$OUT" > stage-b-controller/build/latest-output.txt
python3 - "$OUT" <<'SNAPSHOT'
from pathlib import Path
import json,sys,hashlib
root=Path.cwd();out=Path(sys.argv[1]);snapshot=out/'source-snapshot'
files=['StageBObserver.m','StageBObserverTests.m','build-stage-b.sh','stage-b-controller/StageBController.h','schema-observer/SchemaView.c','schema-observer/SchemaView.h','schema-observer/SchemaMetadata.m','schema-observer/SchemaMetadata.h','memory-copy/MemoryCopy.c','memory-copy/MemoryCopy.h','open-observer/OpenObserver.h']
entries=[]
for name in files:
 p=root/name;assert p.is_file() and not p.is_symlink();data=p.read_bytes();dest=snapshot/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);entries.append({'path':name,'sha256':hashlib.sha256(data).hexdigest()})
manifest={'files':entries,'compiled_from_private_snapshot':True,'production_sources':['StageBObserver.m'],'host_test_sources':['StageBObserver.m','StageBObserverTests.m','schema-observer/SchemaView.c','schema-observer/SchemaMetadata.m','memory-copy/MemoryCopy.c'],'test_define_excluded_from_production':True}
(out/'source-manifest.json').write_text(json.dumps(manifest,indent=2))
SNAPSHOT
SNAP="$OUT/source-snapshot"
SOURCES=("$SNAP/StageBObserver.m" "$SNAP/schema-observer/SchemaView.c" "$SNAP/schema-observer/SchemaMetadata.m" "$SNAP/memory-copy/MemoryCopy.c")
FLAGS=(-fobjc-arc -Wall -Wextra -Werror -framework Foundation)
clang "${FLAGS[@]}" "${SOURCES[@]}" "$SNAP/StageBObserverTests.m" -o "$OUT/production-test"
"$OUT/production-test" > "$OUT/production-test.log" 2>&1
clang -DTIO_STAGE_B_TESTING=1 "${FLAGS[@]}" "${SOURCES[@]}" "$SNAP/StageBObserverTests.m" -o "$OUT/controller-test"
"$OUT/controller-test" > "$OUT/controller-test.log" 2>&1
clang -DTIO_STAGE_B_TESTING=1 -fsanitize=address,undefined -fno-omit-frame-pointer "${FLAGS[@]}" "${SOURCES[@]}" "$SNAP/StageBObserverTests.m" -o "$OUT/controller-asan"
ASAN_OPTIONS=detect_leaks=0 "$OUT/controller-asan" > "$OUT/controller-asan.log" 2>&1
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -dynamiclib "${FLAGS[@]}" "$SNAP/StageBObserver.m" -install_name @rpath/TurboIOStageBController.dylib -o "$OUT/TurboIOStageBController.dylib"
xcrun nm -gU "$OUT/TurboIOStageBController.dylib" > "$OUT/exports.txt"
xcrun nm -u "$OUT/TurboIOStageBController.dylib" > "$OUT/imports.txt"
xcrun otool -L "$OUT/TurboIOStageBController.dylib" > "$OUT/libraries.txt"
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,json,sys,time
root=Path.cwd();out=Path(sys.argv[1]);module=out/'TurboIOStageBController.dylib'
raw=(out/'source-manifest.json').read_bytes();manifest=json.loads(raw)
for entry in manifest['files']:
 assert hashlib.sha256((out/'source-snapshot'/entry['path']).read_bytes()).hexdigest()==entry['sha256']
imports=(out/'imports.txt').read_text(); exports=(out/'exports.txt').read_text(); payload=module.read_bytes()
for forbidden in ['TIOStageBObserverArm','TIOStageBHandleOpen','testGrantForCurrentThread','observeSchema:realKey:','TIO_STAGE_B_TESTING']:
 assert forbidden.encode() not in payload,forbidden
for forbidden in ['isar_','dlsym','dlopen','vm_read','mach_task_self','TIOOpenArm']:
 assert forbidden not in imports,forbidden
receipt={'module':str(module),'module_sha256':hashlib.sha256(payload).hexdigest(),'source_manifest':str(out/'source-manifest.json'),'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'built_epoch':int(time.time()),'install_name':'@rpath/TurboIOStageBController.dylib','entry_installed':False,'active':False,'device_candidate':False,'schema_observation_enabled':False,'runtime_entry':'BLOCKED_RUNTIME_ENTRY','review_status':'UNREVIEWED_NEW_BUILD','test_results':['production blocked forwarding PASS','test-only own-memory typed forwarding PASS','ASAN+UBSAN PASS','iOS arm64 compile PASS'],'limitations':['No actual runtime adapter capability exists','Host fake capability is test-build-only','iOS ARM64 compile is not same-architecture runtime ABI proof','No signing/install/engine/App/device/DB execution','No schema file or network export']}
(out/'build-receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt,indent=2))
PY
printf 'STAGE_B_INTERNAL_BLOCKED_BUILD_PASS\n'
