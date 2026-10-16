#!/usr/bin/env python3
from pathlib import Path
import argparse
import subprocess

p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parent;addon=root.parent;out=args.out
if not out.is_absolute() or out.exists():raise SystemExit('New absolute output required')
for parent in [out,*out.parents]:
 if parent.is_symlink():raise SystemExit('Symlink output refused')
out.mkdir(parents=True,mode=0o700)
base=['cc','-std=c11','-Wall','-Wextra','-Werror','-I',str(addon)]
source=str(root/'PolicyTests.c')
uuid='11111111-2222-4333-8444-555555555555';lab='com.duriea.turboio.manuscriptlab105.public-20261015'
def flags(bundle,expected=uuid):return ['-DTIO_NEWS_TARGET_BUNDLE_ID="'+bundle+'"','-DTIO_NEWS_EXPECTED_ADDON_UUID="'+expected+'"']
for label,config in [('default',[]),('explicit-lab',flags(lab)),('explicit-research',flags('com.duriea.turboio.research','3F635A04-D0C3-3196-919E-D4B73D7B0945'))]:
 exe=out/label;subprocess.run(base+config+[source,'-o',str(exe)],check=True);subprocess.run([str(exe)],check=True)
invalid=[flags('com.rayneo.venus.pub'),flags('com.duriea.turboio.manuscriptlab105.*'),flags('com.duriea.turboio.manuscriptlab105.abc'),flags('com.duriea.turboio.manuscriptlab105.-bad-run'),flags('com.duriea.turboio.manuscriptlab105.public_run'),flags('com.duriea.turboio.manuscriptlab105.Public-run'),flags('com.duriea.turboio.manuscriptlab105.official'),flags('com.duriea.turboio.manuscriptlab105.research'),flags('com.duriea.turboio.manuscriptlab105.'+'a'*41),flags(''),flags(lab,'not-a-uuid'),flags(lab,'abcdef01-2222-4333-8444-555555555555'),flags(lab,uuid+'X'),flags('com.unknown.bundle'),flags(lab+'\";not_c_literal'),flags(lab, '11111111-2222-4333-8444-55555555555*'),['-DTIO_NEWS_TARGET_BUNDLE_ID="'+lab+'"'],['-DTIO_NEWS_EXPECTED_ADDON_UUID="'+uuid+'"']]
for i,config in enumerate(invalid):
 result=subprocess.run(base+config+[source,'-o',str(out/('invalid-'+str(i)))],capture_output=True,text=True)
 if result.returncode==0:raise SystemExit('Invalid compile configuration unexpectedly accepted: '+repr(config))
print('INVALID_COMPILE_CONFIG_REJECT_PASS',len(invalid))
subprocess.run(['clang','-fobjc-arc','-Wall','-Wextra','-Werror','-framework','Foundation','-I',str(addon),str(root/'HostVersionTests.m'),'-o',str(out/'host-version')],check=True)
subprocess.run([str(out/'host-version')],check=True)
sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
for label,config in [('default',[]),('lab',flags(lab)),('ui-validation',['-DTIO_NEWS_UI_VALIDATION=1'])]:
 subprocess.run(['xcrun','--sdk','iphoneos','clang','-arch','arm64','-isysroot',sdk,'-miphoneos-version-min=15.0','-fobjc-arc','-fmodules','-Wall','-Wextra','-Werror','-Wno-unused-parameter','-I',str(addon),*config,'-c',str(addon/'NewsHubNavigation.m'),'-o',str(out/('NewsHubNavigation-'+label+'.o'))],check=True)
print('NAVIGATION_ARM64_COMPILE_PASS default lab ui-validation; no UIKit execution')
