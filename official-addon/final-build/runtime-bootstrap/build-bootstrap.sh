#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/bootstrap-XXXXXX")
printf '%s\n' "$OUT" > build/latest-output.txt
BASE=$(cd ../../manuscript-lab && pwd)
python3 - "$OUT" "$BASE" <<'SNAP'
from pathlib import Path
import json,hashlib,sys
r=Path.cwd();o=Path(sys.argv[1]);base=Path(sys.argv[2]);s=o/'source';s.mkdir()
files=['Bootstrap.h','Bootstrap.c','FixtureFlutter.h','FixtureFlutter.c','RunnerFixture.c','build-bootstrap.sh'];entries=[]
for f in files:
 b=(r/f).read_bytes();(s/f).write_bytes(b);entries.append({'path':f,'sha256':hashlib.sha256(b).hexdigest()})
for f in ['ResolverBridge.h','ResolverBridge.c','ResolverBridge.S']:
 b=(base/'resolver-bridge'/f).read_bytes();(s/f).write_bytes(b);entries.append({'path':'resolver-bridge/'+f,'sha256':hashlib.sha256(b).hexdigest()})
(o/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_private_snapshot':True},indent=2))
(s/'FixtureIdentity.h').write_text('#define TIO_FIXTURE_RUNNER_UUID {0}\n#define TIO_FIXTURE_FLUTTER_UUID {0}\n#define TIO_FIXTURE_RUNNER_PATH "unconfigured"\n#define TIO_FIXTURE_FLUTTER_PATH "unconfigured"\n#define TIO_FIXTURE_NATIVE_PATH "unconfigured"\n#define TIO_FIXTURE_INFO "unconfigured"\n#define TIO_FIXTURE_DUPLICATE_PATH "unconfigured"\n')
(s/'Duplicate.c').write_text('int duplicate_flutter_fixture(void){return 1;}\n')
(s/'FixtureInfo.plist').write_text('<plist><dict><key>CFBundleIdentifier</key><string>local.public.bootstrap.fixture</string></dict></plist>')
SNAP
SRC="$OUT/source"
FLAGS=(-std=c11 -Wall -Wextra -Werror -fno-common)
case "${TIO_BOOTSTRAP_SANITIZE:-none}" in
 asan) FLAGS+=(-fsanitize=address -fno-omit-frame-pointer);;
 ubsan) FLAGS+=(-fsanitize=undefined -fno-sanitize-recover=all);;
 none) ;;
 *) exit 2;;
esac
mkdir -p "$OUT/Shadow"
clang "${FLAGS[@]}" -dynamiclib "$SRC/Duplicate.c" -install_name "$OUT/Shadow/FlutterFixture.dylib" -o "$OUT/Shadow/FlutterFixture.dylib"
clang "${FLAGS[@]}" -dynamiclib "$SRC/FixtureFlutter.c" -install_name "$OUT/FlutterFixture.dylib" -o "$OUT/FlutterFixture.dylib"
clang "${FLAGS[@]}" -DTIO_BOOTSTRAP_TESTING -dynamiclib "$SRC/Bootstrap.c" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" -Wl,-needed_library,"$OUT/FlutterFixture.dylib" -install_name "$OUT/NativeFixture.dylib" -o "$OUT/NativeFixture.dylib"
clang "${FLAGS[@]}" -DTIO_BOOTSTRAP_TESTING "$SRC/RunnerFixture.c" "$OUT/FlutterFixture.dylib" "$OUT/NativeFixture.dylib" -Wl,-sectcreate,__TEXT,__info_plist,"$SRC/FixtureInfo.plist" -o "$OUT/RunnerFixture"
python3 - "$OUT" <<'POLICY'
from pathlib import Path
import struct,json,sys,hashlib
out=Path(sys.argv[1]);s=out/'source'
def uuid(p):
 b=p.read_bytes();magic,cpu,sub,file,ncmd,sz,flags,res=struct.unpack_from('<8I',b);assert magic==0xfeedfacf;o=32;found=[]
 for i in range(ncmd):
  cmd,n=struct.unpack_from('<II',b,o)
  if cmd==0x1b:found.append(b[o+8:o+24])
  o+=n
 assert len(found)==1;return found[0]
def cbytes(b):return '{'+','.join(str(x)for x in b)+'}'
text='#define TIO_FIXTURE_RUNNER_UUID '+cbytes(uuid(out/'RunnerFixture'))+'\n#define TIO_FIXTURE_FLUTTER_UUID '+cbytes(uuid(out/'FlutterFixture.dylib'))+'\n'
for key,value in [('RUNNER_PATH',str(out/'RunnerFixture')),('FLUTTER_PATH',str(out/'FlutterFixture.dylib')),('NATIVE_PATH',str(out/'NativeFixture.dylib')),('DUPLICATE_PATH',str(out/'Shadow/FlutterFixture.dylib')),('INFO',(s/'FixtureInfo.plist').read_text())]:text+='#define TIO_FIXTURE_'+key+' '+json.dumps(value)+'\n'
(s/'FixtureIdentity.h').write_text(text)
(out/'fixture-policy.json').write_text(json.dumps({'fixture_only':True,'identity_header_sha256':hashlib.sha256(text.encode()).hexdigest(),'runner_uuid':uuid(out/'RunnerFixture').hex(),'flutter_uuid':uuid(out/'FlutterFixture.dylib').hex()},indent=2))
manifest=json.loads((out/'source-manifest.json').read_text())
for name in ['FixtureIdentity.h','FixtureInfo.plist','Duplicate.c']:
 manifest['files'].append({'path':name,'sha256':hashlib.sha256((s/name).read_bytes()).hexdigest(),'generated_own_fixture_only':True})
(out/'source-manifest.json').write_text(json.dumps(manifest,indent=2))
POLICY
# Relink only the dependency, not Runner: its recorded UUID remains the executed main UUID.
clang "${FLAGS[@]}" -DTIO_BOOTSTRAP_TESTING -dynamiclib "$SRC/Bootstrap.c" "$SRC/ResolverBridge.c" "$SRC/ResolverBridge.S" -Wl,-needed_library,"$OUT/FlutterFixture.dylib" -install_name "$OUT/NativeFixture.dylib" -o "$OUT/NativeFixture.dylib"
for MODE in normal early readonly wrongidentity wrongheader wrongvalue duplicate race; do
 TIO_FIXTURE_MODE="$MODE" "$OUT/RunnerFixture" > "$OUT/$MODE.log" 2>&1
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -dynamiclib "$SRC/Bootstrap.c" -install_name @rpath/TIOEarlyBootstrapInternal.dylib -o "$OUT/TIOEarlyBootstrapInternal.dylib"
xcrun nm -u "$OUT/TIOEarlyBootstrapInternal.dylib" > "$OUT/production-imports.txt"
xcrun nm -gU "$OUT/TIOEarlyBootstrapInternal.dylib" > "$OUT/production-exports.txt"
python3 - "$OUT" <<'RECEIPT'
from pathlib import Path
import json,hashlib,sys,platform,time
out=Path(sys.argv[1]);manifest=json.loads((out/'source-manifest.json').read_text())
for e in manifest['files']:
 p=out/'source'/e['path'].split('/')[-1];assert hashlib.sha256(p.read_bytes()).hexdigest()==e['sha256']
imports=(out/'production-imports.txt').read_text()
for forbidden in ['malloc','calloc','objc','Foundation','dlsym','mprotect','vm_read','compare_exchange']:assert forbidden not in imports
r={'host_native_isa':platform.machine(),'native_fixture_sanitizer':__import__('os').environ.get('TIO_BOOTSTRAP_SANITIZE','none'),'own_loader_constructor_order_executed':True,'own_fixture_success_before_main_ffi':True,'own_fixture_early_flutter_constructor_ffi_blocked':True,'production_status':'BLOCKED_EARLY_RUNTIME_ENTRY','production_bundle_identity_verified':False,'official_slot_read':False,'official_slot_write':False,'official_engine_executed':False,'actual_observation':False,'ios_arm64_compile':True,'ios_arm64_runtime':False,'source_manifest_sha256':hashlib.sha256((out/'source-manifest.json').read_bytes()).hexdigest(),'module_sha256':hashlib.sha256((out/'TIOEarlyBootstrapInternal.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review_status':'UNREVIEWED_NEW_BUILD'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
RECEIPT
printf 'BOOTSTRAP_OWN_LOADER_FOUNDATION_PASS\n'
