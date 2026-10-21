"""Focused credential audit of owned source/DEX and allowlisted public assets.

Does not inspect user key stores, compare against actual private keys, or log values.
Not a substitute for reviewing new credential formats and the public release diff.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from addon_assets import assets
from dex_slot import addon_dex_slot

parser = argparse.ArgumentParser()
parser.add_argument('apk')
parser.add_argument('--dex', type=Path, default=ROOT / 'build/dex/classes.dex')
parser.add_argument('--source', type=Path)
args = parser.parse_args()

PATTERNS = [
    rb'\bsk-(?:ws-|tinyfish-)?[A-Za-z0-9_.-]{16,}',
    rb'\bwrk-[A-Za-z0-9_-]{16,}',
    rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    rb'\b(?:MUSIC_U|wr_skey|wr_vid|wr_fp)=[A-Za-z0-9%_.-]{12,}',
    rb'(?i)["\'](?:api_key|apiKey|cookie)["\']\s*:\s*["\'][A-Za-z0-9_.=-]{24,}["\']',
]
hits = set()
count = 0

def check(name, content):
    global count
    count += 1
    if any(re.search(p, content) for p in PATTERNS):
        hits.add(name)

for path in sorted((ROOT / 'src').rglob('*.java')):
    check(str(path.relative_to(ROOT)), path.read_bytes())

with zipfile.ZipFile(args.apk) as apk:
    expected_dex = args.dex
    expected_bytes = expected_dex.read_bytes()
    matching_slots = [name for name in apk.namelist() if re.fullmatch(r'classes(?:[2-9][0-9]*|1[0-9]+)?\.dex', name) and apk.read(name) == expected_bytes]
    assert len(matching_slots) == 1, 'Expected addon DEX must appear exactly once'
    addon_slot = matching_slots[0]
    if args.source:
        with zipfile.ZipFile(args.source) as original:
            assert addon_slot == addon_dex_slot(original.namelist()), 'Addon slot differs from source-derived slot'
            assert addon_slot not in original.namelist(), 'Addon DEX overwrites vendor entry'
    check('APK/' + addon_slot, apk.read(addon_slot))
    for name, path in assets().items():
        data = apk.read(name)
        assert data == path.read_bytes(), f'Asset differs from reviewed source: {name}'
        check(name, data)
        if name.endswith('.zip'):
            with zipfile.ZipFile(io.BytesIO(data)) as nested:
                assert sorted(nested.namelist()) == ['app.json', 'manifest.json']
                for item in nested.namelist():
                    check(name + '/' + item, nested.read(item))
    classes = apk.read(addon_slot)
    assert b'Lcom/turboio/addon/ReaderWeb;' not in classes, 'Excluded web adapter packaged'
    assert b'Lcom/turboio/addon/ReaderWebProtocol;' not in classes, 'Excluded web protocol packaged'
    assert b'weread_cookie' not in classes, 'Excluded web credential entry packaged'
    assert not re.search(rb'llm-[a-z0-9-]+\.[a-z0-9.-]*aliyuncs\.com', classes), 'Private tenant packaged'
    assert b'Lcom/turboio/addon/CardFlowTest;' not in classes, 'Test class packaged'
    assert b'Lcom/turboio/addon/MusicFlowTest;' not in classes, 'Music test class packaged'
    assert b'FakeMain' not in classes, 'Test transport double packaged'
    assert classes == expected_bytes, 'Stale DEX packaged'

result = {'checked_owned_inputs': count, 'credential_pattern_hits': sorted(hits),
          'public_assets_match_sources': True, 'test_doubles_absent': True,
          'addon_dex_sha256': hashlib.sha256(classes).hexdigest()}
print(json.dumps(result, ensure_ascii=False))
if hits:
    raise SystemExit(1)
