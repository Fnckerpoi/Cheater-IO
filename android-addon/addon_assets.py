"""Explicit public asset allowlist, shared with packager and derivative auditor.

No directory walks: private bootstrap/config files can never be bundled here.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'android-addon/assets/art'
HOME = ROOT / 'android-addon/assets/art'
GALLERY = ROOT / 'app-gallery'

AA_PAYLOAD = 'assets/turboio/aa/aa-embedded-payload.apk'
AA_SIDECAR = 'assets/turboio/aa/payload.json'
AA_ENTRY = 'com.agentsanywhere.embedded.PayloadEntry'

def aa_assets(directory=None):
    """Embedded AA workbench payload from TURBOIO_AA_PAYLOAD_DIR, else android-addon/assets/aa.

    Kept outside assets(): the ~20 MB payload does not count against the public asset budget.
    An absent directory yields no payload; a present one must validate completely.
    """
    import hashlib, io, os, zipfile
    directory = Path(directory or os.environ.get('TURBOIO_AA_PAYLOAD_DIR') or ROOT / 'android-addon/assets/aa')
    payload, sidecar = directory / 'aa-embedded-payload.apk', directory / 'payload.json'
    if not payload.exists() and not sidecar.exists():
        return {}
    data = payload.read_bytes()
    doc = json.loads(sidecar.read_text(encoding='utf-8'))
    if doc != {'schemaVersion': 1, 'abiVersion': 1, 'entry': AA_ENTRY, 'sha256': hashlib.sha256(data).hexdigest()}:
        raise ValueError('AA payload sidecar mismatch')
    with zipfile.ZipFile(io.BytesIO(data)) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate AA payload members')
        if 'resources.arsc' not in names or 'classes.dex' not in names:
            raise ValueError('AA payload lacks resources or DEX')
        if any(n.startswith('lib/') and n.endswith('.so') for n in names):
            raise ValueError('AA payload native libraries are unsupported')
    return {AA_PAYLOAD: payload, AA_SIDECAR: sidecar}

def manifest_patch(embedded):
    from dsh_manifest import shared_patch
    if not embedded:
        return shared_patch
    from embed_manifest import patch as embed
    return lambda data: embed(shared_patch(data))

def assets():
    result = {}
    for name in ('hero-glass-v5','hero-sequence-v5','settings'):
        result[f'assets/turboio/art/{name}.png'] = ART / f'{name}.png'
    for name in ('dashboard-editor','app-gallery'):
        result[f'assets/turboio/art/{name}.png'] = HOME / f'{name}.png'
    result['assets/turboio/gallery/catalog.json'] = GALLERY / 'catalog.json'
    result['assets/turboio/card.schema.json'] = ROOT / 'android-addon/assets/card.schema.json'
    maps = ROOT / 'official-addon/ios107-edition/tests/maps'
    for name in ('shenzhen.json', 'hongkong.json', 'replay.json', 'README.md'):
        result[f'assets/turboio/maps/{name}'] = maps / name
    catalog = json.loads((GALLERY / 'catalog.json').read_text(encoding='utf-8'))
    import hashlib
    if len(catalog['entries']) != 20:
        raise ValueError('Expected reviewed 20-template catalog')
    for item in catalog['entries']:
        name = item['resource']
        import re
        if not re.fullmatch(r'Gallery_[a-z_]+', name):
            raise ValueError('Unsafe resource path')
        path = GALLERY / (name + '.zip')
        if hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Catalog/package digest mismatch')
        result[f'assets/turboio/gallery/{name}.zip'] = path
    if sum(p.stat().st_size for p in result.values()) > 24 * 1024 * 1024:
        raise ValueError('Asset budget exceeded')
    return result
