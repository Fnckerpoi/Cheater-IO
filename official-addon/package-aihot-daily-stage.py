#!/usr/bin/env python3
"""Local-only stage IPA preparation. Never sign, install, or replace the old addon."""
import argparse
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import plistlib
import stat
import struct
import zipfile

BASE_SHA = 'de798af23e11b4c0fafdb485bee2cea3bd77dc0da39b1a928443ca1f69a28f24'
OLD_SHA = '7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503'
UUID = '748fd301da603095a2449bd0558faca5'
ROOT = 'Payload/Runner.app/'
DYLIB = 'TurboIOAIHotDailyStage.dylib'
LOAD = '@rpath/' + DYLIB

def sha(b):
    return hashlib.sha256(b).hexdigest()

def file_sha(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for b in iter(lambda: f.read(1024 * 1024), b''):
            h.update(b)
    return h.hexdigest()

def inspect(b):
    if len(b) < 32 or struct.unpack_from('<II', b) != (0xfeedfacf, 0x100000c):
        raise ValueError('Requires thin arm64 Mach-O, not FAT or another architecture')
    count, size = struct.unpack_from('<II', b, 16)
    end = 32 + size
    if count > 1024 or end > len(b):
        raise ValueError('Invalid Mach-O header')
    pos, first = 32, len(b)
    commands, deps, rpaths, signatures = [], [], [], []
    uuid, install, minimum = None, None, None
    for _ in range(count):
        if pos + 8 > end:
            raise ValueError('Truncated load command')
        cmd, length = struct.unpack_from('<II', b, pos)
        if length < 8 or length % 8 or pos + length > end:
            raise ValueError('Invalid load command size')
        data = b[pos:pos + length]
        commands.append((cmd, data))
        if cmd == 0x19:
            if length < 72:
                raise ValueError('Short segment')
            sections = struct.unpack_from('<I', data, 64)[0]
            if 72 + sections * 80 > length:
                raise ValueError('Truncated sections')
            for j in range(sections):
                at = 72 + j * 80
                offset = struct.unpack_from('<I', data, at + 48)[0]
                extent = struct.unpack_from('<Q', data, at + 40)[0]
                if offset and extent:
                    first = min(first, offset)
        if cmd == 0x1b:
            if length != 24:
                raise ValueError('Invalid UUID')
            uuid = data[8:24].hex()
        if cmd in (0x21, 0x2c):
            if length < 20 or struct.unpack_from('<I', data, 16)[0]:
                raise ValueError('Encrypted Mach-O unsupported')
        if cmd in (0xc, 0xd, 0x80000018, 0x8000001f, 0x80000023, 0x8000001c):
            at = struct.unpack_from('<I', data, 8)[0]
            if at < (12 if cmd == 0x8000001c else 24) or at >= length or b'\0' not in data[at:]:
                raise ValueError('Invalid load path')
            name = data[at:].split(b'\0')[0].decode('utf8')
            if cmd == 0xd:
                install = name
            elif cmd == 0x8000001c:
                rpaths.append(name)
            else:
                deps.append(name)
        if cmd == 0x1d:
            if length != 16:
                raise ValueError('Invalid signature command')
            offset, extent = struct.unpack_from('<II', data, 8)
            if offset < end or offset + extent > len(b):
                raise ValueError('Invalid signature blob bounds')
            signatures.append((offset, extent))
        if cmd == 0x32:
            if length < 24 or struct.unpack_from('<I', data, 8)[0] != 2:
                raise ValueError('Requires iOS platform')
            minimum = struct.unpack_from('<I', data, 12)[0]
        if cmd == 0x25:
            minimum = struct.unpack_from('<I', data, 8)[0]
        pos += length
    if pos != end or first < end:
        raise ValueError('Load command bounds mismatch')
    return dict(commands=commands, deps=deps, rpaths=rpaths, uuid=uuid, install=install,
                minimum=minimum, signatures=signatures, end=end, first=first,
                filetype=struct.unpack_from('<I', b, 12)[0])

def patch_runner(b):
    before = inspect(b)
    if before['filetype'] != 2 or LOAD in before['deps']:
        raise ValueError('Requires uninjected executable')
    name = LOAD.encode() + b'\0'
    length = (24 + len(name) + 7) // 8 * 8
    command = struct.pack('<IIIIII', 0xc, length, 24, 0, 0, 0) + name
    command += bytes(length - len(command))
    retained = [data for cmd, data in before['commands'] if cmd != 0x1d]
    new = b''.join(retained) + command
    end = 32 + len(new)
    if before['first'] == len(b) or end > before['first']:
        raise ValueError('Insufficient verified header padding')
    if end > before['end'] and any(b[before['end']:end]):
        raise ValueError('Header padding is not empty')
    result = bytearray(b)
    result[32:max(end, before['end'])] = new + bytes(max(end, before['end']) - end)
    struct.pack_into('<II', result, 16, len(retained) + 1, len(new))
    for offset, extent in before['signatures']:
        result[offset:offset + extent] = bytes(extent)
    result = bytes(result)
    after = inspect(result)
    if after['uuid'] != before['uuid'] or after['deps'] != before['deps'] + [LOAD] or after['signatures']:
        raise ValueError('Runner postpatch verification failed')
    if after['commands'][:-1] != [(cmd, data) for cmd, data in before['commands'] if cmd != 0x1d]:
        raise ValueError('Unexpected original load command modification')
    allowed = [(16, 24), (32, max(end, before['end']))] + [(a, a + n) for a, n in before['signatures']]
    cursor = 0
    for start, stop in sorted(allowed):
        if result[cursor:start] != b[cursor:start]:
            raise ValueError('Unexpected non-header byte modification')
        cursor = stop
    if result[cursor:] != b[cursor:]:
        raise ValueError('Unexpected body modification')
    return result

def validate_entries(entries):
    names = set()
    for entry in entries:
        name = entry.filename
        path = PurePosixPath(name)
        if not name or '\\' in name or '\0' in name or path.is_absolute() or any(x in ('', '.', '..') for x in name.rstrip('/').split('/')):
            raise ValueError('Unsafe ZIP path')
        if name in names:
            raise ValueError('Duplicate ZIP entry')
        names.add(name)
        if entry.flag_bits & 1:
            raise ValueError('Encrypted ZIP member unsupported')
        mode = entry.external_attr >> 16
        if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR)):
            raise ValueError('ZIP symlink or special file unsupported')
        if (name.startswith(ROOT) and not entry.is_dir() and any(x in name for x in ('OfficialManuscriptProbe', 'TurboIOAIHotDailyStage'))):
            raise ValueError('Input already contains diagnostic or stage module')
    return names

def signature_resource(name):
    return '_CodeSignature' in PurePosixPath(name).parts or name.endswith('/embedded.mobileprovision')

def prepare(source, dylib, out):
    source, dylib = Path(source).resolve(strict=True), Path(dylib).resolve(strict=True)
    out = Path(out).resolve()
    if out.exists() or out == source or out in source.parents or out in dylib.parents:
        raise ValueError('Output must be a new separate directory')
    if file_sha(source) != BASE_SHA:
        raise ValueError('Requires exact authorized input IPA SHA256')
    module = dylib.read_bytes()
    mi = inspect(module)
    if mi['filetype'] != 6 or mi['install'] != LOAD or mi['signatures'] or mi['minimum'] != 15 << 16:
        raise ValueError('Requires unsigned thin arm64 iOS15 stage dylib with exact install name')
    if not mi['deps'] or any(not d.startswith(('/System/Library/', '/usr/lib/')) for d in mi['deps']):
        raise ValueError('Unexpected stage dylib dependency')
    title_present = any('日报测试'.encode(encoding) in module for encoding in ('utf8', 'utf-16le'))
    if b'TIOAIHotDailyStageInstall' not in module or not title_present:
        raise ValueError('Missing stage entry code')
    with zipfile.ZipFile(source) as zin:
        names = validate_entries(zin.infolist())
        required = [ROOT + x for x in ('Runner', 'Info.plist', 'Frameworks/TurboIOPrivateAddon.dylib', 'Frameworks/App.framework/App', 'Frameworks/Flutter.framework/Flutter')]
        if not set(required).issubset(names):
            raise ValueError('Missing baseline members')
        info = plistlib.loads(zin.read(ROOT + 'Info.plist'))
        if tuple(info.get(k) for k in ('CFBundleIdentifier', 'CFBundleShortVersionString', 'CFBundleVersion')) != ('com.duriea.turboio.research', '1.0.5', '201'):
            raise ValueError('Unexpected baseline identity')
        if sha(zin.read(required[2])) != OLD_SHA:
            raise ValueError('Unexpected original addon')
        runner = zin.read(required[0])
        ri = inspect(runner)
        if ri['uuid'] != UUID or '@executable_path/Frameworks' not in ri['rpaths']:
            raise ValueError('Unexpected UUID or missing Frameworks rpath')
        for dependency in ri['deps']:
            member = None
            if dependency.startswith('@rpath/'):
                member = ROOT + 'Frameworks/' + dependency[len('@rpath/'):]
            elif dependency.startswith('@executable_path/'):
                member = ROOT + dependency[len('@executable_path/'):]
            elif not dependency.startswith(('/System/Library/', '/usr/lib/')):
                raise ValueError('Unsupported Runner dependency: ' + dependency)
            if member and member not in names:
                raise ValueError('Missing Runner dependency member: ' + member)
        patched = patch_runner(runner)
        out.mkdir(parents=True, mode=0o700, exist_ok=False)
        ipa = out / 'TurboIO-AIHOT-Daily-Stage-unsigned.ipa'
        report = dict(status='UNSIGNED_STAGE_NOT_DEVICE_VERIFIED', baseline_sha256=BASE_SHA,
                      runner_uuid=UUID, dylib_sha256=sha(module), dylib_dependencies=mi['deps'],
                      disabled=['official_automatic_import', '09:00_automatic_delivery', 'deduplication'],
                      added=[], changed=[], removed=[], unchanged_count=0)
        baseline_hashes = {}
        with zipfile.ZipFile(ipa, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zout:
            for entry in zin.infolist():
                name = entry.filename
                if signature_resource(name):
                    report['removed'].append(name)
                    continue
                data = zin.read(entry)
                baseline_hashes[name] = sha(data)
                if name == required[0]:
                    data = patched
                    report['changed'].append(dict(path=name, before=baseline_hashes[name], after=sha(data), reason='LC_LOAD_DYLIB added; Runner LC_CODE_SIGNATURE removed and obsolete blob zeroed'))
                else:
                    report['unchanged_count'] += 1
                e = copy.copy(entry)
                e.compress_type = zipfile.ZIP_DEFLATED
                zout.writestr(e, data)
            for name, data, mode in [(ROOT + 'Frameworks/' + DYLIB, module, 0o755),
                                      (ROOT + 'TurboIOAIHotDailyStage.json', json.dumps(dict(stage='manual_fetch_preview_txt_only', official_import=False, automatic_delivery=False, deduplication=False), indent=2).encode() + b'\n', 0o644)]:
                e = zipfile.ZipInfo(name)
                e.create_system = 3
                e.external_attr = (stat.S_IFREG | mode) << 16
                e.compress_type = zipfile.ZIP_DEFLATED
                zout.writestr(e, data)
                report['added'].append(dict(path=name, sha256=sha(data), mode=oct(mode)))
        with zipfile.ZipFile(ipa) as final:
            if final.testzip() is not None:
                raise ValueError('Output ZIP CRC failure')
            final_names = set(final.namelist())
            if final_names != (names - set(report['removed'])) | {x['path'] for x in report['added']}:
                raise ValueError('Unexpected output members')
            for name, digest in baseline_hashes.items():
                if name != required[0] and sha(final.read(name)) != digest:
                    raise ValueError('Baseline member changed: ' + name)
            if final.read(required[0]) != patched or final.read(ROOT + 'Frameworks/' + DYLIB) != module:
                raise ValueError('Injected binary mismatch')
        if file_sha(source) != BASE_SHA:
            raise ValueError('Source was modified')
        report.update(ipa_sha256=file_sha(ipa), ipa_bytes=ipa.stat().st_size, zip_crc='PASS', original_ipa_unchanged=True,
                      baseline_binaries_preserved=[dict(path=p, sha256=baseline_hashes[p]) for p in required[2:]])
        manifest = out / 'stage-package-manifest.json'
        manifest.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(dict(ipa=str(ipa), manifest=str(manifest), sha256=report['ipa_sha256'], bytes=report['ipa_bytes'],
                              added=len(report['added']), changed=len(report['changed']), removed=len(report['removed']), unchanged=report['unchanged_count']), indent=2))
        return report

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--dylib', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    prepare(args.source, args.dylib, args.out)
