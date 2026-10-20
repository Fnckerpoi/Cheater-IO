package com.turboio.addon;

import android.os.SystemClock;
import java.util.*;
import static com.turboio.addon.CardCodec.object;

/** Single-flight controller for the fixed SCADA environment card. */
public final class ScadaCardController {
    public static final String CARD_ID="turbo_ui_card_scada_env_01";
    private static final long SENSOR_REFRESH_MILLIS=10000;
    private enum Action { UPDATE, STALE, REMOVE }

    private static volatile String state="SCADA card has no reading";
    private static ScadaReading latest,queuedReading;
    private static Action queuedAction;
    private static boolean published,stale,inFlight,blocked,waitingForIdle,hasSensorRefresh,removed;
    private static long lastSensorRefresh,inputGeneration;
    private static Runnable staleTimer;
    private static final Runnable rateWake=ScadaCardController::pump;
    private static final Runnable idleWake=()->{waitingForIdle=false;NativeTransfer.MAIN.post(ScadaCardController::pump);};

    private ScadaCardController(){}

    /** Submit a sample; pending sensor updates collapse to this newest reading. */
    public static void onReading(ScadaReading reading){
        if(reading==null){NativeTransfer.MAIN.post(()->state="SCADA reading is null");return;}
        NativeTransfer.MAIN.post(()->acceptReading(reading));
    }

    /** Queue a stale card update immediately, even inside the sensor refresh window. */
    public static void markStale(){NativeTransfer.MAIN.post(ScadaCardController::requestStale);}

    /** Remove the fixed SCADA card after any current transaction completes. */
    public static void remove(){NativeTransfer.MAIN.post(ScadaCardController::requestRemove);}

    /** Current controller outcome for scanner diagnostics. */
    public static String status(){return state;}

    private static void acceptReading(ScadaReading reading){
        if(blocked){blocked=false;published=false;}
        removed=false;latest=reading;inputGeneration++;stale=false;
        cancelStaleTimer();
        long generation=inputGeneration;
        staleTimer=()->{if(generation==inputGeneration&&latest!=null&&!removed&&!blocked){staleTimer=null;queue(Action.STALE,latest);state="SCADA data stale; card update queued";pump();}};
        NativeTransfer.MAIN.postDelayed(staleTimer,SENSOR_REFRESH_MILLIS);
        queue(Action.UPDATE,reading);
        state=inFlight?"SCADA reading coalesced; newest sample retained":"SCADA card update queued";
        pump();
    }

    private static void requestStale(){
        if(removed||latest==null){state="SCADA card has no sample to mark stale";return;}
        if(blocked){blocked=false;published=false;}
        cancelStaleTimer();stale=true;queue(Action.STALE,latest);state="SCADA stale state queued";pump();
    }

    private static void requestRemove(){
        if(blocked){blocked=false;published=false;}
        removed=true;cancelStaleTimer();queue(Action.REMOVE,null);state="SCADA card removal queued";pump();
    }

    private static void queue(Action action,ScadaReading reading){queuedAction=action;queuedReading=reading;}

    private static void pump(){
        if(blocked||inFlight||queuedAction==null)return;
        if(CardTransport.busy()){
            if(!waitingForIdle){waitingForIdle=true;CardTransport.whenIdle(idleWake);}
            state="SCADA update waiting for the active card transaction";return;
        }
        Action action=queuedAction;ScadaReading reading=queuedReading;
        if(action==Action.UPDATE&&hasSensorRefresh){
            long elapsed=SystemClock.elapsedRealtime()-lastSensorRefresh;
            if(elapsed<SENSOR_REFRESH_MILLIS){
                NativeTransfer.MAIN.removeCallbacks(rateWake);
                NativeTransfer.MAIN.postDelayed(rateWake,SENSOR_REFRESH_MILLIS-Math.max(0,elapsed));
                state="SCADA refresh coalesced until the 10 second limit";return;
            }
        }
        queuedAction=null;queuedReading=null;NativeTransfer.MAIN.removeCallbacks(rateWake);inFlight=true;
        if(action==Action.UPDATE){hasSensorRefresh=true;lastSensorRefresh=SystemClock.elapsedRealtime();}
        boolean started;
        if(action==Action.REMOVE){
            started=CardTransport.remove(CARD_ID,result->completed(action,reading,result));
        }else{
            Map<String,Object> document=document(reading,action==Action.STALE);
            started=CardTransport.publish(document,!published,result->completed(action,reading,result));
        }
        if(!started&&inFlight){
            inFlight=false;queue(action,reading);
            if(CardTransport.busy()){if(!waitingForIdle){waitingForIdle=true;CardTransport.whenIdle(idleWake);}}
            else{blocked=true;queuedAction=null;queuedReading=null;state="SCADA card transaction could not start: "+CardTransport.status();}
        }
    }

    private static void completed(Action action,ScadaReading reading,CardTransport.Result result){
        inFlight=false;
        if(result==CardTransport.Result.FAILURE){
            blocked=true;published=false;queuedAction=null;queuedReading=null;waitingForIdle=false;
            NativeTransfer.MAIN.removeCallbacks(rateWake);cancelStaleTimer();
            state="SCADA card transaction failed: "+CardTransport.status();return;
        }
        if(action==Action.REMOVE){
            published=false;stale=false;
            if(queuedAction==null)latest=null;
            state="SCADA card removed and readback confirmed";
        }else{
            published=true;stale=action==Action.STALE;
            state=stale?"SCADA card marked stale and readback confirmed":"SCADA sample readback confirmed";
        }
        pump();
    }

    private static void cancelStaleTimer(){
        if(staleTimer!=null){NativeTransfer.MAIN.removeCallbacks(staleTimer);staleTimer=null;}
    }

    static Map<String,Object> document(ScadaReading reading,boolean isStale){
        if(reading==null)throw new IllegalArgumentException("reading required");
        String temperature=String.format(Locale.ROOT,"温度：%.2f °C",reading.temperatureCentiC/100.0);
        String pressure="环境气压："+reading.pressurePa+" Pa";
        String statusText=isStale?"状态：数据已过期":"状态：正常";
        String freshness=isStale?"新鲜度：已过期（10 秒未收到近距离读数）":"新鲜度：新鲜（10 秒内收到近距离读数）";
        List<Object> components=Arrays.asList(
            CardCodec.txt("status",statusText,10,10,230,18),
            CardCodec.txt("temperature",temperature,10,46,230,20),
            CardCodec.txt("pressure",pressure,10,86,230,16),
            CardCodec.txt("freshness",freshness,10,126,230,16));
        return object("schema",1,"id",CARD_ID,"name","SCADA 环境遥测","components",components);
    }
}
