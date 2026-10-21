"""Native and independent prepared-payload tests. Real native inputs are mandatory."""
import hashlib
import json
import os
import pathlib
import runpy
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[1]
API = runpy.run_path(str(ROOT/'package-official-ota-preparation.py'))
PROFILES = json.loads((ROOT/'native-source-profiles.json').read_text())


class PreparationTest(unittest.TestCase):
    def test_real_native_contract(self):
        paths = [pathlib.Path(p) for p in os.environ['TURBO_NATIVE_INPUTS'].split(os.pathsep)]
        seen = []
        for path in paths:
            before = path.read_bytes()
            sha = hashlib.sha256(before).hexdigest()
            profile = next(p for p in PROFILES if p['sha256'] == sha)
            seen.append(profile['version'])
            after = API['patch_native'](before, profile)
            ranges = [(s['offset'], s['offset']+len(s['text'])) for s in profile['slots']]
            for s in profile['slots']:
                self.assertEqual(after[s['offset']:s['offset']+len(s['text'])], s['replacement'].encode())
            cursor = 0
            for start, end in sorted(ranges):
                self.assertEqual(before[cursor:start], after[cursor:start])
                cursor = end
            self.assertEqual(before[cursor:], after[cursor:])
            self.assertEqual(len(before), len(after))
            for other in PROFILES:
                if other != profile:
                    with self.assertRaises(ValueError): API['patch_native'](before, other)
            for invalid in (after, before[1:], b'unknown'):
                with self.assertRaises(ValueError): API['patch_native'](invalid, profile)
            for position in (100, profile['slots'][0]['offset']-1, profile['slots'][0]['offset']):
                corrupt = bytearray(before); corrupt[position] ^= 1
                with self.assertRaises(ValueError): API['patch_native'](corrupt, profile)
            print('real native', profile['version'], sha, hashlib.sha256(after).hexdigest())
        self.assertTrue(seen)

    def test_audit_member_byte_and_zip_protections(self):
        with tempfile.TemporaryDirectory() as folder:
            work = pathlib.Path(folder); base=work/'base.apk'; derived=work/'derived.apk'
            members = {'AndroidManifest.xml':b'manifest','classes.dex':b'dex\nfixture',API['NATIVE']:b'old','res/x':b'resource'}
            def write(target, data, marker=True):
                with zipfile.ZipFile(target,'w') as z:
                    for name,value in data.items(): z.writestr(name,value)
                    if marker: z.writestr('marker', b'value')
            write(base,members,False)
            expected = dict(members); expected[API['NATIVE']]=b'new'
            write(derived,expected)
            API['audit_prepared'](base,derived,b'new','marker',b'value')
            for name in ('AndroidManifest.xml','classes.dex','res/x',API['NATIVE']):
                corrupt=dict(expected);corrupt[name]=b'tamper';write(derived,corrupt)
                with self.assertRaises(ValueError): API['audit_prepared'](base,derived,b'new','marker',b'value')
            write(derived,expected)
            with zipfile.ZipFile(derived,'a') as z: z.writestr('marker',b'value')
            with self.assertRaises(ValueError): API['audit_prepared'](base,derived,b'new','marker',b'value')
            derived.write_bytes(b'not zip')
            with self.assertRaises(zipfile.BadZipFile): API['audit_prepared'](base,derived,b'new','marker',b'value')

    def test_cli_unknown_and_overwrite_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            work=pathlib.Path(folder);source=work/'source.apk';source.write_bytes(b'unknown')
            out=work/'out.apk'
            result=subprocess.run([sys.executable,str(ROOT/'package-official-ota-preparation.py'),str(source),str(source),str(source),str(out)],capture_output=True)
            self.assertNotEqual(result.returncode,0);self.assertFalse(out.exists());self.assertFalse(out.with_suffix('.audit.json').exists())
            for target in (source,out):
                if target != source: target.write_bytes(b'user output')
                with self.assertRaises(ValueError): API['prepare'](source,source,source,target)
            self.assertEqual(out.read_bytes(),b'user output')


if __name__ == '__main__': unittest.main()
