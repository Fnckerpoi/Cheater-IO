package com.turboio.addon;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DisplayCodec {
    public static String clip(String text,int bytes){StringBuilder b=new StringBuilder();int n=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);String c=new String(Character.toChars(cp));int size=c.getBytes(StandardCharsets.UTF_8).length;if(n+size>bytes)break;b.append(c);n+=size;i+=Character.charCount(cp);}return b.toString();}
    public static byte[] packet(int type,String json) {
        if(type!=3&&type!=5&&type!=7)throw new IllegalArgumentException("type");
        byte[] data=json.getBytes(StandardCharsets.UTF_8);if(data.length>4096)throw new IllegalArgumentException("size");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(8);out.write(1);out.write(16);out.write(type);out.write(26);
        int n=data.length;do{int v=n&127;n>>>=7;out.write(n==0?v:v|128);}while(n!=0);out.write(data,0,data.length);return out.toByteArray();
    }
    public static final class Envelope {public final int type;public final String json;Envelope(int t,String j){type=t;json=j;}}
    private static long var(byte[] b,int[] p){long v=0;for(int i=0;i<10;i++){if(p[0]>=b.length)throw new IllegalArgumentException();int c=b[p[0]++]&255;if(i==9&&(c&254)!=0)throw new IllegalArgumentException();v|=(long)(c&127)<<(7*i);if((c&128)==0)return v;}throw new IllegalArgumentException();}
    public static Envelope decode(byte[] b){
        if(b==null||b.length>262144) return null;
        try{int[] p={0};int seen=0,type=-1;long version=0;String json="{}";
            while(p[0]<b.length){long k=var(b,p);int tag=(int)(k>>3),wire=(int)(k&7);if(tag<1||tag>4||(seen&(1<<tag))!=0)return null;seen|=1<<tag;
                if(tag<=2){if(wire!=0)return null;long v=var(b,p);if(tag==1)version=v;else {if(v>255||v<0)return null;type=(int)v;}}
                else{if(wire!=2)return null;long n=var(b,p);if(n<0||n>b.length-p[0])return null;if(tag==3)json=new String(b,p[0],(int)n,StandardCharsets.UTF_8);p[0]+=(int)n;}}
            return version==1&&type>=0?new Envelope(type,json):null;
        }catch(RuntimeException e){return null;}
    }
}
