# Backs up the Fire TV's current settings/app list, then installs Simple TV over Wi-Fi.
# Double-click setup-firestick.bat, or in PowerShell:  .\setup-firestick.ps1 10.0.0.252 [path\to\tv-debug.apk]
param(
    [string]$Ip,
    [string]$Apk
)
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot
$Pkg = "com.simpletv.launcher"

function Fail($msg) {
    Write-Host ""
    Write-Host "PROBLEM: $msg" -ForegroundColor Red
    Read-Host "`nPress Enter to close"
    exit 1
}

try {
    Write-Host "=== Simple TV setup ===" -ForegroundColor Cyan

    # ---- Find or download adb ----
    $adb = $null
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
    elseif (Test-Path "$PSScriptRoot\platform-tools\adb.exe") { $adb = "$PSScriptRoot\platform-tools\adb.exe" }
    else {
        Write-Host "adb not found. Downloading Google's platform-tools (about 7 MB)..."
        $zip = "$PSScriptRoot\platform-tools.zip"
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip -UseBasicParsing
            Expand-Archive $zip -DestinationPath $PSScriptRoot -Force
            Remove-Item $zip
            $adb = "$PSScriptRoot\platform-tools\adb.exe"
        } catch { Fail "Couldn't download adb: $($_.Exception.Message)" }
    }
    Write-Host "Using adb: $adb"

    # ---- Find the APK ----
    if (-not $Apk) {
        $found = Get-ChildItem -Path $PSScriptRoot -Recurse -Include "tv-debug.apk","SimpleTV-tv.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $found) {
            $found = Get-ChildItem -Path "$env:USERPROFILE\Downloads" -Recurse -Include "tv-debug.apk","SimpleTV-tv.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        }
        if ($found) { $Apk = $found.FullName }
    }
    if (-not $Apk) {
        Write-Host "Downloading the latest Simple TV app from GitHub..."
        $Apk = "$PSScriptRoot\SimpleTV-tv.apk"
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest "https://github.com/codyroycg/SimpleTV/releases/latest/download/SimpleTV-tv.apk" -OutFile $Apk -UseBasicParsing
        } catch { $Apk = $null }
    }
    if (-not $Apk -or -not (Test-Path $Apk)) {
        Fail "Couldn't find tv-debug.apk. Build it first (see README step 1), then put it in this folder or in Downloads and run this again."
    }
    Write-Host "Using APK: $Apk"

    # ---- Connect ----
    if (-not $Ip) { $Ip = Read-Host "Firestick IP address (e.g. 10.0.0.252)" }
    $Ip = $Ip.Trim()
    $D = "${Ip}:5555"
    Write-Host "`nConnecting to $Ip ... If the TV shows 'Allow USB debugging?', choose Always allow + OK."
    $state = ""
    for ($i = 0; $i -lt 6 -and $state -ne "device"; $i++) {
        & $adb connect $D | Out-Host
        Start-Sleep 3
        $state = ((& $adb -s $D get-state 2>&1) | Out-String).Trim()
    }
    if ($state -ne "device") {
        Fail "Couldn't connect to the Firestick ($state). Check: TV is on, ADB debugging is ON in Developer options, the IP is right, and you accepted the prompt on the TV."
    }
    Write-Host "Connected." -ForegroundColor Green

    # ---- Backup ----
    $B = "firestick-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss")
    New-Item -ItemType Directory -Force -Path "$B\apks" | Out-Null
    Write-Host "`nSaving backup to $PSScriptRoot\$B ..."
    & $adb -s $D shell getprop                | Out-File "$B\device-info.txt"
    & $adb -s $D shell pm list packages -f    | Out-File "$B\installed-packages.txt"
    & $adb -s $D shell settings list secure   | Out-File "$B\settings-secure.txt"
    & $adb -s $D shell settings list global   | Out-File "$B\settings-global.txt"
    & $adb -s $D shell settings list system   | Out-File "$B\settings-system.txt"
    $Acc   = ((& $adb -s $D shell settings get secure enabled_accessibility_services) | Out-String).Trim()
    $AccOn = ((& $adb -s $D shell settings get secure accessibility_enabled) | Out-String).Trim()

    foreach ($line in (& $adb -s $D shell pm list packages -3 -f)) {
        $l = ($line | Out-String).Trim() -replace '^package:', ''
        if (-not $l) { continue }
        $i = $l.LastIndexOf('=')
        if ($i -lt 0) { continue }
        $path = $l.Substring(0, $i); $name = $l.Substring($i + 1)
        if ($name -eq $Pkg) { continue }
        & $adb -s $D pull $path "$B\apks\$name.apk" 2>&1 | Out-Null
    }

    $accRestore = if ($Acc -eq "null" -or -not $Acc) { "& `$adb -s $D shell settings delete secure enabled_accessibility_services" }
                  else { "& `$adb -s $D shell settings put secure enabled_accessibility_services '$Acc'" }
    $onRestore  = if ($AccOn -eq "null" -or -not $AccOn) { "& `$adb -s $D shell settings delete secure accessibility_enabled" }
                  else { "& `$adb -s $D shell settings put secure accessibility_enabled $AccOn" }
@"
# Restores the Fire TV to how it was before Simple TV was installed.
`$adb = "$adb"
& `$adb connect $D
& `$adb -s $D uninstall $Pkg
$accRestore
$onRestore
Write-Host "Restored."
Read-Host "Press Enter to close"
"@ | Out-File "$B\undo.ps1"
    "@echo off`r`npowershell -ExecutionPolicy Bypass -File `"%~dp0undo.ps1`"" | Out-File "$B\undo.bat" -Encoding ascii
    Write-Host "Backup saved. To undo everything later, double-click $B\undo.bat" -ForegroundColor Green

    # ---- Install ----
    Write-Host "`nInstalling Simple TV..."
    $out = (& $adb -s $D install -r $Apk 2>&1) | Out-String
    Write-Host $out
    if ($out -notmatch "Success") { Fail "Install failed (see message above)." }

    Write-Host "Turning on Back button + Home-button takeover..."
    $svc = "$Pkg/$Pkg.TvAccessibilityService"
    $newAcc = if ($Acc -eq "null" -or -not $Acc) { $svc } elseif ($Acc -like "*$svc*") { $Acc } else { "${Acc}:$svc" }
    & $adb -s $D shell settings put secure enabled_accessibility_services "$newAcc"
    & $adb -s $D shell settings put secure accessibility_enabled 1
    & $adb -s $D shell appops set $Pkg SYSTEM_ALERT_WINDOW allow
    & $adb -s $D shell am start -n "$Pkg/.MainActivity" | Out-Null

    Write-Host "`nDone! Simple TV should be on your TV now." -ForegroundColor Green
    Write-Host "The address and code for the phone app are at the bottom of the TV screen."
}
catch {
    Fail $_.Exception.Message
}
Read-Host "`nPress Enter to close"
