// Pure preflight and explicit resource copy for the optional static SDK build.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
export function amapResources(root){
  if(!path.isAbsolute(root))throw Error('amap_absolute_root_required');
  const items=[['navi/AMapNaviKit.framework/AMap.bundle','AMap.bundle'],['navi/AMapNaviKit.framework/AMapNavi.bundle','AMapNavi.bundle'],['search/AMapSearchKit.framework/AMapSearch.bundle','AMapSearch.bundle']];
  function inspect(p){const s=fs.lstatSync(p);if(s.isSymbolicLink())throw Error('amap_symlink_requires_review');if(s.isDirectory())for(const n of fs.readdirSync(p))inspect(path.join(p,n));else if(!s.isFile())throw Error('amap_regular_resources_required');}
  return items.map(([rel,name])=>{const source=path.join(root,rel);if(!fs.existsSync(source)||!fs.statSync(source).isDirectory())throw Error('amap_resources_missing');inspect(source);return {source,name};});
}
export function checkAMapDestination(resources,app){
  if(!path.isAbsolute(app)||!fs.statSync(app).isDirectory())throw Error('amap_absolute_app_required');
  for(const r of resources)if(fs.existsSync(path.join(app,r.name)))throw Error('amap_resource_collision');
}
export function copyAMapResources(resources,app){checkAMapDestination(resources,app);for(const r of resources)fs.cpSync(r.source,path.join(app,r.name),{recursive:true,force:false,errorOnExist:true});}
// Shared by the unsigned HTTP packager; importing this module has no effects.
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    const args=process.argv.slice(2),options={};
    if(args.length%2)throw Error('amap_expected_named_arguments');
    for(let i=0;i<args.length;i+=2){const key=args[i];if(!['--sdk-root','--check-app','--app'].includes(key)||options[key])throw Error('amap_invalid_argument');options[key]=args[i+1];}
    if(options['--app']&&options['--check-app'])throw Error('amap_choose_check_or_copy');
    const resources=amapResources(options['--sdk-root']||'');
    if(options['--check-app'])checkAMapDestination(resources,options['--check-app']);
    if(options['--app'])copyAMapResources(resources,options['--app']);
    console.log(JSON.stringify(resources.map(r=>r.name)));
  }catch(error){console.error(error.message);process.exitCode=1;}
}
