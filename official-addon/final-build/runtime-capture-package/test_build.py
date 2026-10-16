from pathlib import Path
import importlib.util
import tempfile
import unittest
import json
import shutil
import struct
ROOT=Path(__file__).resolve().parent

def load():
 spec=importlib.util.spec_from_file_location('capture_build',ROOT/'build_capture.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class BuildGates(unittest.TestCase):
 def test_prebuilt_modules_cannot_be_relabelled_to_another_lab_run(self):
  m=load();self.assertEqual(m.identity('capture-20261015-a'),m.BUNDLE)
  for run in ('official','research','public-20261015','other-public','../escape','*'):
   with self.assertRaises(ValueError):m.identity(run)
 def test_module_hash_and_uuid_must_match_reviewed_pair(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   p=Path(d).resolve()/'fake.dylib';p.write_bytes(b'fake reviewed module')
   with self.assertRaises(ValueError):m.check_module(p,m.NATIVE_SHA,m.NATIVE_UUID,'@rpath/TurboIOPrivateAddon.dylib')
 def test_unsafe_load_and_circular_dependency_fail(self):
  m=load()
  for dep in ('@rpath/Flutter.framework/Flutter','@loader_path/evil.dylib','/private/other'):
   with self.assertRaises(ValueError):m.check_adapter_dependencies([dep])
  self.assertTrue(m.check_adapter_dependencies(['/usr/lib/libSystem.B.dylib']))
 def test_status_does_not_claim_runtime_file_save_or_device(self):
  m=load();status=m.runtime_status()
  for k in ('slot_installed','schema_event','file_created','device_ready','official_save_verified','db_write_enabled','final_newshub_complete'):self.assertIs(status[k],False)
  self.assertIs(status['capture_runtime_observation_attempt'],True)
 def test_actual_module_wrong_uuid_and_single_byte_tamper_fail(self):
  m=load();path=m.REVIEWED_ROOT/'TurboIOPrivateAddon.dylib'
  with self.assertRaises(ValueError):m.check_module(path,m.NATIVE_SHA,'FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF','@rpath/TurboIOPrivateAddon.dylib')
  with tempfile.TemporaryDirectory() as d:
   changed=Path(d).resolve()/'tampered';data=bytearray(path.read_bytes());data[-1]^=1;changed.write_bytes(data)
   with self.assertRaises(ValueError):m.check_module(changed,m.NATIVE_SHA,m.NATIVE_UUID,'@rpath/TurboIOPrivateAddon.dylib')
 def test_actual_runner_patch_rejects_tampered_original(self):
  m=load()
  with m.zipfile.ZipFile(m.ORIGINAL_IPA) as z:data=bytearray(z.read('Payload/Runner.app/Runner'))
  data[-1]^=1
  with self.assertRaises(ValueError):m.patch_runner(bytes(data))
 def test_policy_cannot_accept_wrong_bundle_or_missing_strong_dependency(self):
  m=load();stage=ROOT/'build/capture-20261015-a-prepare/stage/Payload/Runner.app'
  if not stage.exists():self.skipTest('Actual staged capture source required for integration-negative test')
  for scenario in ('wrongbundle','missingload'):
   with tempfile.TemporaryDirectory() as d:
    root=Path(d).resolve();app=root/'Runner.app';app.mkdir();out=root/'out';out.mkdir()
    for name in ['Runner','Info.plist','Frameworks/Flutter.framework/Flutter','Frameworks/TurboIOPrivateAddon.dylib','Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs']:
     target=app/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(stage/name,target)
    if scenario=='wrongbundle':
     info=m.plistlib.loads((app/'Info.plist').read_bytes());info['CFBundleIdentifier']='com.rayneo.venus.pub';(app/'Info.plist').write_bytes(m.plistlib.dumps(info))
    else:
     with m.zipfile.ZipFile(m.ORIGINAL_IPA) as z:(app/'Frameworks/Flutter.framework/Flutter').write_bytes(z.read('Payload/Runner.app/Frameworks/Flutter.framework/Flutter'))
    with self.assertRaises(ValueError):m.make_policy(app,out)
    self.assertFalse((out/'generated-policy').exists())
 def test_no_overwrite_or_symlink_outputs(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   r=Path(d).resolve();p=r/'existing';p.mkdir()
   with self.assertRaises(ValueError):m.new_output(p)
   (r/'alias').symlink_to(r)
   with self.assertRaises(ValueError):m.new_output(r/'alias/new')

if __name__=='__main__':unittest.main(verbosity=2)
