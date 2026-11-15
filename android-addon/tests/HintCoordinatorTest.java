package com.turboio.addon;
import java.util.*;
import org.json.*;

public final class HintCoordinatorTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Hint { private String text; final int metadata=42; Hint(String text){this.text=text;} }
 static McpConnectionStore.Snapshot config(long revision)throws Exception {
  java.lang.reflect.Constructor<McpConnectionStore.Snapshot> c=McpConnectionStore.Snapshot.class.getDeclaredConstructor(boolean.class,String.class,String.class,long.class);
  c.setAccessible(true);return c.newInstance(true,"http://127.0.0.1/mcp","",revision);
 }
 static KnowledgeRetrieval.Result hit(long r){return new KnowledgeRetrieval.Result(KnowledgeRetrieval.Status.HIT,Collections.singletonList(new KnowledgeRetrieval.Evidence("ns","doc","/real","{\"owner\":\"a\"}",1,"parent","real fact")),r);}
 static class Fixture implements ProactiveHintAugmentor.State,ProactiveHintAugmentor.MainScheduler {
  long now=0,gen=1,seq=1,rev=1; int retrieves,models,deliveries; String status;
  ArrayDeque<Runnable> work=new ArrayDeque<>(),main=new ArrayDeque<>(); ArrayList<Runnable> timers=new ArrayList<>();
  KnowledgeRetrieval.Status retrievalStatus=KnowledgeRetrieval.Status.HIT;
  ProactiveHintAugmentor a=new ProactiveHintAugmentor(work::add,this,()->now,this,(q,c,b)->{check(b<=1800,"retrieval budget");retrieves++;return retrievalStatus==KnowledgeRetrieval.Status.HIT?hit(c.revision):KnowledgeRetrieval.result(retrievalStatus,c.revision);});
  public long generation(){return gen;} public long sequence(){return seq;} public long revision(){return rev;}
  public void execute(Runnable r){main.add(r);} public ProactiveHintAugmentor.Cancellation schedule(Runnable r,long ms){check(ms==4000,"watchdog deadline");timers.add(r);return ()->timers.remove(r);}
  ProactiveHintAugmentor.Regenerator model=(q,cloud,r,b)->{check(b<=2200,"model budget");models++;return "regenerated";};
  void start(Hint h)throws Exception {a.interceptAndRegenerateAsync(h,config(rev),gen,seq,model,(v,s)->{check(v==h,"same hint object");check(h.metadata==42,"object metadata");deliveries++;status=s;});}
  void finish(){work.remove().run();while(!main.isEmpty())main.remove().run();}
 }
 public static void main(String[] args)throws Exception {
  orderedAndCache(); fallbacks(); watchdog(); stale(); mutable(); http(); hookDispatch();
  System.out.println("HintCoordinatorTest: ALL PASS (coordinator + real HTTP + hook dispatch)");
 }
 static void orderedAndCache()throws Exception {
  Fixture f=new Fixture();Hint h=new Hint("{\"question\":\"q\",\"answer\":\"cloud\",\"extra\":{\"v\":3}}");String before=h.text;
  f.start(h);check(f.deliveries==0&&f.retrieves==0&&before.equals(h.text),"must hold before network");
  f.work.remove().run();check(f.retrieves==1&&f.models==1&&f.deliveries==0&&h.text.equals(before),"worker cannot mutate or deliver");
  f.main.remove().run();JSONObject out=new JSONObject(h.text);check(out.getString("answer").equals("regenerated")&&out.getJSONObject("extra").getInt("v")==3,"preserve envelope");check(f.deliveries==1&&f.timers.isEmpty(),"single delivery cancels timer");
  f.seq++;Hint cached=new Hint("{\"question\":\"q\",\"answer\":\"new cloud\",\"newExtra\":true}");f.start(cached);while(!f.main.isEmpty())f.main.remove().run();check(f.retrieves==1&&f.models==1&&new JSONObject(cached.text).getBoolean("newExtra"),"same question revision caches answer only");
  f.rev++;f.seq++;f.start(new Hint("q"));f.finish();check(f.retrieves==2,"revision invalidates cache");
 }
 static void fallbacks()throws Exception {
  for(KnowledgeRetrieval.Status s:KnowledgeRetrieval.Status.values())if(s!=KnowledgeRetrieval.Status.HIT){Fixture f=new Fixture();f.retrievalStatus=s;Hint h=new Hint("plain");f.start(h);f.finish();check(h.text.equals("plain")&&f.models==0&&f.deliveries==1,"no hit exact fallback "+s);}
  Fixture f=new Fixture();f.model=(q,c,r,b)->{throw new java.io.IOException("model failure");};Hint h=new Hint("q");f.start(h);f.finish();check(h.text.equals("q")&&f.deliveries==1,"model failure original");
  Fixture empty=new Fixture();empty.model=(q,c,r,b)->" ";Hint eh=new Hint("q");empty.start(eh);empty.finish();check(eh.text.equals("q"),"empty regeneration original");
 }
 static void watchdog()throws Exception {
  Fixture f=new Fixture();Hint h=new Hint("original");f.start(h);f.now=4000;f.timers.get(0).run();check(f.deliveries==1&&h.text.equals("original"),"watchdog original");f.finish();check(f.deliveries==1&&f.models==0,"late result suppressed");
  Fixture g=new Fixture();g.model=(q,c,r,b)->{g.now=4001;return "late";};Hint late=new Hint("original");g.start(late);g.finish();check(late.text.equals("original")&&g.deliveries==1,"late model original even before timer runs");
  Fixture r=new Fixture();r.a=new ProactiveHintAugmentor(r.work::add,r,()->r.now,r,(q,c,b)->{r.now=1801;return hit(1);});Hint rh=new Hint("q");r.start(rh);r.finish();check(r.models==0&&rh.text.equals("q"),"retrieval stage expiration");
 }
 static void stale()throws Exception {
  for(int which=0;which<3;which++){Fixture f=new Fixture();Hint h=new Hint("q");f.start(h);if(which==0)f.seq++;if(which==1)f.gen++;if(which==2)f.rev++;f.timers.get(0).run();f.finish();check(f.deliveries==0&&h.text.equals("q"),"stale state suppresses all delivery");}
  Fixture f=new Fixture();Hint old=new Hint("old");f.start(old);f.seq++;Hint fresh=new Hint("fresh");f.start(fresh);f.finish();f.finish();check(f.deliveries==1&&old.text.equals("old")&&fresh.text.equals("regenerated"),"new question preempts old");
 }
 static void mutable()throws Exception {
  Fixture f=new Fixture();Hint h=new Hint("q");f.start(h);f.work.remove().run();h.text="changed";f.main.remove().run();check(f.deliveries==0&&h.text.equals("changed"),"external mutation suppressed");
  String escaped="{\"question\":\"\u4f60\u597d\",\"answer\":\"a\\\\\",\"extra\":[1,true,null]}";
  check(ProactiveHintAugmentor.parseHintText(escaped).question.equals("你好"),"real parser unicode");
 }
 static void http()throws Exception {
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  final JSONObject[] request={null};final String[] mode={"ok"};final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.createContext("/v1/chat/completions",x->{try{
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=x.getRequestBody().read(b))!=-1)bytes.write(b,0,n);request[0]=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
   check("Bearer test-key".equals(x.getRequestHeaders().getFirst("Authorization")),"model auth");
   if(mode[0].equals("slow")){Thread.sleep(350);}
   int code=mode[0].equals("error")?500:200;
   byte[] body=(mode[0].equals("bad")?"{}":"{\"choices\":[{\"message\":{\"content\":\"real HTTP answer\"}}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
   x.sendResponseHeaders(code,body.length);x.getResponseBody().write(body);
  }catch(Throwable t){if(!mode[0].equals("slow"))failure.set(t);}finally{x.close();}});
  server.start();String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
  try {
   ProactiveHintRegenerator model=new ProactiveHintRegenerator(endpoint,"model","test-key");
   check(model.regenerate("question","cloud",hit(1),2200).equals("real HTTP answer"),"real HTTP model result");
   check(!request[0].getBoolean("stream")&&request[0].getInt("max_tokens")==256&&request[0].getJSONObject("thinking").getString("type").equals("disabled"),"nonstream request");
   String prompt=request[0].getJSONArray("messages").toString();check(prompt.contains("doc")&&prompt.contains("/real")&&prompt.contains("cloud")&&prompt.contains("外部"),"data and real sources in prompt");
   for(String m:new String[]{"error","bad","slow"}){mode[0]=m;long began=System.nanoTime();try{model.regenerate("q","c",hit(1),100);throw new AssertionError("must fail "+m);}catch(Exception expected){}check((System.nanoTime()-began)/1000000<600,"whole I/O deadline");}
   String synth=new ProactiveHintRegenerator(endpoint,"model","").regenerate("q","cloud",hit(1),2200);check(synth.contains("【知识库证据】")&&synth.contains("【云端原回答】cloud")&&synth.contains("/real"),"only absent key synthesis");
   if(failure.get()!=null)throw new AssertionError(failure.get());
  }finally {server.stop(0);((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();}
 }
 static class SourceDouble {
  final List<Hint> delivered=Collections.synchronizedList(new ArrayList<Hint>());
  public void turboioOriginal_onProactiveHint(Hint hint){
   check(Boolean.TRUE.equals(ProactiveHintHookHarness.BYPASS.get()),"BYPASS must be set during vendor callback");
   delivered.add(hint);
  }
 }
 static void hookDispatch()throws Exception {
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  final java.util.concurrent.CountDownLatch mcpEntered=new java.util.concurrent.CountDownLatch(1);
  final java.util.concurrent.CountDownLatch releaseMcp=new java.util.concurrent.CountDownLatch(1);
  final java.util.concurrent.atomic.AtomicReference<Throwable> serverFailure=new java.util.concurrent.atomic.AtomicReference<>();
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.createContext("/mcp",x->{try{
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=x.getRequestBody().read(b))!=-1)bytes.write(b,0,n);
   JSONObject req=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
   String method=req.getString("method"); long id=req.optLong("id",1L);
   if("initialize".equals(method)){
    x.getResponseHeaders().set("Mcp-Session-Id","sess-hook");
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("protocolVersion","2024-11-05")).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }else if("notifications/initialized".equals(method)){
    x.sendResponseHeaders(202,-1);
   }else if("tools/list".equals(method)){
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("tools",new JSONArray().put(new JSONObject().put("name","cortrix_query")))).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }else if("tools/call".equals(method)){
    mcpEntered.countDown();
    releaseMcp.await(5,java.util.concurrent.TimeUnit.SECONDS);
    JSONArray docs=new JSONArray().put(new JSONObject().put("namespace","kb").put("doc_id","doc-hook").put("source_path","specs/hook.md").put("score",0.98).put("evidence",new JSONArray().put(new JSONObject().put("kind","parent").put("text","HOOK_EVIDENCE_FACT"))));
    JSONObject envelope=new JSONObject().put("data",new JSONObject().put("results",docs));
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("structuredContent",envelope)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }
  }catch(Throwable t){serverFailure.set(t);}finally{x.close();}});
  server.createContext("/v1/chat/completions",x->{try{
   byte[] out="{\"choices\":[{\"message\":{\"content\":\"regenerated via hook\"}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
  }catch(Throwable t){serverFailure.set(t);}finally{x.close();}});
  server.start();
  String base="http://127.0.0.1:"+server.getAddress().getPort();
  try {
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.endpoint=base+"/v1";
   ProactiveHintHookHarness.model="deepseek-flash";
   ProactiveHintHookHarness.secretKey="hook-secret";
   ProactiveHintHookHarness.mcpConfig=configWithUrl(true,base+"/mcp",5L);

   // (a) Pre-display hold: source.onProactiveHint is NOT called synchronously before async retrieval/regeneration completes
   SourceDouble sourceA=new SourceDouble();
   Hint hintA=new Hint("{\"question\":\"hook question?\",\"answer\":\"cloud original\",\"keep\":99}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceA,hintA);
   check(mcpEntered.await(5,java.util.concurrent.TimeUnit.SECONDS),"async MCP retrieval must start");
   check(sourceA.delivered.isEmpty(),"pre-display hold: onProactiveHint must NOT be called before async retrieval/regeneration completes");
   check(new JSONObject(hintA.text).getString("answer").equals("cloud original"),"hint text must not be mutated before main looper delivery");

   // (b) Single regenerated delivery on the main looper with diagnostic updated
   releaseMcp.countDown();
   long deadline=System.currentTimeMillis()+5000;
   while(ProactiveHintHookHarness.MAIN.queue.isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(10);
   check(!ProactiveHintHookHarness.MAIN.queue.isEmpty(),"completion must post delivery to MAIN looper");
   check(sourceA.delivered.isEmpty(),"must remain undelivered until MAIN looper runs posted callback");
   ProactiveHintHookHarness.MAIN.drain();
   check(sourceA.delivered.size()==1&&sourceA.delivered.get(0)==hintA,"single regenerated delivery on main looper");
   JSONObject deliveredJson=new JSONObject(hintA.text);
   check("regenerated via hook".equals(deliveredJson.getString("answer"))&&deliveredJson.getInt("keep")==99,"regenerated answer and preserved fields");
   check("提醒知识库回答已准备".equals(ProactiveHintHookHarness.diagnostic),"diagnostic updated on main delivery: "+ProactiveHintHookHarness.diagnostic);

   // (c.1) Single fallback delivery of original hint when McpConnectionStore.snapshot(app) throws
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.snapshotFailure=new IllegalStateException("mcp_credentials_unavailable");
   SourceDouble sourceB=new SourceDouble();
   Hint hintB=new Hint("{\"question\":\"q2\",\"answer\":\"cloud fallback 1\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceB,hintB);
   check(sourceB.delivered.size()==1&&sourceB.delivered.get(0)==hintB,"single fallback delivery when snapshot throws");
   check("cloud fallback 1".equals(new JSONObject(hintB.text).getString("answer")),"original hint preserved when snapshot throws");
   check("知识库配置不可用，已放行云端原回答".equals(ProactiveHintHookHarness.diagnostic),"snapshot failure diagnostic: "+ProactiveHintHookHarness.diagnostic);

   // (c.2) Single fallback delivery of original hint when SecretStore.get(app) throws
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.mcpConfig=configWithUrl(true,base+"/mcp",6L);
   ProactiveHintHookHarness.secretFailure=new IllegalStateException("keystore_unavailable");
   SourceDouble sourceC=new SourceDouble();
   Hint hintC=new Hint("{\"question\":\"q3\",\"answer\":\"cloud fallback 2\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceC,hintC);
   check(sourceC.delivered.size()==1&&sourceC.delivered.get(0)==hintC,"single fallback delivery when SecretStore.get throws");
   check("cloud fallback 2".equals(new JSONObject(hintC.text).getString("answer")),"original hint preserved when SecretStore.get throws");
   check("模型密钥不可用，已放行云端原回答".equals(ProactiveHintHookHarness.diagnostic),"secret failure diagnostic: "+ProactiveHintHookHarness.diagnostic);

   if(serverFailure.get()!=null)throw new AssertionError(serverFailure.get());
  } finally {
   ProactiveHintHookHarness.NETWORK.shutdownNow();
   server.stop(0);
   ((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();
  }
 }
 static McpConnectionStore.Snapshot configWithUrl(boolean enabled,String url,long revision)throws Exception {
  java.lang.reflect.Constructor<McpConnectionStore.Snapshot> c=McpConnectionStore.Snapshot.class.getDeclaredConstructor(boolean.class,String.class,String.class,long.class);
  c.setAccessible(true);return c.newInstance(enabled,url,"",revision);
 }
}
