"""Windows build utilities; reuse the reviewed URL constants and base auditor."""
import hashlib
import json
import pathlib
import re
import runpy
import sys
import zipfile


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def signature(name):
    return re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', name, re.I)


mode, *args = sys.argv[1:]
if mode == 'jar':
    classes, target = map(pathlib.Path, args)
    files = sorted(classes.rglob('*.class'))
    if not files:
        raise SystemExit('No compiled classes')
    with zipfile.ZipFile(target, 'x', zipfile.ZIP_DEFLATED) as out:
        for file in files:
            out.write(file, file.relative_to(classes).as_posix())
elif mode == 'patch':
    source, base, work = map(pathlib.Path, args[:3])
    node = args[3] if len(args) == 4 else 'node'
    script = pathlib.Path(__file__).with_name('package-official-ota-preparation.py')
    api = runpy.run_path(str(script))
    profile = api['native_profile'](source)
    with zipfile.ZipFile(base) as archive:
        api['check_zip'](archive)
        original = archive.read(api['NATIVE'])
    patched = api['patch_native'](original, profile)
    library = work / 'libapp-guarded.so'
    if library.exists():
        if library.read_bytes() != patched:
            raise SystemExit('Refuse to overwrite a different staged native library')
    else:
        with library.open('xb') as output:
            output.write(patched)
    api['prepare'](source, base, library, work/'guarded-unsigned.apk', True, node)
elif mode == 'audit-signed':
    unsigned, signed, source, report = map(pathlib.Path, args)
    with zipfile.ZipFile(unsigned) as before, zipfile.ZipFile(signed) as after:
        for archive in (before, after):
            names = archive.namelist()
            if len(names) != len(set(names)) or archive.testzip() is not None:
                raise SystemExit('Duplicate entries or corrupt ZIP')
        expected = {n for n in before.namelist() if not signature(n)}
        actual = {n for n in after.namelist() if not signature(n)}
        if expected != actual or any(before.read(n) != after.read(n) for n in expected):
            raise SystemExit('Signing/alignment changed non-signature payloads')
    report.write_text(json.dumps({'sourceSha256':sha(source), 'unsignedSha256':sha(unsigned),
        'signedSha256':sha(signed), 'signedPayloadAuditPassed':True,
        'packageNameChanged':False, 'installed':False, 'deviceValidated':False,
        'signatureCoverage':'Compare source-certificates.txt and signed-certificates.txt; a different signer cannot normally update the installed original.'}, indent=2)+'\n')
elif mode == 'public-preflight':
    source, smali_root, report_path = map(pathlib.Path, args)
    expected_source = 'd39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0'
    if sha(source) != expected_source:
        raise SystemExit('PUBLIC input SHA256 mismatch; original allowlist unchanged')
    script = pathlib.Path(__file__).with_name('package-official-ota-preparation.py')
    constants = {}
    exec(compile(script.read_text().split('root = pathlib.Path')[0], str(script), 'exec'), constants)
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise SystemExit('PUBLIC ZIP has duplicates or corruption')
        if names.count('classes4.dex') != 1 or not archive.read('classes4.dex').startswith(b'dex\n'):
            raise SystemExit('PUBLIC addon DEX missing or invalid')
        if archive.read('assets/turboio/official-ota-guarded.txt') != b'ANDROID-105-OFFICIAL-OTA-GUARD-02\n':
            raise SystemExit('Expected guarded PUBLIC marker missing')
        library = archive.read(constants['NATIVE'])
        if hashlib.sha256(library).hexdigest() != constants['PATCHED']:
            raise SystemExit('Expected guarded native library missing')
        for offset, _, replacement in constants['SLOTS']:
            if library[offset:offset+len(replacement)] != replacement:
                raise SystemExit('Guarded URL slot mismatch')
        manifest = archive.read('AndroidManifest.xml')
        original_dex_sha = hashlib.sha256(archive.read('classes4.dex')).hexdigest()
    required = {'E3/V.smali':('turboioOriginal_o(LE3/Q;)V','OfficialOtaBridge;->allowQueue'),
                'E3/u.1.smali':('turboioOriginal_v(Ljava/io/File;','OfficialOtaBridge;->allowFile')}
    for relative, markers in required.items():
        suffix, filename = relative.split('/')
        candidates = list(smali_root.rglob(filename))
        candidates = [p for p in candidates if suffix in p.parts]
        if len(candidates) != 1:
            raise SystemExit('Expected exactly one decoded host class: ' + relative)
        text = candidates[0].read_text(encoding='utf-8')
        if any(marker not in text for marker in markers):
            raise SystemExit('PUBLIC OTA hook precondition missing: ' + relative)
    report_path.write_text(json.dumps({'mode':'public-dex-update','sourceSha256':expected_source,
        'package':'com.rayneo.venus.pub','manifestSha256':hashlib.sha256(manifest).hexdigest(),
        'originalDexSha256':original_dex_sha,
        'guardedNativeSha256':constants['PATCHED'],'guardedUrlSlotsVerified':True,
        'otaHooksVerified':True,'installedSignerComparison':'Not available; installation update requires matching signer.'},indent=2)+'\n')
elif mode == 'public-merge':
    source, new_dex, output, report_path = map(pathlib.Path, args)
    if sha(source) != 'd39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0':
        raise SystemExit('PUBLIC input SHA256 mismatch; original allowlist unchanged')
    dex_bytes = new_dex.read_bytes()
    if not dex_bytes.startswith(b'dex\n'):
        raise SystemExit('New compiled addon is not DEX')
    if output.exists() or output.resolve() in {source.resolve(), new_dex.resolve()}:
        raise SystemExit('Refuse to overwrite an existing artifact or an input')
    with zipfile.ZipFile(source) as original:
        names = original.namelist()
        if len(names) != len(set(names)) or original.testzip() is not None:
            raise SystemExit('PUBLIC source ZIP integrity failure')
        if names.count('classes4.dex') != 1:
            raise SystemExit('Expected one addon DEX member')
        with zipfile.ZipFile(output, 'x', allowZip64=True) as dest:
            for info in original.infolist():
                if signature(info.filename):
                    continue
                dest.writestr(info,dex_bytes if info.filename=='classes4.dex' else original.read(info))
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as derivative:
        before,after=original.namelist(),derivative.namelist()
        if len(after)!=len(set(after)) or derivative.testzip() is not None:
            raise SystemExit('Derived APK ZIP integrity failure')
        removed=sorted(n for n in before if signature(n) and n not in after)
        if set(before)-set(removed)!=set(after):
            raise SystemExit('PUBLIC derivation changed ZIP member allowlist')
        expected_order=[n for n in before if n not in removed]
        if after!=expected_order:
            raise SystemExit('PUBLIC derivation changed ZIP member order')
        if derivative.read('classes4.dex')!=dex_bytes:
            raise SystemExit('New addon DEX mismatch')
        changed=sorted(n for n in before if n in after and original.read(n)!=derivative.read(n))
        if changed!=['classes4.dex'] or original.read('AndroidManifest.xml')!=derivative.read('AndroidManifest.xml'):
            raise SystemExit('Unexpected payload or manifest change: '+repr(changed))
    report_path.write_text(json.dumps({'mode':'public-dex-update','sourceSha256':sha(source),
        'outputSha256':sha(output),'changedPayloads':['classes4.dex'],
        'removedSignatureEntries':removed,'allOtherPayloadsByteIdentical':True,
        'manifestChanged':False,'packageNameChanged':False,'signed':False,'installed':False},indent=2)+'\n')
elif mode == 'public-audit':
    source, derivative_path, report_path = map(pathlib.Path, args)
    if sha(source) != 'd39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0':
        raise SystemExit('PUBLIC input SHA256 mismatch; original allowlist unchanged')
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(derivative_path) as derivative:
        before,after=original.namelist(),derivative.namelist()
        if len(before)!=len(set(before)) or len(after)!=len(set(after)) or original.testzip() is not None or derivative.testzip() is not None:
            raise SystemExit('PUBLIC source or derivative ZIP integrity failure')
        removed=sorted(n for n in before if signature(n) and n not in after)
        if set(before)-set(removed)!=set(after) or after!=[n for n in before if n not in removed]:
            raise SystemExit('PUBLIC derivation changed member allowlist or order')
        if derivative.read('AndroidManifest.xml')!=original.read('AndroidManifest.xml'):
            raise SystemExit('PUBLIC manifest changed')
        changed=sorted(n for n in before if n in after and derivative.read(n)!=original.read(n))
        if changed!=['classes4.dex']:
            raise SystemExit('Expected only classes4.dex changed: '+repr(changed))
        if not derivative.read('classes4.dex').startswith(b'dex\n'):
            raise SystemExit('Derived addon DEX invalid')
    report_path.write_text(json.dumps({'mode':'public-dex-update','sourceSha256':sha(source),
        'outputSha256':sha(derivative_path),'changedPayloads':changed,
        'removedSignatureEntries':removed,'allOtherPayloadsByteIdentical':True,
        'manifestChanged':False,'packageNameChanged':False,'signed':False,'installed':False},indent=2)+'\n')
else:
    raise SystemExit('Unknown utility mode')
