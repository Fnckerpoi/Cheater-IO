param(
 [string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [string]$JsonJar='C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $env:TEMP ('toolroundtrace-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $out | Out-Null
try {
 & "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $JsonJar -d $out (Join-Path $root 'src/com/turboio/addon/ToolRoundTrace.java') (Join-Path $PSScriptRoot 'ToolRoundTraceTest.java')
 if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
 & "$JavaHome/bin/java.exe" -cp "$out;$JsonJar" com.turboio.addon.ToolRoundTraceTest
 if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $out -ErrorAction SilentlyContinue
}
