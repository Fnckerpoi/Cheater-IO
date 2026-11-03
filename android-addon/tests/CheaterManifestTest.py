import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import background_manifest as ax
import cheater_manifest as cm
from tmig1_coexist import parse

NS = 'http://schemas.android.com/apk/res/android'


def fixture(utf8, label_type=1, include_label=True):
    strings = ['manifest', 'application', 'package', 'com.example.host', NS, 'label', 'Original', 'activity']
    encoded = []
    for s in strings:
        b = s.encode('utf-8' if utf8 else 'utf-16le')
        encoded.append((bytes([len(s), len(b)]) + b + b'\0') if utf8 else (struct.pack('<H', len(s)) + b + b'\0\0'))
    offsets, raw = [], b''
    for item in encoded:
        offsets.append(len(raw)); raw += item
    raw += b'\0' * (-len(raw) % 4)
    start = 28 + 4 * len(strings)
    pool = struct.pack('<HHIIIIII', 1, 28, start + len(raw), len(strings), 0, 0x100 if utf8 else 0, start, 0)
    pool += struct.pack('<' + 'I' * len(offsets), *offsets) + raw
    package = ax._attr_record(ax.U, 2, 3, 3, 3)
    label = ax._attr_record(4, 5, 6 if label_type == 3 else ax.U, label_type, 6 if label_type == 3 else 0x7f120001)
    chunks = [pool, ax._begin(0, [package]), ax._begin(1, [label] if include_label else []), ax._begin(7, [label]), ax._end(7), ax._end(1), ax._end(0)]
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body


class CheaterManifestTest(unittest.TestCase):
    def test_only_application_label_changes(self):
        for utf8 in (False, True):
            for label_type in (1, 3):
                with self.subTest(utf8=utf8, label_type=label_type):
                    original = fixture(utf8, label_type)
                    patched = cm.patch(original)
                    before, pool_index, strings, nodes = parse(original)
                    after, _, new_strings, new_nodes = parse(patched)
                    self.assertEqual(new_strings[:len(strings)], strings)
                    self.assertEqual(new_strings[-1], 'Cheater-IO')
                    app_index = next(i for i, tag, _ in nodes if tag == 'application')
                    attrs = next(a for _, tag, a in new_nodes if tag == 'application')
                    self.assertEqual([(a['name'], a['type'], a['value']) for a in attrs], [('label', 3, 'Cheater-IO')])
                    for i in range(len(before)):
                        if i not in (pool_index, app_index):
                            self.assertEqual(before[i], after[i])
                    old_app, new_app = before[app_index], after[app_index]
                    self.assertEqual(old_app[:44], new_app[:44])
                    self.assertEqual(cm.patch(patched), patched)

    def test_missing_label_rejected(self):
        with self.assertRaises(ValueError):
            cm.patch(fixture(True, include_label=False))


if __name__ == '__main__':
    unittest.main()
