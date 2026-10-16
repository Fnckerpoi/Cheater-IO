#!/usr/bin/env python3
from pathlib import Path
import argparse, hashlib, json, subprocess, sys, difflib
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parent; addon=root.parent; out=args.out
support=root.parent/'support-manifest.json'
if support.exists():
 for entry in json.loads(support.read_text()):
  path=addon/entry['path']
  assert path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(addon.resolve())
  data=path.read_bytes();assert len(data)==entry['size'] and hashlib.sha256(data).hexdigest()==entry['sha256'],entry['path']
 print('FROZEN_SUPPORT_MANIFEST_VERIFIED',len(json.loads(support.read_text())))
if not out.is_absolute() or out.exists():raise SystemExit('New absolute output required')
if any(x.is_symlink() for x in [out,*out.parents]):raise SystemExit('Symlink output refused')
out.mkdir(parents=True,mode=0o700)
lab='com.duriea.turboio.manuscriptlab106.public-run-a'
other='com.duriea.turboio.manuscriptlab106.public-run-b'
def flags(value):return ['-DTIO_STORAGE_LAB_BUNDLE_ID="'+value+'"','-DTIO_TARGET_BUNDLE_ID="'+value+'"']
base=['xcrun','clang','-fobjc-arc','-fmodules','-Wall','-Wextra','-Werror','-framework','Foundation','-framework','Security','-I',str(addon)]
results={}
for label,config in [('default',[]),('lab-a',flags(lab)),('lab-b',flags(other)+['-DTIO_TEST_LAB_SECOND=1'])]:
 exe=out/label;subprocess.run(base+config+[str(root/'StorageIdentityTests.m'),'-o',str(exe)],check=True)
 run=subprocess.run([str(exe)],check=True,capture_output=True,text=True);results[label]=json.loads(run.stdout.splitlines()[0]);(out/(label+'.log')).write_text(run.stdout);print(label,run.stdout.strip())
assert all(results['lab-a'][k]!=results['lab-b'][k] and results['default'][k]!=results['lab-a'][k] for k in results['default'])
invalid=[flags('com.rayneo.venus.pub'),flags('com.duriea.turboio.research'),flags('com.duriea.turboio.manuscriptlab106.*'),flags('com.duriea.turboio.manuscriptlab106.abc'),flags('com.duriea.turboio.manuscriptlab106.-public'),flags('com.duriea.turboio.manuscriptlab106.public_run'),flags('com.duriea.turboio.manuscriptlab106.Public-run'),flags('com.duriea.turboio.manuscriptlab106.official'),flags('com.duriea.turboio.manuscriptlab106.research'),flags('com.duriea.turboio.manuscriptlab106.'+'a'*41),flags(''),flags(lab+'";bad_literal'),flags(lab+'\\0suffix'),['-DTIO_STORAGE_LAB_BUNDLE_ID="'+lab+'"'],['-DTIO_TARGET_BUNDLE_ID="'+lab+'"'],['-DTIO_STORAGE_LAB_BUNDLE_ID="'+lab+'"','-DTIO_TARGET_BUNDLE_ID="'+other+'"']]
for i,config in enumerate(invalid):
 r=subprocess.run(base+config+[str(root/'StorageIdentityTests.m'),'-o',str(out/('invalid-'+str(i)))],capture_output=True,text=True)
 (out/('invalid-'+str(i)+'.log')).write_text(r.stderr)
 assert r.returncode!=0,config
print('INVALID_STORAGE_COMPILE_REJECT_PASS',len(invalid))
# Execute actual one-line query definitions, not preferences or Security service calls.
queries=[]
for name,renamed in [('Addon.m','AddonQuery'),('KnowledgeClient.m','KnowledgeQuery'),('NavigationUI.m','NavigationQuery')]:
 line=next(x for x in (addon/name).read_text().splitlines() if x.startswith('static NSDictionary *KeyQuery('))
 line=line.replace('KeyQuery(',renamed+'(').replace('kSecAttrService:Domain(),','kSecAttrService:TIOStorageAddonDomain(),');queries.append(line)
querysource='#import <Foundation/Foundation.h>\n#import <Security/Security.h>\n#import "TIOStorageIdentity.h"\n#include <assert.h>\n'+'\n'.join(queries)+'\nint main(void){@autoreleasepool {assert([AddonQuery(@"public-host")[(__bridge id)kSecAttrService] isEqual:TIOStorageAddonDomain()]);assert([KnowledgeQuery()[(__bridge id)kSecAttrService] isEqual:TIOStorageKnowledgeDomain()]);assert([NavigationQuery()[(__bridge id)kSecAttrService] isEqual:TIOStorageNavigationDomain()]);puts("ACTUAL_QUERY_DEFINITIONS_PASS_NO_KEYCHAIN_CALL");}return 0;}\n'
(out/'ActualQueries.m').write_text(querysource)
for label,config in [('default',[]),('lab',flags(lab))]:
 exe=out/('query-'+label);subprocess.run(base+config+[str(out/'ActualQueries.m'),'-o',str(exe)],check=True);subprocess.run([str(exe)],check=True)
source=(addon/'KnowledgeClient.m').read_text();body=source[source.index('void TIOImportKnowledgeConnection(void){'):source.index('@interface TIOKnowledgeClient()')]
# Compiled lab implementation is executed with forbidden-home trap; no real file or service access.
importsource='#import <Foundation/Foundation.h>\n#import "TIOStorageIdentity.h"\nstatic NSString *ForbiddenHome(void){abort();}\n#define NSHomeDirectory ForbiddenHome\n'+body+'\nint main(void){@autoreleasepool{TIOImportKnowledgeConnection();puts("LAB_IMPORTER_NO_FILE_ACCESS_PASS");}return 0;}\n'
(out/'Importer.m').write_text(importsource)
exe=out/'importer-lab';subprocess.run(base+['-Wno-unused-function']+flags(lab)+[str(out/'Importer.m'),'-o',str(exe)],check=True);subprocess.run([str(exe)],check=True)
pre=subprocess.check_output(['xcrun','clang','-E','-P','-fobjc-arc','-fmodules','-I',str(addon),*flags(lab),str(out/'Importer.m')],text=True)
active=pre[pre.index('void TIOImportKnowledgeConnection(void){'):pre.index('int main(void)')]
assert 'return;' in active and all(x not in active for x in ['NSHomeDirectory','NSFileManager','JSONObject','removeItem','TurboIOKnowledgeConnection.json'])
print('LAB_IMPORTER_PREPROCESSED_NO_READ_PARSE_DELETE_PASS')
# SDK syntax compiles real modules only, never links/runs their constructors.
sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
modules=['Addon.m','Profile.m','KnowledgeClient.m','KnowledgeUI.m','VoiceTTSCore.m','VoiceTTS.m','NavigationUI.m']
for label,config in [('default',[]),('lab',flags(lab))]:
 for name in modules:
  cmd=['xcrun','--sdk','iphoneos','clang','-arch','arm64','-isysroot',sdk,'-miphoneos-version-min=16.0','-fobjc-arc','-fmodules','-Wno-deprecated-declarations','-Wno-incompatible-pointer-types','-Wno-unused-parameter','-fsyntax-only','-I',str(addon),*config,str(addon/name)]
  r=subprocess.run(cmd,capture_output=True,text=True);(out/(label+'-'+name+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:print(r.stderr[-5000:]);raise SystemExit('SDK compile failed '+label+' '+name)
print('SDK_ARM64_SYNTAX_PASS 7_modules_2_modes NO_CONSTRUCTOR_EXECUTION')
manifest=[]
for name in ['TIOStorageIdentity.h',*modules,'storage-identity-tests/StorageIdentityTests.m','storage-identity-tests/check.py']:
 manifest.append({'path':name,'sha256':hashlib.sha256((addon/name).read_bytes()).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('STORAGE_IDENTITY_ALL_PASS')
