#!/usr/bin/env python3
"""Truthful Stage B gates: no signing, installation, App, engine or DB calls."""
import argparse
import importlib.util
import json
from pathlib import Path
import plistlib
import shutil
import stat
import struct
import zipfile

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('sealed_stage_a',ROOT/'lab.py')
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
APP='Payload/Runner.app/'
DYLIB='TurboIOStageBController.dylib'
LOAD='@executable_path/Frameworks/'+DYLIB
RUNNER_SHA='b27f3581bea7a945560bce501823a9697fc878ab54286a330fa199bb85b828aa'
REJECTED_SHA='b44fd88ad9f04571e71bbd1f8be77565a904047b1a23e1a6d97fd9b57353ca8f'
# A separately reviewed code change must add literal module/receipt/source identities.
# A CLI flag or a self-authored adjacent receipt is never an approval authority.
APPROVED_MODULES={}


def file_sha(path):
 with Path(path).open('rb') as f:return lab.stream_sha(f)


def blocked(status):
 return dict(status=status,module_linked=False,entry_installed=False,observation_active=False,device_ready=False,
             schema_capture_accepted=False,signing_performed=False,install_performed=False)


def module_gate(module,receipt=None):
 result=blocked('BLOCKED_UNREVIEWED_MODULE')
 module=lab.no_symlinks(Path(module).absolute())
 if not module.is_file():return result
 digest=file_sha(module);result['module_sha256']=digest
 approved=APPROVED_MODULES.get(digest)
 if approved is None or receipt is None:return result
 receipt=lab.no_symlinks(Path(receipt).absolute())
 if not receipt.is_file() or receipt.stat().st_size>1024*1024 or file_sha(receipt)!=approved['receipt_sha256']:return result
 metadata=json.loads(receipt.read_bytes())
 if metadata.get('module_sha256')!=digest or metadata.get('source_manifest_sha256')!=approved['source_manifest_sha256']:return result
 h=lab.inspect(module.read_bytes())
 if h['filetype']!=6 or h['install']!='@rpath/'+DYLIB or h['signatures']:raise ValueError('Approved module Mach-O identity mismatch')
 if any(not d.startswith(('/usr/lib/','/System/Library/')) for d in h['deps']):raise ValueError('Unapproved external module dependency')
 result.update(status='BLOCKED_RUNTIME_ENTRY',module_reviewed=True,receipt_sha256=approved['receipt_sha256'],source_manifest_sha256=approved['source_manifest_sha256'],receipt_path=str(receipt))
 return result


def new_output(source,output):
 source=lab.no_symlinks(Path(source).absolute());output=lab.no_symlinks(Path(output).absolute())
 if output.exists() or output==source or source in output.parents or output in source.parents:raise ValueError('Output must be new, separate, without symlink ancestors')
 return output


def verify_runner_delta(before,after):
 old,new=lab.inspect(before),lab.inspect(after)
 if new['commands'][:-1]!=[c for c in old['commands'] if c[0]!=0x1d] or new['deps']!=old['deps']+[LOAD]:raise ValueError('Original commands/ordinals or required Stage B load mismatch')
 if old['uuid']!=new['uuid'] or len(before)!=len(after) or new['signatures']:raise ValueError('Runner UUID/size/unsigned mismatch')
 ranges=[(16,24),(32,max(old['end'],new['end']))]+[(a,a+n) for a,n in old['signatures']]
 cursor=0
 for start,stop in sorted(ranges):
  if before[cursor:start]!=after[cursor:start]:raise ValueError('Runner non-allowlisted bytes changed')
  cursor=stop
 if before[cursor:]!=after[cursor:]:raise ValueError('Runner body/binding bytes changed')
 for offset,extent in old['signatures']:
  if any(after[offset:offset+extent]):raise ValueError('Runner signature blob not removed')
 return True


def patch_runner(data,synthetic=False):
 before=lab.inspect(data)
 if not synthetic and (lab.digest(data)!=RUNNER_SHA or len(before['deps'])!=156):raise ValueError('Requires exact original Runner hash and 156 dependencies')
 if before['filetype']!=2 or before['uuid']!=lab.UUID or any('TurboIO' in d or 'ManuscriptLab' in d for d in before['deps']):raise ValueError('Wrong or previously modified original Runner')
 text=LOAD.encode()+b'\0';size=(24+len(text)+7)//8*8
 command=struct.pack('<6I',12,size,24,0,0,0)+text+bytes(size-24-len(text))
 retained=[c for c in before['commands'] if c[0]!=0x1d];raw=b''.join(c[1] for c in retained)+command;end=32+len(raw)
 if end>before['first'] or end>len(data) or (end>before['end'] and any(data[before['end']:end])):raise ValueError('Insufficient verified empty header padding')
 result=bytearray(data);stop=max(end,before['end']);result[32:stop]=raw+bytes(stop-end)
 struct.pack_into('<II',result,16,len(retained)+1,len(raw))
 for offset,extent in before['signatures']:result[offset:offset+extent]=bytes(extent)
 result=bytes(result);verify_runner_delta(data,result);return result


def archive_entries(archive):
 names=set();folded=set()
 for entry in archive.infolist():
  name=entry.filename;mode=entry.external_attr>>16;canonical=name.rstrip('/').casefold()
  if not name or name.startswith('/') or '\\' in name or '\0' in name or any(p in ('','.','..') for p in name.rstrip('/').split('/')):raise ValueError('Unsafe output ZIP path')
  if name in names or canonical in folded or entry.flag_bits&1 or stat.S_IFMT(mode) not in (0,stat.S_IFREG,stat.S_IFDIR):raise ValueError('Duplicate/encrypted/special output ZIP member')
  names.add(name);folded.add(canonical)
 return names


def isolated_info(original,run):
 info=dict(original)
 for key in ('CFBundleURLTypes','NSUserActivityTypes','UIBackgroundModes','UTExportedTypeDeclarations','UTImportedTypeDeclarations'):info.pop(key,None)
 ident=lab.identity(run)
 info.update(CFBundleIdentifier=ident['bundle'],CFBundleDisplayName=ident['display_name'],CFBundleName='ManuscriptLab105-'+run,TIOManuscriptLabStage='B_INTERNAL_DISABLED',TIOManuscriptLabRunID=run,LSSupportsOpeningDocumentsInPlace=False,UIFileSharingEnabled=False)
 return info


def audit_archive(ipa,run,original_ipa=None,expected_module_sha=None):
 errors=[];retained=0;removed=0
 with zipfile.ZipFile(ipa) as archive:
  names=archive_entries(archive);info=plistlib.loads(archive.read(APP+'Info.plist'));ident=lab.identity(run)
  if info.get('CFBundleIdentifier')!=ident['bundle']:errors.append('WRONG_BUNDLE')
  if info.get('CFBundleDisplayName')!=ident['display_name']:errors.append('WRONG_DISPLAY_NAME')
  if info.get('TIOManuscriptLabRunID')!=run or info.get('TIOManuscriptLabStage')!='B_INTERNAL_DISABLED':errors.append('WRONG_RUN_MARKER')
  h=lab.inspect(archive.read(APP+'Runner'))
  if h['uuid']!=lab.UUID or h['signatures']:errors.append('WRONG_RUNNER_IDENTITY_OR_SIGNATURE')
  if h['deps'][-1:]!=[LOAD] or h['deps'].count(LOAD)!=1:errors.append('MISSING_STAGE_B_LOAD')
  module=APP+'Frameworks/'+DYLIB
  if module not in names:errors.append('MISSING_MODULE')
  elif expected_module_sha is not None and lab.digest(archive.read(module))!=expected_module_sha:errors.append('TAMPERED_MODULE')
  marker_name=APP+'ManuscriptLabStageB.json';marker=json.loads(archive.read(marker_name)) if marker_name in names else {}
  if any(marker.get(k,False) is not False for k in ('schema_observation_enabled','entry_installed','observation_active','device_ready','writes_enabled','db_access_enabled')):errors.append('FALSE_OBSERVATION_CLAIM')
  if marker.get('stage')!='B_INTERNAL_DISABLED' or marker.get('run')!=run or marker.get('expected_bundle')!=ident['bundle']:errors.append('WRONG_STAGE_B_MARKER')
  if archive.testzip() is not None:errors.append('ZIP_CRC')
  if original_ipa is not None:
   expected=lab.trusted_original_manifest(original_ipa)['files'];removed_names={n for n in expected if '_CodeSignature' in Path(n).parts}
   expected_names={APP+n for n in expected if n not in removed_names}|{module,marker_name}
   if {n for n in names if not n.endswith('/')}!=expected_names:errors.append('UNEXPECTED_MEMBER_DIFF')
   with zipfile.ZipFile(original_ipa) as original:
    old_runner=original.read(APP+'Runner');old_info=plistlib.loads(original.read(APP+'Info.plist'))
   if info!=isolated_info(old_info,run):errors.append('UNEXPECTED_INFO_PLIST_DIFF')
   try:verify_runner_delta(old_runner,archive.read(APP+'Runner'))
   except ValueError:errors.append('RUNNER_BYTES_OR_ORDINALS_CHANGED')
   for name,record in expected.items():
    if name in removed_names or name in ('Runner','Info.plist'):continue
    member=APP+name
    if member not in names or lab.digest(archive.read(member))!=record['sha256'] or stat.S_IMODE(archive.getinfo(member).external_attr>>16)!=record['mode']:
     errors.append('RETAINED_MEMBER_CHANGED:'+name);break
    retained+=1
   removed=len(removed_names)
 result=blocked('BLOCKED_RUNTIME_ENTRY' if not errors else 'REJECTED_ARCHIVE')
 result.update(accepted=not errors,structural_valid=not errors,errors=errors,module_linked=not any(x in errors for x in ('MISSING_STAGE_B_LOAD','MISSING_MODULE')),
               ipa_sha256=file_sha(ipa),retained_files_verified=retained,signature_files_removed=removed)
 return result


def preflight(original_ipa,source,module,run,receipt=None):
 original=lab.preflight(source,run,original_ipa=original_ipa);result=module_gate(module,receipt)
 result.update(original_ipa_sha256=original['original_ipa_sha256'],original_inventory_verified=True,original_files=len(original['source_files']),run=run,expected_bundle=lab.identity(run)['bundle'])
 return result


def prepare_internal(original_ipa,source,module,run,output,receipt=None):
 output=new_output(source,output);state=preflight(original_ipa,source,module,run,receipt)
 if state['status']!='BLOCKED_RUNTIME_ENTRY' or not state.get('module_reviewed'):return state
 return build_reviewed_internal(original_ipa,source,module,run,output,state)


def build_reviewed_internal(original_ipa,source,module,run,output,approval):
 # Recheck approval here too: direct calls cannot bypass the public gate.
 gate=module_gate(module,approval.get('receipt_path'))
 if not gate.get('module_reviewed'):return blocked('BLOCKED_UNREVIEWED_MODULE')
 report=lab.preflight(source,run,original_ipa=original_ipa);output=new_output(source,output)
 data=Path(module).read_bytes();patched=patch_runner((Path(source)/'Runner').read_bytes())
 output.mkdir(parents=True,mode=0o700,exist_ok=False)
 (output/'PARTIAL-UNUSABLE.json').write_text(json.dumps(blocked('PARTIAL_UNUSABLE_NOT_FOR_INSTALL'),indent=2)+'\n')
 app=output/'Payload/Runner.app';app.mkdir(parents=True,mode=0o755)
 for name,mode in sorted(lab.directory_inventory(source).items(),key=lambda x:len(Path(x[0]).parts)):
  if '_CodeSignature' in Path(name).parts:continue
  target=app/name;target.mkdir(parents=True,exist_ok=True);target.chmod(mode)
 for name,record in report['source_files'].items():
  if '_CodeSignature' in Path(name).parts:continue
  target=app/name;target.parent.mkdir(parents=True,exist_ok=True)
  with (Path(source)/name).open('rb') as incoming,target.open('xb') as outgoing:shutil.copyfileobj(incoming,outgoing)
  target.chmod(record['mode'])
 (app/'Runner').write_bytes(patched);info=isolated_info(plistlib.loads((app/'Info.plist').read_bytes()),run);ident=lab.identity(run)
 (app/'Info.plist').write_bytes(plistlib.dumps(info))
 marker=dict(stage='B_INTERNAL_DISABLED',run=run,expected_bundle=ident['bundle'],module_linked=True,entry_installed=False,observation_active=False,schema_observation_enabled=False,writes_enabled=False,db_access_enabled=False,device_ready=False,module_sha256=lab.digest(data),runtime_entry_status='BLOCKED_RUNTIME_ENTRY')
 (app/'ManuscriptLabStageB.json').write_text(json.dumps(marker,indent=2)+'\n');target=app/'Frameworks'/DYLIB;target.write_bytes(data);target.chmod(0o755)
 ipa=output/'INTERNAL-NOT-FOR-INSTALL-StageB-unsigned.ipa'
 with zipfile.ZipFile(ipa,'x',zipfile.ZIP_DEFLATED) as archive:
  for path in sorted(app.rglob('*')):
   if path.is_file():archive.write(path,path.relative_to(output).as_posix())
 result=audit_archive(ipa,run,original_ipa,lab.digest(data))
 if not result['accepted']:raise ValueError('Postarchive rejected: partial output remains unusable')
 if report['source_files']!=lab.preflight(source,run,original_ipa=original_ipa)['source_files'] or file_sha(module)!=lab.digest(data):raise ValueError('Input changed during preparation')
 result.update(status='INTERNAL_UNSIGNED_BLOCKED_RUNTIME_ENTRY_NOT_FOR_INSTALL',source_unchanged=True,module_reviewed=True)
 (output/'stage-b-report.json').write_text(json.dumps(result,indent=2)+'\n');return result


def main():
 parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
 p=sub.add_parser('audit');p.add_argument('--ipa',required=True,type=Path);p.add_argument('--run',required=True);p.add_argument('--original-ipa',type=Path)
 for action in ('preflight','prepare-internal'):
  p=sub.add_parser(action);p.add_argument('--original-ipa',required=True,type=Path);p.add_argument('--app',required=True,type=Path);p.add_argument('--module',required=True,type=Path);p.add_argument('--run',required=True);p.add_argument('--receipt',type=Path)
  if action=='prepare-internal':p.add_argument('--out',required=True,type=Path)
 args=parser.parse_args()
 if args.command=='audit':result=audit_archive(args.ipa,args.run,args.original_ipa)
 elif args.command=='preflight':result=preflight(args.original_ipa,args.app,args.module,args.run,args.receipt)
 else:result=prepare_internal(args.original_ipa,args.app,args.module,args.run,args.out,args.receipt)
 print(json.dumps(result,indent=2));return 2 if result['status'].startswith(('REJECTED','BLOCKED')) else 0


if __name__=='__main__':
 try:raise SystemExit(main())
 except (ValueError,OSError,KeyError,struct.error) as error:raise SystemExit('STAGE_B_FAIL_CLOSED: '+str(error))
