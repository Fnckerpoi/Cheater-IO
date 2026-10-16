#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/bridge-XXXXXX")
printf '%s\n' "$OUT" > build/latest-output.txt
python3 - "$OUT" <<'SNAPSHOT'
from pathlib import Path
import json,hashlib,sys
r=Path.cwd();out=Path(sys.argv[1]);snap=out/'source';files=['ResolverBridge.h','ResolverBridge.c','ResolverBridge.S','Caller.c','Naive.c','Second.c','FixtureABI.h','BridgeTests.c','CallerSensitiveTests.c','RegisterSentinels.S','RegisterSentinelTests.c','Recursive.c','RecursiveTests.c','ProcessGlobal.c','ProcessLocal.c','ProcessCaller.c','ProcessNested.c','ProcessNull.S','ProcessPolicyTests.c','build-bridge.sh']
entries=[]
for f in files:
 p=r/f;assert p.is_file() and not p.is_symlink();data=p.read_bytes();(snap/f).parent.mkdir(parents=True,exist_ok=True);(snap/f).write_bytes(data);entries.append({'path':f,'sha256':hashlib.sha256(data).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_private_snapshot':True},indent=2))
SNAPSHOT
SRC="$OUT/source"
CC=(clang -O1 -Wall -Wextra -Werror)
"${CC[@]}" -dynamiclib "$SRC/Second.c" -install_name "$OUT/libSecond.dylib" -o "$OUT/libSecond.dylib"
"${CC[@]}" -dynamiclib "$SRC/Naive.c" -Wl,-needed_library,"$OUT/libSecond.dylib" -install_name "$OUT/libNaive.dylib" -o "$OUT/libNaive.dylib"
"${CC[@]}" -dynamiclib "$SRC/Caller.c" -Wl,-needed_library,"$OUT/libNaive.dylib" -Wl,-needed_library,"$OUT/libSecond.dylib" -install_name "$OUT/libCaller.dylib" -o "$OUT/libCaller.dylib"
"${CC[@]}" "$SRC/CallerSensitiveTests.c" -o "$OUT/naive-red"
python3 - "$OUT" <<'RED'
from pathlib import Path
import subprocess,sys
out=Path(sys.argv[1]);p=subprocess.run([str(out/'naive-red'),str(out/'libCaller.dylib'),str(out/'libNaive.dylib'),str(out/'libSecond.dylib')],capture_output=True,text=True,timeout=10)
(out/'naive-red.log').write_text(p.stdout+p.stderr)
assert p.returncode!=0 and 'baseline=333' in p.stdout and 'nested=222' in p.stdout,(p.returncode,p.stdout,p.stderr)
print('REAL_CALLER_SENSITIVE_RED_CONFIRMED')
RED
"${CC[@]}" -DTIO_BRIDGE_TESTING=1 "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/BridgeTests.c" -o "$OUT/qualified-test"
"$OUT/qualified-test" "$OUT/libCaller.dylib" "$OUT/libNaive.dylib" "$OUT/libSecond.dylib" > "$OUT/qualified-test.log" 2>&1
"${CC[@]}" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/BridgeTests.c" -o "$OUT/production-test"
"$OUT/production-test" "$OUT/libCaller.dylib" "$OUT/libNaive.dylib" "$OUT/libSecond.dylib" > "$OUT/production-test.log" 2>&1
"${CC[@]}" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/RegisterSentinels.S" "$SRC/RegisterSentinelTests.c" -o "$OUT/register-test"
"$OUT/register-test" > "$OUT/register-test.log" 2>&1
"${CC[@]}" -DTIO_BRIDGE_TESTING=1 -fsanitize=address,undefined -fno-omit-frame-pointer "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/BridgeTests.c" -o "$OUT/qualified-asan"
ASAN_OPTIONS=detect_leaks=0 "$OUT/qualified-asan" "$OUT/libCaller.dylib" "$OUT/libNaive.dylib" "$OUT/libSecond.dylib" > "$OUT/qualified-asan.log" 2>&1
"${CC[@]}" -dynamiclib "$SRC/Recursive.c" -o "$OUT/libRecursive.dylib"
"${CC[@]}" "$SRC/RecursiveTests.c" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" -o "$OUT/recursive-test"
"$OUT/recursive-test" "$OUT/libSecond.dylib" "$OUT/libRecursive.dylib" > "$OUT/recursive-test.log" 2>&1
"${CC[@]}" -dynamiclib "$SRC/ProcessLocal.c" -o "$OUT/process-local.dylib"
"${CC[@]}" -DFIXTURE_TAG=11 -dynamiclib "$SRC/ProcessGlobal.c" -o "$OUT/process-globalA.dylib"
"${CC[@]}" -DFIXTURE_TAG=22 -dynamiclib "$SRC/ProcessGlobal.c" -o "$OUT/process-globalB.dylib"
"${CC[@]}" -dynamiclib "$SRC/ProcessCaller.c" -o "$OUT/process-callerA.dylib"
"${CC[@]}" -dynamiclib "$SRC/ProcessCaller.c" -o "$OUT/process-callerB.dylib"
"${CC[@]}" -dynamiclib "$SRC/ProcessNested.c" -o "$OUT/process-nested.dylib"
"${CC[@]}" -dynamiclib "$SRC/ProcessNull.S" -o "$OUT/process-null.dylib"
"${CC[@]}" -DTIO_BRIDGE_TESTING=1 "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/ProcessPolicyTests.c" -o "$OUT/process-qualified-test"
"${CC[@]}" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/ProcessPolicyTests.c" -o "$OUT/process-production-test"
"${CC[@]}" -DTIO_BRIDGE_TESTING=1 -fsanitize=address,undefined -fno-omit-frame-pointer "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" "$SRC/ProcessPolicyTests.c" -o "$OUT/process-asan"
for ORDER in AB BA; do
    if [ "$ORDER" = AB ]; then FIRST=A; SECOND=B; else FIRST=B; SECOND=A; fi
    for MATCH in 0 1; do
        ARGS=("$OUT/process-local.dylib" "$OUT/process-global${FIRST}.dylib" "$OUT/process-global${SECOND}.dylib" "$OUT/process-callerA.dylib" "$OUT/process-callerB.dylib" "$OUT/process-nested.dylib" "$MATCH" "$OUT/process-null.dylib")
        "$OUT/process-qualified-test" "${ARGS[@]}" > "$OUT/process-qualified-${ORDER}-${MATCH}.log" 2>&1
        "$OUT/process-production-test" "${ARGS[@]}" > "$OUT/process-production-${ORDER}-${MATCH}.log" 2>&1
        ASAN_OPTIONS=detect_leaks=0 "$OUT/process-asan" "${ARGS[@]}" > "$OUT/process-asan-${ORDER}-${MATCH}.log" 2>&1
    done
done
"${CC[@]}" -dynamiclib "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" -install_name @rpath/TIOResolverBridge.dylib -o "$OUT/HostResolverBridge.dylib"
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
ARM=(xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -O1 -Wall -Wextra -Werror)
"${ARM[@]}" -c "$SRC/ResolverBridge.c" -o "$OUT/ResolverBridge-arm64.o"
"${ARM[@]}" -c "$SRC/ResolverBridge.S" -o "$OUT/ResolverBridge-asm-arm64.o"
"${ARM[@]}" -c "$SRC/RegisterSentinels.S" -o "$OUT/RegisterSentinels-arm64.o"
"${ARM[@]}" -DTIO_BRIDGE_TESTING=1 -c "$SRC/ResolverBridge.S" -o "$OUT/FixtureBridge-asm-arm64.o"
"${ARM[@]}" -DTIO_BRIDGE_TESTING=1 -c "$SRC/ResolverBridge.c" -o "$OUT/FixtureBridge-arm64.o"
"${ARM[@]}" -dynamiclib "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" -install_name @rpath/TIOResolverBridge.dylib -o "$OUT/IOSResolverBridge.dylib"
xcrun llvm-objdump -d "$OUT/ResolverBridge-asm-arm64.o" > "$OUT/arm64-passthrough.asm"
xcrun llvm-objdump -d "$OUT/FixtureBridge-asm-arm64.o" > "$OUT/arm64-fixture-branch.asm"
xcrun llvm-objdump -d "$OUT/FixtureBridge-arm64.o" > "$OUT/arm64-fixture-resolve.asm"
xcrun llvm-objdump -d "$OUT/RegisterSentinels-arm64.o" > "$OUT/arm64-sentinels.asm"
xcrun llvm-objdump -d "$OUT/HostResolverBridge.dylib" > "$OUT/host-bridge.asm"
xcrun nm -u "$OUT/IOSResolverBridge.dylib" > "$OUT/ios-imports.txt"
xcrun nm -gU "$OUT/IOSResolverBridge.dylib" > "$OUT/ios-exports.txt"
python3 - "$OUT" <<'RECEIPT'
from pathlib import Path
import json,hashlib,sys,platform,time,re
out=Path(sys.argv[1]);manifest=(out/'source-manifest.json').read_bytes();m=json.loads(manifest)
for e in m['files']:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
asm=(out/'arm64-passthrough.asm').read_text();assert 'br\tx16' in asm or 'br x16' in asm
assert not re.search(r'\b(?:bl|blr|stp|sub|add)\s+(?:x30|sp)',asm)
# Exact production trampoline changes only scratch x16; no SP, LR or argument/vector registers.
lines=[x.split('\t')[-1] for x in asm.splitlines() if re.search(r'^[ \t]*[0-9a-f]+:',x)]
assert len(lines)==4,lines
for symbol in ['TIOBridgeFixture','TestQualify','TestExpire','TIOBridgeRealResolver']:assert symbol not in (out/'ios-exports.txt').read_text()
receipt={'host_native_isa':platform.machine(),'process_default_fixture_policy':True,'non_default_special_handles_qualified':False,'native_runtime_tests':['actual RTLD_NEXT naive-wrapper RED','passthrough+qualified ordinary fixture GREEN','RTLD_NEXT/DEFAULT/NULL/ordinary/unknown/dlerror TLS/errno','4 native threads and real nested dyld symbol-resolver recursion','11-argument typed own-dylib export, nonnull key pointer preserved','register/SP/caller-return-PC sentinels native PASS','ASAN+UBSAN PASS','DEFAULT GLOBAL A/B reverse load order, LOCAL exclusion, two caller images','DEFAULT actual NULL-no-error symbol vs missing-with-error, Flutter clear/lookup/errorcheck','DEFAULT selected global export qualification and shadow mismatch refusal'],'ios_arm64_sdk_compile':True,'ios_arm64_runtime_executed':False,'official_runtime_entry_observed':False,'runtime_status':'UNOBSERVED_NO_OFFICIAL_RUNTIME_CAPABILITY','source_ios_loader_path':'DynamicLibrary.process()','source_ios_qualification':'EXACT_RTLD_DEFAULT_NATIVE_FIXTURE_VALIDATED_NOT_OFFICIAL_RUNTIME','selected_official_backend':'none','official_slot_installed':False,'official_engine_executed':False,'production_qualification_factory':False,'source_manifest_sha256':hashlib.sha256(manifest).hexdigest(),'host_module_sha256':hashlib.sha256((out/'HostResolverBridge.dylib').read_bytes()).hexdigest(),'ios_module_sha256':hashlib.sha256((out/'IOSResolverBridge.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review_status':'UNREVIEWED_NEW_BUILD'}
(out/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt,indent=2))
RECEIPT
printf 'RESOLVER_BRIDGE_NATIVE_FOUNDATION_PASS\n'
