from pathlib import Path
import argparse,subprocess,json,hashlib,shutil
import patch_flutter as patch
p=argparse.ArgumentParser();p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out;r=Path(__file__).resolve().parent
if not out.is_absolute() or out.exists():raise SystemExit('New absolute output required')
out.mkdir(parents=True,mode=0o700);frameworks=out/'Frameworks';unpatched=frameworks/'FlutterOriginal.framework/Flutter';patched=frameworks/'Flutter.framework/Flutter';unpatched.parent.mkdir(parents=True);patched.parent.mkdir()
cc=['clang','-Wall','-Wextra','-Werror','-Wl,-no_adhoc_codesign']
subprocess.run(cc+['-dynamiclib',str(r/'OrderState.c'),'-install_name',str(frameworks/'libOrder.dylib'),'-o',str(frameworks/'libOrder.dylib')],check=True)
subprocess.run(cc+['-dynamiclib',str(r/'BootstrapMarker.c'),str(frameworks/'libOrder.dylib'),'-install_name','@rpath/TurboIOStageBObserver.dylib','-o',str(frameworks/'TurboIOStageBObserver.dylib')],check=True)
subprocess.run(cc+['-dynamiclib',str(r/'FlutterFixture.c'),str(frameworks/'libOrder.dylib'),'-Wl,-headerpad,4096','-install_name',patch.INSTALL,'-o',str(unpatched)],check=True)
subprocess.run(cc+[str(r/'RunnerFixture.c'),'-o',str(out/'runner')],check=True)
red=subprocess.run([str(out/'runner'),'late',str(unpatched),str(frameworks/'TurboIOStageBObserver.dylib')],capture_output=True,text=True)
(out/'actual-late-red.log').write_text(red.stdout+red.stderr)
if red.returncode==0:raise SystemExit('Late-bootstrap control unexpectedly passed')
patch.write_copy(unpatched,patched,fixture=True)
green=subprocess.run([str(out/'runner'),'dependency',str(patched),str(frameworks/'TurboIOStageBObserver.dylib')],capture_output=True,text=True)
(out/'actual-strong-green.log').write_text(green.stdout+green.stderr)
if green.returncode:raise SystemExit(green.stdout+green.stderr)
print('ACTUAL_LATE_RED_EXIT',red.returncode);print(green.stdout.strip())
print(subprocess.check_output(['otool','-L',str(patched)],text=True))

subprocess.run(cc+[str(r/'RunnerDirectFixture.c'),str(patched),'-Wl,-rpath,'+str(frameworks),'-o',str(out/'runner-direct')],check=True)
direct=subprocess.run([str(out/'runner-direct')],capture_output=True,text=True)
(out/'actual-direct-runner-green.log').write_text(direct.stdout+direct.stderr)
if direct.returncode:raise SystemExit(direct.stdout+direct.stderr)
print(direct.stdout.strip())
