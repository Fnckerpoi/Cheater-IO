#!/usr/bin/env python3
from pathlib import Path
import argparse, hashlib, json, subprocess, re, shlex
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent; addon=root.parent; out=a.out
if not out.is_absolute() or out.exists() or any(x.is_symlink() for x in [out,*out.parents]):raise SystemExit('New canonical absolute output required')
out.mkdir(parents=True,mode=0o700)
support=addon/'support-manifest.json'
if support.exists():
 for entry in json.loads(support.read_text()):
  path=addon/entry['path'];data=path.read_bytes();assert not path.is_symlink() and path.resolve().is_relative_to(addon.resolve()) and len(data)==entry['size'] and hashlib.sha256(data).hexdigest()==entry['sha256']
 print('FROZEN_RECORDING_SUPPORT_VERIFIED')
source=(addon/'RecordingTextUI.m').read_text()
domain=next(x for x in source.splitlines() if x.startswith('static NSString *') and 'TextDomain' in x)
prefs=next(x for x in source.splitlines() if x.startswith('static NSUserDefaults *TextPrefs(')).replace('NSUserDefaults','RecordingFixtureDefaults')
query=next(x.strip() for x in source.splitlines() if 'NSDictionary *q=@{' in x and 'kSecAttrService' in x)
fixture='''#import <Foundation/Foundation.h>
#import <Security/Security.h>
#import "TIOStorageIdentity.h"
#include <assert.h>
@interface RecordingFixtureDefaults:NSObject
@property(nonatomic,copy) NSString *observedSuite;
- (instancetype)initWithSuiteName:(NSString *)suite;
@end
@implementation RecordingFixtureDefaults
- (instancetype)initWithSuiteName:(NSString *)suite {if((self=[super init]))self.observedSuite=suite;return self;}
@end
'''+domain+'\n'+prefs+'\nstatic NSDictionary *FixtureQuery(NSString *endpoint){'+query+'return q;}\n'+'''int main(void){@autoreleasepool {
#ifdef TIO_STORAGE_LAB_BUNDLE_ID
NSString *expected=@"com.duriea.turboio.manuscriptlab106.public-recording-a.private-addon";
#else
NSString *expected=@"io.turboio.official-private-addon";
#endif
assert([TextPrefs().observedSuite isEqual:expected]);
assert([TextPrefs().observedSuite isEqual:TIOStorageAddonDomain()]);
NSDictionary *q=FixtureQuery(@"public-fixture-endpoint");
assert([q[(__bridge id)kSecAttrService] isEqual:expected]);
assert([q[(__bridge id)kSecAttrAccount] isEqual:@"public-fixture-endpoint"]);
assert([q[(__bridge id)kSecReturnData] isEqual:@YES]);
assert([q[(__bridge id)kSecClass] isEqual:(__bridge id)kSecClassGenericPassword]);
puts("ACTUAL_RECORDING_PREFS_SPY_QUERY_DICTIONARY_PASS_NO_DATA_ACCESS");
}return 0;}\n'''
(out/'RecordingStorageFixture.m').write_text(fixture)
lab='com.duriea.turboio.manuscriptlab106.public-recording-a';flags=['-DTIO_STORAGE_LAB_BUNDLE_ID="'+lab+'"','-DTIO_TARGET_BUNDLE_ID="'+lab+'"']
base=['xcrun','clang','-fobjc-arc','-fmodules','-Wall','-Wextra','-Werror','-framework','Foundation','-framework','Security','-I',str(addon)]
for label,config in [('default',[]),('lab',flags)]:
 exe=out/label;subprocess.run(base+config+[str(out/'RecordingStorageFixture.m'),'-o',str(exe)],check=True);r=subprocess.run([str(exe)],capture_output=True,text=True);(out/(label+'.log')).write_text(r.stdout+r.stderr);print(label,'EXIT',r.returncode,r.stdout.strip());assert r.returncode==0,label
sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
for label,config in [('default',[]),('lab',flags)]:
 cmd=['xcrun','--sdk','iphoneos','clang','-arch','arm64','-isysroot',sdk,'-miphoneos-version-min=16.0','-fobjc-arc','-fmodules','-Wno-deprecated-declarations','-Wno-incompatible-pointer-types','-fsyntax-only','-I',str(addon),*config,str(addon/'RecordingTextUI.m')]
 r=subprocess.run(cmd,capture_output=True,text=True);(out/(label+'-SDK.log')).write_text(r.stdout+r.stderr);assert r.returncode==0,r.stderr[-4000:]
print('RECORDING_SDK_SYNTAX_PASS_2_CONFIGS_NO_UI_EXECUTION')
assert '@"io.turboio.official-private-addon"' not in source
print('RECORDING_SOURCE_OLD_LITERAL_ABSENT_PASS')
print('RECORDING_STORAGE_ALL_PASS')
