import sys
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import coexist107 as c

class CoexistTest(unittest.TestCase):
    def test_real_manifest(self):
        with zipfile.ZipFile(source) as z: result, changes = c.patch(z.read('AndroidManifest.xml'))
        nodes = c.parse(result)[3]
        authorities = [a['value'] for _, tag, attrs in nodes if tag == 'provider' for a in attrs if a['name'] == 'authorities']
        self.assertEqual(len(authorities), 10)
        self.assertTrue(all(x.startswith(c.NEW + '.') for x in authorities))
        self.assertEqual(len(authorities), len(set(authorities)))
        self.assertTrue(any(tag == 'activity' and a['name'] == 'name' and a['value'] == 'com.rayneo.venus.MainActivity' for _, tag, attrs in nodes for a in attrs))
        self.assertEqual(len(changes), 40)
        self.assertTrue(all('namespace' in x for x in changes))
        self.assertRaises(ValueError, c.patch, result)

if __name__ == '__main__':
    source = sys.argv[1]
    unittest.main(argv=[sys.argv[0]])
