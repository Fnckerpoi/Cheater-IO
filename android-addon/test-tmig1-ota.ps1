param(
 [Parameter(Mandatory=$true)][string]$JavaBin,
 [Parameter(Mandatory=$true)][string]$TmigZip,
 [Parameter(Mandatory=$true)][string]$TapZip,
 [Parameter(Mandatory=$true)][string]$StockZip
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$otaOut=Join-Path $env:TEMP ('turboio-tmig1-'+[guid]::NewGuid())
New-Item -ItemType Directory $otaOut | Out-Null
$sources=@('BoundedJson','OtaFrame','OtaPackage','OtaProfile','OfficialOtaFeed','OfficialOtaGate','OfficialOtaPreflightPoll','OtaRecoveryGuard','OtaSession') | ForEach-Object {Join-Path $PSScriptRoot ('src/com/turboio/addon/'+$_+'.java')}
$tests=@('OtaTest','OfficialOtaFeedTest','OfficialOtaGateTest','OfficialOtaDirectoryTest','OfficialOtaPreflightPollTest','OtaRecoveryGuardTest','Tmig1OtaFeedTest','Tmig1OtaGateTest','Tmig1OtaDirectoryTest','Tmig1OtaIsolationTest','Tmig1OtaSnapshotTest','Tmig1OtaDirectoryRaceTest')
$testSources=$tests | ForEach-Object {Join-Path $PSScriptRoot ('tests/'+$_+'.java')}
& (Join-Path $JavaBin 'javac.exe') -encoding UTF-8 -d $otaOut @sources @testSources
if($LASTEXITCODE -ne 0){throw 'OTA javac failed'}
$failed=@()
foreach($suite in $tests){
 $fixture=if($suite.StartsWith('Tmig1')){$TmigZip}else{$TapZip}
 & (Join-Path $JavaBin 'java.exe') -cp $otaOut ('com.turboio.addon.'+$suite) $fixture $TapZip $StockZip
 if($LASTEXITCODE -ne 0){$failed+=$suite}
}
Write-Output ('Compiled classes: '+$otaOut)
if($failed.Count){throw ('Failed suites: '+($failed -join ', '))}
