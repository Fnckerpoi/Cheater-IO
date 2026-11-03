"""Preserve original ZIP payloads; replace only the DEX files carrying reviewed hooks and add ours.

Never round-trip vendor resources through a case-insensitive filesystem: res/A.xml
and res/a.xml are different ZIP members even on macOS. Signing happens separately.

Which DEX members carry hooks depends on where the host classes were decoded, and a
host can spread them over several vendor DEX files. --hook-dex names each one the
builder patched. Without it, only the legacy classes2.dex slot is allowed.
An assembled member that carries hooks but is not declared is a hard error:
keeping the vendor bytes there would silently drop the hook.
"""
import pathlib
import re
import sys
import zipfile
from dex_slot import addon_dex_slot

HOOK_MARKER = b'turboioOriginal'
DEX_MEMBER = re.compile(r'classes([2-9][0-9]*|1[0-9]+)?\.dex')


def parse_arguments(argv):
    positionals, flags = argv[:4], argv[4:]
    if len(positionals) != 4:
        raise SystemExit('Expected source assembled addon output [--public-assets] [--hook-dex NAME ...]')
    public_assets, declared, index = False, [], 0
    payload_apk = payload_sidecar = None
    cheater_io = False
    while index < len(flags):
        flag = flags[index]
        if flag == '--public-assets':
            public_assets = True
        elif flag == '--cheater-io':
            cheater_io = True
        elif flag in ('--payload-apk', '--payload-sidecar'):
            index += 1
            if index == len(flags) or not flags[index]:
                raise SystemExit(flag + ' expects a path')
            if flag == '--payload-apk': payload_apk = flags[index]
            else: payload_sidecar = flags[index]
        elif flag == '--hook-dex':
            index += 1
            if index == len(flags) or not DEX_MEMBER.fullmatch(flags[index]):
                raise SystemExit('--hook-dex expects a DEX member name')
            declared.append(flags[index])
        else:
            raise SystemExit('Unknown option: ' + flag)
        index += 1
    if bool(payload_apk) != bool(payload_sidecar) or ((payload_apk or cheater_io) and not public_assets):
        raise SystemExit('Payload requires both paths; payload and branding require --public-assets')
    return [pathlib.Path(value) for value in positionals], public_assets, declared, payload_apk, payload_sidecar, cheater_io


def dex_members(archive):
    return [name for name in archive.namelist() if DEX_MEMBER.fullmatch(name)]


positions, public_assets, declared, payload_apk, payload_sidecar, cheater_io = parse_arguments(sys.argv[1:])
source, assembled, addon, destination = positions
extra = {}
if public_assets:
    from addon_assets import assets
    from dsh_manifest import shared_patch as patch_manifest
    extra = assets(payload_apk, payload_sidecar)
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
    original_dex, assembled_dex = dex_members(original), dex_members(modified)
    hooked = [name for name in assembled_dex if HOOK_MARKER in modified.read(name)]
    slots = declared or ['classes2.dex']
    if len(slots) != len(set(slots)):
        raise SystemExit('Duplicate hook DEX slot')
    patched = {}
    for name in slots:
        if name not in original_dex:
            raise SystemExit('Hook DEX missing from original APK: ' + name)
        if name not in assembled_dex:
            raise SystemExit('Hook DEX missing from assembled APK: ' + name)
        content = modified.read(name)
        if not content.startswith(b'dex\n') or HOOK_MARKER not in content:
            raise SystemExit('Hook DEX carries no reviewed hook: ' + name)
        if HOOK_MARKER in original.read(name):
            raise SystemExit('Original hook DEX is not pristine: ' + name)
        patched[name] = content
    undeclared = [name for name in hooked if name not in slots]
    if undeclared:
        raise SystemExit('Assembled hooks outside the declared DEX would be dropped: ' + ', '.join(undeclared))
    with zipfile.ZipFile(temporary, 'x', allowZip64=True) as output:
        for info in original.infolist():
            if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', info.filename, re.I):
                continue
            content = patched[info.filename] if info.filename in patched else original.read(info)
            if extra and info.filename == 'AndroidManifest.xml': content = patch_manifest(content, embedded=bool(payload_apk), cheater_io=cheater_io)
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
print('Original resource/native payloads retained; declared hook DEX ('
      + ', '.join(slots) + '), addon DEX/assets and scoped TTS/background manifest additions reviewed.')
