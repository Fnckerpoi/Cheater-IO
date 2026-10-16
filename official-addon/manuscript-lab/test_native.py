from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent

class NativeGateBoundary(unittest.TestCase):
    def test_native_identity_and_write_collection_defaults_are_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            exe = Path(temp) / 'native-gate-test'
            subprocess.run(['cc','-Wall','-Wextra','-Werror',str(ROOT/'LabGate.c'),str(ROOT/'LabGateTests.c'),'-o',str(exe)],check=True,capture_output=True,text=True)
            result = subprocess.run([str(exe)],check=True,capture_output=True,text=True)
            self.assertIn('LAB_NATIVE_GATES_PASS',result.stdout)

if __name__ == '__main__': unittest.main(verbosity=2)

class ActualCompiledModuleBoundary(unittest.TestCase):
    def test_actual_sdk_module_uses_only_the_four_observed_system_dependencies(self):
        from test_lab import load_lab
        module = ROOT/'build/stage-a-reviewed/TurboIOManuscriptLabProbe.dylib'
        if not module.exists(): self.skipTest('Run the documented first SDK compile before this integration check')
        lab=load_lab()
        data=module.read_bytes()
        h=lab.validate_probe(data)
        self.assertEqual(set(h['deps']), {'/System/Library/Frameworks/Foundation.framework/Foundation','/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation','/usr/lib/libobjc.A.dylib','/usr/lib/libSystem.B.dylib'})
