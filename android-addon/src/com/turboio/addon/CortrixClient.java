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
    private static final class RequestTrace {
        final String method;
        final String toolName;
        final JSONArray namespaces;
        volatile boolean sent;
        volatile int httpCode;
        volatile String traceId;

        RequestTrace(String body) throws JSONException {
            JSONObject request=new JSONObject(body);
            method=request.getString("method");
            JSONObject params="tools/call".equals(method)?request.getJSONObject("params"):null;
            toolName=params==null?null:params.getString("name");
            namespaces=params==null?null:params.getJSONObject("arguments").optJSONArray("namespaces");
        }
    }
    private final class Exchange {
        final long deadline; volatile HttpURLConnection connection;
        String session="",version="";
        volatile RequestTrace currentRequest;
        Exchange(long deadline){this.deadline=deadline;}
        int remaining() throws SocketTimeoutException {
            long nanos=deadline-System.nanoTime();if(nanos<=0||Thread.currentThread().isInterrupted()) throw new SocketTimeoutException("MCP deadline");
            return (int)Math.min(Integer.MAX_VALUE,Math.max(1,TimeUnit.NANOSECONDS.toMillis(nanos)));
        }
        JSONObject post(String body,long id) throws Exception {
            RequestTrace request=new RequestTrace(body);
            currentRequest=request;
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
                try(OutputStream out=c.getOutputStream()){remaining();out.write(bytes);request.sent=true;}
                remaining();c.setReadTimeout(remaining()); int code=c.getResponseCode();
                request.traceId=c.getHeaderField("X-Trace-Id");
                request.httpCode=code;remaining();
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
    private static final String LIST_NAMESPACES="cortrix_list_namespaces";
    static final class NamespaceResult {
        final KnowledgeRetrieval.Status status;
        final List<String> names;
        NamespaceResult(KnowledgeRetrieval.Status status,List<String> names) {
            this.status=status;
            this.names=Collections.unmodifiableList(new ArrayList<>(names));
        }
    }
    NamespaceResult listNamespaces(long budgetMs) {
        if(endpoint.isEmpty()) return namespaceFailure(KnowledgeRetrieval.Status.UNCONFIGURED);
        if(budgetMs<=0) return namespaceFailure(KnowledgeRetrieval.Status.TIMEOUT);
        Exchange exchange=new Exchange(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.min(budgetMs,TimeUnit.DAYS.toMillis(1))));
        FutureTask<NamespaceResult> task=new FutureTask<>(()->{
            try {
                negotiateProtocol(exchange);
                notifyInitialized(exchange);
                if(!hasTool(exchange,LIST_NAMESPACES)) return namespaceFailure(KnowledgeRetrieval.Status.TOOL_ERROR);
                return new NamespaceResult(KnowledgeRetrieval.Status.HIT,fetchNamespaces(exchange));
            } catch(Failure e) { return namespaceFailure(e.status); }
            catch(SocketTimeoutException e) { return namespaceFailure(KnowledgeRetrieval.Status.TIMEOUT); }
            catch(SessionExpired|IOException e) { return namespaceFailure(KnowledgeRetrieval.Status.TRANSPORT_ERROR); }
            catch(JSONException e) { return namespaceFailure(KnowledgeRetrieval.Status.PROTOCOL_ERROR); }
        });
        Thread worker=new Thread(task,"cortrix-namespaces");worker.setDaemon(true);worker.start();
        try { return task.get(Math.max(1,exchange.deadline-System.nanoTime()),TimeUnit.NANOSECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();return namespaceFailure(KnowledgeRetrieval.Status.TIMEOUT); }
        catch(TimeoutException e) { return namespaceFailure(KnowledgeRetrieval.Status.TIMEOUT); }
        catch(ExecutionException e) { return namespaceFailure(KnowledgeRetrieval.Status.PROTOCOL_ERROR); }
        finally { cancelPending(task,exchange); }
    }
    private static NamespaceResult namespaceFailure(KnowledgeRetrieval.Status status) {
        return new NamespaceResult(status,Collections.emptyList());
    }
    private static List<String> fetchNamespaces(Exchange exchange)throws Exception {
        final int pageSize=100;
        Set<String> names=new LinkedHashSet<>();
        int offset=0;
        long id=3;
        while(true) {
            JSONObject args=new JSONObject().put("limit",pageSize).put("offset",offset);
            JSONObject request=new JSONObject().put("jsonrpc","2.0").put("id",id).put("method","tools/call")
                .put("params",new JSONObject().put("name",LIST_NAMESPACES).put("arguments",args));
            JSONObject response=exchange.post(request.toString(),id++);
            if(response.optBoolean("isError",false))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
            JSONObject envelope=response.optJSONObject("structuredContent");
            if(envelope==null) {
                JSONArray content=response.getJSONArray("content");
                for(int i=0;i<content.length();i++) {
                    JSONObject item=content.getJSONObject(i);
                    if("text".equals(item.optString("type"))) {envelope=new JSONObject(item.getString("text"));break;}
                }
            }
            if(envelope==null)throw protocol();
            JSONObject meta=envelope.optJSONObject("meta");
            if(meta!=null && !"success".equals(meta.optString("category","success")))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
            JSONObject data=envelope.has("data")?envelope.getJSONObject("data"):envelope;
            JSONArray rows=data.getJSONArray("namespaces");
            int previousSize=names.size();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                String name=row.optString("namespace",row.optString("name","")).trim();
                if(name.isEmpty())throw protocol();
                names.add(name);
            }
            offset+=rows.length();
            int total=data.optInt("total",-1);
            if(total>=0 ? offset>=total : rows.length()<pageSize)return new ArrayList<>(names);
            if(names.size()==previousSize)throw protocol();
        }
    }
    private static void negotiateProtocol(Exchange exchange)throws Exception {
        JSONObject init=exchange.post(buildInitializePayload(1),1);
        exchange.version=init.optString("protocolVersion");
        if(!exchange.version.equals("2025-03-26")&&!exchange.version.equals("2024-11-05"))throw protocol();
    }
    private static void notifyInitialized(Exchange exchange)throws Exception {
        exchange.post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",0);
    }
    private static boolean hasTool(Exchange exchange,String name)throws Exception {
        JSONObject result=exchange.post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",2);
        JSONArray tools=result.getJSONArray("tools");
        for(int i=0;i<tools.length();i++)if(name.equals(tools.getJSONObject(i).optString("name")))return true;
        return false;
    }
    private static void cancelPending(FutureTask<?> task,Exchange exchange) {
        if(!task.isDone()) {
            task.cancel(true);
            HttpURLConnection c=exchange.connection;
            if(c!=null){Thread closer=new Thread(c::disconnect,"cortrix-close");closer.setDaemon(true);closer.start();}
        }
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
                            if(!hasTool(exchange,"cortrix_query"))throw protocol();
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
                negotiateProtocol(exchange);
                SESSIONS.put(sessionKey(),new Session(exchange.session,exchange.version));
                notifyInitialized(exchange);
                initMs=elapsed(t0);
                long t1=System.nanoTime();
                if(!hasTool(exchange,"cortrix_query"))throw protocol();
                listMs=elapsed(t1);
                return call(query,namespaces,revision,probe,exchange,startNs,false,initMs,listMs);
            }catch(Failure e){traceStage(warm,initMs,listMs,0,e.status);return KnowledgeRetrieval.result(e.status,revision,elapsed(startNs));}
            catch(SessionExpired e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TRANSPORT_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TRANSPORT_ERROR,revision,elapsed(startNs));}
            catch(SocketTimeoutException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TIMEOUT);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,elapsed(startNs));}
            catch(JSONException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.PROTOCOL_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.PROTOCOL_ERROR,revision,elapsed(startNs));}
            catch(IOException e){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.TRANSPORT_ERROR);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TRANSPORT_ERROR,revision,elapsed(startNs));}
        });
        Thread worker=new Thread(task,"cortrix-mcp");worker.setDaemon(true);worker.start();
        try{KnowledgeRetrieval.Result result=task.get(Math.max(1,exchange.deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(result.status!=KnowledgeRetrieval.Status.HIT && result.status!=KnowledgeRetrieval.Status.NO_MATCH && !(result.status==KnowledgeRetrieval.Status.TOOL_ERROR&&!result.namespacesFailed.isEmpty()))traceResult(exchange,null,startNs,query);
            return result;}
        catch(TimeoutException e){traceResult(exchange,null,startNs,query);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        catch(InterruptedException e){Thread.currentThread().interrupt();traceResult(exchange,null,startNs,query);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        catch(ExecutionException e){traceResult(exchange,null,startNs,query);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.PROTOCOL_ERROR,revision,Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNs)));}
        finally { cancelPending(task,exchange); }
    }
    private KnowledgeRetrieval.Result call(String query,List<String> namespaces,long revision,boolean probe,Exchange exchange,long startNs,boolean warm,long initMs,long listMs)throws Exception {
        if(probe){traceStage(warm,initMs,listMs,0,KnowledgeRetrieval.Status.NO_MATCH);return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.NO_MATCH,revision,elapsed(startNs));}
        long t=System.nanoTime();
        ParsedResult parsed=parseEnvelope(exchange.post(buildQueryPayload(3,query,namespaces,2),3));exchange.remaining();
        long callMs=elapsed(t);
        KnowledgeRetrieval.Status status=!parsed.evidences.isEmpty()?KnowledgeRetrieval.Status.HIT:(parsed.coverageRatio==0&&!parsed.namespacesFailed.isEmpty()?KnowledgeRetrieval.Status.TOOL_ERROR:KnowledgeRetrieval.Status.NO_MATCH);
        traceResult(exchange,parsed,startNs,query);
        traceStage(warm,initMs,listMs,callMs,status);
        return new KnowledgeRetrieval.Result(status,parsed.evidences,revision,parsed.coverageRatio,parsed.namespacesFailed,elapsed(startNs),parsed.truncated);
    }
    void traceResult(Exchange exchange,ParsedResult parsed,long startNs,String query) {
        try {
            URL url=new URL(endpoint);
            String safeEndpoint=url.getProtocol()+"://"+url.getHost()+(url.getPort()<0?"":":"+url.getPort());
            JSONObject log=new JSONObject();
            RequestTrace request=exchange.currentRequest;
            boolean querySent=request!=null && request.sent && request.toolName!=null;
            log.put("endpoint",safeEndpoint);
            log.put("method",request==null?JSONObject.NULL:request.method);
            log.put("query_sent",querySent);
            if(querySent) {
                log.put("name",request.toolName);
                if(request.namespaces!=null)log.put("namespaces",request.namespaces);
            }
            Object code=request!=null && request.httpCode!=0?request.httpCode:JSONObject.NULL;
            log.put("stage_http_code",code);
            log.put("http_code",querySent?code:JSONObject.NULL);
            log.put("elapsed_ms",elapsed(startNs));log.put("row_count",parsed==null?JSONObject.NULL:parsed.rowCount);
            log.put("coverage_ratio",parsed==null?JSONObject.NULL:parsed.coverageRatio);log.put("namespaces_failed",parsed==null?new JSONArray():new JSONArray(parsed.namespacesFailed));
            String traceId=querySent?request.traceId:null;
            if(parsed!=null&&!parsed.traceId.isEmpty())traceId=parsed.traceId;
            log.put("trace_id",traceId==null?JSONObject.NULL:traceId);
            String text=log.toString();
            if(query!=null&&!query.isEmpty())text=text.replace(query,"[redacted]");
            if(!bearerToken.isEmpty())text=text.replace(bearerToken,"[redacted]");
            trace.log("hint mcp result="+text);
        } catch(Exception ignored) { }
    }
    static long elapsed(long startNanos){return Math.max(0L,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startNanos));}
    static void traceStage(boolean warm,long initMs,long listMs,long callMs,KnowledgeRetrieval.Status status) {
        trace.log("hint mcp warm="+warm+" init_ms="+initMs+" list_ms="+listMs+" call_ms="+callMs+" status="+status);
    }
    static final class ParsedResult {
        final List<KnowledgeRetrieval.Evidence> evidences;
        final double coverageRatio;
        final List<String> namespacesFailed;
        final boolean truncated; final int rowCount; final String traceId;
        ParsedResult(List<KnowledgeRetrieval.Evidence> evidences,double coverageRatio,List<String> namespacesFailed,boolean truncated,int rowCount,String traceId) {
            this.evidences=evidences;this.coverageRatio=coverageRatio;this.namespacesFailed=namespacesFailed;this.truncated=truncated;this.rowCount=rowCount;this.traceId=traceId;
        }
    }
    static final class ParseContext {
        final List<KnowledgeRetrieval.Evidence> evidences=new ArrayList<>();
        double coverageRatio=1.0;
        final List<String> namespacesFailed=new ArrayList<>();
        boolean truncated=false;
        int totalChars=0,rowCount=0; String traceId="";
    }
    static ParsedResult parseEnvelope(JSONObject result)throws Exception {
        if(result.optBoolean("isError",false))throw new Failure(KnowledgeRetrieval.Status.TOOL_ERROR);
        ParseContext ctx=new ParseContext();
        extractStructuredData(result,ctx);
        if(result.has("structuredContent")){
            parseData(result.get("structuredContent"),ctx);
            return new ParsedResult(ctx.evidences,ctx.coverageRatio,ctx.namespacesFailed,ctx.truncated,ctx.rowCount,ctx.traceId);
        }
        JSONArray content=result.optJSONArray("content");if(content==null)throw protocol();
        for(int i=0;i<content.length();i++){
            JSONObject item=content.getJSONObject(i);
            if("text".equals(item.optString("type"))) parseData(new JSONTokener(item.getString("text")).nextValue(),ctx);
        }
        return new ParsedResult(ctx.evidences,ctx.coverageRatio,ctx.namespacesFailed,ctx.truncated,ctx.rowCount,ctx.traceId);
    }
    static void extractStructuredData(JSONObject obj,ParseContext ctx) {
        if(obj==null) return;
        JSONObject sd=obj.optJSONObject("structured_data");
        JSONObject meta=obj.optJSONObject("meta");
        if(meta!=null) { if(sd==null) sd=meta.optJSONObject("structured_data"); String id=meta.optString("trace_id","");if(!id.isEmpty())ctx.traceId=id; }
        if(sd!=null) {
            String traceId=sd.optString("trace_id","");
            if(!traceId.isEmpty())ctx.traceId=traceId;
            if(sd.has("coverage_ratio")) ctx.coverageRatio=sd.optDouble("coverage_ratio",ctx.coverageRatio);
            JSONArray failed=sd.optJSONArray("namespaces_failed");
            if(failed!=null) {
                for(int i=0;i<failed.length();i++) {
                    JSONObject entry=failed.optJSONObject(i);
                    String ns=entry==null?failed.optString(i,""):entry.optString("namespace","");
                    if(entry!=null&&!entry.optString("error_code","").isEmpty())ns+=(ns.isEmpty()?"":":")+entry.optString("error_code");
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
        ctx.rowCount+=rows.length();
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
