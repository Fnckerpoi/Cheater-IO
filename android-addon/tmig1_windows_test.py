"""Offline regressions for Windows utilities; no SDK, keys or APK downloads."""
import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parent
UTILITY = ROOT / 'tmig1_windows.py'


class WindowsPackagingTest(unittest.TestCase):
    def invoke(self, *args):
        return subprocess.run([sys.executable, str(UTILITY), *map(str, args)], capture_output=True, text=True)

    def test_jar_and_signing_payload_audit_with_space_paths(self):
        with tempfile.TemporaryDirectory(prefix='tmig1 space ') as folder:
            work = pathlib.Path(folder)
            classes = work/'classes/com/example'
            classes.mkdir(parents=True)
            (classes/'Test.class').write_bytes(b'compiled')
            jar = work/'addon jar.jar'
            self.assertEqual(self.invoke('jar', work/'classes', jar).returncode, 0)
            with zipfile.ZipFile(jar) as archive:
                self.assertEqual(archive.namelist(), ['com/example/Test.class'])
            unsigned, signed = work/'unsigned.apk', work/'signed.apk'
            with zipfile.ZipFile(unsigned, 'w') as archive:
                archive.writestr('res/A.xml', b'A')
                archive.writestr('res/a.xml', b'a')
                archive.writestr('META-INF/services/keep', b'service')
            with zipfile.ZipFile(unsigned) as source, zipfile.ZipFile(signed, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, source.read(info))
                target.writestr('META-INF/RESEARCH.SF', b'signature')
            report = work/'audit.json'
            self.assertEqual(self.invoke('audit-signed', unsigned, signed, unsigned, report).returncode, 0)
            self.assertTrue(json.loads(report.read_text())['signedPayloadAuditPassed'])
            with zipfile.ZipFile(signed, 'a') as archive:
                archive.writestr('META-INF/services/unexpected', b'bad')
            self.assertNotEqual(self.invoke('audit-signed', unsigned, signed, unsigned, report).returncode, 0)

    def test_public_pinned_preflight_and_exact_dex_only_merge(self):
        source = ROOT.parent/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
        smali = ROOT.parent/'artifacts/tmig1-public-inspection'
        with tempfile.TemporaryDirectory(prefix='tmig1 public ') as folder:
            work = pathlib.Path(folder)
            report = work/'preflight.json'
            result = self.invoke('public-preflight',source,smali,report)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertTrue(json.loads(report.read_text())['guardedUrlSlotsVerified'])
            dex = work/'fresh classes.dex'
            dex.write_bytes(b'dex\nnewly-compiled-addon')
            output = work/'derived public.apk'
            result = self.invoke('public-merge',source,dex,output,work/'build.json')
            self.assertEqual(result.returncode,0,result.stderr)
            data = json.loads((work/'build.json').read_text())
            self.assertEqual(data['changedPayloads'],['classes4.dex'])
            self.assertFalse(data['manifestChanged'])
            with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as derived:
                self.assertEqual(derived.namelist(),[n for n in original.namelist() if not __import__('re').fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',n,__import__('re').I)])
                self.assertEqual(derived.read('classes4.dex'),dex.read_bytes())
                self.assertEqual(original.read('AndroidManifest.xml'),derived.read('AndroidManifest.xml'))
                self.assertEqual(original.read('lib/arm64-v8a/libapp.so'),derived.read('lib/arm64-v8a/libapp.so'))
            altered = work/'altered.dex'; altered.write_bytes(b'dex\nchanged')
            with zipfile.ZipFile(output,'a') as archive:
                archive.writestr('assets/unexpected.bin',b'bad')
            rejected = self.invoke('public-audit',source,output,work/'bad-audit.json')
            self.assertNotEqual(rejected.returncode,0)

    def test_public_wrong_source_hash_fails_closed(self):
        with tempfile.TemporaryDirectory(prefix='tmig1 public ') as folder:
            work=pathlib.Path(folder); source=work/'wrong.apk'
            source.write_bytes(b'not the pinned public derivative')
            result=self.invoke('public-merge',source,work/'dex',work/'output.apk',work/'report.json')
            self.assertNotEqual(result.returncode,0)
            self.assertFalse((work/'output.apk').exists())

    def test_invalid_native_hash_fails_closed(self):
        with tempfile.TemporaryDirectory(prefix='tmig1 space ') as folder:
            work = pathlib.Path(folder)
            base = work/'base.apk'
            with zipfile.ZipFile(base, 'w') as archive:
                archive.writestr('lib/arm64-v8a/libapp.so', b'wrong version')
            self.assertNotEqual(self.invoke('patch', base, base, work).returncode, 0)
            self.assertFalse((work/'guarded-unsigned.apk').exists())

    def test_existing_assembly_case_regression_with_explicit_python(self):
        spec = importlib.util.spec_from_file_location('assembly_test', ROOT/'tests/ApkAssemblyTest.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        native_run = subprocess.run
        def explicit_python(command, **kwargs):
            if command[0] == 'python3':
                command = [sys.executable, *command[1:]]
            return native_run(command, **kwargs)
        with patch.object(module.subprocess, 'run', explicit_python):
            module.AssemblyTest('test_case_distinct_resources_are_preserved').test_case_distinct_resources_are_preserved()


if __name__ == '__main__':
    unittest.main()
