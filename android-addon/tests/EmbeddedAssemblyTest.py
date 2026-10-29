import importlib.util,io,json,pathlib,struct,sys,tempfile,unittest,zipfile
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import embed_manifest as manifest
import background_manifest as ax
from tmig1_coexist import parse
spec=importlib.util.spec_from_file_location('assembly',pathlib.Path(__file__).resolve().parents[1]/'package-embedded.py')
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
class EmbeddedAssemblyTest(unittest.TestCase):
 def test_manifest_preserves_host_nodes(self):
  root=pathlib.Path(__file__).resolve().parents[2]
  with zipfile.ZipFile(root/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk') as z:before=z.read('AndroidManifest.xml')
  after=manifest.patch(before);self.assertEqual(manifest.patch(after),after)
  old=[c for c in ax._chunks(before) if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
  new=[c for c in ax._chunks(after) if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
  it=iter(new)
  for c in old:self.assertTrue(any(v==c for v in it))
  self.assertEqual(len(new),len(old)+2)
  _,_,_,nodes=parse(after)
  activities=[attrs for _,tag,attrs in nodes if tag=='activity' and any(v['name']=='name' and v['value']==manifest.ACTIVITY for v in attrs)]
  self.assertEqual(len(activities),1)
  self.assertTrue(any(v['name']=='exported' and not v['value'] for v in activities[0]))
 def test_assembled_manifest_includes_exact_oauth_callback(self):
  root=pathlib.Path(__file__).resolve().parents[2]
  with zipfile.ZipFile(root/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk') as z:before=z.read('AndroidManifest.xml')
  after=a.patch(before)
  self.assertEqual(a.patch(after),after)
  import dsh_manifest
  self.assertEqual(dsh_manifest.patch(after),after)
  _,_,_,nodes=parse(after)
  callbacks=[attrs for _,tag,attrs in nodes if tag=='activity' and any(v['name']=='name' and v['value']==dsh_manifest.ACTIVITY for v in attrs)]
  self.assertEqual(len(callbacks),1)
  self.assertTrue(any(v['name']=='exported' and v['value'] for v in callbacks[0]))
  targets=[attrs for _,tag,attrs in nodes if tag=='data' and any(v['name']=='scheme' and v['value']=='agents-anywhere' for v in attrs)]
  self.assertEqual(len(targets),1)
  actual={v['name']:v['value'] for v in targets[0]}
  self.assertEqual(actual.get('host'),'oauth')
  self.assertEqual(actual.get('path'),'/callback')
  self.assertTrue(any(tag=='category' and any(v['name']=='name' and v['value']=='android.intent.category.BROWSABLE' for v in attrs) for _,tag,attrs in nodes))
 def test_payload_hash_and_native_rejection(self):
  def payload(native=False):
   b=io.BytesIO()
   with zipfile.ZipFile(b,'w') as z:
    z.writestr('classes.dex',b'dex\n');z.writestr('resources.arsc',b'fixture')
    if native:z.writestr('lib/arm64-v8a/native.so',b'fixture')
   return b.getvalue()
  p=payload();s=json.dumps(dict(schemaVersion=1,abiVersion=1,entry=a.ENTRY,sha256=a.digest(p))).encode()
  a.validate_payload(p,s)
  with self.assertRaises(ValueError):a.validate_payload(p+b'corrupt',s)
  p=payload(True);s=json.dumps(dict(schemaVersion=1,abiVersion=1,entry=a.ENTRY,sha256=a.digest(p))).encode()
  with self.assertRaises(ValueError):a.validate_payload(p,s)
 def test_pin_failure_does_not_create_output(self):
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);source=root/'source.apk';source.write_bytes(b'wrong');output=root/'result.apk'
   with self.assertRaises(ValueError):a.assemble(source,'missing','missing','missing',output)
   self.assertFalse(output.exists())
if __name__=='__main__':unittest.main()
