"""Build TMG4 firmware module from official 1.0.6.11 ZIP. Includes CRC audit. Never connects to a device."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
BASE = HERE.parent / 'strix-1.0.5.12-tmg3'
FLASH = 0x10190000


def sha(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def run(arguments, cwd=None):
    r = subprocess.run([str(a) for a in arguments], capture_output=True, text=True, cwd=cwd)
    if r.returncode:
        raise RuntimeError(r.stderr)
    return r.stdout


def run_bytes(arguments, input_data=None):
    result = subprocess.run([str(a) for a in arguments], input=input_data, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout


def run_binary(arguments, destination, input_data=None):
    destination.write_bytes(run_bytes(arguments, input_data))


def safe_zip(path, expected):
    assert sha(path.read_bytes()) == expected, f'Wrong input archive: expected {expected}'
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        assert len(names) == len(set(names)) == 17
        assert all(n == Path(n).name and not n.startswith('.') for n in names)
        assert sum(i.file_size for i in z.infolist()) < 24_000_000
        assert all((i.external_attr >> 16) & 0o170000 != 0o120000 for i in z.infolist())
        assert z.testzip() is None
        files = {n: z.read(n) for n in names}
    rows = json.loads(files['OtaFileInfo.json'])
    assert isinstance(rows, list) and len(rows) == 16
    assert len({r['Name'] for r in rows}) == 16
    assert set(files) == {r['Name'] for r in rows} | {'OtaFileInfo.json'}
    for r in rows:
        b = files[r['Name']]
        assert len(b) == r['Size'] and sha(b, 'md5') == r['Md5']
    return files, rows


def build(stock_path, llvm, out):
    spec = json.loads((HERE / 'build-inputs.json').read_text(encoding='utf-8'))
    stock, rows = safe_zip(stock_path, spec['stockZIP'])
    assert sha(stock['nuttx_ap.bin']) == spec['stockAP']
    out.mkdir(parents=True, exist_ok=True)
    src = out / 'source'
    shutil.copytree(BASE / 'src', src)
    shutil.copy2(BASE / 'ImageWireFormat.h', out / 'ImageWireFormat.h')
    patch_tool = shutil.which('patch') or Path(shutil.which('git')).parents[1] / 'usr/bin/patch.exe'
    run([patch_tool, '-p1', '--batch', '-i', HERE / 'source-overrides.patch'], cwd=src)
    for file in (HERE / 'overrides').rglob('*'):
        if file.is_file():
            dest=src / file.relative_to(HERE / 'overrides'); dest.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(file,dest)
    flags = [
        '--target=arm-none-eabi', '-mcpu=cortex-m33', '-mthumb', '-mfloat-abi=hard',
        '-mfpu=fpv5-sp-d16', '-ffreestanding', '-fno-builtin',
        '-Os', '-Wall', '-Wextra', '-Werror',
        '-I', str(src), '-I', str(src / 'app-runtime-v1/arm-include'), '-I', str(BASE / 'include'),
        '-include', str(src / 'firmware_profile.h'), '-DTIO_IMAGE_RX_WIDE=1',
        '-DTIO_DISPLAY_RUNTIME=1', '-DTDP_FREESTANDING', '-DTIO_ANIMATION_EXPERIMENT=1',
        '-DTIO_MUSIC_RUNTIME=1', '-DTD_DIAGNOSTICS_RUNTIME=1', '-DTD_FREESTANDING=1',
        '-DTD_STOCK_10412=0', '-DTD_STOCK_1055=1'
    ]
    objects = []
    rows_source = [r for r in spec['sources'] if not r['source'].startswith(('weread-v1/',)) and not (r['source'].startswith('music-runtime-v1/') and not r['source'].endswith('/menu10.c')) and not (r['source'].startswith('navigation-runtime-v1/') and not r['source'].endswith('/nav_lvgl.c')) and not (r['source'].startswith('focus-v1/') and '/focus_menu' not in r['source'])]
    rows_source.append({'source':'navigation-runtime-v1/shared_slot.c'})
    for row in rows_source:
        p = src / row['source']
        if not (HERE / 'overrides' / row['source']).exists(): assert sha(p.read_bytes()) == row['sha256'], 'Edited source: ' + row['source']
        obj = out / (p.parent.name + '-' + p.stem + '.o')
        run_binary([llvm / 'clang', *flags, '-c', p, '-o', '-'], obj)
        objects.append(obj)
    animation = BASE / 'assets/animation-frames.l8'
    assert sha(animation.read_bytes()) == 'f758dd6e3cdf5fc6550238df26e46111e9d45d098b9d95f649626ea58210ee1d'
    asm = out / 'animation-asset.S'
    asm.write_text(
        '.section .rodata.ta_asset,"a",%progbits\n'
        '.balign 64\n'
        '.global ta_asset\n'
        '.type ta_asset,%object\n'
        'ta_asset:\n'
        f'.incbin "{animation.as_posix()}", 0, 33792\n'
        '.size ta_asset, .-ta_asset\n'
    )
    extra = [
        src / 'port_allocate.c', HERE / 'trampolines.S', src / 'app-runtime-v1/app_abi.S',
        *[src / 'context-english-v1' / n for n in ('head_serial.S', 'head_observer.S', 'coexist.S')],
        asm
    ]
    for p in extra:
        obj = out / (p.stem + '.o')
        if p.suffix == '.S':
            preprocessed = run_bytes([llvm / 'clang', *flags, '-E', p, '-o', '-'])
            run_binary([llvm / 'clang', *flags[:5], '-x', 'assembler', '-c', '-', '-o', '-'], obj, preprocessed)
        else:
            run_binary([llvm / 'clang', *flags, '-c', p, '-o', '-'], obj)
        objects.append(obj)
    elf = out / 'module.elf'
    run_binary([llvm / 'ld.lld', '-T', HERE / 'link.ld', '--entry=m8_hook_ctor', *objects, '-o', '-'], elf)
    assert not run([llvm / 'llvm-nm', '-u', elf]).strip()
    module_path = out / 'module.bin'
    run_binary([llvm / 'llvm-objcopy', '-O', 'binary', elf, '-'], module_path)
    module = module_path.read_bytes()
    print('Compiled module:', len(module), sha(module), flush=True)

    syms = symbol_table(llvm, elf)
    base = stock['nuttx_ap.bin']
    at = int(spec['moduleVA'], 16) - FLASH
    ap = bytearray(base[:-8]) + bytearray(at - (len(base) - 8)) + module
    ap += bytearray((-len(ap)) % 4) + base[-8:]
    for e in spec['edits']:
        offset = int(e['offset'], 16)
        old = bytes.fromhex(e['before'])
        new = edit_bytes(e, syms)
        assert len(new) == len(old), f"Edit length mismatch at {e['offset']}"
        assert ap[offset:offset+len(old)] == old, f"Edit before mismatch at {e['offset']}"
        ap[offset:offset+len(new)] = new
    spec['moduleBytes'] = len(module)
    spec['moduleSHA256'] = sha(module)
    spec['APBytes'] = len(ap)
    spec['APSHA256'] = sha(ap)
    spec['APMD5'] = sha(ap, 'md5')
    stock['nuttx_ap.bin'] = bytes(ap)
    for row in rows:
        if row['Name'] == 'nuttx_ap.bin':
            row.update(Size=len(ap), Md5=sha(ap, 'md5'))
    stock['OtaFileInfo.json'] = (json.dumps(rows, indent=2) + '\n').encode()
    archive = out / (stock_path.stem.replace('Strix-OS-', 'StrixOS-') + '-TurboIO-TMG4-OFFLINE-CANDIDATE.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(stock.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 13, 0, 0, 0))
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100600 << 16
            z.writestr(info, data, compresslevel=9)
    spec['archive'] = {'name': archive.name, 'bytes': archive.stat().st_size, 'sha256': sha(archive.read_bytes()), 'md5': sha(archive.read_bytes(), 'md5')}
    (out / 'build-inputs.built.json').write_text(json.dumps(spec, indent=2) + '\n', encoding='utf-8')
    result = audit(stock_path, archive, spec)
    (out / 'audit.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def audit(stock_path, candidate_path, spec):
    stock, before = safe_zip(stock_path, spec['stockZIP'])
    files, after = safe_zip(candidate_path, spec['archive']['sha256'])
    ap = files['nuttx_ap.bin']
    base = stock['nuttx_ap.bin']
    assert len(ap) == spec['APBytes'] and sha(ap) == spec['APSHA256']
    assert [r['Name'] for r in before] == [r['Name'] for r in after]
    for old, new in zip(before, after):
        if old['Name'] == 'nuttx_ap.bin':
            assert {k: v for k, v in old.items() if k not in ('Size', 'Md5')} == {k: v for k, v in new.items() if k not in ('Size', 'Md5')}
        else:
            assert old == new and stock[old['Name']] == files[new['Name']]
    assert ap[:16] == base[:16] and ap[-8:] == base[-8:]
    for at in (0x408, 0x414, 0x574, 0x580, 0x58c, 0x598, 0x674):
        assert ap[at:at+12] == base[at:at+12]
    syms = None
    allowed = set()
    for e in spec['edits']:
        at = int(e['offset'], 16)
        a = bytes.fromhex(e['before'])
        assert base[at:at+len(a)] == a and len(ap[at:at+len(a)]) == len(a) and ap[at:at+len(a)] != a
        assert not allowed.intersection(range(at, at+len(a)))
        allowed.update(range(at, at+len(a)))
    assert all(x == y or i in allowed for i, (x, y) in enumerate(zip(base[:-8], ap)))
    at = int(spec['moduleVA'], 16) - FLASH
    assert not any(ap[len(base)-8:at])
    assert sha(ap[at:at+spec['moduleBytes']]) == spec['moduleSHA256']
    assert not any(ap[at+spec['moduleBytes']:-8])
    return {
        'passed': True,
        'customChangedPayloads': ['nuttx_ap.bin'],
        'unchangedNewStockPayloads': sum(1 for r in before if r['Name'] != 'nuttx_ap.bin'),
        'manifestPayloads': len(before),
        'archiveEntries': len(files),
        'APBytes': len(ap),
        'APSHA256': sha(ap),
        'APMD5': sha(ap, 'md5'),
        'moduleBytes': spec['moduleBytes'],
        'moduleSHA256': spec['moduleSHA256'],
        'archive': spec['archive']['name'],
        'archiveBytes': candidate_path.stat().st_size,
        'archiveSHA256': sha(candidate_path.read_bytes()),
        'archiveMD5': sha(candidate_path.read_bytes(), 'md5'),
        'OtaFileInfoBytes': len(files['OtaFileInfo.json']),
        'OtaFileInfoSHA256': sha(files['OtaFileInfo.json']),
        'whitelistedPatches': len(spec['edits']),
        'startupDescriptorsPreserved': 7,
        'members': {n: [len(d), sha(d)] for n, d in sorted(files.items())},
        'deviceIO': False
    }


def symbol_table(llvm, elf):
    table = {}
    for line in run([llvm / 'llvm-nm', '--defined-only', elf]).splitlines():
        part = line.split()
        if len(part) == 3:
            table[part[2]] = int(part[0], 16) & ~1
    return table


def encode_branch(kind, source, target):
    delta = target - (source + 4)
    assert delta % 2 == 0 and -(1 << 24) <= delta < (1 << 24)
    s = 1 if delta < 0 else 0
    u = delta & 0x1ffffff
    j1 = ~(((u >> 23) & 1) ^ s) & 1
    j2 = ~(((u >> 22) & 1) ^ s) & 1
    h1 = 0xF000 | (s << 10) | ((u >> 12) & 0x3ff)
    h2 = (0xD000 if kind == 'BL' else 0x9000) | (j1 << 13) | (j2 << 11) | ((u >> 1) & 0x7ff)
    return struct.pack('<HH', h1, h2)


def edit_bytes(edit, symbols):
    if 'symbol' not in edit:
        return bytes.fromhex(edit['after'])
    return encode_branch(edit['kind'], FLASH + int(edit['offset'], 16), symbols[edit['symbol']]) + bytes.fromhex(edit.get('tail', ''))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--stock', type=Path, required=True)
    parser.add_argument('--audit', type=Path)
    parser.add_argument('--llvm-bin', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    if args.audit:
        spec = json.loads((args.audit.parent / 'build-inputs.built.json').read_text(encoding='utf-8'))
        result = audit(args.stock, args.audit, spec)
    else:
        assert args.llvm_bin and args.out
        result = build(args.stock.resolve(), args.llvm_bin.resolve(), args.out.resolve())
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
