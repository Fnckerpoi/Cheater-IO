package com.turboio.addon;
import java.util.*;
public final class AaBridgePolicyTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 static final class Model{static final String URL="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";static final String PERM="dsh:permission:cmVhZC1vbmx5";}
 public static void main(String[] args)throws Exception{
  bridgeV3();
  check("".equals(AaBridgePolicy.check(1,1,"A","A","s","s")));
  check("STALE_AUTH".equals(AaBridgePolicy.check(1,2,"A","A","s","s")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,"A","B","s","s")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,"","A","s","s")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,null,"A","s","s")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,"A","A","other","bound")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,"A","A","","")));
  check("STALE_SELECTION".equals(AaBridgePolicy.check(1,1,"A","A","s","")));
  check(AaBridgePolicy.sessionMatches("s","s"));
  check(!AaBridgePolicy.sessionMatches("s","other"));
  check(!AaBridgePolicy.sessionMatches("",""));
  check(!AaBridgePolicy.sessionMatches(null,"s"));
  for(String state:new String[]{"unknown_delivery","unknown","failed","locked","","new_state"})check(!AaBridgePolicy.successful(state));
  for(String state:new String[]{"submitted","completed","ended","session_selected","running","waiting_approval","idle_no_reply"})check(AaBridgePolicy.successful(state));
  check(AaBridgePolicy.accepted("submitted"));
  for(String state:new String[]{"completed","ended","running","session_selected","unknown_delivery",""})check(!AaBridgePolicy.accepted(state));
  check(AaBridgePolicy.completed("completed"));
  for(String state:new String[]{"submitted","ended","running",""})check(!AaBridgePolicy.completed(state));
  check(AaBridgePolicy.unknownDelivery("unknown_delivery"));
  for(String state:new String[]{"submitted","completed","unknown","failed",""})check(!AaBridgePolicy.unknownDelivery(state));
  DshClient.Config config=new DshClient.Config(true,"https://example.com","token","connector","project","runtime",Model.URL,Model.PERM);
  check(config.ready());
  final int[] posts={0};
  DshClient.StateStore store=new DshClient.StateStore(){
   public String sessionId(){return "bound-session";}
   public boolean unknownDelivery(){return false;}
   public String clientMessageId(){return "";}
   public String targetFingerprint(){return "";}
   public void savePending(String f,String s,String id){throw new IllegalStateException("STALE_SELECTION");}
   public void clearPending(){}
   public void saveSession(String f,String s,String id){}
   public void clearSession(){}
   public void clearSessionAndPending(){}
  };
  DshClient client=new DshClient(config,store,(method,url,token,body)->{posts[0]++;return DshClient.mapOf();});
  try{client.dispatch(DshClient.mapOf("task","hello task"));}
  catch(IllegalStateException expected){check("STALE_SELECTION".equals(expected.getMessage()));}
  catch(Exception other){check(false);}
  check(posts[0]==0);
  check(!store.unknownDelivery());
  System.out.println("AaBridgePolicyTest "+n+" checks passed");
 }
 static void bridgeV3()throws Exception{
  check(AaHostBridge.bindMatches(true,"","new-revision","B","A",true));
  check(AaHostBridge.bindMatches(true,"R","R","B","A",true));
  check(!AaHostBridge.bindMatches(true,"old","R","B","A",true));
  check(!AaHostBridge.bindMatches(false,"R","R","B","A",true));
  check(AaHostBridge.bindMatches(true,"","R","","A",false));
  DshClient.MapStateStore moving=new DshClient.MapStateStore();moving.saveSession("target","A","");
  String requestSession="A",expectedRevision="";String[] currentRevision={"before"};
  boolean before=AaHostBridge.bindMatches(true,expectedRevision,currentRevision[0],requestSession,moving.sessionId(),true);check(before);
  DshClient.Transport changesDuringTakeover=(method,url,token,body)->{
   check(!Thread.holdsLock(DshClient.LOCK));
   if(url.endsWith("/takeover")){synchronized(DshClient.LOCK){moving.saveSession("target","concurrent-session","");currentRevision[0]="after";}}
   return DshClient.mapOf();
  };
  AaHostBridge.takeoverSelections(changesDuringTakeover,"https://example.com","token","selected-session",Model.URL,Model.PERM);
  synchronized(DshClient.LOCK){
   boolean after=AaHostBridge.bindMatches(true,expectedRevision,currentRevision[0],requestSession,moving.sessionId(),true);
   check(after==before);check(after);if(after)moving.saveSession("target","selected-session","");
  }
  check("selected-session".equals(moving.sessionId()));check(!moving.unknownDelivery());
  List<String> calls=new ArrayList<>();
  DshClient.Transport transport=(method,url,token,body)->{check(!Thread.holdsLock(DshClient.LOCK));calls.add(method+" "+url);
   if("PATCH".equals(method)){check(body.containsKey("selections"));throw new DshClient.HttpStatusException(409,"session is running");}
   if(url.endsWith("/takeover")){check(body.isEmpty());return DshClient.mapOf();}
   return DshClient.mapOf("catalog",DshClient.mapOf("runtime","dsh","models",Collections.singletonList(DshClient.mapOf("displayName","Model","selectionId",Model.URL))));
  };
  java.util.List<DshSelectionDraft.Candidate> rows=AaHostBridge.catalogOptions(transport,"https://example.com","token","c","r","models");check(rows.size()==1);check(Model.URL.equals(rows.get(0).id));
  AaHostBridge.takeoverSelections(transport,"https://example.com","token","s",Model.URL,Model.PERM);check(calls.size()==3);check(calls.get(1).endsWith("/s/takeover"));check(calls.get(2).startsWith("PATCH "));
 }
}
