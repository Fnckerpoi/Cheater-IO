package com.turboio.addon;
import java.util.*;
/** Asserts the frozen Bundle field names, not just behaviour. */
public final class DshDraftContractTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void exact(Set<String> actual,String... expected){ok(actual.equals(new TreeSet<>(Arrays.asList(expected))));}
 static Map<String,Object> map(Object...a){return DshClient.mapOf(a);}
 static class Fake implements DshSelectionDraft.Authority {
  final Map<String,Object> state=map("authenticated",true,"authGeneration",0L,"revision","R0","connectorId","c","projectId","p","runtimeId","r","sessionId","s","model","m","permission","perm");
  public Map<String,Object> current(){return new LinkedHashMap<>(state);}
  public List<DshSelectionDraft.Candidate> query(String level,Map<String,String> p)throws Exception{
   if("fail".equals(level))throw new Exception("boom");
   return Collections.singletonList(new DshSelectionDraft.Candidate("connectors".equals(level)?"c":"projects".equals(level)?"p":"runtimes".equals(level)?"r":"sessions".equals(level)?"s":"models".equals(level)?"m":"perm","L","S",true));
  }
  public Map<String,Object> save(Map<String,String> v)throws Exception{state.put("revision","R1");return current();}
 }
 public static void main(String[] args)throws Exception{
  DshSelectionDraft d=new DshSelectionDraft(new Fake());
  Map<String,Object> open=d.open();String id=String.valueOf(open.get("draftId"));long rev=((Number)open.get("draftRevision")).longValue();
  exact(new TreeSet<>(open.keySet()),"ok","errorCode","draftId","draftRevision","authGeneration","baseRevision","selected","valid","newSessionId");
  ok(Boolean.TRUE.equals(open.get("ok")));ok("@new-session".equals(open.get("newSessionId")));
  Map<String,Object> list=d.list(id,rev,"connectors",7L);
  exact(new TreeSet<>(list.keySet()),"ok","errorCode","draftId","draftRevision","authGeneration","baseRevision","selected","valid","newSessionId","options","parents","requestId","level","requestDraftRevision");
  ok(Long.valueOf(7).equals(list.get("requestId")));ok("connectors".equals(list.get("level")));ok(Long.valueOf(rev).equals(list.get("requestDraftRevision")));
  @SuppressWarnings("unchecked") Map<String,Object> parents=(Map<String,Object>)list.get("parents");exact(new TreeSet<>(parents.keySet()),"connectorId","projectId","runtimeId");
  @SuppressWarnings("unchecked") List<Map<String,Object>> options=(List<Map<String,Object>>)list.get("options");ok(options.size()==1);
  exact(new TreeSet<>(options.get(0).keySet()),"id","label","subtitle","available");
  @SuppressWarnings("unchecked") Map<String,Object> selected=(Map<String,Object>)list.get("selected");
  @SuppressWarnings("unchecked") Map<String,Object> one=(Map<String,Object>)selected.get("connectors");exact(new TreeSet<>(one.keySet()),"id","label","subtitle","available");
  @SuppressWarnings("unchecked") Map<String,Object> valid=(Map<String,Object>)list.get("valid");
  exact(new TreeSet<>(valid.keySet()),"connectors","projects","runtimes","sessions","models","permissions");
  Map<String,Object> choose=d.choose(id,((Number)list.get("draftRevision")).longValue(),"connectors","c");
  exact(new TreeSet<>(choose.keySet()),"ok","errorCode","draftId","draftRevision","authGeneration","baseRevision","selected","valid","newSessionId","requestDraftRevision");
  Map<String,Object> failed;
  try{d.list(id,999L,"connectors",8L);throw new AssertionError("stale accepted");}
  catch(DshSelectionDraft.Failure stale){ok("STALE_DRAFT".equals(stale.code));failed=d.error(stale.code,id,999L,"connectors",8L);}
  exact(new TreeSet<>(failed.keySet()),"ok","errorCode","draftId","requestDraftRevision","authGeneration","level","requestId");
  ok(Boolean.FALSE.equals(failed.get("ok")));ok("STALE_DRAFT".equals(failed.get("errorCode")));ok(Long.valueOf(999).equals(failed.get("requestDraftRevision")));ok(Long.valueOf(8).equals(failed.get("requestId")));
  for(String code:Arrays.asList("HOST_CLOSED","WORKER_REQUIRED","STALE_AUTH","STALE_SELECTION","STALE_DRAFT","DELIVERY_UNKNOWN","STORAGE_FAILED","REQUEST_FAILED")){
   Map<String,Object> boundary=d.error(code,id,rev,"sessions",37L);
   exact(new TreeSet<>(boundary.keySet()),"ok","errorCode","draftId","requestDraftRevision","authGeneration","level","requestId");
   ok(Boolean.FALSE.equals(boundary.get("ok")));ok(code.equals(boundary.get("errorCode")));ok(id.equals(boundary.get("draftId")));ok(Long.valueOf(rev).equals(boundary.get("requestDraftRevision")));ok("sessions".equals(boundary.get("level")));ok(Long.valueOf(37L).equals(boundary.get("requestId")));ok(Long.valueOf(0L).equals(boundary.get("authGeneration")));
  }
  Map<String,Object> closed=d.close(id);exact(new TreeSet<>(closed.keySet()),"ok","errorCode");ok(Boolean.TRUE.equals(closed.get("ok")));
  Map<String,Object> afterClose;
  try{afterClose=d.list(id,0L,"connectors",9L);throw new AssertionError("closed draft accepted");}
  catch(DshSelectionDraft.Failure stale){ok("STALE_DRAFT".equals(stale.code));afterClose=d.error(stale.code,id,0L,"connectors",9L);}
  ok(id.equals(afterClose.get("draftId")));
  DshSelectionDraft other=new DshSelectionDraft(new Fake());Map<String,Object> second=other.open();
  ok(!id.equals(second.get("draftId")));
  Map<String,Object> oldError=d.error("STALE_DRAFT",id,0,"sessions",99);ok(Long.valueOf(0L).equals(oldError.get("authGeneration")));
  Map<String,Object> unknownError=d.error("STALE_DRAFT","foreign",0,"sessions",99);ok(Long.valueOf(-1L).equals(unknownError.get("authGeneration")));
  System.out.println("DshDraftContractTest "+checks+" checks passed");
 }
}
