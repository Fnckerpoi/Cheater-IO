package com.turboio.addon;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.*;

public final class AaHostBridge {
    private final Activity activity;
    private final DshSelectionDraft draft;
    AaHostBridge(Activity a){activity=a;ToolClient.init(a.getApplicationContext());draft=new DshSelectionDraft(new DraftAuthority());}
    private SharedPreferences prefs(){return activity.getSharedPreferences("turboio_settings",0);}
    private static Bundle result(boolean ok,String code){Bundle b=new Bundle();b.putBoolean("ok",ok);b.putString("errorCode",code);b.putString("code",code);b.putString("status",ok?"ok":"failed");return b;}
    private String revision()throws Exception {
        SharedPreferences p=prefs();StringBuilder s=new StringBuilder();
        for(String k:new String[]{"user_id","url","connector","project","runtime","model","permission","session_id","client_message_id","target_fingerprint"}){String v=p.getString("dsh_"+k,"");s.append(v.length()).append(':').append(v);}
        s.append(p.getBoolean("dsh",false)).append(p.getBoolean("dsh_unknown_delivery",false)).append(DshSettings.authGeneration());
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private String storedSession(){return prefs().getString("dsh_session_id","");}
    public Bundle call(String operation,Bundle input){
        Bundle args=input==null?new Bundle():new Bundle(input);
        try {
            if(activity.isFinishing()||activity.isDestroyed())return operationFailure(operation,args,"HOST_CLOSED");
            if("auth.login".equals(operation)||"selection.open".equals(operation)||"auth.logout".equals(operation)){
                activity.runOnUiThread(()->{if(!activity.isFinishing()){DshSettings.show(activity);DshSettings.onClosed=()->{if(activity instanceof AaEmbeddedActivity)((AaEmbeddedActivity)activity).settingsChanged();};}});
                Bundle b=result(true,"");b.putString("status","configuration_opened");b.putString("message","请在宿主设置中明确授权、选择或退出。");return b;
            }
            if("auth.snapshot".equals(operation)||"selection.snapshot".equals(operation))return snapshot("auth.snapshot".equals(operation));
            if(Looper.myLooper()==Looper.getMainLooper())return operationFailure(operation,args,"WORKER_REQUIRED");
            if(operation!=null&&operation.startsWith("selection.draft."))return draftCall(operation,args);
            if("selection.catalog".equals(operation))return catalog(args);
            if("selection.commit".equals(operation))return commit(args);
            String tool="dsh.dispatch".equals(operation)?"dsh_dispatch":"dsh.status".equals(operation)?"dsh_status":"dsh.end_session".equals(operation)?"dsh_end_session":null;
            if(tool==null)return result(false,"UNSUPPORTED_OPERATION");
            JSONObject parameters=new JSONObject();if("dsh_dispatch".equals(tool))parameters.put("task",args.getString("task",""));
            final long requestAuth=args.getLong("expectedAuthGeneration",-1);
            final String requestRevision=args.getString("expectedRevision","");
            final String requestSession=args.getString("expectedSessionId","");
            Runnable guard=()->{try{String error="dsh_dispatch".equals(tool)?AaBridgePolicy.checkDispatch(requestAuth,DshSettings.authGeneration(),requestRevision,revision(),requestSession,storedSession(),args.getBoolean("newSession",false)):AaBridgePolicy.check(requestAuth,DshSettings.authGeneration(),requestRevision,revision(),requestSession,storedSession());if(!error.isEmpty())throw new BridgeFailure(error);}catch(BridgeFailure e){throw e;}catch(Exception e){throw new BridgeFailure("REQUEST_FAILED");}};
            JSONObject response=new ToolClient(activity.getApplicationContext()).call(tool,parameters,SecretStore.get(activity.getApplicationContext()),guard);
            synchronized(DshClient.LOCK){
                if(requestAuth!=DshSettings.authGeneration())return result(false,"STALE_AUTH");
                String status=response.optString("status","");
                if("dsh_status".equals(tool)&&(!requestRevision.equals(revision())||!AaBridgePolicy.sessionMatches(requestSession,storedSession())))return result(false,"STALE_SELECTION");
                if("submitted".equals(status)&&(!response.optString("sessionId","").equals(storedSession())||!response.optString("clientMessageId","").equals(prefs().getString("dsh_client_message_id",""))))return result(false,"STALE_SELECTION");
                if("ended".equals(status)&&!storedSession().isEmpty())return result(false,"STALE_SELECTION");
                boolean unknown=AaBridgePolicy.unknownDelivery(status);
                boolean ok=AaBridgePolicy.successful(status);
                Bundle b=result(ok,unknown?"DELIVERY_UNKNOWN":response.optString("code",""));
                b.putString("requestRevision",requestRevision);
                b.putLong("requestAuthGeneration",requestAuth);
                b.putString("requestSessionId",requestSession);
                b.putString("revision",revision());
                b.putLong("authGeneration",DshSettings.authGeneration());
                b.putString("sessionId",storedSession());
                b.putBoolean("accepted",AaBridgePolicy.accepted(status));
                b.putBoolean("completed",AaBridgePolicy.completed(status));
                b.putBoolean("unknownDelivery",unknown);
                b.putString("json",response.toString());
                b.putString("status",status);
                b.putString("message",response.optString("message",response.optString("answer","")));
                return b;
            }
        } catch(BridgeFailure failure){return operationFailure(operation,args,failure.code);}catch(Exception failure){return operationFailure(operation,args,"REQUEST_FAILED");}
    }
    private Bundle operationFailure(String operation,Bundle args,String code){
        if("selection.catalog".equals(operation)){Bundle out=result(false,code);out.putString("level",args.getString("level",""));out.putLong("requestId",args.getLong("requestId",-1));out.putLong("authGeneration",args.getLong("expectedAuthGeneration",-1));return out;}
        if(operation!=null&&operation.startsWith("selection.draft."))return bundle(draft.error(code,args.getString("draftId",""),args.getLong("expectedDraftRevision",-1),args.getString("level",""),args.getLong("requestId",-1)));
        return result(false,code);
    }
    private static final class BridgeFailure extends RuntimeException {final String code;BridgeFailure(String c){super(c);code=c;}}
    private Bundle snapshot(boolean auth)throws Exception {
        synchronized(DshClient.LOCK){
            SharedPreferences p=prefs();Bundle b=result(true,"");String user=p.getString("dsh_user_id","");
            b.putString("userId",user);b.putString("origin",p.getString("dsh_url",DshSettings.ORIGIN));b.putString("serverOrigin",b.getString("origin"));
            b.putLong("authGeneration",DshSettings.authGeneration());b.putString("revision",revision());b.putString("runtimeType","dsh");
            String[] external={"connectorId","projectId","runtimeId","model","permission","sessionId","clientMessageId"};String[] local={"connector","project","runtime","model","permission","session_id","client_message_id"};
            for(int i=0;i<local.length;i++)b.putString(external[i],p.getString("dsh_"+local[i],""));
            b.putBoolean("unknownDelivery",DshSettings.locked(activity));b.putBoolean("enabled",p.getBoolean("dsh",false));
            if(auth){String token=user.isEmpty()?"":SecretStore.get(activity,"dsh_key");boolean ready=!user.isEmpty()&&!token.isEmpty();b.putBoolean("authenticated",ready);b.putBoolean("ok",ready);if(!ready)b.putString("errorCode","AUTH_REQUIRED");b.putString("token",token);b.putString("accessToken",token);}
            return b;
        }
    }
    private Bundle commit(Bundle args) throws Exception {
        final String token,user;final long generation;final String expected=args.getString("expectedRevision","");
        final String requestSession=args.getString("expectedSessionId","");
        final boolean force=args.getBoolean("forceBind",false);
        synchronized(DshClient.LOCK){
            if(DshSettings.locked(activity))return result(false,"DELIVERY_UNKNOWN");
            generation=DshSettings.authGeneration();if(args.getLong("expectedAuthGeneration",-1)!=generation)return result(false,"STALE_AUTH");
            if(!bindMatches(force,expected,revision(),requestSession,storedSession(),args.containsKey("expectedSessionId")))return result(false,"STALE_SELECTION");
            user=prefs().getString("dsh_user_id","");token=SecretStore.get(activity,"dsh_key");if(user.isEmpty()||token.isEmpty())return result(false,"AUTH_REQUIRED");
        }
        String c=args.getString("connectorId",""),p=args.getString("projectId",""),r=args.getString("runtimeId",""),s=args.getString("sessionId",""),m=args.getString("model",""),permission=args.getString("permission","");
        if(!"dsh".equals(args.getString("runtimeType","dsh"))||!DshClient.validBinding(DshSettings.ORIGIN,c,p,r,m,permission))return result(false,"INVALID_TARGET");
        if(DshSettings.Policy.find(DshDirectory.projects(DshDirectory.rows(get("/projects",token),"projects"),c),p)==null)return result(false,"INVALID_TARGET");
        Map<String,Object> response=get("/connectors/"+DshSettings.encode(c)+"/runtimes",token);
        if(!c.equals(DshDirectory.text(response,"connectorId")))return result(false,"INVALID_TARGET");
        List<Map<String,Object>> rows=DshDirectory.rows(response,"runtimes");for(Map<String,Object> row:rows){String bound=DshDirectory.text(row,"connectorId");if(!bound.isEmpty()&&!c.equals(bound))return result(false,"INVALID_TARGET");row.put("connectorId",c);}
        if(DshSettings.Policy.find(DshDirectory.runtimes(rows,c),r)==null)return result(false,"INVALID_TARGET");
        String catalog="/connectors/"+DshSettings.encode(c)+"/runtimes/"+DshSettings.encode(r)+"/catalogs/";
        if(DshSettings.Policy.find(DshDirectory.catalog(get(catalog+"model",token),true),m)==null||DshSettings.Policy.find(DshDirectory.catalog(get(catalog+"permission",token),false),permission)==null)return result(false,"INVALID_TARGET");
        if(!s.isEmpty()&&DshSettings.Policy.find(DshDirectory.sessions(DshDirectory.rows(get("/sessions/list",token),"sessions"),c,p,r),s)==null)return result(false,"INVALID_TARGET");
        synchronized(DshClient.LOCK){
            if(DshSettings.locked(activity))return result(false,"DELIVERY_UNKNOWN");
            if(generation!=DshSettings.authGeneration()||!user.equals(prefs().getString("dsh_user_id",""))||!token.equals(SecretStore.get(activity,"dsh_key")))return result(false,"STALE_AUTH");
            if(!bindMatches(force,expected,revision(),requestSession,storedSession(),args.containsKey("expectedSessionId")))return result(false,"STALE_SELECTION");
        }
        // Network work precedes final validation: takeover is repeatable, and PATCH tolerates running 409.
        // A later identity/lock failure may still leave a remote change; local state is never written on that failure.
        if(!s.isEmpty())takeoverSelections(DshClient::httpTransport,DshSettings.ORIGIN,token,s,m,permission);
        synchronized(DshClient.LOCK){
            if(DshSettings.locked(activity))return result(false,"DELIVERY_UNKNOWN");
            if(generation!=DshSettings.authGeneration()||!user.equals(prefs().getString("dsh_user_id",""))||!token.equals(SecretStore.get(activity,"dsh_key")))return result(false,"STALE_AUTH");
            if(!bindMatches(force,expected,revision(),requestSession,storedSession(),args.containsKey("expectedSessionId")))return result(false,"STALE_SELECTION");
            SharedPreferences pref=prefs();String fingerprint=s.isEmpty()?"":new DshClient.Config(true,DshSettings.ORIGIN,token,c,p,r,m,permission).targetFingerprint();
            boolean changed=!DshClient.sameTarget(pref.getString("dsh_url",""),pref.getString("dsh_connector",""),pref.getString("dsh_project",""),pref.getString("dsh_runtime",""),DshSettings.ORIGIN,c,p,r)||!m.equals(pref.getString("dsh_model",""))||!permission.equals(pref.getString("dsh_permission",""));
            String message=DshSettings.Policy.savedMessage(changed,s,pref.getString("dsh_session_id",""),fingerprint,pref.getString("dsh_target_fingerprint",""),pref.getString("dsh_client_message_id",""));
            String[] keys={"url","connector","project","runtime","model","permission","session_id","client_message_id","target_fingerprint"};String[] values={DshSettings.ORIGIN,c,p,r,m,permission,s,message,fingerprint};
            SharedPreferences.Editor editor=pref.edit().putBoolean("dsh",true);for(int i=0;i<keys.length;i++)editor.putString("dsh_"+keys[i],values[i]);
            if(!editor.commit()){DshSettings.lockStorage();throw new IllegalStateException("storage_failed");}
            for(int i=0;i<keys.length;i++)if(!values[i].equals(pref.getString("dsh_"+keys[i],""))){DshSettings.lockStorage();throw new IllegalStateException("storage_failed");}
            if(!pref.getBoolean("dsh",false)||pref.getBoolean("dsh_unknown_delivery",false)){DshSettings.lockStorage();throw new IllegalStateException("storage_failed");}
            Bundle b=snapshot(false);
            b.putString("requestRevision",expected);
            b.putLong("requestAuthGeneration",generation);
            b.putString("requestSessionId",requestSession);
            b.putString("revision",revision());
            b.putLong("authGeneration",DshSettings.authGeneration());
            b.putString("sessionId",storedSession());
            b.putBoolean("accepted",true);
            b.putBoolean("completed",false);
            b.putBoolean("unknownDelivery",false);
            return b;
        }
    }

    static boolean bindMatches(boolean force,String expected,String actual,String requestSession,String storedSession,boolean hasSession){
        if((!force||!expected.isEmpty())&&!expected.equals(actual))return false;
        return force||(hasSession&&requestSession.equals(storedSession));
    }
    private static String encodePart(String value)throws Exception{return java.net.URLEncoder.encode(value,"UTF-8");}
    static List<DshSelectionDraft.Candidate> catalogOptions(DshClient.Transport transport,String origin,String token,String connector,String runtime,String level)throws Exception {
        if(Thread.holdsLock(DshClient.LOCK))throw new IllegalStateException("network_under_lock");
        if(!Arrays.asList("models","permissions").contains(level)||connector.isEmpty()||runtime.isEmpty())throw new DshSelectionDraft.Failure("INVALID_TARGET");
        String path=origin+"/api/v2/connectors/"+encodePart(connector)+"/runtimes/"+encodePart(runtime)+"/catalogs/"+("models".equals(level)?"model":"permission");
        List<DshSelectionDraft.Candidate> out=new ArrayList<>();for(DshDirectory.Option row:DshDirectory.catalog(transport.send("GET",path,token,null),"models".equals(level)))out.add(new DshSelectionDraft.Candidate(row.id,row.label,row.subtitle,true));return out;
    }
    static void takeoverSelections(DshClient.Transport transport,String origin,String token,String session,String model,String permission)throws Exception {
        if(Thread.holdsLock(DshClient.LOCK))throw new IllegalStateException("network_under_lock");
        String base=origin+"/api/v2/sessions/"+encodePart(DshClient.validateSessionId(session));
        transport.send("POST",base+"/takeover",token,Collections.emptyMap());
        try{transport.send("PATCH",base+"/runtime/selections",token,DshClient.mapOf("selections",DshClient.mapOf("model",model,"permission",permission)));}
        catch(DshClient.HttpStatusException rejected){if(rejected.status!=409)throw rejected;}
    }
    private Bundle catalog(Bundle args){
        String level=args.getString("level",""),connector=args.getString("connectorId",""),runtime=args.getString("runtimeId","");long request=args.getLong("requestId",-1),generation=args.getLong("expectedAuthGeneration",-1);
        Bundle out=result(false,"");out.putString("level",level);out.putLong("requestId",request);out.putLong("authGeneration",generation);
        try{
            final String token,user;
            synchronized(DshClient.LOCK){
                if(generation!=DshSettings.authGeneration())throw new DshSelectionDraft.Failure("STALE_AUTH");
                if(DshSettings.locked(activity))throw new DshSelectionDraft.Failure("DELIVERY_UNKNOWN");
                user=prefs().getString("dsh_user_id","");token=SecretStore.get(activity,"dsh_key");if(user.isEmpty()||token.isEmpty())throw new DshSelectionDraft.Failure("AUTH_REQUIRED");
            }
            List<DshSelectionDraft.Candidate> rows=catalogOptions(DshClient::httpTransport,DshSettings.ORIGIN,token,connector,runtime,level);
            synchronized(DshClient.LOCK){
                if(generation!=DshSettings.authGeneration()||!user.equals(prefs().getString("dsh_user_id",""))||!token.equals(SecretStore.get(activity,"dsh_key")))throw new DshSelectionDraft.Failure("STALE_AUTH");
                if(DshSettings.locked(activity))throw new DshSelectionDraft.Failure("DELIVERY_UNKNOWN");
                ArrayList<Bundle> options=new ArrayList<>();for(DshSelectionDraft.Candidate row:rows)options.add(bundle(row.map()));
                out.putBoolean("ok",true);out.putString("errorCode","");out.putString("connectorId",connector);out.putString("runtimeId",runtime);out.putParcelableArrayList("options",options);
            }
        }catch(DshSelectionDraft.Failure failure){out.putString("errorCode",failure.code);}catch(Exception failure){out.putString("errorCode","DIRECTORY_FAILED");}
        return out;
    }

    private static Bundle bundle(Map<String,?> values){
        Bundle out=new Bundle();
        for(Map.Entry<String,?> entry:values.entrySet()){
            Object v=entry.getValue();String k=entry.getKey();
            if(v instanceof Map)out.putBundle(k,bundle((Map<String,?>)v));
            else if(v instanceof List){ArrayList<Bundle> rows=new ArrayList<>();for(Object row:(List<?>)v)rows.add(bundle((Map<String,?>)row));out.putParcelableArrayList(k,rows);}
            else if(v instanceof Boolean)out.putBoolean(k,(Boolean)v);
            else if(v instanceof Number)out.putLong(k,((Number)v).longValue());
            else if(v instanceof String)out.putString(k,(String)v);
        }
        return out;
    }
    private Map<String,Object> plain(Bundle values){Map<String,Object> out=new LinkedHashMap<>();for(String k:values.keySet())out.put(k,values.get(k));return out;}
    private Bundle draftCall(String operation,Bundle args){
        String id=args.getString("draftId","");long rev=args.getLong("expectedDraftRevision",-1),request=args.getLong("requestId",-1);String level=args.getString("level","");
        try {
            Map<String,Object> out;
            switch(operation){
                case "selection.draft.open":out=draft.open();break;
                case "selection.draft.list":out=draft.list(id,rev,level,request);break;
                case "selection.draft.choose":out=draft.choose(id,rev,level,args.getString("id",""));break;
                case "selection.draft.save":out=draft.save(id,rev);break;
                case "selection.draft.close":out=draft.close(id);break;
                default:throw new DshSelectionDraft.Failure("UNSUPPORTED_OPERATION");
            }
            return bundle(out);
        }catch(DshSelectionDraft.Failure failure){return bundle(draft.error(failure.code,id,rev,level,request));}
        catch(Exception failure){return bundle(draft.error("REQUEST_FAILED",id,rev,level,request));}
    }
    private final class DraftAuthority implements DshSelectionDraft.Authority {
        public Map<String,Object> current()throws Exception {
            Map<String,Object> out=plain(snapshot(false));
            out.put("authenticated",!prefs().getString("dsh_user_id","").isEmpty()&&!SecretStore.get(activity,"dsh_key").isEmpty());
            out.put("storageFailed",DshSettings.storageLocked()||ToolClient.dshLocked());
            return out;
        }
        private List<DshSelectionDraft.Candidate> candidates(List<DshDirectory.Option> rows){List<DshSelectionDraft.Candidate> out=new ArrayList<>();for(DshDirectory.Option o:rows)out.add(new DshSelectionDraft.Candidate(o.id,o.label,o.subtitle,true));return out;}
        public List<DshSelectionDraft.Candidate> query(String level,Map<String,String> parent)throws Exception {
            if(Thread.holdsLock(DshClient.LOCK))throw new IllegalStateException("network_under_lock");
            String token=SecretStore.get(activity,"dsh_key");String c=parent.get("connectorId"),p=parent.get("projectId"),r=parent.get("runtimeId");
            if("connectors".equals(level)){
                List<DshSelectionDraft.Candidate> out=new ArrayList<>();for(Map<String,Object> row:DshDirectory.rows(get("/connectors",token),"connectors")){String id=DshDirectory.text(row,"id"),name=DshDirectory.text(row,"name");if(!id.isEmpty())out.add(new DshSelectionDraft.Candidate(id,name.isEmpty()?id:name,"",true));}return out;
            }
            if("projects".equals(level))return candidates(DshDirectory.projectChoices(DshDirectory.rows(get("/projects",token),"projects"),c));
            if("runtimes".equals(level)){
                Map<String,Object> response=get("/connectors/"+DshSettings.encode(c)+"/runtimes",token);
                if(!c.equals(DshDirectory.text(response,"connectorId")))throw new IllegalArgumentException("wrong_connector");
                List<Map<String,Object>> rows=DshDirectory.rows(response,"runtimes");for(Map<String,Object> row:rows){String bound=DshDirectory.text(row,"connectorId");if(!bound.isEmpty()&&!c.equals(bound))throw new IllegalArgumentException("wrong_connector");row.put("connectorId",c);}
                Set<String> usable=new HashSet<>();for(DshDirectory.Option o:DshDirectory.runtimes(rows,c))usable.add(o.id);
                List<DshSelectionDraft.Candidate> out=new ArrayList<>();for(DshDirectory.Option o:DshDirectory.runtimeCards(rows,c))out.add(new DshSelectionDraft.Candidate(o.id,o.label,o.subtitle,usable.contains(o.id)));return out;
            }
            if("sessions".equals(level)){
                List<DshDirectory.Option> rows=DshDirectory.sessions(DshDirectory.rows(get("/sessions/list",token),"sessions"),c,p,r);
                rows.add(0,DshDirectory.newSession());return candidates(rows);
            }
            if("models".equals(level)||"permissions".equals(level))return catalogOptions(DshClient::httpTransport,DshSettings.ORIGIN,token,c,r,level);
            throw new IllegalArgumentException("invalid_level");
        }
        public Map<String,Object> save(Map<String,String> selected)throws Exception {
            if(!Thread.holdsLock(DshClient.LOCK))throw new IllegalStateException("save_without_lock");
            if(DshSettings.locked(activity))throw new DshSelectionDraft.Failure("DELIVERY_UNKNOWN");
            SharedPreferences pref=prefs();String c=selected.get("connectors"),p=selected.get("projects"),r=selected.get("runtimes"),m=selected.get("models"),permission=selected.get("permissions"),session=selected.get("sessions");
            String s=DshDirectory.NEW_SESSION.equals(session)?"":DshClient.validateSessionId(session);
            if(!DshClient.validBinding(DshSettings.ORIGIN,c,p,r,m,permission))throw new DshSelectionDraft.Failure("INVALID_TARGET");
            String token=SecretStore.get(activity,"dsh_key");
            String fp=s.isEmpty()?"":new DshClient.Config(true,DshSettings.ORIGIN,token,c,p,r,m,permission).targetFingerprint();
            boolean changed=!DshClient.sameTarget(pref.getString("dsh_url",""),pref.getString("dsh_connector",""),pref.getString("dsh_project",""),pref.getString("dsh_runtime",""),DshSettings.ORIGIN,c,p,r)||!m.equals(pref.getString("dsh_model",""))||!permission.equals(pref.getString("dsh_permission",""));
            String msg=DshSettings.Policy.savedMessage(changed,s,pref.getString("dsh_session_id",""),fp,pref.getString("dsh_target_fingerprint",""),pref.getString("dsh_client_message_id",""));
            String[] keys={"url","connector","project","runtime","session_id","model","permission","client_message_id","target_fingerprint"};String[] values={DshSettings.ORIGIN,c,p,r,s,m,permission,msg,fp};
            SharedPreferences.Editor editor=pref.edit().putBoolean("dsh",true);for(int i=0;i<keys.length;i++)editor.putString("dsh_"+keys[i],values[i]);
            if(!editor.commit()){DshSettings.lockStorage();throw new DshSelectionDraft.Failure("STORAGE_FAILED");}
            for(int i=0;i<keys.length;i++)if(!values[i].equals(pref.getString("dsh_"+keys[i],""))){DshSettings.lockStorage();throw new DshSelectionDraft.Failure("STORAGE_FAILED");}
            if(!pref.getBoolean("dsh",false)||pref.getBoolean("dsh_unknown_delivery",false)){DshSettings.lockStorage();throw new DshSelectionDraft.Failure("STORAGE_FAILED");}
            return plain(snapshot(false));
        }
    }
    private static Map<String,Object> get(String path,String token)throws Exception{return DshClient.httpTransport("GET",DshSettings.ORIGIN+"/api/v2"+path,token,null);}
}
