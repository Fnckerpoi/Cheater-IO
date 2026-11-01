package com.turboio.addon;

import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DshWorkbenchCardTest {
    private static int checks;
    private static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Map<String,Object> obj(Object...p){return CardCodec.object(p);}
    private static Map<String,Object> copy(Map<String,Object> m){return BoundedJson.map(BoundedJson.read(BoundedJson.canonical(m)));}
    private static CardEnvelope.Reply req(){byte[] b=NativeTransfer.packets.get(NativeTransfer.packets.size()-1).clone();b[3]=19;b[b.length-1]=2;return CardEnvelope.reply(b);}
    private static void reply(Map<String,Object> m){long s=req().sequence;byte[] b=CardEnvelope.request(s,m);b[3]=19;b[b.length-1]=2;NativeTransfer.receiver.message(NativeTransfer.peer,b);NativeTransfer.MAIN.drain();}
    private static Map<String,Object> dashboard(Map<String,Object>...r){return obj("widgets_v2",new ArrayList<Object>(Arrays.asList(r)),"simple_mode",true);}
    private static Map<String,Object> with(Map<String,Object>b,Map<String,Object> own){Map<String,Object>o=copy(b);List<Object> rows=new ArrayList<>(CardCodec.rows(o));int i=CardCodec.indexOf(o,DshWorkbenchCardModel.CARD_ID);if(i<0)rows.add(own);else rows.set(i,own);o.put("widgets_v2",rows);return o;}
    private static Map<String,Object> config(Map<String,Object>c){return obj("cmd","dashboard_config","payload",obj("data",new String(BoundedJson.canonical(c),StandardCharsets.UTF_8)));}
    private static void publish(Map<String,Object> before){yes("dashboard_config".equals(req().json.get("cmd")),"publish starts with dashboard read");reply(config(before));yes("widget_install".equals(req().json.get("cmd")),"publish sends install");Map<String,Object> own=BoundedJson.map(BoundedJson.map(req().json.get("payload")).get("data"));reply(obj("code",0));reply(config(with(before,own)));yes(!CardTransport.busy(),"publish waits for readback");}
    private static void remove(Map<String,Object> before){yes("dashboard_config".equals(req().json.get("cmd")),"remove starts with dashboard read");reply(config(before));yes("widget_uninstall".equals(req().json.get("cmd")),"disable removes workbench card");reply(obj("code",0));Map<String,Object> after=copy(before);List<Object> rows=new ArrayList<>();for(Object row:CardCodec.rows(after))if(!DshWorkbenchCardModel.CARD_ID.equals(BoundedJson.map(row).get("id")))rows.add(row);after.put("widgets_v2",rows);reply(config(after));yes(!CardTransport.busy(),"remove waits for readback");}
    public static void main(String[] args){
        CardTransport.init();
        Map<String,Object> doc=DshWorkbenchCardModel.create("","待命","","");
        byte[] encoded=CardCodec.encode(doc,CardIcons::pixels);
        List<Object> components=BoundedJson.list(doc.get("components"));
        yes(encoded.length<=2048&&components.size()==9,"model stays within TCE1 budgets");
        for(Object raw:components){Map<String,Object> c=BoundedJson.map(raw);for(String key:new String[]{"x","y","w","h"})yes((((Number)c.get(key)).intValue()&1)==0,"geometry is even: "+key);}
        String clip=DshWorkbenchCardModel.clip("😀漢字".repeat(80),54);yes(clip.getBytes(StandardCharsets.UTF_8).length<=54,"text truncation respects UTF-8 bytes");
        Map<String,Object> base=dashboard(obj("id","weather","type","native"),obj("id","notes","type","native"));
        DshWorkbenchCardSync.setEnabled(true);DshWorkbenchCardSync.aa("正在执行","session-1","结果摘要");NativeTransfer.MAIN.drain();
        NativeTransfer.MAIN.advance(799);yes(NativeTransfer.packets.isEmpty(),"publish respects 800ms debounce");
        NativeTransfer.MAIN.advance(1);publish(base);
        int count=NativeTransfer.packets.size();DshWorkbenchCardSync.aa("正在执行","session-1","结果摘要");NativeTransfer.MAIN.drain();NativeTransfer.MAIN.advance(1000);yes(NativeTransfer.packets.size()==count,"identical content is not retransmitted");
        DshWorkbenchCardSync.asr("hello",false);NativeTransfer.MAIN.drain();NativeTransfer.MAIN.advance(800);yes("dashboard_config".equals(req().json.get("cmd")),"ASR update enters serialized transaction");
        reply(config(base));yes("widget_install".equals(req().json.get("cmd")),"ASR updates use card upsert");Map<String,Object> own=BoundedJson.map(BoundedJson.map(req().json.get("payload")).get("data"));reply(obj("code",0));Map<String,Object> with=with(base,own);reply(config(with));yes(!CardTransport.busy(),"updated card is confirmed by readback");
        DshWorkbenchCardSync.setEnabled(false);NativeTransfer.MAIN.drain();remove(with);yes(NativeTransfer.lease.isEmpty(),"disable releases message lease");
        System.out.println("DshWorkbenchCardTest: "+checks+" assertions; budget/debounce/dedup/ASR/update/remove");
    }
}
