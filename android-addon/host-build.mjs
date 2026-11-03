import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {profileFor,assertBuildProfile,verifyHost} from './host-profiles.mjs';
import {queueHookFor,fileHookFor,queueHook,fileHook,EVENT_GUARD} from './ota-hooks.mjs';

const root=path.dirname(fileURLToPath(import.meta.url));
const sha256=b=>crypto.createHash('sha256').update(b).digest('hex');
function wrapText(text,name,signature,args) {
  const original='turboioOriginal_'+name;
  const header='.method public final '+name+signature;
  if(text.includes(original+'(')) {
    const start=text.indexOf(header+'\n');
    if(start<0)throw new Error('Generated wrapper missing');
    const end=text.indexOf('.end method',start);
    if(end<0)throw new Error('Truncated wrapper');
    text=text.slice(0,start)+text.slice(end+'.end method'.length);
  } else {
    if(text.split(header).length!==2)throw new Error('Method signature mismatch: '+name);
    text=text.replace(header,'.method public final '+original+signature);
  }
  const className=text.match(/^\.class[^\n]* (L[^;]+;)$/m)?.[1];
  if(!className)throw new Error('Class header missing');
  const invoke='invoke-virtual/range {'+args+'}, '+className+'->'+original+signature;
  const map={onAsrResult:'dispatchAsr(Ljava/lang/Object;Ljava/lang/String;ZLjava/lang/String;)V',onNlpResult:'dispatchNlp(Ljava/lang/Object;Ljava/lang/Object;)V',onResponseComplete:'dispatchComplete(Ljava/lang/Object;)V',onPostResume:'install(Landroid/app/Activity;)V'};
  const bridge='invoke-static/range {'+args+'}, Lcom/turboio/addon/TurboAddon;->'+map[name];
  const body=name!=='onPostResume'? '    :turboio_try\n    '+bridge+'\n    :turboio_try_end\n    return-void\n    :turboio_error\n    move-exception v0\n    '+invoke+'\n    return-void' : '    '+invoke+'\n    :turboio_try\n    '+bridge+'\n    :turboio_try_end\n    return-void\n    :turboio_error\n    move-exception v0\n    return-void';
  return text+'\n'+header+'\n    .locals 1\n'+body+'\n    .catch Ljava/lang/Throwable; {:turboio_try .. :turboio_try_end} :turboio_error\n.end method\n';
}
export function planHookWrites(abi, profile = { version: '1.0.5' }) {
  const writes=new Map();
  const transform=(file,fn)=>{const source=writes.get(file)??fs.readFileSync(file,'utf8');writes.set(file,fn(source));};
  transform(abi.listener,t=>wrapText(t,'onAsrResult','(Ljava/lang/String;ZLjava/lang/String;)V','p0 .. p3'));
  transform(abi.listener,t=>wrapText(t,'onNlpResult','(Lcom/rayneo/airuntime/controller/NlpResult;)V','p0 .. p1'));
  transform(abi.listener,t=>wrapText(t,'onResponseComplete','()V','p0 .. p0'));
  transform(abi.activity,t=>wrapText(t,'onPostResume','()V','p0 .. p0'));
  const eventHeader='.method public final z(Ljava/lang/String;Ljava/util/Map;)V';
  transform(abi.event,source=>{
    let t=source;
    if(t.includes('turboioOriginal_z(')){const start=t.indexOf(eventHeader+'\n');if(start<0)throw new Error('Missing event wrapper');const end=t.indexOf('.end method',start);if(end<0)throw new Error('Truncated event wrapper');t=t.slice(0,start)+t.slice(end+11);}
    else {if(t.split(eventHeader).length!==2)throw new Error('Event signature mismatch');t=t.replace(eventHeader,'.method public final turboioOriginal_z(Ljava/lang/String;Ljava/util/Map;)V');}
    const eventClass = source.match(/^\.class[^\n]* (L[^;]+;)$/m)?.[1] || 'Lcom/rayneo/rayneo_venus_sdk_plugin/j;';
    return t+'\n'+eventHeader+'\n    .locals 1\n'+(abi.ota?EVENT_GUARD:'')+'\n    invoke-virtual {p0, p1, p2}, '+eventClass+'->turboioOriginal_z(Ljava/lang/String;Ljava/util/Map;)V\n    :turboio_nav_try\n    invoke-static {p1, p2}, Lcom/turboio/addon/NativeTransfer;->event(Ljava/lang/String;Ljava/util/Map;)V\n    :turboio_nav_end\n    return-void\n    :turboio_nav_error\n    move-exception v0\n    return-void\n    .catch Ljava/lang/Throwable; {:turboio_nav_try .. :turboio_nav_end} :turboio_nav_error\n.end method\n';
  });
  if(abi.ota){transform(abi.ota.queue,t=>queueHookFor(profile,t));transform(abi.ota.sender,t=>fileHookFor(profile,t));}
  return writes;
}
// apktool emits smali/, smali_classes2/, ... into classes.dex, classes2.dex, ... so the
// patched classes' own directories determine which vendor DEX members must be replaced.
const SMALI_DIR=/^smali(?:_classes([2-9][0-9]*|1[0-9]+))?(?=$|[\\/])/;
export function hookDexSlots(writes,hostRoot){
  const slots=new Set();
  for(const file of writes.keys()){
    const relative=path.relative(path.resolve(hostRoot),path.resolve(file)).split(path.sep).join('/');
    const match=SMALI_DIR.exec(relative);
    if(!match)throw new Error('Hook class outside a smali directory: '+file);
    slots.add(match[1]?'classes'+match[1]+'.dex':'classes.dex');
  }
  if(!slots.size)throw new Error('No hook classes planned');
  return [...slots].sort((a,b)=>a.localeCompare(b,undefined,{numeric:true}));
}
export function runTool(bin,args,options={}) {
  const result=spawnSync(bin,args,{...options,encoding:'utf8'});
  if(result.error)throw result.error;
  if(result.status!==0)throw new Error('Tool failed: '+bin+' (status '+result.status+')');
  return (result.stdout||'')+(result.stderr||'');
}
const hostApi={profileFor,assertBuildProfile,verifyHost};
export function build({source,workParent,dex,java,apktool,python='python3',payloadApk,payloadSidecar,runner=runTool},api=hostApi) {
  if(Boolean(payloadApk)!==Boolean(payloadSidecar))throw new Error('Payload APK and sidecar must be supplied together');
  source=path.resolve(source);dex=path.resolve(dex);
  const sha=sha256(fs.readFileSync(source));
  const profile=api.profileFor(sha);
  api.assertBuildProfile(profile);
  const profileSnapshot=JSON.parse(JSON.stringify(profile));
  const profileSha256=sha256(JSON.stringify(profileSnapshot));
  const supported = [{version:'1.0.5',code:201},{version:'1.0.7',code:230},{version:'1.0.10',code:258}];
  if(!supported.some(s=>s.version===profile.version&&s.code===profile.code))throw new Error('INTEGRATION-07 OTA adapter only supports pinned 1.0.5 (201), 1.0.7 (230) and 1.0.10 (258)');
  fs.mkdirSync(workParent,{recursive:true});
  const work=fs.mkdtempSync(path.join(workParent,'turbo-build-'));
  const host=path.join(work,'host');
  const runApktool=args=>java?runner(java,['-jar',apktool,...args],{stdio:'inherit'}):runner('apktool',args,{stdio:'inherit'});
  const toolVersions={node:process.version,apktool:String(java?runner(java,['-jar',apktool,'--version'],{stdio:'pipe'}):runner('apktool',['--version'],{stdio:'pipe'})).trim(),python:String(runner(python,['--version'],{stdio:'pipe'})).trim()};
  if(java)toolVersions.java=String(runner(java,['-version'],{stdio:'pipe'})).trim();
  const sourceCopy=path.join(work,'source.apk'),dexCopy=path.join(work,'addon.dex');
  fs.copyFileSync(source,sourceCopy,fs.constants.COPYFILE_EXCL);
  if(sha256(fs.readFileSync(sourceCopy))!==sha)throw new Error('Source changed during preparation');
  fs.copyFileSync(dex,dexCopy,fs.constants.COPYFILE_EXCL);
  const addonSha256=sha256(fs.readFileSync(dexCopy));
  const assetArgs=[];
  if(payloadApk){
    const payloadCopy=path.join(work,'aa-embedded-payload.apk'),sidecarCopy=path.join(work,'payload.json');
    fs.copyFileSync(payloadApk,payloadCopy,fs.constants.COPYFILE_EXCL);
    fs.copyFileSync(payloadSidecar,sidecarCopy,fs.constants.COPYFILE_EXCL);
    assetArgs.push('--payload-apk',payloadCopy,'--payload-sidecar',sidecarCopy);
  }
  const applicationLabel=profile.version==='1.0.10'?'Cheater-IO':null;
  if(applicationLabel)assetArgs.push('--cheater-io');
  runApktool(['d','--no-res','--output',host,sourceCopy]);
  const abi=api.verifyHost(host,profile);
  const writes=planHookWrites(abi, profile);
  for(const [file,text] of writes)fs.writeFileSync(file,text);
  const hookDex=hookDexSlots(writes,host);
  const assembled=path.join(work,'host-assembled.apk'),candidate=path.join(work,'candidate.apk');
  runApktool(['b',host,'-o',assembled]);
  const pythonOptions={stdio:'inherit',env:{...process.env,PYTHONUTF8:'1'}};
  const hookArgs=hookDex.flatMap(slot=>['--hook-dex',slot]);
  runner(python,[path.join(root,'assemble_apk.py'),sourceCopy,assembled,dexCopy,candidate,'--public-assets',...hookArgs,...assetArgs],pythonOptions);
  runner(python,[path.join(root,'verify_apk.py'),sourceCopy,candidate,'--public-assets',...hookArgs,...assetArgs],pythonOptions);
  const outputSha256=sha256(fs.readFileSync(candidate));
  const report={schemaVersion:1,work:path.resolve(work),sourceSha256:sha,inputSha256:sha,addonSha256,profile:profileSnapshot,profileSha256,adapter:profile.adapterId,toolVersions,
    hookDex,embeddedAa:Boolean(payloadApk),applicationLabel,
    checks:{admission:true,hostAbi:true,hookPlan:true,verifyApk:true},outputSha256,originalSignaturePreserved:false,credentialsBundled:false,installed:false,flashed:false,runtimeValidated:false,nonRootValidated:false};
  return {work,candidate,report};
}
export function publish(result,destination,receiptPath) {
  if(fs.existsSync(destination)||fs.existsSync(receiptPath))throw new Error('Refusing to overwrite existing output or receipt');
  if(sha256(fs.readFileSync(result.candidate))!==result.report.outputSha256)throw new Error('Candidate changed after verification');
  const stagedReceipt=path.join(result.work,'receipt.json');
  fs.writeFileSync(stagedReceipt,JSON.stringify(result.report,null,2)+'\n',{flag:'wx'});
  const stagedApk=path.join(result.work,'publish-ready.apk');
  fs.copyFileSync(result.candidate,stagedApk,fs.constants.COPYFILE_EXCL);
  if(sha256(fs.readFileSync(stagedApk))!==result.report.outputSha256)throw new Error('Candidate changed while staging publication');
  let receiptCreated=false;
  try {
    fs.linkSync(stagedReceipt,receiptPath);
    receiptCreated=true;
    fs.linkSync(stagedApk,destination);
  } catch(e) {
    if(receiptCreated)fs.unlinkSync(receiptPath);
    throw e;
  }
  fs.unlinkSync(stagedApk);
  return {output:destination,receipt:receiptPath,work:result.work};
}
