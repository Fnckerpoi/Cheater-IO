package com.turboio.addon;
import java.util.*;
public final class DshSessionSelectionTest {
 static int checks;
 static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
 static Map<String,Object> row(){return DshClient.mapOf("id","s","connectorId","c","projectId","p","runtimeId","r","runtimeType","dsh","status","idle","archived",false,"title","Same");}
 static List<DshDirectory.Option> list(Map<String,Object> row){return DshDirectory.sessions(Collections.singletonList(row),"c","p","r");}
 public static void main(String[] args)throws Exception{
  ok(!DshDirectory.NEW_SESSION.isEmpty());ok(list(row()).size()==1);
  for(String key:Arrays.asList("id","connectorId","projectId","runtimeId","runtimeType","status","archived")){Map<String,Object> r=row();r.remove(key);ok(list(r).isEmpty());}
  for(String key:Arrays.asList("connectorId","projectId","runtimeId","runtimeType")){Map<String,Object> r=row();r.put(key,"wrong");ok(list(r).isEmpty());}
  for(String id:Arrays.asList("a%2fb","a\\b","../bad","a/b","a?b","a#b"," a ","",DshDirectory.NEW_SESSION)){Map<String,Object> r=row();r.put("id",id);ok(list(r).isEmpty());}
  for(String state:Arrays.asList("archived","deleted","missing")){Map<String,Object> r=row();r.put("status",state);ok(list(r).isEmpty());}
  Map<String,Object> r=row();r.put("archived",true);ok(list(r).isEmpty());r=row();r.put("archivedAt","date");ok(list(r).isEmpty());
  r=row();r.remove("runtimeType");r.put("runtime","dsh");ok(list(r).size()==1);r.put("runtimeType","");ok(list(r).isEmpty());
  r=row();r.put("id","s2");List<DshDirectory.Option> same=DshDirectory.sessions(Arrays.asList(row(),r),"c","p","r");ok(same.size()==2);ok(!same.get(0).subtitle.equals(same.get(1).subtitle));
  DshClient.Config cfg=new DshClient.Config(true,"https://example.com","token","c","p","r",DshClientTest.MODEL,DshClientTest.PERM);
  DshClient.MapStateStore store=new DshClient.MapStateStore();store.saveSession(cfg.targetFingerprint(),"s","");int[] calls={0};
  DshClient client=new DshClient(cfg,store,(method,url,token,body)->{calls[0]++;ok(url.endsWith("/s/runtime/state"));return DshClient.mapOf("state",DshClient.mapOf("sessionId","s","runtimeId","r","status","idle","answer","OLD"));});
  Map<String,Object> state=client.status(Collections.emptyMap());ok("session_selected".equals(state.get("status")));ok(!state.containsKey("answer"));ok(calls[0]==1);ok("idle".equals(state.get("runtimeState")));
  for(String key:Arrays.asList("sessionId","runtimeId")){final String field=key;DshClient wrong=new DshClient(cfg,store,(m,u,t,b)->{Map<String,Object> v=DshClient.mapOf("sessionId","s","runtimeId","r");v.put(field,"wrong");return DshClient.mapOf("state",v);});ok("unknown".equals(wrong.status(Collections.emptyMap()).get("status")));}
  DshClient append=new DshClient(cfg,store,(m,u,t,b)->{ok("POST".equals(m));ok(u.endsWith("/s/runtime/messages"));return DshClient.mapOf();});ok("continued".equals(append.dispatch(DshClient.mapOf("task","continue work")).get("mode")));ok("s".equals(store.sessionId()));
  ok("msg".equals(DshSettings.Policy.savedMessage(false,"s","s","f","f","msg")));ok(DshSettings.Policy.savedMessage(true,"s","s","f","f","msg").isEmpty());ok(DshSettings.Policy.savedMessage(false,"s2","s","f","f","msg").isEmpty());
  DshDirectory d=new DshDirectory();d.connector("c");d.project("p");d.runtime("r");long gen=d.generation();
  ok(DshSettings.Policy.sessionCurrent(1,1,gen,gen,2,2,"c","p","r",d));
  d.project("other");ok(!DshSettings.Policy.sessionCurrent(1,1,gen,gen,2,2,"c","p","r",d));d.project("p");
  ok(!DshSettings.Policy.sessionCurrent(1,2,gen,gen,2,2,"c","p","r",d));ok(!DshSettings.Policy.sessionCurrent(1,1,gen,gen,2,3,"c","p","r",d));ok(!DshSettings.Policy.sessionCurrent(1,1,gen,gen+1,2,2,"c","p","r",d));
  ok(DshSettings.Policy.find(same,"missing")==null);ok(DshSettings.Policy.find(same,DshDirectory.NEW_SESSION)==null);
  System.out.println("DshSessionSelectionTest: "+checks+" passed");
 }
}