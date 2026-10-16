#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p slot-installer/build
OUT=$(mktemp -d "$PWD/slot-installer/build/installer-XXXXXX")
printf '%s\n' "$OUT" > slot-installer/build/latest-output.txt
python3 - "$OUT" <<'SNAP'
from pathlib import Path
import json,sys,hashlib
r=Path.cwd();o=Path(sys.argv[1]);files=['slot-installer/SlotInstaller.h','slot-installer/SlotInstaller.c','slot-installer/SlotInstallerTests.c','slot-installer/ProductionBoundaryTests.c','slot-installer/build-installer.sh','image-attestation/ImageAttestation.h','image-attestation/ImageAttestation.c','image-attestation/generated/ImagePolicy.inc','memory-copy/MemoryCopy.h','memory-copy/MemoryCopy.c','resolver-bridge/ResolverBridge.h','resolver-bridge/ResolverBridge.c','resolver-bridge/ResolverBridge.S'];entries=[]
for name in files:
 p=r/name;assert p.is_file() and not p.is_symlink();b=p.read_bytes();d=o/'source'/name;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes(b);entries.append({'path':name,'sha256':hashlib.sha256(b).hexdigest()})
(o/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_from_private_snapshot':True,'testing_policy_excluded_from_production':True},indent=2))
SNAP
SRC="$OUT/source"
SOURCES=("$SRC/slot-installer/SlotInstaller.c" "$SRC/image-attestation/ImageAttestation.c" "$SRC/memory-copy/MemoryCopy.c")
FLAGS=(-std=c11 -Wall -Wextra -Werror)
clang "${FLAGS[@]}" "${SOURCES[@]}" "$SRC/slot-installer/ProductionBoundaryTests.c" -o "$OUT/production-test"
"$OUT/production-test" > "$OUT/production-test.log" 2>&1
for MODE in normal asan ubsan; do
    SAN=(-fno-common)
    if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
    if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
    clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_SLOT_TESTING -DTIO_IMAGE_ATTESTATION_TESTING "${SOURCES[@]}" "$SRC/resolver-bridge/ResolverBridge.c" "$SRC/resolver-bridge/ResolverBridge.S" "$SRC/slot-installer/SlotInstallerTests.c" -o "$OUT/$MODE"
    ASAN_OPTIONS=detect_leaks=0 "$OUT/$MODE" > "$OUT/$MODE.log" 2>&1
 done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" "${FLAGS[@]}" -c "$SRC/slot-installer/SlotInstaller.c" -o "$OUT/SlotInstaller-arm64.o"
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" "${FLAGS[@]}" -dynamiclib "${SOURCES[@]}" -install_name @rpath/TIOSlotInstallerInternal.dylib -o "$OUT/TIOSlotInstallerInternal.dylib"
xcrun nm -gU "$OUT/TIOSlotInstallerInternal.dylib" > "$OUT/exports.txt"
xcrun nm -u "$OUT/TIOSlotInstallerInternal.dylib" > "$OUT/imports.txt"
python3 - "$OUT" <<'RECEIPT'
from pathlib import Path
import json,hashlib,sys,time,platform
out=Path(sys.argv[1]);raw=(out/'source-manifest.json').read_bytes();m=json.loads(raw)
for e in m['files']:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
exports=(out/'exports.txt').read_text();imports=(out/'imports.txt').read_text()
for forbidden in ['TIOSlotFixture','TIOImageTest','TIOResolverBridgeTest']:assert forbidden not in exports
for forbidden in ['_mprotect','_mmap','_munmap','_dlsym']:assert forbidden not in imports
receipt={'host_native_isa':platform.machine(),'own_mapping_vm_cas_tests':True,'production_status':'BLOCKED_SLOT_NOT_PROVEN_OR_UNSUPPORTED_LIFETIME','actual_official_slot_read':False,'actual_official_slot_write':False,'slot_installed_on_official_image':False,'event_observed':False,'schema_validated':False,'runtime_deployment_authorized':False,'caller_proof_parameter':False,'own_slot_bridge':'task71 pure transparent assembly bridge','ios_arm64_compile':True,'ios_arm64_runtime':False,'aba_protected':False,'lifetime_pin_implemented':False,'test_scope':'constructor-owned self mappings retained for process lifetime; no real Flutter GOT','source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIOSlotInstallerInternal.dylib').read_bytes()).hexdigest(),'source_freeze_epoch':int(time.time()),'review_status':'UNREVIEWED_NEW_BUILD'};(out/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt,indent=2))
RECEIPT
printf 'SLOT_INTERNAL_MAPPED_FOUNDATION_PASS\n'
