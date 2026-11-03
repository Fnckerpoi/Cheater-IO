"""AA embedded asset names and the shared payload/sidecar contract."""
import hashlib
import io
import json
import zipfile

PAYLOAD = 'assets/turboio/aa/aa-embedded-payload.apk'
SIDECAR = 'assets/turboio/aa/payload.json'
ENTRY = 'com.agentsanywhere.embedded.PayloadEntry'


def validate_payload(payload, sidecar):
    doc = json.loads(sidecar)
    if doc != {'schemaVersion': 1, 'abiVersion': 1, 'entry': ENTRY,
               'sha256': hashlib.sha256(payload).hexdigest()}:
        raise ValueError('payload sidecar mismatch')
    with zipfile.ZipFile(io.BytesIO(payload)) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate payload members')
        if 'resources.arsc' not in names or 'classes.dex' not in names:
            raise ValueError('missing payload resources/dex')
        if any(n.startswith('lib/') and n.endswith('.so') for n in names):
            raise ValueError('native payload libraries unsupported')
    return doc
