package com.turboio.addon;

import java.util.*;

/** One host-owned, non-persistent editor; queries never hold the submission lock. */
final class DshSelectionDraft {
    static final List<String> LEVELS=Collections.unmodifiableList(Arrays.asList("connectors","projects","runtimes","sessions","models","permissions"));
    static final List<String> HOME_LEVELS=Collections.unmodifiableList(Arrays.asList("connectors","projects","runtimes"));
    private Map<String,String> savedParents=Collections.emptyMap();
    private String savedSession="";
    interface Authority {
        Map<String,Object> current() throws Exception;
        List<Candidate> query(String level,Map<String,String> parents) throws Exception;
        Map<String,Object> save(Map<String,String> selected) throws Exception;
    }
    static final class Candidate {
        final String id,label,subtitle;final boolean available;
        Candidate(String id,String label,String subtitle,boolean available){this.id=id;this.label=label;this.subtitle=subtitle;this.available=available;}
        Map<String,Object> map(){return DshClient.mapOf("id",id,"label",label,"subtitle",subtitle,"available",available);}
    }
    static final class Failure extends Exception {final String code;Failure(String code){super(code);this.code=code;}}
    private final Authority authority;
    private String id="",base="";private long auth,revision;
    private final Map<String,Candidate> selected=new LinkedHashMap<>();
    private final Map<String,List<Candidate>> options=new LinkedHashMap<>();
    private final Map<String,Long> requests=new HashMap<>();
    private final Map<String,Long> draftEpochs=new LinkedHashMap<String,Long>(){protected boolean removeEldestEntry(Map.Entry<String,Long> entry){return size()>32;}};
    DshSelectionDraft(Authority authority){this.authority=authority;}
    static String text(Map<String,Object> row,String key){Object v=row.get(key);return v instanceof String?(String)v:"";}
    static long number(Map<String,Object> row,String key){Object v=row.get(key);return v instanceof Number?((Number)v).longValue():-1;}
    private void unlocked(Map<String,Object> state)throws Failure {
        if(Boolean.TRUE.equals(state.get("storageFailed")))throw new Failure("STORAGE_FAILED");
        if(Boolean.TRUE.equals(state.get("unknownDelivery")))throw new Failure("DELIVERY_UNKNOWN");
        if(!Boolean.TRUE.equals(state.get("authenticated")))throw new Failure("AUTH_REQUIRED");
    }
    private void check(String draft,long expected)throws Exception {
        if(id.isEmpty()||!id.equals(draft)||expected!=revision)throw new Failure("STALE_DRAFT");
        Map<String,Object> state=authority.current();unlocked(state);
        if(number(state,"authGeneration")!=auth)throw new Failure("STALE_AUTH");
        if(!base.equals(text(state,"revision")))throw new Failure("STALE_SELECTION");
    }
    Map<String,Object> open()throws Exception {synchronized(DshClient.LOCK){
        Map<String,Object> state=authority.current();unlocked(state);
        id=UUID.randomUUID().toString();revision=0;auth=number(state,"authGeneration");base=text(state,"revision");draftEpochs.put(id,auth);selected.clear();options.clear();requests.clear();
        String[] keys={"connectorId","projectId","runtimeId","sessionId","model","permission"};
        for(int i=0;i<LEVELS.size();i++){String value=text(state,keys[i]);if(i==3&&value.isEmpty())value=DshDirectory.NEW_SESSION;if(!value.isEmpty())selected.put(LEVELS.get(i),new Candidate(value,i==1?"已选择项目":value,"",true));}
        savedParents=new LinkedHashMap<>(parents());savedSession=text(state,"sessionId");
        return snapshot();
    }}
    private String value(String level){Candidate c=selected.get(level);return c==null?"":c.id;}
    private Map<String,String> parents(){Map<String,String> p=new LinkedHashMap<>();p.put("connectorId",value("connectors"));p.put("projectId",value("projects"));p.put("runtimeId",value("runtimes"));return p;}
    private boolean parentsReady(String level){if("connectors".equals(level))return true;if(value("connectors").isEmpty())return false;if("sessions".equals(level))return !value("projects").isEmpty()&&!value("runtimes").isEmpty();if("models".equals(level)||"permissions".equals(level))return !value("runtimes").isEmpty();return true;}
    Map<String,Object> list(String draft,long expected,String level,long requestId)throws Exception {
        final Map<String,String> parent;final long ticket;final long capturedAuth;
        synchronized(DshClient.LOCK){check(draft,expected);requireLevel(level);if(!parentsReady(level))throw new Failure("INVALID_TARGET");parent=parents();capturedAuth=auth;ticket=requests.getOrDefault(level,0L)+1;requests.put(level,ticket);options.remove(level);}
        final List<Candidate> rows;
        try{rows=authority.query(level,Collections.unmodifiableMap(parent));}
        catch(Exception failure){synchronized(DshClient.LOCK){check(draft,expected);if(requests.getOrDefault(level,0L)!=ticket||!parent.equals(parents()))throw new Failure("STALE_DRAFT");}throw new Failure("DIRECTORY_FAILED");}
        synchronized(DshClient.LOCK){
            check(draft,expected);if(capturedAuth!=auth||requests.getOrDefault(level,0L)!=ticket||!parent.equals(parents()))throw new Failure("STALE_DRAFT");
            List<Candidate> safe=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(Candidate c:rows){if(c.id.isEmpty()||!seen.add(c.id))continue;safe.add("projects".equals(level)?new Candidate(c.id,DshDirectory.projectLabel(c.label),"",c.available):c);}
            options.put(level,Collections.unmodifiableList(safe));Candidate chosen=find(safe,value(level));if(chosen!=null)selected.put(level,chosen);
            Map<String,Object> out=snapshot();List<Map<String,Object>> payload=new ArrayList<>();for(Candidate c:safe)payload.add(c.map());out.put("options",payload);out.put("parents",new LinkedHashMap<>(parent));out.put("requestId",requestId);out.put("level",level);out.put("requestDraftRevision",expected);return out;
        }
    }
    private static Candidate find(List<Candidate> rows,String id){if(rows!=null)for(Candidate c:rows)if(c.id.equals(id))return c;return null;}
    private static void requireLevel(String level)throws Failure{if(!LEVELS.contains(level))throw new Failure("INVALID_TARGET");}
    private void clear(String... levels){for(String l:levels){selected.remove(l);options.remove(l);requests.put(l,requests.getOrDefault(l,0L)+1);}}
    Map<String,Object> choose(String draft,long expected,String level,String choice)throws Exception {synchronized(DshClient.LOCK){
        check(draft,expected);requireLevel(level);if(choice.equals(value(level))){Map<String,Object> same=snapshot();same.put("requestDraftRevision",expected);return same;}
        Candidate c=find(options.get(level),choice);if(c==null||!c.available)throw new Failure("INVALID_TARGET");selected.put(level,c);
        if("connectors".equals(level))clear("projects","runtimes","sessions","models","permissions");
        if("projects".equals(level))clear("sessions");
        if("runtimes".equals(level))clear("sessions","models","permissions");
        revision++;Map<String,Object> out=snapshot();out.put("requestDraftRevision",expected);return out;
    }}
    Map<String,Object> save(String draft,long expected)throws Exception {
        final Map<String,String> parent;final Map<String,String> choices=new LinkedHashMap<>();
        final Set<String> need=new LinkedHashSet<>();final boolean sessionVerified;
        final Map<String,Long> tickets=new HashMap<>();
        synchronized(DshClient.LOCK){
            check(draft,expected);parent=parents();
            for(String level:HOME_LEVELS){Candidate c=find(options.get(level),value(level));if(c==null||!c.available)throw new Failure("INVALID_TARGET");choices.put(level,c.id);}
            for(String level:Arrays.asList("models","permissions")){
                choices.put(level,value(level));Candidate c=find(options.get(level),value(level));
                if(c==null||!c.available){need.add(level);long ticket=requests.getOrDefault(level,0L)+1;requests.put(level,ticket);tickets.put(level,ticket);}
            }
            Candidate session=find(options.get("sessions"),value("sessions"));sessionVerified=session!=null&&session.available;
            // A refreshed directory that explicitly lost the selection must not silently create a session.
            if(requests.containsKey("sessions")&&!sessionVerified&&!value("sessions").isEmpty())throw new Failure("INVALID_TARGET");
            choices.put("sessions",sessionVerified?session.id:(parent.equals(savedParents)&&!savedSession.isEmpty()?savedSession:DshDirectory.NEW_SESSION));
        }
        Map<String,List<Candidate>> fetched=new LinkedHashMap<>();
        try{for(String level:need)fetched.put(level,authority.query(level,Collections.unmodifiableMap(parent)));}
        catch(Exception failure){synchronized(DshClient.LOCK){check(draft,expected);}throw new Failure("DIRECTORY_FAILED");}
        synchronized(DshClient.LOCK){
            check(draft,expected);if(!parent.equals(parents()))throw new Failure("STALE_DRAFT");
            for(String level:need){
                if(!tickets.get(level).equals(requests.get(level)))throw new Failure("STALE_DRAFT");
                List<Candidate> rows=fetched.get(level);Candidate best=find(rows,choices.get(level));
                if(best==null||!best.available){best=null;for(Candidate row:rows)if(row.available&&!row.id.isEmpty()){best=row;break;}}
                if(best==null)throw new Failure("INVALID_TARGET");choices.put(level,best.id);
            }
            Map<String,Object> saved=authority.save(Collections.unmodifiableMap(choices));
            Map<String,Object> state=authority.current();unlocked(state);if(number(state,"authGeneration")!=auth)throw new Failure("STALE_AUTH");
            for(String level:need){List<Candidate> rows=fetched.get(level);options.put(level,Collections.unmodifiableList(new ArrayList<>(rows)));selected.put(level,find(rows,choices.get(level)));}
            if(!sessionVerified){Candidate session=new Candidate(choices.get("sessions"),DshDirectory.NEW_SESSION.equals(choices.get("sessions"))?"新建会话（下次发送时创建）":"已选择会话","",true);selected.put("sessions",session);options.remove("sessions");}
            base=text(state,"revision");savedParents=new LinkedHashMap<>(parent);savedSession=text(state,"sessionId");revision++;
            Map<String,Object> out=snapshot();out.put("saved",saved);out.put("requestDraftRevision",expected);return out;
        }
    }
    Map<String,Object> close(String draft){synchronized(DshClient.LOCK){if(id.equals(draft)){id="";selected.clear();options.clear();requests.clear();}return DshClient.mapOf("ok",true,"errorCode","");}}
    Map<String,Object> error(String code,String draft,long requestRevision,String level,long requestId){synchronized(DshClient.LOCK){return DshClient.mapOf("ok",false,"errorCode",code,"draftId",draft,"requestDraftRevision",requestRevision,"authGeneration",draftEpochs.containsKey(draft)?draftEpochs.get(draft):(draft.isEmpty()?currentAuth():-1L),"level",level,"requestId",requestId);}}
    private long currentAuth(){try{return number(authority.current(),"authGeneration");}catch(Exception failure){return -1;}}
    private Map<String,Object> snapshot(){
        Map<String,Object> fields=new LinkedHashMap<>(),valid=new LinkedHashMap<>();
        for(String level:LEVELS){Candidate c=selected.get(level);if(c!=null)fields.put(level,c.map());Candidate verified=find(options.get(level),value(level));valid.put(level,verified!=null&&verified.available);}
        return DshClient.mapOf("ok",true,"errorCode","","draftId",id,"draftRevision",revision,"authGeneration",auth,"baseRevision",base,"selected",fields,"valid",valid,"newSessionId",DshDirectory.NEW_SESSION);
    }
}
