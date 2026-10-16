from pathlib import Path
import unittest,struct,hashlib
import patch_flutter as p
ROOT=Path(__file__).resolve().parent
HOST=ROOT/'build/host-order/Frameworks/FlutterOriginal.framework/Flutter'
class HeaderOnlyTransform(unittest.TestCase):
 def setUp(self):self.data=HOST.read_bytes()
 def test_preserves_every_original_dependency_ordinal_and_payload_byte(self):
  after=p.patch(self.data,fixture=True);before=p.inspect(self.data);new=p.inspect(after)
  self.assertEqual(new['deps'],before['deps']+[p.LOAD]);self.assertEqual(new['uuid'],before['uuid']);self.assertEqual(len(after),len(self.data))
  stop=max(before['end'],new['end']);self.assertEqual(after[stop:],self.data[stop:]);self.assertEqual(after[:16],self.data[:16]);self.assertEqual(after[24:32],self.data[24:32])
  self.assertEqual(new['commands'][:-1],[c for c in before['commands'] if c[0]!=0x1d])
 def test_rejects_existing_dependency(self):
  with self.assertRaisesRegex(ValueError,'already'):p.patch(p.patch(self.data,fixture=True),fixture=True)
 def test_rejects_wrong_install_and_wrong_clean_hash(self):
  bad=self.data.replace(b'@rpath/Flutter.framework/Flutter',b'@rpath/Fluttxr.framework/Fluttxr')
  with self.assertRaises(ValueError):p.patch(bad,fixture=True)
  with self.assertRaisesRegex(ValueError,'SHA'):p.patch(self.data)
 def test_malformed_header_rejected(self):
  bad=bytearray(self.data);struct.pack_into('<I',bad,16,9999)
  with self.assertRaises(ValueError):p.patch(bytes(bad),fixture=True)
 def test_nonzero_and_full_header_padding_rejected(self):
  before=p.inspect(self.data);bad=bytearray(self.data);bad[before['end']]=1
  with self.assertRaisesRegex(ValueError,'padding'):p.patch(bytes(bad),fixture=True)
  bad=bytearray(self.data);pos=32
  for cmd,raw in before['commands']:
   if cmd==0x19:
    for i in range(struct.unpack_from('<I',raw,64)[0]):
     q=pos+72+80*i
     if struct.unpack_from('<I',bad,q+48)[0]:struct.pack_into('<I',bad,q+48,before['end'])
   pos+=len(raw)
  with self.assertRaisesRegex(ValueError,'padding'):p.patch(bytes(bad),fixture=True)
 def test_circular_or_unknown_bootstrap_dependencies_rejected(self):
  for deps in [('@rpath/Flutter.framework/Flutter',),('@rpath/App.framework/App',)]:
   with self.assertRaises(ValueError):p.patch(self.data,fixture=True,bootstrap_deps=deps)
 def test_no_overwrite_and_exact_sibling_resolution(self):
  with self.assertRaises(ValueError):p.write_copy(HOST,HOST,fixture=True)
  flutter=Path('/PublicLab/Payload/Runner.app/Frameworks/Flutter.framework/Flutter')
  target=(flutter.parent/p.LOAD.replace('@loader_path/','')).resolve()
  self.assertEqual(target,Path('/PublicLab/Payload/Runner.app/Frameworks/TurboIOStageBObserver.dylib'))
if __name__=='__main__':unittest.main(verbosity=2)
