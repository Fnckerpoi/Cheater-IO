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
  ProactiveHintAugmentor a=new ProactiveHintAugmentor(work::add,this,()->now,this,(q,c,b)->{check(b<=15000,"retrieval budget");retrieves++;return retrievalStatus==KnowledgeRetrieval.Status.HIT?hit(c.revision):KnowledgeRetrieval.result(retrievalStatus,c.revision);});
  public long generation(){return gen;} public long sequence(){return seq;} public long revision(){return rev;}
  public void execute(Runnable r){main.add(r);} public ProactiveHintAugmentor.Cancellation schedule(Runnable r,long ms){check(ms==42000,"watchdog deadline");timers.add(r);return ()->timers.remove(r);}
  ProactiveHintAugmentor.Regenerator model=(q,cloud,r,b)->{check(b<=25000,"model budget");models++;return "regenerated";};
  void start(Hint h)throws Exception {a.interceptAndRegenerateAsync(h,config(rev),gen,seq,model,(v,s)->{check(v==h,"same hint object");check(h.metadata==42,"object metadata");deliveries++;status=s;});}
  void finish(){work.remove().run();while(!main.isEmpty())main.remove().run();}
 }
 public static void main(String[] args)throws Exception {
  slowModelWithinBudget(); newestQueuedHintWins(); supersededModelIsCancelled(); orderedAndCache(); fallbacks(); namespaceFeedback(); watchdog(); stale(); mutable(); http(); answerBudget(); hookDispatch(); endToEnd();
  System.out.println("HintCoordinatorTest: ALL PASS (coordinator + real HTTP + hook dispatch)");
 }
 static void newestQueuedHintWins()throws Exception {
  java.util.concurrent.ThreadPoolExecutor hints=ProactiveHintAugmentor.newHintExecutor();
  java.util.concurrent.CountDownLatch active=new java.util.concurrent.CountDownLatch(2), release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.CountDownLatch latest=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.atomic.AtomicInteger obsolete=new java.util.concurrent.atomic.AtomicInteger();
  Runnable busy=()->{active.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}};
  try {
   hints.execute(busy);hints.execute(busy);
   check(active.await(2,java.util.concurrent.TimeUnit.SECONDS),"both model workers occupied");
   hints.execute(obsolete::incrementAndGet);
   hints.execute(latest::countDown);
   release.countDown();
   check(latest.await(2,java.util.concurrent.TimeUnit.SECONDS)&&obsolete.get()==0,"queued older hint removed");
  } finally {release.countDown();hints.shutdownNow();}
 }
 static void supersededModelIsCancelled()throws Exception {
  Fixture f=new Fixture();
  java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newSingleThreadExecutor();
  java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1), interrupted=new java.util.concurrent.CountDownLatch(1);
  f.a=new ProactiveHintAugmentor(workers,f,()->f.now,f,(q,c,b)->hit(c.revision));
  f.model=(q,c,r,b)->{
   if(q.equals("old")) {
    started.countDown();
    try { new java.util.concurrent.CountDownLatch(1).await(); }
    catch(InterruptedException e) { interrupted.countDown();throw e; }
   }
   return "new answer";
  };
  try {
   f.start(new Hint("old"));
   check(started.await(2,java.util.concurrent.TimeUnit.SECONDS),"old model started");
   f.seq++;f.start(new Hint("new"));
   check(interrupted.await(2,java.util.concurrent.TimeUnit.SECONDS),"superseded work interrupted");
  } finally { workers.shutdownNow();workers.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS); }
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
  for(KnowledgeRetrieval.Status s:KnowledgeRetrieval.Status.values())if(s!=KnowledgeRetrieval.Status.HIT && s!=KnowledgeRetrieval.Status.NO_MATCH){Fixture f=new Fixture();f.retrievalStatus=s;Hint h=new Hint("plain");f.start(h);f.finish();check(h.text.equals("plain")&&f.models==0&&f.deliveries==1,"no hit exact fallback "+s);}
  Fixture f=new Fixture();f.model=(q,c,r,b)->{throw new java.io.IOException("model failure");};Hint h=new Hint("q");f.start(h);f.finish();check(h.text.contains("知识库摘录")&&h.text.contains("real fact")&&f.deliveries==1,"model failure retains retrieved evidence");
  Fixture empty=new Fixture();empty.model=(q,c,r,b)->" ";Hint eh=new Hint("q");empty.start(eh);empty.finish();check(eh.text.contains("知识库摘录")&&empty.deliveries==1,"empty regeneration retains retrieved evidence");
 }
 static void namespaceFeedback()throws Exception {
  String failure="knowledge (CX_ERR_INDEX_CORRUPT)";
  for(KnowledgeRetrieval.Status status:Arrays.asList(KnowledgeRetrieval.Status.NO_MATCH,KnowledgeRetrieval.Status.TOOL_ERROR,KnowledgeRetrieval.Status.HIT)) {
   Fixture f=new Fixture();
   boolean partial=status==KnowledgeRetrieval.Status.HIT, empty=status==KnowledgeRetrieval.Status.NO_MATCH;
   f.a=new ProactiveHintAugmentor(f.work::add,f,()->f.now,f,(q,c,b)->{
    f.retrieves++;
    return new KnowledgeRetrieval.Result(status,partial?hit(c.revision).evidences:Collections.emptyList(),c.revision,
        empty?1:partial?0.5:0,empty?Collections.emptyList():Collections.singletonList(failure),45);
   });
   for(int i=0;i<2;i++) {
    Hint h=new Hint("{\"question\":\"q\",\"answer\":\"vendor\"}");
    f.start(h);f.finish();f.seq++;
    String shown=new JSONObject(h.text).getString("answer");
    check(shown.contains(empty?"未找到相关内容":partial?"regenerated":"知识库检索失败"),"visible retrieval outcome: "+shown);
    check(empty||shown.contains(failure),"failed namespace and code visible");
   }
   check(f.retrieves==2 && f.models==(partial?2:0),"retry incomplete or empty retrieval on next hint");
  }
 }
 static void slowModelWithinBudget()throws Exception {
  Fixture f=new Fixture();
  Hint h=new Hint("slow question");
  f.a=new ProactiveHintAugmentor(f.work::add,f,()->f.now,f,(q,c,b)->{f.now+=1000;return hit(c.revision);});
  f.model=(q,c,r,b)->{f.now+=16000;return "slow answer";};
  f.start(h);f.finish();
  check(f.deliveries==1&&"slow answer".equals(h.text),"complete model answer at 17s must be delivered");
  Fixture queued=new Fixture();
  queued.a=new ProactiveHintAugmentor(queued.work::add,queued,()->queued.now,queued,(q,c,b)->{queued.now+=1000;return hit(c.revision);});
  queued.model=(q,c,r,b)->{check(b==5000,"queue and retrieval consume total budget");return "remaining budget answer";};
  Hint nearDeadline=new Hint("queued");queued.start(nearDeadline);queued.now=36000;queued.finish();
  check(queued.deliveries==1 && "remaining budget answer".equals(nearDeadline.text),"queued hint within watchdog");
 }
 static void watchdog()throws Exception {
  Fixture f=new Fixture();Hint h=new Hint("original");f.start(h);f.now=42000;f.timers.get(0).run();check(f.deliveries==1&&h.text.equals("original"),"watchdog original");f.finish();check(f.deliveries==1&&f.models==0,"late result suppressed");
  Fixture g=new Fixture();g.model=(q,c,r,b)->{g.now=25001;return "late";};Hint late=new Hint("original");g.start(late);g.finish();check(late.text.contains("知识库摘录")&&!late.text.contains("late")&&g.deliveries==1,"late model answer is rejected, evidence is retained");
  Fixture r=new Fixture();r.a=new ProactiveHintAugmentor(r.work::add,r,()->r.now,r,(q,c,b)->{r.now=15001;return hit(1);});Hint rh=new Hint("q");r.start(rh);r.finish();check(r.models==0&&rh.text.equals("q"),"retrieval stage expiration");
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
   int code=mode[0].equals("error")?503:mode[0].equals("auth")?401:200;
   x.getResponseHeaders().set("X-Request-Id","fixture-request-id");
   byte[] body=(mode[0].equals("completion_limit")?"{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\",\"reasoning_content\":\"private-reasoning\"}}],\"usage\":{\"completion_tokens\":2048}}":mode[0].equals("null_content")?"{\"choices\":[{\"message\":{\"content\":null}}]}":mode[0].equals("empty")?"{\"choices\":[{\"message\":{\"content\":\" \"}}]}":mode[0].equals("bad")?"{}":"{\"choices\":[{\"message\":{\"content\":\"real HTTP answer\"}}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
   x.sendResponseHeaders(code,body.length);
   if(mode[0].equals("slow_body")){x.getResponseBody().write(body,0,1);x.getResponseBody().flush();Thread.sleep(350);x.getResponseBody().write(body,1,body.length-1);}
   else x.getResponseBody().write(body);
  }catch(Throwable t){if(!mode[0].startsWith("slow"))failure.set(t);}finally{x.close();}});
  server.start();String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
  try {
   ProactiveHintRegenerator model=new ProactiveHintRegenerator(endpoint,"model","test-key");
   check(model.regenerate("question","cloud",hit(1),2200).equals("real HTTP answer"),"real HTTP model result");
   check(!request[0].getBoolean("stream")&&request[0].getInt("max_tokens")==2048&&request[0].getJSONObject("thinking").getString("type").equals("disabled"),"nonstream request");
   String prompt=request[0].getJSONArray("messages").toString();check(prompt.contains("doc")&&prompt.contains("/real")&&prompt.contains("cloud")&&prompt.contains("外部"),"data and real sources in prompt");
   List<String> events=new ArrayList<>();
   ProactiveHintRegenerator traced=new ProactiveHintRegenerator(endpoint,"model","test-key",events::add);
   Map<String,String> expected=new LinkedHashMap<>();
   expected.put("auth","stage=headers status=http_error http=401");
   expected.put("error","stage=headers status=http_error http=503");
   expected.put("bad","stage=parse status=invalid_response http=200");
   expected.put("completion_limit","stage=parse status=completion_limit http=200");
   expected.put("empty","stage=parse status=empty_completion http=200");
   expected.put("null_content","stage=parse status=empty_completion http=200");
   expected.put("slow","stage=headers status=timeout http=0");
   expected.put("slow_body","stage=body status=timeout http=200");
   for(Map.Entry<String,String> test:expected.entrySet()) {
    mode[0]=test.getKey();events.clear();
    try {traced.regenerate("private-question","cloud",hit(1),mode[0].startsWith("slow")?150:1500);throw new AssertionError("expected model failure "+mode[0]);}
    catch(java.io.IOException | org.json.JSONException expectedFailure) {}
    check(events.size()==1 && events.get(0).contains(test.getValue()),"model phase "+test.getKey()+": "+events);
    if(test.getKey().equals("completion_limit"))check(events.get(0).contains("finish=length answer_chars=7 reasoning_chars=17 completion_tokens=2048")&&!events.get(0).contains("private-reasoning"),"completion diagnostics omit reasoning text");
    check(!events.get(0).contains("private-question")&&!events.get(0).contains("test-key"),"trace omits request content and credential");
   }
   check(events.get(0).contains("request_id=fixture-request-id"),"model request id");
   if(failure.get()!=null)throw new AssertionError(failure.get());
  }finally {server.stop(0);((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();}
 }
 static void answerBudget()throws Exception {
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.createContext("/v1/chat/completions",x->{try{
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=x.getRequestBody().read(b))!=-1)bytes.write(b,0,n);
   JSONObject req=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
   check(req.getJSONArray("messages").getJSONObject(0).getString("content").contains("120"),"display budget in prompt");
   byte[] body=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",longAnswer())))).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
   x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);
  }catch(Throwable t){throw new RuntimeException(t);}finally{x.close();}});
  server.start();String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
  try{
   ProactiveHintRegenerator model=new ProactiveHintRegenerator(endpoint,"model","test-key");
   String answer=model.regenerate("q","c",hit(1),2200);
   check(answer.length()<=120,"answer clipped to display budget: "+answer.length());
   check(answer.indexOf('\n')<0 && answer.indexOf('\r')<0,"no control characters in answer");
  }finally{server.stop(0);((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();}
 }
 static String longAnswer(){StringBuilder sb=new StringBuilder();for(int i=0;i<300;i++)sb.append("很长的回答内容");return sb.append("\n\r").toString();}
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
   String trace=String.join("\n",ProactiveHintHookHarness.chatLines);
   check(trace.contains("hint enter seq=")&&trace.contains("hint parse ")&&trace.contains("question_len=14")&&trace.contains("hint retrieval ")&&trace.contains("status=HIT")&&trace.contains("regenerated=true"),"stage trace logged: "+trace);
    check(trace.contains("hint mcp warm=false")&&trace.contains("call_ms="),"mcp stage trace logged: "+trace);
   check(!trace.contains("hook question")&&!trace.contains("regenerated via hook")&&!trace.contains("cloud original"),"trace omits hint text");

   // (c.1) Single fallback delivery of original hint when McpConnectionStore.snapshot(app) throws
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.snapshotFailure=new IllegalStateException("mcp_credentials_unavailable");
   SourceDouble sourceB=new SourceDouble();
   Hint hintB=new Hint("{\"question\":\"q2\",\"answer\":\"cloud fallback 1\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceB,hintB);
   check(sourceB.delivered.size()==1&&sourceB.delivered.get(0)==hintB,"single fallback delivery when snapshot throws");
   check("cloud fallback 1".equals(new JSONObject(hintB.text).getString("answer")),"original hint preserved when snapshot throws");
   check("知识库配置不可用，已放行云端原回答".equals(ProactiveHintHookHarness.diagnostic),"snapshot failure diagnostic: "+ProactiveHintHookHarness.diagnostic);
   check(ProactiveHintHookHarness.chatLines.contains("hint fallback config_unavailable"),"config fallback logged");

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
 static void endToEnd()throws Exception {
  ProactiveHintHookHarness.NETWORK=java.util.concurrent.Executors.newFixedThreadPool(2);
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  final java.util.concurrent.atomic.AtomicInteger mcpCalls=new java.util.concurrent.atomic.AtomicInteger();
  final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch> enterGate=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch> releaseGate=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicReference<Throwable> serverFailure=new java.util.concurrent.atomic.AtomicReference<>();
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.createContext("/mcp",x->{try{
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=x.getRequestBody().read(b))!=-1)bytes.write(b,0,n);
   JSONObject req=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
   String method=req.getString("method"); long id=req.optLong("id",1L);
   if("initialize".equals(method)){
    x.getResponseHeaders().set("Mcp-Session-Id","sess-e2e");
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("protocolVersion","2024-11-05")).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }else if("notifications/initialized".equals(method)){
    x.sendResponseHeaders(202,-1);
   }else if("tools/list".equals(method)){
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("tools",new JSONArray().put(new JSONObject().put("name","cortrix_query")))).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }else if("tools/call".equals(method)){
    int index=mcpCalls.incrementAndGet();
    JSONObject params=req.getJSONObject("params"), args=params.getJSONObject("arguments");
    check("cortrix_query".equals(params.getString("name"))&&!args.getBoolean("rerank"),"actual tool and rerank");
    check("Ignition 部署".equals(args.getString("query")),"same query in all cases");
    JSONArray ns=args.optJSONArray("namespaces");
    if(index==1) check(!args.has("namespaces"),"omit default namespace on wire");
    if(index==2 || index==3) check(ns!=null && ns.length()==1 && (index==2?"obsidian-knowledge":"knowledge").equals(ns.getString(0)),"explicit namespace on wire");
    java.util.concurrent.CountDownLatch enter=enterGate.get();if(enter!=null)enter.countDown();
    java.util.concurrent.CountDownLatch release=releaseGate.get();if(release!=null)release.await(5,java.util.concurrent.TimeUnit.SECONDS);
    JSONArray docs=new JSONArray().put(new JSONObject().put("namespace","kb").put("doc_id","doc-e2e").put("source_path","specs/e2e.md").put("score",0.98).put("evidence",new JSONArray().put(new JSONObject().put("kind","parent").put("text","E2E_EVIDENCE"))));
    boolean invalid=index==3;
    JSONArray failed=new JSONArray();
    if(invalid) failed.put(new JSONObject().put("namespace","knowledge").put("error_code","CX_ERR_INDEX_CORRUPT"));
    JSONObject coverage=new JSONObject().put("coverage_ratio",invalid?0:1).put("namespaces_failed",failed);
    JSONObject envelope=new JSONObject().put("meta",new JSONObject().put("category","success").put("structured_data",coverage))
        .put("data",new JSONObject().put("results",invalid?new JSONArray():docs));
    byte[] out=new JSONObject().put("jsonrpc","2.0").put("id",id).put("result",new JSONObject().put("structuredContent",envelope)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
   }
  }catch(Throwable t){serverFailure.set(t);}finally{x.close();}});
  server.createContext("/v1/chat/completions",x->{try{
   byte[] out="{\"choices\":[{\"message\":{\"content\":\"E2E_MODEL_ANSWER\"}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   x.sendResponseHeaders(200,out.length);x.getResponseBody().write(out);
  }catch(Throwable t){serverFailure.set(t);}finally{x.close();}});
  server.start();
  String base="http://127.0.0.1:"+server.getAddress().getPort();
  try {
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.endpoint=base+"/v1";
   ProactiveHintHookHarness.model="deepseek-flash";
   ProactiveHintHookHarness.secretKey="e2e-secret";
   ProactiveHintHookHarness.mcpConfig=configWithUrl(true,base+"/mcp",5L);
   java.util.concurrent.CountDownLatch enter1=new java.util.concurrent.CountDownLatch(1);
   java.util.concurrent.CountDownLatch release1=new java.util.concurrent.CountDownLatch(1);
   enterGate.set(enter1);releaseGate.set(release1);
   SourceDouble sourceA=new SourceDouble();
   Hint hintA=new Hint("{\"question\":\"Ignition 部署\",\"answer\":\"cloud first\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceA,hintA);
   check(enter1.await(5,java.util.concurrent.TimeUnit.SECONDS),"async MCP retrieval must start");
   check(sourceA.delivered.isEmpty(),"pre-display hold before retrieval");
   enterGate.set(null);releaseGate.set(null);release1.countDown();
   waitForDelivery();
   check(sourceA.delivered.size()==1&&sourceA.delivered.get(0)==hintA,"single hit delivery");
   check("E2E_MODEL_ANSWER".equals(new JSONObject(hintA.text).getString("answer")),"model answer delivered");
   check("提醒知识库回答已准备".equals(ProactiveHintHookHarness.diagnostic),"hit diagnostic");
   java.util.concurrent.CountDownLatch enter2=new java.util.concurrent.CountDownLatch(1);
   java.util.concurrent.CountDownLatch release2=new java.util.concurrent.CountDownLatch(1);
   enterGate.set(enter2);releaseGate.set(release2);
   ProactiveHintHookHarness.mcpConfig=namespaceConfig(base+"/mcp",9L,"obsidian-knowledge");
   SourceDouble sourceB=new SourceDouble();
   Hint hintB=new Hint("{\"question\":\"Ignition 部署\",\"answer\":\"cloud second\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceB,hintB);
   check(enter2.await(5,java.util.concurrent.TimeUnit.SECONDS),"revision change must trigger retrieval");
   check(mcpCalls.get()==2,"revision change must not reuse cache: "+mcpCalls.get());
   enterGate.set(null);releaseGate.set(null);release2.countDown();
   waitForDelivery();
   check("E2E_MODEL_ANSWER".equals(new JSONObject(hintB.text).getString("answer")),"revision change re-retrieved");
   check("提醒知识库回答已准备".equals(ProactiveHintHookHarness.diagnostic),"revision retrieval diagnostic");
   ProactiveHintHookHarness.mcpConfig=namespaceConfig(base+"/mcp",10L,"knowledge");
   Hint invalid=new Hint("{\"question\":\"Ignition 部署\",\"answer\":\"vendor\"}");
   SourceDouble negative=new SourceDouble();ProactiveHintHookHarness.dispatchProactiveHint(negative,invalid);waitForDelivery();
   String notice=new JSONObject(invalid.text).getString("answer");
   check(negative.delivered.size()==1 && notice.contains("知识库检索失败") && notice.contains("knowledge") && notice.contains("CX_ERR_INDEX_CORRUPT"),"negative control delivered to host");
   ProactiveHintHookHarness.reset();
   ProactiveHintHookHarness.mcpConfig=configWithUrl(false,base+"/mcp",11L);
   SourceDouble sourceC=new SourceDouble();
   Hint hintC=new Hint("{\"question\":\"miss question?\",\"answer\":\"cloud miss\"}");
   ProactiveHintHookHarness.dispatchProactiveHint(sourceC,hintC);
   check(sourceC.delivered.size()==1&&sourceC.delivered.get(0)==hintC,"disabled config delivers once (synchronous)");
   check("cloud miss".equals(new JSONObject(hintC.text).getString("answer")),"disabled config passes cloud answer through");
   check("提醒知识库未启用或问题为空，已放行云端原回答".equals(ProactiveHintHookHarness.diagnostic),"disabled diagnostic");
   if(serverFailure.get()!=null)throw new AssertionError(serverFailure.get());
  } finally {
   ProactiveHintHookHarness.NETWORK.shutdownNow();
   server.stop(0);
   ((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();
  }
 }
 static void waitForDelivery()throws Exception {
  long deadline=System.currentTimeMillis()+5000;
  while(ProactiveHintHookHarness.MAIN.queue.isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(10);
  check(!ProactiveHintHookHarness.MAIN.queue.isEmpty(),"delivery must post to MAIN looper");
  ProactiveHintHookHarness.MAIN.drain();
 }
 static McpConnectionStore.Snapshot namespaceConfig(String url,long revision,String namespaces)throws Exception {
  java.lang.reflect.Constructor<McpConnectionStore.Snapshot> c=McpConnectionStore.Snapshot.class.getDeclaredConstructor(boolean.class,String.class,String.class,long.class,String.class);
  c.setAccessible(true);return c.newInstance(true,url,"",revision,namespaces);
 }
 static McpConnectionStore.Snapshot configWithUrl(boolean enabled,String url,long revision)throws Exception {
  java.lang.reflect.Constructor<McpConnectionStore.Snapshot> c=McpConnectionStore.Snapshot.class.getDeclaredConstructor(boolean.class,String.class,String.class,long.class);
  c.setAccessible(true);return c.newInstance(enabled,url,"",revision);
 }
}
