package com.turboio.addon;
import java.util.*;
public final class DshSelectionDraftTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 interface Work{void run()throws Exception;}
 static void fails(String code,Work w)throws Exception{try{w.run();throw new AssertionError("accepted "+code);}catch(DshSelectionDraft.Failure e){ok(code.equals(e.code));}}
 static Map<String,Object> map(Object... a){return DshClient.mapOf(a);}
 @SuppressWarnings("unchecked") static Map<String,Object> section(Map<String,Object> s,String k){return (Map<String,Object>)s.get(k);}
 static String selected(Map<String,Object>s,String l){Object v=section(s,"selected").get(l);return v instanceof Map?String.valueOf(((Map<?,?>)v).get("id")):"";}
 static String id(Map<String,Object>s){return String.valueOf(s.get("draftId"));}static long rev(Map<String,Object>s){return ((Number)s.get("draftRevision")).longValue();}
 static class Fake implements DshSelectionDraft.Authority {
  Map<String,Object> state=map("authenticated",true,"authGeneration",0L,"revision","R0","connectorId","c","projectId","p","runtimeId","r","sessionId","s","model","m","permission","perm");
  boolean fail,missing,storeFail;int writes;DshSelectionDraft editor;Runnable during;
  public Map<String,Object> current(){return new LinkedHashMap<>(state);}
  public List<DshSelectionDraft.Candidate> query(String level,Map<String,String> p){
   ok(!Thread.holdsLock(DshClient.LOCK));if(during!=null){Runnable f=during;during=null;f.run();}if(fail)throw new RuntimeException("private error");
   List<DshSelectionDraft.Candidate> out=new ArrayList<>();
   String[] ids="connectors".equals(level)?new String[]{"c","c2"}:"projects".equals(level)?new String[]{"p","p2"}:"runtimes".equals(level)?new String[]{"r","r2"}:"sessions".equals(level)?(missing?new String[]{DshDirectory.NEW_SESSION}:new String[]{"s",DshDirectory.NEW_SESSION}):"models".equals(level)?new String[]{"m"}:new String[]{"perm"};
   for(String id:ids)out.add(new DshSelectionDraft.Candidate(id,"Name "+id,"projects".equals(level)?"C:/secret/path":"note",true));return out;
  }
  public Map<String,Object> save(Map<String,String> v)throws Exception{ok(Thread.holdsLock(DshClient.LOCK));if(storeFail){state.put("storageFailed",true);throw new DshSelectionDraft.Failure("STORAGE_FAILED");}writes++;state.put("revision","R"+writes);state.put("sessionId",DshDirectory.NEW_SESSION.equals(v.get("sessions"))?"":v.get("sessions"));return current();}
 }
 static Map<String,Object> load(DshSelectionDraft d,Map<String,Object>s,String level)throws Exception{return d.list(id(s),rev(s),level,10);}
 static Map<String,Object> full(DshSelectionDraft d,Map<String,Object>s)throws Exception{for(String l:DshSelectionDraft.LEVELS)s=load(d,s,l);return s;}
 public static void main(String[] args)throws Exception{
  homeV3();
  Fake f=new Fake();DshSelectionDraft d=new DshSelectionDraft(f);f.editor=d;Map<String,Object>s=d.open();ok(((Number)s.get("authGeneration")).longValue()==0);ok(!Boolean.TRUE.equals(section(s,"valid").get("sessions")));
  s=full(d,s);for(String level:DshSelectionDraft.LEVELS)ok(Boolean.TRUE.equals(section(s,"valid").get(level)));ok("".equals(section(section(s,"selected"),"projects").get("subtitle")));
  long initial=rev(s);Map<String,Object> same=d.choose(id(s),rev(s),"connectors","c");ok(rev(same)==initial);ok("s".equals(selected(same,"sessions")));ok("m".equals(selected(same,"models")));
  Map<String,Object> changed=d.choose(id(s),rev(s),"projects","p2");ok(selected(changed,"sessions").isEmpty());ok("r".equals(selected(changed,"runtimes")));ok("m".equals(selected(changed,"models")));ok(f.writes==0);
  String oldId=id(s);long oldRev=rev(s);fails("STALE_DRAFT",()->d.choose(oldId,oldRev,"sessions","s"));
  s=d.open();s=full(d,s);s=d.choose(id(s),rev(s),"runtimes","r2");ok(selected(s,"sessions").isEmpty());ok(selected(s,"models").isEmpty());ok(selected(s,"permissions").isEmpty());ok("p".equals(selected(s,"projects")));
  s=d.open();s=full(d,s);s=d.choose(id(s),rev(s),"connectors","c2");for(String l:Arrays.asList("projects","runtimes","sessions","models","permissions"))ok(selected(s,l).isEmpty());
  s=d.open();s=full(d,s);f.missing=true;s=load(d,s,"sessions");ok("s".equals(selected(s,"sessions")));ok(!Boolean.TRUE.equals(section(s,"valid").get("sessions")));final Map<String,Object> missing=s;fails("INVALID_TARGET",()->d.save(id(missing),rev(missing)));ok("s".equals(f.state.get("sessionId")));
  f.missing=false;s=d.open();s=full(d,s);f.fail=true;final Map<String,Object> failing=s;fails("DIRECTORY_FAILED",()->load(d,failing,"sessions"));ok("s".equals(f.state.get("sessionId")));fails("INVALID_TARGET",()->d.save(id(failing),rev(failing)));f.fail=false;
  s=d.open();s=full(d,s);s=d.choose(id(s),rev(s),"sessions",DshDirectory.NEW_SESSION);Map<String,Object> saved=d.save(id(s),rev(s));ok("".equals(f.state.get("sessionId")));ok(saved.get("saved") instanceof Map);ok(saved.containsKey("requestDraftRevision"));ok(f.writes==1);
  s=d.open();final Map<String,Object> locked=s;f.state.put("unknownDelivery",true);fails("DELIVERY_UNKNOWN",()->d.choose(id(locked),rev(locked),"connectors","c2"));fails("DELIVERY_UNKNOWN",()->d.save(id(locked),rev(locked)));ok(f.writes==1);f.state.remove("unknownDelivery");
  s=d.open();s=full(d,s);f.storeFail=true;final Map<String,Object> broken=s;fails("STORAGE_FAILED",()->d.save(id(broken),rev(broken)));fails("STORAGE_FAILED",()->load(d,broken,"sessions"));ok(f.writes==1);f.storeFail=false;f.state.remove("storageFailed");
  s=d.open();final Map<String,Object> stale=s;f.during=()->f.state.put("authGeneration",1L);fails("STALE_AUTH",()->load(d,stale,"connectors"));
  s=d.open();final Map<String,Object> moving=s;f.during=()->f.state.put("revision","EXTERNAL");fails("STALE_SELECTION",()->load(d,moving,"connectors"));
  s=d.open();s=load(d,s,"connectors");final Map<String,Object> parent=s;f.during=()->{try{d.choose(id(parent),rev(parent),"connectors","c2");}catch(Exception e){throw new RuntimeException(e);}};fails("STALE_DRAFT",()->load(d,parent,"projects"));
  Map<String,Object> error=d.error("DIRECTORY_FAILED","old",12,"sessions",13);ok("old".equals(error.get("draftId")));ok(Long.valueOf(12).equals(error.get("requestDraftRevision")));ok(Long.valueOf(13).equals(error.get("requestId")));ok("sessions".equals(error.get("level")));
  d.close(id(s));d.close(id(s));
  List<DshDirectory.Option> choices=DshDirectory.projectChoices(Arrays.asList(map("id","p","name","Project","path","C:/private"),map("id","q","connectorId","other","name","Wrong"),map("id","p","connectorId","c","name","Project","workspacePath","C:/private")),"c");ok(choices.size()==1);ok("p".equals(choices.get(0).id));ok("Project".equals(choices.get(0).label));ok(choices.get(0).subtitle.isEmpty());
  f.state.put("storageFailed",true);f.state.put("unknownDelivery",true);fails("STORAGE_FAILED",()->d.open());f.state.remove("storageFailed");fails("DELIVERY_UNKNOWN",()->d.open());f.state.remove("unknownDelivery");
  for(String label:Arrays.asList("","C:/private/project","C:\\private\\project","/Users/private","~/private","../private","\\\\server\\share"))ok("项目".equals(DshDirectory.projectLabel(label)));
  ok("Named project".equals(DshDirectory.projectLabel("Named project")));
  f.state.put("projectId","C:/private/project");Map<String,Object> restored=d.open();ok("已选择项目".equals(section(section(restored,"selected"),"projects").get("label")));ok("".equals(section(section(restored,"selected"),"projects").get("subtitle")));ok("C:/private/project".equals(selected(restored,"projects")));
  List<DshDirectory.Option> hidden=DshDirectory.projectChoices(Collections.singletonList(map("id","C:/secret/id","connectorId","c","path","C:/secret/path")),"c");ok("项目".equals(hidden.get(0).label));ok(hidden.get(0).subtitle.isEmpty());ok("C:/secret/id".equals(hidden.get(0).id));
  System.out.println("DshSelectionDraftTest "+checks+" checks passed");
 }
 static void homeV3()throws Exception{
  Fake f=new Fake();DshSelectionDraft d=new DshSelectionDraft(f);Map<String,Object> s=d.open();
  for(String level:DshSelectionDraft.HOME_LEVELS)s=load(d,s,level);
  Map<String,Object> saved=d.save(id(s),rev(s));ok(f.writes==1);ok("s".equals(f.state.get("sessionId")));ok("m".equals(selected(saved,"models")));ok("perm".equals(selected(saved,"permissions")));
  f.state.put("sessionId","");s=d.open();for(String level:DshSelectionDraft.HOME_LEVELS)s=load(d,s,level);saved=d.save(id(s),rev(s));ok("".equals(f.state.get("sessionId")));ok(DshDirectory.NEW_SESSION.equals(selected(saved,"sessions")));
  f.state.put("sessionId","s");s=d.open();for(String level:DshSelectionDraft.HOME_LEVELS)s=load(d,s,level);s=d.choose(id(s),rev(s),"projects","p2");saved=d.save(id(s),rev(s));ok("".equals(f.state.get("sessionId")));ok(DshDirectory.NEW_SESSION.equals(selected(saved,"sessions")));
  s=d.open();for(String level:DshSelectionDraft.HOME_LEVELS)s=load(d,s,level);final Map<String,Object> pending=s;f.during=()->f.state.put("authGeneration",9L);fails("STALE_AUTH",()->d.save(id(pending),rev(pending)));
 }
}
