param(
 [Parameter(Mandatory=$true)][string]$JavaBin,
 [Parameter(Mandatory=$true)][string]$Tmg4Zip,
 [Parameter(Mandatory=$true)][string]$StockZip,
 [Parameter(Mandatory=$true)][string]$Tmg3Zip
)
$ErrorActionPreference='Stop'
$otaOut=Join-Path $env:TEMP ('turboio-tmg4-'+[guid]::NewGuid())
New-Item -ItemType Directory $otaOut | Out-Null
$sources=@('BoundedJson','OtaFrame','OtaPackage','OtaProfile','OfficialOtaAuthorization','OfficialOtaFeed','OfficialOtaGate','OfficialOtaPreflightPoll','OtaRecoveryGuard') | ForEach-Object {Join-Path $PSScriptRoot ('src/com/turboio/addon/'+$_+'.java')}
$tests=@('OfficialOtaAuthorizationTest','OfficialOtaPreflightPollTest','OtaRecoveryGuardTest','OtaProfileHistoryTest','OfficialOtaUnmeasuredProfileTest','Stock10611ProfileTest','Tmg4OtaProfileTest')
$testSources=$tests | ForEach-Object {Join-Path $PSScriptRoot ('tests/'+$_+'.java')}
& (Join-Path $JavaBin 'javac.exe') -encoding UTF-8 -d $otaOut @sources @testSources
if($LASTEXITCODE -ne 0){throw 'OTA javac failed'}
foreach($suite in $tests){
 $fixture=if($suite -eq 'Tmg4OtaProfileTest'){$Tmg4Zip}else{$StockZip}
 & (Join-Path $JavaBin 'java.exe') -cp $otaOut ('com.turboio.addon.'+$suite) $fixture $Tmg3Zip
 if($LASTEXITCODE -ne 0){throw ('Failed suite: '+$suite)}
}
Write-Output ('TMG4 OTA regressions passed; compiled classes: '+$otaOut)
