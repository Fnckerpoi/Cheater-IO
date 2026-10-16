#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
OUT=$(mktemp -d "$PWD/build/policy-XXXXXX")
printf '%s\n' "$OUT" >build/latest-output.txt
mkdir "$OUT/source"
for F in ResolverSlot.h ResolverSlot.c Tests.c OwnLookup.c Counterfeit.c build-policy.sh; do cp "$F" "$OUT/source/$F"; done
SRC="$OUT/source"
clang -std=c11 -O1 -Wall -Wextra -Werror -dynamiclib -Wl,-no_fixup_chains "$SRC/OwnLookup.c" -install_name "$OUT/OwnLookup.dylib" -o "$OUT/OwnLookup.dylib"
python3 - "$OUT" <<'PY'
from pathlib import Path
import struct,sys,json,hashlib
out=Path(sys.argv[1]);b=(out/'OwnLookup.dylib').read_bytes();h=struct.unpack_from('<8I',b);assert h[0]==0xfeedfacf;o=32;sections=[];sym=None;ind=None
for _ in range(h[4]):
 cmd,n=struct.unpack_from('<II',b,o);assert n>=8 and o+n<=32+h[5]
 if cmd==2:sym=struct.unpack_from('<4I',b,o+8)
 if cmd==0xb:ind=struct.unpack_from('<2I',b,o+56)
 if cmd==0x19:
  ns=struct.unpack_from('<I',b,o+64)[0]
  for j in range(ns):sections.append(struct.unpack_from('<16s16sQQIIIIIIII',b,o+72+j*80))
 o+=n
assert sym and ind;strings=b[sym[2]:sym[2]+sym[3]];matches=[]
for s in sections:
 if s[8]&255!=7:continue
 for j in range(s[3]//8):
  k=struct.unpack_from('<I',b,ind[0]+4*(s[9]+j))[0]
  if k&0xc0000000:continue
  strx=struct.unpack_from('<I',b,sym[0]+16*k)[0]
  if strings[strx:].split(bytes([0]),1)[0]==b'_dlsym':matches.append((s[2]+j*8,struct.unpack_from('<Q',b,s[4]+j*8)[0]))
assert len(matches)==1;slot,helper=matches[0];(out/'source/OwnPolicy.h').write_text(f'#define TIO_OWN_SLOT {slot}\n#define TIO_OWN_HELPER {helper}\n');(out/'own-policy.json').write_text(json.dumps({'slot':slot,'raw_helper':helper,'dylib_sha256':hashlib.sha256(b).hexdigest()},indent=2))
PY
clang -std=c11 -Wall -Wextra -Werror -dynamiclib "$SRC/Counterfeit.c" -o "$OUT/Counterfeit.dylib"
for MODE in normal asan ubsan; do
 SAN=(-fno-common)
 if [ "$MODE" = asan ]; then SAN=(-fsanitize=address -fno-omit-frame-pointer); fi
 if [ "$MODE" = ubsan ]; then SAN=(-fsanitize=undefined -fno-sanitize-recover=all); fi
 clang -std=c11 -Wall -Wextra -Werror "${SAN[@]}" "$SRC/ResolverSlot.c" "$SRC/Tests.c" "$OUT/OwnLookup.dylib" -o "$OUT/test-$MODE"
 ASAN_OPTIONS=detect_leaks=0 "$OUT/test-$MODE" >"$OUT/$MODE.log" 2>&1
done
DYLD_INSERT_LIBRARIES="$OUT/Counterfeit.dylib" "$OUT/test-normal" counterfeit >"$OUT/counterfeit.log" 2>&1
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -c "$SRC/ResolverSlot.c" -o "$OUT/ResolverSlot-arm64.o"
xcrun nm -u "$OUT/ResolverSlot-arm64.o" >"$OUT/imports.txt"
python3 - "$OUT" <<'PY'
from pathlib import Path
import json,hashlib,sys,time
out=Path(sys.argv[1]);entries=[]
for p in sorted((out/'source').iterdir()):entries.append({'path':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
raw=json.dumps({'files':entries,'actual_compiled_private_snapshot':True},indent=2).encode();(out/'source-manifest.json').write_bytes(raw)
r={'status':'HOST_RESOLVED_IMPORT_PROVEN_NOT_SLOT_INSTALL_AUTHORITY','ios_runtime':False,'ios_arm64_compile':True,'no_lazy_acceptance':True,'official_slot_read':False,'official_slot_write':False,'classifier_calls_dlsym':False,'classifier_calls_dlerror':False,'loader_lock_safety_proven':False,'source_manifest_sha256':hashlib.sha256(raw).hexdigest(),'arm64_object_sha256':hashlib.sha256((out/'ResolverSlot-arm64.o').read_bytes()).hexdigest(),'freeze_epoch':int(time.time()),'review_status':'UNREVIEWED'};(out/'receipt.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
PY
