#!/usr/bin/env python3
"""Stage-A isolation gates only. No DB operations, signing, or installation."""
import re


def identity(run):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{5,39}', run) or run in ('research', 'official'):
        raise ValueError('Explicit unique lowercase lab run ID required')
    return dict(bundle='com.duriea.turboio.manuscriptlab105.' + run,
                display_name='文稿隔离实验105-' + run, run=run)


def signing_plan(run, team):
    if not re.fullmatch(r'[A-Z0-9]{10}', team):
        raise ValueError('Exact signing team required; no wildcard/profile inheritance')
    return {'application-identifier': team + '.' + identity(run)['bundle'],
            'com.apple.developer.team-identifier': team, 'get-task-allow': False}


def check_nested_entitlements(actual):
    if not isinstance(actual, dict) or actual:
        raise ValueError('Nested code must not inherit app capabilities')
    return True


def check_entitlements(actual, run, team):
    expected = signing_plan(run, team)
    if not isinstance(actual, dict) or actual != expected or type(actual.get('get-task-allow')) is not bool:
        raise ValueError('Unexpected, wildcard or shared signing entitlement')
    return True


def safe_path(home, candidate):
    from pathlib import Path
    home, candidate = Path(home), Path(candidate)
    if not home.is_absolute() or not candidate.is_absolute() or '..' in candidate.parts or '..' in home.parts:
        raise ValueError('Absolute unambiguous lab path required')
    for path in (home, candidate):
        current = Path(path.anchor)
        for part in path.parts[1:]:
            current /= part
            if current.is_symlink():
                raise ValueError('Symlink path refused')
    try:
        home = home.resolve(strict=True)
        candidate = candidate.resolve(strict=True)
    except OSError as error:
        raise ValueError('Unverified/missing storage path') from error
    roots = [home / 'Library/Application Support/ManuscriptLab', home / 'tmp/ManuscriptLab']
    if not any(candidate == root or root in candidate.parents for root in roots):
        raise ValueError('Outside precise lab storage allowlist')
    return candidate

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import plistlib
import shutil
import stat
import struct
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location('lab_macho_parser', (HERE / 'macho_parser.py') if (HERE / 'macho_parser.py').is_file() else HERE.parent / 'package-aihot-daily-stage.py')
_parser = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_parser)
inspect = _parser.inspect
ORIGINAL_IPA_SHA256 = '39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a'
ORIGINAL_IPA_BYTES = 353131781
APPROVED_PROBE_SHA256 = 'f66325400dab0044257720a9c7b0760f780ea2505b30c6280cbc9b8c1cd351e0'
UUID = '748fd301da603095a2449bd0558faca5'
LOAD = '@executable_path/Frameworks/TurboIOManuscriptLabProbe.dylib'
PINS = {
    'Frameworks/App.framework/App': 'f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a',
    'Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs': '728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88',
    'Frameworks/sqflite_darwin.framework/sqflite_darwin': '5e9880de65045f1600e355e0f20e235638c4752a617fbbd28ded46fc7d0725df',
}
FORBIDDEN_PARTS = {'PlugIns', 'Watch', 'AppClips', 'Documents', 'Library', 'SharedSupport', 'iCloud', 'AppGroups'}
FORBIDDEN_TOKENS = ('TurboIO', 'ManuscriptLab', 'Frida', 'Substrate', 'ElleKit', 'bootstrap', 'Cookie', 'PrivateTTS', 'mobileprovision')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def no_symlinks(path):
    path = Path(path)
    if not path.is_absolute() or '..' in path.parts:
        raise ValueError('Absolute canonical source/output path required')
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current /= part
        if current.is_symlink():
            raise ValueError('Source/output symlink refused')
    return path


def inventory(app):
    files = {}
    for root, dirs, names in os.walk(app, followlinks=False):
        for name in dirs + names:
            path = Path(root) / name
            rel = path.relative_to(app).as_posix()
            mode = path.lstat().st_mode
            if stat.S_ISLNK(mode) or not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
                raise ValueError('Special/symlink source member refused')
            if any(p in FORBIDDEN_PARTS or p.endswith(('.app', '.appex', '.xpc')) for p in Path(rel).parts) or any(t.lower() in rel.lower() for t in FORBIDDEN_TOKENS):
                raise ValueError('Unknown embedded/private/shared source resource: ' + rel)
            if path.suffix.lower() in ('.db', '.sqlite', '.sqlite3', '.isar', '.hive', '.dylib', '.p12', '.pfx', '.key', '.pem', '.mobileprovision') or name.endswith(('-wal', '-shm')):
                raise ValueError('Unreviewed database or sidecar source member: ' + rel)
            if stat.S_ISREG(mode):
                if path.stat().st_size > 1024 * 1024 * 1024:
                    raise ValueError('Source member size limit')
                files[rel] = dict(sha256=digest(path.read_bytes()), mode=stat.S_IMODE(mode))
    return files


def directory_inventory(app):
    result = {}
    for root, dirs, _ in os.walk(app, followlinks=False):
        for name in dirs:
            path = Path(root) / name
            result[path.relative_to(app).as_posix()] = stat.S_IMODE(path.lstat().st_mode)
    return result


def require_original_inventory(app, expected):
    actual = dict(files=inventory(app), directories=directory_inventory(app))
    if actual != expected:
        raise ValueError('Exact original inventory mismatch: changed, extra or missing file/directory/mode')
    return actual


def stream_sha(stream):
    h = hashlib.sha256()
    for data in iter(lambda: stream.read(1024 * 1024), b''):
        h.update(data)
    return h.hexdigest()


def safe_zip_entries(entries):
    names, canonical = set(), set()
    total = 0
    for entry in entries:
        name = entry.filename
        parts = name.rstrip('/').split('/')
        if not name or '\\' in name or '\0' in name or name.startswith('/') or any(x in ('', '.', '..') for x in parts):
            raise ValueError('Unsafe original ZIP path')
        folded = name.rstrip('/').casefold()
        if name in names or folded in canonical:
            raise ValueError('Duplicate/colliding original ZIP member')
        names.add(name);canonical.add(folded)
        mode = entry.external_attr >> 16
        if entry.flag_bits & 1 or stat.S_IFMT(mode) not in (stat.S_IFREG, stat.S_IFDIR):
            raise ValueError('Encrypted/symlink/special original ZIP member')
        if entry.is_dir() != stat.S_ISDIR(mode):
            raise ValueError('Original ZIP type mismatch')
        if name != 'Payload/' and name != 'Payload/Runner.app/' and not name.startswith('Payload/Runner.app/'):
            raise ValueError('Unexpected original ZIP root')
        if entry.file_size > 1024 * 1024 * 1024:
            raise ValueError('Original ZIP member exceeds bound')
        total += entry.file_size
        if total > 2 * 1024 * 1024 * 1024 or len(names) > 20000:
            raise ValueError('Original ZIP inventory exceeds bound')
    return names


def trusted_original_manifest(ipa):
    ipa = no_symlinks(Path(ipa).absolute())
    if not ipa.is_file() or ipa.stat().st_size != ORIGINAL_IPA_BYTES:
        raise ValueError('Pinned original IPA size/hash trust root required')
    with ipa.open('rb') as source:
        if stream_sha(source) != ORIGINAL_IPA_SHA256:
            raise ValueError('Pinned original IPA whole hash mismatch')
        source.seek(0)
        with zipfile.ZipFile(source) as archive:
            safe_zip_entries(archive.infolist())
            files, dirs = {}, {}
            prefix = 'Payload/Runner.app/'
            for entry in archive.infolist():
                if not entry.filename.startswith(prefix) or entry.filename == prefix:
                    continue
                relative = entry.filename[len(prefix):].rstrip('/')
                mode = stat.S_IMODE(entry.external_attr >> 16)
                if entry.is_dir():
                    dirs[relative] = mode
                else:
                    with archive.open(entry) as member:
                        files[relative] = dict(sha256=stream_sha(member), mode=mode)
            source.seek(0)
            if stream_sha(source) != ORIGINAL_IPA_SHA256:
                raise ValueError('Original IPA changed while authenticating inventory')
    return dict(files=files, directories=dirs)


def extract_original(ipa, output):
    output = no_symlinks(Path(output).absolute())
    if output.exists():
        raise ValueError('New extraction output required')
    expected = trusted_original_manifest(ipa)
    output.mkdir(parents=True, mode=0o700, exist_ok=False)
    app = output / 'Runner.app'
    app.mkdir(mode=0o755)
    with zipfile.ZipFile(ipa) as archive:
        for name in sorted(expected['directories'], key=lambda n: len(Path(n).parts)):
            path = app / name
            path.mkdir(parents=True, exist_ok=False)
            path.chmod(expected['directories'][name])
        for name, record in expected['files'].items():
            path = app / name
            path.parent.mkdir(parents=True, exist_ok=True)
            with archive.open('Payload/Runner.app/' + name) as source, path.open('xb') as target:
                shutil.copyfileobj(source, target, 1024 * 1024)
            path.chmod(record['mode'])
    require_original_inventory(app, expected)
    report = dict(status='AUTHENTICATED_ORIGINAL_EXTRACTED_NOT_DEVICE_READY', original_ipa_sha256=ORIGINAL_IPA_SHA256,
                  app=str(app), files=len(expected['files']), directories=len(expected['directories']), device_ready=False)
    (output/'original-inventory.json').write_text(json.dumps(dict(report=report, inventory=expected), indent=2)+'\n')
    return report


def preflight(source, run, expected_hashes=None, synthetic=False, original_ipa=None, expected_inventory=None):
    identity(run)
    source = no_symlinks(Path(source).absolute())
    if not source.is_dir() or source.name != 'Runner.app':
        raise ValueError('Explicit clean original Runner.app required')
    if not isinstance(synthetic, bool):
        raise ValueError('Invalid fixture mode')
    if not synthetic and (expected_hashes is not None or expected_inventory is not None):
        raise ValueError('Production hash/inventory overrides are forbidden')
    if not synthetic and original_ipa is None:
        raise ValueError('Pinned original IPA whole hash trust root required')
    trusted = expected_inventory if synthetic else trusted_original_manifest(original_ipa)
    if trusted is not None:
        require_original_inventory(source, trusted)
    pins = expected_hashes if synthetic else PINS
    files = inventory(source)
    info = plistlib.loads((source / 'Info.plist').read_bytes())
    if tuple(info.get(k) for k in ('CFBundleIdentifier', 'CFBundleShortVersionString', 'CFBundleVersion', 'CFBundleExecutable')) != ('com.rayneo.venus.pub', '1.0.5', '201', 'Runner'):
        raise ValueError('Requires original official 1.0.5(201) identity')
    for key in info:
        lowered = key.lower()
        namespace_parts = re.split(r'[._:-]+', lowered)
        custom_tio = lowered.startswith(('tio', 'turboio')) or any(part in ('tio', 'turboio') for part in namespace_parts)
        shared = any(x in lowered for x in ('icloud', 'ubiquity', 'ubiquitous', 'appgroup', 'applicationgroup', 'application-groups', 'keychain'))
        if custom_tio or shared:
            raise ValueError('Unknown shared/custom Info.plist policy: ' + key)
    for name, expected in pins.items():
        if name not in files or files[name]['sha256'] != expected:
            raise ValueError('Original engine/App hash mismatch: ' + name)
    runner = (source / 'Runner').read_bytes()
    header = inspect(runner)
    if header['filetype'] != 2 or header['uuid'] != UUID:
        raise ValueError('Wrong original Runner UUID/type')
    for name in files:
        data = (source / name).read_bytes()
        if data[:4] in (b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca', b'\xca\xfe\xba\xbf'):
            raise ValueError('FAT image requires separate review')
        if data[:4] == b'\xcf\xfa\xed\xfe':
            image = inspect(data)
            for dep in image['deps']:
                if any(token.lower() in dep.lower() for token in ('turboio', 'manuscriptlab', 'frida', 'substrate', 'ellekit', '/var/jb/')):
                    raise ValueError('Previously modified source load chain refused')
                if dep.startswith(('/usr/lib/', '/System/Library/')):
                    continue
                if dep.startswith('@rpath/'):
                    target = 'Frameworks/' + dep[len('@rpath/'):]
                elif dep.startswith('@executable_path/'):
                    target = dep[len('@executable_path/'):]
                elif dep.startswith('@loader_path/') and name != 'Runner':
                    target = str(Path(name).parent / dep[len('@loader_path/'):])
                else:
                    raise ValueError('Unreviewed library dependency path')
                if '..' in Path(target).parts or target not in files:
                    raise ValueError('Unresolved source dependency')
    return dict(status='SYNTHETIC_PREFLIGHT_ONLY' if synthetic else 'CLEAN_INPUT_STATIC_PREFLIGHT_ONLY',
                device_ready=False, db_access_enabled=False, runtime_isolation_proven=False,
                identity=identity(run), source=str(source), source_files=files, original_runner_sha256=digest(runner),
                engine_hashes=pins, runner_uuid=header['uuid'],
                original_ipa_sha256=None if synthetic else ORIGINAL_IPA_SHA256, exact_original_inventory_verified=trusted is not None)


def patch_runner(data):
    before = inspect(data)
    if before['filetype'] != 2 or before['uuid'] != UUID or any('TurboIO' in d or 'ManuscriptLab' in d for d in before['deps']):
        raise ValueError('Only clean pinned original Runner may receive probe load')
    text = LOAD.encode() + b'\0'
    size = (24 + len(text) + 7) // 8 * 8
    command = struct.pack('<6I', 0xc, size, 24, 0, 0, 0) + text + bytes(size - 24 - len(text))
    retained = [c for c in before['commands'] if c[0] != 0x1d]
    raw = b''.join(c[1] for c in retained) + command
    end = 32 + len(raw)
    if end > before['first'] or end > len(data) or (end > before['end'] and any(data[before['end']:end])):
        raise ValueError('Insufficient verified empty header padding')
    result = bytearray(data)
    stop = max(end, before['end'])
    result[32:stop] = raw + bytes(stop - end)
    struct.pack_into('<II', result, 16, len(retained) + 1, len(raw))
    for offset, extent in before['signatures']:
        result[offset:offset + extent] = bytes(extent)
    result = bytes(result)
    after = inspect(result)
    if after['commands'][:-1] != retained or after['deps'] != before['deps'] + [LOAD] or after['uuid'] != before['uuid'] or after['signatures']:
        raise ValueError('Runner preservation verification failed')
    ranges = [(16, 24), (32, stop)] + [(a, a + n) for a, n in before['signatures']]
    cursor = 0
    for a,b in sorted(ranges):
        if result[cursor:a] != data[cursor:a]: raise ValueError('Unexpected Runner byte change')
        cursor = b
    if result[cursor:] != data[cursor:] or len(result) != len(data): raise ValueError('Unexpected Runner body/size change')
    return result


def validate_probe(data):
    if digest(data) != APPROVED_PROBE_SHA256:
        raise ValueError('Probe differs from approved reviewed native module identity')
    h = inspect(data)
    if h['filetype'] != 6 or h['install'] != '@rpath/TurboIOManuscriptLabProbe.dylib' or h['signatures'] or h['minimum'] != 15 << 16:
        raise ValueError('Requires unsigned iOS15 arm64 minimal probe')
    allowed = {'/System/Library/Frameworks/Foundation.framework/Foundation',
               '/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation',
               '/usr/lib/libobjc.A.dylib', '/usr/lib/libSystem.B.dylib'}
    if not h['deps'] or any(p not in allowed for p in h['deps']):
        raise ValueError('Probe must use only exact Foundation/CoreFoundation/system runtime dependencies')
    for token in (b'FilePickerPlugin', b'TurboIOPrivateAddon', b'isar_open', b'sqlite3_open', b'NSURLSession', b'ImportTrial', b'AIHotStage'):
        if token in data: raise ValueError('Disallowed probe implementation')
    for symbol in (b'_TIOManuscriptLabWritesEnabled\0', b'_TIOManuscriptLabCollectionEnabled\0', b'_TIOManuscriptLabRuntimeAllowed\0'):
        if symbol not in data: raise ValueError('Required probe gate symbol absent')
    return h


def prepare(source, module_path, output, run, expected_hashes=None, synthetic=False, original_ipa=None, expected_inventory=None):
    source = Path(source).absolute()
    output = no_symlinks(Path(output).absolute())
    if output.exists() or output == source or source in output.parents or output in source.parents:
        raise ValueError('New separate nonexisting output required')
    before = preflight(source, run, expected_hashes, synthetic, original_ipa, expected_inventory)
    module = Path(module_path).read_bytes()
    if not synthetic: validate_probe(module)
    patched = patch_runner((source / 'Runner').read_bytes())
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    app = output / 'Payload/Runner.app'
    shutil.copytree(source, app, symlinks=False)
    removed = []
    for path in sorted(app.rglob('_CodeSignature'), reverse=True):
        relative = path.relative_to(app).as_posix()
        if path.resolve().is_relative_to(app.resolve()) if hasattr(Path, 'is_relative_to') else app.resolve() in path.resolve().parents:
            shutil.rmtree(path)
            removed.append(relative + '/')
        else: raise ValueError('Signature removal outside new output')
    (app / 'Runner').write_bytes(patched)
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    cleared = []
    for key in ('CFBundleURLTypes', 'NSUserActivityTypes', 'UIBackgroundModes', 'UTExportedTypeDeclarations', 'UTImportedTypeDeclarations'):
        if key in info:
            info.pop(key)
            cleared.append(key)
    ident = identity(run)
    info.update(CFBundleIdentifier=ident['bundle'], CFBundleDisplayName=ident['display_name'], CFBundleName='ManuscriptLab105-' + run,
                TIOManuscriptLabStage='A_DISABLED', TIOManuscriptLabRunID=run, LSSupportsOpeningDocumentsInPlace=False,
                UIFileSharingEnabled=False)
    (app / 'Info.plist').write_bytes(plistlib.dumps(info))
    marker = dict(stage='A_DISABLED', run=run, expected_bundle=ident['bundle'], schema='UNKNOWN', engine_for_manuscripts='UNKNOWN',
                  writes_enabled=False, collection_enabled=False, import_accepted=False, runtime_isolation_proven=False,
                  public_fixture_only=True, original_engine_hashes=before['engine_hashes'])
    (app / 'ManuscriptLabStageA.json').write_text(json.dumps(marker, indent=2) + '\n')
    target = app/'Frameworks/TurboIOManuscriptLabProbe.dylib'
    target.parent.mkdir(exist_ok=True)
    target.write_bytes(module)
    target.chmod(0o755)
    changed = {'Runner', 'Info.plist'}
    for name, record in before['source_files'].items():
        if name in changed or '_CodeSignature' in Path(name).parts: continue
        p = app/name
        if not p.is_file() or digest(p.read_bytes()) != record['sha256'] or stat.S_IMODE(p.stat().st_mode) != record['mode']:
            raise ValueError('Retained source member changed: ' + name)
    after_source = preflight(source, run, expected_hashes, synthetic, original_ipa, expected_inventory)
    if before['source_files'] != after_source['source_files']:
        raise ValueError('Original input changed')
    report = dict(before, status='SYNTHETIC_UNSIGNED_FIXTURE_NOT_INSTALLABLE' if synthetic else 'UNSIGNED_STAGE_A_DISABLED_NOT_DEVICE_READY',
                  output=str(output), added=['Frameworks/TurboIOManuscriptLabProbe.dylib', 'ManuscriptLabStageA.json'],
                  changed=sorted(changed), removed=removed, cleared_info_keys=cleared, source_unchanged=True,
                  retained_hashes_verified=True, probe_sha256=digest(module), signing_performed=False)
    if not synthetic:
        ipa = output/'ManuscriptLab105-UNSIGNED-STAGE-A-DISABLED.ipa'
        with zipfile.ZipFile(ipa, 'x', zipfile.ZIP_DEFLATED) as z:
            for p in sorted((output/'Payload').rglob('*')):
                if p.is_file(): z.write(p, p.relative_to(output).as_posix())
        with zipfile.ZipFile(ipa) as z:
            if z.testzip() is not None: raise ValueError('Archive CRC failed')
            for p in app.rglob('*'):
                if p.is_file() and digest(z.read(p.relative_to(output).as_posix())) != digest(p.read_bytes()):
                    raise ValueError('Archive member verification failed')
        report['unsigned_ipa_sha256'] = digest(ipa.read_bytes())
    (output/'stage-a-report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='command', required=True)
    for name in ('preflight', 'prepare'):
        p = subs.add_parser(name)
        p.add_argument('--app', required=True, type=Path)
        p.add_argument('--run', required=True)
        p.add_argument('--original-ipa', required=True, type=Path, help='Exact pinned public original trust root; no arbitrary manifest')
        if name == 'prepare':
            p.add_argument('--probe', required=True, type=Path)
            p.add_argument('--out', required=True, type=Path)
    p = subs.add_parser('extract-original')
    p.add_argument('--original-ipa', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    p = subs.add_parser('sign-plan')
    p.add_argument('--run', required=True)
    p.add_argument('--team', required=True)
    p = subs.add_parser('check-entitlements')
    p.add_argument('--run', required=True)
    p.add_argument('--team', required=True)
    p.add_argument('--metadata', required=True, type=Path)
    p.add_argument('--nested', action='store_true', help='Nested framework/dylib must have an empty entitlement dictionary')
    args = parser.parse_args()
    if args.command == 'extract-original': result = extract_original(args.original_ipa, args.out)
    elif args.command == 'preflight': result = preflight(args.app, args.run, original_ipa=args.original_ipa)
    elif args.command == 'prepare': result = prepare(args.app, args.probe, args.out, args.run, original_ipa=args.original_ipa)
    elif args.command == 'sign-plan': result = dict(status='PLAN_ONLY_NOT_SIGNED', entitlements=signing_plan(args.run, args.team))
    else:
        if args.metadata.suffix.lower() in ('.mobileprovision', '.p12', '.pfx', '.key') or args.metadata.stat().st_size > 1024 * 1024:
            raise ValueError('Only bounded public entitlement metadata may be checked')
        data = args.metadata.read_bytes()
        if b'<?xml' in data or data.startswith(b'bplist'): actual = plistlib.loads(data)
        else: actual = json.loads(data)
        if args.nested: check_nested_entitlements(actual)
        else: check_entitlements(actual, args.run, args.team)
        result = dict(status='ENTITLEMENTS_METADATA_PASS_ONLY_NOT_SIGNATURE_OR_CONTAINER_ACCEPTANCE')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    try: main()
    except (ValueError, OSError, KeyError, struct.error) as error:
        raise SystemExit('LAB_FAIL_CLOSED: ' + str(error))
