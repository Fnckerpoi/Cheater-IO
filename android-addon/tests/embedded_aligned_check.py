import hashlib,json,pathlib,re,sys,zipfile
root=pathlib.Path(__file__).resolve().parents[2]
target=root/(sys.argv[1] if len(sys.argv)>1 else 'artifacts/embedded-aa-build-20261026/TurboIO-AA-embedded-aligned-unsigned.apk')
host=root/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
PAYLOAD='assets/turboio/aa/aa-embedded-payload.apk'
SIDECAR='assets/turboio/aa/payload.json'
def sha(b):return hashlib.sha256(b).hexdigest()
def signature(name):return bool(re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',name,re.I))
with zipfile.ZipFile(host) as a, zipfile.ZipFile(target) as b:
 an=a.namelist();bn=b.namelist()
 dropped=sorted(set(an)-set(bn))
 print('target',str(target.relative_to(root)))
 print('host_members',len(an),'output_members',len(bn))
 print('dropped_signature_members',dropped)
 assert all(signature(n) for n in dropped), dropped
 assert sorted(set(bn)-set(an))==sorted([PAYLOAD,SIDECAR]), sorted(set(bn)-set(an))
 unchanged=0;changed=[]
 for name in an:
  if signature(name):continue
  if name in ('AndroidManifest.xml','classes4.dex'):changed.append(name);continue
  if a.read(name)==b.read(name):unchanged+=1
  else:raise SystemExit('member changed unexpectedly: '+name)
 print('changed_members',sorted(changed))
 print('byte_identical_members',unchanged)
 assert sorted(changed)==['AndroidManifest.xml','classes4.dex']
 print('resources_arsc_identical',a.read('resources.arsc')==b.read('resources.arsc'))
 print('resources_arsc_sha256',sha(b.read('resources.arsc')))
 payload=b.read(PAYLOAD);sidecar=json.loads(b.read(SIDECAR))
 print('embedded_payload_sha256',sha(payload))
 print('sidecar_matches_payload',sidecar['sha256']==sha(payload))
 print('sidecar',json.dumps(sidecar,ensure_ascii=False))
 import io
 with zipfile.ZipFile(io.BytesIO(payload)) as p:
  native=[n for n in p.namelist() if n.startswith('lib/') and n.endswith('.so')]
  print('payload_native_libs',native)
  assert not native
print('OK')
