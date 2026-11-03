package com.turboio.addon;
import android.os.Bundle;import java.io.*;import java.net.*;import java.util.*;
/** Real bridge + ToolClient + status transport/evaluator; fake only platform, secrets and HTTPS. */
public final class AaHostBridgeDiagnosticTest {
 static int checks,requests;static android.app.Activity app;static Map<String,Object> prefs;static AaHostBridge bridge;
 static String stage="",fault="",mutation="";static final String PRIVATE="private-token-http://secret.invalid/result";
 static final String MODEL="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0",PERMISSION="dsh:permission:cmVhZC1vbmx5";
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Connection extends HttpURLConnection {
  Connection(URL u){super(u);}public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  boolean hit(){return (stage.equals("runtime_state")&&url.getPath().endsWith("/state"))||(stage.equals("timeline")&&url.getPath().endsWith("/timeline"));}
  public int getResponseCode()throws IOException {ok(!Thread.holdsLock(DshClient.LOCK),"HTTP outside LOCK");requests++;
   if(hit()){if(mutation.equals("auth"))DshSettings.auth++;if(mutation.equals("selection"))ToolClient.selectionChanged();
    if(fault.equals("timeout"))throw new SocketTimeoutException(PRIVATE);if(fault.equals("connect"))throw new ConnectException(PRIVATE);if(fault.equals("unknown"))throw new IOException("timeout "+PRIVATE);if(fault.equals("http"))return 403;}
   return 200;
  }
  InputStream stream(Object o){return new ByteArrayInputStream(DshClient.stringifyJson(o).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  public InputStream getErrorStream(){return stream(DshClient.mapOf("code",PRIVATE));}
  public InputStream getInputStream(){if(hit()&&fault.equals("json_invalid"))return new ByteArrayInputStream(PRIVATE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   if(hit()&&fault.equals("response_limit"))return new InputStream(){int remaining=16*1048576+1;public int read(){if(remaining--<=0)return -1;return ' ';}public int read(byte[] b,int off,int len){if(remaining<=0)return -1;int n=Math.min(remaining,len);Arrays.fill(b,off,off+n,(byte)' ');remaining-=n;return n;}};
   String path=url.getPath();if(path.endsWith("/runtimes"))return stream(DshClient.mapOf("connectorId","c","runtimes",Arrays.asList(DshClient.mapOf("connectorId","c","runtimeId","r","runtimeType","dsh","status","running","available",true,"configured",true,"active",true))));
   if(path.endsWith("/state"))return stream(DshClient.mapOf("state",DshClient.mapOf("sessionId","s","runtimeId","r","status","idle")));
   return stream(DshClient.mapOf("sessionId","s","hasMore",true,"items",Collections.emptyList()));
  }
 }
 static void setup()throws Exception {
  app=new android.app.Activity();bridge=new AaHostBridge(app);app.getSharedPreferences("turboio_settings",0);prefs=app.values.get("turboio_settings");SecretStore.reset();DshSettings.auth=1;stage=fault=mutation="";requests=0;
  prefs.put("dsh",true);prefs.put("dsh_user_id","u");prefs.put("dsh_url",DshSettings.ORIGIN);prefs.put("dsh_connector","c");prefs.put("dsh_project","p");prefs.put("dsh_runtime","r");prefs.put("dsh_model",MODEL);prefs.put("dsh_permission",PERMISSION);prefs.put("dsh_session_id","s");prefs.put("dsh_client_message_id","m");prefs.put("dsh_unknown_delivery",true);prefs.put("dsh_target_fingerprint",new DshClient.Config(true,DshSettings.ORIGIN,"test-token","c","p","r",MODEL,PERMISSION).targetFingerprint());
 }
 static Bundle args(){Bundle snapshot=bridge.call("selection.snapshot",new Bundle());ok(snapshot.getBoolean("ok"),"fixture selection readable");Bundle b=new Bundle();b.putLong("expectedAuthGeneration",snapshot.getLong("authGeneration",-1));b.putString("expectedRevision",snapshot.getString("revision",""));b.putString("expectedSessionId","s");return b;}
 static Bundle query(){return bridge.call("dsh.status",args());}
 static void safe(Bundle out,String diagnostic){
  ok(!out.getBoolean("ok")&&out.getString("status","").equals("failed"),"exception remains failed");ok("REQUEST_FAILED".equals(out.getString("errorCode")),"exception keeps REQUEST_FAILED");ok(diagnostic.equals(out.getString("diagnosticCode","")),"safe diagnostic expected "+diagnostic+" got "+out.getString("diagnosticCode",""));
  int split=diagnostic.indexOf('/');ok(diagnostic.substring(0,split).equals(out.getString("diagnosticStage",""))&&diagnostic.substring(split+1).equals(out.getString("diagnosticReason","")),"structured safe fields agree");ok(out.getString("message","").contains("["+diagnostic+"]"),"AA nonblank top-level message shows diagnosis");ok(!out.toString().contains(PRIVATE)&&!out.toString().contains("test-token")&&!out.containsKey("json"),"exception bundle has no raw data/cause/body");ok("s".equals(prefs.get("dsh_session_id"))&&"m".equals(prefs.get("dsh_client_message_id"))&&Boolean.TRUE.equals(prefs.get("dsh_unknown_delivery")),"failure keeps pending and anchors");ok(!out.containsKey("sessionId")&&!out.containsKey("revision"),"exception does not supplement identity");
 }
 public static void main(String[] ignored)throws Exception {
  URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
  setup();Bundle out=query();ok(!out.getBoolean("ok")&&"unknown".equals(out.getString("status")),"legitimate unknown unchanged");ok(out.getString("message","").contains("clientMessageId")&&!out.containsKey("diagnosticCode"),"normal unknown preserves real pagination message, no exception diagnostic");
  setup();SecretStore.modelFailure=new java.security.GeneralSecurityException(PRIVATE);safe(query(),"model_secret/local_secret");ok(requests==0,"model secret failure sends zero HTTP");
  setup();SecretStore.modelFailure=new ArrayIndexOutOfBoundsException("timeout "+PRIVATE);safe(query(),"model_secret/unknown");ok(requests==0,"unknown model failure zero HTTP");
  setup();prefs.put("search",true);SecretStore.searchFailure=new java.security.GeneralSecurityException(PRIVATE);safe(query(),"tool_call/local_secret");ok(requests==0,"specs search secret failure zero HTTP");
  setup();prefs.put("search",true);SecretStore.searchFailure=new IllegalStateException("runtime_state/timeout "+PRIVATE);safe(query(),"tool_call/unknown");ok(requests==0,"specs opaque failure zero HTTP");
  for(String s:Arrays.asList("runtime_state","timeline"))for(String f:Arrays.asList("timeout","connect","http","unknown","json_invalid","response_limit")){setup();stage=s;fault=f;safe(query(),s+"/"+(f.equals("http")?"http_403":f));ok(requests==(s.equals("runtime_state")?2:3),"failure stops status requests at stage");}
  for(String change:Arrays.asList("auth","selection")){setup();stage="runtime_state";fault="timeout";mutation=change;out=query();ok((change.equals("auth")?"STALE_AUTH":"STALE_SELECTION").equals(out.getString("errorCode")),"fresh refusal overrides typed original failure");ok(!out.containsKey("diagnosticCode")&&out.getString("message","").isEmpty(),"stale owner gets no old diagnosis");}
  setup();SecretStore.onModel=()->DshSettings.auth++;SecretStore.modelFailure=new java.security.GeneralSecurityException(PRIVATE);out=query();ok("STALE_AUTH".equals(out.getString("errorCode"))&&!out.containsKey("diagnosticCode"),"fresh overrides model preflight error");
  setup();Bundle stale=args();stale.putLong("expectedAuthGeneration",0);SecretStore.modelFailure=new java.security.GeneralSecurityException(PRIVATE);out=bridge.call("dsh.status",stale);ok("STALE_AUTH".equals(out.getString("errorCode"))&&!out.containsKey("diagnosticCode")&&requests==0,"initial guard unchanged");
  setup();stage="";fault="";prefs.put("dsh",false);safe(query(),"tool_call/unknown");ok(requests==0,"tool disabled does not invent reason from message");
  setup();prefs.put("dsh_unknown_delivery",false);out=query();ok("unknown".equals(out.getString("status"))&&out.getString("message","").contains("clientMessageId")&&!out.containsKey("diagnosticCode"),"normal confirmed round unknown remains ordinary result");
  setup();prefs.put("dsh_unknown_delivery",false);DshAliasRegistry aliases=new DshAliasRegistry("u",DshSettings.ORIGIN);aliases.upsertSnapshot("desktop",DshSettings.ORIGIN,"c","r","other-project",MODEL,PERMISSION);app.getSharedPreferences(ToolClient.ALIAS_FILE,0).edit().putString(ToolClient.ALIAS_DOC,DshAliasRegistry.serializeSnapshot(aliases)).commit();Bundle explicit=args();explicit.putString("target","desktop");out=bridge.call("dsh.status",explicit);ok("SESSION_UNSELECTED".equals(out.getString("errorCode"))&&!out.containsKey("diagnosticCode")&&requests==0,"explicit alias route refusal remains code-only, no exception diagnostic");
  System.out.println("AaHostBridgeDiagnosticTest "+checks+" checks passed");
 }
}
