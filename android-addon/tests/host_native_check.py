import pathlib,zipfile
host=pathlib.Path(__file__).resolve().parents[2]/'artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
with zipfile.ZipFile(host) as z:
 native=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
print('host_native_count',len(native))
for n in native[:20]:print(' ',n)
