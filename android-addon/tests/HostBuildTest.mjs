import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {build,publish} from '../host-build.mjs';
import {profileFor,assertBuildProfile,verifyHost} from '../host-profiles.mjs';
import {packageApk} from '../package.mjs';
import {packageWindows} from '../package-tmig1-windows.mjs';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const profile=profileFor('770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'host-build-test-'));
const source=path.join(tmp,'fixture.apk'),dex=path.join(tmp,'fixture.dex');
fs.writeFileSync(source,'fixture input (not a real APK)');fs.writeFileSync(dex,'fixture dex');
const fixture=()=>{
 const files={};
 for(const [descriptor,line] of profile.baseline){const f=descriptor.slice(1,-1)+'.smali';files[f]=(files[f]||'.class public '+descriptor+'\n')+(line.startsWith('"')?'const-string v0, '+line:line)+'\n'+(line.startsWith('.method')?'    .locals 0\n    return-void\n.end method\n':'');}
 return files;
};
const api={profileFor:()=>profile,assertBuildProfile,verifyHost};
let fixtures=fixture(),calls=[],lastHost;
function runner(bin,args,options){
 calls.push([bin,args]);
 if(args.includes('--version')||args.includes('-version'))return bin+' fixture-version';
 const a=args[0]==='-jar'?args.slice(2):args;
 if(a[0]==='d'){lastHost=a[a.indexOf('--output')+1];assert.equal(fs.existsSync(lastHost),false);for(const [f,text] of Object.entries(fixtures)){const file=path.join(lastHost,'smali_classes2',f);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,text);}return '';}
 if(a[0]==='b'){fs.writeFileSync(a[a.indexOf('-o')+1],'fixture assembled');return '';}
 if(path.basename(a[0])==='assemble_apk.py'){assert.equal(options.env.PYTHONUTF8,'1');fs.writeFileSync(a[4],'fixture verified candidate');return '';}
 if(path.basename(a[0])==='verify_apk.py'){assert.equal(options.env.PYTHONUTF8,'1');return '';}
 throw new Error('Unexpected runner invocation');
}
const options={source,dex,workParent:path.join(tmp,'work'),runner};
try {
 for(const entry of [()=>packageApk(source,{options:{runner}}),()=>packageWindows([source,tmp,dex,'java','apktool.jar','python'],{options:{runner}})]){assert.throws(entry,/Unsupported/);assert.equal(calls.length,0);}
 assert.equal(fs.existsSync(options.workParent),false);
 for(const status of ['draft','revoked']){const p={...profile,status};assert.throws(()=>build(options,{...api,profileFor:()=>p}),/not approved/);assert.equal(calls.length,0);}
 assert.throws(()=>build(options,{...api,profileFor:()=>({...profile,adapterId:'unknown'})}),/adapter/);assert.equal(calls.length,0);
 fixtures=fixture();delete fixtures['H7/c$c.smali'];
 assert.throws(()=>build(options,api),/Missing|ABI/);
 assert.equal(calls.some(([,a])=>a[0]==='b'),false);
 assert.equal(fs.readdirSync(options.workParent).length,1);
 assert.equal(fs.existsSync(path.join(tmp,'published.apk')),false);
 fixtures=fixture();fixtures['com/rayneo/rayneo_venus_sdk_plugin/j.smali']+='\n.method public final z(Ljava/lang/String;Ljava/util/Map;)V\n.end method\n';
 calls=[];assert.throws(()=>build(options,api),/ABI mismatch/);
 // Simulate an ABI provider admitting a malformed late hook to exercise plan atomicity.
 const laxApi={...api,verifyHost:host=>({listener:path.join(host,'smali_classes2/H7/c$c.smali'),activity:path.join(host,'smali_classes2/com/rayneo/venus/MainActivity.smali'),event:path.join(host,'smali_classes2/com/rayneo/rayneo_venus_sdk_plugin/j.smali'),ota:{queue:path.join(host,'smali_classes2/E3/V.smali'),sender:path.join(host,'smali_classes2/E3/u.smali')}})};
 assert.throws(()=>build(options,laxApi),/Event signature mismatch/);
 for(const [f,text] of Object.entries(fixtures))assert.equal(fs.readFileSync(path.join(lastHost,'smali_classes2',f),'utf8'),text,'No writes before the entire plan succeeds');
 assert.equal(calls.some(([,a])=>a[0]==='b'),false);
 fixtures=fixture();calls=[];
 const first=build(options,api);
 const listener=fs.readFileSync(path.join(first.work,'host/smali_classes2/H7/c$c.smali'),'utf8');
 for(const name of ['onAsrResult','onNlpResult','onResponseComplete'])assert.match(listener,new RegExp('turboioOriginal_'+name));
 const defaultEntry=packageApk(source,{outputDirectory:path.join(tmp,'default-entry'),options:{dex,runner},hostApi:api});
 const windowsEntry=packageWindows([source,path.join(tmp,'windows-entry'),dex,'java','apktool.jar','python'],{options:{runner},hostApi:api});
 const defaultReport=JSON.parse(fs.readFileSync(defaultEntry.receipt));
 const windowsReport=JSON.parse(fs.readFileSync(windowsEntry.receipt));
 for(const field of ['inputSha256','outputSha256','profileSha256','adapter','checks'])assert.deepEqual(defaultReport[field],windowsReport[field]);
 assert.equal(fs.existsSync(defaultEntry.output),true);assert.equal(fs.existsSync(windowsEntry.output),true);
 const second=build({...options,java:'java',apktool:'apktool.jar',python:'python'},api);
 assert.notEqual(first.work,second.work);
 for(const f of Object.keys(fixtures))assert.equal(fs.readFileSync(path.join(first.work,'host/smali_classes2',f),'utf8'),fs.readFileSync(path.join(second.work,'host/smali_classes2',f),'utf8'));
 const destination=path.join(tmp,'published.apk'),receipt=path.join(tmp,'receipt.json');
 publish(first,destination,receipt);
 const report=JSON.parse(fs.readFileSync(receipt));
 assert.equal(report.inputSha256,sha(fs.readFileSync(source)));
 assert.equal(report.outputSha256,sha(fs.readFileSync(destination)));
 assert.equal(report.profileSha256,sha(JSON.stringify(report.profile)));
 assert.equal(report.adapter,profile.adapterId);
 assert.equal(report.work,path.resolve(first.work));
 const receiptFailure=build(options,api),failedReceipt=path.join(tmp,'failed-receipt.json'),failedOutput=path.join(tmp,'failed-publish.apk');
 const realLink=fs.linkSync;
 try {
   fs.linkSync=(src,dst)=>{if(dst===failedReceipt)throw Object.assign(new Error('fixture ENOSPC'),{code:'ENOSPC'});return realLink(src,dst);};
   assert.throws(()=>publish(receiptFailure,failedOutput,failedReceipt),/ENOSPC/);
   assert.equal(fs.existsSync(failedReceipt),false);assert.equal(fs.existsSync(failedOutput),false);
 } finally {fs.linkSync=realLink;}
 const receiptRace=build(options,api),raceReceipt=path.join(tmp,'race-receipt.json'),raceOutput=path.join(tmp,'race.apk');
 try {
   fs.linkSync=(src,dst)=>{if(dst===raceReceipt){fs.writeFileSync(raceReceipt,'user receipt',{flag:'wx'});}return realLink(src,dst);};
   assert.throws(()=>publish(receiptRace,raceOutput,raceReceipt),/EEXIST/);
   assert.equal(fs.readFileSync(raceReceipt,'utf8'),'user receipt');assert.equal(fs.existsSync(raceOutput),false);
 } finally {fs.linkSync=realLink;}
 const apkFailure=build(options,api),rollbackReceipt=path.join(tmp,'rollback-receipt.json'),rollbackOutput=path.join(tmp,'rollback.apk');
 try {
   fs.linkSync=(src,dst)=>{if(dst===rollbackOutput)throw new Error('fixture APK link failed');return realLink(src,dst);};
   assert.throws(()=>publish(apkFailure,rollbackOutput,rollbackReceipt),/APK link failed/);
   assert.equal(fs.existsSync(rollbackReceipt),false);assert.equal(fs.existsSync(rollbackOutput),false);
 } finally {fs.linkSync=realLink;}
 assert.equal(report.toolVersions.apktool,'apktool fixture-version');
 assert.deepEqual([report.installed,report.flashed,report.runtimeValidated],[false,false,false]);
 assert.throws(()=>publish(second,destination,receipt),/overwrite/);
 assert.equal(sha(fs.readFileSync(destination)),report.outputSha256);
 const bad=path.join(tmp,'failed.apk');
 assert.throws(()=>build({...options,runner:(bin,args,opts)=>{if(path.basename(args[0])==='verify_apk.py')throw new Error('audit failed');return runner(bin,args,opts);}},api),/audit failed/);
 assert.equal(fs.existsSync(bad),false);
 fs.writeFileSync(second.candidate,'tampered');
 assert.throws(()=>publish(second,path.join(tmp,'tampered.apk'),path.join(tmp,'tampered.json')),/changed after verification/);
 assert.equal(fs.existsSync(path.join(tmp,'tampered.apk')),false);
 
 // Exercise 1.0.7 build pipeline in fixture runner
 const profile107 = profileFor('30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91');
 const fixture107 = () => {
   const files = {};
   for (const [descriptor, line] of profile107.baseline) {
     const f = descriptor.slice(1, -1) + '.smali';
     files[f] = (files[f] || '.class public ' + descriptor + '\n') + (line.startsWith('"') ? 'const-string v0, ' + line : line) + '\n' + (line.startsWith('.method') ? '    .locals 0\n    return-void\n.end method\n' : '');
   }
   return files;
 };
 fixtures = fixture107();
 calls = [];
 const api107 = { profileFor: () => profile107, assertBuildProfile, verifyHost };
 const build107 = build(options, api107);
 const listener107 = fs.readFileSync(path.join(build107.work, 'host/smali_classes2/q8/c$c.smali'), 'utf8');
 for (const name of ['onAsrResult', 'onNlpResult', 'onResponseComplete']) assert.match(listener107, new RegExp('turboioOriginal_' + name));
 const queue107 = fs.readFileSync(path.join(build107.work, 'host/smali_classes2/Y3/V.smali'), 'utf8');
 assert.match(queue107, /LY3\/V;->turboioOriginal_o\(LY3\/S;\)V/);
 const file107 = fs.readFileSync(path.join(build107.work, 'host/smali_classes2/Y3/u.smali'), 'utf8');
 assert.match(file107, /LY3\/u;->turboioOriginal_u\(Ljava\/io\/File;Ljava\/lang\/String;Lk4\/q;Ljava\/lang\/String;\)Ljava\/lang\/String;/);

 console.log('Host build fixtures passed: admission, all-or-nothing hook planning, shared entry gates, isolated directories, audit failure, receipt binding and overwrite refusal. No real APK build was performed.');
} finally {fs.rmSync(tmp,{recursive:true,force:true});}