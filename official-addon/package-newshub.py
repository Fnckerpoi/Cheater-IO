#!/usr/bin/env python3
"""Pin the shipped IPA, remove experimental modules, and add an independent NewsHub."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import stat
import struct
import zipfile

_spec = importlib.util.spec_from_file_location('stage_macho_parser', Path(__file__).with_name('package-aihot-daily-stage.py'))
_parser = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_parser)
inspect = _parser.inspect
sha = _parser.sha
file_sha = _parser.file_sha
ROOT = 'Payload/Runner.app/'
BASE_SHA = 'c3ce2e6ca4758744f75bab85d0180664020dfb81a6a2400a5bd955f24e9ac5ed'
RUNNER_SHA = '3af9f3f15136aa4578146d8d3602f6c83fe0d33ca945525dd06844248e4f50d3'
ADDON_SHA = '7923e2bcdaf6f2a9736b3db5035b972976d2bf4df3c406ea9f969f0f2b7cb503'
UUID = '748fd301da603095a2449bd0558faca5'
DYLIB = 'TurboIONewsHub.dylib'
LOAD = '@rpath/' + DYLIB
OLD_LOADS = ['@rpath/TurboIOAIHotDailyStage.dylib', '@rpath/TurboIOOfficialImportTrial.dylib']
REMOVED = {ROOT + n for n in ('Frameworks/TurboIOAIHotDailyStage.dylib', 'TurboIOAIHotDailyStage.json', 'Frameworks/TurboIOOfficialImportTrial.dylib', 'TurboIOOfficialImportTrial.json')}


def bounded(data, offset, size):
    if offset < 0 or size < 0 or offset + size > len(data):
        raise ValueError('Mach-O payload bounds')
    return data[offset:offset + size]


def ordinal_guard(value, removed, maximum=157):
    if value in removed or value > maximum or value < -3:
        raise ValueError('Unsupported or removed library ordinal: ' + str(value))


def leb(data, pos, signed=False):
    result, shift = 0, 0
    while pos < len(data) and shift < 64:
        value = data[pos]
        pos += 1
        result |= (value & 127) << shift
        shift += 7
        if not value & 128:
            if signed and value & 64:
                result -= 1 << shift
            return result, pos
    raise ValueError('Truncated/oversized binding LEB')


def ensure_binding_ordinals(data, removed):
    pos, ordinal = 0, 0
    while pos < len(data):
        byte = data[pos]
        pos += 1
        op, imm = byte & 240, byte & 15
        if op == 0:
            ordinal = 0
        elif op == 0x10:
            ordinal = imm
        elif op == 0x20:
            ordinal, pos = leb(data, pos)
        elif op == 0x30:
            ordinal = imm - 16 if imm else 0
        elif op == 0x40:
            try:
                pos = data.index(0, pos) + 1
            except ValueError:
                raise ValueError('Unterminated binding symbol') from None
        elif op == 0x50:
            if imm not in (1, 2, 3):
                raise ValueError('Unknown binding type')
        elif op in (0x60, 0x70, 0x80):
            _, pos = leb(data, pos, signed=op == 0x60)
        elif op in (0x90, 0xb0):
            ordinal_guard(ordinal, removed)
        elif op == 0xa0:
            ordinal_guard(ordinal, removed)
            _, pos = leb(data, pos)
        elif op == 0xc0:
            ordinal_guard(ordinal, removed)
            _, pos = leb(data, pos)
            _, pos = leb(data, pos)
        else:
            raise ValueError('Unknown/threaded binding opcode')
        if op in (0x10, 0x20, 0x30):
            ordinal_guard(ordinal, removed)


def ensure_chained_ordinals(blob, removed):
    if len(blob) < 28:
        raise ValueError('Truncated chained fixups')
    version, starts, imports, symbols, count, form, symform = struct.unpack_from('<7I', blob)
    if version != 0 or form not in (1, 2, 3) or symform != 0 or min(starts, imports, symbols) < 28:
        raise ValueError('Unsupported chained fixups format')
    stride = {1: 4, 2: 8, 3: 16}[form]
    bounded(blob, imports, count * stride)
    bounded(blob, symbols, 0)
    for i in range(count):
        at = imports + stride * i
        if form in (1, 2):
            value = struct.unpack_from('<I', blob, at)[0]
            ordinal = value & 255
            name = value >> 9
            # SDK fixup-chains.h: 8-bit -15..240, NOT signed int8.
            if ordinal > 0xf0:
                ordinal -= 256
        else:
            value = struct.unpack_from('<Q', blob, at)[0]
            if (value >> 17) & 0x7fff:
                raise ValueError('Chained import reserved bits')
            ordinal = value & 65535
            name = value >> 32
            # SDK fixup-chains.h: 16-bit -15..65520.
            if ordinal > 0xfff0:
                ordinal -= 65536
        ordinal_guard(ordinal, removed)
        if symbols + name >= len(blob) or b'\0' not in blob[symbols + name:]:
            raise ValueError('Chained import symbol bounds')


def symbols(data, commands):
    found = []
    for cmd, raw in commands:
        if cmd != 2:
            continue
        symoff, count, stroff, strsize = struct.unpack_from('<4I', raw, 8)
        bounded(data, symoff, count * 16)
        strings = bounded(data, stroff, strsize)
        for i in range(count):
            name, typ, section, desc, value = struct.unpack_from('<IBBHQ', data, symoff + 16 * i)
            end = strings.find(b'\0', name)
            if name >= len(strings) or end < 0:
                raise ValueError('Symbol string bounds')
            found.append((strings[name:end].decode(), typ, section, desc, value))
    return found


def ensure_runner_bindings(data, commands):
    removed = {158, 159}
    for cmd, raw in commands:
        if cmd in (0x22, 0x80000022):
            fields = struct.unpack_from('<10I', raw, 8)
            for offset, size in (fields[2:4], fields[4:6], fields[6:8]):
                ensure_binding_ordinals(bounded(data, offset, size), removed)
        elif cmd == 0x80000034:
            offset, size = struct.unpack_from('<II', raw, 8)
            ensure_chained_ordinals(bounded(data, offset, size), removed)
    for _, typ, _, desc, _ in symbols(data, commands):
        if not typ & 0xe0 and typ & 0xe == 0:
            ordinal = desc >> 8
            if ordinal not in (0xfe, 0xff):
                ordinal_guard(ordinal, removed)


def patch_runner(data):
    before = inspect(data)
    if before['filetype'] != 2 or before['signatures'] or before['uuid'] != UUID:
        raise ValueError('Requires pinned unsigned executable UUID')
    if len(before['deps']) != 159 or before['deps'][-3:] != ['@executable_path/Frameworks/TurboIOPrivateAddon.dylib'] + OLD_LOADS:
        raise ValueError('Unknown Runner dependency suffix')
    suffix = before['commands'][-2:]
    suffix_names = []
    for cmd, raw in suffix:
        if cmd != 0xc:
            raise ValueError('Unknown Runner load command suffix')
        offset = struct.unpack_from('<I', raw, 8)[0]
        suffix_names.append(raw[offset:].split(b'\0')[0].decode())
    if suffix_names != OLD_LOADS:
        raise ValueError('Unknown Runner load command suffix')
    ensure_runner_bindings(data, before['commands'])
    name = LOAD.encode() + b'\0'
    length = (24 + len(name) + 7) // 8 * 8
    command = struct.pack('<6I', 0xc, length, 24, 0, 0, 0) + name
    command += bytes(length - len(command))
    retained = before['commands'][:-2]
    new = b''.join(raw for _, raw in retained) + command
    end = 32 + len(new)
    if end > before['end'] or end > before['first']:
        raise ValueError('New command exceeds verified header space')
    result = bytearray(data)
    result[32:before['end']] = new + bytes(before['end'] - end)
    struct.pack_into('<II', result, 16, len(retained) + 1, len(new))
    result = bytes(result)
    after = inspect(result)
    if after['commands'][:-1] != retained or after['deps'] != before['deps'][:-2] + [LOAD] or after['uuid'] != before['uuid'] or after['signatures']:
        raise ValueError('Runner original command/ordinal preservation failed')
    if len(data) != len(result) or result[:16] != data[:16] or result[24:32] != data[24:32] or result[before['end']:] != data[before['end']:]:
        raise ValueError('Runner non-allowlisted bytes changed')
    return result


def validate_entries(entries):
    names = set()
    for entry in entries:
        name = entry.filename
        mode = entry.external_attr >> 16
        if not name or '\\' in name or '\0' in name or PurePosixPath(name).is_absolute() or any(p in ('', '.', '..') for p in name.rstrip('/').split('/')):
            raise ValueError('Unsafe ZIP path')
        if name in names or entry.flag_bits & 1 or stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR):
            raise ValueError('Duplicate/encrypted/special ZIP member')
        if '_CodeSignature' in PurePosixPath(name).parts or name.endswith('/embedded.mobileprovision'):
            raise ValueError('Pinned source must be unsigned')
        names.add(name)
    return names


def validate_module(data):
    info = inspect(data)
    if info['filetype'] != 6 or info['install'] != LOAD or info['minimum'] != 15 << 16 or info['signatures']:
        raise ValueError('Requires unsigned thin arm64 iOS15 independent NewsHub dylib')
    if not info['deps'] or any(not d.startswith(('/System/Library/', '/usr/lib/')) for d in info['deps']):
        raise ValueError('NewsHub dependencies must be system-only')
    records = symbols(data, info['commands'])
    defined = {name for name, typ, _, _, _ in records if not typ & 0xe0 and typ & 0xe != 0}
    required = {'_TIONewsHubInstallUI', '_TIONewsHubNavigationInstall', '_OBJC_CLASS_$_TIONewsHubPanel', '_OBJC_CLASS_$_TIOAIHotDailyClient', '_OBJC_CLASS_$_TIONewsFetcher', '_OBJC_CLASS_$_TIONewsSourceConfig', '_OBJC_CLASS_$_TIONewsScheduler', '_OBJC_CLASS_$_TIOImportQueue'}
    if not required <= defined:
        raise ValueError('Real NewsHub entry/client/classes absent')
    forbidden = {'_OBJC_CLASS_$_TIONewsReader', '_OBJC_CLASS_$_TIOResearchTabs'}
    if forbidden & defined or any(any(t in n for t in ('DailyStage', 'AIHotStage', 'ImportTrial', 'ManuscriptProbe')) for n in defined):
        raise ValueError('Experimental or duplicate host classes remain')
    if any((desc >> 8) == 0xfe for _, typ, _, desc, _ in records if not typ & 0xe0 and typ & 0xe == 0):
        raise ValueError('Dynamic lookup import is forbidden')
    initializers, text_sections = [], []
    for cmd, raw in info['commands']:
        if cmd == 0x19:
            for i in range(struct.unpack_from('<I', raw, 64)[0]):
                at = 72 + i * 80
                name = raw[at:at + 16].split(b'\0')[0]
                addr = struct.unpack_from('<Q', raw, at + 32)[0]
                extent, offset = struct.unpack_from('<QI', raw, at + 40)
                if name == b'__text':
                    text_sections.append((addr, extent, offset))
                if name == b'__mod_init_func':
                    bounded(data, offset, extent)
                    if extent % 8:
                        raise ValueError('Invalid initializer section')
                    initializers.extend(struct.unpack_from('<Q', data, offset + k)[0] for k in range(0, extent, 8))
    entries = {n: value for n, typ, _, _, value in records if typ & 0xe != 0 and not typ & 0xe0}
    if initializers != [entries.get('_LoadNewsHub')]:
        raise ValueError('Expected the defined NewsHub startup constructor')
    for name in ('_LoadNewsHub', '_TIONewsHubInstallUI', '_TIONewsHubNavigationInstall'):
        address = entries[name]
        section = next((s for s in text_sections if s[0] <= address and address + 4 <= s[0] + s[1]), None)
        if section is None or bounded(data, section[2] + address - section[0], 4) == b'\xc0\x03\x5f\xd6':
            raise ValueError('NewsHub executable entry is absent or an empty return: ' + name)
    return info


def verify_archive(source, final, module, patched, marker):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(final) as result:
        oldnames = validate_entries(original.infolist())
        newnames = validate_entries(result.infolist())
        added = {ROOT + 'Frameworks/' + DYLIB, ROOT + 'TurboIONewsHub.json'}
        if newnames != (oldnames - REMOVED) | added or result.testzip() is not None:
            raise ValueError('ZIP exact member diff or CRC failed')
        unchanged = 0
        for entry in original.infolist():
            name = entry.filename
            if name in REMOVED:
                continue
            expected = patched if name == ROOT + 'Runner' else original.read(entry)
            if sha(result.read(name)) != sha(expected):
                raise ValueError('Unexpected package member change: ' + name)
            if result.getinfo(name).external_attr != entry.external_attr:
                raise ValueError('ZIP member mode changed')
            unchanged += name != ROOT + 'Runner'
        if result.read(ROOT + 'Frameworks/' + DYLIB) != module or result.read(ROOT + 'TurboIONewsHub.json') != marker:
            raise ValueError('New module or marker mismatch')
    return unchanged


def prepare(source, dylib, out):
    source, dylib = Path(source).resolve(strict=True), Path(dylib).resolve(strict=True)
    out = Path(out).resolve()
    if out.exists() or out in source.parents or out in dylib.parents:
        raise ValueError('Output must be a new separate directory')
    if file_sha(source) != BASE_SHA:
        raise ValueError('Requires exact shipped 135831 IPA SHA256')
    module = dylib.read_bytes()
    header = validate_module(module)
    build_manifest = dylib.parent / 'newshub-source-manifest.json'
    build_data = build_manifest.read_bytes()
    build = json.loads(build_data)
    expected_sources = {'AIHotDaily.m', 'TIONewsSource.m', 'TIONewsScheduler.m', 'TIOImportQueue.m', 'TIONewsHubPanel.m', 'TIONewsHubEntry.m', 'NewsHubNavigation.m'}
    if set(build.get('compiled_sources', [])) != expected_sources or build.get('dylib_sha256') != sha(module) or build.get('install_name') != LOAD or build.get('signed') is not False or build.get('dynamic_lookup') is not False:
        raise ValueError('Requires exact independent frozen build provenance')
    for record in build['sources']:
        name = record['path']
        if Path(name).name != name or sha((dylib.parent / 'source-snapshot' / name).read_bytes()) != record['sha256']:
            raise ValueError('Frozen source snapshot changed')
    with zipfile.ZipFile(source) as original:
        names = validate_entries(original.infolist())
        if not REMOVED <= names or ROOT + 'Frameworks/' + DYLIB in names or ROOT + 'TurboIONewsHub.json' in names:
            raise ValueError('Unexpected baseline module/marker inventory')
        runner = original.read(ROOT + 'Runner')
        if sha(runner) != RUNNER_SHA or sha(original.read(ROOT + 'Frameworks/TurboIOPrivateAddon.dylib')) != ADDON_SHA:
            raise ValueError('Pinned Runner or original Addon hash mismatch')
        patched = patch_runner(runner)
        for dependency in inspect(patched)['deps']:
            member = None
            if dependency.startswith('@rpath/'):
                member = ROOT + 'Frameworks/' + dependency[7:]
            elif dependency.startswith('@executable_path/'):
                member = ROOT + dependency[len('@executable_path/'):]
            elif not dependency.startswith(('/System/Library/', '/usr/lib/')):
                raise ValueError('Unsupported Runner dependency')
            if member and member != ROOT + 'Frameworks/' + DYLIB and member not in names - REMOVED:
                raise ValueError('Missing retained dependency: ' + member)
        marker = json.dumps(dict(module='NewsHub', build_tag=out.name, module_sha256=sha(module), frozen_build_manifest_sha256=sha(build_data), source_ipa_sha256=BASE_SHA, experimental_pages_removed=True, device_verified=False, automatic_official_import_verified=False), indent=2).encode() + b'\n'
        out.mkdir(parents=True, mode=0o700, exist_ok=False)
        ipa = out / 'TurboIO-NewsHub-unsigned.ipa'
        with zipfile.ZipFile(ipa, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as result:
            for entry in original.infolist():
                if entry.filename in REMOVED:
                    continue
                preserved = copy.copy(entry)
                preserved.compress_type = zipfile.ZIP_DEFLATED
                result.writestr(preserved, patched if entry.filename == ROOT + 'Runner' else original.read(entry))
            for name, data, mode in ((ROOT + 'Frameworks/' + DYLIB, module, 0o755), (ROOT + 'TurboIONewsHub.json', marker, 0o644)):
                entry = zipfile.ZipInfo(name)
                entry.create_system = 3
                entry.external_attr = (stat.S_IFREG | mode) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                result.writestr(entry, data)
    unchanged = verify_archive(source, ipa, module, patched, marker)
    if file_sha(source) != BASE_SHA or file_sha(dylib) != sha(module) or build_manifest.read_bytes() != build_data:
        raise ValueError('Source IPA, module or frozen build manifest changed')
    report = dict(status='UNSIGNED_NEWSHUB_NOT_DEVICE_VERIFIED', source_ipa_sha256=BASE_SHA, ipa_sha256=file_sha(ipa), ipa_bytes=ipa.stat().st_size,
                  runner_uuid=UUID, changed=[dict(path=ROOT + 'Runner', before=RUNNER_SHA, after=sha(patched), reason='Remove final Stage/Trial load commands; append NewsHub; original ordinals 1..157 and body unchanged')],
                  removed=sorted(REMOVED), added=[dict(path=ROOT + 'Frameworks/' + DYLIB, sha256=sha(module)), dict(path=ROOT + 'TurboIONewsHub.json', sha256=sha(marker))],
                  original_addon_sha256=ADDON_SHA, unchanged_count=unchanged, all_retained_members_sha256_verified=True, source_unchanged=True, zip_crc='PASS', binding_ordinals='PASS_CLASSIC_WEAK_LAZY_CHAINED_NLIST',
                  module_dependencies=header['deps'], frozen_build_manifest_sha256=sha(build_data), compiled_sources=sorted(expected_sources), info_plist_unchanged=True, device_verified=False, automatic_official_import_verified=False)
    (out / 'newshub-manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(dict(ipa=str(ipa), sha256=report['ipa_sha256'], changed=1, removed=4, added=2, unchanged=unchanged), indent=2))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--dylib', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    prepare(args.source, args.dylib, args.out)
