"""Private preparation derivative. Preserve every audited member except two URL strings.

Requires the ordinary package's existing allowlist audit first. Never signs,
installs, alters firmware, reads credentials or starts an OTA operation.
"""
import hashlib
import json
import pathlib
import subprocess
import sys
import zipfile

ORIGINAL = '78214ba0e1057871315da1cee17d860415c8b913521e59afc2748e45ead64c6c'
PATCHED = '851e0c59babc656e4eb4f47e00f3947605c3ddba530eeef4b65e7942e2898541'
NATIVE = 'lib/arm64-v8a/libapp.so'
MARKER = 'assets/turboio/official-ota-preparation.txt'
VALUE = b'ANDROID-105-OFFICIAL-OTA-PREPARE-01\n'
SLOTS = [(0x86e1f5, b'/xrlauncherapi/v1/signApi/gray/upgrade', b'http://127.0.0.1:18794/g/xxxxxxxA78p2M'),
         (0xd95b8, b'/xrlauncherhwapi/v1/signApi/gray/upgrade', b'http://127.0.0.1:18794/g/xxxxxxxxxxCCXhB')]
root = pathlib.Path(__file__).resolve().parent


def digest(data):
    return hashlib.sha256(data).hexdigest()


def native_profile(source):
    source_sha = digest(source.read_bytes())
    profiles = json.loads((root/'native-source-profiles.json').read_text(encoding='utf-8'))
    matches = [p for p in profiles if p['hostSha256'] == source_sha]
    if len(matches) != 1:
        raise ValueError('Unsupported host APK SHA256')
    return matches[0]


def string_hash(data):
    h = 0
    for c in data:
        h = ((h+c)*1025) & 0xffffffff
        h ^= h >> 6
    h = (h*9) & 0xffffffff
    h ^= h >> 11
    h = (h*32769) & 0xffffffff
    return (h & 0x3fffffff) or 1


def patch_native(before, profile):
    if digest(before) != profile['sha256']:
        raise ValueError('Native library SHA256 does not match host profile')
    expected = bytearray(before)
    for slot in profile['slots']:
        offset = slot['offset']
        old, replacement = (slot[k].encode('ascii') for k in ('text', 'replacement'))
        if len(old) != len(replacement) or string_hash(old) != string_hash(replacement) or before.find(old) != offset or before.find(old, offset+1) != -1:
            raise ValueError('Serialized URL position, uniqueness or size mismatch')
        if before[offset-1] != 128+len(old)*2:
            raise ValueError('Serialized URL length/CID mismatch')
        expected[offset:offset+len(old)] = replacement
    return bytes(expected)


def check_zip(archive):
    names = archive.namelist()
    if len(names) != len(set(names)) or archive.testzip() is not None:
        raise ValueError('Duplicate entries or corrupt ZIP')
    return names


def audit_prepared(base, prepared, new, marker, value):
    with zipfile.ZipFile(base) as z, zipfile.ZipFile(prepared) as d:
        before, after = check_zip(z), check_zip(d)
        if after != before+[marker] or d.read(marker) != value:
            raise ValueError('Prepared member order, allowlist or marker mismatch')
        if d.read(NATIVE) != new:
            raise ValueError('Prepared native mismatch')
        for name in before:
            if name != NATIVE and z.read(name) != d.read(name):
                raise ValueError('Non-native payload changed: '+name)


def prepare(original, base, patched_lib, out, guarded=False, node='node'):
    import os
    import tempfile
    receipt = out.with_suffix('.audit.json')
    inputs = {p.resolve() for p in (original, base, patched_lib)}
    if out.exists() or receipt.exists() or out.resolve() in inputs or receipt.resolve() in inputs:
        raise ValueError('Refuse to overwrite an existing artifact or an input')
    admission = subprocess.run([node, str(root/'admit-tmig1.mjs'), str(original)], check=True, capture_output=True, text=True)
    admitted = json.loads(admission.stdout)
    profile = native_profile(original)
    if any(admitted[k] != profile[k] for k in ('version', 'code', 'adapterId')) or admitted['sha256'] != profile['hostSha256']:
        raise ValueError('Host admission/native contract mismatch')
    subprocess.run([sys.executable, str(root/'verify_apk.py'), str(original), str(base), '--public-assets'], check=True, env={**os.environ, 'PYTHONUTF8':'1'})
    new = patched_lib.read_bytes()
    marker = 'assets/turboio/official-ota-'+('guarded.txt' if guarded else 'preparation.txt')
    value = ('ANDROID-'+profile['version'].replace('.', '')+'-OFFICIAL-OTA-'+('GUARD-02' if guarded else 'PREPARE-01')+'\n').encode('ascii')
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(base) as z:
        check_zip(source)
        names = check_zip(z)
        before = z.read(NATIVE)
        if source.read(NATIVE) != before or patch_native(before, profile) != new:
            raise ValueError('Native is not the exact allowed two-slot replacement')
        if any(n in names for n in ('assets/turboio/official-ota-preparation.txt', 'assets/turboio/official-ota-guarded.txt')):
            raise ValueError('Already prepared/guarded input')
        with tempfile.TemporaryDirectory(prefix='.prepare-', dir=out.parent) as folder:
            candidate = pathlib.Path(folder)/'candidate.apk'
            with zipfile.ZipFile(candidate, 'x') as dest:
                for info in z.infolist():
                    dest.writestr(info, new if info.filename == NATIVE else z.read(info))
                dest.writestr(marker, value)
            audit_prepared(base, candidate, patch_native(before, profile), marker, value)
            report = {'profile':admitted, 'mode':'OFFICIAL-OTA-GUARD-02' if guarded else 'OFFICIAL-OTA-PREPARE-01',
                      'sourceSha256':digest(original.read_bytes()), 'baseSha256':digest(base.read_bytes()),
                      'sha256':digest(candidate.read_bytes()), 'modifiedLibrary':NATIVE,
                      'originalLibrarySHA256':profile['sha256'], 'librarySHA256':digest(new),
                      'changesOnlyTwoSerializedURLs':True, 'allOtherPayloadsByteIdentical':True,
                      'marker':marker, 'upgradeSendingEnabled':False, 'oneTimeAuthorizationAvailable':bool(guarded),
                      'installed':False, 'deviceValidated':False}
            staged_receipt = pathlib.Path(folder)/'receipt.json'
            staged_receipt.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
            created = False
            try:
                os.link(staged_receipt, receipt)
                created = True
                os.link(candidate, out)
            except BaseException:
                if created:
                    receipt.unlink()
                raise
    return report


if __name__ == '__main__':
    arguments = sys.argv[1:]
    guarded = bool(arguments and arguments[0] == '--guarded')
    if guarded:
        arguments = arguments[1:]
    if len(arguments) not in (4, 5):
        raise SystemExit('Expected [--guarded] source base patched-lib output [node]')
    original, base, library, out = map(pathlib.Path, arguments[:4])
    print(json.dumps(prepare(original, base, library, out, guarded, arguments[4] if len(arguments) == 5 else 'node'), indent=2))
