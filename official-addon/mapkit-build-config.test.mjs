import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const cwd=path.dirname(fileURLToPath(import.meta.url));
function config(values={}){
 const env={...process.env,TIO_MAPKIT_ENABLED:'0',TIO_MAPKIT_HUD_ENABLED:'0',TIO_NATIVE_NAV:'0',TIO_OTA_RESEARCH_ENABLED:'0',TIO_MUSIC:'0',TIO_LOCAL_TRANSLATION:'0',...values};
 return spawnSync('/bin/bash',['-c','set -eu; mode=embedded; source ./mapkit-build-config.sh; printf "%s\n" ${mapkit_options[@]+"${mapkit_options[@]}"} ${mapkit_sources[@]+"${mapkit_sources[@]}"}'],{cwd,env,encoding:'utf8'});
}
test('default has no mapkit source or framework',()=>{const r=config();assert.equal(r.status,0);assert.equal(r.stdout.trim(),'');});
test('MapKit-only leaves HUD unavailable without adding display or OTA sources',()=>{const r=config({TIO_MAPKIT_ENABLED:'1'});assert.equal(r.status,0);assert.match(r.stdout,/MapKitNavigation.m/);assert.match(r.stdout,/MapKitHUD.m/);assert.match(r.stdout,/-DTIO_MAPKIT_HUD_ENABLED=0/);assert.doesNotMatch(r.stdout,/DisplayPhoneSession|ExperimentalOTA|Spotify|NativeNavigation/);});
test('ordinary MapKit includes stock text HUD without TDP1 dependencies',()=>{const r=config({TIO_MAPKIT_ENABLED:'1'});assert.equal(r.status,0);assert.match(r.stdout,/MapKitSubtitleHUD.m/);assert.doesNotMatch(r.stdout,/DisplayPhoneSession|ExperimentalOTA|TNVTransport/);});
test('HUD opt-in only links minimal TDP1 dependencies',()=>{const r=config({TIO_MAPKIT_ENABLED:'1',TIO_MAPKIT_HUD_ENABLED:'1'});assert.equal(r.status,0);assert.match(r.stdout,/DisplayPhoneSession.m/);assert.match(r.stdout,/display_runtime.c/);assert.doesNotMatch(r.stdout,/DisplayPhoneUI.m|ExperimentalOTA|MusicPlayer|TNVTransport/);});
test('ordinary HTTP and AMap flags remain compatible',()=>{const r=config({TIO_MAPKIT_ENABLED:'1',TIO_MAPKIT_HUD_ENABLED:'1',TIO_LOCAL_HTTP_ENABLED:'1',TIO_AMAP_ENABLED:'1'});assert.equal(r.status,0);});
test('invalid and experimental combinations are rejected',()=>{for(const values of [{TIO_MAPKIT_HUD_ENABLED:'1'},{TIO_MAPKIT_ENABLED:'2'},{TIO_MAPKIT_ENABLED:'1',TIO_MAPKIT_HUD_ENABLED:'yes'},...['TIO_NATIVE_NAV','TIO_OTA_RESEARCH_ENABLED','TIO_MUSIC','TIO_LOCAL_TRANSLATION'].map(k=>({TIO_MAPKIT_ENABLED:'1',[k]:'1'}))])assert.equal(config(values).status,2,JSON.stringify(values));});
