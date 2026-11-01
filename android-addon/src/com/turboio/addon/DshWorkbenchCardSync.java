package com.turboio.addon;
import android.os.SystemClock;
import java.util.*;
/** Single-flight, coalescing publisher for the always-on AA launcher card. */
public final class DshWorkbenchCardSync {
 private static final long DEBOUNCE_MS=800;
 private static String asr="",status="待命",session="",answer="",lastSignature="";
 private static boolean enabled=true,inFlight,waitingIdle,blocked,published,removePending;
 private static long version;
 private static long generation,lastSent;
 private static Runnable debounce;
 private static final Runnable wake=DshWorkbenchCardSync::pump;
 private DshWorkbenchCardSync(){}
 public static void setEnabled(boolean value){NativeTransfer.MAIN.post(()->{enabled=value;version++;generation++;if(debounce!=null)NativeTransfer.MAIN.removeCallbacks(debounce);if(!value){asr="";removePending=true;pump();}else{removePending=false;blocked=false;request();}});}
 public static void asr(String text,boolean finished){NativeTransfer.MAIN.post(()->{asr=DshWorkbenchCardModel.clip(text,54);generation++;request();});}
 public static void aa(String state,String sessionId,String latestAnswer){NativeTransfer.MAIN.post(()->{status=DshWorkbenchCardModel.clip(state,40);session=DshWorkbenchCardModel.clip(sessionId,32);answer=DshWorkbenchCardModel.clip(latestAnswer,64);generation++;request();});}
 private static void request(){if(!enabled||blocked||removePending)return;if(debounce!=null)NativeTransfer.MAIN.removeCallbacks(debounce);long token=generation;debounce=()->{if(token==generation)pump();};NativeTransfer.MAIN.postDelayed(debounce,DEBOUNCE_MS);}
 private static String signature(){return asr+"\n"+status+"\n"+session+"\n"+answer;}
 private static void waitIdle(Runnable next){if(!waitingIdle){waitingIdle=true;CardTransport.whenIdle(()->{waitingIdle=false;NativeTransfer.MAIN.post(next);});}}
 private static void pump(){if(inFlight)return;if(removePending){remove();return;}if(!enabled||blocked)return;if(CardTransport.busy()){waitIdle(DshWorkbenchCardSync::pump);return;}long elapsed=SystemClock.elapsedRealtime()-lastSent;if(published&&elapsed<DEBOUNCE_MS){NativeTransfer.MAIN.removeCallbacks(wake);NativeTransfer.MAIN.postDelayed(wake,DEBOUNCE_MS-Math.max(0,elapsed));return;}String snap=signature();if(published&&snap.equals(lastSignature))return;inFlight=true;long sentVersion=version;boolean started=CardTransport.publish(DshWorkbenchCardModel.create(asr,status,session,answer),false,result->{inFlight=false;if(result==CardTransport.Result.FAILURE){blocked=true;return;}published=true;lastSignature=snap;lastSent=SystemClock.elapsedRealtime();if(removePending||sentVersion!=version){pump();return;}if(!snap.equals(signature()))request();});if(!started&&inFlight){inFlight=false;if(CardTransport.busy())waitIdle(DshWorkbenchCardSync::pump);else blocked=true;}}
 private static void remove(){if(!published){removePending=false;return;}if(CardTransport.busy()){waitIdle(DshWorkbenchCardSync::pump);return;}inFlight=true;boolean started=CardTransport.remove(DshWorkbenchCardModel.CARD_ID,result->{inFlight=false;if(result==CardTransport.Result.SUCCESS){published=false;lastSignature="";removePending=false;}else blocked=true;});if(!started&&inFlight){inFlight=false;if(CardTransport.busy())waitIdle(DshWorkbenchCardSync::pump);else blocked=true;}}
}
