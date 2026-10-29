[CmdletBinding()]
param(
 [Parameter(Mandatory)][string]$PayloadApk,
 [Parameter(Mandatory)][string]$PayloadSidecar,
 [Parameter(Mandatory)][string]$OutputDirectory,
 [string]$JavaHome = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [string]$Python = 'python',
 [string]$SourceApk = ''
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Quote-NativeArgument([string]$Value) {
 if ($null -eq $Value) { return '""' }
 $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
 $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
 return '"' + $escaped + '"'
}
function Run([string]$Executable,[string[]]$Arguments) {
 $info = New-Object System.Diagnostics.ProcessStartInfo
 $info.FileName = $Executable
 $info.UseShellExecute = $false
 $info.Arguments = (($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
 $child = New-Object System.Diagnostics.Process
 $child.StartInfo = $info
 try {
  if (-not $child.Start()) { throw "Process did not start: $Executable" }
  Write-Output "STARTED pid=$($child.Id) executable=$Executable"
  $child.WaitForExit()
  $code = $child.ExitCode
  Write-Output "EXIT=$code executable=$Executable"
  if ($code -ne 0) { throw "Build command failed: $Executable ($code)" }
 } finally { $child.Dispose() }
}
if ([string]::IsNullOrWhiteSpace($SourceApk)) {
 $SourceApk = Join-Path $PSScriptRoot '../artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Use a new output directory'}
$Work=[IO.Path]::GetFullPath($OutputDirectory)
$Sdk=[IO.Path]::GetFullPath("$PSScriptRoot/../artifacts/tools/android-sdk")
$AndroidJar=Join-Path $Sdk 'platforms/android-36/android.jar'
$BuildTools=Join-Path $Sdk 'build-tools/36.0.0'
foreach($inputFile in @($PayloadApk,$PayloadSidecar,$SourceApk,$AndroidJar,(Join-Path $JavaHome 'bin/javac.exe'))){if(-not(Test-Path -LiteralPath $inputFile -PathType Leaf)){throw "Missing input: $inputFile"}}
New-Item -ItemType Directory -Path $Work | Out-Null
$Classes=New-Item -ItemType Directory -Path (Join-Path $Work 'classes')
$Dex=New-Item -ItemType Directory -Path (Join-Path $Work 'dex')
$Sources=@(Get-ChildItem "$PSScriptRoot/src/com/turboio/addon" -Filter '*.java' -File | Sort-Object Name | ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'})
$SourceList=Join-Path $Work 'sources.txt'
[IO.File]::WriteAllLines($SourceList,[string[]]$Sources,[Text.UTF8Encoding]::new($false))
Run (Join-Path $JavaHome 'bin/javac.exe') @('-encoding','UTF-8','-source','8','-target','8','-cp',$AndroidJar,'-d',$Classes.FullName,"@$SourceList")
$Jar=Join-Path $Work 'addon.jar'
Run $Python @((Join-Path $PSScriptRoot 'tmig1_windows.py'),'jar',$Classes.FullName,$Jar)
Run (Join-Path $JavaHome 'bin/java.exe') @('-cp',(Join-Path $BuildTools 'lib/d8.jar'),'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','29','--output',$Dex.FullName,$Jar)
$Unsigned=Join-Path $Work 'TurboIO-AA-embedded-unsigned.apk'
Run $Python @((Join-Path $PSScriptRoot 'package-embedded.py'),$SourceApk,(Join-Path $Dex.FullName 'classes.dex'),$PayloadApk,$PayloadSidecar,$Unsigned)
$Aligned=Join-Path $Work 'TurboIO-AA-embedded-aligned-unsigned.apk'
Run (Join-Path $BuildTools 'zipalign.exe') @('-P','16','-f','4',$Unsigned,$Aligned)
Run (Join-Path $BuildTools 'zipalign.exe') @('-c','-P','16','4',$Aligned)
Write-Output "Aligned unsigned candidate: $Aligned. Signing and installation are separate operations."
