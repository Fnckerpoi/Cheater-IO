"""Official host assembly must bind AA assets and its exact manifest profile."""
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import background_manifest as ax
from BackgroundManifestTest import _base_manifest
from tmig1_coexist import parse

PAYLOAD = 'assets/turboio/aa/aa-embedded-payload.apk'
SIDECAR = 'assets/turboio/aa/payload.json'
ENTRY = 'com.agentsanywhere.embedded.PayloadEntry'


def host_manifest():
    chunks = ax._chunks(_base_manifest())
    strings = ax._decode_pool(chunks[0])[0]
    chunks[0], strings, ix = ax._append_pool_strings(chunks[0], strings, ['label', 'Original'])
    ids = ax._attr_resource_map(chunks[1])
    ids.extend([0] * (len(strings) - len(ids)))
    ids[ix['label']] = 0x01010001
    chunks[1] = struct.pack('<HHI', 0x180, 8, 8 + 4 * len(ids)) + struct.pack('<' + 'I' * len(ids), *ids)
    app = next(i for i, c in enumerate(chunks) if ax._node_name(c) == ix['application'])
    attrs = ax._attributes(chunks[app])[0]
    chunks[app] = ax._begin(ix['application'], [ax._attr_record(ix[ax.NS], ix['label'], ix['Original'], 3, ix['Original']), *attrs])
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body


class OfficialEmbeddedAssemblyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='official-aa-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source, self.assembled, self.dex, self.output, self.payload, self.sidecar = [
            self.root / name for name in ('source.apk', 'assembled.apk', 'addon.dex', 'output.apk', 'aa.apk', 'payload.json')]
        self.original = {'classes.dex': b'dex\nvendor1', 'classes2.dex': b'dex\nvendor2',
                         'classes3.dex': b'dex\nvendor3', 'classes4.dex': b'dex\nvendor4',
                         'AndroidManifest.xml': host_manifest(), 'resources.arsc': b'original resources',
                         'lib/arm64-v8a/libapp.so': b'original native', 'res/A.xml': b'UPPER', 'res/a.xml': b'lower'}
        self.write_zip(self.source, self.original)
        self.write_zip(self.assembled, {'classes2.dex': b'dex\nturboioOriginal_s', 'classes3.dex': b'dex\nturboioOriginal_onPostResume'})
        self.dex.write_bytes(b'dex\naddon')
        self.make_payload()
        self.flags = ['--public-assets', '--hook-dex', 'classes2.dex', '--hook-dex', 'classes3.dex',
                      '--payload-apk', str(self.payload), '--payload-sidecar', str(self.sidecar), '--cheater-io']

    @staticmethod
    def write_zip(path, files):
        with zipfile.ZipFile(path, 'w') as apk:
            for name, data in files.items():
                apk.writestr(name, data)

    def make_payload(self, native=False):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as apk:
            apk.writestr('classes.dex', b'dex\npayload')
            apk.writestr('resources.arsc', b'payload resources')
            if native:
                apk.writestr('lib/arm64-v8a/unsupported.so', b'native')
        self.payload.write_bytes(data.getvalue())
        self.sidecar.write_text(json.dumps({'schemaVersion': 1, 'abiVersion': 1, 'entry': ENTRY,
                                           'sha256': hashlib.sha256(data.getvalue()).hexdigest()}), encoding='utf-8')

    def assemble(self, flags=None):
        return subprocess.run([sys.executable, str(ROOT / 'assemble_apk.py'), str(self.source),
                               str(self.assembled), str(self.dex), str(self.output),
                               *(self.flags if flags is None else flags)], capture_output=True, text=True)

    def audit(self, output=None, flags=None):
        return subprocess.run([sys.executable, str(ROOT / 'verify_apk.py'), str(self.source),
                               str(output or self.output), *(self.flags if flags is None else flags)], capture_output=True, text=True)

    def test_multidex_payload_and_manifest_are_packaged_and_audited(self):
        result = self.assemble()
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            for name, value in self.original.items():
                if name not in ('classes2.dex', 'classes3.dex', 'AndroidManifest.xml'):
                    self.assertEqual(apk.read(name), value, name)
            self.assertEqual(apk.read('classes5.dex'), self.dex.read_bytes())
            self.assertEqual(apk.read(PAYLOAD), self.payload.read_bytes())
            self.assertEqual(apk.read(SIDECAR), self.sidecar.read_bytes())
            nodes = parse(apk.read('AndroidManifest.xml'))[3]
            app = next(attrs for _, tag, attrs in nodes if tag == 'application')
            self.assertIn(('label', 'Cheater-IO'), [(a['name'], a['value']) for a in app])
            activities = [{a['name']: a['value'] for a in attrs} for _, tag, attrs in nodes if tag == 'activity']
            self.assertEqual([a for a in activities if a.get('name') == 'com.turboio.addon.AaEmbeddedActivity'],
                             [{'name': 'com.turboio.addon.AaEmbeddedActivity', 'exported': 0}])
            self.assertEqual(sum(a.get('name') == 'com.turboio.addon.DshAuthCallbackActivity' for a in activities), 1)
        result = self.audit()
        self.assertEqual(result.returncode, 0, result.stderr)
        audit = json.loads(result.stdout)
        self.assertTrue(audit['manifest_only_tts_and_background_added'])
        self.assertTrue(audit['resources_native_libraries_preserved'])
        self.assertEqual(audit['unexpected_paths'], [])

    def test_public_profile_without_payload_preserves_label_and_has_no_aa_entry(self):
        flags = self.flags[:5]
        result = self.assemble(flags)
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            self.assertNotIn(PAYLOAD, apk.namelist())
            self.assertNotIn(SIDECAR, apk.namelist())
            nodes = parse(apk.read('AndroidManifest.xml'))[3]
            self.assertFalse(any(a['value'] == 'com.turboio.addon.AaEmbeddedActivity' for _, _, attrs in nodes for a in attrs))
            app = next(attrs for _, tag, attrs in nodes if tag == 'application')
            self.assertIn(('label', 'Original'), [(a['name'], a['value']) for a in app])
        result = self.audit(flags=flags)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_payload_mismatch_and_native_library_fail_before_output(self):
        self.payload.write_bytes(self.payload.read_bytes() + b'changed')
        result = self.assemble()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('sidecar mismatch', result.stderr)
        self.assertFalse(self.output.exists())
        self.make_payload(native=True)
        result = self.assemble()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('native payload libraries unsupported', result.stderr)
        self.assertFalse(self.output.exists())

    def test_payload_arguments_must_be_paired(self):
        for name, value in (('--payload-apk', self.payload), ('--payload-sidecar', self.sidecar)):
            result = self.assemble(self.flags[:5] + [name, str(value)])
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stderr.strip(),
                             'Payload requires both paths; payload and branding require --public-assets')
            self.assertFalse(self.output.exists())

    def test_audit_rejects_missing_or_changed_payload_and_manifest(self):
        result = self.assemble()
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            good = {name: apk.read(name) for name in apk.namelist()}
        for name in (PAYLOAD, SIDECAR, 'AndroidManifest.xml'):
            for missing in (False, True):
                with self.subTest(name=name, missing=missing):
                    files = dict(good)
                    if missing:
                        del files[name]
                    else:
                        files[name] = self.original[name] if name == 'AndroidManifest.xml' else b'changed'
                    broken = self.root / 'broken.apk'
                    self.write_zip(broken, files)
                    result = self.audit(broken)
                    self.assertNotEqual(result.returncode, 0, result.stdout)
        result = self.audit(flags=self.flags[:5])
        self.assertNotEqual(result.returncode, 0, 'AA assets require an explicit audit profile')


if __name__ == '__main__':
    unittest.main()
