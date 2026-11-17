[CmdletBinding()]
param(
    [string]$JavaHome = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
    [string]$AndroidJar = 'C:/Users/duriea/.gradle/caches/8.10.2/transforms/f208f54ff59bf5cdfdd91105347e65b7/transformed/android.jar',
    [string]$JsonJar = 'C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path $PSScriptRoot -Parent
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'

foreach ($p in @($javac, $java, $AndroidJar, $JsonJar)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Missing required tool/jar: $p" }
}

$suites = @('CortrixClientTest', 'ProactiveHintAugmentorTest')
$sources = @('CortrixClient', 'ProactiveHintAugmentor', 'ProactiveHintRegenerator', 'KnowledgeRetrieval', 'McpConnectionStore', 'SecretStore', 'ChatPolicy') | ForEach-Object { Join-Path $root "src/com/turboio/addon/$_.java" }
$testSources = @('CortrixClientTest', 'ProactiveHintAugmentorTest', 'HintCoordinatorTest') | ForEach-Object { Join-Path $PSScriptRoot "$_.java" }

$classes = Join-Path $env:TEMP ('proactive-hint-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null

try {

$addonSource = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'src/com/turboio/addon/TurboAddon.java')
$serialStart = $addonSource.IndexOf('    private static void serial(Runnable work) {')
$dispatchStart = $addonSource.IndexOf('    private static volatile long hintSequence;')
$dispatchEnd = $addonSource.IndexOf('    private static void observeSpeech(', $dispatchStart)
$invokeStart = $addonSource.IndexOf('    private static void invoke(Object target,')
$invokeEnd = $addonSource.IndexOf('    private static void emit(long token,', $invokeStart)
if ($serialStart -lt 0 -or $dispatchStart -lt 0 -or $dispatchEnd -lt 0 -or $invokeStart -lt 0 -or $invokeEnd -lt 0) {
    throw 'ProactiveHintHookHarness boundaries not found in TurboAddon.java'
}
$serialBlock = $addonSource.Substring($serialStart, $addonSource.IndexOf('    public static void dispatchAsr(', $serialStart) - $serialStart)
$dispatchBlock = $addonSource.Substring($dispatchStart, $dispatchEnd - $dispatchStart).Replace('synchronized(TurboAddon.class)', 'synchronized(ProactiveHintHookHarness.class)').Replace('TurboAddon::chatLog', 'ProactiveHintHookHarness::chatLog')
$invokeBlock = $addonSource.Substring($invokeStart, $invokeEnd - $invokeStart)

$harnessPrefix = @'
package com.turboio.addon;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
final class ProactiveHintHookHarness {
 static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();
 static final class Looper {
  static final Looper MAIN_LOOPER = new Looper();
  static Looper current = MAIN_LOOPER;
  static Looper getMainLooper() { return MAIN_LOOPER; }
  static Looper myLooper() { return current; }
 }
 static final class Handler {
  final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
  final List<Runnable> delayed = Collections.synchronizedList(new ArrayList<Runnable>());
  void post(Runnable r) { queue.add(r); }
  void postDelayed(Runnable r, long ms) { delayed.add(r); }
  void removeCallbacks(Runnable r) { delayed.remove(r); }
  void drain() {
   Looper prev = Looper.current;
   Looper.current = Looper.MAIN_LOOPER;
   try {
    Runnable r;
    while ((r = queue.poll()) != null) r.run();
   } finally {
    Looper.current = prev;
   }
  }
 }
 static final Handler MAIN = new Handler();
 static ExecutorService NETWORK = Executors.newFixedThreadPool(2);
 static final Object app = new Object();
 static volatile long generation = 1;
 static volatile String diagnostic = "";
 static String endpoint = "https://api.deepseek.com/v1/";
 static String model = "deepseek-flash";
 static String secretKey = "";
 static com.turboio.addon.McpConnectionStore.Snapshot mcpConfig;
 static RuntimeException snapshotFailure;
 static RuntimeException secretFailure;
 static final class Prefs {
  String getString(String k, String d) {
   if ("endpoint".equals(k)) return endpoint != null ? endpoint : d;
   if ("model".equals(k)) return model != null ? model : d;
   return d;
  }
 }
 static Prefs prefs = new Prefs();
 static final List<String> chatLines = Collections.synchronizedList(new ArrayList<String>());
 static void chatLog(String kv) { chatLines.add(kv); }
 static final class McpConnectionStore {
  static com.turboio.addon.McpConnectionStore.Snapshot snapshot(Object a) {
   if (snapshotFailure != null) throw snapshotFailure;
   return mcpConfig;
  }
 }
 static final class SecretStore {
  static String get(Object a) throws Exception {
   if (secretFailure != null) throw secretFailure;
   return secretKey;
  }
 }
 static void reset() {
  MAIN.queue.clear();
  MAIN.delayed.clear();
  diagnostic = "";
  generation = 1;
  hintSequence = 0;
  hintAugmentor = null;
  snapshotFailure = null;
  secretFailure = null;
  chatLines.clear();
  com.turboio.addon.CortrixClient.resetSessions();
 }
'@

$dispatchAdjusted = $dispatchBlock.Replace('final McpConnectionStore.Snapshot config;', 'final com.turboio.addon.McpConnectionStore.Snapshot config;')
[IO.File]::WriteAllText((Join-Path $classes 'ProactiveHintHookHarness.java'), $harnessPrefix + $serialBlock + $dispatchAdjusted + $invokeBlock + [Environment]::NewLine + '}', [Text.UTF8Encoding]::new($false))

    $testSources += Join-Path $classes 'ProactiveHintHookHarness.java'
    Write-Output "COMPILING TEST SUITES..."
    & $javac -encoding UTF-8 -source 8 -target 8 -cp "$JsonJar;$AndroidJar" -sourcepath (Join-Path $root 'src') -d $classes @sources @testSources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Output "RUNNING SUITE: McpConnectionStoreTest"
    & (Join-Path $PSScriptRoot 'RunMcpConnectionStoreTests.ps1') -JavaHome $JavaHome
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    foreach ($suite in $suites) {
        Write-Output "RUNNING SUITE: $suite"
        & $java --add-modules jdk.httpserver -cp "$classes;$JsonJar;$AndroidJar" "com.turboio.addon.$suite" "$root/src/com/turboio/addon/McpSettings.java"
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    Write-Output "RUNNING SUITE: ChatRetrievalOrderTest"
    & (Join-Path $PSScriptRoot 'RunChatRetrievalOrderTests.ps1') -JavaHome $JavaHome -JsonJar $JsonJar
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Output "ALL PROACTIVE HINT SUITES PASS"
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $classes -ErrorAction SilentlyContinue
}
