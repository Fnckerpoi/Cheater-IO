import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {amapResources,copyAMapResources} from './amap-resources.mjs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
test('resource CLI preflights without writes, copies all bundles, rejects missing/collision/symlink',()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'turbo-amap-cli-'));
  try{
    const sdk=path.join(root,'sdk'),app=path.join(root,'Runner.app');fs.mkdirSync(app);
    const dirs=['navi/AMapNaviKit.framework/AMap.bundle','navi/AMapNaviKit.framework/AMapNavi.bundle','search/AMapSearchKit.framework/AMapSearch.bundle'];
    for(const dir of dirs){fs.mkdirSync(path.join(sdk,dir),{recursive:true});fs.writeFileSync(path.join(sdk,dir,'fixture.txt'),'synthetic SDK resource');}
    const cli=(...args)=>spawnSync(process.execPath,[fileURLToPath(new URL('./amap-resources.mjs',import.meta.url)),...args],{encoding:'utf8'});
    const check=cli('--sdk-root',sdk,'--check-app',app);assert.equal(check.status,0,check.stderr);assert.deepEqual(JSON.parse(check.stdout),['AMap.bundle','AMapNavi.bundle','AMapSearch.bundle']);assert.deepEqual(fs.readdirSync(app),[]);
    assert.notEqual(cli('--sdk-root',sdk,'--sdk-root',sdk).status,0);assert.notEqual(cli('--sdk-root','relative').status,0);
    const copy=cli('--sdk-root',sdk,'--app',app);assert.equal(copy.status,0,copy.stderr);
    for(const name of JSON.parse(copy.stdout))assert.equal(fs.readFileSync(path.join(app,name,'fixture.txt'),'utf8'),'synthetic SDK resource');
    assert.notEqual(cli('--sdk-root',sdk,'--check-app',app).status,0);
    fs.symlinkSync('/tmp',path.join(sdk,dirs[0],'unexpected'));assert.notEqual(cli('--sdk-root',sdk).status,0);
  }finally{fs.rmSync(root,{recursive:true});}
});
test('resource preflight requires absolute root and all three bundles',()=>{assert.throws(()=>amapResources('relative'));const p=fs.mkdtempSync(path.join(os.tmpdir(),'turbo-amap-test-'));try{assert.throws(()=>amapResources(p),/missing/);}finally{fs.rmSync(p,{recursive:true});}});
test('copy keeps source intact, refuses collisions and symlinks',()=>{const p=fs.mkdtempSync(path.join(os.tmpdir(),'turbo-amap-test-'));try{for(const n of ['navi/AMapNaviKit.framework/AMap.bundle','navi/AMapNaviKit.framework/AMapNavi.bundle','search/AMapSearchKit.framework/AMapSearch.bundle']){fs.mkdirSync(path.join(p,n),{recursive:true});fs.writeFileSync(path.join(p,n,'fixture.txt'),'synthetic resource');}const items=amapResources(p),app=path.join(p,'output');fs.mkdirSync(app);copyAMapResources(items,app);assert.equal(items.length,3);assert.equal(fs.readFileSync(path.join(app,'AMap.bundle/fixture.txt'),'utf8'),'synthetic resource');assert.equal(fs.readFileSync(path.join(items[0].source,'fixture.txt'),'utf8'),'synthetic resource');assert.throws(()=>copyAMapResources(items,app),/collision/);fs.symlinkSync('/tmp',path.join(items[0].source,'link'));assert.throws(()=>amapResources(p),/symlink/);}finally{fs.rmSync(p,{recursive:true});}});
