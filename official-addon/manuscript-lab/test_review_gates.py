from pathlib import Path
import struct
import tempfile
import unittest
from test_lab import load_lab
from test_package import fixture, macho

class ReviewTrustBoundaries(unittest.TestCase):
    def setUp(self):
        self.lab=load_lab()
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name).resolve()
        self.app,self.pins=fixture(self.root)
        self.trusted=dict(files=self.lab.inventory(self.app),directories=self.lab.directory_inventory(self.app))
    def tearDown(self): self.temp.cleanup()

    def test_original_manifest_rejects_changed_runner_body_before_output(self):
        before=(self.app/'Runner').read_bytes()
        data=bytearray(before);data[2050]^=1;(self.app/'Runner').write_bytes(data)
        with self.assertRaisesRegex(ValueError,'inventory'):
            self.lab.preflight(self.app,'public-20261014',self.pins,True,expected_inventory=self.trusted)

    def test_extra_framework_and_load_rejected_against_trusted_original(self):
        path=self.app/'Frameworks/Unreviewed.framework/Unreviewed'
        path.parent.mkdir();path.write_bytes(macho(6))
        (self.app/'Runner').write_bytes(macho(deps=('/usr/lib/libSystem.B.dylib','@rpath/Unreviewed.framework/Unreviewed')))
        with self.assertRaisesRegex(ValueError,'inventory'):
            self.lab.preflight(self.app,'public-20261014',self.pins,True,expected_inventory=self.trusted)

    def test_extra_resource_and_missing_resource_rejected(self):
        extra=self.app/'unreviewed.txt';extra.write_text('public extra fixture')
        with self.assertRaisesRegex(ValueError,'inventory'):
            self.lab.preflight(self.app,'public-20261014',self.pins,True,expected_inventory=self.trusted)
        extra.unlink();(self.app/'Frameworks/App.framework/App').unlink()
        with self.assertRaisesRegex(ValueError,'inventory'):
            self.lab.preflight(self.app,'public-20261014',self.pins,True,expected_inventory=self.trusted)

    def test_production_cannot_selfdeclare_fixture_manifest_or_hashes(self):
        with self.assertRaises(ValueError):
            self.lab.preflight(self.app,'public-20261014',expected_inventory=self.trusted)
        with self.assertRaises(ValueError):
            self.lab.trusted_original_manifest(self.root/'missing.ipa')

    def test_tampered_input_is_rejected_before_output_creation(self):
        data=bytearray((self.app/'Runner').read_bytes());data[2051]^=1;(self.app/'Runner').write_bytes(data)
        probe=self.root/'fixture-probe';probe.write_bytes(b'NOT_DEVICE_CODE')
        out=self.root/'must-not-exist'
        with self.assertRaisesRegex(ValueError,'inventory'):
            self.lab.prepare(self.app,probe,out,'public-20261014',self.pins,True,expected_inventory=self.trusted)
        self.assertFalse(out.exists())

    def test_wrong_archive_and_untrusted_manifest_cannot_create_extraction_output(self):
        bad=self.root/'fake.ipa';bad.write_bytes(b'not the approved original')
        out=self.root/'never-extracted'
        with self.assertRaisesRegex(ValueError,'hash'):
            self.lab.extract_original(bad,out)
        self.assertFalse(out.exists())

    def test_zip_traversal_symlink_duplicate_and_unknown_roots_are_rejected(self):
        import zipfile
        def member(name,mode=0o100644):
            entry=zipfile.ZipInfo(name);entry.external_attr=mode<<16
            return entry
        for entries in ([member('../escape')],[member('Payload/Runner.app/link',0o120777)],
                        [member('Payload/Runner.app/file'),member('Payload/Runner.app/file')],
                        [member('other/Runner.app/file')]):
            with self.assertRaises(ValueError):self.lab.safe_zip_entries(entries)

    def test_actual_reviewed_module_tampered_text_is_rejected(self):
        path=Path(__file__).parent/'build/stage-a-reviewed/TurboIOManuscriptLabProbe.dylib'
        if not path.exists():self.skipTest('Approved compiled artifact is unavailable')
        data=path.read_bytes();self.lab.validate_probe(data)
        header=self.lab.inspect(data);bad=bytearray(data)
        for cmd,raw in header['commands']:
            if cmd==0x19:
                for i in range(struct.unpack_from('<I',raw,64)[0]):
                    at=72+i*80
                    if raw[at:at+16].split(b'\0')[0]==b'__text':
                        offset=struct.unpack_from('<I',raw,at+48)[0];bad[offset]^=1
                        with self.assertRaisesRegex(ValueError,'approved'):
                            self.lab.validate_probe(bytes(bad))
                        return
        self.fail('No actual text section')

if __name__=='__main__':unittest.main(verbosity=2)
