# PHAROS 16 KB ELF Page-Size Alignment Verification Script (PowerShell)
param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"
$ndkReadElf = "$env:ANDROID_HOME\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
if (-not (Test-Path $ndkReadElf)) {
    Write-Error "llvm-readelf.exe not found at $ndkReadElf"
    exit 1
}

$soFiles = Get-ChildItem -Path $ProjectRoot -Recurse -Filter "*.so" | Where-Object {
    $_.FullName -match "merged_native_libs|stripped_native_libs|cmake\\.*\\obj"
}

if ($soFiles.Count -eq 0) {
    Write-Error "No compiled .so binaries found to verify!"
    exit 1
}

$failed = 0
foreach ($so in $soFiles) {
    $output = & $ndkReadElf -l $so.FullName
    $loadLines = $output | Select-String -Pattern "^\s*LOAD\s+"
    foreach ($line in $loadLines) {
        if ($line.Line -match "0x([0-9a-fA-F]+)\s*$") {
            $alignHex = $Matches[1]
            $alignVal = [Convert]::ToInt64($alignHex, 16)
            if ($alignVal -lt 16384) {
                Write-Host "[FAIL] $($so.FullName) has LOAD alignment 0x$alignHex ($alignVal < 16384)" -ForegroundColor Red
                $failed++
            }
        }
    }
    Write-Host "[PASS 16KB] $($so.Name) ($($so.Directory.Name)) verified >= 0x4000" -ForegroundColor Green
}

if ($failed -gt 0) {
    Write-Error "$failed LOAD segment(s) failed 16 KB alignment check!"
    exit 1
}
Write-Host "=== ALL $($soFiles.Count) NATIVE .SO BINARIES PASSED 16 KB PAGE ALIGNMENT ===" -ForegroundColor Cyan
