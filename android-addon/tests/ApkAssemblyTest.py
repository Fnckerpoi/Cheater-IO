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

    def test_hooks_in_multiple_dex_slots_are_packaged(self):
        self.assemble(True, ('classes2.dex', 'classes3.dex'))

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
            addon.write_bytes(b'dex\naddon')
            hook_args = [part for name in hooks for part in ('--hook-dex', name)]
            assemble = [sys.executable,str(ROOT/'assemble_apk.py'),str(source),str(assembled),str(addon)]
            verify = [sys.executable,str(ROOT/'verify_apk.py'),str(source)]
            subprocess.run([*assemble,str(output),*hook_args],check=True,capture_output=True)
            with zipfile.ZipFile(output) as archive:
                for name, data in files.items():
                    if name in hooks or name == 'META-INF/VENDOR.SF': continue
                    self.assertEqual(archive.read(name), data)
                for name, data in patched.items(): self.assertEqual(archive.read(name), data)
                self.assertEqual(archive.read(addon_slot), b'dex\naddon')
                self.assertNotIn('META-INF/VENDOR.SF',archive.namelist())
            subprocess.run([*verify,str(output),*hook_args],check=True,capture_output=True)
            failure = subprocess.run([*assemble,str(source),*hook_args],capture_output=True)
            self.assertNotEqual(failure.returncode,0)
            if len(hooks) > 1:
                for command in ([*assemble,str(root/'undeclared.apk')], [*verify,str(output)]):
                    self.assertNotEqual(subprocess.run([*command,'--hook-dex',hooks[0]],capture_output=True).returncode,0)
            for slot in hooks:
                bad_assembled = root / ('missing-' + slot + '.apk')
                with zipfile.ZipFile(bad_assembled, 'w') as archive:
                    for name, data in patched.items():
                        if name != slot: archive.writestr(name, data)
                self.assertNotEqual(subprocess.run([sys.executable,str(ROOT/'assemble_apk.py'),str(source),str(bad_assembled),str(addon),str(root/'rejected.apk'),*hook_args],capture_output=True).returncode,0)
            for mutation in ('missing-addon', 'changed-vendor-dex'):
                bad = root / (mutation + '.apk')
                with zipfile.ZipFile(output) as good, zipfile.ZipFile(bad, 'w') as broken:
                    for info in good.infolist():
                        if mutation == 'missing-addon' and info.filename == addon_slot: continue
                        content = b'dex\ntampered' if mutation == 'changed-vendor-dex' and info.filename == ('classes4.dex' if vendor4 else 'classes3.dex') else good.read(info)
                        broken.writestr(info, content)
                failure = subprocess.run([*verify,str(bad),*hook_args],capture_output=True)
                self.assertNotEqual(failure.returncode,0,mutation)

if __name__ == '__main__': unittest.main()
