package com.turboio.addon;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
public final class DshClientTest {
 static int checks;
 static class DelegatingStore implements DshClient.StateStore {
  final DshClient.MapStateStore inner=new DshClient.MapStateStore();
  public String sessionId(){return inner.sessionId();}
  public boolean unknownDelivery(){return inner.unknownDelivery();}
  public String clientMessageId(){return inner.clientMessageId();}
  public String targetFingerprint(){return inner.targetFingerprint();}
  public void savePending(String f,String a,String id){inner.savePending(f,a,id);}
  public void saveSession(String f,String a,String id){inner.saveSession(f,a,id);}
  public void clearPending(){inner.clearPending();}
  public void clearSession(){inner.clearSession();}
  public void clearSessionAndPending(){inner.clearSessionAndPending();}
 }
 static void ok(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static Map<String,Object> m(Object...v){return DshClient.mapOf(v);}
 static final String MODEL="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0", PERM="dsh:permission:cmVhZC1vbmx5";
 static DshClient.Config cfg(String token){return new DshClient.Config(true,"https://example.com/",token,"c","p","r",MODEL,PERM);}
 static Map<String,Object> user(String id){return m("role","user","type","message","source",m("clientMessageId",id));}
 static Map<String,Object> answer(String status){return m("role","assistant","type","message","status",status,"content","result");}
 static String state(String runtime,boolean more,Object...items){return String.valueOf(DshClient.evaluateSessionStatus("s","msg_current",m("state",m("sessionId","s","runtimeId","r","status",runtime)),m("sessionId","s","items",Arrays.asList(items),"hasMore",more)).get("status"));}
 static void reject(Runnable r){try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException e){checks++;}}
 public static void main(String[] args)throws Exception{
  contractV3();
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
  DshClient.StateStore broken=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return false;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String anchor,String id){throw new IllegalStateException("commit failed");}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}public void clearSessionAndPending(){}};
  int[] posts={0};try{new DshClient(cfg("a"),broken,(a,b,c,d)->{posts[0]++;return m();}).dispatch(m("task","check build"));throw new AssertionError();}catch(IllegalStateException e){ok(posts[0]==0);}
  DshClient.MapStateStore store=new DshClient.MapStateStore();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
  DshClient first=new DshClient(cfg("a"),store,(a,b,c,d)->{entered.countDown();release.await();return m("session",m("id","old"));});
  Future<?> future=pool.submit(()->{try{first.dispatch(m("task","check build"));}catch(Exception e){throw new RuntimeException(e);}});
  try{ok(entered.await(3,TimeUnit.SECONDS));DshClient second=new DshClient(cfg("a"),store,(a,b,c,d)->{throw new AssertionError("duplicate POST");});ok("unknown_delivery".equals(second.dispatch(m("task","check build")).get("status")));
   synchronized(DshClient.LOCK){store.saveSession("new-target","new-session","new-message");}release.countDown();future.get(3,TimeUnit.SECONDS);ok("new-session".equals(store.sessionId()));
  }finally{release.countDown();pool.shutdownNow();}
  DshClient.StateStore silent=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return false;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String anchor,String id){}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}public void clearSessionAndPending(){}};
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
  unknownOnly.savePending("fp-2","","msg_2");
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
  releaseTarget.savePending("fp-3","","msg_4");
  Map<String,Object> released=DshClient.releasePending(releaseTarget,true);
  ok("released".equals(released.get("status")));ok(Boolean.TRUE.equals(released.get("auditKept")));
  ok(!released.get("message").toString().isEmpty());
  ok(!releaseTarget.unknownDelivery());ok("".equals(releaseTarget.sessionId()));ok("".equals(releaseTarget.clientMessageId()));ok("".equals(releaseTarget.targetFingerprint()));
  Map<String,Object> releasedNoAudit=DshClient.releasePending(releaseTarget,false);
  ok("released".equals(releasedNoAudit.get("status")));ok(Boolean.FALSE.equals(releasedNoAudit.get("auditKept")));
  // releasePending: store failure must propagate, not be swallowed
  DshClient.StateStore failing=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return true;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String anchor,String id){}public void clearPending(){throw new IllegalStateException("storage_failed");}public void saveSession(String f,String id,String msg){}public void clearSession(){}public void clearSessionAndPending(){}};
  try{DshClient.releasePending(failing,true);throw new AssertionError("releasePending swallowed storage failure");}catch(IllegalStateException e){checks++;}
  // releasePending: silent partial-clear store must be detected, not reported as success
  DshClient.StateStore silentClear=new DshClient.StateStore(){public String sessionId(){return "";}public boolean unknownDelivery(){return true;}public String clientMessageId(){return "";}public String targetFingerprint(){return "";}public void savePending(String f,String anchor,String id){}public void clearPending(){}public void saveSession(String f,String id,String msg){}public void clearSession(){}public void clearSessionAndPending(){}};
  try{DshClient.releasePending(silentClear,false);throw new AssertionError("releasePending accepted silent no-op store");}catch(IllegalStateException e){checks++;}
   // ===== session continuation protocol =====
   // 1) first dispatch -> create-and-start, mode=created, session id stored
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    List<String> urls=new ArrayList<>();List<String> verbs=new ArrayList<>();List<Map<String,Object>> bodies=new ArrayList<>();
    DshClient cli=new DshClient(cfg("a"),st,(method,url,token,reqBody)->{
     urls.add(url);verbs.add(method);bodies.add(reqBody);
     if(url.endsWith("/api/v2/sessions/create-and-start"))return m("data",m("sessionId","S1"));
     throw new AssertionError("unexpected url "+url);
    });
    Map<String,Object> out=cli.dispatch(m("task","check build one"));
    ok("submitted".equals(out.get("status")));ok("created".equals(out.get("mode")));
    ok("S1".equals(out.get("sessionId")));ok("S1".equals(st.sessionId()));
    ok(verbs.size()==1&&"POST".equals(verbs.get(0)));
    ok(urls.get(0).equals("https://example.com/api/v2/sessions/create-and-start"));
    ok("check build one".equals(bodies.get(0).get("content")));
    ok(String.valueOf(bodies.get(0).get("clientMessageId")).startsWith("msg_"));
   }
   // 2) second dispatch with existing session -> append to same session, mode=continued
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    st.saveSession(cfg("a").targetFingerprint(),"S","msg_prev");
    List<String> u2=new ArrayList<>();List<Map<String,Object>> b2=new ArrayList<>();
    DshClient cli=new DshClient(cfg("a"),st,(method,url,token,reqBody)->{
     u2.add(url);b2.add(reqBody);
     if(url.endsWith("/sessions/S/runtime/messages"))return m("data",m("sessionId","S"));
     throw new AssertionError("unexpected url "+url);
    });
    Map<String,Object> out=cli.dispatch(m("task","check build two"));
    ok("submitted".equals(out.get("status")));ok("continued".equals(out.get("mode")));
    ok("S".equals(out.get("sessionId")));ok("S".equals(st.sessionId()));
    ok(u2.size()==1);ok(u2.get(0).equals("https://example.com/api/v2/sessions/S/runtime/messages"));
    ok("check build two".equals(b2.get(0).get("content")));
    ok(String.valueOf(b2.get(0).get("clientMessageId")).startsWith("msg_"));
    ok(!b2.get(0).containsKey("connectorId"));ok(!b2.get(0).containsKey("projectId"));
   }
   // Unavailable sessions fail without creating a replacement.
   for(String code:new String[]{"session_archived","session_unavailable","session_deleted","session_missing",""}) {
    DshClient.MapStateStore st=new DshClient.MapStateStore();st.saveSession(cfg("a").targetFingerprint(),"S","msg_prev");
    int[] calls={0};
    Map<String,Object> result=new DshClient(cfg("a"),st,(verb,url,t,b)->{
     calls[0]++;ok(url.equals("https://example.com/api/v2/sessions/S/runtime/messages"));
     throw new DshClient.HttpStatusException(code.isEmpty()?404:409,code);
    }).dispatch(m("task","check build"));
    ok("failed".equals(result.get("status")));ok(calls[0]==1);ok("S".equals(st.sessionId()));ok(!st.unknownDelivery());
   }
   // 4) continued path network failure -> unknown_delivery, anchor session preserved
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    st.saveSession(cfg("a").targetFingerprint(),"S","msg_prev");
    List<String> u4=new ArrayList<>();
    DshClient cli=new DshClient(cfg("a"),st,(method,url,token,reqBody)->{
     u4.add(url);throw new IOException("network down");
    });
    Map<String,Object> out=cli.dispatch(m("task","check build four"));
    ok("unknown_delivery".equals(out.get("status")));
    ok("S".equals(st.sessionId()));ok(st.unknownDelivery());
    ok(u4.size()==1);ok(u4.get(0).contains("/sessions/S/runtime/messages"));
   }
   // 5) continued path 401 -> failed, pending cleared, NO create-and-start attempted
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    st.saveSession(cfg("a").targetFingerprint(),"S","msg_prev");
    List<String> u5=new ArrayList<>();
    DshClient cli=new DshClient(cfg("a"),st,(method,url,token,reqBody)->{
     u5.add(url);throw new DshClient.HttpStatusException(401,"");
    });
    Map<String,Object> out=cli.dispatch(m("task","check build five"));
    ok("failed".equals(out.get("status")));ok(Integer.valueOf(401).equals(out.get("httpStatus")));
    ok(!st.unknownDelivery());ok("".equals(st.clientMessageId()));
    ok(u5.size()==1);ok(u5.get(0).contains("/sessions/S/runtime/messages"));
   }
   // 6) multi-turn timeline attribution: our clientMessageId is the 2nd user message
   {
    Map<String,Object> st=m("state",m("sessionId","s","runtimeId","r","status","idle"));
    Map<String,Object> tl=m("sessionId","s","hasMore",false,"items",Arrays.asList(
      user("msg_turn1"),answer("done"),
      user("msg_current"),answer("done")));
    ok("completed".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl,true).get("status")));
    Map<String,Object> tl2=m("sessionId","s","hasMore",false,"items",Arrays.asList(
      user("msg_turn1"),answer("done"),
      user("msg_current"),answer("done"),
      user("msg_turn3"),answer("done")));
    ok("unknown".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl2,true).get("status")));
    Map<String,Object> tl3=m("sessionId","s","hasMore",false,"items",Arrays.asList(
      user("msg_turn1"),m("type","turn.end","turnId","turn_1","status","failed"),
      user("msg_current"),answer("done")));
    ok("completed".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl3,true).get("status")));
   }
   // 7) latest-window semantics for hasMore
   {
    Map<String,Object> st=m("state",m("sessionId","s","runtimeId","r","status","idle"));
    Map<String,Object> tl=m("sessionId","s","hasMore",true,"items",Arrays.asList(user("msg_current"),answer("done")));
    ok("completed".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl,true).get("status")));
    Map<String,Object> tl2=m("sessionId","s","hasMore",true,"items",Arrays.asList(user("msg_other"),answer("done")));
    ok("unknown".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl2,true).get("status")));
    ok("unknown".equals(DshClient.evaluateSessionStatus("s","msg_current",st,tl).get("status")));
   }
   // 8) MapStateStore keeps the anchor session while unconfirmed
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    st.savePending("fp","ANCHOR","msg_1");
    ok("ANCHOR".equals(st.sessionId()));ok(st.unknownDelivery());ok("msg_1".equals(st.clientMessageId()));
    st.savePending("fp","","msg_2");
    ok("".equals(st.sessionId()));ok(st.unknownDelivery());
   }
   // 9) HttpStatusException exposes the server error code
   {
    DshClient.HttpStatusException e=new DshClient.HttpStatusException(409,"session_archived");
    ok("session_archived".equals(e.code));ok(e.status==409);ok(e.sessionUnavailable());
    ok("".equals(new DshClient.HttpStatusException(500).code));
    ok(!new DshClient.HttpStatusException(500,"boom").sessionUnavailable());
    ok(!new DshClient.HttpStatusException(401,"").sessionUnavailable());
    ok(new DshClient.HttpStatusException(404,"").sessionUnavailable());
   }
   // 10) unknown_delivery blocks dispatch before any request is sent
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    st.savePending("fp","S","msg_1");
    int[] calls={0};
    DshClient cli=new DshClient(cfg("a"),st,(a,b,c,d)->{calls[0]++;return m();});
    ok("unknown_delivery".equals(cli.dispatch(m("task","check build six")).get("status")));
    ok(calls[0]==0);
   }
   // Target drift and invalid anchors refuse locally without clearing the session.
   for(String anchor:new String[]{"S_OLD","../invalid"}) {
    DshClient.MapStateStore st=new DshClient.MapStateStore();
    String fp=anchor.equals("S_OLD")?cfg("b").targetFingerprint():cfg("a").targetFingerprint();
    st.saveSession(fp,anchor,"msg_prev");int[] calls={0};
    Map<String,Object> result=new DshClient(cfg("a"),st,(verb,url,t,b)->{calls[0]++;return m();}).dispatch(m("task","check build"));
    ok("failed".equals(result.get("status")));ok(calls[0]==0);ok(anchor.equals(st.sessionId()));ok(fp.equals(st.targetFingerprint()));
   }
   // 12) same target fingerprint: the anchor is reused (no spurious reset)
   {
    DshClient.MapStateStore st12=new DshClient.MapStateStore();
    st12.saveSession(cfg("a").targetFingerprint(),"S_KEEP","msg_prev");
    List<String> u12=new ArrayList<>();
    DshClient cli12=new DshClient(cfg("a"),st12,(method,url,token,reqBody)->{
     u12.add(url);
     if(url.endsWith("/sessions/S_KEEP/runtime/messages"))return m("data",m("sessionId","S_KEEP"));
     throw new AssertionError("unexpected url "+url);
    });
    Map<String,Object> out12=cli12.dispatch(m("task","check build keep"));
    ok("continued".equals(out12.get("mode")));ok("S_KEEP".equals(out12.get("sessionId")));
    ok(u12.size()==1);ok(u12.get(0).endsWith("/sessions/S_KEEP/runtime/messages"));
   }
   // 13) empty stored fingerprint keeps the anchor (unbound store must not be silently reset)
   {
    DshClient.MapStateStore st13=new DshClient.MapStateStore();
    st13.saveSession("","S_UNBOUND","msg_prev");
    List<String> u13=new ArrayList<>();
    DshClient cli13=new DshClient(cfg("a"),st13,(method,url,token,reqBody)->{
     u13.add(url);
     if(url.endsWith("/sessions/S_UNBOUND/runtime/messages"))return m("data",m("sessionId","S_UNBOUND"));
     throw new AssertionError("unexpected url "+url);
    });
    Map<String,Object> out13=cli13.dispatch(m("task","check build unbound"));
     ok("continued".equals(out13.get("mode")));ok("S_UNBOUND".equals(out13.get("sessionId")));
    ok(u13.size()==1);
   }
   // A paused reservation holds LOCK: a competing dispatch cannot reserve another POST.
   {
    CountDownLatch reserving=new CountDownLatch(1),allowSave=new CountDownLatch(1),posting=new CountDownLatch(1),allowResponse=new CountDownLatch(1),secondStarted=new CountDownLatch(1);
    DelegatingStore st=new DelegatingStore(){
     public void savePending(String f,String anchor,String id){
      ok(Thread.holdsLock(DshClient.LOCK));reserving.countDown();
      try { if(!allowSave.await(3,TimeUnit.SECONDS))throw new AssertionError("save timeout"); }
      catch(InterruptedException e){throw new AssertionError(e);}
      super.savePending(f,anchor,id);
     }
    };
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    DshClient cli=new DshClient(cfg("a"),st,(verb,url,t,b)->{
     calls.incrementAndGet();posting.countDown();
     if(!allowResponse.await(3,TimeUnit.SECONDS))throw new AssertionError("response timeout");
     return m("sessionId","S");
    });
    ExecutorService workers=Executors.newFixedThreadPool(2);
    try {
     Future<Map<String,Object>> one=workers.submit(()->cli.dispatch(m("task","check build")));
     ok(reserving.await(3,TimeUnit.SECONDS));
     Future<Map<String,Object>> two=workers.submit(()->{secondStarted.countDown();return cli.dispatch(m("task","check build"));});
     ok(secondStarted.await(3,TimeUnit.SECONDS));allowSave.countDown();
     ok(posting.await(3,TimeUnit.SECONDS));ok("unknown_delivery".equals(two.get(3,TimeUnit.SECONDS).get("status")));
     allowResponse.countDown();ok("submitted".equals(one.get(3,TimeUnit.SECONDS).get("status")));ok(calls.get()==1);
    } finally {allowSave.countDown();allowResponse.countDown();workers.shutdownNow();}
   }
   // An anchor-only change invalidates a late response even when message and target still match.
   {
    DshClient.MapStateStore st=new DshClient.MapStateStore();st.saveSession(cfg("a").targetFingerprint(),"S","msg_prev");
    Map<String,Object> result=new DshClient(cfg("a"),st,(verb,url,t,b)->{
     synchronized(DshClient.LOCK){st.savePending(st.targetFingerprint(),"REPLACEMENT",st.clientMessageId());}
     return m();
    }).dispatch(m("task","check build"));
    ok("unknown_delivery".equals(result.get("status")));ok("REPLACEMENT".equals(st.sessionId()));ok(st.unknownDelivery());
   }
   // A silent success-write failure must not report submitted and must remain locked.
   {
    DelegatingStore st=new DelegatingStore(){public void saveSession(String f,String anchor,String id){}};
    Map<String,Object> result=new DshClient(cfg("a"),st,(verb,url,t,b)->m("sessionId","S")).dispatch(m("task","check build"));
    ok("unknown_delivery".equals(result.get("status")));ok(st.unknownDelivery());ok("S".equals(st.sessionId()));
   }
  System.out.println("PASS "+checks+" DshClient checks");
 }
 static void contractV3()throws Exception{
  for(String code:Arrays.asList("session is running","connector is offline","takeover disabled","other conflict")){
   DshClient.MapStateStore store=new DshClient.MapStateStore();store.saveSession(cfg("fixture-token").targetFingerprint(),"s","");
   final int[] posts={0},takes={0};DshClient client=new DshClient(cfg("fixture-token"),store,(method,url,token,body)->{
    ok(!Thread.holdsLock(DshClient.LOCK));if(url.endsWith("takeover")){takes[0]++;return m();}posts[0]++;throw new DshClient.HttpStatusException(409,code);
   });Map<String,Object> result=client.dispatch(m("task","check conflict"));ok("failed".equals(result.get("status")));ok(Integer.valueOf(409).equals(result.get("httpStatus")));ok(code.equals(result.get("code")));ok(!store.unknownDelivery());ok("s".equals(store.sessionId()));
   ok(posts[0]==(code.contains("takeover")?2:1));ok(takes[0]==(code.contains("takeover")?1:0));
  }
  DshClient.MapStateStore store=new DshClient.MapStateStore();store.saveSession(cfg("fixture-token").targetFingerprint(),"s","");List<String> calls=new ArrayList<>();List<String> ids=new ArrayList<>();
  DshClient client=new DshClient(cfg("fixture-token"),store,(method,url,token,body)->{ok(!Thread.holdsLock(DshClient.LOCK));calls.add(url);if(url.endsWith("takeover")){ok(body.isEmpty());return m();}ids.add(String.valueOf(body.get("clientMessageId")));if(ids.size()==1)throw new DshClient.HttpStatusException(409,"session is read-only until takeover is enabled");return m();});
  ok("submitted".equals(client.dispatch(m("task","hello takeover")).get("status")));ok(calls.size()==3);ok(calls.get(1).endsWith("/s/takeover"));ok(ids.get(0).equals(ids.get(1)));ok(!store.unknownDelivery());
  java.lang.reflect.Method parser=DshClient.class.getDeclaredMethod("readErrorCode",java.net.HttpURLConnection.class);parser.setAccessible(true);
  for(String json:Arrays.asList("{\"detail\":\"session is running\"}","{\"detail\":{\"code\":\"session is running\"}}","{\"detail\":{\"message\":\"session is running\"}}")){
   java.net.HttpURLConnection response=new java.net.HttpURLConnection(new java.net.URL("https://example.com")){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public java.io.InputStream getErrorStream(){return new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));}};
   ok("session is running".equals(parser.invoke(null,response)));
  }
 }
}