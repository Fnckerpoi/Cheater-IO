package com.turboio.addon;

import android.os.*;
import java.util.*;
import org.json.JSONObject;

/** Pure-display business 19 bridge, owns only its fresh SID. Never starts ASR. */
public final class NavGlasses {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile String device="",sid="",phase="idle";
    private static String note="眼镜显示未开启",latest="",sent="";
    private static int textSubmitted,textCompleted;private static boolean navigationSession;private static long lifetime=240000,interval=3000;
    private static long latestExpires=Long.MAX_VALUE;
    private static long deadline,started,lastSend;private static boolean pending;
    private static Runnable changed;
    public enum ClearState { REQUESTED, CONFIRMED, UNCERTAIN }
    public interface ClearListener { void onClear(ClearState state); }
    private static final Object LEGACY_OWNER=new Object();
    private static final Map<Object,Long> epochs=new IdentityHashMap<>();
    private static Object sessionOwner=LEGACY_OWNER;
    private static long sessionEpoch;
    private static boolean reopenEligible, closeSubmitted, closeSucceeded;
    private static long closeAt;
    private static String previousOutcome="";
    private static ClearState clearState;
    private static ClearListener clearListener;
    public static void listen(Runnable r){changed=r;}
    private static void mark(String p,String n){phase=p;note=n;if(changed!=null)changed.run();}
    public static String status(){return note+(previousOutcome.isEmpty()?"":" · "+previousOutcome)+" · "+phase+" · 文字提交 "+textSubmitted+" / SDK完成 "+textCompleted+" · "+(navigationSession?"路线":"测试");}
    public static String phase(){return phase;}
    public static boolean active(){return !phase.equals("idle");}
    private static Object manager()throws Exception{return NavReflect.field(NavReflect.type("E3.u"),"h");}
    private static String connected()throws Exception{
        List<?> list=(List<?>)NavReflect.call(NavReflect.type("E3.u"),"g");String found="";
        for(Object d:list)if(Boolean.TRUE.equals(NavReflect.call(d,"b"))){if(!found.isEmpty())throw new IllegalStateException("multiple");found=(String)NavReflect.field(d,"a");}return found;
    }
    public static String connection(){try{return connected().isEmpty()?"眼镜未连接":"已找到官方连接";}catch(Exception e){return "连接信息不可用";}}
    public static final class Handle {
        private final Object owner; private final long epoch; private final String sid;
        private Handle(Object owner,long epoch,String sid){this.owner=owner;this.epoch=epoch;this.sid=sid;}
    }
    public static Handle reserve(Object owner,long epoch){
        if(owner==null||epoch<=0||active())return null;
        Long previous=epochs.get(owner);if(previous!=null&&epoch<=previous)return null;
        if(NativeNavigation.active()||ReaderBridge.active()||MusicBridge.active()||NativeTransfer.busy())return null;
        epochs.put(owner,epoch);sessionOwner=owner;sessionEpoch=epoch;sid=UUID.randomUUID().toString().replace("-","");
        clearState=null;clearListener=null;latest="";latestExpires=Long.MAX_VALUE;
        mark("preparing","显示通道已预留");return new Handle(owner,epoch,sid);
    }
    private static boolean valid(Handle h){return h!=null&&owns(h.owner,h.epoch)&&sid.equals(h.sid);}
    public static boolean begin(Handle h,String text,boolean navigation){
        if(!valid(h)||!phase.equals("preparing"))return false;
        lifetime=navigation?240000:3600000;interval=navigation?3000:700;
        boolean ok=open(text,navigation);if(!ok)requestStop();return ok;
    }
    public static boolean offer(Handle h,String text){return valid(h)&&update(h.owner,h.epoch,text);}
    public static void stop(Handle h){if(valid(h))requestStop();}
    public static boolean confirmIdle(Handle h){return valid(h)&&confirmClear(h.owner,h.epoch);}
    // Unscoped entry points cannot prove a producer's identity.
    public static boolean start(String text){return false;}
    public static boolean start(String text,boolean navigation){return false;}
    public static boolean startCaption(String text){return false;}
    public static boolean reopen(Object owner,long oldEpoch,long newEpoch,String text,long expires,long capturedAt,ClearListener listener){
        if(owner==null||owner==LEGACY_OWNER||oldEpoch<=0||newEpoch<=oldEpoch)throw new IllegalArgumentException("owner_epoch");
        if(SystemClock.elapsedRealtime()>=expires)return false;
        if(!owns(owner,oldEpoch)||(!phase.equals("stopping")&&!phase.equals("uncertain")))return false;
        if(!reopenEligible||!closeSubmitted||!closeSucceeded||capturedAt<=closeAt||SystemClock.elapsedRealtime()-closeAt<8000)return false;
        try{if(device.isEmpty()||!device.equals(connected())){reopenEligible=false;return false;}}catch(Exception e){reopenEligible=false;return false;}
        if(NativeNavigation.active()||ReaderBridge.active()||MusicBridge.active()||NativeTransfer.busy())return false;
        Long previous=epochs.get(owner);if(previous!=null&&newEpoch<=previous)return false;
        reopenEligible=false;
        previousOutcome="previous lens clear unverified";
        MAIN.removeCallbacks(tick);
        epochs.put(owner,newEpoch);sessionOwner=owner;sessionEpoch=newEpoch;clearState=null;clearListener=listener;
        latestExpires=expires;lifetime=Long.MAX_VALUE;interval=700;
        sid="";latest="";sent="";pending=false;
        boolean opened=open(text,false);
        if(!opened){if(sid.isEmpty()){device="";clearListener=null;mark("idle","未创建显示会话");}else requestStop();}
        return opened;
    }
    public static boolean claim(Object owner,long epoch,String text,ClearListener listener){
        return claim(owner,epoch,text,Long.MAX_VALUE,listener);
    }
    public static boolean claim(Object owner,long epoch,String text,long expires,ClearListener listener){
        if(owner==null||owner==LEGACY_OWNER||epoch<=0)throw new IllegalArgumentException("owner_epoch");
        if(SystemClock.elapsedRealtime()>=expires)return false;
        if(active()){
            return owns(owner,epoch)&&update(owner,epoch,text,expires);
        }
        Long previous=epochs.get(owner);if(previous!=null&&epoch<=previous)return false;
        if(NativeNavigation.active()||ReaderBridge.active()||MusicBridge.active()||NativeTransfer.busy())return false;
        epochs.put(owner,epoch);sessionOwner=owner;sessionEpoch=epoch;clearState=null;clearListener=listener;latestExpires=expires;
        lifetime=Long.MAX_VALUE;interval=700;
        boolean opened=open(text,false);
        if(!opened){if(sid.isEmpty()){device="";clearListener=null;mark("idle","未创建显示会话");}else requestStop();}
        return opened;
    }
    public static boolean update(Object owner,long epoch,String text,long expires){
        if(SystemClock.elapsedRealtime()>=expires)return false;
        boolean ok=update(owner,epoch,text);if(ok)latestExpires=expires;return ok;
    }
    public static boolean update(Object owner,long epoch,String text){
        if(!owns(owner,epoch)||!phase.equals("starting")&&!phase.equals("ready"))return false;
        latest=NavCore.clip(text,384);return true;
    }
    enum ReopenState { PENDING, ELIGIBLE, FAILED }
    static ReopenState reopenState(Object owner,long epoch){
        if(!owns(owner,epoch)||!reopenEligible||!closeSubmitted)return ReopenState.FAILED;
        return closeSucceeded?ReopenState.ELIGIBLE:ReopenState.PENDING;
    }
    static boolean canReenter(Object owner,long epoch){return owns(owner,epoch)&&reopenEligible;}
    static long closedAt(Object owner,long epoch){return owns(owner,epoch)?closeAt:-1;}
    static long ownedEpoch(Object owner){return sessionOwner==owner?sessionEpoch:0;}
    public static ClearState stopForReentry(Object owner,long epoch){
        if(!owns(owner,epoch))return ClearState.UNCERTAIN;
        boolean eligible=phase.equals("ready");
        requestStop();
        if(eligible&&closeSubmitted)reopenEligible=true;
        return clearState;
    }
    public static ClearState stopAndClear(Object owner,long epoch){
        if(!owns(owner,epoch))return ClearState.UNCERTAIN;
        reopenEligible=false;requestStop();return clearState;
    }
    public static boolean confirmClear(Object owner,long epoch){
        if(!owns(owner,epoch)||!phase.equals("stopping")&&!phase.equals("uncertain"))return false;
        finishClear();return true;
    }
    private static boolean owns(Object owner,long epoch){return owner==sessionOwner&&epoch==sessionEpoch;}
    private static void clearStatus(ClearState state){clearState=state;if(clearListener!=null)clearListener.onClear(state);}
    private static boolean open(String text,boolean navigation){
        try{device=connected();if(device.isEmpty())throw new IllegalStateException();if(!phase.equals("preparing"))sid=UUID.randomUUID().toString().replace("-","");latest=NavCore.clip(text,384);sent="";lastSend=0;pending=false;started=SystemClock.elapsedRealtime();deadline=started+10000;
            textSubmitted=0;textCompleted=0;navigationSession=navigation;
            JSONObject config=new JSONObject().put("font_size",2).put("content_width",100).put("max_lines",5).put("position","center").put("is_display",true).put("straight_view","original");
            mark("starting","正在开启纯显示通道，等待眼镜回执");
            send(7,new JSONObject().put("sid",sid).put("force",false).put("scope","temporary").put("config",config));MAIN.removeCallbacks(tick);MAIN.postDelayed(tick,1000);return true;
        }catch(Exception e){mark("uncertain","无法开启；请确认连接和镜片状态");return false;}
    }
    public static void offer(String text){}
    public static void stop(){}
    private static void requestStop(){if(sid.isEmpty()||phase.equals("idle")||phase.equals("stopping")||phase.equals("uncertain")&&clearState!=null)return;
        if(phase.equals("preparing")){finishClear();return;}
        reopenEligible=false;closeSubmitted=false;closeSucceeded=false;closeAt=SystemClock.elapsedRealtime();
        latest="";sent="";pending=false;deadline=closeAt+8000;
        mark("stopping","已请求关闭，待确认镜片退出");clearStatus(ClearState.REQUESTED);
        try{send(3,new JSONObject().put("sid",sid).put("reason_code",10).put("text",""));closeSubmitted=true;}
        catch(Exception e){mark("uncertain","退出未确认，请用眼镜按钮关闭");clearStatus(ClearState.UNCERTAIN);}
        MAIN.removeCallbacks(tick);if(phase.equals("stopping"))MAIN.postDelayed(tick,1000);
    }
    public static void confirmIdle(){}
    private static void finishClear(){MAIN.removeCallbacks(tick);ClearListener listener=clearListener;clearListener=null;sid="";device="";latest="";sent="";pending=false;clearState=ClearState.CONFIRMED;mark("idle","已由用户确认镜片退出");if(listener!=null)listener.onClear(ClearState.CONFIRMED);}
    private static void send(int type,JSONObject json)throws Exception{
        String submittedSid=sid;Object owner=sessionOwner;long epoch=sessionEpoch;long submittedExpiry=latestExpires;
        if(!device.equals(connected()))throw new IllegalStateException("device_changed");
        Object business=NavReflect.call(NavReflect.type("P3.h"),"valueOf","AI_SUBTITLE"),priority=NavReflect.call(NavReflect.type("E3.b"),"valueOf","NORMAL");
        Object message=NavReflect.make("E3.Q",NavCore.packet(type,json.toString()),UUID.randomUUID().toString(),device,business,null,priority,false,false,null,null);
        Object sdkManager=manager();
        Object callback=NavReflect.proxy("kotlin.jvm.functions.Function2",(name,args)->MAIN.post(()->{
            if(!sid.equals(submittedSid)||!owns(owner,epoch))return;
            if(type==3){closeSucceeded=args.length>1&&args[1]==null;if(!closeSucceeded)reopenEligible=false;}
            if(type==5&&!phase.equals("ready"))return;
            if(type==5){pending=false;if(args.length>1&&args[1]==null){textCompleted++;mark(phase,"文字已完成 SDK 发送，镜片效果以实际为准");}}
            if(args.length>1&&args[1]!=null){if(type==3&&phase.equals("stopping")){mark("uncertain","SDK拒绝退出，请用眼镜按钮关闭");clearStatus(ClearState.UNCERTAIN);}else if(type!=3)requestStop();}
        }));
        if(!sid.equals(submittedSid)||!owns(owner,epoch))return;
        if(type==5){
            if(!phase.equals("ready"))return;
            if(SystemClock.elapsedRealtime()>=submittedExpiry){requestStop();return;}
        }
        NavReflect.call(sdkManager,"u",message,callback);
    }
    private static final Runnable tick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();
        try{
            if(phase.equals("starting")&&now>=deadline){requestStop();}
            else if(phase.equals("ready")){
                if(now>=latestExpires){stopForReentry(sessionOwner,sessionEpoch);}
                else if(now-started>=lifetime){requestStop();}
                else if(pending&&now>=deadline){requestStop();}
                else if(!pending&&!latest.isEmpty()&&!latest.equals(sent)&&now-lastSend>=interval){
                    pending=true;deadline=now+8000;lastSend=now;sent=latest;
                    textSubmitted++;send(5,new JSONObject().put("sid",sid).put("mode",3).put("status",0).put("content",new JSONObject().put("source_transcript",latest)));
                }
            }else if(phase.equals("stopping")&&now>=deadline){mark("uncertain","退出已提交，请确认镜片已回首页");clearStatus(ClearState.UNCERTAIN);}
        }catch(Exception e){requestStop();}
        if(phase.equals("starting")||phase.equals("ready")||phase.equals("stopping")){MAIN.removeCallbacks(this);MAIN.postDelayed(this,1000);}
    }};
    /** Called after official event delivery; never suppresses vendor processing. */
    public static void event(String kind,Map<?,?> data){
        if(!kind.equals("messageReceived")||!active())return;
        try{Object raw=data.get("message");if(!(raw instanceof Map))return;Map<?,?> m=(Map<?,?>)raw;
            String d=String.valueOf(m.get("deviceId"));if(!d.equals(device))return;
            if(HostBusiness.id(m.get("businessId"))!=19)return;
            Object bytes=m.get("payload");if(!(bytes instanceof byte[]))return;
            NavCore.Envelope e=NavCore.decode((byte[])bytes);if(e==null)return;
            // Audio bodies are not retained, parsed or uploaded.
            if(e.type==4)return; // No session identity: a delayed audio frame cannot close any owner.
            final JSONObject j=new JSONObject(e.json);
            Object owner=sessionOwner;long epoch=sessionEpoch;String receivedSid=sid;
            MAIN.post(()->{if(!device.equals(d)||!sid.equals(receivedSid)||!owns(owner,epoch))return;if(!sid.equals(j.optString("sid")))return;
                if(e.type==8&&phase.equals("starting")){Object code=j.opt("code");if(code instanceof Number&&(((Number)code).doubleValue()==1||((Number)code).doubleValue()==2))mark("ready","眼镜已确认显示配置 · 文字可见性待验收");else requestStop();}
                if(e.type==3)requestStop();
            });
        }catch(Exception ignored){}
    }
}