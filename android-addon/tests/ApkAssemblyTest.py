import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]

class AssemblyTest(unittest.TestCase):
    def test_case_distinct_resources_are_preserved(self):
        self.assemble(False)

    def test_vendor_classes4_is_preserved(self):
        self.assemble(True)

    def test_hooks_in_classes2_and_classes3_are_packaged(self):
        self.assemble(True, ('classes2.dex', 'classes3.dex'))

    def test_public_embedded_brand_assembly_and_audit(self):
        import hashlib
        host_fixture = ROOT / 'build/candidate-110/turbo-build-uqx847/source.apk'
        if not host_fixture.exists(): self.skipTest('1.0.10 host fixture unavailable')
        with tempfile.TemporaryDirectory(prefix='turbo-public-') as temporary:
            root = pathlib.Path(temporary)
            host = root / 'source.apk'
            with zipfile.ZipFile(host_fixture) as original, zipfile.ZipFile(host, 'w') as z:
                z.writestr('AndroidManifest.xml', original.read('AndroidManifest.xml'))
                for name in ('classes.dex', 'classes2.dex', 'classes3.dex', 'classes4.dex'): z.writestr(name, b'dex\noriginal')
            assembled, addon, payload, sidecar, output = [root / x for x in ('host.apk', 'addon.dex', 'aa.apk', 'payload.json', 'output.apk')]
            with zipfile.ZipFile(assembled, 'w') as z:
                for name in ('classes2.dex', 'classes3.dex'): z.writestr(name, b'dex\nturboioOriginal_' + name.encode())
            addon.write_bytes(b'dex\naddon')
            with zipfile.ZipFile(payload, 'w') as z:
                z.writestr('classes.dex', b'dex\n'); z.writestr('resources.arsc', b'resources')
            sidecar.write_text(json.dumps({'schemaVersion': 1, 'abiVersion': 1, 'entry': 'com.agentsanywhere.embedded.PayloadEntry', 'sha256': hashlib.sha256(payload.read_bytes()).hexdigest()}))
            flags = ['--public-assets', '--hook-dex', 'classes2.dex', '--hook-dex', 'classes3.dex', '--payload-apk', str(payload), '--payload-sidecar', str(sidecar), '--cheater-io']
            def run(script, *args): return subprocess.run([sys.executable, str(ROOT / script), *map(str, args), *flags], capture_output=True, text=True)
            result = run('assemble_apk.py', host, assembled, addon, output)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = run('verify_apk.py', host, output)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            sys.path.insert(0, str(ROOT))
            from tmig1_coexist import parse
            with zipfile.ZipFile(output) as z:
                self.assertEqual(z.read('assets/turboio/aa/aa-embedded-payload.apk'), payload.read_bytes())
                self.assertEqual(z.read('assets/turboio/aa/payload.json'), sidecar.read_bytes())
                _, _, _, nodes = parse(z.read('AndroidManifest.xml'))
                self.assertTrue(any(tag == 'application' and any(a['name'] == 'label' and a['value'] == 'Cheater-IO' for a in attrs) for _, tag, attrs in nodes))
                self.assertTrue(any(tag == 'activity' and any(a['name'] == 'name' and a['value'] == 'com.turboio.addon.AaEmbeddedActivity' for a in attrs) for _, tag, attrs in nodes))
            restored = root / 'restored-manifest.apk'
            with zipfile.ZipFile(output) as good, zipfile.ZipFile(host) as original, zipfile.ZipFile(restored, 'w') as bad:
                for info in good.infolist():
                    bad.writestr(info, original.read('AndroidManifest.xml') if info.filename == 'AndroidManifest.xml' else good.read(info))
            self.assertNotEqual(run('verify_apk.py', host, restored).returncode, 0)
            sidecar.write_text('{}')
            self.assertNotEqual(run('verify_apk.py', host, output).returncode, 0)
            self.assertNotEqual(run('assemble_apk.py', host, assembled, addon, root / 'bad.apk').returncode, 0)

    def assemble(self, vendor4, hooks=('classes2.dex',)):
        with tempfile.TemporaryDirectory(prefix='turbo-apk-test-') as temporary:
            root = pathlib.Path(temporary)
            source, assembled, addon, output = [root / x for x in ('source.apk','assembled.apk','addon.dex','output.apk')]
            files = {'classes.dex': b'dex\noriginal1', 'classes2.dex': b'dex\noriginal2',
                     'classes3.dex': b'dex\noriginal3', 'classes4.dex': b'dex\nofficial4', 'res/A.xml': b'UPPER',
                     'res/a.xml': b'lower', 'AndroidManifest.xml': b'manifest',
                     'resources.arsc': b'arsc', 'lib/arm64-v8a/libflutter.so': b'native',
                     'META-INF/VENDOR.SF': b'old signature', 'META-INF/services/keep': b'keep'}
            if not vendor4: del files['classes4.dex']
            addon_slot = 'classes5.dex' if vendor4 else 'classes4.dex'
            with zipfile.ZipFile(source, 'w') as archive:
                for name, data in files.items(): archive.writestr(name, data)
            patched = {name: b'dex\nturboioOriginal_' + name.encode() for name in hooks}
            with zipfile.ZipFile(assembled, 'w') as archive:
                for name, data in patched.items(): archive.writestr(name, data)
                archive.writestr('classes.dex', b'dex\nrebuilt but not hooked')
            addon.write_bytes(b'dex\naddon')
            hook_args = [part for name in hooks for part in ('--hook-dex', name)]
            flags = hook_args if len(hooks) > 1 else []
            assemble = [sys.executable,str(ROOT/'assemble_apk.py'),str(source),str(assembled),str(addon)]
            verify = [sys.executable,str(ROOT/'verify_apk.py'),str(source)]
            subprocess.run([*assemble,str(output),*flags],check=True,capture_output=True)
            with zipfile.ZipFile(output) as archive:
                for name, data in files.items():
                    if name in hooks or name == 'META-INF/VENDOR.SF': continue
                    self.assertEqual(archive.read(name), data)
                for name, data in patched.items(): self.assertEqual(archive.read(name), data)
                self.assertEqual(archive.read(addon_slot), b'dex\naddon')
                self.assertNotIn('META-INF/VENDOR.SF',archive.namelist())
            audit = subprocess.run([*verify,str(output),*flags],check=True,capture_output=True,text=True)
            self.assertEqual(json.loads(audit.stdout)['hook_dex_slots'], list(hooks))
            failure = subprocess.run([*assemble,str(source),*flags],capture_output=True)
            self.assertNotEqual(failure.returncode,0)
            for command in ([*assemble,str(root/'duplicate.apk')], [*verify,str(output)]):
                with self.subTest(check='duplicate hook declaration', script=command[1]):
                    failure = subprocess.run([*command,*hook_args,'--hook-dex',hooks[0]],capture_output=True)
                    self.assertNotEqual(failure.returncode,0,'duplicate hook declaration')
            if len(hooks) > 1:
                for omitted in ([], ['--hook-dex', 'classes2.dex']):
                    for command in ([*assemble,str(root/('undeclared-' + str(len(omitted)) + '.apk'))], [*verify,str(output)]):
                        with self.subTest(check='undeclared hook DEX', script=command[1], flags=omitted):
                            failure = subprocess.run([*command,*omitted],capture_output=True)
                            self.assertNotEqual(failure.returncode,0,'undeclared hook DEX')
            for slot in hooks:
                for missing in (False, True):
                    broken_assembled, rejected = root/'broken-assembled.apk', root/'rejected.apk'
                    with zipfile.ZipFile(broken_assembled, 'w') as archive:
                        for name, data in patched.items():
                            if name == slot and missing: continue
                            archive.writestr(name, files[name] if name == slot else data)
                    with self.subTest(check='assembled hook missing', slot=slot, missing=missing):
                        failure = subprocess.run([sys.executable,str(ROOT/'assemble_apk.py'),str(source),str(broken_assembled),str(addon),str(rejected),*flags],capture_output=True)
                        self.assertNotEqual(failure.returncode,0)
                        self.assertFalse(rejected.exists())
            untouched = 'classes4.dex' if vendor4 else 'classes3.dex'
            mutations = {'missing-addon': {addon_slot: None},
                         'changed-vendor-dex': {untouched: b'dex\ntampered'},
                         'changed-vendor-with-marker': {untouched: b'dex\ntampered turboioOriginal'},
                         'unchanged-hooks': {name: files[name] for name in hooks}}
            for name in hooks:
                mutations['restored-' + name] = {name: files[name]}
                mutations['removed-' + name] = {name: None}
            for mutation, changes in mutations.items():
                bad = root / (mutation + '.apk')
                with zipfile.ZipFile(output) as good, zipfile.ZipFile(bad, 'w') as broken:
                    for info in good.infolist():
                        content = changes.get(info.filename, good.read(info))
                        if content is None: continue
                        broken.writestr(info, content)
                with self.subTest(mutation=mutation):
                    failure = subprocess.run([*verify,str(bad),*flags],capture_output=True)
                    self.assertNotEqual(failure.returncode,0,mutation)
                    self.assertIn(next(iter(changes)), json.loads(failure.stdout)['unexpected_paths'], mutation)

if __name__ == '__main__': unittest.main()
