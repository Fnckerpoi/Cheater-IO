from pathlib import Path
import importlib.util
import tempfile
import unittest
from unittest.mock import patch
import subprocess
import sys
ROOT=Path(__file__).resolve().parent

def load():
 spec=importlib.util.spec_from_file_location('recipe',ROOT/'build_modules.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class RecipeInputs(unittest.TestCase):
 def test_literal_shared_storage_blocks_isolated_compile(self):
  m=load()
  for token in m.LEGACY_STORAGE:
   self.assertIn(token,m.storage_issues('static NSString *Domain=@"'+token+'";'))
  self.assertEqual(m.storage_issues('TIOStorageAddonDomain()'),[])
 def test_exact_unique_lab_identity_no_official_or_research_target(self):
  m=load();self.assertEqual(m.identity('public-20261015'),'com.duriea.turboio.manuscriptlab106.public-20261015')
  for run in ('','abc','../escape','*','official','research','UPPER-test'):
   with self.assertRaises(ValueError):m.identity(run)
 def test_output_paths_cannot_overwrite_or_escape(self):
  m=load()
  with tempfile.TemporaryDirectory() as d:
   root=Path(d).resolve();source=root/'source';source.mkdir()
   for out in (source,root,source/'inside'):
    with self.assertRaises(ValueError):m.new_output(source,out)
   own=source/'final-build/build/new-one';self.assertEqual(m.new_output(source,own),own)
   alias=root/'alias';alias.symlink_to(root)
   with self.assertRaises(ValueError):m.new_output(source,alias/'out')
 def test_testing_flags_and_custom_unreviewed_uuid_cli_are_rejected(self):
  m=load()
  for name in ('TIO_NEWS_UI_VALIDATION','TIO_IMPORT_QUEUE_LEGACY_TESTING','TIO_STAGE_B_TESTING','TIO_STORAGE_TESTING','TIO_UI_PREVIEW'):
   with patch.dict(m.os.environ,{name:'1'}):
    with self.assertRaises(ValueError):m.preflight('public-20261015')
  result=subprocess.run([sys.executable,str(ROOT/'build_modules.py'),'--run','public-20261015','--addon-uuid','11111111-2222-4333-8444-555555555555'],capture_output=True,text=True)
  self.assertNotEqual(result.returncode,0)
 def test_module_list_contains_real_reader_and_seven_hub_sources(self):
  m=load();self.assertIn('Addon.m',m.ADDON_SOURCES);self.assertIn('NewsReader.m',m.ADDON_SOURCES);self.assertIn('WebSearch.m',m.ADDON_SOURCES)
  self.assertEqual(len(m.HUB_SOURCES),7);self.assertIn('TIONewsScheduler.m',m.HUB_SOURCES);self.assertIn('TIOImportQueue.m',m.HUB_SOURCES);self.assertNotIn('NewsReader.m',m.HUB_SOURCES)
  self.assertEqual(len(m.MAIN_ADDON_SOURCES),45);self.assertEqual(m.ADDON_SOURCES,m.MAIN_ADDON_SOURCES+m.BRIDGE_SOURCES)
  self.assertEqual(len(m.BRIDGE_SOURCES),14);self.assertEqual(len(m.BRIDGE_SOURCES),len(set(m.BRIDGE_SOURCES)))
  self.assertTrue(all((m.SOURCE/name).is_file() for name in m.BRIDGE_SUPPORT_FILES))
  self.assertIn('tmig1-edition/FocusBridge.m',m.BRIDGE_SOURCES);self.assertIn('tmig1-edition/context-english-v1/ContextEnglishBridge.m',m.BRIDGE_SOURCES)

if __name__=='__main__':unittest.main(verbosity=2)
