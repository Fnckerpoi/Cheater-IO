package com.turboio.addon;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.turboio.addon.BoundedJson.*;
import static com.turboio.addon.CardCodec.object;

/** Read -> targeted mutation -> readback -> optional reorder -> readback. No retry. */
final class CardTransport {
    enum Result { SUCCESS, FAILURE }
    interface Completion { void complete(Result result); }

    private static String owner="",peer="",id="",step="",note="草稿只保存在手机";
    private static long sequence;
    private static Map<String,Object> before,install;
    private static boolean first,mutated,initialized,readOnly,existed;
    private static int targetIndex=-1;
    private static Runnable changed;
    private static Completion completion;
    private static final List<Runnable> idleWaiters=new ArrayList<>();

    static void init(){if(!initialized){initialized=true;NativeTransfer.receive(CardTransport::event);}}
    static void listen(Runnable r){changed=r;}
    static void detach(){changed=null;}
    static String status(){return note;}
    static boolean busy(){return !owner.isEmpty();}
    static void whenIdle(Runnable r){if(r==null)return;if(!busy())r.run();else if(!idleWaiters.contains(r))idleWaiters.add(r);}
    private static void mark(String s){note=s;if(changed!=null)changed.run();}

    static void inspect(){if(busy()){mark("上一轮仍在确认");return;}readOnly=true;install=null;id="";first=false;completion=null;begin();}
    static void publish(Map<String,Object> doc,boolean top){publish(doc,top,null);}
    static boolean publish(Map<String,Object> doc,boolean top,Completion done){
        if(busy()){mark("上一轮仍在确认");return false;}
        readOnly=false;completion=done;
        try{install=CardCodec.install(doc,CardIcons::pixels);id=(String)doc.get("id");first=top;return begin();}
        catch(RuntimeException e){finish(Result.FAILURE,"草稿校验失败，未发送");return false;}
    }
    static void remove(String ident){remove(ident,null);}
    static boolean remove(String ident,Completion done){
        if(busy()){mark("上一轮仍在确认");return false;}
        if(ident==null||!ident.matches("turbo_ui_card_[a-z0-9_]{1,34}")){completion=done;finish(Result.FAILURE,"卡片 ID 无效，未发送");return false;}
        readOnly=false;completion=done;id=ident;install=null;first=false;return begin();
    }
    private static boolean begin(){
        String lease=UUID.randomUUID().toString();
        if(!NativeTransfer.acquireMessages(lease)){finish(Result.FAILURE,"请先连接眼镜并结束其他传输/显示任务");return false;}
        owner=lease;peer=NativeTransfer.connected();before=null;mutated=false;existed=false;targetIndex=-1;query("before");return true;
    }
    private static void finish(Result result,String s){
        String old=owner;Completion done=completion;
        owner="";sequence=0;before=null;install=null;step="";completion=null;existed=false;targetIndex=-1;
        if(!old.isEmpty())NativeTransfer.releaseMessages(old);
        mark(s);
        if(done!=null)try{done.complete(result);}catch(RuntimeException ignored){}
        if(!idleWaiters.isEmpty()){
            List<Runnable> ready=new ArrayList<>(idleWaiters);idleWaiters.clear();
            for(Runnable r:ready)try{r.run();}catch(RuntimeException ignored){}
        }
    }
    private static void fail(String s){finish(Result.FAILURE,s);}
    private static void succeed(String s){finish(Result.SUCCESS,s);}
    private static void query(String stage){send(object("cmd","dashboard_config","payload",object("version",1,"value",0)),stage);}
    private static void send(Map<String,Object> json,String stage){
        if(owner.isEmpty())return;
        if(!peer.equals(NativeTransfer.connected())){fail("连接已变化，停止操作；请回读配置");return;}
        long seq=0x40000000L+new java.security.SecureRandom().nextInt(0x3fffffff);sequence=seq;step=stage;String lease=owner;
        try{
            byte[] bytes=CardEnvelope.request(seq,json);mark("仪表盘 · "+stage+" · "+bytes.length+" 字节，等待回执");
            if(stage.equals("install")||stage.equals("remove")||stage.equals("order"))mutated=true;
            NativeTransfer.sendMessage(owner,peer,bytes,()->{if(owner.equals(lease)&&sequence==seq)fail("发送失败，结果未确认；未自动重发");});
            NativeTransfer.MAIN.postDelayed(()->{if(owner.equals(lease)&&sequence==seq)fail(mutated?"20秒无回执，修改结果未知；请核对眼镜，未自动重发":"读取超时，未修改卡片");},20000);
        }catch(Exception e){fail("通信调用失败，未继续修改");}
    }
    private static Map<String,Object> config(Map<String,Object> j){
        if(!"dashboard_config".equals(j.get("cmd")))throw new IllegalArgumentException();
        Object raw=map(j.get("payload")).get("data");
        Map<String,Object> c=map(raw instanceof String?BoundedJson.read(((String)raw).getBytes(StandardCharsets.UTF_8)):raw);
        CardCodec.rows(c);return c;
    }
    private static Map<String,Object> find(Map<String,Object> c,String ident){for(Object o:CardCodec.rows(c))if(ident.equals(map(o).get("id")))return map(o);return null;}
    private static boolean sameExtras(Object a,Object b){if(!(a instanceof String)||!(b instanceof String))return false;return Objects.equals(read(((String)a).getBytes(StandardCharsets.UTF_8)),read(((String)b).getBytes(StandardCharsets.UTF_8)));}
    private static void event(String d,byte[] bytes){
        if(owner.isEmpty()||!d.equals(peer))return;
        CardEnvelope.Reply r=CardEnvelope.reply(bytes);if(r==null||r.sequence!=sequence)return;
        if(!peer.equals(NativeTransfer.connected())){fail("连接变化，停止本轮");return;}
        try{
            Map<String,Object> j=r.json;String current=step;
            if(current.equals("before")){
                before=config(j);
                if(readOnly){
                    StringBuilder report=new StringBuilder("眼镜仪表盘 · 已回读（未修改）");boolean weather=false;int index=0;
                    for(Object row:CardCodec.rows(before)){Map<String,Object> item=map(row);String type=String.valueOf(item.get("type"));String label=String.valueOf(item.getOrDefault("name",item.get("id")));report.append("\n").append(++index).append(" · ").append(label).append(" [").append(type).append("]");weather|=type.toLowerCase(Locale.ROOT).contains("weather")||String.valueOf(item.get("id")).toLowerCase(Locale.ROOT).contains("weather");}
                    report.append(weather?"\n存在天气卡片；天气数据是否有效需另查官方天气/城市设置。":"\n未识别到天气卡片；请先在官方仪表盘添加天气，不会自动覆盖现有卡片。");succeed(report.toString());return;
                }
                Map<String,Object> old=find(before,id);existed=old!=null;targetIndex=CardCodec.indexOf(before,id);
                if(old!=null&&!("a2ui".equals(old.get("type"))))throw new IllegalArgumentException();
                if(install==null&&old==null){fail("眼镜没有这张卡片，未执行移除");return;}
                send(install!=null?install:object("cmd","widget_uninstall","payload",object("data",object("id",id))),install!=null?"install":"remove");
            }
            else if(current.equals("install")||current.equals("remove")){
                if(number(j.get("code"),0,65535)!=0){fail("眼镜拒绝修改，已停止");return;}
                query(current.equals("install")?"verify-install":"verify-remove");
            }
            else if(current.equals("verify-install")){
                Map<String,Object> after=config(j),own=find(after,id);
                if(own==null||!"a2ui".equals(own.get("type"))||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                Map<String,Object> expected=map(map(install.get("payload")).get("data"));
                if(!Objects.equals(own.get("name"),expected.get("name"))||!sameExtras(own.get("extras"),expected.get("extras")))throw new IllegalArgumentException();
                if(existed)targetIndex=CardCodec.indexOf(before,id);else targetIndex=first?0:CardCodec.rows(after).size()-1;
                if(CardCodec.indexOf(after,id)!=targetIndex){Map<String,Object> ordered=CardCodec.atIndex(after,id,targetIndex);send(object("cmd","dashboard_update","payload",object("version",1,"value",0,"data",new String(canonical(ordered),StandardCharsets.UTF_8))),"order");}
                else succeed("眼镜已确认并回读卡片，其他卡片保留；请检查镜片显示");
            }
            else if(current.equals("order")){
                if(!"dashboard_update".equals(j.get("cmd"))||number(map(j.get("payload")).get("value"),0,65535)!=0)throw new IllegalArgumentException();
                query("verify-order");
            }
            else if(current.equals("verify-order")){
                Map<String,Object> after=config(j);
                if(CardCodec.indexOf(after,id)!=targetIndex||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                succeed(first&&!existed?"卡片已置顶并回读确认，其他卡片顺序保留；镜片效果待确认":"卡片内容已回读，原有顺序及其他卡片保留；镜片效果待确认");
            }
            else if(current.equals("verify-remove")){
                Map<String,Object> after=config(j);
                if(find(after,id)!=null||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                succeed("本卡已移除，其他卡片保留");
            }
        }catch(RuntimeException e){fail("配置或回执不符，已停止后续修改；请核对眼镜，未自动重试");}
    }
}
