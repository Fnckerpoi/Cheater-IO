param([Parameter(Mandatory=$true)][string]$JavaBin)
$ErrorActionPreference="Stop"
$addon=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$out=Join-Path $env:TEMP ("turboio-workbench-card-"+[guid]::NewGuid())
$source=Join-Path $addon "src/com/turboio/addon"
$test=$PSScriptRoot
$files=@("BoundedJson.java","CardCodec.java","CardEnvelope.java","CardTransport.java","DshWorkbenchCardModel.java","DshWorkbenchCardSync.java")|ForEach-Object{Join-Path $source $_}
$files+=@(Join-Path $test "SystemClock.java";Join-Path $test "NativeTransfer.java";Join-Path $test "CardIcons.java";Join-Path $test "DshWorkbenchCardTest.java")
New-Item -ItemType Directory -Path $out -Force|Out-Null
& (Join-Path $JavaBin "javac.exe") -encoding UTF-8 -source 8 -target 8 -d $out @files
if($LASTEXITCODE -ne 0){throw "workbench card compilation failed"}
& (Join-Path $JavaBin "java.exe") -cp $out com.turboio.addon.DshWorkbenchCardTest
if($LASTEXITCODE -ne 0){throw "workbench card regression failed"}
Write-Output "Workbench card regression passed; temporary classes: $out"
