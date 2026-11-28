#!/usr/bin/env python3
"""Run the production auto-run state machine with a deterministic fake environment on macOS."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='turbo-ota-autorun-') as tmp:
    executable = Path(tmp) / 'autorun'
    subprocess.run(['xcrun', 'clang', '-fobjc-arc', '-fblocks', '-framework', 'Foundation',
                    '-I', str(root), str(root / 'ExperimentalOTAAutoRun.m'),
                    str(root / 'ExperimentalOTAAutoRunTests.m'),
                    str(root / 'tests/ota_autorun_stub.m'), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)
