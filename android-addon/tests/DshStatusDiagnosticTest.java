package com.turboio.addon;

import java.io.*;
import java.net.*;
import java.util.*;

/** Real status pipeline with injected transport failures; never contacts a server. */
public final class DshStatusDiagnosticTest {
    private static int checks;
    private static final String SECRET="private-token-URL-result";
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static DshClient.Config config(){return new DshClient.Config(true,"https://example.com",SECRET,"c","p","r",
        "dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0","dsh:permission:cmVhZC1vbmx5");}
    private static DshClient.MapStateStore store(){DshClient.MapStateStore s=new DshClient.MapStateStore();s.saveSession(config().targetFingerprint(),"s","msg_current");return s;}
    private static Map<String,Object> state(){return DshClient.mapOf("state",DshClient.mapOf("sessionId","s","runtimeId","r","status","idle"));}
    private static void failure(int phase,Exception error,String expected)throws Exception{
        final int[] calls={0};DshClient.MapStateStore s=store();s.savePending(config().targetFingerprint(),"s","msg_current");
        DshClient client=new DshClient(config(),s,(method,url,token,body)->{calls[0]++;if(calls[0]==phase)throw error;return state();});
        try{client.status(Collections.emptyMap());throw new AssertionError("exception converted to normal result");}
        catch(Exception out){
            check(expected.equals(out.getMessage()),"safe diagnostic stage/reason expected "+expected);
            check(expected.equals(DshSettings.Policy.statusError(out)),"settings preserves safe stage diagnostic");
            check(out.getClass().getSimpleName().equals("StatusQueryException"),"typed status exception");
            check(out.getCause()==null&&out.getSuppressed().length==0,"raw cause not retained");
            StringWriter trace=new StringWriter();out.printStackTrace(new PrintWriter(trace));check(!trace.toString().contains(SECRET),"no sensitive exception in trace");
        }
        check(calls[0]==phase,"no later request after failure");
        check(s.unknownDelivery()&&"s".equals(s.sessionId())&&"msg_current".equals(s.clientMessageId()),"pending and anchor preserved");
    }
    private static void realHttpFailures()throws Exception{
        final int[] call={0},phase={1};final boolean[] large={false};final byte[][] response={null};
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
                public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
                public int getResponseCode(){return 200;}
                public InputStream getInputStream(){
                    call[0]++;
                    if(phase[0]==3)return null;
                    if(response[0]!=null)return new ByteArrayInputStream(url.getPath().endsWith("/runtime/state")?DshClient.stringifyJson(state()).getBytes():response[0]);
                    if(call[0]!=phase[0])return new ByteArrayInputStream("{\"state\":{\"sessionId\":\"s\",\"runtimeId\":\"r\",\"status\":\"idle\"}}".getBytes());
                    if(!large[0])return new ByteArrayInputStream(("not JSON "+SECRET).getBytes());
                    return new InputStream(){int left=16*1048576+1;public int read(){return left-->0?'x':-1;}};
                }
            };}
        }:null);
        char[] padding=new char[1048576+100];Arrays.fill(padding,'a');
        Map<String,Object> payload=DshClient.mapOf("sessionId","s","hasMore",false,"items",Arrays.asList(DshClient.mapOf("role","user","type","message","source",DshClient.mapOf("clientMessageId","msg_current")),DshClient.mapOf("role","assistant","type","message","status","done","content",new String(padding))));
        response[0]=DshClient.stringifyJson(payload).getBytes("UTF-8");
        DshClient bounded=new DshClient(config(),store(),DshClient::httpTransport);
        check("completed".equals(bounded.status(Collections.emptyMap()).get("status")),"large valid timeline passes real reader parser and evaluator");
        try{DshClient.httpTransport("GET","https://example.com/api/v2/sessions/s/timeline?mode=latest&limit=200",SECRET,null);throw new AssertionError("non-status limit widened");}
        catch(IOException expected){check("response_limit".equals(expected.getMessage()),"ordinary endpoint retains 1MiB limit");}
        try{DshClient.httpTransport("POST","https://example.com/api/v2/sessions/s/timeline",SECRET,null);throw new AssertionError("POST limit widened");}
        catch(IOException expected){check("response_limit".equals(expected.getMessage()),"POST remains 1MiB");}
        DshClient nested=new DshClient(config(),store(),(method,url,token,body)->{
            for(String other:new String[]{"https://example.com/api/v2/directory",url+"x"}){
                try{DshClient.httpTransport("GET",other,token,null);throw new AssertionError("nested other URL widened");}
                catch(IOException expected){check("response_limit".equals(expected.getMessage()),"nested other GET stays 1MiB");}
            }
            try{DshClient.httpTransport("POST",url.replace("/runtime/state","/timeline"),token,null);throw new AssertionError("nested POST widened");}
            catch(IOException expected){check("response_limit".equals(expected.getMessage()),"nested POST stays 1MiB");}
            return DshClient.httpTransport(method,url,token,body);
        });
        check("completed".equals(nested.status(Collections.emptyMap()).get("status")),"exact status GET alone gets allowance");
        response[0]=null;phase[0]=3;
        try{DshClient.httpTransport("GET","https://example.com/api/v2/sessions/s/timeline?mode=latest&limit=200",SECRET,null);throw new AssertionError("null response stream accepted");}
        catch(IOException expected){check("empty_body".equals(expected.getMessage()),"null stream has explicit safe transport diagnosis");check(expected.getCause()==null,"null stream raw cause discarded");}
        for(int step:new int[]{1,2})for(boolean limit:new boolean[]{false,true}){
            call[0]=0;phase[0]=step;large[0]=limit;
            DshClient.MapStateStore s=store();DshClient client=new DshClient(config(),s,DshClient::httpTransport);
            String expected=(step==1?"runtime_state":"timeline")+(limit?"/response_limit":"/json_invalid");
            try{client.status(Collections.emptyMap());throw new AssertionError("malformed response accepted");}
            catch(Exception error){check(expected.equals(error.getMessage()),"real transport diagnostic "+expected);check(error.getCause()==null,"real raw cause discarded");}
            check(call[0]==step,"real transport stops after failure");check("s".equals(s.sessionId())&&"msg_current".equals(s.clientMessageId()),"real transport retains anchor");
        }
    }
    public static void main(String[] args)throws Exception{
        realHttpFailures();
        for(int phase:new int[]{1,2}){
            String stage=phase==1?"runtime_state":"timeline";
            failure(phase,new SocketTimeoutException(SECRET),stage+"/timeout");
            failure(phase,new ConnectException(SECRET),stage+"/connect");
            failure(phase,new UnknownHostException(SECRET),stage+"/connect");
            failure(phase,new IOException(SECRET),stage+"/unknown");
            failure(phase,new IllegalArgumentException("limit "+SECRET),stage+"/unknown");
            for(int status:new int[]{401,403,404,422,429,500})failure(phase,new DshClient.HttpStatusException(status,SECRET),stage+"/http_"+status);
        }
        DshClient client=new DshClient(config(),store(),(m,u,t,b)->Collections.emptyMap());
        check("unknown".equals(client.status(Collections.emptyMap()).get("status")),"missing fields stay normal unknown");
        client=new DshClient(config(),store(),(m,u,t,b)->DshClient.mapOf("state",DshClient.mapOf("sessionId","foreign","runtimeId","r","status","idle")));
        check("unknown".equals(client.status(Collections.emptyMap()).get("status")),"binding mismatch stays normal unknown");
        System.out.println("DshStatusDiagnosticTest: "+checks+" checks passed");
    }
}
