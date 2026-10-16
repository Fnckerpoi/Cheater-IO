from pathlib import Path
import plistlib
import tempfile
import unittest
from test_lab import load_lab
from test_package import fixture

class InfoNamespaceBoundary(unittest.TestCase):
    def test_official_analytics_personalization_key_is_not_tio_namespace(self):
        lab=load_lab()
        with tempfile.TemporaryDirectory() as temp:
            app,pins=fixture(Path(temp).resolve())
            path=app/'Info.plist';info=plistlib.loads(path.read_bytes())
            info['GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS']=False
            path.write_bytes(plistlib.dumps(info))
            result=lab.preflight(app,'public-20261015',pins,True)
            self.assertEqual(result['status'],'SYNTHETIC_PREFLIGHT_ONLY')
            self.assertIn('GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS',plistlib.loads(path.read_bytes()))

    def test_explicit_tio_namespace_and_shared_capabilities_remain_denied(self):
        lab=load_lab()
        for key in ('TIOManuscriptLabRunID','TIOExperimentalOTABuild','TurboIOPrivateBootstrap','tio.customMarker',
                    'com.duriea.turboio.custom','com.apple.security.application-groups','iCloudContainerIdentifiers',
                    'NSUbiquitousContainers','keychain-access-groups','AppGroupIdentifier','ApplicationGroupIdentifier'):
            with self.subTest(key=key),tempfile.TemporaryDirectory() as temp:
                app,pins=fixture(Path(temp).resolve())
                path=app/'Info.plist';info=plistlib.loads(path.read_bytes());info[key]='public-test'
                path.write_bytes(plistlib.dumps(info))
                with self.assertRaisesRegex(ValueError,'shared/custom'):
                    lab.preflight(app,'public-20261015',pins,True)

if __name__=='__main__':unittest.main(verbosity=2)
