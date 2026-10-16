#!/usr/bin/env python3
"""Actual archive audit plus negative tests for the NewsHub package boundary."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import unittest
import zipfile

ROOT = 'Payload/Runner.app/'
EXPERIMENTS = ('TurboIOAIHotDailyStage', 'TurboIOOfficialImportTrial')
SOURCE = Path(__file__).parent / 'build/newshub-clean-20261014-135831/TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa'


def load_package():
    spec = importlib.util.spec_from_file_location('news_package', Path(__file__).with_name('package-newshub.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def dependencies(data):
    assert struct.unpack_from('<II', data) == (0xfeedfacf, 0x100000c)
    pos = 32
    result = []
    for _ in range(struct.unpack_from('<I', data, 16)[0]):
        cmd, size = struct.unpack_from('<II', data, pos)
        if cmd in (0xc, 0x80000018, 0x8000001f, 0x80000023):
            offset = struct.unpack_from('<I', data, pos + 8)[0]
            result.append(data[pos + offset:pos + size].split(b'\0')[0].decode())
        pos += size
    return result


def defined_symbols(data):
    pos = 32
    for _ in range(struct.unpack_from('<I', data, 16)[0]):
        cmd, size = struct.unpack_from('<II', data, pos)
        if cmd == 2:
            symoff, count, stroff, strsize = struct.unpack_from('<4I', data, pos + 8)
            result = set()
            for i in range(count):
                name, typ, _, _, value = struct.unpack_from('<IBBHQ', data, symoff + i * 16)
                if typ & 0xe != 0 and typ & 0xe0 == 0:
                    end = data.index(0, stroff + name, stroff + strsize)
                    result.add(data[stroff + name:end].decode())
            return result
        pos += size
    return set()


def audit_actual(ipa):
    with zipfile.ZipFile(ipa) as archive:
        names = archive.namelist()
        leftovers = [n for n in names if any(token in n for token in EXPERIMENTS)]
        deps = dependencies(archive.read(ROOT + 'Runner'))
        live = [d for d in deps if any(token in d for token in EXPERIMENTS)]
        assert not leftovers and not live, f'Experimental pages still packaged/loadable: {leftovers}; {live}'
        assert deps[-1] == '@rpath/TurboIONewsHub.dylib'
        assert deps[-2] == '@executable_path/Frameworks/TurboIOPrivateAddon.dylib'
        module = archive.read(ROOT + 'Frameworks/TurboIONewsHub.dylib')
        symbols = defined_symbols(module)
        expected = {'_TIONewsHubInstallUI', '_TIONewsHubNavigationInstall', '_OBJC_CLASS_$_TIONewsHubPanel', '_OBJC_CLASS_$_TIOAIHotDailyClient'}
        assert expected <= symbols, 'NewsHub module misses real defined entry/classes'
        forbidden = [s for s in symbols if any(k in s for k in ('AIHotStage', 'DailyStage', 'ImportTrial', 'OfficialManuscriptProbe')) or s in ('_OBJC_CLASS_$_TIONewsReader', '_OBJC_CLASS_$_TIOResearchTabs')]
        assert not forbidden, f'Experiment or duplicate host classes: {forbidden}'
        addon = archive.read(ROOT + 'Frameworks/TurboIOPrivateAddon.dylib')
        assert hashlib.sha256(addon).hexdigest() == '7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503'
        marker = archive.read(ROOT + 'TurboIONewsHub.json')
        marker_info = json.loads(marker)
        assert marker_info['module'] == 'NewsHub'
        assert marker_info['module_sha256'] == hashlib.sha256(module).hexdigest()
        assert marker_info['experimental_pages_removed'] is True
        assert marker_info['device_verified'] is False
    p = load_package()
    p.validate_module(module)
    with zipfile.ZipFile(SOURCE) as baseline:
        original_runner = baseline.read(ROOT + 'Runner')
    assert marker_info['source_ipa_sha256'] == p.BASE_SHA
    p.verify_archive(SOURCE, ipa, module, p.patch_runner(original_runner), marker)
    assert p.file_sha(SOURCE) == p.BASE_SHA
    print('ACTUAL_PACKAGE_GREEN: no experimental members/load chain/classes; executable constructor/entries; exact member diff; every retained member SHA/mode unchanged; source unchanged; CRC PASS')


class PackageBoundaryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p = load_package()
        with zipfile.ZipFile(SOURCE) as z:
            cls.runner = z.read(ROOT + 'Runner')
            cls.old_stage = z.read(ROOT + 'Frameworks/TurboIOAIHotDailyStage.dylib')

    def test_actual_shipped_package_reproduces_experimental_load_chain(self):
        with self.assertRaisesRegex(AssertionError, 'Experimental pages'):
            audit_actual(SOURCE)

    def test_actual_runner_patch_preserves_all_original_ordinals_and_body(self):
        before = self.p.inspect(self.runner)
        patched = self.p.patch_runner(self.runner)
        after = self.p.inspect(patched)
        self.assertEqual(after['deps'], before['deps'][:-2] + ['@rpath/TurboIONewsHub.dylib'])
        self.assertEqual(after['commands'][:-1], before['commands'][:-2])
        self.assertEqual(patched[before['end']:], self.runner[before['end']:])
        self.assertEqual(patched[:16], self.runner[:16])
        self.assertEqual(patched[24:32], self.runner[24:32])
        self.assertEqual(len(patched), len(self.runner))
        self.assertEqual(after['uuid'], before['uuid'])

    def test_unknown_suffix_is_rejected(self):
        bad = bytearray(self.runner)
        struct.pack_into('<I', bad, 16, struct.unpack_from('<I', bad, 16)[0] - 1)
        last = self.p.inspect(self.runner)['commands'][-1][1]
        struct.pack_into('<I', bad, 20, struct.unpack_from('<I', bad, 20)[0] - len(last))
        with self.assertRaisesRegex(ValueError, 'suffix'):
            self.p.patch_runner(bytes(bad))

    def test_classic_deleted_ordinal_binding_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'ordinal'):
            self.p.ensure_binding_ordinals(b'\x20\x9e\x01\x90\x00', {158, 159})

    def test_chained_deleted_ordinal_binding_is_rejected(self):
        for form, stride in ((1, 4), (2, 8), (3, 16)):
            blob = bytearray(struct.pack('<7I', 0, 28, 28, 28 + stride, 1, form, 0) + bytes(stride) + b'_test\0')
            struct.pack_into('<I' if form < 3 else '<Q', blob, 28, 158)
            with self.assertRaisesRegex(ValueError, 'ordinal'):
                self.p.ensure_chained_ordinals(bytes(blob), {158, 159})

    def test_chained_ordinals_follow_sdk_reserved_range_not_signed_int8(self):
        for form, stride in ((1, 4), (2, 8), (3, 16)):
            for ordinal in (0, 157, 158, 159, -1, -2, -3, -4):
                blob = bytearray(struct.pack('<7I', 0, 28, 28, 28 + stride, 1, form, 0) + bytes(stride) + b'_test\0')
                mask = 0xff if form < 3 else 0xffff
                struct.pack_into('<I' if form < 3 else '<Q', blob, 28, ordinal & mask)
                if ordinal in (158, 159, -4):
                    with self.assertRaisesRegex(ValueError, 'ordinal'):
                        self.p.ensure_chained_ordinals(bytes(blob), {158, 159})
                else:
                    self.p.ensure_chained_ordinals(bytes(blob), {158, 159})

    def test_classic_all_supported_special_and_retained_ordinals(self):
        for data in (b'\x20\x9d\x01\x90\x00', b'\x30\x90\x00', b'\x3f\x90\x00', b'\x3e\x90\x00', b'\x3d\x90\x00'):
            self.p.ensure_binding_ordinals(data, {158, 159})
        for data in (b'\x20\x9e\x01\x90\x00', b'\x20\x9f\x01\x90\x00', b'\x3c\x90\x00', b'\xd0'):
            with self.assertRaises(ValueError):
                self.p.ensure_binding_ordinals(data, {158, 159})

    def test_actual_nlist_removed_ordinal_reference_is_rejected(self):
        info = self.p.inspect(self.runner)
        raw = next(raw for cmd, raw in info['commands'] if cmd == 2)
        symoff, count = struct.unpack_from('<2I', raw, 8)
        index = next(i for i in range(count) if self.runner[symoff + i * 16 + 4] & 0xe == 0 and not self.runner[symoff + i * 16 + 4] & 0xe0)
        for ordinal in (158, 159):
            bad = bytearray(self.runner)
            struct.pack_into('<H', bad, symoff + index * 16 + 6, ordinal << 8)
            with self.assertRaisesRegex(ValueError, 'ordinal'):
                self.p.patch_runner(bytes(bad))

    def test_unknown_chained_format_fails_closed(self):
        with self.assertRaises(ValueError):
            self.p.ensure_chained_ordinals(struct.pack('<7I', 0, 28, 28, 28, 0, 4, 0), {158, 159})

    def test_invalid_zip_paths_and_duplicate_members_are_rejected(self):
        for name in ('../escape', '/absolute', 'Payload//bad', 'a\\b'):
            with self.assertRaises(ValueError):
                self.p.validate_entries([zipfile.ZipInfo(name)])
        entry = zipfile.ZipInfo('Payload/Runner.app/Runner')
        with self.assertRaises(ValueError):
            self.p.validate_entries([entry, entry])

    def test_zip_symlink_is_rejected(self):
        entry = zipfile.ZipInfo('link')
        entry.external_attr = 0o120777 << 16
        with self.assertRaises(ValueError):
            self.p.validate_entries([entry])

    def test_empty_and_old_experiment_modules_are_rejected(self):
        for module in (b'', self.old_stage):
            with self.assertRaises(ValueError):
                self.p.validate_module(module)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--audit', type=Path)
    args = parser.parse_args()
    if args.audit:
        audit_actual(args.audit)
    else:
        unittest.main(argv=[__file__], verbosity=2)
