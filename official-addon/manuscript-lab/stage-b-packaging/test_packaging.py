from pathlib import Path
import importlib.util
import json
import plistlib
import struct
import zipfile
import sys
import tempfile
import unittest
import os

ROOT=Path(__file__).resolve().parents[1]
DEFAULT_1_0_6_IPA=ROOT.parent/'.build/ios-inputs/RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa'
# Historical 1.0.5 input remains available explicitly via TIO_TEST_ORIGINAL_IPA.
HISTORIC_1_0_5_IPA=ROOT.parent/'build/manuscript-lab-input-20261015-original105/RayNeo_AI_1.0.5_201_decrypted_original.ipa'
ORIGINAL_IPA=Path(os.environ.get('TIO_TEST_ORIGINAL_IPA',str(DEFAULT_1_0_6_IPA)))
NEW_RUNNER_SHA='d59823568be232b1742f7414a6ebae4718f053e1a251577808041b032306bd44'
NEW_RUNNER_UUID='98a03b57e8163ba19778e55fabe8d762'
NEW_RUNNER_DEPENDENCIES=164
sys.path.insert(0,str(ROOT))
from test_package import fixture, macho


def load():
 spec=importlib.util.spec_from_file_location('stageb',ROOT/'package-stage-b.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class Boundaries(unittest.TestCase):
 def test_unreviewed_module_is_blocked_before_output(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   p=Path(d).resolve()/'unreviewed.dylib';p.write_bytes(b'unreviewed')
   result=m.module_gate(p)
   self.assertEqual(result['status'],'BLOCKED_UNREVIEWED_MODULE');self.assertFalse(result['entry_installed']);self.assertFalse(result['observation_active'])
 def test_actual_rejected_archive_must_fail_isolation_load_and_marker(self):
  m=load();bad=ROOT/'build/stage-b-unsigned-public/ManuscriptLab105-StageB-Observer-unsigned.ipa'
  result=m.audit_archive(bad,'public-20261015')
  self.assertFalse(result['accepted']);self.assertIn('WRONG_BUNDLE',result['errors']);self.assertIn('MISSING_STAGE_B_LOAD',result['errors']);self.assertIn('FALSE_OBSERVATION_CLAIM',result['errors'])
 def test_unsigned_patch_requires_original_and_preserves_ordinals(self):
  m=load();data=macho(deps=('/usr/lib/libSystem.B.dylib',),signed=True)
  result=m.patch_runner(data,synthetic=True)
  before=m.lab.inspect(data);after=m.lab.inspect(result)
  self.assertEqual(after['deps'],before['deps']+[m.LOAD]);self.assertEqual(after['commands'][:-1],[c for c in before['commands'] if c[0]!=0x1d]);self.assertEqual(result[2048:2112],data[2048:2112])
  with self.assertRaises(ValueError):m.patch_runner(data)
 def test_module_hash_tampering_and_selfauthored_receipt_not_approval(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   p=Path(d).resolve()/'module';p.write_bytes(b'arbitrary text');(Path(d).resolve()/'receipt.json').write_text(json.dumps({'approved':True,'module_sha256':m.lab.digest(p.read_bytes())}))
   self.assertEqual(m.module_gate(p)['status'],'BLOCKED_UNREVIEWED_MODULE')
 def synthetic_archive(self,root,*,missing_load=False,wrong_bundle=False,missing_module=False,tampered_module=False):
  m=load();run='public-20261015';ident=m.lab.identity(run)
  info=dict(CFBundleIdentifier='com.rayneo.venus.pub' if wrong_bundle else ident['bundle'],CFBundleDisplayName=ident['display_name'],TIOManuscriptLabRunID=run,TIOManuscriptLabStage='B_INTERNAL_DISABLED')
  runner=macho(deps=('/usr/lib/libSystem.B.dylib',),signed=True)
  if not missing_load:runner=m.patch_runner(runner,synthetic=True)
  marker=dict(stage='B_INTERNAL_DISABLED',run=run,expected_bundle=ident['bundle'],entry_installed=False,observation_active=False,device_ready=False,writes_enabled=False,db_access_enabled=False)
  ipa=root/'synthetic-NOT-INSTALLABLE.zip'
  with zipfile.ZipFile(ipa,'x') as z:
   z.writestr(m.APP+'Runner',runner);z.writestr(m.APP+'Info.plist',plistlib.dumps(info));z.writestr(m.APP+'ManuscriptLabStageB.json',json.dumps(marker))
   if not missing_module:z.writestr(m.APP+'Frameworks/'+m.DYLIB,b'changed' if tampered_module else b'SYNTHETIC-NOT-DEVICE-CODE')
  return ipa
 def test_synthetic_archive_missing_load_wrong_bundle_noprobe_and_tamper_rejected(self):
  m=load()
  for kwargs,error in (({'missing_load':True},'MISSING_STAGE_B_LOAD'),({'wrong_bundle':True},'WRONG_BUNDLE'),({'missing_module':True},'MISSING_MODULE'),({'tampered_module':True},'TAMPERED_MODULE')):
   with self.subTest(error=error),tempfile.TemporaryDirectory() as d:
    ipa=self.synthetic_archive(Path(d).resolve(),**kwargs)
    result=m.audit_archive(ipa,'public-20261015',expected_module_sha=m.lab.digest(b'SYNTHETIC-NOT-DEVICE-CODE'))
    self.assertFalse(result['accepted']);self.assertIn(error,result['errors']);self.assertFalse(result['entry_installed']);self.assertFalse(result['observation_active'])
 def test_synthetic_structural_success_is_still_runtime_blocked(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   ipa=self.synthetic_archive(Path(d).resolve())
   result=m.audit_archive(ipa,'public-20261015')
   self.assertTrue(result['accepted']);self.assertEqual(result['status'],'BLOCKED_RUNTIME_ENTRY');self.assertTrue(result['module_linked']);self.assertFalse(result['device_ready'])
 def test_missing_required_load_and_modified_body_fail_independent_delta_check(self):
  m=load();before=macho(deps=('/usr/lib/libSystem.B.dylib',),signed=True)
  with self.assertRaises(ValueError):m.verify_runner_delta(before,before)
  patched=bytearray(m.patch_runner(before,synthetic=True));patched[2051]^=1
  with self.assertRaises(ValueError):m.verify_runner_delta(before,bytes(patched))
 def test_actual_original_runner_patch_preserves_bindings_and_bytes(self):
  m=load()
  with zipfile.ZipFile(ORIGINAL_IPA) as z:before=z.read(m.APP+'Runner')
  source_sha=m.lab.digest(before);original=m.lab.inspect(before)
  if source_sha==NEW_RUNNER_SHA:
   self.assertEqual(original['uuid'],NEW_RUNNER_UUID)
   self.assertEqual(len(original['deps']),NEW_RUNNER_DEPENDENCIES)
   # The production patcher remains pinned to historical 1.0.5; synthetic mode
   # exercises byte/ordinal preservation for the verified 1.0.6 Runner only.
   m.lab.UUID=NEW_RUNNER_UUID
   after=m.patch_runner(before,synthetic=True)
   expected_dependencies=NEW_RUNNER_DEPENDENCIES+1
  elif source_sha==m.RUNNER_SHA:
   self.assertEqual(original['uuid'],m.lab.UUID)
   self.assertEqual(len(original['deps']),156)
   after=m.patch_runner(before)
   expected_dependencies=157
  else:
   self.fail(f'Unsupported original Runner SHA-256: {source_sha}; historical input runs as TIO_TEST_ORIGINAL_IPA={HISTORIC_1_0_5_IPA}')
  self.assertTrue(m.verify_runner_delta(before,after));h=m.lab.inspect(after)
  self.assertEqual(len(h['deps']),expected_dependencies);self.assertEqual(h['deps'][-1],m.LOAD)
  self.assertEqual(h['uuid'],original['uuid'])
  self.assertEqual(len(before),len(after))
 def test_wrong_whole_original_and_tampered_source_rejected_before_output(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   root=Path(d).resolve();app,pins=fixture(root);bad=root/'wrong-original.ipa';bad.write_bytes(b'not trusted');module=root/'module';module.write_bytes(b'unreviewed');out=root/'no-output'
   with self.assertRaises(ValueError):m.prepare_internal(bad,app,module,'public-20261015',out)
   self.assertFalse(out.exists())
 def test_info_transform_preserves_official_values_and_changes_only_isolation_policy(self):
  m=load();original={'CFBundleIdentifier':'com.rayneo.venus.pub','CFBundleShortVersionString':'1.0.5','CFBundleVersion':'201','GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS':False,'CFBundleURLTypes':[{'CFBundleURLSchemes':['official']}],'UIBackgroundModes':['audio']}
  configured=m.isolated_info(original,'public-20261015')
  self.assertEqual(configured['CFBundleShortVersionString'],'1.0.5');self.assertEqual(configured['CFBundleVersion'],'201')
  self.assertIs(configured['GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS'],False)
  self.assertNotIn('CFBundleURLTypes',configured);self.assertNotIn('UIBackgroundModes',configured)
  self.assertEqual(original['CFBundleIdentifier'],'com.rayneo.venus.pub')
 def test_absent_probe_stays_blocked_and_unsafe_zip_symlink_rejected(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   root=Path(d).resolve();self.assertEqual(m.module_gate(root/'no-probe')['status'],'BLOCKED_UNREVIEWED_MODULE')
   p=root/'symlink.zip'
   with zipfile.ZipFile(p,'x') as z:
    e=zipfile.ZipInfo('Payload/Runner.app/link');e.external_attr=0o120777<<16;z.writestr(e,'outside')
   with zipfile.ZipFile(p) as z:
    with self.assertRaises(ValueError):m.archive_entries(z)
 def test_reused_entitlement_policy_never_inherits_shared_capabilities(self):
  m=load();plan=m.lab.signing_plan('public-20261015','AB12CD34EF')
  self.assertEqual(set(plan),{'application-identifier','com.apple.developer.team-identifier','get-task-allow'})
  self.assertTrue(m.lab.check_nested_entitlements({}))
  with self.assertRaises(ValueError):m.lab.check_entitlements(dict(plan,**{'keychain-access-groups':['AB12CD34EF.shared']}),'public-20261015','AB12CD34EF')
  with self.assertRaises(ValueError):m.lab.check_nested_entitlements({'com.apple.security.application-groups':['group.shared']})
 def test_output_symlink_overwrite_and_overlap_rejected(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   root=Path(d).resolve();source=root/'Runner.app';source.mkdir()
   for out in (source,source/'inside',root):
    with self.assertRaises(ValueError):m.new_output(source,out)
   alias=root/'alias';alias.symlink_to(root)
   with self.assertRaises(ValueError):m.new_output(source,alias/'out')

if __name__=='__main__':unittest.main(verbosity=2)
