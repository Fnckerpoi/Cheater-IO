#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import stat
import struct
import tempfile
import unittest
from unittest.mock import patch
import contextlib
import io
import json
import plistlib
import zipfile

spec = importlib.util.spec_from_file_location('stage_package', Path(__file__).with_name('package-aihot-daily-stage.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

def macho(padding=192, signature=True, filetype=2):
    uuid = struct.pack('<II', 0x1b, 24) + bytes.fromhex(p.UUID)
    old_name = b'@executable_path/Frameworks/TurboIOPrivateAddon.dylib\0'
    length = (24 + len(old_name) + 7) // 8 * 8
    old = struct.pack('<IIIIII', 0xc, length, 24, 0, 0, 0) + old_name
    old += bytes(length - len(old))
    seg_size = 152
    header_end = 32 + len(uuid) + len(old) + seg_size + (16 if signature else 0)
    first = header_end + padding
    seg = bytearray(seg_size)
    struct.pack_into('<II', seg, 0, 0x19, seg_size)
    struct.pack_into('<I', seg, 64, 1)
    struct.pack_into('<Q', seg, 72 + 40, 32)
    struct.pack_into('<I', seg, 72 + 48, first)
    commands = uuid + old + seg
    if signature:
        commands += struct.pack('<IIII', 0x1d, 16, first + 32, 16)
    header = struct.pack('<IIIIIIII', 0xfeedfacf, 0x100000c, 0, filetype, 4 if signature else 3, len(commands), 0, 0)
    return header + commands + bytes(padding) + b'X' * 32 + (b'S' * 16 if signature else b'')

def add_command(b, data):
    before = p.inspect(b)
    out = bytearray(b)
    end = before['end']
    out[end:end + len(data)] = data
    struct.pack_into('<II', out, 16, len(before['commands']) + 1, end - 32 + len(data))
    return bytes(out)

def path_command(cmd, name):
    prefix = 12 if cmd == 0x8000001c else 24
    text = name.encode() + b'\0'
    length = (prefix + len(text) + 7) // 8 * 8
    return struct.pack('<III', cmd, length, prefix) + bytes(prefix - 12) + text + bytes(length - prefix - len(text))

def entry(name, mode=stat.S_IFREG | 0o644):
    e = zipfile.ZipInfo(name)
    e.create_system = 3
    e.external_attr = mode << 16
    return e

class PackageTests(unittest.TestCase):
    def test_patch_preserves_commands_uuid_and_nonheader_bytes(self):
        before = macho()
        after = p.patch_runner(before)
        a, b = p.inspect(before), p.inspect(after)
        self.assertEqual(b['uuid'], a['uuid'])
        self.assertEqual(b['deps'], a['deps'] + [p.LOAD])
        self.assertEqual(b['commands'][:-1], [(c, d) for c, d in a['commands'] if c != 0x1d])
        self.assertEqual(after[a['first']:a['first'] + 32], b'X' * 32)
        self.assertEqual(after[-16:], bytes(16))
        self.assertFalse(b['signatures'])
        self.assertEqual(len(before), len(after))
    def test_no_signature_is_supported(self):
        b = macho(signature=False)
        self.assertEqual(p.inspect(p.patch_runner(b))['deps'][-1], p.LOAD)
    def test_no_padding_rejected(self):
        with self.assertRaisesRegex(ValueError, 'padding'):
            p.patch_runner(macho(padding=0))
    def test_dirty_padding_rejected(self):
        b = bytearray(macho())
        b[p.inspect(b)['end']] = 1
        with self.assertRaisesRegex(ValueError, 'not empty'):
            p.patch_runner(b)
    def test_fat_and_non_arm64_rejected(self):
        for magic, cpu in [(0xcafebabe, 0x100000c), (0xfeedfacf, 7)]:
            b = bytearray(macho())
            struct.pack_into('<II', b, 0, magic, cpu)
            with self.assertRaises(ValueError): p.patch_runner(b)
    def test_duplicate_dependency_rejected(self):
        with self.assertRaises(ValueError): p.patch_runner(p.patch_runner(macho()))
    def test_dylib_not_runner_rejected(self):
        with self.assertRaises(ValueError): p.patch_runner(macho(filetype=6))
    def test_bad_commands_and_signature_bounds_rejected(self):
        b = bytearray(macho())
        struct.pack_into('<I', b, 36, 7)
        with self.assertRaises(ValueError): p.inspect(b)
        b = bytearray(macho())
        struct.pack_into('<I', b, p.inspect(b)['end'] - 8, len(b) + 1)
        with self.assertRaises(ValueError): p.inspect(b)
    def test_zipslip_absolute_backslash_and_empty_components_rejected(self):
        for name in ['../evil', '/tmp/evil', 'Payload/../evil', 'Payload\\evil', 'Payload//evil', 'Payload/./evil']:
            with self.subTest(name=name), self.assertRaises(ValueError): p.validate_entries([entry(name)])
    def test_symlink_and_special_rejected(self):
        for mode in [stat.S_IFLNK | 0o777, stat.S_IFIFO | 0o644]:
            with self.assertRaises(ValueError): p.validate_entries([entry('Payload/file', mode)])
    def test_duplicates_encrypted_probe_rejected(self):
        with self.assertRaises(ValueError): p.validate_entries([entry('Payload/x'), entry('Payload/x')])
        e = entry('Payload/x'); e.flag_bits = 1
        with self.assertRaises(ValueError): p.validate_entries([e])
        with self.assertRaises(ValueError): p.validate_entries([entry(p.ROOT + 'Frameworks/OfficialManuscriptProbe.dylib')])
    def test_permissions_and_signature_paths(self):
        e = entry(p.ROOT + 'Runner', stat.S_IFREG | 0o755)
        self.assertIn(e.filename, p.validate_entries([e]))
        with tempfile.TemporaryDirectory() as d:
            zpath = Path(d) / 'permissions.zip'
            with zipfile.ZipFile(zpath, 'x') as z: z.writestr(e, b'runner')
            with zipfile.ZipFile(zpath) as z:
                self.assertEqual(z.getinfo(e.filename).external_attr >> 16, stat.S_IFREG | 0o755)
        for name in [p.ROOT + '_CodeSignature/', p.ROOT + 'Frameworks/x.framework/_CodeSignature/CodeResources', p.ROOT + 'embedded.mobileprovision']:
            self.assertTrue(p.signature_resource(name))
        for name in [p.ROOT + 'Frameworks/TurboIOPrivateAddon.dylib', p.ROOT + 'Frameworks/App.framework/App', p.ROOT + 'private_CodeSignature_text.json']:
            self.assertFalse(p.signature_resource(name))
    def test_full_archive_diff_permissions_and_opaque_members(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / 'source.ipa'
            module_path = root / 'stage.dylib'
            runner = add_command(macho(padding=512), path_command(0x8000001c, '@executable_path/Frameworks'))
            module = macho(padding=512, signature=False, filetype=6)
            old_path = b'@executable_path/Frameworks/TurboIOPrivateAddon.dylib\0'
            system = b'/usr/lib/libSystem.B.dylib\0'
            module = module.replace(old_path, system + bytes(len(old_path) - len(system)))
            module = add_command(module, path_command(0xd, p.LOAD))
            module = add_command(module, struct.pack('<IIIIII', 0x32, 24, 2, 15 << 16, 15 << 16, 0))
            module += b'TIOAIHotDailyStageInstall' + '日报测试'.encode()
            module_path.write_bytes(module)
            old_addon = b'original-addon-opaque-bytes'
            plist = plistlib.dumps(dict(CFBundleIdentifier='com.duriea.turboio.research', CFBundleShortVersionString='1.0.5', CFBundleVersion='201'))
            originals = {p.ROOT + 'Runner': runner, p.ROOT + 'Info.plist': plist,
                         p.ROOT + 'Frameworks/TurboIOPrivateAddon.dylib': old_addon,
                         p.ROOT + 'Frameworks/App.framework/App': b'original-app',
                         p.ROOT + 'Frameworks/Flutter.framework/Flutter': b'original-flutter',
                         p.ROOT + 'opaque-resource.dat': b'opaque-do-not-parse',
                         p.ROOT + '_CodeSignature/CodeResources': b'obsolete-seal'}
            with zipfile.ZipFile(source, 'x') as z:
                for name, data in originals.items():
                    z.writestr(entry(name, stat.S_IFREG | (0o755 if name.endswith(('Runner', '.dylib')) else 0o644)), data)
            digest = p.file_sha(source)
            with patch.multiple(p, BASE_SHA=digest, OLD_SHA=p.sha(old_addon)), contextlib.redirect_stdout(io.StringIO()):
                report = p.prepare(source, module_path, root / 'new')
            self.assertEqual(p.file_sha(source), digest)
            self.assertEqual([x['path'] for x in report['changed']], [p.ROOT + 'Runner'])
            self.assertEqual(report['removed'], [p.ROOT + '_CodeSignature/CodeResources'])
            self.assertEqual(len(report['added']), 2)
            self.assertEqual(report['unchanged_count'], 5)
            with zipfile.ZipFile(root / 'new' / 'TurboIO-AIHOT-Daily-Stage-unsigned.ipa') as z:
                for name, data in originals.items():
                    if name != p.ROOT + 'Runner' and not p.signature_resource(name):
                        self.assertEqual(z.read(name), data)
                self.assertEqual(z.getinfo(p.ROOT + 'Runner').external_attr >> 16, stat.S_IFREG | 0o755)
                self.assertEqual(z.getinfo(p.ROOT + 'Frameworks/' + p.DYLIB).external_attr >> 16, stat.S_IFREG | 0o755)
                self.assertIsNone(z.testzip())
            manifest = json.loads((root / 'new' / 'stage-package-manifest.json').read_text())
            self.assertEqual(manifest['ipa_sha256'], report['ipa_sha256'])

    def test_existing_output_rejected_before_sha(self):
        with tempfile.TemporaryDirectory() as d:
            source = Path(d) / 'source'; source.write_bytes(b'ipa')
            module = Path(d) / 'module'; module.write_bytes(b'dylib')
            with self.assertRaisesRegex(ValueError, 'new separate'): p.prepare(source, module, d)

if __name__ == '__main__':
    unittest.main(verbosity=2)
