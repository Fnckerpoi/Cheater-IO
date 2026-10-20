package com.turboio.addon;

import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.turboio.addon.CardCodec.object;

public final class ScadaCardControllerTest{
    private static int checks;
    private static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError("check "+checks+" failed: "+why+"; controller="+ScadaCardController.status()+"; transport="+CardTransport.status());}
    private static CardEnvelope.Reply request(){byte[] b=NativeTransfer.packets.get(NativeTransfer.packets.size()-1).clone();b[3]=19;b[b.length-1]=2;return CardEnvelope.reply(b);}
    private static Map<String,Object> current(){return request().json;}
    private static void reply(Map<String,Object> json){long seq=request().sequence;byte[] b=CardEnvelope.request(seq,json);b[3]=19;b[b.length-1]=2;NativeTransfer.receiver.message(NativeTransfer.peer,b);NativeTransfer.MAIN.drain();}
    private static void config(Map<String,Object> value){reply(object("cmd","dashboard_config","payload",object("data",new String(BoundedJson.canonical(value),StandardCharsets.UTF_8))));}
    private static Map<String,Object> copy(Map<String,Object> value){return BoundedJson.map(BoundedJson.read(BoundedJson.canonical(value)));}
    private static Map<String,Object> installData(){return BoundedJson.map(BoundedJson.map(current().get("payload")).get("data"));}
    private static String command(){return String.valueOf(current().get("cmd"));}
    private static List<Object> rows(Map<String,Object> c){return CardCodec.rows(c);}
    private static Map<String,Object> replaceOrAppend(Map<String,Object> baseline,Map<String,Object> own){Map<String,Object> out=copy(baseline);List<Object> list=new ArrayList<>(rows(out));int index=CardCodec.indexOf(out,(String)own.get("id"));if(index<0)list.add(own);else list.set(index,own);out.put("widgets_v2",list);return out;}
    private static Map<String,Object> removeRow(Map<String,Object> baseline,String id){Map<String,Object> out=copy(baseline);List<Object> list=new ArrayList<>();for(Object row:rows(out))if(!id.equals(BoundedJson.map(row).get("id")))list.add(row);out.put("widgets_v2",list);return out;}
    private static void preserved(Map<String,Object> before,Map<String,Object> after,String target){yes(CardCodec.preserves(before,after,target),"other dashboard rows and settings preserved");}
    private static Map<String,Object> finishInstall(Map<String,Object> before){
        yes(command().equals("dashboard_config"),"transaction starts with guarded read");config(before);
        yes(command().equals("widget_install"),"targeted upsert command issued");Map<String,Object> own=installData();reply(object("code",0));
        Map<String,Object> after=replaceOrAppend(before,own);config(after);
        if(command().equals("dashboard_update")){Map<String,Object> payload=BoundedJson.map(current().get("payload"));Map<String,Object> ordered=BoundedJson.map(BoundedJson.read(((String)payload.get("data")).getBytes(StandardCharsets.UTF_8)));reply(object("cmd","dashboard_update","payload",object("value",0)));yes(command().equals("dashboard_config"),"ordering is followed by another readback");config(ordered);after=ordered;}
        yes(!CardTransport.busy(),"upsert transaction completes only after readback");preserved(before,after,ScadaCardController.CARD_ID);return after;
    }
    private static Map<String,Object> finishProbe(Map<String,Object> before){
        yes(command().equals("dashboard_config"),"probe starts with read");config(before);yes(command().equals("widget_install"),"probe uses targeted install");Map<String,Object> own=installData();reply(object("code",0));Map<String,Object> after=replaceOrAppend(before,own);config(after);yes(!CardTransport.busy(),"probe readback completes");return after;
    }
    private static void finishRemove(Map<String,Object> before){
        yes(command().equals("dashboard_config"),"remove begins with read");config(before);yes(command().equals("widget_uninstall"),"remove targets only SCADA id");reply(object("code",0));Map<String,Object> after=removeRow(before,ScadaCardController.CARD_ID);config(after);yes(!CardTransport.busy(),"remove verified by readback");preserved(before,after,ScadaCardController.CARD_ID);
    }
    private static ScadaReading reading(long counter,int centi,int pressure){return new ScadaReading("rayneo-test-device",counter,centi,pressure,-55,SystemClock.elapsedRealtime());}
    private static void expectInstallMatches(ScadaReading reading,boolean stale){Map<String,Object> expected=CardCodec.install(ScadaCardController.document(reading,stale),CardIcons::pixels);Map<String,Object> expectedData=BoundedJson.map(BoundedJson.map(expected.get("payload")).get("data"));yes(Objects.equals(expectedData.get("extras"),installData().get("extras")),"upsert contains newest expected sample/state");}
    private static String extrasFor(Map<String,Object> config,String id){for(Object row:rows(config)){Map<String,Object> m=BoundedJson.map(row);if(id.equals(m.get("id")))return String.valueOf(m.get("extras"));}return "";}
    private static void expectReadbackMatches(ScadaReading reading,boolean stale,Map<String,Object> readback){Map<String,Object> expected=CardCodec.install(ScadaCardController.document(reading,stale),CardIcons::pixels);Map<String,Object> expectedData=BoundedJson.map(BoundedJson.map(expected.get("payload")).get("data"));yes(Objects.equals(expectedData.get("extras"),extrasFor(readback,ScadaCardController.CARD_ID)),"expected card content is present in dashboard readback");}
    private static Map<String,Object> dashboard(Map<String,Object>... rows){return object("widgets_v2",new ArrayList<Object>(Arrays.asList(rows)),"simple_mode",true);}
    private static void checkScadaIndex(Map<String,Object> c,int wanted){yes(CardCodec.indexOf(c,ScadaCardController.CARD_ID)==wanted,"SCADA card stays at its previous row position");}

    public static void main(String[] args){
        CardTransport.init();SystemClock.now=1000;
        Map<String,Object> weather=object("id","weather","type","native"),todo=object("id","todo","type","native");Map<String,Object> base=dashboard(weather,todo);
        CardTransport.publish(CardCodec.template(5,"turbo_ui_card_busy_probe"),false);yes(CardTransport.busy(),"external card operation is active");
        ScadaReading first=reading(1,2345,101325),intermediate=reading(2,2500,100900),newest=reading(3,2678,100876);
        ScadaCardController.onReading(first);ScadaCardController.onReading(intermediate);ScadaCardController.onReading(newest);NativeTransfer.MAIN.drain();
        int packetsWhileBusy=NativeTransfer.packets.size();yes(packetsWhileBusy==1,"controller does not start a parallel transaction");
        Map<String,Object> withProbe=finishProbe(base);NativeTransfer.MAIN.drain();yes(command().equals("dashboard_config"),"coalesced controller starts after active transaction completion");
        Map<String,Object> initial=finishInstall(withProbe);expectReadbackMatches(newest,false,initial);checkScadaIndex(initial,0);
        yes(rows(initial).size()==4,"first SCADA publish preserves all existing dashboard cards");

        Map<String,Object> middle=CardCodec.atIndex(initial,ScadaCardController.CARD_ID,1);ScadaReading changed=reading(4,-123,98765);
        NativeTransfer.MAIN.advance(9000);ScadaCardController.onReading(changed);NativeTransfer.MAIN.drain();yes(command().equals("dashboard_config"),"sensor refresh waits for 10-second interval");
        NativeTransfer.MAIN.advance(1000);yes(command().equals("dashboard_config"),"refresh wakes at ten seconds");
        config(middle);yes(command().equals("widget_install"),"same-id telemetry uses upsert");expectInstallMatches(changed,false);Map<String,Object> changedOwn=installData();reply(object("code",0));Map<String,Object> middleUpdated=replaceOrAppend(middle,changedOwn);config(middleUpdated);
        yes(!CardTransport.busy(),"same-id changed content accepted only after readback");yes(command().equals("dashboard_config"),"existing card update does not reorder it");preserved(middle,middleUpdated,ScadaCardController.CARD_ID);checkScadaIndex(middleUpdated,1);
        yes(!extrasFor(middle,ScadaCardController.CARD_ID).equals(extrasFor(middleUpdated,ScadaCardController.CARD_ID)),"changed sample produces changed same-id content");
        yes(extrasFor(middleUpdated,ScadaCardController.CARD_ID).equals(String.valueOf(changedOwn.get("extras"))),"changed same-id content is reflected by readback");

        ScadaReading queuedA=reading(5,3001,100500),queuedB=reading(6,3102,100400);
        NativeTransfer.MAIN.advance(1000);ScadaCardController.onReading(queuedA);NativeTransfer.MAIN.drain();yes(command().equals("dashboard_config"),"sensor refresh remains rate limited");
        ScadaCardController.onReading(queuedB);NativeTransfer.MAIN.drain();
        NativeTransfer.MAIN.advance(9000);yes(command().equals("dashboard_config"),"coalesced refresh starts at next allowed interval");
        config(middleUpdated);yes(command().equals("widget_install"),"coalesced update uses one install");expectInstallMatches(queuedB,false);Map<String,Object> queuedOwn=installData();reply(object("code",0));Map<String,Object> queuedReadback=replaceOrAppend(middleUpdated,queuedOwn);config(queuedReadback);yes(!CardTransport.busy(),"latest coalesced sample readback completes");

        NativeTransfer.MAIN.advance(1000);yes(command().equals("dashboard_config"),"10 seconds without input queues automatic stale update");
        config(queuedReadback);yes(command().equals("widget_install"),"stale transition updates same SCADA card");expectInstallMatches(queuedB,true);Map<String,Object> staleOwn=installData();reply(object("code",0));Map<String,Object> staleReadback=replaceOrAppend(queuedReadback,staleOwn);config(staleReadback);yes(!CardTransport.busy()&&ScadaCardController.status().contains("stale"),"stale state is confirmed through readback");preserved(queuedReadback,staleReadback,ScadaCardController.CARD_ID);checkScadaIndex(staleReadback,1);

        NativeTransfer.MAIN.advance(9000);ScadaCardController.onReading(reading(7,2800,100200));NativeTransfer.MAIN.drain();yes(command().equals("dashboard_config"),"next sample is rate limited independently");
        NativeTransfer.MAIN.advance(1000);yes(command().equals("dashboard_config"),"new sample starts after refresh window");
        ScadaCardController.markStale();NativeTransfer.MAIN.drain();
        config(staleReadback);yes(command().equals("widget_install"),"busy sensor transaction continues");ScadaReading current=reading(7,2800,100200);expectInstallMatches(current,false);Map<String,Object> currentOwn=installData();reply(object("code",0));Map<String,Object> currentReadback=replaceOrAppend(staleReadback,currentOwn);config(currentReadback);
        yes(command().equals("dashboard_config"),"queued stale transition runs immediately after sensor transaction");config(currentReadback);yes(command().equals("widget_install"),"queued stale transition performs its own upsert");expectInstallMatches(current,true);Map<String,Object> finalStaleOwn=installData();reply(object("code",0));Map<String,Object> finalStale=replaceOrAppend(currentReadback,finalStaleOwn);config(finalStale);yes(!CardTransport.busy(),"queued stale transition readback completes");

        ScadaCardController.remove();NativeTransfer.MAIN.drain();finishRemove(finalStale);yes(ScadaCardController.status().contains("removed"),"remove outcome is exposed");

        ScadaCardController.onReading(reading(8,2222,100000));NativeTransfer.MAIN.drain();yes(command().equals("dashboard_config"),"new session waits for the sensor refresh window after removal");NativeTransfer.MAIN.advance(9900);ScadaCardController.onReading(reading(8,2223,100001));NativeTransfer.MAIN.drain();NativeTransfer.MAIN.advance(100);yes(command().equals("dashboard_config"),"explicit new-session sample starts at ten seconds");int startPackets=NativeTransfer.packets.size();NativeTransfer.MAIN.advance(20000);yes(!CardTransport.busy()&&ScadaCardController.status().contains("failed"),"transport timeout is exposed as a failure");int afterFailure=NativeTransfer.packets.size();NativeTransfer.MAIN.advance(10000);yes(NativeTransfer.packets.size()==afterFailure,"queued stale work is stopped after timeout");
        ScadaCardController.onReading(reading(9,2300,99900));NativeTransfer.MAIN.drain();yes(NativeTransfer.packets.size()==afterFailure+1&&command().equals("dashboard_config"),"new explicit sample starts a fresh session after failure");NativeTransfer.fail.run();yes(!CardTransport.busy()&&ScadaCardController.status().contains("failed"),"send failure reaches typed completion result");int failedPackets=NativeTransfer.packets.size();NativeTransfer.MAIN.advance(20000);yes(NativeTransfer.packets.size()==failedPackets,"failed session does not automatically retry");yes(startPackets>0,"timeout scenario submitted a transaction");

        System.out.println("ScadaCardControllerTest: "+checks+" assertions; upsert/readback/coalescing/stale/remove/failure");
    }
}
