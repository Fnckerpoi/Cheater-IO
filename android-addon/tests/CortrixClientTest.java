package com.turboio.addon;

import com.sun.net.httpserver.*;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class CortrixClientTest {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static McpConnectionStore.Snapshot snapshot(boolean enabled, String endpoint) throws Exception {
        java.lang.reflect.Constructor<McpConnectionStore.Snapshot> c = McpConnectionStore.Snapshot.class.getDeclaredConstructor(boolean.class,String.class,String.class,long.class);
        c.setAccessible(true); return c.newInstance(enabled,endpoint,"secret",17L);
    }
    static String docs() { return "[{\"namespace\":\"kb\",\"doc_id\":\"d1\",\"source_path\":\"manual.md\",\"metadata\":{\"title\":\"说明\"},\"score\":0.9,\"evidence\":[{\"kind\":\"parent\",\"text\":\"中文\\n引号\\\"与\\u4e2d文\"}]}]"; }
    static final class Server implements AutoCloseable {
        final HttpServer server; final ExecutorService pool=Executors.newCachedThreadPool();
        final List<String> methods = Collections.synchronizedList(new ArrayList<String>());
        volatile Throwable failure; String mode; final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        Server(String mode) throws Exception {
            CortrixClient.resetSessions();
            this.mode=mode; server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(pool);
            server.createContext("/mcp", e -> { try { handle(e); } catch(Throwable t) { failure=t; e.close(); } }); server.start();
        }
        String url() { return "http://127.0.0.1:"+server.getAddress().getPort()+"/mcp"; }
        volatile JSONObject wireParams;
        void handle(HttpExchange e) throws Exception {
            JSONObject req=new JSONObject(new String(read(e.getRequestBody()),StandardCharsets.UTF_8));
            String method=req.getString("method"); methods.add(method);
            if(method.equals("tools/call"))wireParams=req.getJSONObject("params");
            if(method.equals("tools/list"))e.getResponseHeaders().set("X-Trace-Id","list-trace");
            check("Bearer secret".equals(e.getRequestHeaders().getFirst("Authorization")),"bearer");
            if (!method.equals("initialize")) {
                check("session-test".equals(e.getRequestHeaders().getFirst("Mcp-Session-Id")),"session");
                check("2024-11-05".equals(e.getRequestHeaders().getFirst("MCP-Protocol-Version")),"version");
            }
            if(mode.equals("401")||mode.equals("403")||mode.equals("500")){e.sendResponseHeaders(Integer.parseInt(mode),-1);e.close();return;}
            if(mode.equals("slow")) Thread.sleep(100);
            if(mode.equals("headers")) Thread.sleep(800);
            if(method.equals("notifications/initialized")){check(!req.has("id"),"notification id");e.sendResponseHeaders(202,-1);e.close();return;}
            Object result;
            if(method.equals("initialize")) { e.getResponseHeaders().set("Mcp-Session-Id","session-test"); result=new JSONObject().put("protocolVersion",mode.equals("version")?"bad":"2024-11-05"); }
            else if(method.equals("tools/list")) result=new JSONObject().put("tools",new JSONArray(mode.equals("missing")?"[]":mode.startsWith("catalog")?"[{\"name\":\"cortrix_list_namespaces\"}]":"[{\"name\":\"cortrix_query\"}]"));
            else if(mode.startsWith("catalog")) {
                check("cortrix_list_namespaces".equals(wireParams.getString("name")),"catalog must not query docs");
                int offset=wireParams.getJSONObject("arguments").getInt("offset");
                String name=offset==0?"obsidian-experience":"obsidian-knowledge";
                JSONObject data=new JSONObject().put("namespaces",new JSONArray().put(new JSONObject().put("namespace",name).put("name","display title"))).put("total",2);
                JSONObject payload=new JSONObject().put("data",data).put("meta",new JSONObject().put("category","success"));
                result=offset==0?new JSONObject().put("structuredContent",payload):new JSONObject().put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",payload.toString())));
            }
            else {
                if(mode.equals("expire")&&calls.incrementAndGet()==2){e.sendResponseHeaders(404,-1);e.close();return;}
                check(Arrays.asList("问题","private-query").contains(req.getJSONObject("params").getJSONObject("arguments").getString("query")),"query");
                check(!req.getJSONObject("params").getJSONObject("arguments").getBoolean("rerank"),"rerank disabled for time budget");
                if(mode.equals("namespaces")) {
                    JSONArray ns = req.getJSONObject("params").getJSONObject("arguments").getJSONArray("namespaces");
                    check(ns.length() == 2 && ns.getString(0).equals("ns1") && ns.getString(1).equals("ns2"), "namespaces in args");
                }
                Object data=mode.equals("empty")?new JSONArray():new JSONArray(docs());
                if(mode.equals("nosource")) data=new JSONArray("[{\"evidence\":[{\"text\":\"事实\"}]}]");
                if(mode.equals("structured_partial") || mode.equals("structured_partial_obj")) {
                    data=new JSONArray("[{\"namespace\":\"kb\",\"doc_id\":\"d1\",\"source_path\":\"manual.md\",\"metadata\":{\"title\":\"说明\",\"updated\":\"2026-11-04\"},\"score\":0.9,\"evidence\":[{\"kind\":\"parent\",\"text\":\"中文证据\"}]}]");
                }
                if(mode.equals("structured_total_failure") || mode.equals("structured_total_failure_text") || mode.equals("empty_zero_cov")) {
                    data=new JSONArray();
                }
                if(mode.equals("doc_timestamp")) {
                    data=new JSONArray("[{\"namespace\":\"kb\",\"doc_id\":\"d2\",\"updated_at\":\"2026-11-02T10:00:00Z\",\"source_path\":\"doc2.md\",\"metadata\":{\"title\":\"Doc2\"},\"score\":0.8,\"evidence\":[{\"kind\":\"parent\",\"text\":\"文档2事实\"}]}]");
                }
                if(mode.equals("huge")) {
                    StringBuilder bigText = new StringBuilder();
                    for(int k=0; k<6000; k++) bigText.append("A");
                    JSONArray hugeItems = new JSONArray();
                    for(int k=0; k<5; k++) {
                        hugeItems.put(new JSONObject().put("namespace","kb").put("doc_id","d"+k).put("source_path","p"+k).put("metadata",new JSONObject().put("updated","2026-11-01")).put("score",0.9).put("evidence",new JSONArray().put(new JSONObject().put("kind","chunk").put("text",bigText.toString()))));
                    }
                    data=hugeItems;
                }
                JSONArray failedArr = new JSONArray();
                if(mode.equals("structured_partial")) failedArr.put("failed_ns");
                if(mode.equals("structured_total_failure") || mode.equals("structured_total_failure_text") || mode.equals("structured_partial_obj")) {
                    failedArr.put(new JSONObject().put("namespace","knowledge").put("error_code","CX_ERR_INDEX_CORRUPT"));
                }
                double cov = mode.equals("structured_partial") ? 0.75 : mode.equals("structured_partial_obj") ? 0.5 : (mode.equals("structured_total_failure") || mode.equals("structured_total_failure_text") || mode.equals("empty_zero_cov")) ? 0 : 1;
                JSONObject structuredData = new JSONObject().put("coverage_ratio", cov).put("namespaces_failed", failedArr);
                JSONObject metaObj = new JSONObject().put("category","success").put("retryable",false).put("structured_data",structuredData);
                if(mode.equals("structured_total_failure") || mode.equals("structured_total_failure_text")) metaObj.put("trace_id","tr-total-fail");
                if(mode.equals("structured_total_failure"))structuredData.put("trace_id","tr-structured");
                if(mode.equals("structured_partial_obj")) metaObj.put("trace_id","tr-partial");
                JSONObject envelope=new JSONObject().put("data",new JSONObject().put("results",data).put("meta",new JSONObject())).put("meta",metaObj);
                boolean useStructured=mode.equals("structured")||mode.equals("structured_partial")||mode.equals("structured_total_failure")||mode.equals("structured_partial_obj")||mode.equals("empty_zero_cov");
                result=useStructured?new JSONObject().put("structuredContent",envelope):new JSONObject().put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",mode.equals("bare")?data.toString():envelope.toString())));
                if(mode.equals("header_trace")) e.getResponseHeaders().set("X-Trace-Id","tr-http-hdr");
                if(mode.equals("tool")) ((JSONObject)result).put("isError",true);
            }
            JSONObject response=new JSONObject().put("jsonrpc","2.0").put("id",mode.equals("id")?99:req.getLong("id")).put("result",result);
            if(mode.equals("rpc")) response.remove("result");
            if(mode.equals("rpc")) response.put("error",new JSONObject().put("code",-1).put("message","error"));
            String body=mode.equals("malformed")?"oops":response.toString();
            if(mode.equals("drip")) {
                e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,0);
                for(byte b:body.getBytes(StandardCharsets.UTF_8)){e.getResponseBody().write(b);e.getResponseBody().flush();Thread.sleep(30);}
                e.close();return;
            }
            if(mode.equals("sse") && method.equals("tools/call")) {
                e.getResponseHeaders().set("Content-Type","text/event-stream");e.sendResponseHeaders(200,0);
                e.getResponseBody().write((": heartbeat\n\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notice\"}\n\ndata: {\"jsonrpc\":\"2.0\",\"id\":88,\"result\":{}}\n\ndata: "+body+"\n\n").getBytes(StandardCharsets.UTF_8));e.getResponseBody().flush();Thread.sleep(1500);
            } else {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}
            e.close();
        }
        public void close(){server.stop(0);pool.shutdownNow();}
    }
    static byte[] read(java.io.InputStream in)throws Exception { java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[1024];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toByteArray(); }
    static KnowledgeRetrieval.Result run(String mode,long budget)throws Exception {try(Server s=new Server(mode)){KnowledgeRetrieval.Result r=KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),budget);if(s.failure!=null)throw new AssertionError(s.failure);return r;}}
    static JSONObject diagnostic(List<String> logs)throws Exception {
        for(String line:logs)if(line.startsWith("hint mcp result="))return new JSONObject(line.substring("hint mcp result=".length()));
        throw new AssertionError("missing diagnostic: "+logs);
    }
    public static void main(String[] args)throws Exception {
        try(Server s=new Server("catalog")) {
            CortrixClient.NamespaceResult r=new CortrixClient(s.url(),"secret",0,0).listNamespaces(5000);
            check(r.status==KnowledgeRetrieval.Status.HIT,"catalog succeeds");
            check(r.names.equals(Arrays.asList("obsidian-experience","obsidian-knowledge")),"catalog uses namespace ids from both pages");
            check(s.methods.equals(Arrays.asList("initialize","notifications/initialized","tools/list","tools/call","tools/call")),"catalog uses MCP lifecycle");
            if(s.failure!=null)throw new AssertionError(s.failure);
        }
        try(Server s=new Server("normal")) {
            CortrixClient.NamespaceResult r=new CortrixClient(s.url(),"secret",0,0).listNamespaces(5000);
            check(r.status==KnowledgeRetrieval.Status.TOOL_ERROR && r.names.isEmpty(),"missing catalog tool is reported");
        }
        check(CortrixClient.extractEvidences(new JSONObject().put("result",new JSONObject().put("structuredContent",new JSONObject().put("data",new JSONObject().put("results",new JSONArray(docs()))))).toString()).size()==1,"structured GEN results parsing");
        for(String mode:new String[]{"normal","bare","structured","sse"}) try(Server s=new Server(mode)){
            long start=System.nanoTime();KnowledgeRetrieval.Result r=KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);
            check(r.isHit(),mode+" HIT: "+r.status);check(r.revision==17,"revision");check(r.evidences.get(0).text.equals("中文\n引号\"与中文"),"unicode/escaping");check(r.evidences.get(0).sourcePath.equals("manual.md"),"source");check(r.evidences.get(0).namespace.equals("kb"),"namespace");
            check(s.methods.equals(Arrays.asList("initialize","notifications/initialized","tools/list","tools/call")),"sequence "+s.methods);check((System.nanoTime()-start)/1000000<1000,"SSE must return before EOF");if(s.failure!=null)throw new AssertionError(s.failure);
        }
        String[][] errors={{"401","AUTH_ERROR"},{"403","AUTH_ERROR"},{"500","TRANSPORT_ERROR"},{"slow","TIMEOUT"},{"id","PROTOCOL_ERROR"},{"version","PROTOCOL_ERROR"},{"missing","PROTOCOL_ERROR"},{"malformed","PROTOCOL_ERROR"},{"rpc","TOOL_ERROR"},{"tool","TOOL_ERROR"},{"empty","NO_MATCH"}};
        for(String[] pair:errors){long start=System.nanoTime();KnowledgeRetrieval.Result r=run(pair[0],pair[0].equals("slow")?240:1000);check(r.status.name().equals(pair[1]),pair[0]+": "+r.status);if(pair[0].equals("slow"))check((System.nanoTime()-start)/1000000<600,"total budget");}
        for(String mode:new String[]{"drip","headers"}) {
            long start=System.nanoTime();KnowledgeRetrieval.Result r=run(mode,180);
            check(r.status==KnowledgeRetrieval.Status.TIMEOUT,mode+" deadline status");
            check((System.nanoTime()-start)/1000000<500,mode+" total monotonic budget");
        }
        KnowledgeRetrieval.Result noSource=run("nosource",1000);check(noSource.isHit(),"no source still evidence");check(noSource.evidences.get(0).sourcePath.isEmpty()&&noSource.evidences.get(0).docId.isEmpty(),"no invented source");check(!KnowledgeRetrieval.formatChatContext(noSource).contains("manual.md"),"format source");
        check(KnowledgeRetrieval.retrieve("问题",snapshot(false,""),100).status.name().equals("DISABLED"),"disabled");check(KnowledgeRetrieval.retrieve("问题",snapshot(true,""),100).status.name().equals("UNCONFIGURED"),"unconfigured");
        check(KnowledgeRetrieval.probe("","secret",100).status.name().equals("UNCONFIGURED"),"probe unconfigured");
        try(Server s=new Server("normal")) {
            check(KnowledgeRetrieval.probe(s.url(),"secret",1000).status==KnowledgeRetrieval.Status.NO_MATCH,"probe succeeds");
            check(s.methods.equals(Arrays.asList("initialize","notifications/initialized","tools/list")),"probe never queries");
        }
        // Warm probe: a cached session is reused and only tool discovery is re-verified.
        try(Server s=new Server("normal")) {
            check(KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000).isHit(),"warm probe setup");
            s.methods.clear();
            check(KnowledgeRetrieval.probe(s.url(),"secret",1000).status==KnowledgeRetrieval.Status.NO_MATCH,"warm probe succeeds");
            check(s.methods.equals(Arrays.asList("tools/list")),"warm probe reuses session: "+s.methods);
        }
        check(KnowledgeRetrieval.retrieve("问题",snapshot(true,"http://127.0.0.1:1/mcp"),500).status==KnowledgeRetrieval.Status.TRANSPORT_ERROR,"connection failure");

        // Test structured_partial: coverage_ratio, namespaces_failed, and updated timestamp
        try(Server s=new Server("structured_partial")) {
            KnowledgeRetrieval.Result r = KnowledgeRetrieval.retrieve("问题", snapshot(true, s.url()), 1000);
            check(r.isHit(), "structured_partial HIT");
            check(Math.abs(r.coverageRatio - 0.75) < 0.0001, "coverageRatio 0.75: " + r.coverageRatio);
            check(r.namespacesFailed.size() == 1 && r.namespacesFailed.get(0).equals("failed_ns"), "namespacesFailed: " + r.namespacesFailed);
            check(r.elapsedMs >= 0, "elapsedMs >= 0: " + r.elapsedMs);
            check(r.evidences.get(0).sourceTimes.equals("2026-11-04"), "sourceTimes extracted: " + r.evidences.get(0).sourceTimes);
            check(!r.truncated, "truncated should be false");
        }

        for(String mode:new String[]{"structured_total_failure","structured_total_failure_text"}) {
            KnowledgeRetrieval.Result r=run(mode,1000);
            check(r.status==KnowledgeRetrieval.Status.TOOL_ERROR,mode+" must report tool error: "+r.status);
            check(r.coverageRatio==0 && r.namespacesFailed.equals(Arrays.asList("knowledge:CX_ERR_INDEX_CORRUPT")),mode+" failure details: "+r.namespacesFailed);
        }
        KnowledgeRetrieval.Result partial=run("structured_partial_obj",1000);
        check(partial.isHit() && partial.evidences.size()==1 && partial.coverageRatio==0.5,"partial failure retains hit");
        check(partial.namespacesFailed.equals(Arrays.asList("knowledge:CX_ERR_INDEX_CORRUPT")),"partial failure details");
        check(run("empty_zero_cov",1000).status==KnowledgeRetrieval.Status.NO_MATCH,"empty without failures is no match");
        try(Server s=new Server("structured_total_failure")) {
            List<String> logs=Collections.synchronizedList(new ArrayList<String>());
            CortrixClient.trace=logs::add;
            try {
                KnowledgeRetrieval.retrieve("private-query",Arrays.asList("  knowledge "," "),snapshot(true,s.url()+"?token=private-secret"),1000);
                String log=String.join("\n",logs);
                JSONObject diag=diagnostic(logs);
                check(diag.getBoolean("query_sent") && diag.getString("name").equals(s.wireParams.getString("name")),"wire tool name");
                check(diag.getJSONArray("namespaces").toString().equals(s.wireParams.getJSONObject("arguments").getJSONArray("namespaces").toString()),"wire namespaces");
                check(log.contains("tr-structured") && log.contains("CX_ERR_INDEX_CORRUPT") && log.contains("knowledge"),"diagnostic failure details: "+log);
                check(log.contains("http_code") && log.contains("elapsed_ms") && log.contains("row_count") && log.contains("coverage_ratio"),"diagnostic fields: "+log);
                check(!log.contains("private-query") && !log.contains("private-secret"),"diagnostic excludes secrets and query");
            } finally { CortrixClient.trace=ev->{}; }
        }
        try(Server s=new Server("header_trace")) {
            List<String> logs=Collections.synchronizedList(new ArrayList<String>());
            CortrixClient.trace=logs::add;
            try { KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);check(String.join("\n",logs).contains("tr-http-hdr"),"HTTP trace ID logged");check(!diagnostic(logs).has("namespaces")&&!s.wireParams.getJSONObject("arguments").has("namespaces"),"omitted wire namespaces"); }
            finally { CortrixClient.trace=ev->{}; }
        }

        try(Server s=new Server("401")) {
            List<String> logs=Collections.synchronizedList(new ArrayList<String>());
            CortrixClient.trace=logs::add;
            try {
                check(KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000).status==KnowledgeRetrieval.Status.AUTH_ERROR,"auth failure status");
                JSONObject diag=diagnostic(logs);
                check(!diag.getBoolean("query_sent") && diag.isNull("http_code") && !diag.has("name"),"handshake is not query");
                check(diag.getString("method").equals("initialize") && diag.getInt("stage_http_code")==401,"handshake HTTP diagnostic");
            } finally { CortrixClient.trace=ev->{}; }
        }

        for(String mode:new String[]{"missing","structured_total_failure_text","bare"})try(Server s=new Server(mode)){
            List<String> logs=Collections.synchronizedList(new ArrayList<String>());CortrixClient.trace=logs::add;
            try {
                KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);JSONObject diag=diagnostic(logs);
                if(mode.equals("missing")) check(!diag.getBoolean("query_sent") && diag.isNull("http_code") && diag.isNull("trace_id") && s.wireParams==null,"discovery failure is not query");
                if(mode.equals("structured_total_failure_text"))check("tr-total-fail".equals(diag.getString("trace_id")),"legacy envelope trace");
                if(mode.equals("bare"))check(diag.isNull("trace_id"),"discovery trace must not carry into query");
            }finally{CortrixClient.trace=ev->{};}
        }

        // Test doc-level timestamp
        try(Server s=new Server("doc_timestamp")) {
            KnowledgeRetrieval.Result r = KnowledgeRetrieval.retrieve("问题", snapshot(true, s.url()), 1000);
            check(r.isHit(), "doc_timestamp HIT");
            check(r.evidences.get(0).sourceTimes.equals("2026-11-02T10:00:00Z"), "doc-level updated_at: " + r.evidences.get(0).sourceTimes);
        }

        // Test missing timestamps and missing structured_data (bare mode)
        try(Server s=new Server("bare")) {
            KnowledgeRetrieval.Result r = KnowledgeRetrieval.retrieve("问题", snapshot(true, s.url()), 1000);
            check(r.isHit(), "bare HIT");
            check(r.coverageRatio == 1.0, "default coverageRatio 1.0: " + r.coverageRatio);
            check(r.namespacesFailed.isEmpty(), "empty namespacesFailed");
            check(r.evidences.get(0).sourceTimes.equals("unknown"), "missing timestamp defaults to unknown");
            check(!r.truncated, "bare not truncated");
        }

        // Test huge response truncation (>20000 chars total)
        try(Server s=new Server("huge")) {
            KnowledgeRetrieval.Result r = KnowledgeRetrieval.retrieve("问题", snapshot(true, s.url()), 1000);
            check(r.isHit(), "huge response still HIT");
            check(r.truncated, "truncated flag must be true");
            check(r.evidences.size() == 3, "expected 3 whole items (3*6000=18000 <= 20000, 4th item would exceed 20000): got " + r.evidences.size());
            int total = 0;
            for(KnowledgeRetrieval.Evidence ev : r.evidences) {
                check(ev.text.length() == 6000, "item must be preserved whole");
                total += ev.text.length();
            }
            check(total <= 20000, "total chars within 20000: " + total);
        }

        // Test retrieve with namespaces overload
        try(Server s=new Server("namespaces")) {
            KnowledgeRetrieval.Result r = KnowledgeRetrieval.retrieve("问题", Arrays.asList("ns1", "ns2"), snapshot(true, s.url()), 1000);
            check(r.isHit(), "namespaces retrieve HIT");
        }

        // Session reuse: a second retrieval on the same endpoint sends only tools/call.
        try(Server s=new Server("normal")) {
            List<String> stages=Collections.synchronizedList(new ArrayList<String>());
            CortrixClient.trace=stages::add;
            try {
                KnowledgeRetrieval.Result first=KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);
                check(first.isHit(),"reuse first HIT");
                KnowledgeRetrieval.Result second=KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);
                check(second.isHit(),"reuse second HIT");
                check(s.methods.equals(Arrays.asList("initialize","notifications/initialized","tools/list","tools/call","tools/call")),"warm sequence "+s.methods);
                boolean cold=false,warm=false;
                for(String line:stages){if(line.contains("warm=false")&&line.contains("call_ms="))cold=true;if(line.contains("warm=true"))warm=true;}
                check(cold&&warm,"stage trace warm/cold: "+stages);
            } finally { CortrixClient.trace=ev->{}; }
        }
        // Session expiry: a warm tools/call rejected with 404 re-handshakes once and retries.
        try(Server s=new Server("expire")) {
            check(KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000).isHit(),"expire setup HIT");
            KnowledgeRetrieval.Result retried=KnowledgeRetrieval.retrieve("问题",snapshot(true,s.url()),1000);
            check(retried.isHit(),"404 session retry HIT: "+retried.status);
            check(s.methods.equals(Arrays.asList("initialize","notifications/initialized","tools/list","tools/call","tools/call","initialize","notifications/initialized","tools/list","tools/call")),"expire sequence "+s.methods);
        }

        String settings=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),StandardCharsets.UTF_8);
        check(settings.contains("KnowledgeRetrieval.probe(") && settings.contains("new Thread(") && settings.contains("runOnUiThread("),"settings off-main-thread protocol self-test missing");
        System.out.println("CortrixClientTest: ALL PASS");
    }
}
