#!/usr/bin/env python3
"""Local-only official-import trial sidecar, preserving the exact tested stage IPA."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path, PurePosixPath
import plistlib
import stat
import struct
import zipfile

_parser_spec = importlib.util.spec_from_file_location('stage_macho_parser', Path(__file__).with_name('package-aihot-daily-stage.py'))
_parser = importlib.util.module_from_spec(_parser_spec)
_parser_spec.loader.exec_module(_parser)
inspect, sha, file_sha = _parser.inspect, _parser.sha, _parser.file_sha
BASE_SHA = '554e1a07422009b43441326fe1d59a9a135750def82fb77d9eb8d71b3eed9863'
RUNNER_SHA = '6939401111098233c70374cd68bcc82c287882e3820d4d6ce8195051ff37d920'
OLD_SHA = '7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503'
STAGE_SHA = '7ee752d3f79aa09b5c3c14fccd3a186a4a535c62e2415fe0c78b651d0c8b3224'
UUID = '748fd301da603095a2449bd0558faca5'
ROOT = 'Payload/Runner.app/'
DYLIB = 'TurboIOOfficialImportTrial.dylib'
LOAD = '@rpath/' + DYLIB
STAGE_LOAD = '@rpath/TurboIOAIHotDailyStage.dylib'


def validate_entries(entries):
    names = set()
    for entry in entries:
        name = entry.filename
        parts = name.rstrip('/').split('/')
        mode = entry.external_attr >> 16
        if not name or '\\' in name or '\0' in name or PurePosixPath(name).is_absolute() or any(p in ('', '.', '..') for p in parts):
            raise ValueError('Unsafe ZIP path')
        if name in names:
            raise ValueError('Duplicate ZIP entry')
        if entry.flag_bits & 1:
            raise ValueError('Encrypted ZIP member')
        if stat.S_ISLNK(mode) or stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR):
            raise ValueError('ZIP symlink or special file')
        if name.startswith(ROOT) and not entry.is_dir() and any(v in name for v in ('OfficialManuscriptProbe', 'TurboIOOfficialImportTrial')):
            raise ValueError('Input already contains probe or trial')
        names.add(name)
    return names


def signature_resource(name):
    return '_CodeSignature' in PurePosixPath(name).parts or name.endswith('/embedded.mobileprovision')


def patch_runner(data):
    before = inspect(data)
    if before['filetype'] != 2 or before['signatures'] or LOAD in before['deps']:
        raise ValueError('Requires unsigned executable without existing trial dependency')
    if STAGE_LOAD not in before['deps']:
        raise ValueError('Tested stage dependency absent')
    text = LOAD.encode() + b'\0'
    length = (24 + len(text) + 7) // 8 * 8
    command = struct.pack('<IIIIII', 0xc, length, 24, 0, 0, 0) + text
    command += bytes(length - len(command))
    start, end = before['end'], before['end'] + length
    if before['first'] == len(data) or end > before['first'] or end > len(data):
        raise ValueError('Insufficient verified header padding')
    if any(data[start:end]):
        raise ValueError('Header padding not empty')
    result = bytearray(data)
    result[start:end] = command
    struct.pack_into('<II', result, 16, len(before['commands']) + 1, end - 32)
    result = bytes(result)
    after = inspect(result)
    if after['commands'][:-1] != before['commands'] or after['deps'] != before['deps'] + [LOAD] or after['uuid'] != before['uuid'] or after['signatures']:
        raise ValueError('Old load commands/ordinals/UUID changed')
    if len(result) != len(data) or result[:16] != data[:16] or result[24:start] != data[24:start] or result[end:] != data[end:]:
        raise ValueError('Unexpected Runner bytes changed')
    return result


def validate_module(module):
    header = inspect(module)
    if header['filetype'] != 6 or header['install'] != LOAD or header['minimum'] != 15 << 16 or header['signatures']:
        raise ValueError('Requires unsigned thin arm64 iOS15 trial dylib with exact install name')
    if not header['deps'] or any(not d.startswith(('/System/Library/', '/usr/lib/')) for d in header['deps']):
        raise ValueError('Trial dependency must be system-only')
    if b'TIOOfficialImportTrialInstallUI\0' not in module:
        raise ValueError('Trial entry symbol absent')
    return header


def prepare(source, dylib, out):
    source, dylib = Path(source).resolve(strict=True), Path(dylib).resolve(strict=True)
    out = Path(out).resolve()
    if out.exists() or out in source.parents or out in dylib.parents:
        raise ValueError('Output must be a new separate directory')
    if file_sha(source) != BASE_SHA:
        raise ValueError('Requires exact tested stage input IPA SHA256')
    module = dylib.read_bytes()
    header = validate_module(module)
    with zipfile.ZipFile(source) as original:
        names = validate_entries(original.infolist())
        paths = {key: ROOT + name for key, name in dict(runner='Runner', info='Info.plist', addon='Frameworks/TurboIOPrivateAddon.dylib', stage='Frameworks/TurboIOAIHotDailyStage.dylib', app='Frameworks/App.framework/App', flutter='Frameworks/Flutter.framework/Flutter').items()}
        if not set(paths.values()).issubset(names):
            raise ValueError('Required baseline member absent')
        info = plistlib.loads(original.read(paths['info']))
        if tuple(info.get(k) for k in ('CFBundleIdentifier', 'CFBundleShortVersionString', 'CFBundleVersion')) != ('com.duriea.turboio.research', '1.0.5', '201'):
            raise ValueError('Host identity mismatch')
        for key, digest in [('runner', RUNNER_SHA), ('addon', OLD_SHA), ('stage', STAGE_SHA)]:
            if sha(original.read(paths[key])) != digest:
                raise ValueError('Baseline hash mismatch: ' + key)
        runner = original.read(paths['runner'])
        runner_info = inspect(runner)
        if runner_info['uuid'] != UUID or '@executable_path/Frameworks' not in runner_info['rpaths']:
            raise ValueError('Runner UUID or Frameworks rpath mismatch')
        for dependency in runner_info['deps']:
            member = None
            if dependency.startswith('@rpath/'):
                member = ROOT + 'Frameworks/' + dependency[len('@rpath/'):]
            elif dependency.startswith('@executable_path/'):
                member = ROOT + dependency[len('@executable_path/'):]
            elif not dependency.startswith(('/System/Library/', '/usr/lib/')):
                raise ValueError('Unsupported Runner dependency')
            if member and member not in names:
                raise ValueError('Missing baseline dependency: ' + member)
        patched = patch_runner(runner)
        out.mkdir(parents=True, mode=0o700, exist_ok=False)
        ipa = out / 'TurboIO-AIHOT-Official-Import-Trial-unsigned.ipa'
        report = dict(status='UNSIGNED_EXPERIMENT_NOT_DEVICE_VERIFIED', baseline_sha256=BASE_SHA,
                      runner_uuid=UUID, trial_dylib_sha256=sha(module), trial_dependencies=header['deps'],
                      startup_hooks=False, phase_b='requires_phase_a_public_fixture_profile_and_one_user_confirm',
                      disabled=['09:00_automatic_delivery', 'batch_import', 'official_background_crud', 'deduplication'],
                      added=[], changed=[], removed=[], unchanged_count=0)
        hashes = {}
        with zipfile.ZipFile(ipa, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for entry in original.infolist():
                name = entry.filename
                if signature_resource(name):
                    report['removed'].append(name)
                    continue
                data = original.read(entry)
                hashes[name] = sha(data)
                if name == paths['runner']:
                    data = patched
                    report['changed'].append(dict(path=name, before=hashes[name], after=sha(data), reason='Append trial LC_LOAD_DYLIB only; old load commands, dependency order and all other bytes unchanged'))
                else:
                    report['unchanged_count'] += 1
                preserved = copy.copy(entry)
                preserved.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(preserved, data)
            marker = dict(experiment='public_fixture_observation_then_single_confirmed_official_import_request', startup_hooks=False, phase_b_requires_profile=True, automatic_delivery=False, batch_import=False, official_crud_verified=False)
            for name, data, mode in [(ROOT + 'Frameworks/' + DYLIB, module, 0o755),
                                     (ROOT + 'TurboIOOfficialImportTrial.json', json.dumps(marker, indent=2).encode() + b'\n', 0o644)]:
                entry = zipfile.ZipInfo(name)
                entry.create_system = 3
                entry.external_attr = (stat.S_IFREG | mode) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, data)
                report['added'].append(dict(path=name, sha256=sha(data), mode=oct(mode)))
        with zipfile.ZipFile(ipa) as final:
            if final.testzip() is not None:
                raise ValueError('ZIP CRC failed')
            if set(final.namelist()) != (names - set(report['removed'])) | {item['path'] for item in report['added']}:
                raise ValueError('Unexpected ZIP member diff')
            for name, digest in hashes.items():
                if name != paths['runner'] and sha(final.read(name)) != digest:
                    raise ValueError('Baseline member changed: ' + name)
            if final.read(paths['runner']) != patched or final.read(ROOT + 'Frameworks/' + DYLIB) != module:
                raise ValueError('Injected binaries mismatch')
        if file_sha(source) != BASE_SHA:
            raise ValueError('Tested stage input changed')
        report.update(ipa_sha256=file_sha(ipa), ipa_bytes=ipa.stat().st_size, zip_crc='PASS', baseline_unchanged=True,
                      preserved_core_binaries=[dict(path=paths[key], sha256=hashes[paths[key]]) for key in ['addon', 'stage', 'app', 'flutter']])
        manifest = out / 'official-import-trial-manifest.json'
        manifest.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(dict(ipa=str(ipa), manifest=str(manifest), sha256=report['ipa_sha256'], bytes=report['ipa_bytes'], added=len(report['added']), changed=len(report['changed']), removed=len(report['removed']), unchanged=report['unchanged_count']), indent=2))
        return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--dylib', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    prepare(args.source, args.dylib, args.out)
