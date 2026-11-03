"""Offline derivative audit; never reads account state or prints asset contents."""
import hashlib
import json
import re
import sys
import zipfile
from dex_slot import addon_dex_slot

HOOK_MARKER = b'turboioOriginal'
DEX_MEMBER = re.compile(r'classes([2-9][0-9]*|1[0-9]+)?\.dex')


def parse_arguments(argv):
    if len(argv) < 2:
        raise SystemExit('Expected source output [--public-assets] [--hook-dex NAME ...]')
    public_assets, declared, index = False, [], 2
    payload_apk = payload_sidecar = None
    cheater_io = False
    while index < len(argv):
        flag = argv[index]
        if flag == '--public-assets':
            public_assets = True
        elif flag == '--cheater-io':
            cheater_io = True
        elif flag in ('--payload-apk', '--payload-sidecar'):
            index += 1
            if index == len(argv) or not argv[index]:
                raise SystemExit(flag + ' expects a path')
            if flag == '--payload-apk': payload_apk = argv[index]
            else: payload_sidecar = argv[index]
        elif flag == '--hook-dex':
            index += 1
            if index == len(argv) or not DEX_MEMBER.fullmatch(argv[index]):
                raise SystemExit('--hook-dex expects a DEX member name')
            declared.append(argv[index])
        else:
            raise SystemExit('Unknown option: ' + flag)
        index += 1
    if bool(payload_apk) != bool(payload_sidecar) or ((payload_apk or cheater_io) and not public_assets):
        raise SystemExit('Payload requires both paths; payload and branding require --public-assets')
    return argv[0], argv[1], public_assets, declared, payload_apk, payload_sidecar, cheater_io


def dex_members(archive):
    return [name for name in archive.namelist() if DEX_MEMBER.fullmatch(name)]


source, output, public_assets, declared, payload_apk, payload_sidecar, cheater_io = parse_arguments(sys.argv[1:])
extra = {}
if public_assets:
    from addon_assets import assets
    from dsh_manifest import shared_patch as patch_manifest
    extra = assets(payload_apk, payload_sidecar)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as derivative:
    before = {f.filename for f in original.infolist() if not f.is_dir()}
    after = {f.filename for f in derivative.infolist() if not f.is_dir()}
    for archive in (original, derivative):
        names = [f.filename for f in archive.infolist()]
        if len(names) != len(set(names)):
            raise SystemExit('Duplicate ZIP entries')
        if archive.testzip() is not None:
            raise SystemExit('ZIP integrity failed')
    removed = sorted(before - after)
    added = sorted(after - before)
    changed = sorted(name for name in before & after
                     if hashlib.sha256(original.read(name)).digest()
                     != hashlib.sha256(derivative.read(name)).digest())
    addon_slot = addon_dex_slot(before)
    unexpected = [name for name in removed if not name.startswith('META-INF/')]
    if addon_slot not in after or not derivative.read(addon_slot).startswith(b'dex\n'):
        unexpected.append(addon_slot)
    unexpected += [name for name in added if name != addon_slot and name not in extra and not name.startswith('META-INF/')]
    unexpected += [name for name, path in extra.items() if name not in after or derivative.read(name) != path.read_bytes()]
    expected_manifest = bool(extra) and derivative.read('AndroidManifest.xml') == patch_manifest(original.read('AndroidManifest.xml'), embedded=bool(payload_apk), cheater_io=cheater_io)
    if public_assets and not expected_manifest:
        unexpected.append('AndroidManifest.xml')
    # The candidate's marker bytes cannot authorize changes to other vendor DEX.
    original_dex = [name for name in dex_members(original) if name in before]
    hook_slots = declared or ['classes2.dex']
    if len(hook_slots) != len(set(hook_slots)):
        raise SystemExit('Duplicate hook DEX slot')
    hook_problems = []
    for name in hook_slots:
        if name not in original_dex or name not in after:
            hook_problems.append(name)
            continue
        content = derivative.read(name)
        if name not in changed or not content.startswith(b'dex\n') or HOOK_MARKER not in content or HOOK_MARKER in original.read(name):
            hook_problems.append(name)
    unexpected += hook_problems
    unexpected += [name for name in changed
                   if name not in hook_slots
                   and not (name == 'AndroidManifest.xml' and expected_manifest)
                   and not name.startswith('META-INF/')]
    print(json.dumps({'added': added, 'changed': changed, 'hook_dex_slots': sorted(hook_slots),
                     'removed_signature_entries': len(removed),
                     'resources_native_libraries_preserved': not unexpected,
                     'manifest_only_tts_and_background_added': expected_manifest,
                     'unexpected_paths': unexpected}, ensure_ascii=False))
    raise SystemExit(bool(unexpected))
