from pathlib import Path
import tempfile,subprocess,json,sys,os,stat
binary=Path(sys.argv[1]);output=Path(sys.argv[2]);records=[]
def run(mode,prepare=None):
 root=Path(tempfile.mkdtemp(prefix='public-fixture-home-',dir=output));sentinel=output/(root.name+'-sentinel');sentinel.write_bytes(b'UNCHANGED_PUBLIC_SENTINEL')
 if prepare:prepare(root,sentinel)
 p=subprocess.run([str(binary),str(root),mode],capture_output=True,text=True,timeout=10);assert p.returncode==0,(mode,p.stdout,p.stderr)
 assert sentinel.read_bytes()==b'UNCHANGED_PUBLIC_SENTINEL'
 records.append({'mode':mode,'exit':p.returncode,'stdout':p.stdout.strip()})
 if mode in ['normal']:
  dest=root/'Documents/ManuscriptLab/captured_schema_projection.json';data=dest.read_bytes();obj=json.loads(data);assert len(data)<=65536 and obj['actual_manuscript_schema_known'] is False and obj['schema'][0]['name']=='PublicFixture';assert stat.S_IMODE(dest.stat().st_mode)==0o600
 return root
for mode in ['normal','invalid','utf8','malformed','duplicate','oversize','expired','rollback']:run(mode)
def directories(root):
 d=root/'Documents/ManuscriptLab';d.mkdir(parents=True);return d
run('exists',lambda r,s:(directories(r)/'captured_schema_projection.json').write_bytes(b'PREEXISTING_PUBLIC_SCHEMA'))
run('final-symlink',lambda r,s:(directories(r)/'captured_schema_projection.json').symlink_to(s))
run('pending-exists',lambda r,s:(directories(r)/'.captured_schema_projection.pending').write_bytes(b'PREEXISTING_PENDING'))
run('pending-symlink',lambda r,s:(directories(r)/'.captured_schema_projection.pending').symlink_to(s))
run('parent-symlink',lambda r,s:(r/'Documents').symlink_to(output))
(output/'attack-results.json').write_text(json.dumps(records,indent=2));print('CAPTURE_ATTACK_MATRIX_PASS: 13 fresh processes, preexisting files/symlinks/parents unchanged, no record content')

def mode_case(mode,expected_success):
 root=Path(tempfile.mkdtemp(prefix='public-private-mode-',dir=output));docs=root/'Documents';docs.mkdir(mode=0o755);docs.chmod(0o755);lab=docs/'ManuscriptLab';lab.mkdir(mode=mode);lab.chmod(mode);sentinel=lab/'public-sentinel';sentinel.write_bytes(b'UNCHANGED_MODE_SENTINEL')
 p=subprocess.run([str(binary),str(root),'normal' if expected_success else 'unsafe-mode'],capture_output=True,text=True,timeout=10);assert p.returncode==0,(oct(mode),p.stdout,p.stderr)
 assert stat.S_IMODE(lab.stat().st_mode)==mode and stat.S_IMODE(docs.stat().st_mode)==0o755 and sentinel.read_bytes()==b'UNCHANGED_MODE_SENTINEL'
 assert (lab/'captured_schema_projection.json').exists()==expected_success
 return root
mode_case(0o755,False);mode_case(0o770,False);mode_case(0o700,True)
print('R1_PRIVATE_OUTPUT_MODE_PASS: parent0755 accepted, dedicated0755/0770 refused unchanged, dedicated0700 accepted')
