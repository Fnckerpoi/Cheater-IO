import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {officialOtaInput} from './official-ota-test-input.mjs';
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'official-ota-input-'));
try {
 const legacy=path.join(temp,'legacy','libapp.so'),explicit=path.join(temp,'explicit.so');
 fs.mkdirSync(path.dirname(legacy),{recursive:true});fs.writeFileSync(legacy,'legacy');fs.writeFileSync(explicit,'explicit');
 assert.equal(officialOtaInput({},legacy).toString(),'legacy');
 assert.equal(officialOtaInput({TURBO_OFFICIAL_LIBAPP:explicit},legacy).toString(),'explicit');
 assert.throws(()=>officialOtaInput({TURBO_OFFICIAL_LIBAPP:path.join(temp,'absent.so')},legacy),/TURBO_OFFICIAL_LIBAPP does not exist/);
 assert.throws(()=>officialOtaInput({},path.join(temp,'absent','libapp.so')),/Missing official Android 1.0.5 libapp.so fixture/);
 console.log('Official OTA input location tests passed; SHA gate remains in OfficialOtaSourceTest.');
} finally {fs.rmSync(temp,{recursive:true,force:true});}
