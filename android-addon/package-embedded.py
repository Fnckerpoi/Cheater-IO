"""Independent pinned PUBLIC-host assembly; never relax the existing updater."""
import argparse,hashlib,json,pathlib,re,zipfile
from embed_manifest import patch as patch_workbench
from dsh_manifest import patch as patch_oauth
from cleartext_manifest import patch as patch_cleartext

def patch(data):
 return patch_cleartext(patch_workbench(patch_oauth(data)))
PIN='d39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0'
PAYLOAD='assets/turboio/aa/aa-embedded-payload.apk'
SIDECAR='assets/turboio/aa/payload.json'
ENTRY='com.agentsanywhere.embedded.PayloadEntry'
def digest(data):return hashlib.sha256(data).hexdigest()
def signature(name):return bool(re.fullmatch(r'META-INF/(?:MANIFEST.MF|[^/]+.(?:SF|RSA|DSA|EC))',name,re.I))
def validate_payload(payload,sidecar):
 doc=json.loads(sidecar)
 if doc!={'schemaVersion':1,'abiVersion':1,'entry':ENTRY,'sha256':digest(payload)}:raise ValueError('payload sidecar mismatch')
 import io
 with zipfile.ZipFile(io.BytesIO(payload)) as apk:
  names=apk.namelist()
  if len(names)!=len(set(names)):raise ValueError('duplicate payload members')
  if 'resources.arsc' not in names or 'classes.dex' not in names:raise ValueError('missing payload resources/dex')
  if any(n.startswith('lib/') and n.endswith('.so') for n in names):raise ValueError('native payload libraries unsupported')
 return doc
def assemble(source,dex,payload,sidecar,output):
 source=pathlib.Path(source);output=pathlib.Path(output)
 if output.exists():raise ValueError('output exists; choose a new candidate path')
 original=source.read_bytes()
 if digest(original)!=PIN:raise ValueError('host pin mismatch')
 addon=pathlib.Path(dex).read_bytes();p=pathlib.Path(payload).read_bytes();s=pathlib.Path(sidecar).read_bytes()
 if not addon.startswith(b'dex\n'):raise ValueError('invalid addon dex')
 validate_payload(p,s)
 with zipfile.ZipFile(source) as src:
  names=src.namelist()
  if len(names)!=len(set(names)) or PAYLOAD in names or SIDECAR in names or names.count('classes4.dex')!=1:raise ValueError('host inventory mismatch')
  changes={'classes4.dex':addon,'AndroidManifest.xml':patch(src.read('AndroidManifest.xml')),PAYLOAD:p,SIDECAR:s}
  with zipfile.ZipFile(output,'x',allowZip64=True) as dst:
   for info in src.infolist():
    if not signature(info.filename):dst.writestr(info,changes.get(info.filename,src.read(info)))
   for name in (PAYLOAD,SIDECAR):dst.writestr(name,changes[name],compress_type=zipfile.ZIP_DEFLATED)
 expected={n for n in names if not signature(n)}|{PAYLOAD,SIDECAR}
 with zipfile.ZipFile(source) as src, zipfile.ZipFile(output) as dst:
  produced=dst.namelist()
  if set(produced)!=expected or len(produced)!=len(expected):raise ValueError('output inventory mismatch')
  for name in sorted(expected):
   wanted=changes[name] if name in changes else src.read(name)
   if dst.read(name)!=wanted:raise ValueError('payload changed: '+name)
  unchanged=sorted(n for n in expected if n not in changes)
 report={'schemaVersion':1,'sourceSha256':digest(original),'outputSha256':digest(output.read_bytes()),'payloadSha256':digest(p),'sidecarSha256':digest(s),'addonSha256':digest(addon),'changed':['AndroidManifest.xml','classes4.dex'],'added':[PAYLOAD,SIDECAR],'unchangedMemberCount':len(unchanged),'otherPayloadsIdentical':True,'installed':False,'runtimeValidated':False}
 return report
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('source');p.add_argument('dex');p.add_argument('payload');p.add_argument('sidecar');p.add_argument('output');a=p.parse_args()
 report=assemble(a.source,a.dex,a.payload,a.sidecar,a.output)
 pathlib.Path(a.output+'.report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(report,indent=2))
