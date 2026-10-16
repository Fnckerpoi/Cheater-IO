#!/usr/bin/env python3
"""No-sign isolated two-module recipe. Unsafe storage source fails before compilation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import struct
import uuid

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent
MAIN_ADDON_SOURCES=[
 'NavigationRouteSession.m','NavigationSession.m','NavigationModes.m','ProtocolContext.m','NavigationSubtitleHUD.m',
 'NavigationPlaces.m','NavigationPlacePicker.m','A2UIProtocol.m','NavigationCore.m','NavigationTeleHUD.m',
 'NavigationTransport.m','NavigationUI.m','ManualHUD.m','SubtitleHUDCore.m','SubtitleHUD.m',
 'Core.m','Profile.m','KnowledgeClient.m','KnowledgeUI.m','ProfileUI.m','HomeTabLayout.m','HomeTabBridge.m',
 'ResearchCatalog.m','ResearchUI.m','NewsPresentation.m','PrivateBootstrap.m','VoiceTTSCore.m','VoiceTTS.m',
 'WebSearch.m','TodoProtocol.m','TodoRuntime.m','NewsCore.m','NewsReader.m','NewsTeleprompter.m',
 'RecordingExports.m','RecordingExportsUI.m','RecordingExportsMenu.m','RecordingText.m','RecordingTextUI.m',
 'RecordingTextMenu.m','AlwaysOnAudioFiles.m','AlwaysOnOgg.m','AlwaysOnAudioNative.m','AlwaysOnAudioUI.m','Addon.m']
BRIDGE_SOURCES=[
 'tmig1-edition/FocusBridge.m','tmig1-edition/FocusTransport.m','tmig1-edition/focus.c',
 'tmig1-edition/DisplayDiagnostics.m','tmig1-edition/display_carrier.c','tmig1-edition/display_runtime.c',
 'tmig1-edition/context-english-v1/ContextEnglishBridge.m','tmig1-edition/context-english-v1/ContextEnglishTransport.m',
 'tmig1-edition/context-english-v1/ContextEnglishNative.m','tmig1-edition/context-english-v1/ContextEnglishHistory.m',
 'tmig1-edition/context-english-v1/english_transfer.c','tmig1-edition/context-english-v1/english_progress.c',
 'tmig1-edition/context-english-v1/english_wire.c','tmig1-edition/context-english-v1/english_view.c']
ADDON_SOURCES=MAIN_ADDON_SOURCES+BRIDGE_SOURCES
BRIDGE_SUPPORT_FILES=[
 'tmig1-edition/FocusBridge.h','tmig1-edition/FocusTransport.h','tmig1-edition/ProtocolContext.h','tmig1-edition/focus.h',
 'tmig1-edition/DisplayDiagnostics.h','tmig1-edition/display_carrier.h','tmig1-edition/display_runtime.h','tmig1-edition/display_memory.h',
 'tmig1-edition/context-english-v1/ContextEnglishBridge.h','tmig1-edition/context-english-v1/ContextEnglishTransport.h',
 'tmig1-edition/context-english-v1/ContextEnglishNative.h','tmig1-edition/context-english-v1/ContextEnglishHistory.h',
 'tmig1-edition/context-english-v1/english_transfer.h','tmig1-edition/context-english-v1/english_progress.h',
 'tmig1-edition/context-english-v1/english_wire.h','tmig1-edition/context-english-v1/english_view.h',
 'tmig1-edition/context-english-v1/english_store.h','tmig1-edition/context-english-v1/english_session.h',
 'tmig1-edition/context-english-v1/english_schedule.h','tmig1-edition/navigation-runtime-v1/nav_view.h',
 'tmig1-edition/navigation-runtime-v1/nav_visual.h','tmig1-edition/navigation-runtime-v1/nav_runtime.h']
HUB_SOURCES=['AIHotDaily.m','TIONewsSource.m','TIONewsScheduler.m','TIOImportQueue.m','TIONewsHubPanel.m','TIONewsHubEntry.m','NewsHubNavigation.m']
STORAGE_SOURCES=['RecordingTextUI.m','NavigationUI.m','Addon.m','Profile.m','KnowledgeClient.m','KnowledgeUI.m','VoiceTTSCore.m','VoiceTTS.m']
LEGACY_STORAGE=('io.turboio.official-private-addon','io.turboio.official-profile','io.turboio.official-knowledge','io.turboio.research.preview','io.turboio.navigation')
FORBIDDEN_ENV=('CFLAGS','CPPFLAGS','LDFLAGS','DYLD_INSERT_LIBRARIES','DYLD_LIBRARY_PATH')


def digest(data):return hashlib.sha256(data).hexdigest()


def identity(run):
 if not re.fullmatch(r'[a-z0-9][a-z0-9-]{5,39}',run) or run in ('official','research'):raise ValueError('Explicit unique lab run ID required')
 return 'com.duriea.turboio.manuscriptlab106.'+run


def no_symlinks(path):
 path=Path(path)
 if not path.is_absolute() or '..' in path.parts:raise ValueError('Absolute unambiguous path required')
 for p in [path,*path.parents]:
  if p.is_symlink():raise ValueError('Symlink path rejected')
 return path


def new_output(source,output):
 source=no_symlinks(Path(source).absolute());output=no_symlinks(Path(output).absolute())
 owned_build_root=source/'final-build'/'build'
 inside_owned_build=owned_build_root in output.parents
 if output.exists() or output==source or (source in output.parents and not inside_owned_build) or output in source.parents:raise ValueError('New separate output required; only own final-build/build descendant is permitted under source')
 return output


def storage_issues(text):
 return [token for token in LEGACY_STORAGE if '@"'+token+'"' in text]


def preflight_sources(source):
 source=Path(source);blocking=[];found={}
 for name in STORAGE_SOURCES:
  text=(source/name).read_text()
  literals=storage_issues(text)
  if literals:blocking.append(name);found[name]=literals
 if not blocking and (source/'TIOStorageIdentity.h').is_file():
  for name in STORAGE_SOURCES:
   if '#import "TIOStorageIdentity.h"' not in (source/name).read_text():raise ValueError('Storage helper missing from actual compiled source: '+name)
 missing=[name for name in ADDON_SOURCES+HUB_SOURCES if not (source/name).is_file()]
 if missing:raise ValueError('Missing real production sources: '+','.join(missing))
 return dict(status='BLOCKED_STORAGE_NAMESPACE' if blocking else ('SOURCE_STORAGE_SEAM_PRESENT_NOT_MODULE_APPROVED' if (source/'TIOStorageIdentity.h').is_file() else 'BLOCKED_MISSING_STORAGE_SEAM'),blocking_sources=blocking,legacy_literals=found,
  native_compiled=False,newshub_compiled=False,storage_seam_reviewed=False,device_ready=False,bridge_available=False,
  probe_network_operations=False,whole_app_network_disabled_verified=False,runtime_isolation_proven=False)


def source_recipe(source):
 # Read source text only. Never source/execute the original codesign builder.
 script=(Path(source)/'build.sh').read_text()
 if 'codesign --force --sign - "$output"' not in script:raise ValueError('Original source recipe changed; review required')
 for name in MAIN_ADDON_SOURCES:
  if name not in script and name not in ('NavigationUI.m','SubtitleHUDCore.m','SubtitleHUD.m'):raise ValueError('Ordinary source list no longer matches main build')
 if len(ADDON_SOURCES)!=len(set(ADDON_SOURCES)) or len(HUB_SOURCES)!=len(set(HUB_SOURCES)):raise ValueError('Duplicate compilation source')
 if 'NewsReader.m' in HUB_SOURCES:raise ValueError('Duplicate Reader class in Hub')
 return dict(native_sources=ADDON_SOURCES,newshub_sources=HUB_SOURCES,
  native_install_name='@rpath/TurboIOPrivateAddon.dylib',hub_install_name='@rpath/TurboIONewsHub.dylib',
  target_arch='arm64',native_minimum_ios='16.0',hub_minimum_ios='15.0',signing_performed=False)


def preflight(run,allow_http=False,source=SOURCE):
 bundle=identity(run)
 for name in ('TIO_NEWS_UI_VALIDATION','TIO_IMPORT_QUEUE_LEGACY_TESTING','TIO_STAGE_B_TESTING','TIO_STORAGE_TESTING','TIO_UI_PREVIEW'):
  if os.environ.get(name) not in (None,'','0'):raise ValueError('Production recipe rejects testing flags: '+name)
 for name in FORBIDDEN_ENV:
  if os.environ.get(name):raise ValueError('Ambient compiler/injection setting rejected: '+name)
 result=preflight_sources(source)
 result.update(source_recipe(source),target_bundle=bundle,http_explicit_opt_in=bool(allow_http),
  http_service_executed=False,official_save_verified=False,source_root=str(source))
 return result


def inspect_module(path,install):
 data=Path(path).read_bytes()
 if len(data)<32 or struct.unpack_from('<II',data)!=(0xfeedfacf,0x100000c):raise ValueError('Not thin arm64 Mach-O')
 if struct.unpack_from('<I',data,12)[0]!=6:raise ValueError('Expected dylib')
 position=32;identity=None;actual_install=None;deps=[]
 for _ in range(struct.unpack_from('<I',data,16)[0]):
  command,length=struct.unpack_from('<II',data,position)
  if length<8 or length%8 or position+length>len(data):raise ValueError('Invalid module command bounds')
  raw=data[position:position+length]
  if command==0x1b:identity=str(uuid.UUID(bytes=raw[8:24])).upper()
  if command==0x1d:raise ValueError('Unexpected code signature; build must not sign')
  if command in (12,13,0x80000018,0x8000001f,0x80000023):
   offset=struct.unpack_from('<I',raw,8)[0];name=raw[offset:].split(b'\0')[0].decode()
   if command==13:actual_install=name
   else:deps.append(name)
  position+=length
 if actual_install!=install or identity is None:raise ValueError('Module UUID/install identity absent')
 if any(not d.startswith(('/usr/lib/','/System/Library/')) for d in deps):raise ValueError('Unexpected plugin/framework dependency')
 return dict(path=str(path),sha256=digest(data),uuid=identity,install_name=actual_install,dependencies=deps,signed=False)


def build(run,output,allow_http=False,source=SOURCE):
 output=new_output(source,output);pre=preflight(run,allow_http,source)
 if pre['status']!='SOURCE_STORAGE_SEAM_PRESENT_NOT_MODULE_APPROVED':return pre
 knowledge=(Path(source)/'KnowledgeClient.m').read_text()
 match=re.search(r'void TIOImportKnowledgeConnection\(void\)\s*\{(.*?)\n\}',knowledge,re.S)
 if not match or not re.search(r'#if\s+TIO_STORAGE_IS_LAB\s*\n\s*return\s*;',match.group(1)):raise ValueError('Lab external knowledge importer must return before file access')
 # Freeze complete source inputs, not private files, JSON resources or signing data.
 output.mkdir(parents=True,mode=0o700,exist_ok=False);snapshot=output/'source-snapshot';snapshot.mkdir()
 names=set(ADDON_SOURCES+HUB_SOURCES+BRIDGE_SUPPORT_FILES)|{p.name for p in Path(source).glob('*.h')}|{p.name for p in Path(source).glob('*.inc')}
 names|={'build.sh','build-newshub.sh'}
 records=[]
 for name in sorted(names):
  path=Path(source)/name
  if path.is_symlink() or not path.is_file():raise ValueError('Unsafe/missing source snapshot input')
  data=path.read_bytes();dest=snapshot/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);records.append(dict(path=name,sha256=digest(data),native_compiled=name in ADDON_SOURCES,hub_compiled=name in HUB_SOURCES))
 for record in records:
  if digest((Path(source)/record['path']).read_bytes())!=record['sha256']:raise ValueError('Source changed during freeze')
 (output/'source-manifest.json').write_text(json.dumps(dict(source_files=records,native_sources=ADDON_SOURCES,hub_sources=HUB_SOURCES,source_approved=False),indent=2)+'\n')
 recipe_data=Path(__file__).read_bytes();(output/'recipe-snapshot.py').write_bytes(recipe_data)
 if preflight_sources(snapshot)['blocking_sources']:raise ValueError('Frozen sources still use legacy storage namespaces')
 bundle=identity(run)
 sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
 common=['xcrun','--sdk','iphoneos','clang','-arch','arm64','-isysroot',sdk,'-fobjc-arc','-fmodules','-dynamiclib','-Wall','-Wextra','-Werror','-Wno-unused-parameter','-Wno-incompatible-pointer-types','-Wno-error=deprecated-declarations','-Wno-error=atomic-property-with-user-defined-accessor','-Wno-error=arc-performSelector-leaks','-Wno-error=sign-compare','-Wl,-undefined,error','-Wl,-no_fixup_chains','-Wl,-no_adhoc_codesign','-I',str(snapshot)]
 target=['-DTIO_TARGET_BUNDLE_ID="'+bundle+'"','-DTIO_STORAGE_LAB_BUNDLE_ID="'+bundle+'"']
 disabled=['-DTIO_UI_PREVIEW=0','-DTIO_NEWS_UI_VALIDATION=0','-DTIO_IMPORT_QUEUE_LEGACY_TESTING=0','-DTIO_AMAP_ENABLED=0','-DTIO_MAPKIT_ENABLED=0','-DTIO_MAPKIT_HUD_ENABLED=0','-DTIO_OTA_RESEARCH_ENABLED=0','-DTIO_NATIVE_NAV=0','-DTIO_MUSIC=0','-DTIO_LOCAL_TRANSLATION=0']
 native=output/'TurboIOPrivateAddon.dylib'
 native_args=common+['-miphoneos-version-min=16.0',*target,*disabled,'-DTIO_LOCAL_HTTP_ENABLED='+('1' if allow_http else '0'),'-install_name','@rpath/TurboIOPrivateAddon.dylib']
 for framework in ('Foundation','UIKit','Security','UniformTypeIdentifiers','CoreLocation','AVFAudio'):native_args+=['-framework',framework]
 native_args += [str(snapshot/n) for n in ADDON_SOURCES]+['-o',str(native)]
 with (output/'native-compile.log').open('w') as log:subprocess.run(native_args,check=True,stdout=log,stderr=log)
 native_info=inspect_module(native,'@rpath/TurboIOPrivateAddon.dylib')
 symbols=subprocess.check_output(['xcrun','nm','-g',str(native)],text=True)
 if symbols.count(' _OBJC_CLASS_$_TIONewsReader\n')!=1 or ' _TIONewsConfigure\n' not in symbols or ' _OBJC_CLASS_$_TIOWebChatRequest\n' not in symbols:raise ValueError('Actual TinyFish reader/model chain definitions missing or duplicated')
 bridge_symbols=subprocess.check_output(['xcrun','nm','-gU',str(native)],text=True)
 if any(symbol not in bridge_symbols for symbol in ('_CEPhoneSendCards','_TFFocusConsume')):raise ValueError('TMIG1 paired phone bridge exports missing')
 native_bytes=native.read_bytes()
 for suffix in ('.private-addon','.profile','.knowledge','.navigation'):
  literal=(bundle+suffix).encode()
  if literal not in native_bytes and (bundle+suffix).encode('utf-16le') not in native_bytes:raise ValueError('Expected isolated storage service missing from actual binary: '+suffix)
 if '_TIOCreateResearchPreview' in symbols:raise ValueError('Preview fixture unexpectedly compiled into production module')
 if b'TurboIOKnowledgeConnection.json' in native_bytes:raise ValueError('Lab external knowledge importer was not excluded')
 for token in LEGACY_STORAGE:
  if token.encode()+b'\0' in native.read_bytes() or token.encode('utf-16le')+b'\0\0' in native.read_bytes():raise ValueError('Legacy storage namespace leaked into lab binary: '+token)
 hub=output/'TurboIONewsHub.dylib'
 hub_args=common+['-miphoneos-version-min=15.0','-DTIO_NEWS_TARGET_BUNDLE_ID="'+bundle+'"','-DTIO_NEWS_EXPECTED_ADDON_UUID="'+native_info['uuid']+'"',*disabled,'-install_name','@rpath/TurboIONewsHub.dylib']
 for framework in ('Foundation','UIKit','CoreGraphics'):hub_args+=['-framework',framework]
 hub_args += [str(snapshot/n) for n in HUB_SOURCES]+['-o',str(hub)]
 with (output/'hub-compile.log').open('w') as log:subprocess.run(hub_args,check=True,stdout=log,stderr=log)
 hub_info=inspect_module(hub,'@rpath/TurboIONewsHub.dylib')
 hub_bytes=hub.read_bytes()
 if native_info['uuid'].encode() not in hub_bytes or bundle.encode() not in hub_bytes:raise ValueError('NewsHub binary does not contain actual configured Addon UUID and exact lab identity')
 syms=subprocess.check_output(['xcrun','nm','-g',str(hub)],text=True)
 required=['_TIONewsHubInstallUI','_TIONewsHubNavigationInstall','_OBJC_CLASS_$_TIONewsScheduler','_OBJC_CLASS_$_TIOImportQueue','_OBJC_CLASS_$_TIONewsFetcher']
 if any(token not in syms for token in required) or '_OBJC_CLASS_$_TIONewsReader' in syms:raise ValueError('NewsHub production definitions missing/duplicate original Reader')
 config=dict(bundle=bundle,http_explicit_opt_in=allow_http,storage_lab_bundle=bundle,news_expected_addon_uuid=native_info['uuid'],ui_validation=False,legacy_import_testing=False,signing_performed=False)
 manifest=dict(status='INTERNAL_UNSIGNED_MODULES_ONLY_NO_RUNTIME_OR_SAVE_ACCEPTANCE',native_compiled=True,newshub_compiled=True,module_registration_approved=False,configuration=config,recipe_sha256=digest(recipe_data),source_manifest_sha256=digest((output/'source-manifest.json').read_bytes()),source_files=records,modules=[native_info,hub_info],compiler_commands=[native_args,hub_args],warnings_policy='Strict Werror; existing incompatible-pointer/unused-parameter flags retained; Existing source diagnostics visible not fatal: SDK deprecation, NavigationPlacePicker atomic accessor, NavigationWorkspace performSelector ARC leak, RecordingExportsUI/Addon sign-compare',device_ready=False,official_save_verified=False,bridge_available=False,app_executed=False,db_accessed=False,network_services_executed=False,runtime_isolation_proven=False)
 for record in records:
  if digest((Path(source)/record['path']).read_bytes())!=record['sha256']:raise ValueError('Source changed after freeze; output requires review')
 (output/'build-receipt.json').write_text(json.dumps(manifest,indent=2)+'\n')
 return manifest


def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',required=True);p.add_argument('--out',type=Path);p.add_argument('--allow-http',action='store_true');args=p.parse_args()
 if args.out:result=build(args.run,args.out,args.allow_http)
 else:result=preflight(args.run,args.allow_http)
 print(json.dumps(result,indent=2))
 return 0 if result.get('native_compiled') and result.get('newshub_compiled') else 2

if __name__=='__main__':
 try:raise SystemExit(main())
 except (ValueError,OSError) as error:raise SystemExit('FINAL_BUILD_FAIL_CLOSED: '+str(error))
