package com.turboio.addon;
import java.util.*;import java.lang.reflect.*;import org.json.*;
/**
 * Round authorization on this branch's own host: the durable round identity, its freshness guards, and
 * the rule that only a durably submitted round may arm the single display coordinator. Production
 * ToolClient/DshClient/DshAliasRegistry are compiled unchanged; only platform and secret boundaries are
 * faked, and DshProactiveDisplay here is the recording fake from HostFakes.
 */
public final class ToolClientHudRoundTest {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static final String MODEL="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0",PERMISSION="dsh:permission:cmVhZC1vbmx5";
 static android.content.Context app=new android.content.Context();
 static ToolClient host=new ToolClient(app);
 // The fake preferences proxy is bound to one backing map; the host keeps that same map for its
 // lifetime, so reset() must clear the map's contents rather than drop the map itself.
 static Map<String,Object> prefs=app.values.get("turboio_settings");
  static String fingerprint(){return new DshClient.Config(true,"https://example.com","token","connector","project","runtime",MODEL,PERMISSION).targetFingerprint();}
  static void reset()throws Exception{
   app.values.keySet().retainAll(Collections.singletonList("turboio_settings"));prefs.clear();app.fail=false;
   prefs.put("dsh",true);prefs.put("dsh_user_id","user");prefs.put("dsh_url","https://example.com");
  prefs.put("dsh_connector","connector");prefs.put("dsh_project","project");prefs.put("dsh_runtime","runtime");
  prefs.put("dsh_model",MODEL);prefs.put("dsh_permission",PERMISSION);
  prefs.put("dsh_session_id","session");prefs.put("dsh_client_message_id","message");prefs.put("dsh_unknown_delivery",false);
  prefs.put("dsh_target_fingerprint",fingerprint());
  SecretStore.token="token";DshSettings.locked=false;DshSettings.auth=1;DshProactiveDisplay.reset();
  Field f=ToolClient.class.getDeclaredField("dshStorageFailed");f.setAccessible(true);f.set(null,false);
  Field g=ToolClient.class.getDeclaredField("selectionGeneration");g.setAccessible(true);g.set(null,0L);
 }
 static void mutate(String key,Object changed)throws Exception{
  reset();DshProactiveReport.RoundIdentity r=host.snapshotRound();ok(r!=null,"baseline round readable before mutating "+key);
  prefs.put(key,changed);
  ok(!host.isCurrentRound(r),"round invalid after "+key);
 }
 static DshClient.StateStore store(Runnable guard)throws Exception{
  Method m=ToolClient.class.getDeclaredMethod("dshClient",Runnable.class,boolean.class);m.setAccessible(true);
  DshClient client=(DshClient)m.invoke(host,guard,false);
  Field s=DshClient.class.getDeclaredField("store");s.setAccessible(true);return (DshClient.StateStore)s.get(client);
 }
 public static void main(String[] args)throws Exception{
  // 1. A complete persisted slot is exactly one watchable round, and the identity is its own answer key.
  reset();DshProactiveReport.RoundIdentity round=host.snapshotRound();
  ok(round!=null&&host.isCurrentRound(round),"actual persisted round accepted");
  ok("user".equals(round.account)&&"session".equals(round.sessionId)&&"message".equals(round.clientMessageId)
   &&fingerprint().equals(round.targetFingerprint),"identity carries the durable triple and fingerprint");
  // 2. Any churn of the durable state invalidates the ticket, so no delayed callback can display.
  mutate("dsh_user_id","other");mutate("dsh_session_id","other");mutate("dsh_client_message_id","other");
  mutate("dsh_unknown_delivery",true);mutate("dsh_runtime","other");mutate("dsh_target_fingerprint","other");
  mutate("dsh",false);mutate("dsh_url","https://elsewhere.example");
  reset();round=host.snapshotRound();DshSettings.auth++;
  ok(!host.isCurrentRound(round),"auth generation rejects delayed draws");
  reset();round=host.snapshotRound();ToolClient.selectionChanged();
  ok(!host.isCurrentRound(round),"selection generation rejects delayed draws");
  // ABA: the keys come back byte-identical, yet the generation advanced, so the old ticket stays dead.
  reset();round=host.snapshotRound();
  String[] durable={"dsh_runtime","dsh_model","dsh_permission","dsh_session_id","dsh_client_message_id","dsh_target_fingerprint"};
  Map<String,Object> held=new LinkedHashMap<>();for(String key:durable)held.put(key,prefs.get(key));
  ToolClient.selectionChanged();
  prefs.putAll(held);
  ok(!host.isCurrentRound(round),"an ABA re-selection with identical keys cannot revive the old round");
  DshProactiveReport.RoundIdentity replacement=host.snapshotRound();
  ok(replacement!=null&&host.isCurrentRound(replacement),"the same-looking keys now form a NEW current round");
  ok(!replacement.equals(round),"the replacement round is a distinct identity, so old tickets stay dead");
  reset();round=host.snapshotRound();SecretStore.token="changed";
  ok(!host.isCurrentRound(round),"credential rotation invalidates the round");
  reset();round=host.snapshotRound();DshSettings.locked=true;
  ok(!host.isCurrentRound(round),"storage lock invalidates the round");
  // 3. An unconfirmed delivery is never a watchable round at all.
  reset();prefs.put("dsh_unknown_delivery",true);ok(host.snapshotRound()==null,"unknown delivery is not a round");
  // 4. queryRound refuses a stale round before any network, and never runs its guard under the host lock.
  reset();round=host.snapshotRound();prefs.put("dsh_client_message_id","new");
  try{host.queryRound(round);throw new AssertionError("stale query allowed");}
  catch(IllegalStateException e){ok("stale_round".equals(e.getMessage()),"query stale throws before network");}
  // 5. A committed local session change retires the round the monitor was watching.
  reset();round=host.snapshotRound();
  DshClient.StateStore s=store(null);
  synchronized(DshClient.LOCK){s.saveSession(fingerprint(),"session","next-message");}
  ok(!host.isCurrentRound(round),"a new submitted message in the same session retires the old round");
  ok(host.snapshotRound()!=null&&"next-message".equals(host.snapshotRound().clientMessageId),"the newer round is readable");
  // 6. A failed durable write locks the host and yields no round.
  reset();s=store(null);app.fail=true;
  try{synchronized(DshClient.LOCK){s.clearSession();}throw new AssertionError("failed persistence accepted");}
  catch(IllegalStateException e){
   ok("session".equals(prefs.get("dsh_session_id")),"failed persistence keeps the durable slot");
   ok(host.snapshotRound()==null,"a storage-locked host yields no round at all");
  }
  reset();ok(host.snapshotRound()!=null,"a healthy slot is a round again after the lock is cleared");
  // 7. Only a durably submitted round arms the single display coordinator.
  reset();final String[] message={null};
  final ToolClient armed=new ToolClient(app);
  // The real routing plus durable-commit path runs against a scripted HTTPS transport.
  java.net.URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new java.net.URLStreamHandler(){
   protected java.net.URLConnection openConnection(java.net.URL u)throws java.io.IOException{
    if(u.getPath().endsWith("/runtimes"))return stream(u,DshClient.mapOf("connectorId","connector","runtimes",
     Arrays.asList(DshClient.mapOf("connectorId","connector","runtimeId","runtime","runtimeType","dsh","status","running","available",true,"configured",true,"active",true))));
    if(u.getPath().endsWith("/create-and-start")||u.getPath().endsWith("/runtime/messages"))return stream(u,DshClient.mapOf("sessionId","session"));
    if(u.getPath().endsWith("/runtime/state"))return stream(u,DshClient.mapOf("state",DshClient.mapOf("sessionId","session","runtimeId","runtime","status","idle")));
    String id=(String)prefs.get("dsh_client_message_id");
    return stream(u,DshClient.mapOf("sessionId","session","hasMore",false,"items",
     Arrays.asList(DshClient.mapOf("role","user","type","message","source",DshClient.mapOf("clientMessageId",id)),
      DshClient.mapOf("role","assistant","type","message","status","done","content","done"))));
   }}:null);
  JSONObject task=new JSONObject().put("task","do the work");
  JSONObject submitted=armed.call("dsh_dispatch",task,"");
  ok("submitted".equals(submitted.optString("status")),"real dispatch reports submitted got="+submitted);
  ok(DshProactiveDisplay.created==1&&DshProactiveDisplay.INSTANCE.starts==1,"submitted round arms the singleton display once");
  ok(DshProactiveDisplay.INSTANCE.started!=null&&host.isCurrentRound(DshProactiveDisplay.INSTANCE.started),
   "the coordinator receives exactly the current durable round");
  ok(DshProactiveDisplay.INSTANCE.started.clientMessageId.equals(submitted.optString("clientMessageId")),
   "the armed round is the message this dispatch submitted");
  // 8. A refused dispatch arms nothing: neither a routing refusal nor an unconfirmed delivery.
  reset();DshProactiveDisplay.reset();
  JSONObject refused=armed.call("dsh_dispatch",new JSONObject().put("task","do the work").put("target","nope"),"");
  ok(!"submitted".equals(refused.optString("status"))&&DshProactiveDisplay.INSTANCE.starts==0,"a routing refusal arms nothing");
  reset();DshProactiveDisplay.reset();app.fail=true;
  try{armed.call("dsh_dispatch",task,"");}catch(IllegalStateException storageFailed){}catch(Exception ignored){}
  app.fail=false;
  ok(DshProactiveDisplay.INSTANCE.starts==0,"a failed durable submitted never arms the display");
  // 9. The HUD switch reaches the coordinator through the host, and an idle startup never opens a lens.
  reset();DshProactiveDisplay.reset();
  ok(DshProactiveDisplay.get(app).hudEnabled(),"the composite HUD is default-on when no preference exists");
  ok(DshProactiveDisplay.created==1,"the host reuses the one display coordinator instance");
  ok(DshProactiveDisplay.INSTANCE.stops==0&&DshProactiveDisplay.INSTANCE.starts==0,
   "reading the switch alone starts nothing: an idle startup keeps the phone in charge");
  // 10. The guarded AA path arms the SAME single coordinator exactly once, and a refused pin arms nothing.
  reset();DshProactiveDisplay.reset();
  final int[] guardRuns={0};
  Runnable bridgeGuard=()->{
   ok(Thread.holdsLock(DshClient.LOCK),"the bridge pin is checked inside the host lock");
   guardRuns[0]++;
   if("foreign".equals(prefs.get("dsh_client_message_id")))throw new IllegalStateException("stale bridge pin");
  };
  JSONObject bridgeSubmitted=armed.call("dsh_dispatch",task,"",bridgeGuard);
  ok("submitted".equals(bridgeSubmitted.optString("status")),"an AA bridge dispatch reports submitted");
  ok(guardRuns[0]>0,"the bridge pin was honoured");
  ok(DshProactiveDisplay.INSTANCE.starts==1&&DshProactiveDisplay.created>=1,
   "the guarded AA path arms the one coordinator exactly once, like the voice path");
  ok(host.isCurrentRound(DshProactiveDisplay.INSTANCE.started)
   &&bridgeSubmitted.optString("clientMessageId").equals(DshProactiveDisplay.INSTANCE.started.clientMessageId),
   "the AA bridge round that armed the display is the current durable round");
  // A refused bridge pin fails closed: it throws before any durable write, so nothing new is armed.
  String trackedBefore=(String)prefs.get("dsh_client_message_id");
  prefs.put("dsh_client_message_id","foreign");
  String pinned=(String)prefs.get("dsh_client_message_id");
  boolean pinThrew=false;
  try{armed.call("dsh_dispatch",task,"",bridgeGuard);}catch(IllegalStateException refusedPin){pinThrew=true;}
  ok(pinThrew,"a refused bridge pin throws instead of dispatching");
  ok(pinned.equals(prefs.get("dsh_client_message_id")),"a refused bridge pin leaves the durable slot untouched");
  ok(DshProactiveDisplay.INSTANCE.starts==1,"a refused bridge pin arms no new display round");
  System.out.println("ToolClientHudRoundTest: "+checks+" checks passed");
 }
  static java.net.URLConnection stream(java.net.URL u,Object body)throws java.io.IOException{
  return new java.net.HttpURLConnection(u){
   final java.io.ByteArrayOutputStream sink=new java.io.ByteArrayOutputStream();
   public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
   // The real transport writes a JSON body, so the double must own its output sink: the JDK base class
   // returns no usable stream for a synthetic handler.
   public java.io.OutputStream getOutputStream(){return sink;}
   public int getResponseCode(){ok(!Thread.holdsLock(DshClient.LOCK),"actual HTTP outside the host lock");return 200;}
   public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(DshClient.stringifyJson(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  };
 }
}
