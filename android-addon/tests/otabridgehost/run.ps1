[CmdletBinding()]
param(
 [Parameter(Mandatory)][string]$JavaBin,
 [Parameter(Mandatory)][string]$AndroidJar,
 [Parameter(Mandatory)][string]$Output
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$javac = Join-Path $JavaBin 'javac.exe'
$java = Join-Path $JavaBin 'java.exe'
foreach ($inputPath in @($javac, $java, $AndroidJar)) {
 if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) { throw "Missing input: $inputPath" }
}
$AndroidJar = (Resolve-Path -LiteralPath $AndroidJar).Path
$Output = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path $Output | Out-Null
# Each invocation uses a fresh class directory; previous build classes never enter the classpath.
$classes = Join-Path $Output ('classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null
$fixtureSources = @(Get-ChildItem (Join-Path $PSScriptRoot 'fixture') -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { $_.FullName })
$fixtureNames = @($fixtureSources | ForEach-Object { [IO.Path]::GetFileName($_) })
$sources = @(Get-ChildItem (Join-Path $root 'android-addon/src') -Recurse -Filter '*.java' | Where-Object { $_.Name -notin $fixtureNames } | Sort-Object FullName | ForEach-Object { $_.FullName }) + $fixtureSources
@{ compiler=$javac; androidJar=$AndroidJar; sources=$sources; classes=$classes } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $Output 'command.json')
$ErrorActionPreference = 'Continue'
& $javac -encoding UTF-8 -source 8 -target 8 -classpath $AndroidJar -d $classes @sources 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -Variable compileOutput
$compileOutput | Set-Content -Encoding UTF8 (Join-Path $Output 'compile.log')
$compileCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Write-Output "COMPILE_EXIT=$compileCode"
if ($compileCode -ne 0) { exit $compileCode }
$ErrorActionPreference = 'Continue'
& $java -cp ($classes + [IO.Path]::PathSeparator + $AndroidJar) com.turboio.addon.BridgeFixtureMain 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -Variable testOutput
$testCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
$testOutput | Set-Content -Encoding UTF8 (Join-Path $Output 'test.log')
Write-Output "TEST_EXIT=$testCode"
exit $testCode
