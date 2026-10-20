$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Path = Join-Path $PSScriptRoot 'build-tmig1-public-update-windows.ps1'
$ParseErrors = $null; $Tokens = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$Tokens, [ref]$ParseErrors)
if ($ParseErrors.Count) { throw ($ParseErrors | Out-String) }
$NodeParameter = $Ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -eq 'Node' }
if ($NodeParameter.DefaultValue -or -not $NodeParameter.Extent.Text.Contains('[Parameter(Mandatory)]')) { throw 'Node must require an explicit executable path' }
$Text = Get-Content -LiteralPath $Path -Raw
$Start = $Text.IndexOf('& $Python $Utility public-audit')
if ($Start -lt 0) { throw 'Missing independent unsigned payload audit' }
$Flow = [scriptblock]::Create($Text.Substring($Start))
$script:Calls = [Collections.Generic.List[string]]::new()
$script:FailVerify = $false
function Mock-Python { $script:Calls.Add('python ' + ($args -join ' ')); $global:LASTEXITCODE = 0 }
function Mock-Java {
    $script:Calls.Add('java ' + ($args -join ' '))
    $global:LASTEXITCODE = 0
    if ($script:FailVerify -and $args -contains 'verify') { $global:LASTEXITCODE = 1 }
}
function Mock-Align { $script:Calls.Add('align ' + ($args -join ' ')); $global:LASTEXITCODE = 0 }
$Python = 'Mock-Python'; $Java = 'Mock-Java'; $ZipAlign = 'Mock-Align'
$Utility = 'utility.py'; $Source = 'source.apk'; $Output = 'unsigned.apk'
$Work = Join-Path ([IO.Path]::GetTempPath()) ('tmig1 flow ' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Work | Out-Null
$KeyStore = ''; $KeyAlias = 'test'; $ApkSignerJar = 'mock-apksigner.jar'
$StorePasswordEnvironment = 'TMIG1_TEST_STORE'; $KeyPasswordEnvironment = 'TMIG1_TEST_KEY'
& $Flow | Out-Null
if ($script:Calls.Count -ne 1 -or $script:Calls[0] -notmatch 'public-audit') { throw 'Unsigned path invoked signing tools or omitted payload audit' }
$script:Calls.Clear(); $KeyStore = 'authorized-test-input.jks'
& $Flow | Out-Null
$CallText = $script:Calls -join "
"
if ($CallText -notmatch ' sign ' -or $CallText -notmatch ' verify ' -or $CallText -notmatch 'audit-signed' -or $CallText -notmatch 'align -c') { throw 'Signed path did not sign, verify and audit payloads' }
if ($CallText -match 'verify .*unsigned.apk') { throw 'Unsigned output was passed to signature verification' }
$script:FailVerify = $true
$Rejected = $false
try { & $Flow | Out-Null } catch { if ($_.Exception.Message -eq 'APK signature verification failed.') { $Rejected = $true } else { throw } }
if (-not $Rejected) { throw 'Signed verification failure was ignored' }
Write-Output 'PASS: explicit mandatory Node; unsigned audit without signing; optional signing/alignment/verification/audit; failed signature rejection. No SDK tools executed.'
$global:LASTEXITCODE = 0
