#!/usr/bin/env python3
from pathlib import Path
import argparse,json,hashlib,stat,struct,zipfile,re
import build_capture as b

def audit(root):
 root=Path(root);app=root/'stage/Payload/Runner.app';receipt=json.loads((root/'pre-archive-receipt.json').read_bytes());inventory=json.loads((root/'original-inventory.json').read_bytes())
 if receipt['status']!='PRE_ARCHIVE_REVIEW_REQUIRED' or receipt['bundle']!=b.BUNDLE:raise ValueError('Wrong review state/bundle')
 info_data=(app/'Info.plist').read_bytes();info=b.plistlib.loads(info_data)
 if info.get('CFBundleIdentifier')!=b.BUNDLE or info.get('TIOManuscriptLabRunID')!=b.RUN or info.get('UIFileSharingEnabled') is not True or info.get('LSSupportsOpeningDocumentsInPlace') is not True:raise ValueError('Wrong exact Lab Info/sharing policy')
 if info.get('CFBundleShortVersionString')!='1.0.5' or info.get('CFBundleVersion')!='201':raise ValueError('Wrong host version')
 if any(k in info for k in ['CFBundleURLTypes','UIBackgroundModes','NSUserActivityTypes']):raise ValueError('Original association leaked')
 for k,v in b.runtime_status().items():
  if receipt[k] is not v:raise ValueError('False runtime acceptance field: '+k)
 if b.digest(info_data)!=receipt['policy']['info_sha256']:raise ValueError('Actual Info SHA differs from compiled policy')
 original=b.lab.trusted_original_manifest(b.ORIGINAL_IPA)
 if original!=inventory:raise ValueError('Trusted original inventory mismatch')
 added={'Frameworks/TurboIOPrivateAddon.dylib','Frameworks/TurboIONewsHub.dylib','Frameworks/TurboIOStageBObserver.dylib'}
 removed={n for n in inventory['files'] if '_CodeSignature' in Path(n).parts}
 changed={'Runner','Info.plist','Frameworks/Flutter.framework/Flutter'}
 actual={p.relative_to(app).as_posix() for p in app.rglob('*') if p.is_file()}
 if actual!=set(inventory['files'])-removed|added:raise ValueError('Exact member difference failed')
 retained=0
 for name,record in inventory['files'].items():
  if name in removed:continue
  p=app/name
  if p.is_symlink() or stat.S_IMODE(p.stat().st_mode)!=record['mode']:raise ValueError('Member mode/symlink differs: '+name)
  if name not in changed:
   if b.file_sha(p)!=record['sha256']:raise ValueError('Retained member changed: '+name)
   retained+=1
 with zipfile.ZipFile(b.ORIGINAL_IPA) as z:
  before=z.read('Payload/Runner.app/Runner');flutter_before=z.read('Payload/Runner.app/Frameworks/Flutter.framework/Flutter');old_info=b.plistlib.loads(z.read('Payload/Runner.app/Info.plist'))
 if (app/'Runner').read_bytes()!=b.patch_runner(before):raise ValueError('Runner differs from exact reviewed delta')
 # Check Flutter command preservation and *every* non-header byte separately.
 old=b.lab.inspect(flutter_before);new=b.lab.inspect((app/'Frameworks/Flutter.framework/Flutter').read_bytes());flutter=(app/'Frameworks/Flutter.framework/Flutter').read_bytes()
 if new['deps']!=old['deps']+[b.FLUTTER_OBSERVER] or new['commands'][:-1]!=[x for x in old['commands'] if x[0]!=0x1d] or new['uuid']!=old['uuid'] or new['signatures']:raise ValueError('Missing strong Flutter dependency or old ordinals changed')
 stop=max(old['end'],new['end'])
 if flutter[:16]!=flutter_before[:16] or flutter[24:32]!=flutter_before[24:32] or flutter[stop:]!=flutter_before[stop:] or len(flutter)!=len(flutter_before):raise ValueError('Flutter non-header code/binding/signature blob changed')
 for key in ('CFBundleURLTypes','NSUserActivityTypes','UIBackgroundModes','UTExportedTypeDeclarations','UTImportedTypeDeclarations'):old_info.pop(key,None)
 old_info.update(CFBundleIdentifier=b.BUNDLE,CFBundleDisplayName='文稿公开Schema取证-'+b.RUN,CFBundleName='ManuscriptLabCapture',UIFileSharingEnabled=True,LSSupportsOpeningDocumentsInPlace=True,TIOManuscriptLabRunID=b.RUN,TIOManuscriptLabStage='SCHEMA_CAPTURE_ATTEMPT',TIOCaptureBuildID=root.name)
 if info!=old_info:raise ValueError('Unexpected Info.plist field delta')
 native=app/'Frameworks/TurboIOPrivateAddon.dylib';hub=app/'Frameworks/TurboIONewsHub.dylib';adapter=app/'Frameworks/TurboIOStageBObserver.dylib'
 for module in receipt['fresh_modules']:
  p=app/'Frameworks'/Path(module['path']).name
  b.check_module(p,module['sha256'],module['uuid'],module['install_name'])
 observer=receipt['adapter'];b.check_module(adapter,observer['sha256'],observer['uuid'],observer['install_name']);b.check_adapter_dependencies(b.lab.inspect(adapter.read_bytes())['deps'])
 native_bytes=native.read_bytes();bundle=b.BUNDLE
 if re.search(rb'io\.turboio\.official-[A-Za-z0-9._-]+',native_bytes) or 'io.turboio.official-'.encode('utf-16le') in native_bytes:raise ValueError('Old persistent domain leaked')
 for suffix in ['.private-addon','.profile','.knowledge','.navigation']:
  if (bundle+suffix).encode() not in native_bytes:raise ValueError('New namespace absent')
 if b'TurboIOKnowledgeConnection.json' in native_bytes:raise ValueError('External bootstrap import path compiled')
 if receipt['fresh_modules'][0]['uuid'].encode() not in hub.read_bytes() or bundle.encode() not in hub.read_bytes():raise ValueError('Hub not bound to fresh Native identity')
 for source in receipt['adapter_source_files']:
  if b.file_sha(root/'source'/source['path'])!=source['sha256']:raise ValueError('Reviewed adapter source changed')
 prov=receipt['policy']
 for name,field in [('LabPolicy.inc','policy_sha256'),('LabConfig.inc','config_sha256')]:
  if b.file_sha(root/'generated-policy'/name)!=prov[field]:raise ValueError('Compiled generated policy changed')
 # Recompute byte-for-byte fixed image policies from actual staged immutable code.
 expected='#define TIO_FIXED_POLICY_PRESENT 1\nstatic const TIOFixedImagePolicy MainPolicy='+b.policy((app/'Runner').read_bytes(),'','@rpath/Flutter.framework/Flutter')+';\nstatic const TIOFixedImagePolicy FlutterPolicy='+b.policy(flutter,'Frameworks/Flutter.framework/Flutter','',True)+';\n'
 if (root/'generated-policy/LabPolicy.inc').read_text()!=expected:raise ValueError('Generated image policy not actual staged images')
 config=(root/'generated-policy/LabConfig.inc').read_text()
 for token in ['Frameworks/TurboIOPrivateAddon.dylib','Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs','Frameworks/TurboIOStageBObserver.dylib',b.FLUTTER_OBSERVER,b.MAIN_NATIVE]:
  if json.dumps(token) not in config:raise ValueError('Config paths/dependencies not real staging')
 if 'OwnFlutter' in config or 'OwnIsar' in config or 'OwnNative' in config:raise ValueError('Fixture policy contamination')
 isar=(app/'Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs').read_bytes()
 arr=lambda data:'{'+','.join(map(str,data))+'}'
 expected_config='static const TIOFixedImagePolicy NativePolicy='+b.policy(native_bytes,'Frameworks/TurboIOPrivateAddon.dylib')+';\nstatic const TIOFixedImagePolicy IsarPolicy='+b.policy(isar,'Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs')+';\nstatic const unsigned char LabInfoSHA[32]='+arr(hashlib.sha256(info_data).digest())+';\nstatic const unsigned long LabInfoSize='+str(len(info_data))+';\nstatic const unsigned long long IsarEntryVA='+str(b.defined(isar,'_isar_open_instance'))+';\nstatic const char ObserverRelative[]="Frameworks/TurboIOStageBObserver.dylib";\nstatic const char MainNativeLoad[]='+json.dumps(b.MAIN_NATIVE)+';\nstatic const char FlutterObserverLoad[]='+json.dumps(b.FLUTTER_OBSERVER)+';\n'
 if config!=expected_config:raise ValueError('Generated config not byte-exact actual identities/Info')
 # Constants must actually be present in the compiled adapter, not merely beside it.
 adapter_bytes=adapter.read_bytes()
 for value in [hashlib.sha256(info_data).digest(),bytes.fromhex(b.lab.inspect(native_bytes)['uuid']),hashlib.sha256(flutter[b.section(flutter,'__text')[4]:b.section(flutter,'__text')[4]+b.section(flutter,'__text')[3]]).digest(),b.FLUTTER_OBSERVER.encode()+b'\0',b.MAIN_NATIVE.encode()+b'\0']:
  if value not in adapter_bytes:raise ValueError('Actual adapter does not embed generated binding constants')

 if (app/'Documents').exists() or list(app.rglob('captured_schema_projection.json')):raise ValueError('Capture output fabricated in build')
 result=dict(status='STATIC_PRE_ARCHIVE_AUDIT_PASS_NOT_RUNTIME_PROOF',retained_unchanged_files=retained,removed_signature_resources=len(removed),changed_original_files=sorted(changed),added_files=sorted(added),runner_original_ordinals=156,runner_final_dependencies=158,flutter_original_ordinals=36,flutter_final_dependencies=37,original_uuid_preserved=True,all_non_header_bytes_preserved=True,source_policy_actual_binding_verified=True,info_file_sharing_lab_only=True,signed=False,no_capture_output=True,**b.runtime_status())
 return result

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--root',required=True,type=Path);a=p.parse_args();result=audit(a.root);(a.root/'static-stage-audit.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
