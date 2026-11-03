[CmdletBinding()]
param([string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',[Parameter(Mandatory)][string]$AndroidSdk)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$androidJar=Join-Path $AndroidSdk 'platforms/android-36/android.jar'
$javac=Join-Path $JavaHome 'bin/javac.exe';$java=Join-Path $JavaHome 'bin/java.exe'
$classes=Join-Path $env:TEMP ('aa-bridge-diagnostic-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $classes|Out-Null
# ToolClient reaches the adoption host, so the adopter compiles alongside it here.
# ToolClient reaches the adoption host and the persisted composite switch, so both compile alongside it here.
$production=@('AaHostBridge','AaBridgePolicy','ToolClient','DshClient','DshAliasRegistry','DshDirectory','DshSelectionDraft','DshProactiveReport','DshSessionAdopter','DshCompositeHudSettings','BoundedJson')|ForEach-Object{Join-Path $root ('src/com/turboio/addon/'+$_+'.java')}
$fakes=@(Get-ChildItem (Join-Path $PSScriptRoot 'bridgediagnostic/*.java')|ForEach-Object{$_.FullName})
$fakes+=@('proactiveround/Context.java','proactiveround/JSONArray.java')|ForEach-Object{Join-Path $PSScriptRoot $_}
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -d $classes @production @fakes
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& $java -cp "$classes;$androidJar" com.turboio.addon.AaHostBridgeDiagnosticTest
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$real=Join-Path $classes 'production';New-Item -ItemType Directory $real|Out-Null
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -sourcepath (Join-Path $root 'src') -d $real (Join-Path $root 'src/com/turboio/addon/AaHostBridge.java')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Write-Output "PRODUCTION BRIDGE COMPILE PASS; CLASSES=$classes"
