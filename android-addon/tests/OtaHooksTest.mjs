import assert from 'node:assert/strict';
import {queueHook,fileHook,EVENT_GUARD} from '../ota-hooks.mjs';
let q='.class public LE3/V;\n.method public static o(LE3/Q;)V\n .locals 0\n return-void\n.end method\n';
let result=queueHook(q);assert.match(result,/\.method public static turboioOriginal_o/);assert.equal(result.split('.method public static o(').length,2);assert.match(result,/allowQueue/);assert.match(result,/->blocked/);assert.match(result,/\.catch Ljava\/lang\/Throwable/);let repeated=queueHook(result);assert.equal(repeated.split('.method public static turboioOriginal_o').length,2);assert.equal(repeated.split('allowQueue').length,4);assert.throws(()=>queueHook(q.replace('o(LE3/Q;)','o(Ljava/lang/Object;)')));
assert.ok(result.indexOf('OfficialOtaBridge;->allowQueue')<result.indexOf('OfficialOtaPreparation;->allowQueue'));
assert.ok(result.indexOf('OfficialOtaPreparation;->allowQueue')<result.indexOf('OtaController;->allowQueue'));
assert.match(EVENT_GUARD,/if-nez v0, :ota_event_pass/);
let f='.class public LE3/u;\n.method public final v(Ljava/io/File;Ljava/lang/String;LQ3/q;Ljava/lang/String;)Ljava/lang/String;\n .locals 0\n return-object p1\n.end method\n';
result=fileHook(f);assert.match(result,/\.method public final turboioOriginal_v/);assert.match(result,/invoke-virtual\/range \{p0 .. p4\}/);assert.match(result,/throw v0/);assert.equal(fileHook(result).split('allowFile').length,3);assert.match(EVENT_GUARD,/consume/);assert.match(EVENT_GUARD,/return-void/);
assert.ok(EVENT_GUARD.indexOf('OfficialOtaBridge;->observe')<EVENT_GUARD.indexOf('OtaController;->consume'));

import {queueHookFor,fileHookFor} from '../ota-hooks.mjs';
let q107='.class public LY3/V;\n.method public static o(LY3/S;)V\n .locals 0\n return-void\n.end method\n';
let res107=queueHookFor({version:'1.0.7'},q107);
assert.match(res107,/\.method public static turboioOriginal_o/);
assert.match(res107,/invoke-static \{p0\}, LY3\/V;->turboioOriginal_o\(LY3\/S;\)V/);
let f107='.class public LY3/u;\n.method public final u(Ljava/io/File;Ljava/lang/String;Lk4/q;Ljava/lang/String;)Ljava/lang/String;\n .locals 0\n return-object p1\n.end method\n';
let fileRes107=fileHookFor({version:'1.0.7'},f107);
assert.match(fileRes107,/\.method public final turboioOriginal_u/);
assert.match(fileRes107,/invoke-virtual\/range \{p0 .. p4\}, LY3\/u;->turboioOriginal_u\(Ljava\/io\/File;Ljava\/lang\/String;Lk4\/q;Ljava\/lang\/String;\)Ljava\/lang\/String;/);

console.log('OTA wrapper tests passed (including 1.0.7);  exact ABI checked again before package mutation.');
