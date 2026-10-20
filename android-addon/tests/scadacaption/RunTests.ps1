param([Parameter(Mandatory=$true)][string]$JavaBin)
$ErrorActionPreference = 'Stop'
$addon = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$output = Join-Path $env:TEMP ('turboio-scada-caption-' + [guid]::NewGuid())
$source = Join-Path $addon 'src/com/turboio/addon'
$tests = Join-Path $addon 'tests'
$javac = Join-Path $JavaBin 'javac.exe'
$java = Join-Path $JavaBin 'java.exe'
function Run-Group([string]$Group, [string[]]$Sources, [string[]]$Classes) {
    $destination = Join-Path $output $Group
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    & $javac -encoding UTF-8 -source 8 -target 8 -d $destination @Sources
    if ($LASTEXITCODE -ne 0) { throw "$Group compilation failed" }
    foreach ($class in $Classes) {
        & $java -cp $destination $class
        if ($LASTEXITCODE -ne 0) { throw "$class failed" }
    }
}
$sensor = @('ScadaReading','ScadaPacketCodec','ScadaReplayGuard','ScadaProximity','ScadaReplayHistory','ScadaSampleTime') | ForEach-Object { Join-Path $source ($_ + '.java') }
$sensor += @(Get-ChildItem -LiteralPath (Join-Path $tests 'scadable') -Filter '*.java' | ForEach-Object FullName)
Run-Group 'proximity' $sensor @('com.turboio.addon.ScadaPacketCodecTest','com.turboio.addon.ScadaReplayGuardTest','com.turboio.addon.ScadaProximityTest','com.turboio.addon.ScadaDurabilityTimeTest')
$caption = @('BoundedJson','HostBusiness','NavCore','NavSessionPolicy','NavGlasses','CaptionEventQueue','ScadaCaptionController','ScadaProximity','ScadaReading') | ForEach-Object { Join-Path $source ($_ + '.java') }
$caption += @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
$caption += @(Join-Path $tests 'otaflow/Looper.java'; Join-Path $tests 'scadacard/SystemClock.java'; Join-Path $tests 'NavCoreTest.java'; Join-Path $tests 'NavSessionPolicyTest.java')
Run-Group 'caption' $caption @('com.turboio.addon.ScadaCaptionControllerTest','NavCoreTest','NavSessionPolicyTest')
Write-Output "Focused SCADA tests passed; temporary classes: $output"
$controllerText = Get-Content -Raw -Encoding UTF8 (Join-Path $source 'ScadaSensorController.java')
if (-not $controllerText.Contains('proximity.accept(reading, ScadaSampleTime.captureMillis(capturedAtNanos), SystemClock.elapsedRealtime())')) { throw 'Sensor consumer must convert BLE nanos to proximity milliseconds' }
if (-not $controllerText.Contains('config.lastPresentedAtNanos')) { throw 'Sensor main consumer must recheck queued capture time' }
Write-Output 'Sensor timestamp boundary wiring checks passed'
if ($controllerText.Contains('ScadaCardController')) { throw 'SCADA scanning must submit captions only' }
if (-not $controllerText.Contains('putBoolean(ScadaReplayHistory.marker(fingerprint), true).commit()')) { throw 'Replay initialization and counter must commit together' }
$cloudText=Get-Content -Raw -Encoding UTF8 (Join-Path $source 'CloudCaption.java')
if (-not $cloudText.Contains('new CaptionEventQueue(()->epoch==ticket&&active') -or -not $cloudText.Contains('BoundedWebSocket socket=connection[0]') -or -not $cloudText.Contains('if(!events.acquire())return;') -or $cloudText.Contains('queuedEvents')) { throw 'Cloud socket must use connection-bound queue and close callback' }
Write-Output 'Caption-only, replay initialization and cloud queue wiring checks passed'
