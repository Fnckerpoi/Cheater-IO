[CmdletBinding()]
param(
 [string]$JavaHome = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [Parameter(Mandatory)][string]$AndroidSdk
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$classes = Join-Path $env:TEMP ('dsh-status-diagnostics-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -sourcepath (Join-Path $root 'src') -d $classes (Join-Path $root 'src/com/turboio/addon/DshSettings.java') (Join-Path $PSScriptRoot 'DshStatusDiagnosticTest.java') (Join-Path $PSScriptRoot 'DshSettingsPolicyTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($suite in @('DshStatusDiagnosticTest','DshSettingsPolicyTest')) {
 & $java -cp "$classes;$androidJar" "com.turboio.addon.$suite"
 if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Write-Output "CLASSES=$classes"
