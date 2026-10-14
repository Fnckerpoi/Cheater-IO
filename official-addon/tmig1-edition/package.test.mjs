import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import {patch} from './patch-ios106-route.mjs';
import {validateOptions,validateResearchPair,entitlementsFor} from './package.mjs';
import {inspectedHost} from './host-compatibility.mjs';

test('only matching 1.0.6/218 UUID accepted as the new host',()=>{
 const i={CFBundleIdentifier:'com.rayneo.venus.pub',CFBundleExecutable:'Runner',CFBundleShortVersionString:'1.0.6',CFBundleVersion:'218'};
 assert.ok(inspectedHost(i,'98a03b57e8163ba19778e55fabe8d762'));
 assert.equal(inspectedHost({...i,CFBundleVersion:'219'},'98a03b57e8163ba19778e55fabe8d762'),null);
 assert.equal(inspectedHost(i,'0'.repeat(32)),null);
});
test('options do not accept old OTA candidates or implicit firmware',()=>{
 const o={app:'/example/Runner.app',addon:'/example/addon.dylib',profile:'/example/profile.mobileprovision',out:'/example/new-tmig1-package',identity:'A'.repeat(40),device:'TEST-DEVICE-01',bundle:'com.rayneo.venus.pub','experimental-ota':'TMIG1',firmware:'/example/tmig1.zip'};
 validateOptions(o);
 assert.throws(()=>validateOptions({...o,'experimental-ota':'TFP1'}));
 assert.throws(()=>validateOptions({...o,firmware:undefined}));
 assert.throws(()=>validateOptions({...o,bundle:'example.other'}));
});
test('profile checks and entitlements preserve only granted rights',()=>{
 const o={identity:'A'.repeat(40),device:'TEST-DEVICE-01',bundle:'com.rayneo.venus.pub'};
 const p={certs:[o.identity],devices:[o.device],expires:'2099-01-01T00:00:00Z',entitlements:{'application-identifier':'EXAMPLE.*','keychain-access-groups':['EXAMPLE.*']}};
 const e=entitlementsFor(p,o);
 assert.equal(e['application-identifier'],'EXAMPLE.com.rayneo.venus.pub');
 assert.equal(e['aps-environment'],undefined);
 assert.throws(()=>entitlementsFor({...p,devices:[]},o));
 assert.throws(()=>entitlementsFor({...p,expires:'2000-01-01T00:00:00Z'},o));
});
test('unknown host bytes and unmatched firmwares fail closed',()=>{
 assert.throws(()=>patch(Buffer.from('not-a-host')));
 assert.throws(()=>validateResearchPair('_CEPhoneSendCards _TFFocusConsume','TMIG1',Buffer.alloc(32)));
 assert.throws(()=>validateResearchPair('','TMIG1',Buffer.alloc(32)));
});
test('optional exact real-input acceptance without signing, installation or device IO',()=>{
 const host=process.env.TMIG1_TEST_HOST_IMAGE, firmware=process.env.TMIG1_TEST_FIRMWARE;
 if(!host||!firmware)return;
 const input=fs.readFileSync(host), result=patch(input);
 assert.equal(createHash('sha256').update(result.output).digest('hex'),'532bdfc1971668df113434de54f69cede8bab3f61c7d82d028d62c54f5ca5940');
 assert.equal(result.report.changes.length,2);
 assert.equal(result.report.objectHeadersChanged,false);
 validateResearchPair('_CEPhoneSendCards _TFFocusConsume','TMIG1',fs.readFileSync(firmware));
 const wrong=Buffer.from(input);wrong[0]^=1;assert.throws(()=>patch(wrong));
 const bad=fs.readFileSync(firmware);bad[bad.length-1]^=1;assert.throws(()=>validateResearchPair('_CEPhoneSendCards _TFFocusConsume','TMIG1',bad));
});
