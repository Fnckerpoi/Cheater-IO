import json, pathlib, sys, tempfile, unittest, zipfile
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import addon_assets as a
import embed_manifest
import dsh_manifest


class AaPayloadAssemblyTest(unittest.TestCase):
    def test_bundled_payload_is_validated_and_listed(self):
        found = a.aa_assets()
        self.assertEqual(sorted(found), [a.AA_PAYLOAD, a.AA_SIDECAR])
        self.assertTrue(all(p.is_file() for p in found.values()))

    def test_payload_and_source_are_tracked_by_git(self):
        import subprocess
        root = pathlib.Path(__file__).resolve().parents[2]
        out = subprocess.run(['git', 'ls-files', 'android-addon/assets/aa', 'android-addon/aa-embedded/source'],
                             cwd=root, capture_output=True, text=True, check=True).stdout.split()
        self.assertIn('android-addon/assets/aa/aa-embedded-payload.apk', out)
        self.assertIn('android-addon/assets/aa/payload.json', out)
        self.assertIn('android-addon/aa-embedded/source/android/app/src/embeddedDebug/java/com/agentsanywhere/embedded/PayloadEntry.kt', out)

    def test_sidecar_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            d = pathlib.Path(d)
            with zipfile.ZipFile(d / 'aa-embedded-payload.apk', 'w') as z:
                z.writestr('resources.arsc', b'x'); z.writestr('classes.dex', b'dex\n')
            (d / 'payload.json').write_text(json.dumps({'schemaVersion': 1, 'abiVersion': 1, 'entry': a.AA_ENTRY, 'sha256': '0' * 64}))
            with self.assertRaises(ValueError):
                a.aa_assets(d)

    def test_native_libraries_are_rejected(self):
        import hashlib
        with tempfile.TemporaryDirectory() as d:
            d = pathlib.Path(d)
            with zipfile.ZipFile(d / 'aa-embedded-payload.apk', 'w') as z:
                z.writestr('resources.arsc', b'x'); z.writestr('classes.dex', b'dex\n'); z.writestr('lib/arm64-v8a/x.so', b'x')
            digest = hashlib.sha256((d / 'aa-embedded-payload.apk').read_bytes()).hexdigest()
            (d / 'payload.json').write_text(json.dumps({'schemaVersion': 1, 'abiVersion': 1, 'entry': a.AA_ENTRY, 'sha256': digest}))
            with self.assertRaises(ValueError):
                a.aa_assets(d)

    def test_missing_directory_means_no_payload(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(a.aa_assets(d), {})

    def test_manifest_patch_adds_activity_only_when_embedded(self):
        self.assertIs(a.manifest_patch(False), dsh_manifest.shared_patch)
        self.assertNotEqual(a.manifest_patch(True), dsh_manifest.shared_patch)


if __name__ == '__main__':
    unittest.main()
