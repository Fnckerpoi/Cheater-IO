import pathlib
import json
import inspect
import struct
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import tmig1_coexist as c
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/'artifacts/android-tmig1/public-unsigned-20261018-152656/build/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC-rebuilt-unsigned.apk'

class CoexistTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(SOURCE) as z: cls.original=z.read('AndroidManifest.xml')

    def test_exact_attribute_delta_components_and_callbacks(self):
        result,changes,components=c.patch(self.original)
        before=c.parse(self.original)[3]; after=c.parse(result)[3]
        actual=[]
        for (ci,tag,attrs),(cj,other,newattrs) in zip(before,after):
            self.assertEqual((ci,tag),(cj,other));self.assertEqual(len(attrs),len(newattrs))
            for a,b in zip(attrs,newattrs):
                self.assertEqual((a['name'],a['namespace']),(b['name'],b['namespace']))
                if (a['type'],a['value']) != (b['type'],b['value']): actual.append((ci,a['name'],a['value'],b['value']))
        self.assertEqual(actual,[(a['chunk'],a['attribute'],a['before'],a['after']) for a in changes])
        self.assertEqual(sum(x['attribute']=='authorities' for x in changes),9)
        self.assertEqual(sum(x['tag']=='permission' for x in changes),8)
        self.assertIn('com.rayneo.venus.VenusApplication',[x['class'] for x in components])
        self.assertIn('com.rayneo.venus.MainActivity',[x['class'] for x in components])
        self.assertFalse(any(x['after'] in {'rayneo.venus.app','signinwithapple'} for x in changes))
        self.assertEqual([a for _,t,aa in before for a in aa if t in c.COMPONENTS and a['name'] in {'name','targetActivity'}],[a for _,t,aa in after for a in aa if t in c.COMPONENTS and a['name'] in {'name','targetActivity'}])

    def mutate(self,chunk,attr,new):
        chunks,pi,strings,nodes=c.parse(self.original)
        chunks[pi],_,indices=c.ax._append_pool_strings(chunks[pi],strings,[new])
        entry=next(a for ci,_,aa in nodes if ci==chunk for a in aa if a['name']==attr)
        ch=bytearray(chunks[chunk]);struct.pack_into('<I',ch,entry['offset']+8,indices[new]);struct.pack_into('<I',ch,entry['offset']+16,indices[new]);chunks[chunk]=bytes(ch)
        body=b''.join(chunks);return struct.pack('<HHI',3,8,len(body)+8)+body

    def test_missing_identity_fails_closed(self):
        for change in c.plan(self.original)[0]:
            if change['attribute']=='label':continue
            with self.subTest(change=change),self.assertRaises(ValueError): c.patch(self.mutate(change['chunk'],change['attribute'],'unexpected.identity'))

    def test_relative_component_rejected(self):
        _,_,_,nodes=c.parse(self.original)
        ci=next(ci for ci,t,aa in nodes if t=='application')
        with self.assertRaises(ValueError):c.patch(self.mutate(ci,'name','.VenusApplication'))

    def test_payload_audit_rejects_extra_duplicate_dex_and_manifest_changes(self):
        manifest=c.patch(self.original)[0]
        with tempfile.TemporaryDirectory() as temp:
            temp=pathlib.Path(temp);before=temp/'before.apk';after=temp/'after.apk'
            def write(path,m,d=b'dex\nfixed',extra=False,duplicate=False):
                with zipfile.ZipFile(path,'w') as z:
                    z.writestr('AndroidManifest.xml',m);z.writestr('classes4.dex',d)
                    if extra:z.writestr('extra',b'bad')
                    if duplicate:z.writestr('classes4.dex',d)
            write(before,self.original)
            with patch.object(c,'PIN',c.sha(before)):
                write(after,manifest);self.assertTrue(c.manifest_audit(before,after)['allOtherPayloadsByteIdentical'])
                for kwargs in [{'d':b'bad'},{'extra':True},{'duplicate':True}]:
                    write(after,manifest,**kwargs)
                    with self.assertRaises(ValueError):c.manifest_audit(before,after)
                write(after,self.mutate(3,'versionName','bad'))
                with self.assertRaises(ValueError):c.manifest_audit(before,after)

    def test_profile_verification_rejects_tampered_bindings(self):
        fields={'schema':'tmig1-coexist-baseline-v1','sourceSha256':'source','publicSha256':c.PUBLIC_PIN,'manifestSha256':c.MANIFEST_PIN,'classes4Sha256':'dex','buildReportSha256':'build','auditReportSha256':'audit','public':'public.apk','buildReport':'build.json','auditReport':'audit.json'}
        with tempfile.TemporaryDirectory() as temp:
            profile=pathlib.Path(temp)/'profile.json'
            profile.write_text(json.dumps(fields))
            with patch.object(c,'baseline_evidence',return_value=fields) as rerun:
                self.assertEqual(c.verify_profile(SOURCE,profile),'source');self.assertEqual(rerun.call_count,1)
                for key in fields:
                    wrong=dict(fields);wrong[key]='tampered';profile.write_text(json.dumps(wrong))
                    with self.subTest(key=key),self.assertRaises(ValueError):c.verify_profile(SOURCE,profile)

    def test_baseline_rejects_wrong_root_or_report_before_subprocess(self):
        with tempfile.TemporaryDirectory() as temp:
            work=pathlib.Path(temp);public=work/'public';public.write_bytes(b'root');source=work/'source';source.write_bytes(b'source')
            report=work/'report';report.write_text('{}')
            with patch.object(c,'run') as run:
                with self.assertRaises(ValueError):c.baseline_evidence(source,public,report,report,work)
                with patch.object(c,'PUBLIC_PIN',c.sha(public)):
                    with self.assertRaises(ValueError):c.baseline_evidence(source,public,report,report,work)
                run.assert_not_called()

    def test_baseline_reruns_audit_then_rejects_manifest_or_extra_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            work=pathlib.Path(temp);public=work/'public.apk';source=work/'source.apk';report=work/'report.json'
            for path,dex in [(public,b'dex\nold'),(source,b'dex\nnew')]:
                with zipfile.ZipFile(path,'w') as z:z.writestr('AndroidManifest.xml',self.original);z.writestr('classes4.dex',dex)
            root=c.sha(public)
            def reports():
                value={'mode':'public-dex-update','sourceSha256':root,'outputSha256':c.sha(source),'changedPayloads':['classes4.dex'],'allOtherPayloadsByteIdentical':True,'manifestChanged':False,'packageNameChanged':False,'signed':False,'installed':False}
                report.write_text(json.dumps(value));return value
            value=reports()
            def audit(command,log):
                self.assertEqual(command[2],'public-audit')
                self.assertEqual(command[3:5],[public,source])
                with zipfile.ZipFile(public) as a,zipfile.ZipFile(source) as b:
                    changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
                    if changed!=['classes4.dex'] or a.namelist()!=b.namelist():raise RuntimeError('audit rejected')
                pathlib.Path(command[-1]).write_text(json.dumps(value))
            with patch.object(c,'PUBLIC_PIN',root),patch.object(c,'run',side_effect=audit) as rerun:
                c.baseline_evidence(source,public,report,report,work);self.assertEqual(rerun.call_count,1)
                with patch.object(c,'MANIFEST_PIN','wrong'),self.assertRaises(ValueError):c.baseline_evidence(source,public,report,report,work)
                with zipfile.ZipFile(source,'a') as z:z.writestr('extra',b'bad')
                value=reports()
                with self.assertRaises(RuntimeError):c.baseline_evidence(source,public,report,report,work)

    def test_existing_signer_never_creates_secrets(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=pathlib.Path(temp)/'missing'
            with self.assertRaises(ValueError):c.existing_signer(folder)
            self.assertFalse(folder.exists())
            folder.mkdir();(folder/'tmig1-research.p12').write_bytes(b'key');(folder/'password.txt').write_bytes(b'private')
            before={p.name:p.read_bytes() for p in folder.iterdir()}
            c.existing_signer(folder)
            self.assertEqual(before,{p.name:p.read_bytes() for p in folder.iterdir()})
        self.assertNotIn('genkeypair',inspect.getsource(c))
        self.assertNotIn('token_urlsafe',inspect.getsource(c))

    def test_signer_certificate_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            work=pathlib.Path(temp)
            def export(command,log):
                self.assertNotIn('-genkeypair',command)
                (work/'research-signer.der').write_bytes(b'wrong certificate')
            with patch.object(c,'run',side_effect=export),self.assertRaises(ValueError):
                c.verify_signer(pathlib.Path('java'),pathlib.Path('key'),pathlib.Path('password'),work)

    def test_pkcs12_uses_one_password_file_reference(self):
        args=c.signing_command('java','signer.jar','key.p12',pathlib.Path('local/password.txt'),'signed.apk','aligned.apk')
        self.assertEqual(sum(str(x).startswith('file:') for x in args),1)
        self.assertNotIn('--key-pass',args)
        self.assertEqual(args[args.index('--ks-pass')+1],'file:'+str(pathlib.Path('local/password.txt')))

    def test_signing_audit_rejects_payload_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            a=pathlib.Path(temp)/'a.apk';b=pathlib.Path(temp)/'b.apk'
            for path,value in [(a,b'original'),(b,b'changed')]:
                with zipfile.ZipFile(path,'w') as z:z.writestr('classes4.dex',value)
            with self.assertRaises(ValueError):c.signed_audit(a,b)

if __name__=='__main__':unittest.main()
