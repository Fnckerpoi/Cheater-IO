package com.turboio.addon;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.turboio.addon.BoundedJson.*;
import static com.turboio.addon.CardCodec.object;

/** Read -> targeted mutation -> readback -> optional reorder -> readback. No retry. */
final class CardTransport {
    enum Result { SUCCESS, UNVERIFIED, FAILURE }
    interface Completion { void complete(Result result); }

    private static String owner="",peer="",id="",step="",note="草稿只保存在手机";
    private static long sequence;
    private static Map<String,Object> before,install;
    private static boolean first,mutated,initialized,readOnly,existed,existingOnly;
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

    static void inspect(){if(busy()){mark("上一轮仍在确认");return;}readOnly=true;install=null;id="";first=false;existingOnly=false;completion=null;begin();}
    static void publishDevcard(String text,boolean top){if(busy()){mark("上一轮仍在确认");return;}readOnly=false;completion=null;existingOnly=false;try{install=CardCodec.devcard(text);id="turbo_ui_devcard_v1";first=top;begin();}catch(RuntimeException e){finish(Result.FAILURE,"TDC1 数值不合法，未发送");}}
    static void publish(Map<String,Object> doc,boolean top){publish(doc,top,null);}
    static boolean publish(Map<String,Object> doc,boolean top,Completion done){
        return publish(doc,top,false,done);
    }
    static boolean publish(Map<String,Object> doc,boolean top,boolean existingOnly,Completion done){
        if(busy()){mark("上一轮仍在确认");return false;}
        readOnly=false;completion=done;CardTransport.existingOnly=existingOnly;
        try{install=CardCodec.install(doc,CardIcons::pixels);id=(String)doc.get("id");first=top;return begin();}
        catch(RuntimeException e){finish(Result.FAILURE,"草稿校验失败，未发送");return false;}
    }
    static void remove(String ident){remove(ident,null);}
    static boolean remove(String ident,Completion done){
        if(busy()){mark("上一轮仍在确认");return false;}
        if(ident==null||!(ident.matches("turbo_ui_card_[a-z0-9_]{1,34}")||ident.equals("turbo_ui_devcard_v1"))){completion=done;finish(Result.FAILURE,"卡片 ID 无效，未发送");return false;}
        readOnly=false;completion=done;id=ident;install=null;first=false;existingOnly=false;return begin();
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
        Map<String,Object> c=decodeConfig(j);
        validateConfig(c);
        return c;
    }
    private static Map<String,Object> decodeConfig(Map<String,Object> j){
        if(!"dashboard_config".equals(j.get("cmd")))throw new IllegalArgumentException("回执不是仪表盘配置");
        if(!(j.get("payload") instanceof Map))throw new IllegalArgumentException("回执缺少配置载荷");
        Map<String,Object> payload=map(j.get("payload"));
        Object raw=payload.get("data");
        if(raw==null)throw new IllegalArgumentException("回执未返回配置数据（value="+numericStatus(payload.get("value"))+"）");
        Object decoded=raw instanceof String?BoundedJson.readDevice(((String)raw).getBytes(StandardCharsets.UTF_8)):raw;
        if(!(decoded instanceof Map))throw new IllegalArgumentException("配置数据不是对象");
        return map(decoded);
    }
    private static void validateConfig(Map<String,Object> c){
        Object rows=c.get("widgets_v2");
        if(!(rows instanceof List))throw new IllegalArgumentException("配置缺少 widgets_v2 列表（"+describeConfig(c)+"）");
        CardCodec.rows(c);
    }
    private static String numericStatus(Object value){
        return value instanceof Number?value.toString():"无";
    }
    private static String shape(Object value){
        if(value==null)return "空值";
        if(value instanceof List)return "列表("+list(value).size()+")";
        if(value instanceof Map)return "对象";
        if(value instanceof String)return "字符串";
        if(value instanceof Number)return "数字";
        if(value instanceof Boolean)return "布尔值";
        return "其他";
    }
    private static String fieldShape(Map<String,Object> value,String key){
        return value.containsKey(key)?shape(value.get(key)):"未返回";
    }
    private static String describeConfig(Map<String,Object> c){
        StringBuilder result=new StringBuilder("widgets=").append(fieldShape(c,"widgets"))
            .append("，widgets_v2=").append(fieldShape(c,"widgets_v2"));
        Object legacy=c.get("widgets");
        if(legacy instanceof List&&!list(legacy).isEmpty()){
            Object first=list(legacy).get(0);result.append("，首项=").append(shape(first));
            if(first instanceof Map){
                Map<String,Object> item=map(first);
                for(String key:new String[]{"id","type","name","extras"})result.append("，").append(key).append("=").append(fieldShape(item,key));
            }
        }
        return result.toString();

    }
    private static Map<String,Object> find(Map<String,Object> c,String ident){for(Object o:CardCodec.rows(c))if(ident.equals(map(o).get("id")))return map(o);return null;}
    private static Map<String,Object> extras(Object value){
        return map(value instanceof String?read(((String)value).getBytes(StandardCharsets.UTF_8)):value);
    }
    private static boolean matchesInstall(Map<String,Object> row){
        if(row==null||!"a2ui".equals(row.get("type")))return false;
        Map<String,Object> expected=map(map(install.get("payload")).get("data"));
        if(row.containsKey("name")&&!Objects.equals(row.get("name"),expected.get("name")))return false;
        return !row.containsKey("extras")||extras(row.get("extras")).equals(extras(expected.get("extras")));
    }
    private static boolean expectedReply(String stage,Map<String,Object> reply){
        if(stage.equals("install")||stage.equals("remove"))return reply.get("code") instanceof Number;
        return (stage.equals("order")?"dashboard_update":"dashboard_config").equals(reply.get("cmd"));
    }
    private static String stageLabel(String stage){
        switch(stage){
            case "before":return "读取原配置";
            case "install":return "提交卡片";
            case "verify-install":return "回读安装结果";
            case "order":return "调整排序";
            case "verify-order":return "回读排序";
            case "remove":return "移除";
            case "verify-remove":return "回读移除结果";
            default:return stage;
        }
    }
    private static void event(String d,byte[] bytes){
        if(owner.isEmpty()||!d.equals(peer))return;
        CardEnvelope.Reply r=CardEnvelope.reply(bytes);if(r==null||r.sequence!=sequence)return;
        if(!peer.equals(NativeTransfer.connected())){fail("连接变化，停止本轮");return;}
        try{
            Map<String,Object> j=r.json;String current=step;
            if(!expectedReply(current,j))return;
            if(current.equals("before")){
                before=decodeConfig(j);
                if(readOnly){
                    try{validateConfig(before);}catch(RuntimeException unsupported){
                        succeed("眼镜仪表盘 · 已回读（未修改）；配置格式暂不支持写入（"+describeConfig(before)+"）");return;
                    }
                    StringBuilder report=new StringBuilder("眼镜仪表盘 · 已回读（未修改）");boolean weather=false;int index=0;
                    for(Object row:CardCodec.rows(before)){Map<String,Object> item=map(row);String type=String.valueOf(item.get("type"));String label=String.valueOf(item.getOrDefault("name",item.get("id")));report.append("\n").append(++index).append(" · ").append(label).append(" [").append(type).append("]");weather|=type.toLowerCase(Locale.ROOT).contains("weather")||String.valueOf(item.get("id")).toLowerCase(Locale.ROOT).contains("weather");}
                    report.append(weather?"\n存在天气卡片；天气数据是否有效需另查官方天气/城市设置。":"\n未识别到天气卡片；请先在官方仪表盘添加天气，不会自动覆盖现有卡片。");succeed(report.toString());return;
                }
                validateConfig(before);
                Map<String,Object> old=find(before,id);existed=old!=null;targetIndex=CardCodec.indexOf(before,id);
                if(old!=null&&!("a2ui".equals(old.get("type"))))throw new IllegalArgumentException();
                if(existingOnly&&!existed){fail("眼镜上没有此卡片，请确认后重新安装");return;}
                if(install==null&&old==null){fail("眼镜没有这张卡片，未执行移除");return;}
                send(install!=null?install:object("cmd","widget_uninstall","payload",object("data",object("id",id))),install!=null?"install":"remove");
            }
            else if(current.equals("install")||current.equals("remove")){
                if(number(j.get("code"),0,65535)!=0){fail("眼镜拒绝修改，已停止");return;}
                query(current.equals("install")?"verify-install":"verify-remove");
            }
            else if(current.equals("verify-install")){
                Map<String,Object> after=config(j),own=find(after,id);
                if(!matchesInstall(own)||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                if(existed&&!own.containsKey("extras")){finish(Result.UNVERIFIED,"卡片身份已回读，更新结果未知：内容未回读；未继续排序");return;}
                if(existed)targetIndex=CardCodec.indexOf(before,id);else targetIndex=first?0:CardCodec.rows(after).size()-1;
                if(CardCodec.indexOf(after,id)!=targetIndex){Map<String,Object> ordered=CardCodec.atIndex(after,id,targetIndex);send(object("cmd","dashboard_update","payload",object("version",1,"value",0,"data",new String(canonical(ordered),StandardCharsets.UTF_8))),"order");}
                else if(own.containsKey("extras"))succeed("眼镜已确认并回读卡片，其他卡片保留；请检查镜片显示");
                else finish(Result.UNVERIFIED,"卡片身份已回读，其他卡片保留；内容未回读，请检查镜片显示");
            }
            else if(current.equals("order")){
                if(!"dashboard_update".equals(j.get("cmd"))||number(map(j.get("payload")).get("value"),0,65535)!=0)throw new IllegalArgumentException();
                query("verify-order");
            }
            else if(current.equals("verify-order")){
                Map<String,Object> after=config(j);
                Map<String,Object> own=find(after,id);
                if(CardCodec.indexOf(after,id)!=targetIndex||!matchesInstall(own)||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                if(!own.containsKey("extras")){finish(Result.UNVERIFIED,"卡片身份与排序已回读，但内容未回读；请检查镜片显示");return;}
                succeed(first&&!existed?"卡片已置顶并回读确认，其他卡片顺序保留；镜片效果待确认":"卡片内容已回读，原有顺序及其他卡片保留；镜片效果待确认");
            }
            else if(current.equals("verify-remove")){
                Map<String,Object> after=config(j);
                if(find(after,id)!=null||!CardCodec.preserves(before,after,id))throw new IllegalArgumentException();
                succeed("本卡已移除，其他卡片保留");
            }
        }catch(RuntimeException e){
            String reason=e.getMessage();
            fail("仪表盘"+stageLabel(step)+"失败"+(reason==null?"：配置或回执不符":"："+reason)
                +(mutated?"；请核对眼镜，未自动重试":"；未修改卡片"));
        }
    }
}
