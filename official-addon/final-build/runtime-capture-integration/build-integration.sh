#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/integration-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib
r=Path.cwd();out=Path(sys.argv[1]);base=r.parent.parent;fixed=Path('/private/tmp/task93-capture-bind-r1/source');files={}
for f in ['Integration.c','Integration.h','Selector.S','OwnFlutter.c','OwnEngine.c','Runner.c','OwnLazyPolicy.py','build-integration.sh']:files['final-build/runtime-capture-integration/'+f]=r/f
for f in ['Capture.c','Capture.h','CaptureInternal.h','SchemaPolicy.h']:files['final-build/schema-capture/'+f]=fixed/'final-build/schema-capture'/f
for d,names in [('resolver-slot-policy',['ResolverSlot.c','ResolverSlot.h'])]:
 for f in names:files['final-build/'+d+'/'+f]=base/'final-build'/d/f
for f in ['ResolverBridge.c','ResolverBridge.h','ResolverBridge.S']:files['manuscript-lab/resolver-bridge/'+f]=base/'manuscript-lab/resolver-bridge'/f
for d,f in [('schema-observer','SchemaView.h'),('memory-copy','MemoryCopy.h')]:files['manuscript-lab/'+d+'/'+f]=fixed/'manuscript-lab'/d/f
entries=[]
for name,p in files.items():
 data=p.read_bytes();q=out/'source'/name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(data);entries.append({'path':name,'sha256':hashlib.sha256(data).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps({'files':entries,'compiled_private_snapshot':True,'capture_input':'task93 immutable R1, not live task97'},indent=2))
PY
SRC="$OUT/source/final-build/runtime-capture-integration"
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
p=out/'source/final-build/runtime-capture-integration/OwnPolicy.h';p.write_text(text)
m=json.loads((out/'source-manifest.json').read_text());m['files'].append({'path':'final-build/runtime-capture-integration/OwnPolicy.h','sha256':__import__('hashlib').sha256(p.read_bytes()).hexdigest(),'generated_fixture_only':True});(out/'source-manifest.json').write_text(json.dumps(m,indent=2))
PY
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 clang "${FLAGS[@]}" "${SAN[@]}" -DTIO_INTEGRATION_FIXTURE -dynamiclib "$SRC/Integration.c" "$SRC/Selector.S" "$CAP/Capture.c" "$POL/ResolverSlot.c" "$BR/ResolverBridge.c" "$BR/ResolverBridge.S" -Wl,-needed_library,"$OUT/OwnFlutter.dylib" -Wl,-needed_library,"$OUT/OwnEngine.dylib" -install_name "$OUT/OwnIntegration-$MODE.dylib" -o "$OUT/OwnIntegration-$MODE.dylib"
 clang "${FLAGS[@]}" "${SAN[@]}" "$SRC/Runner.c" "$OUT/OwnFlutter.dylib" "$OUT/OwnEngine.dylib" "$OUT/OwnIntegration-$MODE.dylib" -o "$OUT/Runner-$MODE"
 for CASE in normal early bundle uuid pointer; do
  ASAN_OPTIONS=detect_leaks=0 TIO_INTEGRATION_MODE="$CASE" "$OUT/Runner-$MODE" >"$OUT/$MODE-$CASE.log" 2>&1
 done
done
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -dynamiclib "$SRC/Integration.c" -install_name @rpath/TIORuntimeIntegrationBlocked.dylib -o "$OUT/TIORuntimeIntegrationBlocked.dylib"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib,time
out=Path(sys.argv[1]);raw=(out/'source-manifest.json').read_bytes();m=json.loads(raw)
for e in m['files']:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
r={'status':'WAITING_CAPTURE_IMPLEMENTATION','production_status':'BLOCKED_EARLY_RUNTIME_ENTRY','host_actual_own_linker_lookup_bind':True,'capture_grant':False,'capture_file_written':False,'official_slot_read':False,'official_slot_write':False,'ios_arm64_compile':True,'ios_runtime':False,'task97_called':False,'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'module_sha256':hashlib.sha256((out/'TIORuntimeIntegrationBlocked.dylib').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review':'UNREVIEWED'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
PY
