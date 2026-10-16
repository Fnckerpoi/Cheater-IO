#!/usr/bin/env python3
import contextlib
import importlib.util
import io
from pathlib import Path
import plistlib
import stat
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('trial_package', Path(__file__).with_name('package-official-import-trial.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


def entry(name, mode=stat.S_IFREG | 0o644):
    item = zipfile.ZipInfo(name)
    item.create_system = 3
    item.external_attr = mode << 16
    return item


def command(kind, name):
    offset = 12 if kind == 0x8000001c else 24
    text = name.encode() + b'\0'
    length = (offset + len(text) + 7) // 8 * 8
    return struct.pack('<III', kind, length, offset) + bytes(offset - 12) + text + bytes(length - offset - len(text))


def macho(filetype=2, padding=192, signed=False, stage=True, minimum=15 << 16, install=None, deps=None):
    commands = [struct.pack('<II', 0x1b, 24) + bytes.fromhex(p.UUID)]
    commands.append(command(0x8000001c, '@executable_path/Frameworks'))
    dependencies = deps if deps is not None else ['@executable_path/Frameworks/TurboIOPrivateAddon.dylib'] + ([p.STAGE_LOAD] if stage else [])
    commands.extend(command(0xc, value) for value in dependencies)
    if install:
        commands.append(command(0xd, install))
    commands.append(struct.pack('<IIIIII', 0x32, 24, 2, minimum, 26 << 16, 0))
    end = 32 + sum(len(c) for c in commands) + 152 + (16 if signed else 0)
    first = end + padding
    segment = bytearray(152)
    struct.pack_into('<II', segment, 0, 0x19, 152)
    struct.pack_into('<I', segment, 64, 1)
    struct.pack_into('<Q', segment, 72 + 40, 32)
    struct.pack_into('<I', segment, 72 + 48, first)
    commands.append(bytes(segment))
    if signed:
        commands.append(struct.pack('<IIII', 0x1d, 16, first + 32, 16))
    data = b''.join(commands)
    head = struct.pack('<IIIIIIII', 0xfeedfacf, 0x100000c, 0, filetype, len(commands), len(data), 0, 0)
    return head + data + bytes(padding) + b'X' * 32 + (b'S' * 16 if signed else b'')


class TrialPackageTests(unittest.TestCase):
    def test_unsigned_append_preserves_every_old_command_and_body(self):
        before = macho()
        after = p.patch_runner(before)
        a, b = p.inspect(before), p.inspect(after)
        self.assertEqual(a['commands'], b['commands'][:-1])
        self.assertEqual(b['deps'], a['deps'] + [p.LOAD])
        self.assertEqual(a['uuid'], b['uuid'])
        self.assertEqual(after[:16], before[:16])
        self.assertEqual(after[24:a['end']], before[24:a['end']])
        self.assertEqual(after[b['end']:], before[b['end']:])
        self.assertEqual(len(before), len(after))
    def test_no_padding_dirty_padding_signed_and_no_stage_reject(self):
        for data in [macho(padding=0), macho(signed=True), macho(stage=False), macho(filetype=6), p.patch_runner(macho())]:
            with self.assertRaises(ValueError): p.patch_runner(data)
        data = bytearray(macho()); data[p.inspect(data)['end']] = 1
        with self.assertRaisesRegex(ValueError, 'not empty'): p.patch_runner(data)
    def test_fat_other_architecture_and_bad_commands_reject(self):
        for magic, cpu in [(0xcafebabe, 0x100000c), (0xfeedfacf, 7)]:
            data = bytearray(macho()); struct.pack_into('<II', data, 0, magic, cpu)
            with self.assertRaises(ValueError): p.patch_runner(data)
        data = bytearray(macho()); struct.pack_into('<I', data, 36, 9)
        with self.assertRaises(ValueError): p.inspect(data)
    def test_module_exact_ios15_unsigned_install_and_marker(self):
        valid = macho(filetype=6, install=p.LOAD, deps=['/usr/lib/libSystem.B.dylib']) + b'_TIOOfficialImportTrialInstallUI\0'
        self.assertEqual(p.validate_module(valid)['install'], p.LOAD)
        for data in [macho(filetype=6, install=p.LOAD, minimum=16 << 16), macho(filetype=6, install=p.LOAD, signed=True), macho(filetype=6, install='@rpath/wrong.dylib'), macho(filetype=6, install=p.LOAD, deps=['@rpath/old-stage.dylib']), valid.replace(b'TIOOfficialImportTrialInstallUI', b'TIOOfficialImportTrialWrongName')]:
            with self.assertRaises(ValueError): p.validate_module(data)
    def test_paths_duplicate_symlink_special_and_encrypted_reject(self):
        for name in ['../x', '/abs', 'Payload/../x', 'Payload\\x', 'Payload//x', 'Payload/./x']:
            with self.assertRaises(ValueError): p.validate_entries([entry(name)])
        with self.assertRaises(ValueError): p.validate_entries([entry('x'), entry('x')])
        for mode in [stat.S_IFLNK | 0o777, stat.S_IFIFO | 0o644]:
            with self.assertRaises(ValueError): p.validate_entries([entry('x', mode)])
        e = entry('x'); e.flag_bits = 1
        with self.assertRaises(ValueError): p.validate_entries([e])
        with self.assertRaises(ValueError): p.validate_entries([entry(p.ROOT + p.DYLIB)])
        self.assertIn(p.ROOT + 'Frameworks/TurboIOAIHotDailyStage.dylib', p.validate_entries([entry(p.ROOT + 'Frameworks/TurboIOAIHotDailyStage.dylib')]))
    def test_signature_white_list(self):
        for n in [p.ROOT + '_CodeSignature/', p.ROOT + 'Frameworks/x/_CodeSignature/CodeResources', p.ROOT + 'embedded.mobileprovision']:
            self.assertTrue(p.signature_resource(n))
        self.assertFalse(p.signature_resource(p.ROOT + 'not_CodeSignature.json'))
    def test_full_pipeline_permissions_core_bytes_manifest_and_crc(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); source = root / 'tested.ipa'; dylib = root / 'trial.dylib'
            runner = macho(); addon = b'original-addon'; stage = b'tested-stage'
            module = macho(filetype=6, install=p.LOAD, deps=['/usr/lib/libSystem.B.dylib']) + b'_TIOOfficialImportTrialInstallUI\0'
            dylib.write_bytes(module)
            members = {p.ROOT + 'Runner': runner,
                       p.ROOT + 'Info.plist': plistlib.dumps(dict(CFBundleIdentifier='com.duriea.turboio.research', CFBundleShortVersionString='1.0.5', CFBundleVersion='201')),
                       p.ROOT + 'Frameworks/TurboIOPrivateAddon.dylib': addon,
                       p.ROOT + 'Frameworks/TurboIOAIHotDailyStage.dylib': stage,
                       p.ROOT + 'Frameworks/App.framework/App': b'original-App',
                       p.ROOT + 'Frameworks/Flutter.framework/Flutter': b'original-Flutter',
                       p.ROOT + 'opaque-file.dat': b'opaque-do-not-read-parameters',
                       p.ROOT + '_CodeSignature/CodeResources': b'obsolete-seal'}
            with zipfile.ZipFile(source, 'x') as z:
                for n, b in members.items(): z.writestr(entry(n, stat.S_IFREG | (0o755 if n.endswith(('Runner', '.dylib')) else 0o644)), b)
            digest = p.file_sha(source)
            with patch.multiple(p, BASE_SHA=digest, RUNNER_SHA=p.sha(runner), OLD_SHA=p.sha(addon), STAGE_SHA=p.sha(stage)), contextlib.redirect_stdout(io.StringIO()):
                report = p.prepare(source, dylib, root / 'new')
                with self.assertRaises(ValueError): p.prepare(source, dylib, root / 'new')
                with patch.object(p, 'STAGE_SHA', 'wrong-tested-stage'):
                    with self.assertRaisesRegex(ValueError, 'stage'): p.prepare(source, dylib, root / 'bad-stage')
                self.assertFalse((root / 'bad-stage').exists())
            self.assertEqual(report['unchanged_count'], 6)
            self.assertEqual(len(report['added']), 2)
            self.assertEqual([v['path'] for v in report['changed']], [p.ROOT + 'Runner'])
            self.assertEqual(report['removed'], [p.ROOT + '_CodeSignature/CodeResources'])
            self.assertEqual(p.file_sha(source), digest)
            with zipfile.ZipFile(root / 'new' / 'TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa') as z:
                self.assertIsNone(z.testzip())
                for n, b in members.items():
                    if n != p.ROOT + 'Runner' and not p.signature_resource(n): self.assertEqual(z.read(n), b)
                self.assertEqual(z.getinfo(p.ROOT + 'Runner').external_attr >> 16, stat.S_IFREG | 0o755)
                self.assertEqual(z.getinfo(p.ROOT + 'Frameworks/' + p.DYLIB).external_attr >> 16, stat.S_IFREG | 0o755)
            self.assertTrue((root / 'new' / 'official-import-trial-manifest.json').exists())
            self.assertFalse(report['startup_hooks'])
            self.assertEqual(report['phase_b'], 'requires_phase_a_public_fixture_profile_and_one_user_confirm')
            broken_runner = macho(deps=['@rpath/missing.framework/missing', p.STAGE_LOAD])
            broken = dict(members); broken[p.ROOT + 'Runner'] = broken_runner
            bad_source = root / 'missing-dependency.ipa'
            with zipfile.ZipFile(bad_source, 'x') as z:
                for n, b in broken.items(): z.writestr(entry(n), b)
            with patch.multiple(p, BASE_SHA=p.file_sha(bad_source), RUNNER_SHA=p.sha(broken_runner), OLD_SHA=p.sha(addon), STAGE_SHA=p.sha(stage)):
                with self.assertRaisesRegex(ValueError, 'Missing baseline dependency'): p.prepare(bad_source, dylib, root / 'bad-dependency')
            self.assertFalse((root / 'bad-dependency').exists())
    def test_exact_base_hash_and_nooutput_before_invalidinput(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); source = root / 'x'; source.write_bytes(b'wrong-base'); module = root / 'm'; module.write_bytes(b'invalid')
            with self.assertRaisesRegex(ValueError, 'stage input'): p.prepare(source, module, root / 'new')
            self.assertFalse((root / 'new').exists())
            with self.assertRaisesRegex(ValueError, 'new separate'): p.prepare(source, module, root)


if __name__ == '__main__':
    unittest.main(verbosity=2)
