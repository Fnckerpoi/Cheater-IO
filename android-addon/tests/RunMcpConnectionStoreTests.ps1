[CmdletBinding()]
param([string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$classes=Join-Path $env:TEMP ('mcp-connection-store-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
try {
$fixture=Join-Path $PSScriptRoot 'mcpstorage'
$sources=@(Get-ChildItem -LiteralPath $fixture -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+=Join-Path $PSScriptRoot 'McpConnectionStoreTest.java'
$store=Join-Path $root 'src/com/turboio/addon/McpConnectionStore.java'
if(Test-Path -LiteralPath $store){$sources+=$store}
& (Join-Path $JavaHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Duser.country=US' --release 8 -Xlint:-options -encoding UTF-8 -sourcepath $fixture -d $classes @sources
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& (Join-Path $JavaHome 'bin/java.exe') -cp $classes com.turboio.addon.McpConnectionStoreTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
 Remove-Item -Recurse -Force -LiteralPath $classes -ErrorAction SilentlyContinue
}
