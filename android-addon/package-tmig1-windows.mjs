// Windows entry uses the same admission, isolated build, verification and receipt pipeline.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {build,publish} from './host-build.mjs';
export function packageWindows(args,dependencies={}) {
  const [source,work,dex,java,apktool,python]=args;
  if(![source,work,dex,java,apktool,python].every(Boolean))throw new Error('Expected source work dex java apktool python');
  const result=build({source,workParent:work,dex,java,apktool,python,...dependencies.options},dependencies.hostApi);
  return publish(result,path.join(work,'base-unsigned.apk'),path.join(work,`package-report-${result.report.profile.version}.json`));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const result=packageWindows(process.argv.slice(2));
  console.log('Allowlisted unsigned base prepared and audited. Work: '+result.work);
}
