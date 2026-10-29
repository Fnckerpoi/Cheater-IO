import hashlib,json,pathlib,sys,zipfile
root=pathlib.Path(__file__).resolve().parents[2]
out=root/'Agents-Anywhere/android/app/build/outputs/embedded'
apk=out/'aa-embedded-payload.apk'
sidecar=out/'payload.json'
data=apk.read_bytes()
digest=hashlib.sha256(data).hexdigest()
doc=json.loads(sidecar.read_text(encoding='utf-8'))
print('apk_bytes',len(data))
print('apk_sha256',digest)
print('sidecar_fields',sorted(doc.keys()))
print('sidecar',json.dumps(doc,ensure_ascii=False))
assert sorted(doc.keys())==['abiVersion','entry','schemaVersion','sha256'], doc
assert doc['schemaVersion']==1 and doc['abiVersion']==1
assert doc['entry']=='com.agentsanywhere.embedded.PayloadEntry'
assert doc['sha256']==digest, (doc['sha256'],digest)
with zipfile.ZipFile(apk) as z:
 names=z.namelist()
 print('members',len(names),'unique',len(set(names)))
 assert len(names)==len(set(names))
 assert 'resources.arsc' in names and 'classes.dex' in names
 native=[n for n in names if n.startswith('lib/') and n.endswith('.so')]
 print('native_libs',native)
 print('dex_files',[n for n in names if n.endswith('.dex')])
print('OK')
