// Android 1.0.5 and 1.0.7 offline serialized-string source routing, not an OTA sender.
// Dart StringSerializationCluster encodes length<<1, then raw one-byte
// characters. Preserve length AND hash: the canonical table layout is serialized.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {makeRoute,stringHash} from '../official-addon/research/patch-ios105-ota-source.mjs';

const nativeProfiles=JSON.parse(fs.readFileSync(new URL('./native-source-profiles.json',import.meta.url),'utf8'));
export const SOURCE_PROFILES = Object.freeze(Object.fromEntries(nativeProfiles.map(p=>[p.sha256,Object.freeze(p)])));

// Backward compatibility defaults for 1.0.5
export const sourceSHA='78214ba0e1057871315da1cee17d860415c8b913521e59afc2748e45ead64c6c';
export const slots=SOURCE_PROFILES[sourceSHA].slots;

const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const check=(v,m)=>{if(!v)throw Error(m);};

export function patchAndroidSource(input){
 const inputSHA = sha(input);
 const profile = SOURCE_PROFILES[inputSHA];
 check(profile, 'Unsupported Android libapp.so; no changes');

 const output=Buffer.from(input),changes=[];
 for(const {offset,pp,text,replacement: pinnedReplacement} of profile.slots){
  check(input.indexOf(text)===offset&&input.indexOf(text,offset+1)===-1,'Nonunique string');
  check(input[offset-1]===0x80+(text.length<<1),'Unexpected serialized string length/CID');
  const replacement=makeRoute(text);
   check(replacement===pinnedReplacement,'Replacement contract mismatch');
  check(replacement.length===text.length&&stringHash(replacement)===stringHash(text),'Canonical hash/size changed');
  output.write(replacement,offset,'ascii');
  changes.push({offset,pp,bytes:text.length,replacement,hash:stringHash(text)});
 }
 for(let i=0;i<input.length;i++)if(input[i]!==output[i])check(changes.some(c=>i>=c.offset&&i<c.offset+c.bytes),'Outside string change');
 return {output,report:{inputSHA,version:profile.version,outputSHA:sha(output),changes,bytes:output.length,
  deviceIO:false,installed:false,flashed:false,runtimeValidated:false,
  scope:'Source routing only; requires preparation-only transport guard. No firmware or executable instruction modification.'}};
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const [src,dst]=process.argv.slice(2);check(src&&dst,'Usage: original-libapp.so new-output-directory');
 check(!fs.existsSync(dst),'Output already exists');
 const {output,report}=patchAndroidSource(fs.readFileSync(src));fs.mkdirSync(dst,{recursive:true});
 fs.writeFileSync(dst+'/libapp.so',output,{flag:'wx'});fs.writeFileSync(dst+'/report.json',JSON.stringify(report,null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify(report,null,2));
}
