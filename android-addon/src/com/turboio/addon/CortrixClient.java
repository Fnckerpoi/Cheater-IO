package com.turboio.addon;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Streamable HTTP MCP client with one cached session per endpoint and credential. */
public final class CortrixClient {
    private static final int MAX_RESPONSE_BYTES=1048576;
    private static final int MAX_EVIDENCE_TOTAL_CHARS=20000;
    /** Stage timing sink; receives durations and statuses only, never query text. */
    public interface Trace { void log(String event); }
    static volatile Trace trace = event -> {};
    private static final Map<String,Session> SESSIONS=new ConcurrentHashMap<>();
    static final class Session { final String id,version; Session(String id,String version){this.id=id;this.version=version;} }
    private static final class SessionExpired extends Exception {}
    static void resetSessions() { SESSIONS.clear(); }
    private String sessionKey(){return endpoint+"\u0000"+bearerToken;}
    private final String endpoint, bearerToken;
    private final int connectTimeoutMs, readTimeoutMs;
    private static final CortrixClient DEFAULT_INSTANCE=new CortrixClient();
    public static CortrixClient getDefault() { return DEFAULT_INSTANCE; }
    public CortrixClient() { this("","",500,800); }
    public CortrixClient(String endpoint,String bearerToken,int connectTimeoutMs,int readTimeoutMs) {
        this.endpoint=KnowledgeRetrieval.clean(endpoint).trim();this.bearerToken=KnowledgeRetrieval.clean(bearerToken);
        this.connectTimeoutMs=connectTimeoutMs;this.readTimeoutMs=readTimeoutMs;
    }
    public static String buildInitializePayload(long id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"+id+",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"turboio-android\",\"version\":\"1.0.0\"}}}";
    }
    public static String buildQueryPayload(long id,String query,int topK) {
        return buildQueryPayload(id, query, null, topK);
    }
    public static String buildQueryPayload(long id,String query,List<String> namespaces,int topK) {
        try {
            JSONObject args=new JSONObject().put("query",query).put("top_k",topK).put("compact",true).put("rerank",false);
            if(namespaces!=null&&!namespaces.isEmpty()) {
                JSONArray nsArr=new JSONArray();
                for(String ns:namespaces) {
                    if(ns!=null&&!ns.trim().isEmpty()) nsArr.put(ns.trim());
                }
                if(nsArr.length()>0) args.put("namespaces",nsArr);
            }
            return new JSONObject().put("jsonrpc","2.0").put("id",id).put("method","tools/call").put("params",new JSONObject().put("name","cortrix_query").put("arguments",args)).toString();
        }catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    private static final class Failure extends Exception {
        final KnowledgeRetrieval.Status status;
        Failure(KnowledgeRetrieval.Status status){this.status=status;}
    }
    private static Failure protocol(){return new Failure(KnowledgeRetrieval.Status.PROTOCOL_ERROR);}
    private final class Exchange {
        final long deadline; volatile HttpURLConnection connection;
        String session="",version="";
        Exchange(long deadline){this.deadline=deadline;}
        int remaining() throws SocketTimeoutException {
            long nanos=deadline-System.nanoTime();if(nanos<=0||Thread.currentThread().isInterrupted()) throw new SocketTimeoutException("MCP deadline");
            return (int)Math.min(Integer.MAX_VALUE,Math.max(1,TimeUnit.NANOSECONDS.toMillis(nanos)));
        }
        JSONObject post(String body,long id) throws Exception {
            remaining(); HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); connection=c;
            {
                c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestMethod("POST");c.setDoOutput(true);
                c.setConnectTimeout(connectTimeoutMs>0?Math.min(connectTimeoutMs,remaining()):remaining());
                c.setReadTimeout(readTimeoutMs>0?Math.min(readTimeoutMs,remaining()):remaining());
                c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json, text/event-stream");
                if(!bearerToken.isEmpty()) c.setRequestProperty("Authorization","Bearer "+bearerToken);
                if(!session.isEmpty()) c.setRequestProperty("Mcp-Session-Id",session);
                if(!version.isEmpty()) c.setRequestProperty("MCP-Protocol-Version",version);
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){remaining();out.write(bytes);}
                remaining();c.setReadTimeout(remaining()); int code=c.getResponseCode();remaining();
                if(code==401||code==403)throw new Failure(KnowledgeRetrieval.Status.AUTH_ERROR);
                if(id==0) {if(code!=200&&code!=202&&code!=204)throw new Failure(KnowledgeRetrieval.Status.TRANSPORT_ERROR);return null;}
                if(code==404&&!session.isEmpty())throw new SessionExpired();
                if(code!=200)throw new Failure(KnowledgeRetrieval.Status.TRANSPORT_ERROR);
                String header=c.getHeaderField("Mcp-Session-Id");if(header!=null)session=header;
                String type=c.getContentType();JSONObject response;
                try(InputStream in=c.getInputStream()) {
                    response=type!=null&&type.toLowerCase(Locale.ROOT).startsWith("text/event-stream")?readSse(c,in,id):new JSONObject(readJson(c,in));
                }
                remaining();return validate(response,id);
            }
            // Keep the socket in the keep-alive pool for the next request; only the
            // cancellation path in retrieve() disconnects it.
        }
        String readJson(HttpURLConnection c,InputStream in)throws Exception {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            while(true){c.setReadTimeout(remaining());n=in.read(buf);if(n<0)break;out.write(buf,0,n);if(out.size()>MAX_RESPONSE_BYTES)throw protocol();}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
        JSONObject readSse(HttpURLConnection c,InputStream in,long id)throws Exception {
            ByteArrayOutputStream line=new ByteArrayOutputStream();StringBuilder data=new StringBuilder();int total=0;
            while(true){c.setReadTimeout(remaining());int b=in.read();if(b<0)throw protocol();if(++total>MAX_RESPONSE_BYTES)throw protocol();
                if(b!='\n'){line.write(b);continue;}
                String s=new String(line.toByteArray(),StandardCharsets.UTF_8);line.reset();if(s.endsWith("\r"))s=s.substring(0,s.length()-1);
                if(s.isEmpty()) {
                    if(data.length()==0)continue;
                    JSONObject event=new JSONObject(data.toString());data.setLength(0);
                    if(event.has("id")&&sameId(event.opt("id"),id))return event;
                } else if(s.startsWith("data:")) {if(data.length()>0)data.append('\n');String value=s.substring(5);data.append(value.startsWith(" ")?value.substring(1):value);}
            }
        }
    }
    static boolean sameId(Object actual,long expected){return actual instanceof Number&&((Number)actual).doubleValue()==expected;}
    static JSONObject validate(JSONObject response,long id)throws Exception {
        if(!"2.0".equals(response.optString("jsonrpc"))||!sameId(response.opt("id"),id))throw protocol();
        if(response.has("error"))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
        JSONObject result=response.optJSONObject("result");if(result==null)throw protocol();return result;
    }
    KnowledgeRetrieval.Result retrieve(String query,long budgetMs,long revision,boolean probe) {
        return retrieve(query, null, budgetMs, revision, probe);
    }
    KnowledgeRetrieval.Result retrieve(String query,List<String> namespaces,long budgetMs,long revision,boolean probe) {
        long startNs=System.nanoTime();
        if(endpoint.isEmpty())return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.UNCONFIGURED,revision,0L);
        if(budgetMs<=0)return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,0L);
        long budgetNanos=TimeUnit.MILLISECONDS.toNanos(Math.min(budgetMs,TimeUnit.DAYS.toMillis(1)));
        Exchange exchange=new Exchange(System.nanoTime()+budgetNanos);
        FutureTask<KnowledgeRetrieval.Result> task=new FutureTask<>(()->{
            boolean warm=false;long initMs=0,listMs=0;
            try {
                Session cached=SESSIONS.get(sessionKey());
                if(cached!=null) {
                    warm=true;exchange.session=cached.id;exchange.version=cached.version;
                    try {
                        if(probe) {
                            // Reuse the cached session but still verify tool discovery.
                            long t1=System.nanoTime();
                            JSONObject list=exchange.post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",2);
                            boolean found=false;JSONArray tools=list.getJSONArray("tools");for(int i=0;i<tools.length();i++)if("cortrix_query".equals(tools.getJSONObject(i).optString("name")))found=true;
                            if(!found)throw protocol();
                            listMs=elapsed(t1);
                            return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.NO_MATCH,revision,elapsed(startNs));
                        }
                        return call(query,namespaces,revision,probe,exchange,startNs,true,initMs,listMs);
                    } catch(SessionExpired stale) {
                        SESSIONS.remove(sessionKey());
                        exchange.session="";exchange.version="";warm=false;
                    } catch(IOException stale) {
                        // Stale keep-alive socket: drop the cached session and fall back to a fresh handshake.
                        SESSIONS.remove(sessionKey());
                        exchange.session="";exchange.version="";warm=false;
                    }
                }
                long t0=System.nanoTime();
                JSONObject init=exchange.post(buildInitializePayload(1),1);exchange.version=init.optString("protocolVersion");
                if(!exchange.version.equals("2025-03-26")&&!exchange.version.equals("2024-11-05"))throw protocol();
                SESSIONS.put(sessionKey(),new Session(exchange.session,exchange.version));
                exchange.post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",0);
                initMs=elapsed(t0);
                long t1=System.nanoTime();
                JSONObject list=exchange.post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",2);
                boolean found=false;JSONArray tools=list.getJSONArray("tools");for(int i=0;i<tools.length();i++)if("cortrix_query".equals(tools.getJSONObject(i).optString("name")))found=true;
                if(!found)throw protocol();
                listMs=elapsed(t1);
                return call(query,namespaces,revision,probe,exchange,startNs,false,initMs,listMs);
            }catch(Failure e){traceStage(warm,initMs,listMs,0,e.status);return KnowledgeRetrieval.result(e.status,revision,elapsed(startNs));}
            catch(SessionExpired e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TRANSPORT_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TRANSPORT_ERROR,revision,elapsed(startNs));}
            catch(SocketTimeoutException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TIMEOUT);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,elapsed(startNs));}
            catch(JSONException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.PROTOCOL_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.PROTOCOL_ERROR,revision,elapsed(startNs));}
            catch(IOException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TRANSPORT_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TRANSPORT_ERROR,revision,elapsed(startNs));}
        });
        Thread worker=new Thread(task,"cortrix-mcp");worker.setDaemon(true);worker.start();
        try{return task.get(Math.max(1,exchange.deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
        catch(TimeoutException e){return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        catch(InterruptedException e){Thread.currentThread().interrupt();return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        catch(ExecutionException e){return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.PROTOCOL_ERROR,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        finally {if(!task.isDone()){
            task.cancel(true);final HttpURLConnection c=exchange.connection;
            if(c!=null){Thread closer=new Thread(()->c.disconnect(),"cortrix-close");closer.setDaemon(true);closer.start();}
        }}
    }
    private KnowledgeRetrieval.Result call(String query,List<String> namespaces,long revision,boolean probe,Exchange exchange,long startNs,boolean warm,long initMs,long listMs)throws Exception {
        if(probe){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.NO_MATCH);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.NO_MATCH,revision,elapsed(startNs));}
        long t=System.nanoTime();
        ParsedResult parsed=parseEnvelope(exchange.post(buildQueryPayload(3,query,namespaces,2),3));exchange.remaining();
        long callMs=elapsed(t);
        KnowledgeRetrieval.Status status=parsed.evidences.isEmpty()?KnowledgeRetrieval.Status.NO_MATCH:KnowledgeRetrieval.Status.HIT;
        traceStage(warm,initMs,listMs,callMs,status);
        return new KnowledgeRetrieval.Result(status,parsed.evidences,revision,parsed.coverageRatio,parsed.namespacesFailed,elapsed(startNs),parsed.truncated);
    }
    static long elapsed(long startNanos){return Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNanos));}
    static void traceStage(boolean warm,long initMs,long listMs,long callMs,KnowledgeRetrieval.Status status) {
        trace.log("hint mcp warm="+warm+" init_ms="+initMs+" list_ms="+listMs+" call_ms="+callMs+" status="+status);
    }
    static final class ParsedResult {
        final List<KnowledgeRetrieval.Evidence> evidences;
        final double coverageRatio;
        final List<String> namespacesFailed;
        final boolean truncated;
        ParsedResult(List<KnowledgeRetrieval.Evidence> evidences,double coverageRatio,List<String> namespacesFailed,boolean truncated) {
            this.evidences=evidences;this.coverageRatio=coverageRatio;this.namespacesFailed=namespacesFailed;this.truncated=truncated;
        }
    }
    static final class ParseContext {
        final List<KnowledgeRetrieval.Evidence> evidences=new ArrayList<>();
        double coverageRatio=1.0;
        final List<String> namespacesFailed=new ArrayList<>();
        boolean truncated=false;
        int totalChars=0;
    }
    static ParsedResult parseEnvelope(JSONObject result)throws Exception {
        if(result.optBoolean("isError",false))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
        ParseContext ctx=new ParseContext();
        extractStructuredData(result,ctx);
        if(result.has("structuredContent")){
            parseData(result.get("structuredContent"),ctx);
            return new ParsedResult(ctx.evidences,ctx.coverageRatio,ctx.namespacesFailed,ctx.truncated);
        }
        JSONArray content=result.optJSONArray("content");if(content==null)throw protocol();
        for(int i=0;i<content.length();i++){
            JSONObject item=content.getJSONObject(i);
            if("text".equals(item.optString("type"))) parseData(new JSONTokener(item.getString("text")).nextValue(),ctx);
        }
        return new ParsedResult(ctx.evidences,ctx.coverageRatio,ctx.namespacesFailed,ctx.truncated);
    }
    static void extractStructuredData(JSONObject obj,ParseContext ctx) {
        if(obj==null) return;
        JSONObject sd=obj.optJSONObject("structured_data");
        JSONObject meta=obj.optJSONObject("meta");
        if(meta!=null&&sd==null) sd=meta.optJSONObject("structured_data");
        if(sd!=null) {
            if(sd.has("coverage_ratio")) ctx.coverageRatio=sd.optDouble("coverage_ratio",ctx.coverageRatio);
            JSONArray failed=sd.optJSONArray("namespaces_failed");
            if(failed!=null) {
                for(int i=0;i<failed.length();i++) {
                    String ns=failed.optString(i,"");
                    if(!ns.isEmpty()&&!ctx.namespacesFailed.contains(ns)) ctx.namespacesFailed.add(ns);
                }
            }
        }
    }
    static List<KnowledgeRetrieval.Evidence> parseResult(JSONObject result)throws Exception {
        return parseEnvelope(result).evidences;
    }
    static void parseData(Object value,List<KnowledgeRetrieval.Evidence> out)throws Exception {
        ParseContext ctx=new ParseContext();parseData(value,ctx);out.addAll(ctx.evidences);
    }
    static void parseData(Object value,ParseContext ctx)throws Exception {
        if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;
            extractStructuredData(obj,ctx);
            JSONObject meta=obj.optJSONObject("meta");if(meta!=null&&!meta.optString("category","success").equals("success"))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
            if(obj.has("data")){parseData(obj.get("data"),ctx);return;}
            if(obj.has("results")){parseData(obj.get("results"),ctx);return;}
            if(obj.has("evidence")){parseRows(new JSONArray().put(obj),ctx);return;}
            throw protocol();
        }
        if(value instanceof JSONArray){parseRows((JSONArray)value,ctx);return;}
        throw protocol();
    }
    static void parseRows(JSONArray rows,List<KnowledgeRetrieval.Evidence> out)throws Exception {
        ParseContext ctx=new ParseContext();parseRows(rows,ctx);out.addAll(ctx.evidences);
    }
    static void parseRows(JSONArray rows,ParseContext ctx)throws Exception {
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);JSONArray evidence=row.optJSONArray("evidence");if(evidence==null)throw protocol();
            String sourceTime=extractSourceTime(row);
            Object metadata=row.opt("metadata");
            String metadataStr=metadata==null||metadata==JSONObject.NULL?"":metadata.toString();
            String ns=row.optString("namespace","");
            String docId=row.optString("doc_id","");
            String sourcePath=row.optString("source_path","");
            double score=row.optDouble("score",0);
            for(int j=0;j<evidence.length();j++) {
                JSONObject e=evidence.getJSONObject(j);String text=e.optString("text","");if(text.trim().isEmpty())continue;
                int len=text.length();
                if(ctx.totalChars+len>MAX_EVIDENCE_TOTAL_CHARS) {
                    ctx.truncated=true;
                    if(!ctx.evidences.isEmpty()) return;
                    text=text.substring(0,MAX_EVIDENCE_TOTAL_CHARS);
                    len=text.length();
                }
                ctx.evidences.add(new KnowledgeRetrieval.Evidence(ns,docId,sourcePath,metadataStr,score,e.optString("kind",""),text,sourceTime));
                ctx.totalChars+=len;
                if(ctx.truncated) return;
            }
        }
    }
    static String extractSourceTime(JSONObject row) {
        if(row==null) return "unknown";
        String t=findTimeField(row);
        if(!t.isEmpty()) return t;
        Object meta=row.opt("metadata");
        if(meta instanceof JSONObject) {
            t=findTimeField((JSONObject)meta);
            if(!t.isEmpty()) return t;
        } else if(meta instanceof String) {
            String s=((String)meta).trim();
            if(s.startsWith("{")&&s.endsWith("}")) {
                try {
                    t=findTimeField(new JSONObject(s));
                    if(!t.isEmpty()) return t;
                }catch(Exception ignored){}
            }
        }
        return "unknown";
    }
    private static String findTimeField(JSONObject obj) {
        if(obj==null) return "";
        String[] keys={"updated","updated_at","created","created_at","timestamp","date","time","datetime","source_time","source_times","modified"};
        for(String k:keys) {
            if(obj.has(k)) {
                Object val=obj.opt(k);
                if(val!=null&&val!=JSONObject.NULL) {
                    String s=val.toString().trim();
                    if(!s.isEmpty()&&!s.equalsIgnoreCase("null")) return s;
                }
            }
        }
        return "";
    }
    public static List<String> extractEvidences(String responseJson) {
        List<String> out=new ArrayList<>();try {
            JSONObject response=new JSONObject(responseJson);JSONObject result=response.optJSONObject("result");
            for(KnowledgeRetrieval.Evidence e:parseResult(result==null?response:result))out.add(e.text);
        }catch(Exception ignored){}return out;
    }
}
