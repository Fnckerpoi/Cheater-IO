param(
 [string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [string]$JsonJar='C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $env:TEMP ('chat-retrieval-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $out | Out-Null
try {
$source=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'src/com/turboio/addon/TurboAddon.java')
$start=$source.IndexOf('    private static JSONObject message(')
$end=$source.IndexOf('    private static void archive(', $start)
if($start -lt 0 -or $end -lt 0){ throw 'request boundaries not found' }
$methods=$source.Substring($start,$end-$start).Replace('private static void request(', 'static void request(')
$prefix=@'
package com.turboio.addon;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
class ChatRequestHarness {
 static long generation=1; static boolean owns=true,done=false; static Object app=null; static HttpURLConnection connection;
 static java.util.concurrent.atomic.AtomicInteger modelRequests=new java.util.concurrent.atomic.AtomicInteger();
 static ToolRoundTrace TOOL_TRACE=new ToolRoundTrace();
 static class Handler { void post(Runnable r) { if(r!=null) r.run(); } } static Handler MAIN=new Handler();
 static List<String> emittedAnswers=new ArrayList<String>();
 static List<Boolean> emittedFinalFlags=new ArrayList<Boolean>();
 static List<String> chatLogs=new ArrayList<String>();
 static void chatLog(String kv) { if(kv!=null) chatLogs.add(kv); }
 static void emit(long t,String a,boolean f,String e) { emittedFinalFlags.add(Boolean.valueOf(f)); if(f && a!=null) emittedAnswers.add(a); }
 static boolean emittedDraft() { for(Boolean value:emittedFinalFlags) if(!value.booleanValue()) return true; return false; }
 static void recordError(Throwable t) {throw new RuntimeException(t);}
 static void reset() {
  generation=1; owns=true; done=false; connection=null; TOOL_TRACE.reset(generation);
  modelRequests.set(0);
  emittedAnswers.clear();
  emittedFinalFlags.clear();
  chatLogs.clear();
 }
'@
[IO.File]::WriteAllText((Join-Path $out 'ChatRequestHarness.java'), $prefix + $methods + [Environment]::NewLine + '}', [Text.UTF8Encoding]::new($false))
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $JsonJar -d $out (Join-Path $out 'ChatRequestHarness.java') (Join-Path $root 'src/com/turboio/addon/ChatPolicy.java') (Join-Path $root 'src/com/turboio/addon/ToolTurn.java') (Join-Path $root 'src/com/turboio/addon/TodoProtocol.java') (Join-Path $root 'src/com/turboio/addon/HostBusiness.java') (Join-Path $root 'src/com/turboio/addon/BoundedJson.java') (Join-Path $root 'src/com/turboio/addon/ToolRoundTrace.java') (Join-Path $root 'src/com/turboio/addon/KnowledgeRetrieval.java') (Join-Path $root 'src/com/turboio/addon/CortrixClient.java') (Join-Path $PSScriptRoot 'chatdoubles/ChatDoubles.java') (Join-Path $PSScriptRoot 'ChatRetrievalOrderTest.java')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$JavaHome/bin/java.exe" -cp "$out;$JsonJar" com.turboio.addon.ChatRetrievalOrderTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $out -ErrorAction SilentlyContinue
}
