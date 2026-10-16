#!/usr/bin/env python3
from pathlib import Path
import argparse, subprocess, hashlib, json, os, tempfile, ctypes
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
tests=Path(__file__).resolve().parent;src=tests.parent;base=src.parents[1];out=a.out
if not out.is_absolute() or out.exists() or any(x.is_symlink() for x in [out,*out.parents]):raise SystemExit('New canonical absolute output required')
out.mkdir(parents=True,mode=0o700)
files=['final-build/schema-capture/'+n for n in ['Capture.h','CaptureInternal.h','Capture.c','SchemaPolicy.h','SchemaPolicy.c','SchemaPolicyTests.c','CaptureTests.c','OriginalExceptionTests.m','AttackMatrix.py','OwnEngine.c','capture-core-tests/BindTests.c','capture-core-tests/SelfEngineTests.c','capture-core-tests/start-check.py','capture-core-tests/StartTests.c','capture-core-tests/StartFaultTests.c']]+['manuscript-lab/'+n for n in ['schema-observer/SchemaView.h','schema-observer/SchemaView.c','memory-copy/MemoryCopy.h','memory-copy/MemoryCopy.c']]
entries=[]
for name in files:
 source=base/name;assert source.is_file() and not source.is_symlink();data=source.read_bytes();target=out/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);entries.append({'path':name,'size':len(data),'sha256':hashlib.sha256(data).hexdigest()})
(out/'source-manifest.json').write_text(json.dumps({'task':'task-97','files':entries,'production_runtime_capability':False,'production_export_requires_internal_start':True},indent=2)+'\n')
src=out/'source/final-build/schema-capture';lab=out/'source/manuscript-lab';test=src/'capture-core-tests';env=dict(os.environ,ASAN_OPTIONS='detect_leaks=0')
flags=['clang','-std=c11','-Wall','-Wextra','-Werror','-I',str(src)]
core=[str(src/'Capture.c'),str(src/'SchemaPolicy.c'),str(lab/'schema-observer/SchemaView.c'),str(lab/'memory-copy/MemoryCopy.c')]
def run(cmd,log):
 r=subprocess.run([str(x) for x in cmd],capture_output=True,text=True,env=env);(out/log).write_text(r.stdout+r.stderr)
 if r.returncode:raise SystemExit('FAILED '+log+' exit='+str(r.returncode)+' '+r.stderr[-2000:])
 return r
for mode,san in [('normal',[]),('asan',['-fsanitize=address','-fno-omit-frame-pointer']),('ubsan',['-fsanitize=undefined','-fno-sanitize-recover=all'])]:
 run(flags+san+[*core,test/'BindTests.c','-o',out/('bind-'+mode)],'compile-bind-'+mode+'.log');run([out/('bind-'+mode)],'bind-'+mode+'.log')
 run(flags+san+[src/'SchemaPolicy.c',src/'SchemaPolicyTests.c','-o',out/('policy-'+mode)],'compile-policy-'+mode+'.log');run([out/('policy-'+mode)],'policy-'+mode+'.log')
 run(flags+san+['-DTIO_CAPTURE_TESTING',*core,src/'CaptureTests.c','-o',out/('capture-'+mode)],'compile-capture-'+mode+'.log')
 attacks=out/('attacks-'+mode);attacks.mkdir();run(['python3',src/'AttackMatrix.py',out/('capture-'+mode),attacks],'attacks-'+mode+'.log')
 run(flags+san+['-dynamiclib',src/'OwnEngine.c','-o',out/('OwnEngine-'+mode+'.dylib')],'compile-engine-'+mode+'.log')
 run(flags+san+['-DTIO_CAPTURE_TESTING',*core,test/'SelfEngineTests.c','-o',out/('self-engine-'+mode)],'compile-self-engine-'+mode+'.log')
 home=Path(tempfile.mkdtemp(prefix='public-self-engine-',dir=out));run([out/('self-engine-'+mode),out/('OwnEngine-'+mode+'.dylib'),home],'self-engine-'+mode+'.log')
 run(flags+san+['-DTIO_CAPTURE_TESTING','-fobjc-arc','-framework','Foundation',*core,src/'OriginalExceptionTests.m','-o',out/('exception-'+mode)],'compile-exception-'+mode+'.log')
 home=Path(tempfile.mkdtemp(prefix='public-exception-',dir=out));run([out/('exception-'+mode),home],'exception-'+mode+'.log')
 run(flags+san+[*core,test/'StartTests.c','-o',out/('start-'+mode)],'compile-start-'+mode+'.log')
 for directory_mode in [0o700,0o755,0o770]:
  home=Path(tempfile.mkdtemp(prefix='public-start-',dir=out));home.chmod(directory_mode)
  run([out/('start-'+mode),home,'normal' if directory_mode==0o700 else 'unsafe-mode'],'start-'+mode+'-'+oct(directory_mode)+'.log')
 run(flags+san+[test/'StartFaultTests.c',src/'SchemaPolicy.c',lab/'schema-observer/SchemaView.c',lab/'memory-copy/MemoryCopy.c','-o',out/('start-fault-'+mode)],'compile-start-fault-'+mode+'.log')
 home=Path(tempfile.mkdtemp(prefix='public-start-fault-',dir=out));run([out/('start-fault-'+mode),home],'start-fault-'+mode+'.log')
 print('MODE_PASS',mode,'activation dirfd no-file-before-open keygate one-shot ABI plus old policy attacks exception',flush=True)
# Independent production links, loaded only for the pure blocked status getter.
for label in ['a','b']:
 dylib=out/('CaptureProduction-'+label+'.dylib');run(flags+['-dynamiclib','-Wl,-exported_symbol,_TIOCaptureGetState',*core,'-o',dylib],'compile-production-'+label+'.log')
 exports=run(['xcrun','nm','-gU',dylib],'production-exports-'+label+'.log').stdout
 imports=run(['xcrun','nm','-u',dylib],'production-imports-'+label+'.log').stdout
 assert [line.split()[-1] for line in exports.splitlines() if line.strip()]==['_TIOCaptureGetState']
 assert all(name not in exports for name in ['TIOCaptureStartInternal','TIOCaptureBindOriginalInternal','TIOCaptureForward','TIOCaptureFixture','constructor','arm'])
 assert all(name not in imports for name in ['dlsym','objc','NSUser'])
 loaded=ctypes.CDLL(str(dylib));loaded.TIOCaptureGetState.restype=ctypes.c_int;assert loaded.TIOCaptureGetState()==0
 for name in ['TIOCaptureStartInternal','TIOCaptureBindOriginalInternal','TIOCaptureForward','TIOCaptureFixtureBind']:
  try:getattr(loaded,name)
  except AttributeError:pass
  else:raise AssertionError('Hidden/test symbol dynamically accessible '+name)
 assert all('public-' in str(x) or 'attacks-' in str(x) for x in out.glob('**/captured_schema_projection.json'))
print('TWO_PRODUCTION_LINKS_BLOCKED_HIDDEN_NO_DYNAMIC_BIND_PASS',flush=True)
sdk=subprocess.check_output(['xcrun','--sdk','iphoneos','--show-sdk-path'],text=True).strip()
run(['xcrun','--sdk','iphoneos','clang','-target','arm64-apple-ios15.0','-isysroot',sdk,'-std=c11','-Wall','-Wextra','-Werror','-dynamiclib','-Wl,-exported_symbol,_TIOCaptureGetState',*core,'-o',out/'CaptureArm64Internal.dylib'],'arm64-link.log')
exports=run(['xcrun','nm','-gU',out/'CaptureArm64Internal.dylib'],'arm64-exports.log').stdout;assert [line.split()[-1] for line in exports.splitlines() if line.strip()]==['_TIOCaptureGetState'];assert 'TIOCaptureStartInternal' not in exports and 'TIOCaptureBindOriginalInternal' not in exports and 'TIOCaptureFixture' not in exports and 'TIOCaptureForward' not in exports
for e in entries:assert hashlib.sha256((out/'source'/e['path']).read_bytes()).hexdigest()==e['sha256']
print('ARM64_HIDDEN_BIND_LINK_PASS_NO_RUNTIME',flush=True)
print('TASK97_ALL_PASS source_manifest_sha256='+hashlib.sha256((out/'source-manifest.json').read_bytes()).hexdigest(),flush=True)
