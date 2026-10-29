package com.turboio.addon;
import java.util.*;
public final class AaDispatchFlowTest {
 static int checks,posts,gets;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static DshClient.Config cfg=new DshClient.Config(true,"https://example.com","token","c","p","r","dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0","dsh:permission:cmVhZC1vbmx5");
 static class Store implements DshClient.StateStore {
  final DshClient.MapStateStore data=new DshClient.MapStateStore();String expected,expectedRevision;boolean fresh,status;
  String rev(){return data.sessionId()+"|"+data.clientMessageId()+"|"+data.unknownDelivery();}
  void guard(){ok(Thread.holdsLock(DshClient.LOCK));String error=AaBridgePolicy.checkDispatch(0,0,expectedRevision,rev(),expected,data.sessionId(),fresh);if(!error.isEmpty())throw new IllegalStateException(error);}
  void capture(String session,boolean newSession){expected=session;expectedRevision=rev();fresh=newSession;}
  public String sessionId(){if(status)guard();return data.sessionId();}public String clientMessageId(){return data.clientMessageId();}public boolean unknownDelivery(){return data.unknownDelivery();}public String targetFingerprint(){return data.targetFingerprint();}
  public void savePending(String f,String s,String id){guard();data.savePending(f,s,id);}public void saveSession(String f,String s,String id){data.saveSession(f,s,id);}public void clearPending(){data.clearPending();}public void clearSession(){data.clearSession();}public void clearSessionAndPending(){data.clearSessionAndPending();}
 }
 public static void main(String[] args)throws Exception {
  Store s=new Store();s.data.saveSession(cfg.targetFingerprint(),"A","");
  DshClient client=new DshClient(cfg,s,(method,url,token,body)->{
   ok(!Thread.holdsLock(DshClient.LOCK));
   if("POST".equals(method)){posts++;return DshClient.mapOf("sessionId","A");}
   gets++;
   if(url.contains("runtime/state"))return DshClient.mapOf("state",DshClient.mapOf("sessionId","A","runtimeId","r","status","idle"));
   return DshClient.mapOf("sessionId","A","hasMore",false,"items",Arrays.asList(DshClient.mapOf("role","user","type","message","source",DshClient.mapOf("clientMessageId",s.data.clientMessageId())),DshClient.mapOf("role","assistant","type","message","status","done","content","result")));
  });
  s.capture("B",false);boolean rejected=false;try{client.dispatch(DshClient.mapOf("task","hello"));}catch(IllegalStateException e){rejected=true;}ok(rejected&&posts==0);
  s.capture("A",false);String before=s.rev();ok("submitted".equals(client.dispatch(DshClient.mapOf("task","hello")).get("status")));ok(posts==1);ok(!before.equals(s.rev()));
  s.capture("A",false);s.status=true;ok("completed".equals(client.status(Collections.emptyMap()).get("status")));s.status=false;ok(gets==2);
  s.capture("A",false);ok("submitted".equals(client.dispatch(DshClient.mapOf("task","continue")).get("status")));ok(posts==2);
  s.capture("B",false);s.status=true;rejected=false;try{client.status(Collections.emptyMap());}catch(IllegalStateException e){rejected=true;}s.status=false;ok(rejected&&gets==2);
  s.capture("B",false);synchronized(DshClient.LOCK){rejected=false;try{s.guard();s.data.clearSession();}catch(IllegalStateException e){rejected=true;}}ok(rejected&&"A".equals(s.data.sessionId()));
  ok("".equals(AaBridgePolicy.checkDispatch(0,0,"r","r","","",true)));ok(!"".equals(AaBridgePolicy.checkDispatch(0,0,"r","r","","A",true)));ok(!"".equals(AaBridgePolicy.checkDispatch(0,0,"r","r","","",false)));ok(!AaBridgePolicy.successful("unknown_delivery"));
  System.out.println("AaDispatchFlowTest "+checks+" checks passed");
 }
}
