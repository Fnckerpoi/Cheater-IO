param(
 [string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [string]$AndroidJar='C:/Users/duriea/.gradle/caches/8.10.2/transforms/f208f54ff59bf5cdfdd91105347e65b7/transformed/android.jar',
 [string]$JsonJar='C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$classes=Join-Path $env:TEMP ('cortrix-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
try {
$cp="$JsonJar;$AndroidJar"
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -cp $cp -sourcepath "$root/src" -d $classes "$root/src/com/turboio/addon/CortrixClient.java" "$PSScriptRoot/CortrixClientTest.java"
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$JavaHome/bin/java.exe" -cp "$classes;$cp" com.turboio.addon.CortrixClientTest "$root/src/com/turboio/addon/McpSettings.java"
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $classes -ErrorAction SilentlyContinue
}
