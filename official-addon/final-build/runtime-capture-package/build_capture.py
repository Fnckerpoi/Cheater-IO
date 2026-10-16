#!/usr/bin/env python3
"""Pinned, no-sign capture assembly. Stops before IPA until independent review."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import plistlib
import re
import shutil
import stat
import struct
import subprocess
import uuid
import zipfile

HERE=Path(__file__).resolve().parent
ADDON=HERE.parents[1]
RUN='capture-20261015-a'
BUNDLE='com.duriea.turboio.manuscriptlab105.'+RUN
NATIVE_SHA='cf4dfcb29fec29e583f22555ef47593ee587edb1d986d92eeaeed4c939a6ab42'
NATIVE_UUID='AEFBC764-A565-362B-A8BE-95374D7ADF2C'
ORIGINAL_SHA='39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a'
RUNNER_SHA='b27f3581bea7a945560bce501823a9697fc878ab54286a330fa199bb85b828aa'
FLUTTER_SHA='488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0'
ISAR_SHA='728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88'
APP_SHA='f5691eb07c1eaa4270de24ad1a0b09829cb35c2d22a5fab16b818aa519c8c23a'
RECIPE_SHA='033e5f9d294436dbde1ce357f936d1bcdccef6b87ecaf82a71f282958c718a19'
MODULE_RECEIPT_SHA='2b7e34bd8e82e66957a38d71c8ef56c694b66aa6e67239152881d4f69988914d'
SOURCE_MANIFEST_SHA='bc97eea73cf891d39eb44310ad07a40b0c264ed2290d16e7ffb76d105e72111e'
ADAPTER_MANIFEST_SHA='5b30cfc7d8a15e4de9563b922cec39829b218c7d12655c47a37a8953a655f9fb'
ADAPTER_C_SHA='d6566972040454d184cd2cf68ab4283538851d90431060d07cd62c111cd91941'
FLUTTER_PATCH_MANIFEST_SHA='48497dcb9aa0152b4dc1a4f7626c028b43e01199c6082af9975bb3367754d30b'
MAIN_NATIVE='@executable_path/Frameworks/TurboIOPrivateAddon.dylib'
MAIN_HUB='@executable_path/Frameworks/TurboIONewsHub.dylib'
FLUTTER_OBSERVER='@loader_path/../TurboIOStageBObserver.dylib'
REVIEWED_ROOT=ADDON/'final-build/build/final-task73-20261015-0856'
ADAPTER_ROOT=ADDON/'final-build/production-capture-adapter/adapter-tests/frozen-r114'
ORIGINAL_IPA=ADDON/'build/manuscript-lab-input-20261015-original105/RayNeo_AI_1.0.5_201_decrypted_original.ipa'
LAB_PATH=ADDON/'manuscript-lab/lab.py'


def digest(data):return hashlib.sha256(data).hexdigest()
def file_sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def import_file(name,path):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
if file_sha(LAB_PATH)!='339f5eca791db53897e70fa22974181a85f37f3630dd633c237d3cb2bacf04e0':raise ValueError('Reviewed sealed StageA helper changed')
parser_path=ADDON/'package-aihot-daily-stage.py'
if file_sha(parser_path)!='790cd20a310830fa3592e8323a2104b321172b15a02c1e1dedca9c3bf02e3a35':raise ValueError('Reviewed pure MachO parser changed')
lab=import_file('capture_sealed_lab',LAB_PATH)


def identity(run):
 if run!=RUN:raise ValueError('This approved build plan requires the new exact capture run; prebuilt lab modules cannot be relabelled')
 return BUNDLE

def runtime_status():
 return dict(device_ready=False,official_save_verified=False,db_write_enabled=False,final_newshub_complete=False,
 capture_runtime_observation_attempt=True,slot_installed=False,schema_event=False,file_created=False,
 app_executed=False,network_services_executed=False,whole_app_network_disabled_verified=False,runtime_isolation_proven=False)

def new_output(path):
 path=lab.no_symlinks(Path(path).absolute())
 if path.exists():raise ValueError('New nonexisting canonical output required')
 return path

def check_module(path,expected_sha,expected_uuid,install):
 if file_sha(path)!=expected_sha:raise ValueError('Reviewed module hash mismatch')
 h=lab.inspect(Path(path).read_bytes())
 if h['filetype']!=6 or h['install']!=install or h['uuid']!=expected_uuid.replace('-','').lower() or h['signatures']:raise ValueError('Reviewed module UUID/install/signature mismatch')
 return h

def check_adapter_dependencies(deps):
 if not deps or any(not p.startswith(('/usr/lib/','/System/Library/')) for p in deps):raise ValueError('Adapter must have system-only dependencies and no circular Flutter/App dependency')
 return True

def trusted_sources(out):
 for name,want in [('build-receipt.json',MODULE_RECEIPT_SHA),('source-manifest.json',SOURCE_MANIFEST_SHA),('recipe-snapshot.py',RECIPE_SHA)]:
  if file_sha(REVIEWED_ROOT/name)!=want:raise ValueError('Task73 reviewed provenance mismatch: '+name)
 manifest=json.loads((REVIEWED_ROOT/'source-manifest.json').read_bytes())
 for item in manifest['source_files']:
  name=item['path']
  if Path(name).name!=name or file_sha(REVIEWED_ROOT/'source-snapshot'/name)!=item['sha256']:raise ValueError('Frozen task73 source mismatch')
 if file_sha(ADAPTER_ROOT/'source-manifest.json')!=ADAPTER_MANIFEST_SHA:raise ValueError('Reviewed R114 source manifest mismatch')
 adapter=json.loads((ADAPTER_ROOT/'source-manifest.json').read_bytes())
 source=out/'source'
 for item in adapter['files']:
  relative=Path(item['path'])
  if relative.is_absolute() or '..' in relative.parts:raise ValueError('Unsafe reviewed source reference')
  path=ADAPTER_ROOT/'source'/relative
  if path.is_symlink() or file_sha(path)!=item['sha256']:raise ValueError('Reviewed R114 source changed: '+str(relative))
  target=source/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
 if file_sha(source/'final-build/production-capture-adapter/Adapter.c')!=ADAPTER_C_SHA:raise ValueError('Exact name gate source not R114')
 # Fixture generated policies are frozen reference evidence, never compilation inputs.
 return manifest,adapter

def image(data):
 h=lab.inspect(data);cpu,filetype=struct.unpack_from('<II',data,4)[0],struct.unpack_from('<I',data,12)[0]
 sections=[];sym=None;ind=None
 for cmd,raw in h['commands']:
  if cmd==2:sym=struct.unpack_from('<4I',raw,8)
  if cmd==0xb:ind=struct.unpack_from('<2I',raw,56)
  if cmd==0x19:
   for i in range(struct.unpack_from('<I',raw,64)[0]):sections.append(struct.unpack_from('<16s16sQQIIIIIIII',raw,72+80*i))
 return dict(header=h,cpu=cpu,filetype=filetype,sections=sections,sym=sym,ind=ind)

def section(data,name):
 found=[s for s in image(data)['sections'] if s[0].split(b'\0')[0]==name.encode()]
 if len(found)!=1:raise ValueError('Expected unique section '+name)
 s=found[0]
 if s[4]+s[3]>len(data):raise ValueError('Section file bounds')
 return s

def defined(data,name):
 im=image(data);sym=im['sym']
 if sym is None:raise ValueError('Symbol table required')
 strings=data[sym[2]:sym[2]+sym[3]];values=[]
 for i in range(sym[1]):
  st,ty,sc,de,value=struct.unpack_from('<IBBHQ',data,sym[0]+16*i)
  if strings[st:].split(b'\0')[0]==name.encode() and ty&14==14 and ty&1:values.append(value)
 if len(values)!=1:raise ValueError('Exact single defined export required: '+name)
 return values[0]

def flutter_slot(data):
 im=image(data);sym,ind=im['sym'],im['ind']
 if sym is None or ind is None:raise ValueError('Flutter symbol mapping absent')
 strings=data[sym[2]:sym[2]+sym[3]];found=[]
 for s in im['sections']:
  if s[8]&255!=7:continue
  for j in range(s[3]//8):
   index=struct.unpack_from('<I',data,ind[0]+4*(s[9]+j))[0]
   if index&0xc0000000:continue
   if index>=sym[1]:raise ValueError('Indirect symbol bound')
   strx=struct.unpack_from('<I',data,sym[0]+16*index)[0]
   if strings[strx:].split(b'\0')[0]==b'_dlsym':found.append((s[2]+8*j,struct.unpack_from('<Q',data,s[4]+8*j)[0]))
 if len(found)!=1 or found[0]!=(0x8341d8,0x6741a4):raise ValueError('Wrong exact Flutter dlsym slot/lazy metadata')
 return found[0]

def policy(data,relative,required='',needs_slot=False):
 im=image(data);text=section(data,'__text');infos=[s for s in im['sections'] if s[0].split(b'\0')[0]==b'__info_plist'];iva=isz=0;ih=bytes(32)
 if infos:
  if len(infos)!=1:raise ValueError('Duplicate embedded Info')
  s=infos[0];iva,isz=s[2:4];ih=hashlib.sha256(data[s[4]:s[4]+s[3]]).digest()
 slot,lazy=flutter_slot(data) if needs_slot else (0,0)
 arr=lambda b:'{'+','.join(map(str,b))+'}'
 fields=[str(im['cpu']),str(im['filetype']),arr(bytes.fromhex(im['header']['uuid'])),str(text[2]),str(text[3]),arr(hashlib.sha256(data[text[4]:text[4]+text[3]]).digest()),str(iva),str(isz),arr(ih),json.dumps(relative),str(slot),str(lazy),json.dumps(required)]
 return '{'+','.join(fields)+'}'

def patch_runner(data):
 h=lab.inspect(data)
 if digest(data)!=RUNNER_SHA or len(h['deps'])!=156 or h['uuid']!=lab.UUID:raise ValueError('Wrong original Runner')
 commands=[raw for cmd,raw in h['commands'] if cmd!=0x1d]
 for path in (MAIN_NATIVE,MAIN_HUB):
  name=path.encode()+b'\0';size=(24+len(name)+7)//8*8
  commands.append(struct.pack('<6I',12,size,24,0,0,0)+name+bytes(size-24-len(name)))
 raw=b''.join(commands);end=32+len(raw)
 if end>h['first'] or any(data[h['end']:max(end,h['end'])]):raise ValueError('Runner header padding insufficient')
 result=bytearray(data);stop=max(end,h['end']);result[32:stop]=raw+bytes(stop-end);struct.pack_into('<II',result,16,len(commands),len(raw));result=bytes(result)
 after=lab.inspect(result)
 if after['deps']!=h['deps']+[MAIN_NATIVE,MAIN_HUB] or after['commands'][:-2]!=[c for c in h['commands'] if c[0]!=0x1d] or result[stop:]!=data[stop:] or result[:16]!=data[:16] or result[24:32]!=data[24:32]:raise ValueError('Runner original graph/body changed')
 return result

def stage_original(original,out):
 inventory=lab.trusted_original_manifest(original);app=out/'stage/Payload/Runner.app';app.mkdir(parents=True,mode=0o755)
 with zipfile.ZipFile(original) as z:
  for name,mode in sorted(inventory['directories'].items(),key=lambda x:len(Path(x[0]).parts)):
   if '_CodeSignature' in Path(name).parts:continue
   p=app/name;p.mkdir(parents=True,exist_ok=True);p.chmod(mode)
  for name,entry in inventory['files'].items():
   if '_CodeSignature' in Path(name).parts:continue
   target=app/name;target.parent.mkdir(parents=True,exist_ok=True)
   with z.open('Payload/Runner.app/'+name) as source,target.open('xb') as sink:shutil.copyfileobj(source,sink,1024*1024)
   target.chmod(entry['mode'])
 return app,inventory

def make_policy(app,out):
 runner=(app/'Runner').read_bytes();flutter=(app/'Frameworks/Flutter.framework/Flutter').read_bytes();native=(app/'Frameworks/TurboIOPrivateAddon.dylib').read_bytes();isar=(app/'Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs').read_bytes();info=(app/'Info.plist').read_bytes()
 info_obj=plistlib.loads(info)
 if info_obj['CFBundleIdentifier']!=BUNDLE or not info_obj.get('UIFileSharingEnabled') or not info_obj.get('LSSupportsOpeningDocumentsInPlace'):raise ValueError('Exact Lab Info/file-sharing policy missing')
 rh=lab.inspect(runner);fh=lab.inspect(flutter)
 if rh['deps'][-2:]!=[MAIN_NATIVE,MAIN_HUB] or len(rh['deps'])!=158 or fh['deps'][-1:]!=[FLUTTER_OBSERVER] or len(fh['deps'])!=37:raise ValueError('Exact strong dependency graph missing')
 entry=defined(isar,'_isar_open_instance')
 if entry!=0x1e530:raise ValueError('Unexpected exact Isar open address')
 folder=out/'generated-policy';folder.mkdir();main=folder/'LabPolicy.inc';config=folder/'LabConfig.inc'
 main.write_text('#define TIO_FIXED_POLICY_PRESENT 1\nstatic const TIOFixedImagePolicy MainPolicy='+policy(runner,'','@rpath/Flutter.framework/Flutter')+';\nstatic const TIOFixedImagePolicy FlutterPolicy='+policy(flutter,'Frameworks/Flutter.framework/Flutter','',True)+';\n')
 arr=lambda b:'{'+','.join(map(str,b))+'}'
 config.write_text('static const TIOFixedImagePolicy NativePolicy='+policy(native,'Frameworks/TurboIOPrivateAddon.dylib')+';\nstatic const TIOFixedImagePolicy IsarPolicy='+policy(isar,'Frameworks/isar_plus_flutter_libs.framework/isar_plus_flutter_libs')+';\nstatic const unsigned char LabInfoSHA[32]='+arr(hashlib.sha256(info).digest())+';\nstatic const unsigned long LabInfoSize='+str(len(info))+';\nstatic const unsigned long long IsarEntryVA='+str(entry)+';\nstatic const char ObserverRelative[]="Frameworks/TurboIOStageBObserver.dylib";\nstatic const char MainNativeLoad[]='+json.dumps(MAIN_NATIVE)+';\nstatic const char FlutterObserverLoad[]='+json.dumps(FLUTTER_OBSERVER)+';\n')
 provenance=dict(original_ipa_sha256=ORIGINAL_SHA,runner_sha256=digest(runner),runner_uuid=rh['uuid'],info_sha256=digest(info),info_bytes=len(info),bundle=BUNDLE,flutter_sha256=digest(flutter),native_sha256=digest(native),native_uuid=lab.inspect(native)['uuid'],isar_sha256=digest(isar),isar_entry_va=entry,slot_va=0x8341d8,raw_helper_va=0x6741a4,helper_branch_supported=False,adapter_source_manifest_sha256=ADAPTER_MANIFEST_SHA,adapter_c_sha256=ADAPTER_C_SHA,policy_sha256=file_sha(main),config_sha256=file_sha(config),**runtime_status())
 (folder/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');return main,config,provenance

def compile_adapter(out,app,main,config):
 source=out/'source';root=source/'final-build/production-capture-adapter';cap=source/'final-build/schema-capture';lower=source/'manuscript-lab';target=app/'Frameworks/TurboIOStageBObserver.dylib'
 names=[root/'Adapter.c',root/'Selector.S',root/'Vendor/View.c',root/'Vendor/SystemResolver.c',root/'FrozenBridge/ResolverBridge.c',root/'FrozenBridge/ResolverBridge.S',cap/'Capture.c',cap/'SchemaPolicy.c',lower/'schema-observer/SchemaView.c',lower/'memory-copy/MemoryCopy.c']
 sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
 args=['xcrun','--sdk','iphoneos','clang','-arch','arm64','-isysroot',sdk,'-miphoneos-version-min=15.0','-std=c11','-fno-common','-dynamiclib','-Wall','-Wextra','-Werror','-I'+str(cap),'-DTIO_BOOTSTRAP_POLICY_HEADER='+json.dumps(str(main)),'-DTIO_ADAPTER_CONFIG_HEADER='+json.dumps(str(config)),*map(str,names),'-Wl,-no_fixup_chains','-Wl,-no_adhoc_codesign','-Wl,-undefined,error','-Wl,-exported_symbol,_TIOStageBCaptureAdapterGetStatus','-install_name','@rpath/TurboIOStageBObserver.dylib','-o',str(target)]
 (out/'adapter-command.json').write_text(json.dumps(args,indent=2)+'\n')
 with (out/'adapter-compile.log').open('w') as log:subprocess.run(args,check=True,stdout=log,stderr=log)
 h=lab.inspect(target.read_bytes())
 if h['install']!='@rpath/TurboIOStageBObserver.dylib' or h['signatures']:raise ValueError('Adapter install/signature wrong')
 check_adapter_dependencies(h['deps']);return dict(path=str(target),sha256=file_sha(target),uuid=h['uuid'],install_name=h['install'],dependencies=h['deps'],signed=False)

def build(out,run):
 identity(run);out=new_output(out);out.mkdir(parents=True,mode=0o700)
 (out/'PARTIAL-NOT-FOR-INSTALL.json').write_text(json.dumps(dict(status='INCOMPLETE_UNREVIEWED_NO_IPA',**runtime_status()),indent=2)+'\n')
 manifest,adapter_manifest=trusted_sources(out)
 recipe=import_file('reviewed_two_module_recipe',REVIEWED_ROOT/'recipe-snapshot.py')
 modules=recipe.build(run,out/'modules',allow_http=True,source=REVIEWED_ROOT/'source-snapshot')
 if not modules.get('native_compiled') or not modules.get('newshub_compiled'):raise ValueError('Fresh two-module compilation blocked')
 app,inventory=stage_original(ORIGINAL_IPA,out)
 runner_original=(app/'Runner').read_bytes();flutter_original=(app/'Frameworks/Flutter.framework/Flutter').read_bytes()
 if digest(runner_original)!=RUNNER_SHA or digest(flutter_original)!=FLUTTER_SHA:raise ValueError('Trusted raw image identity changed')
 (app/'Runner').write_bytes(patch_runner(runner_original))
 patch_root=ADDON/'final-build/loader-order-prototype/build/frozen-task107'
 if file_sha(patch_root/'source-manifest.json')!=FLUTTER_PATCH_MANIFEST_SHA:raise ValueError('Reviewed Flutter transform manifest changed')
 patch_info=json.loads((patch_root/'source-manifest.json').read_bytes());entry=next(x for x in patch_info['files'] if x['path']=='patch_flutter.py');patch_path=patch_root/'source-snapshot/patch_flutter.py'
 if file_sha(patch_path)!=entry['sha256']:raise ValueError('Flutter transform source mismatch')
 patch=import_file('reviewed_flutter_patch',patch_path);patched=patch.patch(flutter_original,bootstrap_deps=('/usr/lib/libSystem.B.dylib',));(app/'Frameworks/Flutter.framework/Flutter').write_bytes(patched)
 for name in ['TurboIOPrivateAddon.dylib','TurboIONewsHub.dylib']:
  source=out/'modules'/name;dest=app/'Frameworks'/name;dest.write_bytes(source.read_bytes());dest.chmod(0o755)
 info=plistlib.loads((app/'Info.plist').read_bytes())
 for key in ('CFBundleURLTypes','NSUserActivityTypes','UIBackgroundModes','UTExportedTypeDeclarations','UTImportedTypeDeclarations'):info.pop(key,None)
 info.update(CFBundleIdentifier=BUNDLE,CFBundleDisplayName='文稿公开Schema取证-'+run,CFBundleName='ManuscriptLabCapture',UIFileSharingEnabled=True,LSSupportsOpeningDocumentsInPlace=True,TIOManuscriptLabRunID=run,TIOManuscriptLabStage='SCHEMA_CAPTURE_ATTEMPT',TIOCaptureBuildID=out.name)
 (app/'Info.plist').write_bytes(plistlib.dumps(info))
 main,config,provenance=make_policy(app,out);observer=compile_adapter(out,app,main,config)
 # Only policy-independent code identities are embedded: no adapter self-hash cycle.
 source_list=[dict(path=x['path'],sha256=x['sha256']) for x in adapter_manifest['files']]
 report=dict(status='PRE_ARCHIVE_REVIEW_REQUIRED',output=str(out),run=run,bundle=BUNDLE,original_ipa_sha256=ORIGINAL_SHA,adapter=observer,fresh_modules=modules['modules'],adapter_source_files=source_list,adapter_source_manifest_sha256=ADAPTER_MANIFEST_SHA,policy=provenance,policy_embedded=True,policy_has_no_adapter_self_hash=True,source_snapshot_hashes_verified=True,signed=False,ipa_created=False,**runtime_status())
 (out/'pre-archive-receipt.json').write_text(json.dumps(report,indent=2)+'\n')
 (out/'original-inventory.json').write_text(json.dumps(inventory,indent=2)+'\n')
 return report


def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--run',default=RUN);a=p.parse_args()
 report=build(a.out,a.run);print(json.dumps({k:v for k,v in report.items() if k not in ('adapter_source_files','policy')},indent=2))
if __name__=='__main__':
 try:main()
 except (ValueError,OSError,KeyError,struct.error,subprocess.CalledProcessError) as e:raise SystemExit('CAPTURE_BUILD_BLOCKED: '+str(e))
