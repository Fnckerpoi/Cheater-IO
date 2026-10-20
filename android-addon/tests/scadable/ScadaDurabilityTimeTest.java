package com.turboio.addon;
import java.util.HashMap;
import java.util.Map;
public final class ScadaDurabilityTimeTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void corrupt(Map<String,Object> state,String f){boolean failed=false;try{ScadaReplayHistory.load(state,f);}catch(IllegalArgumentException expected){failed=true;}check(failed,"corrupt history must fail closed");}
    public static void main(String[] args){
        String a=String.join("",java.util.Collections.nCopies(64,"A")),b=String.join("",java.util.Collections.nCopies(64,"B"));
        Map<String,Object> saved=new HashMap<>();check(ScadaReplayHistory.load(saved,a)==-1,"first use");
        saved.put(ScadaReplayHistory.key(a),42L);check(ScadaReplayHistory.load(saved,b)==-1,"different credentials first use");
        saved.put(ScadaReplayHistory.key(b),8L);check(ScadaReplayHistory.load(saved,a)==42,"A B A retains A");
        saved.remove("config_fingerprint");check(ScadaReplayHistory.load(saved,a)==42,"clearing provisioning retains history");
        check(!new ScadaReplayGuard(ScadaReplayHistory.load(saved,a)).accept(42),"recorded A rejected after clear/reentry");
        saved.put("last_counter",80L);saved.put("config_fingerprint",a);check(ScadaReplayHistory.load(saved,a)==80,"legacy highwater conservatively merged");check(ScadaReplayHistory.load(saved,b)==8,"known legacy belongs to A");
        saved.remove("config_fingerprint");check(ScadaReplayHistory.load(saved,b)==80,"orphan legacy applies conservatively");
        saved.put("last_counter",-2L);corrupt(saved,a);saved.remove("last_counter");saved.put(ScadaReplayHistory.key(b),"8");corrupt(saved,a);
        Map<String,Object> partial=new HashMap<>();partial.put("config_fingerprint",a);corrupt(partial,a);corrupt(partial,b);
        partial.clear();partial.put(ScadaReplayHistory.marker(a),true);corrupt(partial,a);corrupt(partial,b);
        partial.put(ScadaReplayHistory.key(a),-1L);check(ScadaReplayHistory.load(partial,a)==-1,"initialized first-use has explicit counter");
        partial.put(ScadaReplayHistory.key(a),42L);check(ScadaReplayHistory.load(partial,a)==42,"initialized replay history retained");
        partial.remove(ScadaReplayHistory.key(a));corrupt(partial,a);
        long start=1000000000L,now=start+10000000000L;
        check(ScadaSampleTime.fresh(start+1,now,start,-1),"9999999999 nanos fresh");
        check(!ScadaSampleTime.fresh(start,now,start,-1),"ten seconds expired");
        check(!ScadaSampleTime.fresh(now+1,now,start,-1),"future rejected");
        check(!ScadaSampleTime.fresh(start-1,start,start,-1),"previous scan rejected");
        check(!ScadaSampleTime.fresh(now-2,now,start,now-1),"out of order rejected");
        final int[] published={0};
        ScadaProximity gate=new ScadaProximity(-70,6,new ScadaProximity.Listener(){
            public void onReading(ScadaReading r,long captured){published[0]++;check(captured==1003,"sensor boundary uses milliseconds");}
            public void onExit(){} public void markStale(){} public void remove(){}
        });
        for(int i=1;i<=3;i++)gate.accept(new ScadaReading("lab",i,2000,100000,-60,0),ScadaSampleTime.captureMillis(1000000000L+i*1000000L),1003);
        check(published[0]==1,"BLE nanos integrate with proximity milliseconds");
        System.out.println("ScadaDurabilityTimeTest: "+checks+" checks passed");
    }
}