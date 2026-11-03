"""AA payload assets and the shared public manifest profile.

Inputs are always explicit: the allowlist must never discover a foreign checkout on
its own, and the AA-free profile must keep producing the previous build.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import addon_assets
import background_manifest as ax
import dsh_manifest as dsh
import embed_manifest as workbench
from addon_assets import assets
from BackgroundManifestTest import _base_manifest
from embedded_payload import ENTRY, PAYLOAD, SIDECAR, validate_payload
from tmig1_coexist import parse

LOADER = Path(addon_assets.ROOT) / 'android-addon/src/com/turboio/addon/AaPayloadLoader.java'


def payload_apk(native=False, padding=0):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as apk:
        apk.writestr('classes.dex', b'dex\npayload')
        apk.writestr('resources.arsc', b'payload resources')
        if padding:
            apk.writestr('assets/padding.bin', bytes(padding))
        if native:
            apk.writestr('lib/arm64-v8a/unsupported.so', b'native')
    return buffer.getvalue()


def sidecar(data):
    return json.dumps({'schemaVersion': 1, 'abiVersion': 1, 'entry': ENTRY,
                       'sha256': hashlib.sha256(data).hexdigest()}).encode()


class AaPublicAssetTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='aa-assets-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.payload = self.root / 'aa-embedded-payload.apk'
        self.sidecar = self.root / 'payload.json'
        self.payload.write_bytes(payload_apk())
        self.sidecar.write_bytes(sidecar(self.payload.read_bytes()))
        self.aa_free = assets()

    def test_payload_inputs_are_explicit(self):
        signature = assets.__code__.co_varnames[:assets.__code__.co_argcount]
        self.assertEqual(signature, ('payload_apk', 'payload_sidecar'))
        self.assertEqual(assets.__defaults__, (None, None))
        self.assertNotIn(PAYLOAD, self.aa_free)
        self.assertNotIn(SIDECAR, self.aa_free)
        source = Path(addon_assets.__file__).read_text(encoding='utf-8')
        for implicit in ('Agents-Anywhere', 'build/outputs', 'os.environ', 'getenv'):
            self.assertNotIn(implicit, source, implicit)

    def test_aa_free_profile_is_unchanged(self):
        self.assertEqual(len(self.aa_free), 35)
        self.assertFalse([name for name in self.aa_free if '/aa/' in name])
        self.assertLess(sum(path.stat().st_size for path in self.aa_free.values()), 24 * 1024 * 1024)
        self.assertEqual(assets(), assets(payload_apk=None, payload_sidecar=None))

    def test_payload_pair_binds_the_contract_names(self):
        self.payload.write_bytes(payload_apk(padding=5 * 1024 * 1024))
        self.sidecar.write_bytes(sidecar(self.payload.read_bytes()))
        with_payload = assets(self.payload, self.sidecar)
        self.assertEqual(set(with_payload) - set(self.aa_free), {PAYLOAD, SIDECAR})
        self.assertEqual(with_payload[PAYLOAD].read_bytes(), self.payload.read_bytes())
        self.assertEqual(with_payload[SIDECAR].read_bytes(), self.sidecar.read_bytes())
        # The payload pair is one externally built artifact, so the reviewed allowlist
        # budget cannot size it; that ceiling keeps guarding the repository set.
        self.assertGreater(sum(path.stat().st_size for path in with_payload.values()), 24 * 1024 * 1024)
        self.assertLess(sum(path.stat().st_size for path in self.aa_free.values()), 24 * 1024 * 1024)
        prefix = LOADER.read_text(encoding='utf-8').split('PREFIX="')[1].split('"')[0]
        for name in (PAYLOAD, SIDECAR):
            self.assertTrue(name.startswith('assets/'), name)
            self.assertEqual(name[len('assets/'):], prefix + Path(name).name, name)

    def test_halves_are_rejected(self):
        for value in (self.payload, self.sidecar):
            other = None if value == self.payload else self.payload
            with self.assertRaises(ValueError):
                assets(value, other)

    def test_bad_payload_fails_before_assets_are_returned(self):
        self.sidecar.write_bytes(sidecar(self.payload.read_bytes() + b'trailing'))
        with self.assertRaises(ValueError):
            assets(self.payload, self.sidecar)
        self.payload.write_bytes(payload_apk(native=True))
        self.sidecar.write_bytes(sidecar(self.payload.read_bytes()))
        with self.assertRaises(ValueError):
            assets(self.payload, self.sidecar)

    def test_payload_is_audited_by_the_shared_contract(self):
        data = self.payload.read_bytes()
        self.assertEqual(validate_payload(data, self.sidecar.read_bytes())['entry'], ENTRY)
        for corrupt in (data + b'x', data[:len(data) // 2]):
            with self.assertRaises(ValueError):
                validate_payload(corrupt, self.sidecar.read_bytes())

    def test_manifest_profile_without_payload_matches_legacy(self):
        original = _base_manifest()
        legacy = dsh.shared_patch(original)
        self.assertEqual(dsh.shared_patch(original, embedded=False), legacy)
        self.assertEqual(dsh.shared_patch(legacy, embedded=False), legacy)
        names = {a['value'] for _, tag, attrs in parse(legacy)[3] if tag == 'activity'
                 for a in attrs if a['name'] == 'name'}
        self.assertEqual(names, {dsh.ACTIVITY})

    def test_embedded_profile_adds_only_the_workbench_activity(self):
        original = _base_manifest()
        plain = dsh.shared_patch(original)
        embedded = dsh.shared_patch(original, embedded=True)
        self.assertEqual(embedded, workbench.patch(plain))
        self.assertEqual(dsh.shared_patch(embedded, embedded=True), embedded)
        chunks_before = [c for c in ax._chunks(plain) if c[:2] in (b'\x02\x01', b'\x03\x01')]
        chunks_after = [c for c in ax._chunks(embedded) if c[:2] in (b'\x02\x01', b'\x03\x01')]
        iterator = iter(chunks_after)
        for chunk in chunks_before:
            self.assertTrue(any(candidate == chunk for candidate in iterator))
        self.assertEqual(len(chunks_after), len(chunks_before) + 2)
        activities = [{a['name']: a['value'] for a in attrs} for _, tag, attrs in parse(embedded)[3] if tag == 'activity']
        self.assertEqual([a for a in activities if a.get('name') == workbench.ACTIVITY],
                         [{'name': workbench.ACTIVITY, 'exported': 0}])
        self.assertEqual(sum(a.get('name') == dsh.ACTIVITY for a in activities), 1)


if __name__ == '__main__':
    unittest.main()
