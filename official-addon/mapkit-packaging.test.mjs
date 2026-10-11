import test from 'node:test';
import assert from 'node:assert/strict';
import {mapkitLocationInfo} from './package.mjs';
test('MapKit adds missing foreground purpose without background or ATS changes',()=>{const info={CFBundleIdentifier:'com.duriea.turboio.research',NSAppTransportSecurity:{NSAllowsArbitraryLoads:true}};const before=structuredClone(info);const out=mapkitLocationInfo(info,'_TIOMapKitBuildPolicy');assert.deepEqual(info,before);assert.match(out.NSLocationWhenInUseUsageDescription,/前台/);assert.deepEqual(out.NSAppTransportSecurity,before.NSAppTransportSecurity);assert.equal(out.UIBackgroundModes,undefined);assert.equal(out.NSLocationAlwaysAndWhenInUseUsageDescription,undefined);});
test('no capability marker keeps existing configuration untouched',()=>{const info={CFBundleIdentifier:'com.rayneo.venus.pub'};assert.deepEqual(mapkitLocationInfo(info,''),info);});
test('existing purpose and AMap background modes preserved',()=>{const info={NSLocationWhenInUseUsageDescription:'原有用途',UIBackgroundModes:['location','audio']};assert.deepEqual(mapkitLocationInfo(info,'_TIOMapKitBuildPolicy'),info);});
