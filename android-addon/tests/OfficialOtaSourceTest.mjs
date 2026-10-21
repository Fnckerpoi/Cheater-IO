import assert from 'node:assert/strict';
import {patchAndroidSource,SOURCE_PROFILES} from '../official-ota-source.mjs';
import {officialOtaInput} from './official-ota-test-input.mjs';
let original;
try {original=officialOtaInput();} catch(error) {console.error('Official OTA source test prerequisites missing: '+error.message);process.exitCode=1;throw error;}
const {output,report}=patchAndroidSource(original);
assert.equal(output.length,original.length);assert.equal(report.changes.length,2);
const slots=SOURCE_PROFILES[report.inputSHA].slots;
for(const s of slots){assert.equal(output[s.offset-1],original[s.offset-1]);assert.equal(output.subarray(s.offset,s.offset+s.text.length).toString('ascii'),s.replacement);}
for(let i=0;i<original.length;i++)if(!slots.some(s=>i>=s.offset&&i<s.offset+s.text.length))assert.equal(output[i],original[i]);
assert.deepEqual(patchAndroidSource(original).output,output);
assert.throws(()=>patchAndroidSource(output));
const corrupt=Buffer.from(original);corrupt[100]^=1;assert.throws(()=>patchAndroidSource(corrupt));
assert.throws(()=>patchAndroidSource(original.subarray(1)));
console.log('Android source routing: fixed input, length/hash, byte allowlist, wrong-input and repeated-patch tests passed');
