"""Reproduce pinned TMG3 from the exact official 1.0.6.11 ZIP. Never connects to a device."""
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
    spec = json.loads((HERE / 'probe-build-inputs.json').read_text(encoding='utf-8'))
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
    run_binary([llvm / 'ld.lld', '-T', HERE / 'probe-link.ld', '--entry=m8_hook_ctor', *objects, '-o', '-'], elf)
    assert not run([llvm / 'llvm-nm', '-u', elf]).strip()
    module_path = out / 'module.bin'
    run_binary([llvm / 'llvm-objcopy', '-O', 'binary', elf, '-'], module_path)
    module = module_path.read_bytes()
    print('Compiled module:', len(module), sha(module), flush=True)

if __name__ == '__main__':
    import sys
    build(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve(),Path(sys.argv[3]).resolve())
