package com.turboio.addon;
import java.util.*;import java.io.*;import java.net.*;import java.lang.reflect.*;import android.os.Handler;import android.os.SystemClock;import org.json.*;
/** Real ToolClient + DshClient + Display + monitor + Nav; only platform/HTTP boundaries are faked. */
public final class ToolClientDisplayFlowTest {
 static int checks,gets,posts;static String mode="success";static android.content.Context app;static Map<String,Object> prefs;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static final class Timer implements DshProactiveReport.Scheduler {
  final List<Job> jobs=new ArrayList<>();static final class Job implements DshProactiveReport.Handle {Runnable r;boolean canceled;Job(Runnable a){r=a;}public void cancel(){canceled=true;}}
  public DshProactiveReport.Handle schedule(Runnable r,long delay){jobs.add(new Job(r));return ()->jobs.forEach(j->{if(j.r==r)j.canceled=true;});}
  int live(){return (int)jobs.stream().filter(j->!j.canceled).count();}void poll(){while(!jobs.isEmpty()){Job j=jobs.remove(0);if(!j.canceled){j.r.run();return;}}throw new AssertionError("missing monitor poll");}
 }
 static final class Phone implements DshProactiveDisplay.Listener {Map<String,Object> last;int updates;public void onReport(Map<String,Object> r){ok(Thread.holdsLock(DshClient.LOCK)||"stopped".equals(r.get("status")),"phone check and draw same host lock");last=r;updates++;}}
 static final class Connection extends HttpURLConnection {
  ByteArrayOutputStream output=new ByteArrayOutputStream();Connection(URL u){super(u);}
  public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public OutputStream getOutputStream(){return output;}
  public int getResponseCode()throws IOException{ok(!Thread.holdsLock(DshClient.LOCK),"actual HTTP outside host lock");if("POST".equals(method)){posts++;if(mode.equals("throw"))throw new IOException("network unavailable");if(mode.equals("reject"))return 403;if(mode.equals("saveFail"))app.fail=true;}else gets++;return 200;}
  public InputStream getErrorStream(){return stream(DshClient.mapOf("code","forbidden"));}
  InputStream stream(Object o){return new ByteArrayInputStream(DshClient.stringifyJson(o).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  public InputStream getInputStream(){String p=url.getPath();if(p.endsWith("/runtimes"))return stream(DshClient.mapOf("connectorId","connector","runtimes",Arrays.asList(DshClient.mapOf("connectorId","connector","runtimeId","runtime","runtimeType","dsh","status","running","available",true,"configured",true,"active",true))));
   if("POST".equals(method))return stream(DshClient.mapOf("sessionId","session"));
   if(p.endsWith("/state"))return stream(DshClient.mapOf("state",DshClient.mapOf("sessionId","session","runtimeId","runtime","status","idle")));
   String id=(String)prefs.get("dsh_client_message_id");return stream(DshClient.mapOf("sessionId","session","items",Arrays.asList(DshClient.mapOf("role","user","type","message","source",DshClient.mapOf("clientMessageId",id)),DshClient.mapOf("role","assistant","type","message","status","done","content","answer for "+id)),"hasMore",false));
  }
 }
 static Timer timer;static ToolClient host;static DshProactiveDisplay display;static Phone phone;
 static void setup()throws Exception{app=new android.content.Context();host=new ToolClient(app);prefs=app.values.get("turboio_settings");prefs.put("dsh",true);prefs.put("dsh_user_id","user");prefs.put("dsh_url","https://example.com");prefs.put("dsh_connector","connector");prefs.put("dsh_project","project");prefs.put("dsh_runtime","runtime");prefs.put("dsh_model","dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0");prefs.put("dsh_permission","dsh:permission:cmVhZC1vbmx5");Field lock=ToolClient.class.getDeclaredField("dshStorageFailed");lock.setAccessible(true);lock.set(null,false);timer=new Timer();display=new DshProactiveDisplay(host,timer,SystemClock::elapsedRealtime);Field singleton=DshProactiveDisplay.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,display);phone=new Phone();display.subscribe(phone);mode="success";posts=gets=0;}
 static JSONObject dispatch(boolean aa)throws Exception{JSONObject args=new JSONObject().put("task","do work");if(!aa)return host.call("dsh_dispatch",args,"");ToolClient.CardRequest r=host.captureCard();return host.call("dsh_dispatch",args,"",r::guard,r::committed);}
 static void cleanup(){display.stop();Handler.drain();display.confirmGlassesClosed();Handler.drain();ok(!NavGlasses.active(),"own lease cleaned explicitly");}
 public static void main(String[] a)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
  setup();JSONObject result=dispatch(false);ok("submitted".equals(result.optString("status"))&&timer.live()==1,"voice submitted durable slot automatically starts singleton monitor");ok(host.snapshotRound()!=null,"submitted actual slot readable");timer.poll();Handler.drain();ok("completed".equals(phone.last.get("status")),"real status automatically reaches phone terminal");ok(NavGlasses.active(),"real eligible result reserves Nav");cleanup();
  setup();result=dispatch(true);ok("submitted".equals(result.optString("status"))&&timer.live()==1,"AA committed callback starts same monitor path");Handler.drain();int count=phone.updates;String message=(String)prefs.get("dsh_client_message_id");prefs.put("dsh_client_message_id","foreign");timer.poll();Handler.drain();ok(phone.updates==count&&!NavGlasses.active(),"identity changes before poll retire monitor without old result");cleanup();
  setup();dispatch(false);timer.poll();prefs.put("dsh_client_message_id","foreign");Handler.drain();ok(phone.updates==0&&!NavGlasses.active(),"identity change after actual HTTP drops delayed MAIN result");cleanup();
  setup();dispatch(false);timer.poll();prefs.put("dsh_runtime","targetB");Handler.drain();ok(phone.updates==0&&!NavGlasses.active(),"NEW-02 cross-target switch after actual HTTP drops delayed MAIN result");cleanup();
  setup();mode="reject";result=dispatch(false);ok("failed".equals(result.optString("status"))&&timer.live()==0,"HTTP rejection never starts monitor");cleanup();
  setup();mode="throw";result=dispatch(false);ok("unknown_delivery".equals(result.optString("status"))&&timer.live()==0&&Boolean.TRUE.equals(prefs.get("dsh_unknown_delivery")),"unknown transport preserves pending without monitor");cleanup();
  setup();mode="saveFail";result=dispatch(false);ok(!"submitted".equals(result.optString("status"))&&timer.live()==0,"failed durable submitted never starts monitor");app.fail=false;cleanup();
  setup();app.fail=true;try{dispatch(false);throw new AssertionError("pending save failure accepted");}catch(IllegalStateException expected){ok(timer.live()==0&&posts==0,"failed pending persistence sends nothing and starts no monitor");}app.fail=false;cleanup();
  setup();dispatch(false);timer.poll();Handler.drain();ok("completed".equals(phone.last.get("status")),"A finished");String aMessage=(String)prefs.get("dsh_client_message_id");dispatch(false);Handler.drain();ok("submitted".equals(phone.last.get("status"))&&"".equals(phone.last.get("answer"))&&!aMessage.equals(phone.last.get("clientMessageId")),"new submitted B clears completed A card before first B network reply");cleanup();
  System.out.println("ToolClientDisplayFlowTest: "+checks+" checks passed");
 }
}
