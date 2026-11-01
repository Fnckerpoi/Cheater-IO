package com.turboio.addon;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.turboio.addon.CardCodec.object;

/** Bounded TCE1 layout for launcher ASR and the persistent AA workbench. */
public final class DshWorkbenchCardModel {
    public static final String CARD_ID="turbo_ui_card_aa_workbench";
    private DshWorkbenchCardModel(){}

    public static Map<String,Object> create(String asr,String status,String session,String answer){
        String upper=clip(asr,54),state=clip(status,40),sid=clip(session,32),reply=clip(answer,64);
        List<Object> c=new ArrayList<>();
        c.add(CardCodec.txt("title","AA 工作台",8,6,240,18));
        c.add(CardCodec.component("divider","divider",8,32,240,2));
        c.add(CardCodec.txt("asr_label","实时字幕",8,38,240,14));
        c.add(CardCodec.txt("asr_text",upper.isEmpty()?"待命中 · 等待语音":upper,8,58,240,18));
        c.add(CardCodec.component("divider2","divider",8,84,240,2));
        c.add(CardCodec.txt("aa_label","Agent-Anywhere",8,90,240,14));
        c.add(CardCodec.txt("aa_state",state.isEmpty()?"待命":state,8,108,240,16));
        c.add(CardCodec.txt("aa_session",sid.isEmpty()?"会话：暂无":"会话："+sid,8,130,240,14));
        c.add(CardCodec.txt("aa_reply",reply.isEmpty()?"最新回复：暂无":"回复："+reply,8,150,240,14));
        Map<String,Object> d=object("schema",1,"id",CARD_ID,"name","AA 工作台","components",c);
        CardCodec.encode(d,(i,s)->new byte[s*s/8]);
        return d;
    }
    static String clip(String s,int max){
        if(s==null||s.isEmpty())return "";
        String clean=s.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
        if(clean.getBytes(StandardCharsets.UTF_8).length<=max)return clean;
        StringBuilder out=new StringBuilder();int bytes=0;
        for(int i=0;i<clean.length();){int cp=clean.codePointAt(i),n=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;if(bytes+n>max)break;out.appendCodePoint(cp);bytes+=n;i+=Character.charCount(cp);}
        return out.toString();
    }
}
