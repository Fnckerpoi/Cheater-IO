param(
 [string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [string]$AndroidJar='C:/Users/duriea/.gradle/caches/8.10.2/transforms/f208f54ff59bf5cdfdd91105347e65b7/transformed/android.jar',
 [string]$JsonJar='C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $env:TEMP ('hint-coordinator-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out -Force | Out-Null
$classes=$out
try {
$sources=@('ProactiveHintAugmentor','ProactiveHintRegenerator','CortrixClient','KnowledgeRetrieval','McpConnectionStore','SecretStore','ChatPolicy') | ForEach-Object {Join-Path $root "src/com/turboio/addon/$_.java"}
$tests=@((Join-Path $PSScriptRoot 'ProactiveHintAugmentorTest.java'), (Join-Path $PSScriptRoot 'HintCoordinatorTest.java'))

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
$dispatchBlock = $addonSource.Substring($dispatchStart, $dispatchEnd - $dispatchStart).Replace('synchronized(TurboAddon.class)', 'synchronized(ProactiveHintHookHarness.class)')
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
 }
'@

$dispatchAdjusted = $dispatchBlock.Replace('final McpConnectionStore.Snapshot config;', 'final com.turboio.addon.McpConnectionStore.Snapshot config;')
[IO.File]::WriteAllText((Join-Path $classes 'ProactiveHintHookHarness.java'), $harnessPrefix + $serialBlock + $dispatchAdjusted + $invokeBlock + [Environment]::NewLine + '}', [Text.UTF8Encoding]::new($false))

$tests += Join-Path $out 'ProactiveHintHookHarness.java'
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$JsonJar;$AndroidJar" -d $out @sources @tests
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$JavaHome/bin/java.exe" --add-modules jdk.httpserver -cp "$out;$JsonJar;$AndroidJar" com.turboio.addon.ProactiveHintAugmentorTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$JavaHome/bin/java.exe" --add-modules jdk.httpserver -cp "$out;$JsonJar;$AndroidJar" com.turboio.addon.HintCoordinatorTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $out -ErrorAction SilentlyContinue
}
