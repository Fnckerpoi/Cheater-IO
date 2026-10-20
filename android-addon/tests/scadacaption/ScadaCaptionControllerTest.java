package com.turboio.addon;

import android.os.Handler;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScadaCaptionControllerTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        legacyCaptionAndNavigationRemainCompatible();
        scadaCannotReuseOrStopAnotherOwner();
        ownerEpochAndCallbacksAreIsolated();
        clearNeedsExplicitConfirmation();
        sdkFailureAndTimeoutStayUncertain();
        failedStartsAndTextSendsClearOnlyTheirSession();
        otherDisplaysKeepTheirBusyGuards();
        proximityExitAndStaleClearImmediately();
        controllerRejectsOldAndExpiredSamples();
        absoluteExpiryAndLongRunningStream();
        delayedAudioAndConstructionRaces();
        connectionQueueIsolation();
        boundedSameOwnerReopenAndSessionRotation();
        expiryOrderingAndCloseEvidence();
        exitBeforeConfigurationAckClearsSession();
        reopenPreservesGuardsAndEpochIsolation();
        controllerWaitsForCloseSdkEvidence();
        System.out.println("ScadaCaptionControllerTest: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message + "; display=" + NavGlasses.status());
    }

    private static ScadaCaptionController controller() { ScadaCaptionController c=new ScadaCaptionController(); c.beginScan(1); return c; }

    private static ScadaReading reading(int temperature) {
        return new ScadaReading("lab", temperature, temperature, 101325, -60, 123);
    }

    private static void event(int type, String sid, int code) {
        byte[] payload = NavCore.packet(7, "{\"sid\":\"" + sid + "\",\"code\":" + code + "}");
        payload[3] = (byte)type;
        Map<String,Object> message = new HashMap<>();
        message.put("deviceId", NavReflect.peer);
        message.put("businessId", 19);
        message.put("payload", payload);
        Map<String,Object> data = new HashMap<>();
        data.put("message", message);
        NavGlasses.event("messageReceived", data);
    }

    private static String ready() {
        String sid = NavReflect.last().sid();
        event(8, sid, 1);
        Handler.drain();
        check("ready".equals(NavGlasses.phase()), "matching configuration ack readies session");
        return sid;
    }

    private static NavGlasses.Handle cloud;
    private static boolean cloudStart(String text){cloud=NavGlasses.reserve(new Object(),1);return cloud!=null&&NavGlasses.begin(cloud,text,false);}
    private static void legacyCaptionAndNavigationRemainCompatible() {
        Object cloudOwner=new Object(), navigationOwner=new Object(), testOwner=new Object();
        NavGlasses.Handle preparing=NavGlasses.reserve(cloudOwner,1);
        check(preparing!=null&&NavGlasses.active(),"cloud preparation reserves the display");
        check(NavGlasses.reserve(navigationOwner,1)==null&&NavGlasses.reserve(testOwner,1)==null,"navigation and test cannot steal cloud preparation");
        check(!NavGlasses.startCaption("old cloud")&&!NavGlasses.start("old nav",true),"unscoped starts fail closed");
        int before=NavReflect.messages.size();NavGlasses.offer("old");NavGlasses.stop();NavGlasses.confirmIdle();
        check(NavReflect.messages.size()==before&&NavGlasses.phase().equals("preparing"),"unscoped mutations cannot bypass lease");
        NavGlasses.stop(preparing);
        check(!NavGlasses.active()&&!NavGlasses.begin(preparing,"late ready",false),"stopped preparation cannot revive");
        NavGlasses.Handle navigation=NavGlasses.reserve(navigationOwner,1);
        check(NavGlasses.begin(navigation,"navigation",true),"navigation owns fresh lease");
        String sid=ready();
        check(!NavGlasses.offer(preparing,"late cloud")&&!NavGlasses.confirmIdle(preparing),"old cloud cannot mutate navigation");
        NavGlasses.stop(preparing);Handler.advance(1000);
        check(NavReflect.last().sid().equals(sid)&&NavGlasses.phase().equals("ready"),"old stop cannot close navigation");
        check(NavGlasses.reserve(testOwner,1)==null,"test cannot reuse navigation SID");
        NavGlasses.stop(navigation);check(NavGlasses.confirmIdle(navigation),"navigation explicit confirmation");Handler.drain();
    }

    private static void scadaCannotReuseOrStopAnotherOwner() {
        check(cloudStart("cloud owns the display"), "cloud opens before SCADA");
        String sid = ready();
        int before = NavReflect.messages.size();
        ScadaCaptionController captions = controller();
        captions.onReading(1,reading(2150), SystemClock.now, SystemClock.now);
        captions.stop();
        check(NavReflect.messages.size() == before && "ready".equals(NavGlasses.phase()), "SCADA neither reuses nor stops cloud SID");
        NavGlasses.offer(cloud,"cloud still updates");
        Handler.advance(1000);
        check(NavReflect.last().sid().equals(sid), "cloud keeps updating after denied SCADA claim");
        NavGlasses.stop(cloud);
        NavGlasses.confirmIdle(cloud);
        Handler.drain();
        Object other = new Object();
        check(NavGlasses.claim(other, 1, "another owner", state -> {}), "another owner claims");
        sid = ready();
        before = NavReflect.messages.size();
        captions.onReading(1,reading(2200), SystemClock.now, SystemClock.now);
        captions.stop();
        check(NavReflect.messages.size() == before && NavReflect.last().sid().equals(sid), "SCADA cannot mutate an explicitly owned SID");
        NavGlasses.stopAndClear(other, 1);
        check(NavGlasses.confirmClear(other, 1), "correct owner can confirm its exit");
        Handler.drain();
    }

    private static void ownerEpochAndCallbacksAreIsolated() throws Exception {
        Object owner = new Object();
        Object foreign = new Object();
        check(NavGlasses.claim(owner, 1, "first epoch", state -> {}), "first epoch claims");
        String firstSid = ready();
        Handler.advance(1000);
        NavReflect.Message oldText = NavReflect.last();
        check(oldText.type() == 5, "first epoch submitted text");
        check(!NavGlasses.update(foreign, 1, "foreign") && !NavGlasses.update(owner, 2, "future"), "updates need exact owner and epoch");
        check(!NavGlasses.claim(owner, 2, "cannot replace active epoch", state -> {}), "new epoch cannot reuse an active SID");
        int before = NavReflect.messages.size();
        NavGlasses.stopAndClear(foreign, 1);
        NavGlasses.stopAndClear(owner, 2);
        NavGlasses.stop();
        NavGlasses.offer("legacy cannot overwrite owned text");
        check(!NavGlasses.startCaption("cloud cannot steal") && !NavGlasses.start("navigation cannot steal", true), "legacy starts do not steal owned session");
        check(NavReflect.messages.size() == before && "ready".equals(NavGlasses.phase()), "foreign stops never send a stop for owned SID");
        NavGlasses.stopAndClear(owner, 1);
        NavReflect.Message oldStop = NavReflect.last();
        check(oldStop.sid().equals(firstSid), "stop retains the owned SID");
        event(3, firstSid, 1);
        check(NavGlasses.confirmClear(owner, 1), "explicit confirmation releases old epoch");
        check(!NavGlasses.claim(owner, 1, "stale claim", state -> {}), "released epoch cannot reclaim");
        check(NavGlasses.claim(owner, 2, "second epoch", state -> {}), "fresh epoch claims a new SID");
        String secondSid = ready();
        check(!firstSid.equals(secondSid), "new epoch always gets a fresh SID");
        before = NavReflect.messages.size();
        oldText.complete("late SDK failure");
        oldStop.complete("late stop failure");
        event(3, firstSid, 1);
        NavGlasses.stopAndClear(owner, 1);
        check(!NavGlasses.confirmClear(owner, 1), "old epoch cannot confirm new session");
        Handler.drain();
        check(NavReflect.messages.size() == before && "ready".equals(NavGlasses.phase()), "old events and SDK callbacks cannot stop new epoch");
        NavGlasses.stopAndClear(owner, 2);
        NavGlasses.confirmClear(owner, 2);
        Handler.drain();
    }

    private static void clearNeedsExplicitConfirmation() {
        Object owner = new Object();
        List<NavGlasses.ClearState> states = new ArrayList<>();
        check(NavGlasses.claim(owner, 1, "clear me", states::add), "clear test claims");
        String sid = ready();
        check(NavGlasses.stopAndClear(owner, 1) == NavGlasses.ClearState.REQUESTED, "stop reports clear requested");
        int before = NavReflect.messages.size();
        Handler.drain();
        event(3, sid, 1);
        Handler.drain();
        check(states.equals(Arrays.asList(NavGlasses.ClearState.REQUESTED)), "SDK success and peer stop do not prove the lens is clear");
        check(NavReflect.messages.size() == before, "repeated stop is idempotent");
        check(!NavGlasses.update(owner, 1, "must not reappear"), "stopping cannot accept text updates");
        NavGlasses.confirmIdle();
        check("stopping".equals(NavGlasses.phase()), "legacy confirmation cannot release owned session");
        check(NavGlasses.confirmClear(owner, 1), "owner confirmation releases display");
        check(states.equals(Arrays.asList(NavGlasses.ClearState.REQUESTED, NavGlasses.ClearState.CONFIRMED)), "confirmed follows explicit lens verification");
        Handler.advance(9000);
        check(NavReflect.messages.size() == before && !NavGlasses.active(), "cleared session has no queued text or timer");
    }

    private static void sdkFailureAndTimeoutStayUncertain() {
        ScadaCaptionController captions = controller();
        captions.onReading(1,reading(2150), SystemClock.now, SystemClock.now);
        ready();
        captions.stop();
        Handler.drain();
        check(captions.status().endsWith("clear requested"), "controller exposes requested after SDK success");
        Handler.advance(7999);
        check(captions.status().endsWith("clear requested"), "before timeout remains requested");
        Handler.advance(1);
        check(captions.status().endsWith("clear uncertain"), "stop timeout reports uncertainty");
        int before = NavReflect.messages.size();
        captions.onReading(1,reading(2200), SystemClock.now, SystemClock.now);
        check(NavReflect.messages.size() == before, "uncertain clear prevents reentry");
        check(captions.confirmClear() && captions.status().endsWith("clear confirmed"), "user verification confirms SCADA clear");
        captions.onReading(1,reading(2300), SystemClock.now, SystemClock.now);
        ready();
        NavReflect.rejectStop = true;
        captions.stop();
        Handler.drain();
        check(captions.status().endsWith("clear uncertain"), "SDK rejection reports uncertainty");
        NavReflect.rejectStop = false;
        check(captions.confirmClear(), "SDK rejection can be resolved by lens verification");
        captions.onReading(1,reading(2400), SystemClock.now, SystemClock.now);
        ready();
        NavReflect.peer = "different-peer";
        before = NavReflect.messages.size();
        captions.stop();
        check(captions.status().endsWith("clear uncertain") && NavReflect.messages.size() == before, "device change never sends old SID to another peer");
        check(captions.confirmClear(), "device-change uncertainty can be explicitly confirmed");
        NavReflect.peer = "caption-test-peer";
        Handler.drain();
    }

    private static ScadaProximity proximity(ScadaCaptionController captions) {
        return new ScadaProximity(-70, 6, new ScadaProximity.Listener() {
            public void onReading(ScadaReading reading, long capturedAt) { captions.onReading(1,reading, capturedAt, SystemClock.now); }
            public void onExit() { captions.onExit(1); }
            public void markStale() { captions.onStale(1); }
            public void remove() {}
        });
    }

    private static void failedStartsAndTextSendsClearOnlyTheirSession() {
        ScadaCaptionController captions = controller();
        NavReflect.peer = "";
        int before = NavReflect.messages.size();
        captions.onReading(1,reading(2150), SystemClock.now, SystemClock.now);
        check(NavReflect.messages.size() == before && !NavGlasses.active(), "no peer creates no SID and sends no stop");
        check(!captions.status().contains("clear confirmed"), "failed claim does not invent lens confirmation");
        NavReflect.peer = "caption-test-peer";
        captions.beginScan(2);
        NavReflect.throwType = 7;
        captions.onReading(2,reading(2200), SystemClock.now, SystemClock.now);
        check(NavReflect.last().type() == 3 && captions.status().endsWith("clear requested"), "synchronous open failure requests owned cleanup");
        check(NavReflect.messages.get(NavReflect.messages.size() - 2).sid().equals(NavReflect.last().sid()), "open failure clears exactly its allocated SID");
        NavReflect.throwType = -1;
        check(captions.confirmClear(), "failed open remains explicitly confirmable");
        captions.onReading(2,reading(2300), SystemClock.now, SystemClock.now);
        String sid = ready();
        NavReflect.rejectType = 5;
        Handler.advance(1000);
        check(NavReflect.last().type() == 3 && NavReflect.last().sid().equals(sid), "text SDK failure stops exactly the owning SID");
        check(captions.status().endsWith("clear requested"), "automatic error cleanup reaches controller state");
        NavReflect.rejectType = -1;
        captions.confirmClear();
        captions.onReading(2,reading(2400), SystemClock.now, SystemClock.now);
        sid = NavReflect.last().sid();
        Handler.advance(10000);
        check(NavReflect.last().type() == 3 && NavReflect.last().sid().equals(sid), "configuration timeout clears its session");
        captions.confirmClear();
        captions.onReading(2,reading(2500), SystemClock.now, SystemClock.now);
        ready();
        NavReflect.throwType = 3;
        captions.stop();
        check(captions.status().endsWith("clear uncertain"), "synchronous stop failure is uncertain");
        NavReflect.throwType = -1;
        captions.confirmClear();
        Handler.drain();
    }

    private static void otherDisplaysKeepTheirBusyGuards() {
        ScadaCaptionController captions = controller();
        int before = NavReflect.messages.size();
        NativeNavigation.running = true;
        captions.onReading(1,reading(2100), SystemClock.now, SystemClock.now);
        captions.stop();
        NativeNavigation.running = false;
        ReaderBridge.running = true;
        captions.onReading(1,reading(2200), SystemClock.now, SystemClock.now);
        captions.stop();
        ReaderBridge.running = false;
        MusicBridge.running = true;
        captions.onReading(1,reading(2300), SystemClock.now, SystemClock.now);
        captions.stop();
        MusicBridge.running = false;
        NativeTransfer.running = true;
        captions.onReading(1,reading(2400), SystemClock.now, SystemClock.now);
        captions.stop();
        NativeTransfer.running = false;
        check(NavReflect.messages.size() == before && !NavGlasses.active(), "native navigation, reader, music and transfer guards prevent SCADA claims");
    }

    private static void enter(ScadaProximity proximity) {
        for (int index = 0; index < 3; index++) {
            Handler.advance(1);
            proximity.accept(reading(2150 + index), SystemClock.now, SystemClock.now);
        }
        ready();
    }

    private static void proximityExitAndStaleClearImmediately() {
        ScadaCaptionController captions = controller();
        ScadaProximity proximity = proximity(captions);
        enter(proximity);
        Handler.advance(1);
        ScadaReading far = new ScadaReading("lab", 10, 2200, 101325, -77, 123);
        proximity.accept(far, SystemClock.now, SystemClock.now);
        check(NavReflect.last().type() == 3 && captions.status().endsWith("clear requested"), "exit immediately submits stop and clear");
        check(proximity.isPublished(), "caption exit preserves existing card lifetime");
        captions.confirmClear();
        enter(proximity);
        Handler.advance(9999);
        proximity.tick(SystemClock.now);
        check(!captions.status().contains("clear"), "9999 ms leaves caption active");
        Handler.advance(1);
        proximity.tick(SystemClock.now);
        check(NavReflect.last().type() == 3 && captions.status().endsWith("clear requested"), "10000 ms staleness submits caption clear");
        check(proximity.isPublished(), "stale caption clear does not wait for 30 second card removal");
        captions.confirmClear();
        Handler.drain();
    }

    private static void controllerRejectsOldAndExpiredSamples() {
        ScadaCaptionController captions = controller();
        captions.onReading(1,reading(2150), SystemClock.now, SystemClock.now);
        ready();
        int before = NavReflect.messages.size();
        captions.onReading(1,reading(9999), SystemClock.now - 1, SystemClock.now);
        Handler.advance(1000);
        check(NavReflect.messages.size() == before + 1, "old queued reading does not create another session");
        check(!new String(NavReflect.last().payload, StandardCharsets.UTF_8).contains("99.99"), "old queued reading cannot replace the latest text");
        captions.stop();
        captions.confirmClear();
        Handler.advance(10000);
        before = NavReflect.messages.size();
        captions.onReading(1,reading(2200), SystemClock.now - 10000, SystemClock.now);
        check(NavReflect.messages.size() == before && !NavGlasses.active(), "expired sample cannot reopen a cleared caption");
    }
    private static void absoluteExpiryAndLongRunningStream() throws Exception {
        ScadaCaptionController c=controller();
        c.onReading(1,reading(1234),SystemClock.now,SystemClock.now);ready();
        Handler.advance(9999);check(NavGlasses.phase().equals("ready"),"independent expiry keeps 9999 fresh");
        Handler.advance(1);check(NavReflect.last().type()==3,"absolute expiry clears without proximity tick");c.confirmClear();
        Object owner=new Object();long expires=SystemClock.now+1;
        check(NavGlasses.claim(owner,1,"must expire before send",expires,x->{}),"short expiry claim");ready();
        SystemClock.now+=2;
        java.lang.reflect.Method send=NavGlasses.class.getDeclaredMethod("send",int.class,org.json.JSONObject.class);send.setAccessible(true);
        int before=NavReflect.messages.size();send.invoke(null,5,new org.json.JSONObject());
        check(NavReflect.messages.size()==before+1&&NavReflect.last().type()==3,"actual send rejects expired facts even before scheduler runs");NavGlasses.confirmClear(owner,1);Handler.drain();
        ScadaCaptionController stream=controller();
        stream.onReading(1,reading(2150),SystemClock.now,SystemClock.now);ready();
        for(int i=0;i<751;i++){Handler.advance(5000);stream.onReading(1,reading(2150),SystemClock.now,SystemClock.now);}
        check(NavGlasses.phase().equals("ready"),"unchanged fresh values continue beyond one hour");
        stream.stop();stream.confirmClear();Handler.drain();
    }
    private static void delayedAudioAndConstructionRaces() throws Exception {
        Object a=new Object(),b=new Object();
        NavGlasses.claim(a,1,"A",x->{});String oldSid=ready();NavGlasses.stopAndClear(a,1);NavGlasses.confirmClear(a,1);
        NavGlasses.claim(b,1,"B",x->{});String newSid=ready();int before=NavReflect.messages.size();
        event(4,oldSid,1);Handler.drain();
        check(NavGlasses.phase().equals("ready")&&NavReflect.messages.size()==before,"A delayed audio cannot stop B without SID correlation");
        NavGlasses.stopAndClear(b,1);NavGlasses.confirmClear(b,1);Handler.drain();
        Object expiring=new Object();long expiry=SystemClock.now+500;
        NavGlasses.claim(expiring,1,"expires in construction",expiry,x->{});ready();before=NavReflect.messages.size();
        NavReflect.advanceOnTextConstruction=501;
        java.lang.reflect.Method send=NavGlasses.class.getDeclaredMethod("send",int.class,org.json.JSONObject.class);send.setAccessible(true);
        send.invoke(null,5,new org.json.JSONObject());
        check(NavReflect.messages.size()==before+1&&NavReflect.last().type()==3,"construction crossing deadline sends close and no type5");
        NavGlasses.confirmClear(expiring,1);Handler.drain();
        Object c=new Object(),d=new Object();NavGlasses.claim(c,1,"C",x->{});ready();
        NavReflect.duringTextConstruction=()->{NavGlasses.stopAndClear(c,1);NavGlasses.confirmClear(c,1);NavGlasses.claim(d,1,"D",x->{});};
        before=NavReflect.messages.size();send.invoke(null,5,new org.json.JSONObject());
        check(NavReflect.messages.size()==before+2&&NavReflect.last().type()==7,"construction owner replacement cannot submit old text or stop new SID");
        ready();NavGlasses.stopAndClear(d,1);NavGlasses.confirmClear(d,1);Handler.drain();
    }
    private static void connectionQueueIsolation(){
        final long[] current={1};final int[] closed={0,0};
        CaptionEventQueue a=new CaptionEventQueue(()->current[0]==1,()->closed[0]++);
        for(int i=0;i<32;i++)check(a.acquire(),"A queue accepts bounded events");
        current[0]=2;CaptionEventQueue b=new CaptionEventQueue(()->current[0]==2,()->closed[1]++);
        for(int i=0;i<40;i++)check(!a.acquire(),"A stale callback returns before accounting");
        for(int i=0;i<32;i++)check(b.acquire(),"B quota independent of A backlog");
        check(!b.acquire()&&closed[0]==0&&closed[1]==1,"overflow closes only owning socket");
        for(int i=0;i<32;i++)a.release();
    }

    private static void boundedSameOwnerReopenAndSessionRotation() throws Exception {
        ScadaCaptionController c=controller();
        c.beginScan(1);c.onReading(1,reading(2000),SystemClock.now,SystemClock.now);String first=ready();
        Handler.advance(1);c.onExit(1);NavReflect.Message oldStop=NavReflect.last();Handler.drain();
        int before=NavReflect.messages.size();long closed=SystemClock.now;
        Handler.advance(7999);c.onReading(1,reading(2001),SystemClock.now,SystemClock.now);
        check(NavReflect.messages.size()==before,"8 second cooldown does not consume attempt");
        Handler.advance(1);c.onReading(1,reading(2002),closed,SystemClock.now);
        check(NavReflect.messages.size()==before,"capture at close cannot reopen");
        c.onReading(1,reading(2003),SystemClock.now,SystemClock.now);String second=ready();
        check(!first.equals(second),"normal exit rotates fresh SID");
        check(NavGlasses.status().contains("previous lens clear unverified"),"rotation retains unverified old outcome");
        oldStop.complete("late failure");event(3,first,1);event(4,first,1);Handler.drain();
        check(NavGlasses.phase().equals("ready"),"old callbacks cannot affect new SID");
        Handler.advance(1);c.onExit(1);Handler.drain();Handler.advance(8000);
        NativeNavigation.running=true;c.onReading(1,reading(2010),SystemClock.now,SystemClock.now);NativeNavigation.running=false;
        before=NavReflect.messages.size();
        for(int i=0;i<20;i++){Handler.advance(1000);c.onReading(1,reading(2011),SystemClock.now,SystemClock.now);}
        check(NavReflect.messages.size()==before,"failed attempt stays latched across many fresh packets");
        check(c.confirmClear(),"failed attempt retains manual clear fallback");
        c.onReading(1,reading(2012),SystemClock.now,SystemClock.now);ready();c.stop();Handler.drain();Handler.advance(8000);
        before=NavReflect.messages.size();c.onReading(1,reading(2013),SystemClock.now,SystemClock.now);
        check(NavReflect.messages.size()==before,"manual stop never reopens");c.confirmClear();c.stopScan();
        c.beginScan(2);before=NavReflect.messages.size();c.onReading(1,reading(2014),SystemClock.now,SystemClock.now);c.onExit(1);
        check(NavReflect.messages.size()==before,"old scan reading and exit ignored");
        c.onReading(2,reading(2015),SystemClock.now,SystemClock.now);ready();c.stopScan();Handler.drain();c.confirmClear();
        before=NavReflect.messages.size();c.onReading(2,reading(2016),SystemClock.now,SystemClock.now);
        check(NavReflect.messages.size()==before,"end scan cannot revive after confirmation");
        for(int failure=0;failure<5;failure++){
            ScadaCaptionController f=controller();f.beginScan(1);
            if(failure==3)NavReflect.rejectType=7;
            f.onReading(1,reading(2100),SystemClock.now,SystemClock.now);
            if(failure==3){Handler.drain();NavReflect.rejectType=-1;}
            else if(failure==4){Handler.advance(10000);}
            else {ready();Handler.advance(1);if(failure==1)NavReflect.rejectStop=true;f.onExit(1);Handler.drain();NavReflect.rejectStop=false;}
            if(failure==0)NavReflect.peer="changed-peer";
            if(failure==2)NavReflect.peer="";
            Handler.advance(8000);before=NavReflect.messages.size();
            for(int i=0;i<12;i++){f.onReading(1,reading(2101),SystemClock.now,SystemClock.now);Handler.advance(1000);}
            check(NavReflect.messages.size()==before,"disconnect/change/close rejection/open rejection/timeout cannot auto reopen "+failure);
            NavReflect.peer="caption-test-peer";f.confirmClear();f.stopScan();Handler.drain();
        }
    }
    private static void exitBeforeConfigurationAckClearsSession() {
        ScadaCaptionController captions = controller();
        captions.onReading(1, reading(2200), SystemClock.now, SystemClock.now);
        String sid = NavReflect.last().sid();
        Handler.advance(1);
        captions.onExit(1);
        check(NavReflect.last().type() == 3 && NavReflect.last().sid().equals(sid), "exit before configuration ack immediately closes its SID");
        event(8, sid, 1);
        Handler.drain();
        check("stopping".equals(NavGlasses.phase()), "late configuration ack cannot revive an exited session");
        Handler.advance(8000);
        int before = NavReflect.messages.size();
        captions.onReading(1, reading(2201), SystemClock.now, SystemClock.now);
        check(NavReflect.messages.size() == before, "unacknowledged start does not qualify for automatic cycling");
        check(captions.confirmClear(), "early exit retains manual confirmation fallback");
        captions.stopScan();
        Handler.drain();
    }

    private static void reopenPreservesGuardsAndEpochIsolation() throws Exception {
        for (boolean timedOut : new boolean[]{false, true}) {
            Object owner = new Object(), other = new Object();
            List<NavGlasses.ClearState> oldStates = new ArrayList<>();
            check(NavGlasses.claim(owner, 1, "old facts", oldStates::add), "rotation guard session starts");
            String oldSid = ready();
            Handler.advance(1000);
            NavReflect.Message oldText = NavReflect.last();
            check(oldText.type() == 5, "old session has a text callback to isolate");
            check(NavGlasses.update(owner, 1, "discarded old pending facts"), "old latest text is queued");
            NavGlasses.stopForReentry(owner, 1);
            NavReflect.Message oldStop = NavReflect.last();
            Handler.drain();
            if (timedOut) Handler.advance(8000); else SystemClock.now += 8000;
            String phase = timedOut ? "uncertain" : "stopping";
            check(phase.equals(NavGlasses.phase()), "rotation covers " + phase);
            int before = NavReflect.messages.size();
            check(!NavGlasses.claim(other, 1, "foreign claim", state -> {}), "foreign owner cannot claim a closing session");
            check(NavGlasses.reserve(other, 1) == null, "foreign owner cannot reserve a closing session");
            check(!NavGlasses.reopen(other, 1, 2, "foreign reopen", Long.MAX_VALUE, SystemClock.now, state -> {}), "foreign owner cannot rotate a closing session");
            check(!NavGlasses.update(other, 1, "foreign update"), "foreign owner cannot update a closing session");
            NavGlasses.stopAndClear(other, 1);
            NavGlasses.stopForReentry(other, 1);
            check(!NavGlasses.confirmClear(other, 1), "foreign owner cannot clear a closing session");
            check(!NavGlasses.reopen(owner, 2, 3, "wrong epoch", Long.MAX_VALUE, SystemClock.now, state -> {}), "same owner must present current old epoch");
            check(!NavGlasses.reopen(owner, 1, 2, "expired facts", SystemClock.now, SystemClock.now, state -> {}), "expired facts cannot rotate session");
            for (int guard = 0; guard < 4; guard++) {
                NativeNavigation.running = guard == 0;
                ReaderBridge.running = guard == 1;
                MusicBridge.running = guard == 2;
                NativeTransfer.running = guard == 3;
                check(!NavGlasses.reopen(owner, 1, 2, "busy display", Long.MAX_VALUE, SystemClock.now, state -> {}), "rotation respects busy guard " + guard);
            }
            NativeNavigation.running = false;
            ReaderBridge.running = false;
            MusicBridge.running = false;
            NativeTransfer.running = false;
            check(NavReflect.messages.size() == before && phase.equals(NavGlasses.phase()), "denied operations leave old SID and phase intact");
            oldText.complete("queued old text failure");
            oldStop.complete("queued old stop failure");
            event(3, oldSid, 1);
            event(4, oldSid, 1);
            List<NavGlasses.ClearState> newStates = new ArrayList<>();
            check(NavGlasses.reopen(owner, 1, 2, "new fresh facts", SystemClock.now + 10000, SystemClock.now, newStates::add), "qualified owner rotates from " + phase);
            NavReflect.Message opened = NavReflect.last();
            String newSid = opened.sid();
            check(opened.type() == 7 && !newSid.equals(oldSid), "rotation allocates a fresh SID with type 7");
            check(Boolean.FALSE.equals(new org.json.JSONObject(NavCore.decode(opened.payload).json).opt("force")), "rotation keeps force false");
            ready();
            oldStop.complete("late old stop failure");
            event(3, oldSid, 1);
            event(8, oldSid, 0);
            event(4, oldSid, 1);
            NavGlasses.stopAndClear(owner, 1);
            check(!NavGlasses.confirmClear(owner, 1), "old epoch cannot clear rotated session");
            check(!NavGlasses.update(owner, 1, "stale epoch facts"), "old epoch cannot update rotated session");
            check(!NavGlasses.reopen(owner, 1, 3, "stale rotation", Long.MAX_VALUE, SystemClock.now, state -> {}), "old epoch cannot rotate again");
            check(!NavGlasses.claim(other, 2, "foreign takeover", state -> {}), "foreign owner cannot claim rotated session");
            Handler.advance(1000);
            check("ready".equals(NavGlasses.phase()) && newStates.isEmpty(), "old callbacks and events cannot clear rotated session");
            NavReflect.Message freshText = NavReflect.last();
            String body = NavCore.decode(freshText.payload).json;
            check(freshText.type() == 5 && freshText.sid().equals(newSid) && body.contains("new fresh facts") && !body.contains("old"), "rotated SID sends only new facts");
            check(!oldStates.contains(NavGlasses.ClearState.CONFIRMED), "rotation does not fabricate lens-clear confirmation");
            NavGlasses.stopAndClear(owner, 2);
            check(NavGlasses.confirmClear(owner, 2), "current epoch keeps manual confirmation");
            Handler.drain();
        }
    }

    private static void expiryOrderingAndCloseEvidence() throws Exception {
        java.lang.reflect.Field tickField=NavGlasses.class.getDeclaredField("tick");tickField.setAccessible(true);
        Runnable tick=(Runnable)tickField.get(null);
        for(boolean navFirst:new boolean[]{true,false}){
            ScadaCaptionController c=controller();c.onReading(1,reading(2200),SystemClock.now,SystemClock.now);String sid=ready();
            SystemClock.now+=10000;
            if(navFirst){tick.run();c.onStale(1);}else{c.onStale(1);tick.run();}
            Handler.drain();Handler.advance(8000);c.onReading(1,reading(2201),SystemClock.now,SystemClock.now);
            check(NavReflect.last().type()==7&&!NavReflect.last().sid().equals(sid),"expiry ordering permits bounded reentry "+navFirst);
            ready();c.stopScan();c.confirmClear();Handler.drain();
        }
        Object owner=new Object(),other=new Object();NavGlasses.claim(owner,1,"close evidence",s->{});ready();
        Handler.advance(1);NavGlasses.stopForReentry(owner,1);NavReflect.Message close=NavReflect.last();
        SystemClock.now+=8000;int before=NavReflect.messages.size();
        check(!NavGlasses.reopen(owner,1,2,"wait for SDK",SystemClock.now+10000,SystemClock.now,s->{}),"close SDK callback must complete before reopen");
        Handler.drain();
        check(!NavGlasses.claim(owner,2,"generic bypass",s->{}),"generic claim cannot rotate");
        check(!NavGlasses.reopen(other,1,2,"foreign",SystemClock.now+10000,SystemClock.now,s->{}),"foreign owner cannot rotate");
        close.complete("late rejection");Handler.drain();
        check(!NavGlasses.reopen(owner,1,2,"rejected",SystemClock.now+10000,SystemClock.now,s->{}),"close SDK rejection revokes qualification");
        check(before==NavReflect.messages.size(),"denied reopen emits no packets");NavGlasses.confirmClear(owner,1);
        ScadaCaptionController inactive=new ScadaCaptionController();before=NavReflect.messages.size();
        inactive.onReading(1,reading(1),SystemClock.now,SystemClock.now);check(before==NavReflect.messages.size(),"explicit scan start required");
        long oldCapture=SystemClock.now;Handler.advance(1);inactive.beginScan(3);
        inactive.onReading(3,reading(2),oldCapture,SystemClock.now);check(before==NavReflect.messages.size(),"capture predating scan start rejected");inactive.stopScan();
    }
    private static void controllerWaitsForCloseSdkEvidence() {
        for(boolean accepted:new boolean[]{true,false}){
            ScadaCaptionController c=controller();c.onReading(1,reading(2300),SystemClock.now,SystemClock.now);String sid=ready();
            Handler.advance(1);NavReflect.rejectStop=!accepted;c.onExit(1);NavReflect.rejectStop=false;
            SystemClock.now+=8001;int before=NavReflect.messages.size();
            for(int i=0;i<4;i++)c.onReading(1,reading(2301),SystemClock.now,SystemClock.now);
            check(NavReflect.messages.size()==before,"pending close SDK evidence preserves attempt without sending");
            Handler.drain();c.onReading(1,reading(2302),SystemClock.now,SystemClock.now);
            if(accepted){
                check(NavReflect.messages.size()==before+1&&NavReflect.last().type()==7&&!sid.equals(NavReflect.last().sid()),"late close SDK success permits next fresh controller reading to rotate once");
                ready();
                for(int i=0;i<4;i++)c.onReading(1,reading(2303),SystemClock.now,SystemClock.now);
                check(NavReflect.messages.size()==before+1,"continuous near updates cannot allocate more SIDs");
            }else{
                for(int i=0;i<12;i++){Handler.advance(1000);c.onReading(1,reading(2304),SystemClock.now,SystemClock.now);}
                check(NavReflect.messages.size()==before,"late close SDK rejection locks controller attempt");
            }
            c.stopScan();c.confirmClear();Handler.drain();
        }
    }
}
