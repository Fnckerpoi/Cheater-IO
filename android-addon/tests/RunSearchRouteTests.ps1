[CmdletBinding()]
param(
    [string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
    [string]$JsonJar='C:/Users/duriea/.gradle/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$classes=Join-Path $env:TEMP ('search-route-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
try {
& (Join-Path $JavaHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Duser.country=US' --release 8 -Xlint:-options -encoding UTF-8 -cp $JsonJar -d $classes (Join-Path $root 'src/com/turboio/addon/SearchRoute.java') (Join-Path $PSScriptRoot 'SearchRouteTest.java')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& (Join-Path $JavaHome 'bin/java.exe') -cp "$classes;$JsonJar" com.turboio.addon.SearchRouteTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $classes -ErrorAction SilentlyContinue
}
