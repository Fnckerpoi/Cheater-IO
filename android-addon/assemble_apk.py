"""Preserve original ZIP payloads; replace only reviewed hook DEX files and add ours.

Never round-trip vendor resources through a case-insensitive filesystem: res/A.xml
and res/a.xml are different ZIP members even on macOS. Signing happens separately.
"""
import pathlib
import re
import sys
import zipfile
from dex_slot import addon_dex_slot

source, assembled, addon, destination = map(pathlib.Path, sys.argv[1:5])
flags = sys.argv[5:]
extra = {}
if '--public-assets' in flags:
    from addon_assets import assets
    from dsh_manifest import shared_patch as patch_manifest
    extra = assets()
    flags.remove('--public-assets')
slots = []
while flags:
    if len(flags) < 2 or flags.pop(0) != '--hook-dex':
        raise SystemExit('Unknown assembly option')
    slot = flags.pop(0)
    if not re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', slot):
        raise SystemExit('Invalid hook DEX slot')
    slots.append(slot)
slots = slots or ['classes2.dex']
if len(slots) != len(set(slots)):
    raise SystemExit('Duplicate hook DEX slot')
if destination.resolve() in {source.resolve(), assembled.resolve(), addon.resolve()}:
    raise SystemExit('Output must not overwrite input')
temporary = destination.with_suffix('.assembling.apk')
if temporary.exists():
    raise SystemExit('Temporary output exists; inspect before retrying')
with zipfile.ZipFile(source) as original, zipfile.ZipFile(assembled) as modified:
    addon_slot = addon_dex_slot(original.namelist())
    if len(original.namelist()) != len(set(original.namelist())):
        raise SystemExit('Duplicate original ZIP members')
    if set(extra) & set(original.namelist()):
        raise SystemExit('Addon asset collision')
    if not addon.read_bytes().startswith(b'dex\n'):
        raise SystemExit('Invalid DEX input')
    patched = {}
    for slot in slots:
        if slot not in original.namelist() or slot not in modified.namelist():
            raise SystemExit('Missing reviewed hook DEX: ' + slot)
        content = modified.read(slot)
        if not content.startswith(b'dex\n') or b'turboioOriginal' not in content:
            raise SystemExit('Hook DEX carries no reviewed hook: ' + slot)
        if b'turboioOriginal' in original.read(slot):
            raise SystemExit('Original hook DEX is not pristine: ' + slot)
        patched[slot] = content
    undeclared = [name for name in modified.namelist()
                  if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', name)
                  and name not in slots and b'turboioOriginal' in modified.read(name)]
    if undeclared:
        raise SystemExit('Assembled hooks outside declared DEX: ' + ', '.join(undeclared))
    with zipfile.ZipFile(temporary, 'x', allowZip64=True) as output:
        for info in original.infolist():
            if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', info.filename, re.I):
                continue
            content = patched.get(info.filename)
            if content is None: content = original.read(info)
            if extra and info.filename == 'AndroidManifest.xml': content = patch_manifest(content)
            output.writestr(info, content)
        info = zipfile.ZipInfo(addon_slot, (2026, 9, 26, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        output.writestr(info, addon.read_bytes())
        for name, path in sorted(extra.items()):
            info = zipfile.ZipInfo(name, (2026, 9, 26, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            output.writestr(info, path.read_bytes())
temporary.replace(destination)
print('Original resource/native payloads retained; hook DEX, addon DEX/assets and scoped TTS/background manifest additions reviewed.')
