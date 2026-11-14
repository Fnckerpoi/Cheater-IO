"""Offline derivative audit; never reads account state or prints asset contents."""
import hashlib
import json
import re
import sys
import zipfile
from dex_slot import addon_dex_slot

source, output = sys.argv[1:3]
flags = sys.argv[3:]
extra = {}
if '--public-assets' in flags:
    from addon_assets import assets
    from dsh_manifest import shared_patch as patch_manifest
    extra = assets()
    flags.remove('--public-assets')
slots = []
while flags:
    if len(flags) < 2 or flags.pop(0) != '--hook-dex':
        raise SystemExit('Unknown audit option')
    slot = flags.pop(0)
    if not re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', slot):
        raise SystemExit('Invalid hook DEX slot')
    slots.append(slot)
slots = slots or ['classes2.dex']
if len(slots) != len(set(slots)):
    raise SystemExit('Duplicate hook DEX slot')
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
    expected_manifest = bool(extra) and derivative.read('AndroidManifest.xml') == patch_manifest(original.read('AndroidManifest.xml'))
    for slot in slots:
        if slot not in before or slot not in after or slot not in changed:
            unexpected.append(slot)
            continue
        if not derivative.read(slot).startswith(b'dex\n') or b'turboioOriginal' not in derivative.read(slot):
            unexpected.append(slot)
        if b'turboioOriginal' in original.read(slot):
            unexpected.append(slot)
    unexpected += [name for name in changed if name not in slots and not (name == 'AndroidManifest.xml' and expected_manifest)
                   and not name.startswith('META-INF/')]
    print(json.dumps({'added': added, 'changed': changed, 'hook_dex_slots': slots, 'removed_signature_entries': len(removed),
                     'resources_native_libraries_preserved': not unexpected,
                     'manifest_only_tts_and_background_added': expected_manifest,
                     'unexpected_paths': unexpected}, ensure_ascii=False))
    raise SystemExit(bool(unexpected))
