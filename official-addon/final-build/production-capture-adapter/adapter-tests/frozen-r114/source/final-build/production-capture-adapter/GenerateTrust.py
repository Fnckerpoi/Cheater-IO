from pathlib import Path
import struct,hashlib,json,sys,plistlib
main,flutter,native,isar,info,output=map(Path,sys.argv[1:]);records=[]
def policy(path,relative,dependency,slot_required=False):
 b=path.read_bytes();h=struct.unpack_from('<8I',b);assert h[0]==0xfeedfacf;o=32;u=None;sections=[];sym=ind=None
 for _ in range(h[4]):
  cmd,n=struct.unpack_from('<II',b,o);assert n>=8 and o+n<=32+h[5]
  if cmd==0x1b:u=b[o+8:o+24]
  if cmd==2:sym=struct.unpack_from('<4I',b,o+8)
  if cmd==0xb:ind=struct.unpack_from('<2I',b,o+56)
  if cmd==0x19:
   ns=struct.unpack_from('<I',b,o+64)[0]
   for j in range(ns):sections.append(struct.unpack_from('<16s16sQQIIIIIIII',b,o+72+j*80))
  o+=n
 assert u;text=[s for s in sections if s[0].split(bytes([0]),1)[0]==b'__text'];assert len(text)==1;t=text[0];info=[s for s in sections if s[0].split(bytes([0]),1)[0]==b'__info_plist'];infova=infosize=0;infosha=bytes(32)
 if info:assert len(info)==1;sec=info[0];infova,infosize=sec[2:4];infosha=hashlib.sha256(b[sec[4]:sec[4]+sec[3]]).digest()
 slot=lazy=0
 if slot_required:
  assert sym and ind;strings=b[sym[2]:sym[2]+sym[3]];matches=[]
  for s in sections:
   if s[8]&255!=7:continue
   for j in range(s[3]//8):
    k=struct.unpack_from('<I',b,ind[0]+4*(s[9]+j))[0]
    if k&0xc0000000:continue
    assert k<sym[1];strx=struct.unpack_from('<I',b,sym[0]+k*16)[0]
    if strings[strx:].split(bytes([0]),1)[0]==b'_dlsym':matches.append((s[2]+8*j,struct.unpack_from('<Q',b,s[4]+8*j)[0]))
  assert len(matches)==1;slot,lazy=matches[0]
 def arr(x):return '{'+','.join(str(c)for c in x)+'}'
 fields=[str(h[1]),str(h[3]),arr(u),str(t[2]),str(t[3]),arr(hashlib.sha256(b[t[4]:t[4]+t[3]]).digest()),str(infova),str(infosize),arr(infosha),json.dumps(relative),str(slot),str(lazy),json.dumps(dependency)]
 records.append({'path':str(path),'sha256':hashlib.sha256(b).hexdigest(),'uuid':u.hex(),'text_va':t[2],'text_size':t[3],'slot':slot,'lazy':lazy})
 return '{'+','.join(fields)+'}'

lab=plistlib.loads(info.read_bytes());assert lab['CFBundleIdentifier'].startswith('local.public.') or lab['CFBundleIdentifier'].startswith('com.duriea.turboio.manuscriptlab105.')
def defined(path,name):
 b=path.read_bytes();h=struct.unpack_from('<8I',b);o=32;sym=None
 for _ in range(h[4]):
  cmd,n=struct.unpack_from('<II',b,o)
  if cmd==2:sym=struct.unpack_from('<4I',b,o+8)
  o+=n
 assert sym;strings=b[sym[2]:sym[2]+sym[3]];values=[]
 for j in range(sym[1]):
  strx,ty,section,desc,value=struct.unpack_from('<IBBHQ',b,sym[0]+16*j)
  if strings[strx:].split(bytes([0]),1)[0]==name and ty&0xe==0xe and ty&1:values.append(value)
 assert len(values)==1;return values[0]
main_policy=policy(main,'',str(flutter));fl_policy=policy(flutter,'Frameworks/OwnFlutter.dylib','',True);native_policy=policy(native,'Frameworks/OwnNative.dylib','');isar_policy=policy(isar,'Frameworks/OwnIsar.dylib','');entry=defined(isar,b'_isar_open_instance')
output.write_text('#define TIO_FIXED_POLICY_PRESENT 1\nstatic const TIOFixedImagePolicy MainPolicy='+main_policy+';\nstatic const TIOFixedImagePolicy FlutterPolicy='+fl_policy+';\n')
def arr(b):return '{'+','.join(str(x)for x in b)+'}'
ext='static const TIOFixedImagePolicy NativePolicy='+native_policy+';\nstatic const TIOFixedImagePolicy IsarPolicy='+isar_policy+';\nstatic const unsigned char LabInfoSHA[32]='+arr(hashlib.sha256(info.read_bytes()).digest())+';\nstatic const unsigned long LabInfoSize='+str(info.stat().st_size)+';\nstatic const unsigned long long IsarEntryVA='+str(entry)+';\nstatic const char ObserverRelative[]="Frameworks/Observer.dylib";\nstatic const char MainNativeLoad[]='+json.dumps(str(native))+';\nstatic const char FlutterObserverLoad[]='+json.dumps('@loader_path/Observer.dylib')+';\n'
output.with_name('Config-'+output.stem+'.inc').write_text(ext);output.with_suffix('.json').write_text(json.dumps({'images':records,'info_sha256':hashlib.sha256(info.read_bytes()).hexdigest(),'lab_bundle':lab['CFBundleIdentifier'],'entry':entry},indent=2))
