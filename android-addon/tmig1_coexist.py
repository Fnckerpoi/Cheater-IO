"""Fixed-input TMIG1 coexist identity patch; never edits executable payloads."""
import argparse
import collections
import hashlib
import json
import pathlib
import re
import sys
import struct
import subprocess
import zipfile
import background_manifest as ax

OLD = 'com.rayneo.venus.pub'
NEW = 'com.turboio.tmig1.flash'
LABEL = 'TurboIO TMIG1刷写'
PIN = 'cb21447117fe578d211ef78fbaa5711ba09ff19c897195635a29b91dda48e92c'
PUBLIC_PIN = 'd39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0'
MANIFEST_PIN = '29fe34ec0fdfc777e78a46af6668ae670e0730e7f8d30b71a6afa7731b21b81e'
CERT_PIN = '227c274835a9c0f90f5e486b5def5a93a83f5fbdcb4f978c4a9d12ebcc297f58'
AUTHORITIES = ['flutter.image_provider', 'fluwxprovider', 'flutter.share_provider', 'utilcode.fileprovider', 'SentryPerformanceProvider', 'androidx-startup', 'huawei.push.provider', 'aaidinitprovider', 'AGCInitializeProvider']
PERMISSIONS = ['DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION', 'push.permission.MESSAGE', 'permission.MIPUSH_RECEIVE', 'AGOO', 'ACCS', 'permission.PROCESS_PUSH_MSG', 'permission.PUSH_PROVIDER', 'permission.PUSH_WRITE_PROVIDER']
COMPONENTS = {'application', 'activity', 'activity-alias', 'service', 'receiver', 'provider', 'instrumentation'}

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def parse(data):
    chunks = ax._chunks(data)
    pools = [i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0] == 1]
    if len(pools) != 1:
        raise ValueError('Expected one string pool')
    strings = ax._decode_pool(chunks[pools[0]])[0]
    nodes = []
    for ci,c in enumerate(chunks):
        ni = ax._node_name(c)
        if ni is None:
            continue
        attrs,start,stride = ax._attributes(c)
        records = []
        for ai,a in enumerate(attrs):
            ns,name,raw = struct.unpack_from('<III',a)
            records.append({'name':strings[name], 'namespace':None if ns == ax.U else strings[ns], 'type':a[15], 'value':ax._attribute_string(a,strings) if a[15] == 3 else struct.unpack_from('<I',a,16)[0], 'offset':start+ai*stride})
        if len({(a['namespace'],a['name']) for a in records}) != len(records):
            raise ValueError('Duplicate XML attributes')
        nodes.append((ci,strings[ni],records))
    return chunks,pools[0],strings,nodes

def plan(data):
    chunks,pi,strings,nodes = parse(data)
    changes = []
    found_auth = []
    found_defs = []
    permission_refs = collections.Counter()
    counts = collections.Counter()
    components = []
    for ci,tag,attrs in nodes:
        for a in attrs:
            name,value = a['name'],a['value']
            new = None
            if tag in COMPONENTS and name in {'name','targetActivity','appComponentFactory','backupAgent','manageSpaceActivity','parentActivityName'} and a['type'] == 3:
                if '.' not in value or value.startswith('.'):
                    raise ValueError('Component is not fully qualified: '+value)
                components.append({'tag':tag,'attribute':name,'class':value})
            if tag == 'manifest' and name == 'package':
                if value != OLD: raise ValueError('Unexpected package')
                new = NEW; counts['package'] += 1
            elif tag == 'application' and name == 'label':
                new = LABEL; counts['label'] += 1
            elif tag == 'provider' and name == 'authorities':
                if value not in [OLD+'.'+s for s in AUTHORITIES]: raise ValueError('Unexpected provider authority')
                found_auth.append(value); new = NEW+value[len(OLD):]
            elif name in {'permission','readPermission','writePermission'} or (tag in {'permission','uses-permission','uses-permission-sdk-23'} and name == 'name'):
                if a['type'] == 3 and value.startswith(OLD+'.'):
                    if value not in [OLD+'.'+s for s in PERMISSIONS]: raise ValueError('Unexpected custom permission')
                    new = NEW+value[len(OLD):]
                    permission_refs[value[len(OLD)+1:]] += 1
                    if tag == 'permission': found_defs.append(value)
            elif tag == 'data' and name == 'host' and value == OLD:
                values = {x['name']:x['value'] for x in attrs}
                if values.get('scheme') != 'agoo': raise ValueError('Unexpected package host')
                new = NEW; counts['agooHost'] += 1
            elif tag == 'data' and name == 'scheme' and value in {'rayneo.venus.app','signinwithapple'}:
                new = {'rayneo.venus.app':'turboio.tmig1.flash','signinwithapple':'turboio.tmig1.flash.apple'}[value]
                counts[value] += 1
            elif name == 'taskAffinity' and value == OLD:
                new = NEW; counts['taskAffinity'] += 1
            elif tag == 'action' and name == 'name' and value == OLD+'.messenger':
                new = NEW+'.messenger'; counts['messenger'] += 1
            elif tag == 'category' and name == 'name' and value == OLD:
                new = NEW; counts['category'] += 1
            if new is not None:
                changes.append({'chunk':ci,'tag':tag,'attribute':name,'namespace':a['namespace'],'beforeType':a['type'],'before':value,'after':new,'offset':a['offset']})
    if sorted(found_auth) != sorted(OLD+'.'+s for s in AUTHORITIES) or sorted(found_defs) != sorted(OLD+'.'+s for s in PERMISSIONS):
        raise ValueError('Authority/permission inventory mismatch')
    if permission_refs != dict(zip(PERMISSIONS,[2,2,2,2,2,4,3,2])):
        raise ValueError('Custom permission reference inventory mismatch')
    if counts != {'package':1,'label':1,'agooHost':1,'rayneo.venus.app':1,'signinwithapple':1,'taskAffinity':2,'messenger':1,'category':1}:
        raise ValueError('Identity occurrence mismatch: '+repr(counts))
    return changes,components

def patch(data):
    changes,components = plan(data)
    chunks,pi,strings,nodes = parse(data)
    chunks[pi],_,indices = ax._append_pool_strings(chunks[pi],strings,[c['after'] for c in changes])
    for c in changes:
        chunk = bytearray(chunks[c['chunk']]); off = c['offset']; index = indices[c['after']]
        struct.pack_into('<I',chunk,off+8,index)
        struct.pack_into('<HBBI',chunk,off+12,8,0,3,index)
        chunks[c['chunk']] = bytes(chunk)
    body = b''.join(chunks)
    result = struct.pack('<HHI',3,8,len(body)+8)+body
    parse(result)
    return result,[{k:v for k,v in c.items() if k != 'offset'} for c in changes],components

def validate_zip(z):
    names = z.namelist()
    if len(names) != len(set(names)) or z.testzip() is not None:
        raise ValueError('Duplicate members or corrupt ZIP')
    return names

def manifest_audit(source,output,expected_sha=None):
    if sha(source) != (PIN if expected_sha is None else expected_sha): raise ValueError('Fixed unsigned input SHA mismatch')
    with zipfile.ZipFile(source) as before,zipfile.ZipFile(output) as after:
        names = validate_zip(before)
        if validate_zip(after) != names: raise ValueError('Changed ZIP inventory or order')
        expected,changes,components = patch(before.read('AndroidManifest.xml'))
        if after.read('AndroidManifest.xml') != expected: raise ValueError('Manifest differs from precise attribute allowlist')
        changed = [n for n in names if before.read(n) != after.read(n)]
        if changed != ['AndroidManifest.xml']: raise ValueError('Nonmanifest payload changed: '+repr(changed))
        dex = hashlib.sha256(after.read('classes4.dex')).hexdigest()
    return {'sourceSha256':sha(source),'outputSha256':sha(output),'changedPayloads':changed,'allOtherPayloadsByteIdentical':True,'classes4Sha256':dex,'attributeChanges':changes,'preservedComponents':components,'package':NEW,'installed':False,'deviceValidated':False}

def build_unsigned(source,installed,output,report,profile=None):
    expected_sha = verify_profile(source,profile) if profile else PIN
    if sha(source) != expected_sha: raise ValueError('Fixed unsigned input SHA mismatch')
    if output.exists(): raise ValueError('Refuse overwrite')
    with zipfile.ZipFile(source) as z,zipfile.ZipFile(installed) as host:
        validate_zip(z); validate_zip(host)
        manifest = z.read('AndroidManifest.xml')
        if manifest != host.read('AndroidManifest.xml'): raise ValueError('Installed-host manifest differs from reviewed input')
        patched,_,_ = patch(manifest)
        with zipfile.ZipFile(output,'x',allowZip64=True) as out:
            for info in z.infolist():
                out.writestr(info,patched if info.filename == 'AndroidManifest.xml' else z.read(info))
    result = manifest_audit(source,output,expected_sha)
    result['installedHostSha256'] = sha(installed)
    report.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')

def run(command,log):
    with log.open('a',encoding='utf-8') as stream:
        stream.write('COMMAND '+json.dumps([str(x) for x in command])+'\n'); stream.flush()
        result = subprocess.run([str(x) for x in command],stdout=stream,stderr=subprocess.STDOUT)
        stream.write('EXIT '+str(result.returncode)+'\n')
    if result.returncode: raise RuntimeError('Command failed; see '+str(log))

def signed_audit(unsigned,signed):
    signature = lambda n: re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',n,re.I)
    with zipfile.ZipFile(unsigned) as b,zipfile.ZipFile(signed) as a:
        bn = validate_zip(b); an = validate_zip(a)
        if [n for n in bn if not signature(n)] != [n for n in an if not signature(n)]: raise ValueError('Signed payload inventory/order changed')
        if any(b.read(n) != a.read(n) for n in bn if not signature(n)): raise ValueError('Signed payload bytes changed')
    return {'unsignedSha256':sha(unsigned),'signedSha256':sha(signed),'signedPayloadAuditPassed':True,'package':NEW,'installed':False,'deviceValidated':False}

def baseline_evidence(source,public,build_report,audit_report,work):
    if sha(public) != PUBLIC_PIN:
        raise ValueError('PUBLIC root SHA mismatch')
    reports = [json.loads(p.read_text(encoding='utf-8')) for p in (build_report,audit_report)]
    actual = sha(source)
    for report in reports:
        if any(report.get(k) != v for k,v in {'mode':'public-dex-update','sourceSha256':PUBLIC_PIN,'outputSha256':actual,'changedPayloads':['classes4.dex'],'allOtherPayloadsByteIdentical':True,'manifestChanged':False,'packageNameChanged':False,'signed':False,'installed':False}.items()):
            raise ValueError('PUBLIC report binding or audit claims mismatch')
    fresh = work/'public-audit-revalidated.json'
    run([sys.executable,pathlib.Path(__file__).with_name('tmig1_windows.py'),'public-audit',public,source,fresh],work/'baseline-commands.log')
    checked = json.loads(fresh.read_text(encoding='utf-8'))
    if checked != reports[1]:
        raise ValueError('Supplied independent audit differs from rerun')
    with zipfile.ZipFile(source) as z:
        validate_zip(z)
        manifest = z.read('AndroidManifest.xml')
        if hashlib.sha256(manifest).hexdigest() != MANIFEST_PIN:
            raise ValueError('Fixed original manifest SHA mismatch')
        if len(plan(manifest)[0]) != 37:
            raise ValueError('Expected precisely 37 manifest attribute changes')
        dex = hashlib.sha256(z.read('classes4.dex')).hexdigest()
    return {'schema':'tmig1-coexist-baseline-v1','sourceSha256':actual,'publicSha256':PUBLIC_PIN,'manifestSha256':MANIFEST_PIN,'classes4Sha256':dex,'buildReportSha256':sha(build_report),'auditReportSha256':sha(audit_report),'public':str(public.resolve()),'buildReport':str(build_report.resolve()),'auditReport':str(audit_report.resolve())}

def create_profile(source,public,build_report,audit_report,profile):
    if profile.exists():
        raise ValueError('Refuse profile overwrite')
    profile.parent.mkdir(parents=True,exist_ok=True)
    evidence = baseline_evidence(source,public,build_report,audit_report,profile.parent)
    with profile.open('x',encoding='utf-8') as stream:
        json.dump(evidence,stream,indent=2); stream.write('\n')
    return evidence

def verify_profile(source,profile):
    expected = json.loads(profile.read_text(encoding='utf-8'))
    if expected.get('schema') != 'tmig1-coexist-baseline-v1':
        raise ValueError('Unknown baseline schema')
    evidence = baseline_evidence(source,pathlib.Path(expected['public']),pathlib.Path(expected['buildReport']),pathlib.Path(expected['auditReport']),profile.parent)
    if evidence != expected:
        raise ValueError('Baseline profile binding mismatch')
    return evidence['sourceSha256']

def existing_signer(signing):
    key=signing/'tmig1-research.p12'; password=signing/'password.txt'
    if not key.is_file() or not password.is_file():
        raise ValueError('Existing research key and password required; generation prohibited')
    return key,password

def verify_signer(java_home,key,password,work):
    cert=work/'research-signer.der'
    if cert.exists():
        raise ValueError('Refuse certificate overwrite')
    run([java_home/'bin/keytool.exe','-exportcert','-keystore',key,'-alias','tmig1-research','-storepass:file',password,'-file',cert],work/'commands.log')
    if sha(cert) != CERT_PIN:
        raise ValueError('Research signer certificate SHA mismatch')

def signing_command(java,signer,key,password,signed,aligned):
    return [java,'-jar',signer,'sign','--ks',key,'--ks-key-alias','tmig1-research','--ks-pass','file:'+str(password),'--out',signed,aligned]

def profile_main():
    p=argparse.ArgumentParser(description='Bind a new source only after fixed-root PUBLIC audit')
    for name in ('source','public','build-report','audit-report','profile'):
        p.add_argument('--'+name,type=pathlib.Path,required=True)
    a=p.parse_args(sys.argv[2:])
    create_profile(a.source,a.public,a.build_report,a.audit_report,a.profile)
    print('Verified baseline profile:',a.profile)

def main():
    p=argparse.ArgumentParser(); p.add_argument('--source',type=pathlib.Path,required=True); p.add_argument('--installed-host',type=pathlib.Path,required=True); p.add_argument('--output-directory',type=pathlib.Path,required=True); p.add_argument('--java-home',type=pathlib.Path,required=True); p.add_argument('--build-tools',type=pathlib.Path,required=True); p.add_argument('--signing-directory',type=pathlib.Path,required=True); p.add_argument('--baseline-profile',type=pathlib.Path)
    a=p.parse_args(); work=a.output_directory.resolve(); work.mkdir(parents=True,exist_ok=False)
    unsigned=work/'TurboIO-TMIG1-flash-coexist-unsigned.apk'; aligned=work/'TurboIO-TMIG1-flash-coexist-aligned.apk'; signed=work/'TurboIO-TMIG1-flash-coexist-signed.apk'; log=work/'commands.log'
    key,password=existing_signer(a.signing_directory.resolve())
    verify_signer(a.java_home,key,password,work)
    build_unsigned(a.source,a.installed_host,unsigned,work/'manifest-payload-audit.json',a.baseline_profile)
    java=a.java_home/'bin/java.exe'; align=a.build_tools/'zipalign.exe'; signer=a.build_tools/'lib/apksigner.jar'
    run([align,'-P','16','-f','4',unsigned,aligned],log)
    run(signing_command(java,signer,key,password,signed,aligned),log)
    run([align,'-c','-P','16','4',signed],log)
    run([java,'-jar',signer,'verify','--verbose','--print-certs',signed],work/'certificate-verification.log')
    cert_log=(work/'certificate-verification.log').read_text(encoding='utf-8')
    if 'Signer #1 certificate SHA-256 digest: '+CERT_PIN not in cert_log or 'Number of signers: 1' not in cert_log:
        raise ValueError('Signed APK certificate identity mismatch')
    audit=signed_audit(unsigned,signed); (work/'signed-payload-audit.json').write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(audit,indent=2)); print('Signed APK:',signed)

if __name__ == '__main__':
    if len(sys.argv)>1 and sys.argv[1]=='create-profile':
        profile_main()
    else:
        main()
