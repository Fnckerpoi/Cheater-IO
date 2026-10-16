import importlib.util
from pathlib import Path
import tempfile
import unittest


def load_lab():
    spec = importlib.util.spec_from_file_location('lab', Path(__file__).with_name('lab.py'))
    lab = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lab)
    return lab


class IdentityBoundary(unittest.TestCase):
    def test_explicit_lab_identity_rejects_daily_target_and_invalid_run(self):
        lab = load_lab()
        self.assertEqual(lab.identity('public-20261014')['bundle'], 'com.duriea.turboio.manuscriptlab105.public-20261014')
        for run in ('', '../private', '*', 'COM', 'research', 'official'):
            with self.assertRaises(ValueError):
                lab.identity(run)


class SigningAndPathBoundary(unittest.TestCase):
    def test_signing_plan_has_no_shared_or_inherited_permissions(self):
        lab = load_lab()
        plan = lab.signing_plan('public-20261014', 'AB12CD34EF')
        self.assertEqual(set(plan), {'application-identifier', 'com.apple.developer.team-identifier', 'get-task-allow'})
        self.assertEqual(plan['application-identifier'], 'AB12CD34EF.com.duriea.turboio.manuscriptlab105.public-20261014')
        for extra in ({'com.apple.security.application-groups': ['group.shared']}, {'keychain-access-groups': ['AB12CD34EF.shared']}, {'com.apple.developer.icloud-container-identifiers': ['iCloud.shared']}, {'aps-environment': 'development'}, {'unexpected': True}):
            with self.assertRaises(ValueError):
                lab.check_entitlements(dict(plan, **extra), 'public-20261014', 'AB12CD34EF')
        with self.assertRaises(ValueError):
            lab.check_entitlements(dict(plan, **{'application-identifier': 'AB12CD34EF.*'}), 'public-20261014', 'AB12CD34EF')

    def test_nested_metadata_refuses_inherited_app_capabilities(self):
        lab=load_lab()
        self.assertTrue(lab.check_nested_entitlements({}))
        for value in (None, [], {'keychain-access-groups':['TEAM.shared']}, {'application-identifier':'TEAM.lab'}):
            with self.assertRaises(ValueError): lab.check_nested_entitlements(value)

    def test_paths_reject_symlinks_escapes_and_unknown_storage(self):
        lab = load_lab()
        with tempfile.TemporaryDirectory() as d:
            home = Path(d).resolve() / 'lab'
            allowed = home / 'Library/Application Support/ManuscriptLab'
            allowed.mkdir(parents=True)
            self.assertEqual(lab.safe_path(home, allowed), allowed.resolve())
            for bad in (home / 'Documents', home / '../outside', Path(d), home / 'Library/Application Support/ManuscriptLab/../outside'):
                with self.assertRaises(ValueError):
                    lab.safe_path(home, bad)
            (allowed / 'link').symlink_to(Path(d))
            with self.assertRaises(ValueError):
                lab.safe_path(home, allowed / 'link')


if __name__ == '__main__':
    unittest.main(verbosity=2)
