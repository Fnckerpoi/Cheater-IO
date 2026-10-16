import importlib.util
from pathlib import Path
import hashlib
import json
import plistlib
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('lab', Path(__file__).with_name('lab.py'))
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


def macho(filetype=2, deps=(), signed=False):
    commands = [struct.pack('<II', 0x1b, 24) + bytes.fromhex('748fd301da603095a2449bd0558faca5')]
    section = bytearray(152)
    struct.pack_into('<II', section, 0, 0x19, 152)
    struct.pack_into('<I', section, 64, 1)
    section[72:78] = b'__text'
    struct.pack_into('<QI', section, 112, 64, 2048)
    commands.append(bytes(section))
    for dep in deps:
        name = dep.encode() + b'\0'
        size = (24 + len(name) + 7) // 8 * 8
        commands.append(struct.pack('<6I', 0xc, size, 24, 0, 0, 0) + name + bytes(size - 24 - len(name)))
    if signed:
        commands.append(struct.pack('<4I', 0x1d, 16, 2200, 32))
    data = bytearray(2304)
    struct.pack_into('<8I', data, 0, 0xfeedfacf, 0x100000c, 0, filetype, len(commands), sum(map(len, commands)), 0, 0)
    data[32:32 + sum(map(len, commands))] = b''.join(commands)
    data[2048:2112] = bytes(range(64))
    data[2200:2232] = b'S' * 32
    return bytes(data)


def fixture(root):
    app = root / 'Runner.app'
    app.mkdir()
    (app / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': 'com.rayneo.venus.pub', 'CFBundleShortVersionString': '1.0.5', 'CFBundleVersion': '201', 'CFBundleExecutable': 'Runner'}))
    (app / 'Runner').write_bytes(macho(deps=('/usr/lib/libSystem.B.dylib',), signed=True))
    files = {}
    for name in ('App', 'isar_plus_flutter_libs', 'sqflite_darwin'):
        target = app / 'Frameworks' / (name + '.framework') / name
        target.parent.mkdir(parents=True)
        target.write_bytes(macho(6))
        files[str(target.relative_to(app))] = hashlib.sha256(target.read_bytes()).hexdigest()
    return app, files


class PreflightBoundary(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.app, self.hashes = fixture(self.root)

    def tearDown(self):
        self.tmp.cleanup()

    def test_synthetic_preflight_is_explicit_and_cannot_claim_production(self):
        report = lab.preflight(self.app, 'public-20261014', expected_hashes=self.hashes, synthetic=True)
        self.assertEqual(report['status'], 'SYNTHETIC_PREFLIGHT_ONLY')
        self.assertFalse(report['device_ready'])
        with self.assertRaisesRegex(ValueError, 'hash'):
            lab.preflight(self.app, 'public-20261014')

    def test_version_bundle_uuid_and_old_addon_are_rejected(self):
        original = plistlib.loads((self.app/'Info.plist').read_bytes())
        for key,value in [('CFBundleVersion','218'), ('CFBundleShortVersionString','1.0.6'), ('CFBundleIdentifier','com.duriea.turboio.research')]:
            info = dict(original, **{key:value})
            (self.app/'Info.plist').write_bytes(plistlib.dumps(info))
            with self.assertRaises(ValueError): lab.preflight(self.app, 'public-20261014', self.hashes, True)
        (self.app/'Info.plist').write_bytes(plistlib.dumps(original))
        (self.app/'Runner').write_bytes(macho(deps=('@rpath/TurboIOPrivateAddon.dylib',)))
        with self.assertRaises(ValueError): lab.preflight(self.app, 'public-20261014', self.hashes, True)
        bad = bytearray(macho());bad[40] ^= 1;(self.app/'Runner').write_bytes(bad)
        with self.assertRaises(ValueError): lab.preflight(self.app, 'public-20261014', self.hashes, True)

    def test_nested_watch_private_data_and_symlink_fail_closed(self):
        for name in ('PlugIns', 'Watch', 'Documents', 'TurboIOPrivateBootstrap.json', 'private.isar', 'signing.p12', 'secret.key', 'embedded.mobileprovision'):
            p = self.app / name;p.mkdir() if '.' not in name else p.write_bytes(b'x')
            with self.assertRaises(ValueError): lab.preflight(self.app, 'public-20261014', self.hashes, True)
            p.rmdir() if p.is_dir() else p.unlink()
        (self.app/'link').symlink_to(self.root)
        with self.assertRaises(ValueError): lab.preflight(self.app, 'public-20261014', self.hashes, True)

    def test_output_source_overlap_and_symlink_parent_are_rejected(self):
        module=self.root/'probe.dylib';module.write_bytes(b'fixture')
        for out in (self.app, self.app/'new', self.root):
            with self.assertRaises(ValueError):lab.prepare(self.app,module,out,'public-20261014',self.hashes,True)
        alias=self.root/'alias';alias.symlink_to(self.root)
        with self.assertRaises(ValueError):lab.prepare(self.app,module,alias/'out','public-20261014',self.hashes,True)

    def test_patch_preserves_ordinals_body_and_source(self):
        data = (self.app/'Runner').read_bytes()
        before = lab.inspect(data)
        patched = lab.patch_runner(data)
        after = lab.inspect(patched)
        self.assertEqual(after['deps'], before['deps'] + ['@executable_path/Frameworks/TurboIOManuscriptLabProbe.dylib'])
        self.assertEqual(after['commands'][:-1], [c for c in before['commands'] if c[0] != 0x1d])
        self.assertEqual(patched[2048:2112], data[2048:2112])
        self.assertEqual(patched[2200:2232], bytes(32))
        self.assertEqual((self.app/'Runner').read_bytes(), data)

    def test_new_output_only_and_source_hashes_preserved(self):
        out = self.root / 'new-output'
        module = self.root / 'probe.dylib'; module.write_bytes(b'FIXTURE_NOT_DEVICE_CODE')
        report = lab.prepare(self.app, module, out, 'public-20261014', self.hashes, True)
        self.assertEqual(report['status'], 'SYNTHETIC_UNSIGNED_FIXTURE_NOT_INSTALLABLE')
        self.assertFalse(list(out.glob('*.ipa')))
        result = out/'Payload/Runner.app'
        for name,digest in self.hashes.items(): self.assertEqual(hashlib.sha256((result/name).read_bytes()).hexdigest(), digest)
        info=plistlib.loads((result/'Info.plist').read_bytes());self.assertEqual(info['CFBundleDisplayName'],'文稿隔离实验105-public-20261014')
        self.assertEqual(info['CFBundleIdentifier'],'com.duriea.turboio.manuscriptlab105.public-20261014')
        with self.assertRaises(ValueError): lab.prepare(self.app, module, out, 'public-20261014', self.hashes, True)


if __name__ == '__main__': unittest.main(verbosity=2)
