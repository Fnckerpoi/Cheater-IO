import assert from 'node:assert/strict';
import {parsePackageArgs} from '../package.mjs';
const parsed=parsePackageArgs(['source.apk','--java','C:/jdk/java.exe','--apktool','C:/tools/apktool.jar','--python','C:/python/python.exe','--dex','addon.dex','--output-dir','artifacts/run']);
assert.deepEqual(parsed,{source:'source.apk',outputDirectory:'artifacts/run',options:{java:'C:/jdk/java.exe',apktool:'C:/tools/apktool.jar',python:'C:/python/python.exe',dex:'addon.dex'}});
const embedded=parsePackageArgs(['source.apk','--payload-apk','aa.apk','--payload-sidecar','payload.json']);
assert.deepEqual(embedded.options,{payloadApk:'aa.apk',payloadSidecar:'payload.json'});
for(const args of [['--payload-apk','aa.apk'],['--payload-sidecar','payload.json'],['--payload-apk','','--payload-sidecar','payload.json']]) assert.throws(()=>parsePackageArgs(['source.apk',...args]));
for(const args of [[],['a.apk','extra.apk'],['a.apk','--java','java'],['a.apk','--apktool','tool.jar'],['a.apk','--python','']]) assert.throws(()=>parsePackageArgs(args));
console.log('package CLI options validated');
