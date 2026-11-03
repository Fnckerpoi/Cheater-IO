param([Parameter(Mandatory=$true)][string]$AndroidJar,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$java=Join-Path $JavaHome 'bin/java.exe';$javac=Join-Path $JavaHome 'bin/javac.exe'
$AndroidJar=(Resolve-Path $AndroidJar).Path
$root=Join-Path ([System.IO.Path]::GetTempPath()) ('dsh-proactive-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $root | Out-Null
function RunSuite($name,$production,$fakes,$test){
 $classes=Join-Path $root $name;New-Item -ItemType Directory $classes|Out-Null
 $sources=@($production|ForEach-Object{Join-Path $repo ('android-addon/src/com/turboio/addon/'+$_+'.java')})+@($fakes|ForEach-Object{Join-Path $repo $_})
 & $javac -encoding UTF-8 -source 8 -target 8 -cp $AndroidJar -d $classes @sources
 if($LASTEXITCODE -ne 0){throw "$name compilation failed ($LASTEXITCODE)"}
 & $java -cp "$classes;$AndroidJar" ('com.turboio.addon.'+$test)
 if($LASTEXITCODE -ne 0){throw "$name execution failed ($LASTEXITCODE)"}
}
$round=Get-ChildItem (Join-Path $PSScriptRoot 'proactiveround/*.java')|ForEach-Object{'android-addon/tests/proactiveround/'+$_.Name}
$display=@('android-addon/tests/proactivedisplay/ToolClient.java','android-addon/tests/proactivedisplay/DshProactiveDisplayTest.java','android-addon/tests/scadacaption/Handler.java','android-addon/tests/otaflow/Looper.java','android-addon/tests/scadacard/SystemClock.java','android-addon/tests/scadacaption/NavReflect.java','android-addon/tests/scadacaption/JSONObject.java','android-addon/tests/scadacaption/HostSymbols.java')
# Platform doubles shared by the host-level suites. The caption NavReflect double carries its own
# minimal producer stubs, so the workbench card publisher is faked in each suite's HostFakes instead.
$plugs=@('android-addon/tests/scadacaption/Handler.java','android-addon/tests/otaflow/Looper.java','android-addon/tests/scadacard/SystemClock.java','android-addon/tests/scadacaption/NavReflect.java','android-addon/tests/scadacaption/HostSymbols.java')
# Pure composite model, board and preference suites: no Android runtime, injected clocks.
RunSuite 'hud' @('DshProactiveReport','DshDashboardModel','DshCompositeHudModel') @('android-addon/tests/DshCompositeHudModelTest.java') 'DshCompositeHudModelTest'
RunSuite 'hud-settings' @('DshCompositeHudSettings') @('android-addon/tests/DshCompositeHudSettingsTest.java') 'DshCompositeHudSettingsTest'
RunSuite 'board' @('DshProactiveReport','DshDashboardModel') @('android-addon/tests/DshDashboardModelTest.java','android-addon/tests/DshProactiveReportTest.java') 'DshDashboardModelTest'
# Durable round identity and ticket freshness on this branch's host (ToolClient) with fake prefs.
# DshProactiveDisplay here is the recording fake in proactiveround/HostFakes, never the production lease owner.
# The merged host notifies the launcher card and the adoption poller, so the real NavGlasses interlock
# doubles and the DshWorkbenchCardSync recording fake from proactiveround/HostFakes both join this unit.
RunSuite 'round' @('DshClient','DshProactiveReport','ToolClient','DshSessionAdopter','DshAnchorStore','DshDashboardModel','DshCompositeHudModel','DshCompositeHudSettings','DshAliasRegistry','DshDirectory','BoundedJson','NavCore','NavGlasses','NavSessionPolicy','HostBusiness') ($round+$plugs) 'ToolClientHudRoundTest'
# The single display coordinator, including the HUD suspend/resume matrix and ASR sharing one lease.
RunSuite 'display' @('DshClient','DshProactiveReport','DshProactiveDisplay','DshDashboardModel','DshCompositeHudModel','DshCompositeHudSettings','NavGlasses','NavCore','NavSessionPolicy','HostBusiness','BoundedJson') $display 'DshProactiveDisplayTest'
# Desktop-submitted rounds are discovered from the bound session timeline and adopted into the one monitor.
$flowFakes=@('android-addon/tests/proactiveflow/HostFakes.java','android-addon/tests/proactiveflow/ToolClientDisplayFlowTest.java','android-addon/tests/proactiveround/Context.java','android-addon/tests/proactiveround/JSONObject.java','android-addon/tests/proactiveround/JSONArray.java')
$flowFakes+=@($plugs)
$adoptFakes=@('android-addon/tests/proactiveround/Context.java','android-addon/tests/proactiveround/JSONObject.java','android-addon/tests/proactiveround/JSONArray.java','android-addon/tests/proactiveadopt/HostFakes.java','android-addon/tests/proactiveadopt/DshSessionAdopterTest.java')
$adoptFakes+=@($plugs)
RunSuite 'flow' @('ToolClient','DshAliasRegistry','DshDirectory','DshClient','DshProactiveReport','DshSessionAdopter','DshProactiveDisplay','DshDashboardModel','DshCompositeHudModel','DshCompositeHudSettings','DshAnchorStore','NavGlasses','NavCore','NavSessionPolicy','HostBusiness','BoundedJson') $flowFakes 'ToolClientDisplayFlowTest'
RunSuite 'adopt' @('DshClient','DshProactiveReport','ToolClient','DshProactiveDisplay','DshDashboardModel','DshCompositeHudModel','DshCompositeHudSettings','DshSessionAdopter','DshAliasRegistry','DshDirectory','BoundedJson','DshAnchorStore','NavGlasses','NavCore','NavSessionPolicy','HostBusiness') $adoptFakes 'DshSessionAdopterTest'
Write-Output ('Compiled test outputs: '+$root)

# Static wiring assertions: the ASR seam and the settings switch must stay on their real call sites.
$addonSrc=Join-Path $repo 'android-addon/src/com/turboio/addon'
$asrText=Get-Content -Raw -Encoding UTF8 (Join-Path $addonSrc 'TurboAddon.java')
if(-not $asrText.Contains('DshProactiveDisplay display=DshProactiveDisplay.existing(); if(display!=null)display.onAsr(text,finished,session);')){throw 'ASR dispatch must forward to the existing HUD display only'}
# The pre-existing diagnostic dialog may only read NavGlasses connection/phase text; it must never hold
# or write a session, because ASR alone must not be able to open the lens channel.
$lensCalls=@([regex]::Matches($asrText,'NavGlasses\.([A-Za-z0-9_]+)')|ForEach-Object{$_.Groups[1].Value})
foreach($call in $lensCalls){if($call -notin @('connection','status','phase','active')){throw "TurboAddon must not drive the lens channel from ASR (found NavGlasses.$call)"}}
if(-not $asrText.Contains('invokeTyped(source,"onAsrResult"')){throw 'official ASR callback must keep its original position in the serial chain'}
$asrOrder=$asrText.IndexOf('VoiceSpeech.asr(source,session,finished)');$forwardOrder=$asrText.IndexOf('display.onAsr(text,finished,session)')
if($asrOrder -lt 0 -or $forwardOrder -lt $asrOrder){throw 'HUD forwarding must happen after the official ASR handling on the same serial chain'}
$hostText=Get-Content -Raw -Encoding UTF8 (Join-Path $addonSrc 'ToolClient.java')
if($hostText -notmatch 'DshProactiveReport\.RoundIdentity\s+round\s*=\s*snapshotRound\(\)'){throw 'only a durably submitted round may arm the HUD monitor'}
if(-not $hostText.Contains('display.setHudEnabled')){throw 'the host must hand the persisted switch to the display coordinator'}
# Every durable selection writer must retire round tickets under the same host lock.
foreach($writer in @('DshSettings.java','AaHostBridge.java','ToolClient.java')){
 if(([regex]::Matches((Get-Content -Raw -Encoding UTF8 (Join-Path $addonSrc $writer)),'ToolClient\.selectionChanged\(\)|(?<!\.)\bselectionChanged\(\)')).Count -lt 1){
  throw "$writer changes durable selection state but never advances the selection generation"
 }
}
$settingsText=Get-Content -Raw -Encoding UTF8 (Join-Path $addonSrc 'DshSettings.java')
if(-not $settingsText.Contains('DshCompositeHudSettings.setEnabled')){throw 'the AA settings page must persist the composite switch through the shared settings model'}
if(-not $settingsText.Contains('setHudEnabled')){throw 'the switch must command the display coordinator with generation naming'}
if(([regex]::Matches($settingsText,'compositeHudSwitch\s*=\s*new Switch\(a\)')).Count -ne 1){throw 'the AA settings page must own exactly one combined HUD switch'}
# The phone keeps authority: the page follows the monitor, can stop only local tracking, and can release
# only its own lens lease through the confirmation flow. Card reads stay bound to one card identity.
if(-not $settingsText.Contains('DshProactiveDisplay.get(app).subscribe(proactiveListener)')){throw 'the AA settings page must follow the single display coordinator'}
if(-not $settingsText.Contains('DshProactiveDisplay.get(app).unsubscribe(proactiveListener)')){throw 'a closed settings page must stop following the coordinator'}
if(-not $settingsText.Contains('ToolClient.pauseAdoption')){throw 'stopping local monitoring must also pause desktop-round adoption'}
if(-not $settingsText.Contains('ToolClient.resumeAdoption')){throw 'looking at the card again must re-arm desktop-round adoption'}
if(-not $settingsText.Contains('confirmGlassesClosed')){throw 'the page may release only a coordinator-owned lens lease through user confirmation'}
if(([regex]::Matches($settingsText,'client\.call\("dsh_status",client\.statusArguments\(\),modelSecret\(\),request::guard,request::committed\)')).Count -ne 2){throw 'both card status reads must carry the binding alias under one card identity guard'}
Write-Output 'HUD wiring assertions passed'
