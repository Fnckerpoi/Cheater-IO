[CmdletBinding()]
param(
 [string]$JavaHome = 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr',
 [Parameter(Mandatory)][string]$AndroidSdk,
 [switch]$IncludeProactive
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
foreach ($inputPath in @($androidJar, $javac, $java)) {
 if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) { throw "Missing input: $inputPath" }
}
$suites = @('DshAliasRegistryTest','AaBridgePolicyTest','AaResultIdentityTest','AaDispatchFlowTest','DshClientTest','DshAuthTest','DshDirectoryTest','DshDraftContractTest','DshSelectionDraftTest','DshSessionSelectionTest','DshSettingsPolicyTest','DshStatusDiagnosticTest')
# Pure composite HUD suites: models and the one persisted switch need no Android classes at all.
$hudSuites = @('DshDashboardModelTest','DshCompositeHudModelTest','DshCompositeHudSettingsTest')
# Parentheses matter: without them '+' binds tighter than '+=' and concatenates a string instead.
if ($IncludeProactive) { $suites += @('DshProactiveReportTest') + $hudSuites }
$sources = @('DshAliasRegistry','ToolClient','DshSettings','TurboAddon') | ForEach-Object { Join-Path $root "src/com/turboio/addon/$_.java" }
$sources += @('DshProactiveReport','DshDashboardModel','DshCompositeHudModel','DshCompositeHudSettings') | ForEach-Object { Join-Path $root "src/com/turboio/addon/$_.java" }
$sources += $suites | ForEach-Object { Join-Path $PSScriptRoot "$_.java" }
foreach ($source in $sources) {
 if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing source: $source" }
}
$classes = Join-Path $env:TEMP ('turboio-dsh-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -sourcepath (Join-Path $root 'src') -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($suite in $suites) {
 Write-Output "SUITE=$suite"
 & $java -cp "$classes;$androidJar" "com.turboio.addon.$suite"
 if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Write-Output "CLASSES=$classes"
Write-Output "SUITES=$($suites.Count) PASS"
