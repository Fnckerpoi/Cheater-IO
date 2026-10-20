// Windows entry: mirrors package.mjs hook transformations; explicit tools and isolated output.
// Mechanical transformation of a private, locally supplied APK derivative.
// Does not remove login, signature checks, certificate pinning or app licensing.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {profileFor,verifyHost} from './host-profiles.mjs';
import {queueHook,fileHook,EVENT_GUARD} from './ota-hooks.mjs';

import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
const [source, work, dex, java, apktool, python] = process.argv.slice(2);
if (![source, work, dex, java, apktool, python].every(Boolean)) throw new Error('Expected source work dex java apktool python');
const runApktool = args => execFileSync(java, ['-jar', apktool, ...args], {stdio:'inherit'});
if(!source) throw new Error('Usage: node android-addon/package.mjs /path/to/supported-RayNeo.apk');
const sha=crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex');
const profile=profileFor(sha),host=path.join(work,profile.directory);
if(profile.version!=='1.0.5')throw new Error('INTEGRATION-07 OTA adapter only supports pinned 1.0.5 (201); use the prior builder for 1.0.4');
if(!fs.existsSync(host)) runApktool(['d','--no-res','--output',host,source]);
// No writes until every required callback/transport descriptor has been checked.
const abi=verifyHost(host,profile);
if(abi.ota){fs.writeFileSync(abi.ota.queue,queueHook(fs.readFileSync(abi.ota.queue,'utf8')));fs.writeFileSync(abi.ota.sender,fileHook(fs.readFileSync(abi.ota.sender,'utf8')));}

function wrap(file,name,signature,args,returnMode) {
  let text=fs.readFileSync(file,'utf8');
  const original='turboioOriginal_'+name;
  const header='.method public final '+name+signature;
  // Replace our generated wrapper on repeated builds, retain the original body.
  if(text.includes(original+'(')) {
    const start=text.indexOf(header+'\n');
    if(start<0)throw new Error('Generated wrapper missing');
    const end=text.indexOf('.end method',start);
    text=text.slice(0,start)+text.slice(end+'.end method'.length);
  } else {
  if(text.split(header).length!==2) throw new Error('Method signature mismatch: '+name);
  text=text.replace(header,'.method public final '+original+signature);
  }
  const className=text.match(/^\.class[^\n]* (L[^;]+;)$/m)?.[1];
  if(!className) throw new Error('Class header missing');
  const invoke='invoke-virtual/range {'+args+'}, '+className+'->'+original+signature;
  const map={onAsrResult:'dispatchAsr(Ljava/lang/Object;Ljava/lang/String;ZLjava/lang/String;)V',
    onNlpResult:'dispatchNlp(Ljava/lang/Object;Ljava/lang/Object;)V',onResponseComplete:'dispatchComplete(Ljava/lang/Object;)V',
    onPostResume:'install(Landroid/app/Activity;)V'};
  const bridge='invoke-static/range {'+args+'}, Lcom/turboio/addon/TurboAddon;->'+map[name];
  const body=name!=='onPostResume'
    ? `    :turboio_try\n    ${bridge}\n    :turboio_try_end\n    return-void\n    :turboio_error\n    move-exception v0\n    ${invoke}\n    return-void`
    : `    ${invoke}\n    :turboio_try\n    ${bridge}\n    :turboio_try_end\n    return-void\n    :turboio_error\n    move-exception v0\n    return-void`;
  text+='\n'+header+'\n    .locals 1\n'+body+'\n    .catch Ljava/lang/Throwable; {:turboio_try .. :turboio_try_end} :turboio_error\n.end method\n';
  fs.writeFileSync(file,text);
}
const listener=abi.listener;
wrap(listener,'onAsrResult','(Ljava/lang/String;ZLjava/lang/String;)V','p0 .. p3','after');
wrap(listener,'onNlpResult','(Lcom/rayneo/airuntime/controller/NlpResult;)V','p0 .. p1','guard');
wrap(listener,'onResponseComplete','()V','p0 .. p0','guard');
wrap(abi.activity,'onPostResume','()V','p0 .. p0','after');
// Preserve official event delivery; observe bounded file and custom ACK metadata afterward.
const eventFile=abi.event;
let eventText=fs.readFileSync(eventFile,'utf8');
const eventHeader='.method public final z(Ljava/lang/String;Ljava/util/Map;)V';
if(eventText.includes('turboioOriginal_z(')) {
  const start=eventText.indexOf(eventHeader+'\n');if(start<0)throw new Error('Missing event wrapper');
  const end=eventText.indexOf('.end method',start);eventText=eventText.slice(0,start)+eventText.slice(end+11);
} else {
  if(eventText.split(eventHeader).length!==2)throw new Error('Event signature mismatch');
  eventText=eventText.replace(eventHeader,'.method public final turboioOriginal_z(Ljava/lang/String;Ljava/util/Map;)V');
}
eventText+='\n'+eventHeader+`\n    .locals 1
${abi.ota?EVENT_GUARD:''}
    invoke-virtual {p0, p1, p2}, Lcom/rayneo/rayneo_venus_sdk_plugin/j;->turboioOriginal_z(Ljava/lang/String;Ljava/util/Map;)V
    :turboio_nav_try
    invoke-static {p1, p2}, Lcom/turboio/addon/NativeTransfer;->event(Ljava/lang/String;Ljava/util/Map;)V
    :turboio_nav_end
    return-void
    :turboio_nav_error
    move-exception v0
    return-void
    .catch Ljava/lang/Throwable; {:turboio_nav_try .. :turboio_nav_end} :turboio_nav_error
.end method\n`;
fs.writeFileSync(eventFile,eventText);
const output=path.join(work,'base-unsigned.apk');
const assembled=path.join(work,'host-assembled.apk');
runApktool(['b',host,'-o',assembled]);
execFileSync(python,[path.join(root,'assemble_apk.py'),source,assembled,dex,output,'--public-assets'],{stdio:'inherit'});
execFileSync(python,[path.join(root,'verify_apk.py'),source,output,'--public-assets'],{stdio:'inherit'});
console.log('Allowlisted unsigned base prepared and audited.');
