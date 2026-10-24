package com.turboio.addon;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
public final class DshClientTest {
 static int checks;
 static void ok(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static Map<String,Object> m(Object...v){return DshClient.mapOf(v);}
 static final String MODEL="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0", PERM="dsh:permission:cmVhZC1vbmx5";
 static DshClient.Config cfg(String token){return new DshClient.Config(true,"https://example.com/",token,"c","p","r",MODEL,PERM);}
 static Map<String,Object> user(String id){return m("role","user","type","message","source",m("clientMessageId",id));}
 static Map<String,Object> answer(String status){return m("role","assistant","type","message","status",status,"content","result");}
 static String state(String runtime,boolean more,Object...items){return String.valueOf(DshClient.evaluateSessionStatus("s","msg_current",m("state",m("sessionId","s","runtimeId","r","status",runtime)),m("sessionId","s","items",Arrays.asList(items),"hasMore",more)).get("status"));}
 static void reject(Runnable r){try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException e){checks++;}}
 public static void main(String[] args)throws Exception{
  ok(cfg("a").ready());ok(!cfg("a").targetFingerprint().equals(cfg("b").targetFingerprint()));
  ok("https://example.com".equals(DshClient.normalizeBaseUrl("https://EXAMPLE.com/")));
  for(String url:Arrays.asList("http://example.com","https://u:p@example.com","https://example.com/api","https://example.com?a","https://example.com#b"))ok(!DshClient.validBaseUrl(url));
  ok(DshClient.validSelection(MODEL,true));ok(DshClient.validSelection(PERM,false));
  for(String value:Arrays.asList("model","dsh:model:@@","dsh:model:W10","dsh:model:WyJhIiwiYiIsbnVsbF0="))ok(!DshClient.validSelection(value,true));
  ok(!DshClient.validSelection("read-only",false));ok(!DshClient.validBinding("https://example.com","","p","r",MODEL,PERM));
  reject(()->DshClient.validateTask(m("task",42)));reject(()->DshClient.validateTask(m("task","abc","extra",1)));
  reject(()->DshClient.validateTask(m("task","leak secret"),"secret"));reject(()->DshClient.validateTask(m("task","a\nb")));
  Map<String,Object> body=DshClient.buildCreateAndStartBody(cfg("a"),"check build","msg_abc");ok("dsh".equals(body.get("runtime")));ok(MODEL.equals(((Map<?,?>)body.get("selections")).get("model")));
  ok("completed".equals(state("idle",false,user("msg_current"),answer("done"))));
  ok("idle_no_reply".equals(state("idle",false,user("msg_current"))));
  ok("waiting_approval".equals(state("waiting_approval",false,user("msg_current"))));
  ok("running".equals(state("running",false,user("msg_current"),answer("done"))));
  ok("failed".equals(state("blocked",false,user("msg_current"))));
  ok("unknown".equals(state("idle",true,user("msg_current"),answer("done"))));
  ok("unknown".equals(state("idle",false,user("msg_other"),answer("done"))));
  ok("unknown".equals(state("idle",false,user("msg_current"),answer("done"),user("msg_next"),answer("done"))));
  ok("unknown".equals(DshClient.evaluateSessionStatus("s","msg_current",m("state",m("sessionId","wrong","status","idle")),m("sessionId","s","items",Arrays.asList(user("msg_current"),answer("done")))).get("status")));
  ok(DshClient.recoverSessionId(m("sessions",Arrays.asList(m("id","old"))),cfg("a"),"msg_current").isEmpty());
  ok(DshClient.recoverSessionId(m("sessions",Arrays.asList(m("id","old","clientMessageId","msg_current","connectorId","WRONG"))),cfg("a"),"msg_current").isEmpty());
  for(int code:new int[]{401,403,422,408,409,429,500}){
   DshClient.MapStateStore store=new DshClient.MapStateStore();int[] calls={0};
   DshClient client=new DshClient(cfg("a"),store,(method,url,token,b)->{calls[0]++;throw new DshClient.HttpStatusException(code);});
   Map<String,Object> out=client.dispatch(m("task","check build"));boolean rejected=code==401||code==403||code==422;
   ok((rejected?"failed":"unknown_delivery").equals(out.get("status")));ok(store.unknownDelivery()!=rejected);
   if(!rejected){client.dispatch(m("task","check build"));client.status(Collections.emptyMap());ok(calls[0]==1);}
  }
  DshClient.StateStore broken=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return false;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String id){throw new IllegalStateException("commit failed");}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}};
  int[] posts={0};try{new DshClient(cfg("a"),broken,(a,b,c,d)->{posts[0]++;return m();}).dispatch(m("task","check build"));throw new AssertionError();}catch(IllegalStateException e){ok(posts[0]==0);}
  DshClient.MapStateStore store=new DshClient.MapStateStore();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
  DshClient first=new DshClient(cfg("a"),store,(a,b,c,d)->{entered.countDown();release.await();return m("session",m("id","old"));});
  Future<?> future=pool.submit(()->{try{first.dispatch(m("task","check build"));}catch(Exception e){throw new RuntimeException(e);}});
  try{ok(entered.await(3,TimeUnit.SECONDS));DshClient second=new DshClient(cfg("a"),store,(a,b,c,d)->{throw new AssertionError("duplicate POST");});ok("unknown_delivery".equals(second.dispatch(m("task","check build")).get("status")));
   synchronized(DshClient.LOCK){store.saveSession("new-target","new-session","new-message");}release.countDown();future.get(3,TimeUnit.SECONDS);ok("new-session".equals(store.sessionId()));
  }finally{release.countDown();pool.shutdownNow();}
  DshClient.StateStore silent=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return false;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String id){}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}};
  try{new DshClient(cfg("a"),silent,(a,b,c,d)->{throw new AssertionError("POST without saved pending");}).dispatch(m("task","check build"));throw new AssertionError();}catch(IllegalStateException e){checks++;}
  DshClient.MapStateStore bound=new DshClient.MapStateStore();bound.saveSession(cfg("a").targetFingerprint(),"s","msg_current");
  DshClient wrongRuntime=new DshClient(cfg("a"),bound,(a,b,c,d)->b.contains("runtime/state")?m("state",m("sessionId","s","runtimeId","WRONG","status","idle")):m("sessionId","s","items",Arrays.asList(user("msg_current"),answer("done"))));
  ok("unknown".equals(wrongRuntime.status(Collections.emptyMap()).get("status")));
  DshClient wrongAccount=new DshClient(cfg("b"),bound,(a,b,c,d)->{throw new AssertionError("cross account HTTP");});ok("failed".equals(wrongAccount.status(Collections.emptyMap()).get("status")));ok("s".equals(bound.sessionId()));
  for(String ending:Arrays.asList("failed","interrupted")) {
   Map<String,Object> partial=answer("done");partial.put("turnId","turn_1");
   ok("failed".equals(state("idle",false,user("msg_current"),partial,m("type","turn.end","turnId","turn_1","status",ending))));
   ok("completed".equals(state("idle",false,user("msg_current"),partial,m("type","turn.end","turnId","other_turn","status",ending))));
  }
  ok("unknown".equals(DshClient.evaluateSessionStatus("s","msg_current",m("state",m("sessionId","s","status","idle")),m("sessionId","wrong","items",Arrays.asList(user("msg_current"),answer("done")))).get("status")));
  DshClient.MapStateStore moving=new DshClient.MapStateStore();moving.saveSession(cfg("a").targetFingerprint(),"s","msg_current");
  DshClient querying=new DshClient(cfg("a"),moving,(a,b,c,d)->{
   if(b.contains("runtime/state"))return m("state",m("sessionId","s","runtimeId","r","status","idle"));
   synchronized(DshClient.LOCK){moving.saveSession(cfg("a").targetFingerprint(),"s2","msg_next");}
   return m("sessionId","s","hasMore",false,"items",Arrays.asList(user("msg_current"),answer("done")));
  });ok("unknown".equals(querying.status(Collections.emptyMap()).get("status")));
  // describePending: tracked session -> hasSession=true, fields mirror the store, no network calls
  DshClient.MapStateStore tracked=new DshClient.MapStateStore();
  tracked.saveSession("fp-1","sess-1","msg_1");
  Map<String,Object> snap=DshClient.describePending(tracked);
  ok(Boolean.TRUE.equals(snap.get("hasSession")));ok(Boolean.TRUE.equals(snap.get("unknownDelivery"))==tracked.unknownDelivery());
  ok("sess-1".equals(snap.get("sessionId")));ok("msg_1".equals(snap.get("clientMessageId")));ok("fp-1".equals(snap.get("targetFingerprint")));
  ok(snap.get("message") instanceof String && !((String)snap.get("message")).isEmpty());
  ok(snap.size()==6);
  // describePending: unknown delivery without session -> hasSession=false
  DshClient.MapStateStore unknownOnly=new DshClient.MapStateStore();
  unknownOnly.savePending("fp-2","msg_2");
  Map<String,Object> unknownSnap=DshClient.describePending(unknownOnly);
  ok(Boolean.FALSE.equals(unknownSnap.get("hasSession")));
  ok(Boolean.TRUE.equals(unknownSnap.get("unknownDelivery")));
  ok("".equals(unknownSnap.get("sessionId")));ok("msg_2".equals(unknownSnap.get("clientMessageId")));
  // describePending: empty store -> all empty, flags false
  Map<String,Object> emptySnap=DshClient.describePending(new DshClient.MapStateStore());
  ok(Boolean.FALSE.equals(emptySnap.get("hasSession")));ok(Boolean.FALSE.equals(emptySnap.get("unknownDelivery")));
  ok("".equals(emptySnap.get("sessionId")));ok("".equals(emptySnap.get("clientMessageId")));ok("".equals(emptySnap.get("targetFingerprint")));
  // releasePending: clears pending and session, returns released + auditKept flag
  DshClient.MapStateStore releaseTarget=new DshClient.MapStateStore();
  releaseTarget.saveSession("fp-3","sess-3","msg_3");
  releaseTarget.savePending("fp-3","msg_4");
  Map<String,Object> released=DshClient.releasePending(releaseTarget,true);
  ok("released".equals(released.get("status")));ok(Boolean.TRUE.equals(released.get("auditKept")));
  ok(!released.get("message").toString().isEmpty());
  ok(!releaseTarget.unknownDelivery());ok("".equals(releaseTarget.sessionId()));ok("".equals(releaseTarget.clientMessageId()));ok("".equals(releaseTarget.targetFingerprint()));
  Map<String,Object> releasedNoAudit=DshClient.releasePending(releaseTarget,false);
  ok("released".equals(releasedNoAudit.get("status")));ok(Boolean.FALSE.equals(releasedNoAudit.get("auditKept")));
  // releasePending: store failure must propagate, not be swallowed
  DshClient.StateStore failing=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return true;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String id){}public void clearPending(){throw new IllegalStateException("storage_failed");}public void saveSession(String f,String id,String msg){}public void clearSession(){}};
  try{DshClient.releasePending(failing,true);throw new AssertionError("releasePending swallowed storage failure");}catch(IllegalStateException e){checks++;}
  // releasePending: silent partial-clear store must be detected, not reported as success
  DshClient.StateStore silentClear=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return true;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String id){}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}};
  try{DshClient.releasePending(silentClear,false);throw new AssertionError("releasePending accepted silent no-op store");}catch(IllegalStateException e){checks++;}
  System.out.println("PASS "+checks+" DshClient checks");
 }
}
