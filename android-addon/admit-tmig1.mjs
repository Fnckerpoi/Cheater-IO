import fs from 'node:fs';
import crypto from 'node:crypto';
import {profileFor,assertBuildProfile} from './host-profiles.mjs';
const source=process.argv[2];
if(!source||process.argv.length!==3)throw new Error('Expected source APK');
const profile=profileFor(crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'));
assertBuildProfile(profile);
console.log(JSON.stringify({id:profile.id,version:profile.version,code:profile.code,sha256:profile.sha256,adapterId:profile.adapterId}));
