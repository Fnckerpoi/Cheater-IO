param([string]$JavaHome='C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $env:TEMP ('tools-settings-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $out | Out-Null
try {
    $source=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'src/com/turboio/addon/TurboAddon.java')
    $start=$source.IndexOf('    static void showTools() {')
    if($start -lt 0){ throw 'showTools boundary not found' }
    $methods=$source.Substring($start)
    $prefix=@'
package com.turboio.addon;
class TurboToolsHarness {
    static final Object app=new Object(); static SharedPreferences prefs;
    static void reset(boolean storageLocked,boolean pending) { prefs=new SharedPreferences(); prefs.values.put("dsh_unknown_delivery",pending); ToolClient.locked=storageLocked;SecretStore.searchKey="existing-search-key"; }
    static Activity host() { return new Activity(); }
    static LinearLayout panel(Activity a) { return new LinearLayout(); }
    static EditText input(Activity a,LinearLayout box,String hint,String value,boolean secret) { return new EditText(); }
    static void action(Activity a,LinearLayout b,String label,Runnable r) {}
    static void label(Activity a,LinearLayout b,String text) {}
    static void cancel() {} static void showHome() {}
'@
    [IO.File]::WriteAllText((Join-Path $out 'TurboToolsHarness.java'), $prefix + [Environment]::NewLine + $methods, [Text.UTF8Encoding]::new($false))
    & "$JavaHome/bin/javac.exe" -encoding UTF-8 -d $out (Join-Path $out 'TurboToolsHarness.java') (Join-Path $PSScriptRoot 'ToolsSettingsTest.java')
    if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
    & "$JavaHome/bin/java.exe" -cp $out com.turboio.addon.ToolsSettingsTest
    if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
} finally {
    Remove-Item -Recurse -Force -LiteralPath $out -ErrorAction SilentlyContinue
}
