[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceApk,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$AndroidJar,
    [Parameter(Mandatory)][string]$BuildTools,
    [Parameter(Mandatory)][string]$ApktoolJar,
    [Parameter(Mandatory)][string]$Python,
    [Parameter(Mandatory)][string]$Node,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$KeyStore,
    [string]$KeyAlias,
    [string]$StorePasswordEnvironment = 'TMIG1_STORE_PASSWORD',
    [string]$KeyPasswordEnvironment = 'TMIG1_KEY_PASSWORD'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Require-File([string]$File) {
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { throw "Missing file: $File" }
    return (Resolve-Path -LiteralPath $File).Path
}
function Run([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Executable" }
}
$SourceApk = Require-File $SourceApk
$Node = Require-File $Node
Run $Node @((Join-Path $PSScriptRoot 'admit-tmig1.mjs'), $SourceApk)
$Admission = ($LASTEXITCODE -eq 0)
if (-not $Admission) { throw 'Source APK admission failed' }
$Java = Require-File (Join-Path $JavaHome 'bin/java.exe')
$Javac = Require-File (Join-Path $JavaHome 'bin/javac.exe')
$AndroidJar = Require-File $AndroidJar
$ApktoolJar = Require-File $ApktoolJar
$Python = Require-File $Python
$Node = Require-File $Node
$D8Jar = Require-File (Join-Path $BuildTools 'lib/d8.jar')
$ApkSignerJar = Require-File (Join-Path $BuildTools 'lib/apksigner.jar')
$ZipAlign = Require-File (Join-Path $BuildTools 'zipalign.exe')
if ($KeyStore) {
    $KeyStore = Require-File $KeyStore
    if (-not $KeyAlias -or -not [Environment]::GetEnvironmentVariable($StorePasswordEnvironment)) {
        throw 'Signing requires KeyAlias and a populated store password environment variable'
    }
}
if (-not $Admission) { throw 'Source APK admission failed' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'OutputDirectory must be new; inspect previous artifacts before retrying' }
$Work = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $Work | Out-Null
$Classes = New-Item -ItemType Directory -Path (Join-Path $Work 'classes')
$Dex = New-Item -ItemType Directory -Path (Join-Path $Work 'dex')
$Sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/com/turboio/addon') -Filter '*.java' -File | Sort-Object Name)
if ($Sources.Count -eq 0) { throw 'No addon Java sources' }
$SourceList = Join-Path $Work 'sources.txt'
$SourceLines = [string[]]@($Sources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
[IO.File]::WriteAllLines($SourceList, $SourceLines, (New-Object Text.UTF8Encoding($false)))
$Utility = Join-Path $PSScriptRoot 'tmig1_windows.py'
Run $Javac @('-encoding','UTF-8','-source','8','-target','8','-cp',$AndroidJar,'-d',$Classes.FullName,"@$SourceList")
$AddonJar = Join-Path $Work 'addon.jar'
Run $Python @($Utility,'jar',$Classes.FullName,$AddonJar)
Run $Java @('-cp',$D8Jar,'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','29','--output',$Dex.FullName,$AddonJar)
Run $Node @((Join-Path $PSScriptRoot 'package-tmig1-windows.mjs'),$SourceApk,$Work,(Join-Path $Dex.FullName 'classes.dex'),$Java,$ApktoolJar,$Python)
Run $Python @($Utility,'patch',$SourceApk,(Join-Path $Work 'base-unsigned.apk'),$Work,$Node)
$Unsigned = Join-Path $Work 'guarded-unsigned.apk'
Run $Java @('-jar',$ApkSignerJar,'verify','--verbose','--print-certs',$SourceApk) | Tee-Object -FilePath (Join-Path $Work 'source-certificates.txt')
if (-not $KeyStore) {
    Write-Output "Audited unsigned APK: $Unsigned. Signing deferred: supply an authorized KeyStore and KeyAlias. No key generated."
    return
}
$Aligned = Join-Path $Work 'guarded-aligned.apk'
$Signed = Join-Path $Work 'TMIG1-guarded-signed.apk'
Run $ZipAlign @('-P','16','-f','4',$Unsigned,$Aligned)
$SignArguments = @('-jar',$ApkSignerJar,'sign','--ks',$KeyStore,'--ks-key-alias',$KeyAlias,'--ks-pass',"env:$StorePasswordEnvironment",'--out',$Signed)
if ([Environment]::GetEnvironmentVariable($KeyPasswordEnvironment)) { $SignArguments += @('--key-pass',"env:$KeyPasswordEnvironment") }
Run $Java ($SignArguments + $Aligned)
Run $ZipAlign @('-c','-P','16','4',$Signed)
Run $Java @('-jar',$ApkSignerJar,'verify','--verbose','--print-certs',$Signed) | Tee-Object -FilePath (Join-Path $Work 'signed-certificates.txt')
Run $Python @($Utility,'audit-signed',$Unsigned,$Signed,$SourceApk,(Join-Path $Work 'windows-build-report.json'))
Write-Output "Verified signed APK: $Signed. Compare signer certificates before any separately authorized installation."
