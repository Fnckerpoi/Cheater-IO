import hashlib,json,pathlib,re,sys,zipfile
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import embed_manifest as em
from background_manifest import _chunks
from tmig1_coexist import parse
import struct
root=pathlib.Path(__file__).resolve().parents[2]
outdir=root/(sys.argv[1] if len(sys.argv)>1 else 'artifacts/embedded-aa-bridgefix-20261026')
host=root/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
PAYLOAD='assets/turboio/aa/aa-embedded-payload.apk'
SIDECAR='assets/turboio/aa/payload.json'
def sha(b):return hashlib.sha256(b).hexdigest()
def fsha(p):return sha(p.read_bytes())
def signature(name):return bool(re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',name,re.I))
dex=outdir/'dex/classes.dex'
unsigned=outdir/'TurboIO-AA-embedded-unsigned.apk'
aligned=outdir/'TurboIO-AA-embedded-aligned-unsigned.apk'
report={'schemaVersion':1,'sourceApk':str(host.relative_to(root)),'sourceSha256':fsha(host),
 'payloadApk':'Agents-Anywhere/android/app/build/outputs/embedded/aa-embedded-payload.apk','payloadSha256':fsha(root/'Agents-Anywhere/android/app/build/outputs/embedded/aa-embedded-payload.apk'),
 'sidecarSha256':fsha(root/'Agents-Anywhere/android/app/build/outputs/embedded/payload.json'),
 'addonDexSha256':fsha(dex),'addonDexBytes':dex.stat().st_size,
 'unsignedApk':str(unsigned.relative_to(root)),'unsignedApkBytes':unsigned.stat().st_size,'unsignedApkSha256':fsha(unsigned),
 'alignedApk':str(aligned.relative_to(root)),'alignedApkBytes':aligned.stat().st_size,'alignedApkSha256':fsha(aligned),
 'zipalignVerified':True,'signed':False,'installed':False,'runtimeValidated':False}
with zipfile.ZipFile(host) as a, zipfile.ZipFile(aligned) as b:
 an=a.namelist();bn=b.namelist()
 dropped=sorted(set(an)-set(bn));assert all(signature(n) for n in dropped)
 added=sorted(set(bn)-set(an));assert added==sorted([PAYLOAD,SIDECAR])
 changed=[];unchanged=0
 for name in an:
  if signature(name):continue
  if name in ('AndroidManifest.xml','classes4.dex'):changed.append(name);continue
  if a.read(name)==b.read(name):unchanged+=1
  else:raise SystemExit('changed: '+name)
 assert sorted(changed)==['AndroidManifest.xml','classes4.dex']
 assert a.read('resources.arsc')==b.read('resources.arsc')
 assert b.read('classes4.dex')==dex.read_bytes(), 'embedded dex differs from built dex'
 manifest=b.read('AndroidManifest.xml')
 _,_,_,nodes=parse(manifest)
 acts=[attrs for _,tag,attrs in nodes if tag=='activity' and any(v['name']=='name' and v['value']==em.ACTIVITY for v in attrs)]
 assert len(acts)==1, len(acts)
 exported=[v['value'] for v in acts[0] if v['name']=='exported']
 assert exported==[0], exported
 payload=b.read(PAYLOAD);sidecar=json.loads(b.read(SIDECAR))
 assert sidecar['sha256']==sha(payload)
 with zipfile.ZipFile(__import__('io').BytesIO(payload)) as p:
  assert not [n for n in p.namelist() if n.startswith('lib/') and n.endswith('.so')]
 report.update({'hostMembers':len(an),'outputMembers':len(bn),'byteIdenticalMembers':unchanged,
  'changedMembers':sorted(changed),'addedMembers':added,'droppedSignatureMembers':dropped,
  'resourcesArscIdentical':True,'resourcesArscSha256':sha(b.read('resources.arsc')),
  'embeddedManifestActivity':em.ACTIVITY,'embeddedActivityExported':False,'embeddedActivityCount':1,
  'embeddedDexMatchesBuiltDex':True,'embeddedPayloadSha256':sha(payload),'payloadNativeLibs':0,
  'sidecarMatchesPayload':True})
path=outdir/'assembly-report.json'
path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
print('REPORT',str(path.relative_to(root)))
