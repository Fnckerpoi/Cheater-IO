#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/integration-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib
r=Path.cwd();out=Path(sys.argv[1]);base=r.parent.parent;fixed=Path('/private/tmp/task97-capture-start-r1/source');files={}
for f in ['Integration.c','Integration.h','Selector.S','OwnFlutter.c','OwnEngine.c','Runner.c','OwnLazyPolicy.py','build-integration.sh']:files['final-build/runtime-capture-bootstrap-v2/'+f]=r/f
for f in ['Capture.c','Capture.h','CaptureInternal.h','SchemaPolicy.h','SchemaPolicy.c']:files['final-build/schema-capture/'+f]=fixed/'final-build/schema-capture'/f
for d,names in [('resolver-slot-policy',['ResolverSlot.c','ResolverSlot.h'])]:
 for f in names:files['final-build/'+d+'/'+f]=base/'final-build'/d/f
for f in ['ResolverBridge.c','ResolverBridge.h','ResolverBridge.S']:files['manuscript-lab/resolver-bridge/'+f]=base/'manuscript-lab/resolver-bridge'/f
for d,f in [('schema-observer','SchemaView.h'),('schema-observer','SchemaView.c'),('memory-copy','MemoryCopy.h'),('memory-copy','MemoryCopy.c')]:files['manuscript-lab/'+d+'/'+f]=fixed/'manuscript-lab'/d/f
entries=[]
for name,p in files.items():
 data=p.read_bytes();q=out/'source'/name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(data);entries.append({'path':name,'sha256':hashlib.sha256(data).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_private_snapshot':True,'capture_input':'task97 immutable R1, no live inputs'},indent=2))
PY
SRC="$OUT/source/final-build/runtime-capture-bootstrap-v2"
CAP="$OUT/source/final-build/schema-capture"
POL="$OUT/source/final-build/resolver-slot-policy"
BR="$OUT/source/manuscript-lab/resolver-bridge"
FLAGS=(-std=c11 -Wall -Wextra -Werror -I"$CAP" -I"$POL" -I"$BR")
clang "${FLAGS[@]}" -O1 -dynamiclib -Wl,-no_fixup_chains "$SRC/OwnFlutter.c" -install_name "$OUT/OwnFlutter.dylib" -o "$OUT/OwnFlutter.dylib"
clang "${FLAGS[@]}" -dynamiclib "$SRC/OwnEngine.c" -install_name "$OUT/OwnEngine.dylib" -o "$OUT/OwnEngine.dylib"
python3 "$SRC/OwnLazyPolicy.py" "$OUT/OwnFlutter.dylib" >"$OUT/own-slot.json"
python3 - "$OUT" <<'PY'
from pathlib import Path
import json,struct,sys
out=Path(sys.argv[1]);slot=json.loads((out/'own-slot.json').read_text());b=(out/'OwnEngine.dylib').read_bytes();h=struct.unpack_from('<8I',b);o=32;uuid=None;sym=None
for _ in range(h[4]):
 cmd,n=struct.unpack_from('<II',b,o)
 if cmd==0x1b:uuid=b[o+8:o+24]
 if cmd==2:sym=struct.unpack_from('<4I',b,o+8)
 o+=n
assert sym and uuid;strings=b[sym[2]:sym[2]+sym[3]];matches=[]
for j in range(sym[1]):
 strx,ty,sec,desc,value=struct.unpack_from('<IBBHQ',b,sym[0]+j*16)
 if strings[strx:].split(bytes([0]),1)[0]==b'_isar_open_instance' and ty&0xe==0xe:matches.append(value)
assert len(matches)==1
text=''
for key,val in [('FLUTTER_PATH',str(out/'OwnFlutter.dylib')),('ENGINE_PATH',str(out/'OwnEngine.dylib'))]:text+='#define TIO_OWN_'+key+' '+json.dumps(val)+'\n'
text+='#define TIO_OWN_SLOT '+str(slot['slot_va'])+'\n#define TIO_OWN_ENTRY '+str(matches[0])+'\n'
for key,data in [('FLUTTER_UUID',bytes.fromhex(slot['uuid'])),('ENGINE_UUID',uuid)]:text+='#define TIO_OWN_'+key+' {'+','.join(str(v)for v in data)+'}\n'
p=out/'source/final-build/runtime-capture-bootstrap-v2/OwnPolicy.h';p.write_text(text+'#define TIO_OWN_RUNNER_UUID {0}\n#define TIO_OWN_RUNNER_PATH \"unconfigured\"\n#define TIO_OWN_INFO \"unconfigured\"\n')
m=json.loads((out/'source-manifest.json').read_text());m['files'].append({'path':'final-build/runtime-capture-bootstrap-v2/OwnPolicy.h','sha256':__import__('hashlib').sha256(p.read_bytes()).hexdigest(),'generated_fixture_only':True});(out/'source-manifest.json').write_text(json.dumps(m,indent=2))
PY
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 for CASE in normal early bundle uuid pointer nostart malformed exists rootmode rootlink info; do
  ROOT="$OUT/public-$MODE-$CASE.app"
  mkdir -m 700 "$ROOT"
  printf '%s' '<plist><dict><key>CFBundleIdentifier</key><string>local.public.schema.fixture</string></dict></plist>' >"$ROOT/Info.plist"
  compile_adapter(){ clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_INTEGRATION_FIXTURE -dynamiclib "$SRC/Integration.c" "$SRC/Selector.S" "$CAP/Capture.c" "$CAP/SchemaPolicy.c" "$OUT/source/manuscript-lab/schema-observer/SchemaView.c" "$OUT/source/manuscript-lab/memory-copy/MemoryCopy.c" "$POL/ResolverSlot.c" "$BR/ResolverBridge.c" "$BR/ResolverBridge.S" -Wl,-needed_library,"$OUT/OwnFlutter.dylib" -Wl,-needed_library,"$OUT/OwnEngine.dylib" -install_name "$OUT/OwnIntegration-$MODE-$CASE.dylib" -o "$OUT/OwnIntegration-$MODE-$CASE.dylib"; }
  compile_adapter
  clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/Runner.c" "$OUT/OwnFlutter.dylib" "$OUT/OwnEngine.dylib" "$OUT/OwnIntegration-$MODE-$CASE.dylib" -o "$ROOT/Runner"
  python3 - "$OUT" "$ROOT" "$MODE-$CASE" <<'POLICY'
from pathlib import Path
import struct,json,sys
out,root=Path(sys.argv[1]),Path(sys.argv[2]);b=(root/'Runner').read_bytes();h=struct.unpack_from('<8I',b);o=32;uuid=None
for _ in range(h[4]):
 cmd,n=struct.unpack_from('<II',b,o)
 if cmd==0x1b:uuid=b[o+8:o+24]
 o+=n
assert uuid;p=out/'source/final-build/runtime-capture-bootstrap-v2/OwnPolicy.h';text='\n'.join(l for l in p.read_text().splitlines() if not any(k in l for k in ['RUNNER_UUID','RUNNER_PATH','OWN_INFO']))+'\n';text+='#define TIO_OWN_RUNNER_UUID {'+','.join(str(v)for v in uuid)+'}\n#define TIO_OWN_RUNNER_PATH '+json.dumps(str(root/'Runner'))+'\n#define TIO_OWN_INFO '+json.dumps((root/'Info.plist').read_text())+'\n';p.write_text(text);(p.parent/('OwnPolicy-'+sys.argv[3]+'.h')).write_text(text)
POLICY
  compile_adapter
  python3 - "$ROOT" "$CASE" <<'SETUP'
from pathlib import Path
import sys
root=Path(sys.argv[1]);case=sys.argv[2]
if case in ['exists','rootmode']:
 lab=root/'Documents/ManuscriptLab';lab.mkdir(parents=True,mode=0o700);lab.chmod(0o700 if case=='exists' else 0o770)
 if case=='exists':(lab/'captured_schema_projection.json').write_bytes(b'PREEXISTING_PUBLIC_SENTINEL')
if case=='rootlink':(root/'Documents').symlink_to(root.parent)
if case=='info':(root/'Info.plist').write_text('wrong public fixture identity')
SETUP
  ASAN_OPTIONS=detect_leaks=0 TIO_INTEGRATION_MODE="$CASE" "$ROOT/Runner" >"$OUT/$MODE-$CASE.log" 2>&1
  python3 - "$ROOT" "$CASE" <<'VERIFY'
from pathlib import Path
import json,stat,sys
root=Path(sys.argv[1]);case=sys.argv[2];file=root/'Documents/ManuscriptLab/captured_schema_projection.json'
if case=='normal':
 data=file.read_bytes();o=json.loads(data);assert len(data)<=65536 and o['schema']==[{'name':'OwnPublicSchema','properties':[]}] and o['actual_manuscript_schema_known'] is False;assert stat.S_IMODE(file.stat().st_mode)==0o600 and stat.S_IMODE(file.parent.stat().st_mode)==0o700
elif case=='exists':assert file.read_bytes()==b'PREEXISTING_PUBLIC_SENTINEL'
else:assert not file.exists()
if case=='rootmode':assert stat.S_IMODE(file.parent.stat().st_mode)==0o770
print('ACTUAL_FILESYSTEM_CAPTURE_STATE_VERIFIED',case)
VERIFY
 done
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -dynamiclib "$SRC/Integration.c" -install_name @rpath/TIORuntimeIntegrationBlocked.dylib -o "$OUT/TIORuntimeIntegrationBlocked.dylib"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib,time
out=Path(sys.argv[1]);raw=(out/'source-manifest.json').read_bytes();m=json.loads(raw)
for e in m['files']:
 if e.get('generated_fixture_only'):e['sha256']=hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()
for p in (out/'source/final-build/runtime-capture-bootstrap-v2').glob('OwnPolicy-*.h'):m['files'].append({'path':str(p.relative_to(out/'source')),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'actual_compiled_variant':True})
raw=json.dumps(m,indent=2).encode();(out/'source-manifest.json').write_bytes(raw)
for e in m['files']:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
r={'status':'OWN_FIXTURE_QUALIFIED_START_CAPTURE_FILE','production_status':'BLOCKED_EARLY_RUNTIME_ENTRY','host_actual_own_linker_lookup_bind':True,'own_fixture_start':True,'own_fixture_capture_file_written':True,'production_capture_grant':False,'official_slot_read':False,'official_slot_write':False,'ios_arm64_compile':True,'ios_runtime':False,'task97_called_in_own_fixture':True,'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIORuntimeIntegrationBlocked.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review':'UNREVIEWED'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
PY
