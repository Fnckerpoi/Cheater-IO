[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$AndroidJar,
    [Parameter(Mandatory)][string]$BuildTools,
    [Parameter(Mandatory)][string]$ApktoolJar,
    [Parameter(Mandatory)][string]$Python,
    [string]$OutputDirectory = "artifacts/tmig1-public-update-rebuild",
    [Parameter(Mandatory)][string]$Node,
    [string]$KeyStore,
    [string]$KeyAlias,
    [string]$StorePasswordEnvironment = "TMIG1_STORE_PASSWORD",
    [string]$KeyPasswordEnvironment = "TMIG1_KEY_PASSWORD",
    [switch]$ConfirmAndroidSdkLicenseTermsAccepted
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
if (-not $ConfirmAndroidSdkLicenseTermsAccepted) { throw "Confirm SDK license terms before invoking Android SDK tools." }
$Source = Join-Path $PSScriptRoot "..\artifacts\android-105-guard07-tap1-test01\TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk"
$Source = (Resolve-Path -LiteralPath $Source).Path
$ExpectedSha = "d39312a28f22abb064552b536bc31e37126918219065b35af2f557a70e5a24b0"
if ((Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedSha) { throw "PUBLIC derivative SHA mismatch; original source allowlist unchanged." }
function Resolve-Input([string]$Path) { if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing file: $Path" }; return (Resolve-Path -LiteralPath $Path).Path }
$Java = Resolve-Input (Join-Path $JavaHome "bin/java.exe")
$Javac = Resolve-Input (Join-Path $JavaHome "bin/javac.exe")
$AndroidJar = Resolve-Input $AndroidJar
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
$D8Jar = Resolve-Input (Join-Path $BuildTools "lib/d8.jar")
$ApkSignerJar = $null
if ($KeyStore) {
    $KeyStore = Resolve-Input $KeyStore
    $ApkSignerJar = Resolve-Input (Join-Path $BuildTools "lib/apksigner.jar")
    $ZipAlign = Resolve-Input (Join-Path $BuildTools "zipalign.exe")
    if (-not $KeyAlias -or -not [Environment]::GetEnvironmentVariable($StorePasswordEnvironment)) { throw "Signing requires KeyAlias and a populated password environment variable." }
}
$Python = Resolve-Input $Python
$ApktoolJar = Resolve-Input $ApktoolJar
if (Test-Path -LiteralPath $OutputDirectory) { throw "Use a new output directory; existing artifacts are never overwritten." }
$Work = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $Work | Out-Null
$Decoded = Join-Path $PSScriptRoot "..\artifacts\tmig1-public-inspection"
if (-not (Test-Path -LiteralPath $Decoded -PathType Container)) { throw "Expected independently decoded PUBLIC smali inventory is missing." }
$Node = Resolve-Input $Node
& $Node --check (Join-Path $PSScriptRoot "package-tmig1-windows.mjs")
if ($LASTEXITCODE -ne 0) { throw "Windows host packaging helper syntax check failed." }
$Utility = Join-Path $PSScriptRoot "tmig1_windows.py"
& $Python $Utility public-preflight $Source $Decoded (Join-Path $Work "public-preflight.json")
if ($LASTEXITCODE -ne 0) { throw "PUBLIC source / hook / guarded URL audit failed." }
$Classes = Join-Path $Work "classes"; New-Item -ItemType Directory -Path $Classes | Out-Null
$SourceList = Join-Path $Work "sources.txt"
$JavaSources = @(Get-ChildItem (Join-Path $PSScriptRoot "src/com/turboio/addon") -Filter "*.java" -File | Sort-Object Name | ForEach-Object { "`"" + $_.FullName.Replace("\", "/") + "`"" })
[IO.File]::WriteAllLines($SourceList, [string[]]$JavaSources, [Text.UTF8Encoding]::new($false))
if (-not (Get-Content -LiteralPath $SourceList).Count) { throw "No addon Java sources." }
& $Javac -encoding UTF-8 -source 8 -target 8 -cp $AndroidJar -d $Classes "@$SourceList"
if ($LASTEXITCODE -ne 0) { throw "Full addon Java compilation failed." }
$AddonJar = Join-Path $Work "addon.jar"
& $Python $Utility jar $Classes $AddonJar
if ($LASTEXITCODE -ne 0) { throw "JAR construction failed." }
$Dex = Join-Path $Work "dex"; New-Item -ItemType Directory -Path $Dex | Out-Null
& $Java -cp $D8Jar com.android.tools.r8.D8 --lib $AndroidJar --min-api 29 --output $Dex $AddonJar
if ($LASTEXITCODE -ne 0) { throw "D8 compilation failed." }
$Output = Join-Path $Work "TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC-rebuilt-unsigned.apk"
& $Python $Utility public-merge $Source (Join-Path $Dex "classes.dex") $Output (Join-Path $Work "public-build-report.json")
if ($LASTEXITCODE -ne 0) { throw "Exact classes4.dex-only merge audit failed." }
& $Python $Utility public-audit $Source $Output (Join-Path $Work "public-audit.json")
if ($LASTEXITCODE -ne 0) { throw "Independent unsigned payload audit failed." }
if (-not $KeyStore) {
    Write-Output "Unsigned PUBLIC derivative rebuilt and audited: $Output. Signing deferred; no signature verification requested."
    return
}
$Aligned = Join-Path $Work "public-aligned.apk"
$Signed = Join-Path $Work "public-signed.apk"
& $ZipAlign -P 16 -f 4 $Output $Aligned
if ($LASTEXITCODE -ne 0) { throw "APK alignment failed." }
$SignArguments = @('-jar',$ApkSignerJar,'sign','--ks',$KeyStore,'--ks-key-alias',$KeyAlias,'--ks-pass',"env:$StorePasswordEnvironment",'--out',$Signed)
if ([Environment]::GetEnvironmentVariable($KeyPasswordEnvironment)) { $SignArguments += @('--key-pass',"env:$KeyPasswordEnvironment") }
& $Java @SignArguments $Aligned
if ($LASTEXITCODE -ne 0) { throw "APK signing failed." }
& $ZipAlign -c -P 16 4 $Signed
if ($LASTEXITCODE -ne 0) { throw "Signed APK alignment check failed." }
& $Java -jar $ApkSignerJar verify --verbose --print-certs $Signed | Tee-Object -FilePath (Join-Path $Work "derived-certificates.txt")
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
& $Python $Utility audit-signed $Output $Signed $Source (Join-Path $Work "signed-payload-audit.json")
if ($LASTEXITCODE -ne 0) { throw "Signed payload audit failed." }
Write-Output "Signed PUBLIC derivative verified: $Signed. Installation requires the installed signer or a separately authorized uninstall/data-loss decision."
