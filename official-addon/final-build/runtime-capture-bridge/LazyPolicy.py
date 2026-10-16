from pathlib import Path
import struct,json,hashlib,sys
p=Path(sys.argv[1]);b=p.read_bytes();h=struct.unpack_from('<8I',b);assert h[0]==0xfeedfacf
segments=[];sections=[];uuid=None;sym=None;indirect=None;o=32
for _ in range(h[4]):
 cmd,n=struct.unpack_from('<II',b,o);assert n>=8 and o+n<=32+h[5]
 if cmd==0x1b:uuid=b[o+8:o+24]
 if cmd==0x2:sym=struct.unpack_from('<4I',b,o+8)
 if cmd==0xb:indirect=struct.unpack_from('<2I',b,o+56)
 if cmd==0x19:
  raw=struct.unpack_from('<II16sQQQQiiII',b,o);segments.append(raw)
  for j in range(raw[9]):sections.append(struct.unpack_from('<16s16sQQIIIIIIII',b,o+72+j*80))
 o+=n
assert uuid and sym and indirect
strings=b[sym[2]:sym[2]+sym[3]];matches=[]
for s in sections:
 if s[8]&255!=7:continue
 for j in range(s[3]//8):
  k=struct.unpack_from('<I',b,indirect[0]+4*(s[9]+j))[0]
  if k&0xc0000000:continue
  assert k<sym[1];strx=struct.unpack_from('<I',b,sym[0]+16*k)[0];name=strings[strx:].split(b'\0',1)[0]
  if name==b'_dlsym':
   va=s[2]+8*j;off=s[4]+8*j;helper=struct.unpack_from('<Q',b,off)[0];matches.append((va,off,helper))
assert len(matches)==1,matches
va,off,helper=matches[0]
def fileoff(address):
 for s in segments:
  if s[3]<=address<s[3]+s[6]:return s[5]+address-s[3]
 raise AssertionError('no file mapping')
helper_sections=[s for s in sections if s[0].split(b'\0',1)[0]==b'__stub_helper' and s[2]<=helper<s[2]+s[3]];assert len(helper_sections)==1
helperoff=fileoff(helper);code=b[helperoff:helperoff+12]
r={'path':str(p.resolve()),'sha256':hashlib.sha256(b).hexdigest(),'uuid':uuid.hex(),'slot_va':va,'slot_fileoff':off,'initial_lazy_helper_va':helper,'helper_fileoff':helperoff,'helper_code_hex':code.hex(),'helper_12_sha256':hashlib.sha256(code).hexdigest(),'helper_inside_stub_helper':True}
print(json.dumps(r,indent=2))
