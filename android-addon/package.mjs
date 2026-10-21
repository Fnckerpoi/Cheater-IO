// Mechanical transformation of a private APK; signing and runtime acceptance remain separate.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseArgs} from 'node:util';
import {build,publish} from './host-build.mjs';
const root=path.dirname(fileURLToPath(import.meta.url));
export function parsePackageArgs(args) {
  const {values,positionals}=parseArgs({args,allowPositionals:true,options:Object.fromEntries(['java','apktool','python','dex','output-dir'].map(name=>[name,{type:'string'}]))});
  if(positionals.length!==1)throw new Error('Usage: node android-addon/package.mjs source.apk [--java java --apktool apktool.jar --python python --dex classes.dex --output-dir directory]');
  if(Boolean(values.java)!==Boolean(values.apktool))throw new Error('--java and --apktool must be supplied together');
  for(const [name,value] of Object.entries(values))if(!value.trim())throw new Error('--'+name+' must not be empty');
  const {'output-dir':outputDirectory,...options}=values;
  return {source:positionals[0],outputDirectory,options};
}
export function packageApk(source,dependencies={}) {
  if(!source)throw new Error('Usage: node android-addon/package.mjs /path/to/supported-RayNeo.apk');
  const {options={},outputDirectory:requestedOutput}=dependencies;
  const outputDirectory=requestedOutput??path.join(root,'build');
  const result=build({source,workParent:outputDirectory,dex:options.dex??path.join(root,'build/dex/classes.dex'),...options},dependencies.hostApi);
  const v=result.report?.profile?.version||'1.0.5'; return publish(result,path.join(outputDirectory,`TurboIO-RayNeo-${v}-unsigned.apk`),path.join(outputDirectory,`package-report-${v}.json`));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const {source,...dependencies}=parsePackageArgs(process.argv.slice(2));
  const result=packageApk(source,dependencies);
  console.log('Unsigned private derivative prepared; signing and non-root acceptance still required. Work: '+result.work);
}
