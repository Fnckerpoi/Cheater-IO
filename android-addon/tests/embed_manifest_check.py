import pathlib,sys,zipfile,struct
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import embed_manifest as m, background_manifest as ax
from tmig1_coexist import parse
host=pathlib.Path(__file__).resolve().parents[2]/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
with zipfile.ZipFile(host) as z: before=z.read('AndroidManifest.xml')
after=m.patch(before)
old=[c for c in ax._chunks(before) if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
new=[c for c in ax._chunks(after) if struct.unpack_from('<H',c)[0] in (0x102,0x103)]
it=iter(new);preserved=all(any(v==c for v in it) for c in old)
_,_,_,nodes=parse(after)
acts=[attrs for _,tag,attrs in nodes if tag=='activity' and any(v['name']=='name' and v['value']==m.ACTIVITY for v in attrs)]
print('activity',m.ACTIVITY)
print('idempotent',m.patch(after)==after)
print('nodes_before_after',len(old),len(new))
print('host_nodes_preserved',preserved)
print('embedded_activity_count',len(acts))
print('exported',[v for v in acts[0] if v['name']=='exported'] if acts else None)
assert len(acts)==1 and preserved and len(new)==len(old)+2
print('OK')
