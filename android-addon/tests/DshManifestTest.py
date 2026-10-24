import pathlib,sys,unittest,zipfile,struct
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import dsh_manifest as d
import background_manifest as ax
from tmig1_coexist import parse
class DshManifestTest(unittest.TestCase):
 def test_real_manifest(self):
  root=pathlib.Path(__file__).resolve().parents[2]
  with zipfile.ZipFile(root/'artifacts/android-rayneo-device-107-extract/base.apk') as apk:original=apk.read('AndroidManifest.xml')
  before=ax.patch(original);after=d.patch(before)
  self.assertEqual(d.patch(after),after)
  self.assertEqual(d.shared_patch(original),after)
  b=ax._chunks(before);a=ax._chunks(after)
  # Every old XML element is byte-identical and ordered; only pool/map may grow.
  old=[c for c in b if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
  new=[c for c in a if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
  it=iter(new)
  for c in old:self.assertTrue(any(candidate==c for candidate in it))
  _,_,_,nodes=parse(after)
  self.assertEqual(sum(tag=='activity' and any(v['name']=='name' and v['value']==d.ACTIVITY for v in attrs) for _,tag,attrs in nodes),1)
  self.assertTrue(any(tag=='data' and {v['name']:v['value'] for v in attrs}=={'scheme':d.SCHEME,'host':d.HOST,'path':d.PATH} for _,tag,attrs in nodes))
  broken=after.replace('agents-anywhere'.encode('utf-16le'),'agents-anyxxxe'.encode('utf-16le')).replace(b'agents-anywhere',b'agents-anyxxxe')
  self.assertNotEqual(broken,after)
  with self.assertRaises(ValueError):d.patch(broken)
 def test_malformed(self):
  with self.assertRaises((ValueError,struct.error)):d.patch(b'bad')
if __name__=='__main__':unittest.main()
