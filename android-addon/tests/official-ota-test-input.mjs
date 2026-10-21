import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const testDirectory=path.dirname(fileURLToPath(import.meta.url));
export function officialOtaInput(env=process.env,legacyPath=path.join(testDirectory,'..','build','host-105','lib','arm64-v8a','libapp.so')) {
  const configured=env.TURBO_OFFICIAL_LIBAPP?.trim();
  const candidate=configured?path.resolve(configured):legacyPath;
  if(!fs.existsSync(candidate)) {
    if(configured)throw new Error('TURBO_OFFICIAL_LIBAPP does not exist: '+candidate);
    throw new Error('Missing official Android 1.0.5 libapp.so fixture. Set TURBO_OFFICIAL_LIBAPP to the original extracted libapp.so; legacy path also checked: '+legacyPath);
  }
  if(!fs.statSync(candidate).isFile())throw new Error('Official libapp.so path is not a file: '+candidate);
  return fs.readFileSync(candidate);
}
