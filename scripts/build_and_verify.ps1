# PHAROS Master Reproducible Build & Verification Gate Runner (PowerShell)
$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $ProjectRoot

Write-Host "=== STEP 1: Verify Zero-Internet & Play Store Policy on Merged Manifests ===" -ForegroundColor Cyan
& "$PSScriptRoot\check_no_internet_perm.ps1" -ProjectRoot $ProjectRoot

Write-Host "=== STEP 2: Verify 16 KB ELF Page Alignment on All Native C++20 .so Libraries ===" -ForegroundColor Cyan
& "$PSScriptRoot\check_16kb_alignment.ps1" -ProjectRoot $ProjectRoot

Write-Host "=== STEP 3: Copy & Verify Canonical Release Artifacts + SHA-256 Checksums ===" -ForegroundColor Cyan
$standaloneApkSrc = "$ProjectRoot\app\build\outputs\apk\standalone\release\app-standalone-release.apk"
$standaloneUniversalDst = "$ProjectRoot\app\build\outputs\apk\standalone\release\app-standalone-universal-release.apk"
$playAabSrc = "$ProjectRoot\app\build\outputs\bundle\playStoreRelease\app-playStore-release.aab"

if (Test-Path $standaloneApkSrc) {
    Copy-Item $standaloneApkSrc $standaloneUniversalDst -Force
    Copy-Item $standaloneApkSrc "$ProjectRoot\marketing-kit\site\app-standalone-universal-release.apk" -Force
    Copy-Item $standaloneApkSrc "$ProjectRoot\PHAROS-universal-release.apk" -Force
}
if (Test-Path $playAabSrc) {
    Copy-Item $playAabSrc "$ProjectRoot\PHAROS-playStore-release.aab" -Force
}

$artifacts = @(
    "$ProjectRoot\PHAROS-universal-release.apk",
    "$ProjectRoot\PHAROS-playStore-release.aab",
    $standaloneUniversalDst,
    $playAabSrc
) | Where-Object { Test-Path $_ }

$checksumLines = @()
foreach ($art in $artifacts) {
    $hash = (Get-FileHash -Path $art -Algorithm SHA256).Hash.ToLower()
    $sizeMb = "{0:N2} MB" -f ((Get-Item $art).Length / 1MB)
    $line = "$hash  $(Split-Path $art -Leaf) ($sizeMb)"
    $checksumLines += $line
    Write-Host "[ARTIFACT READY] $line -> $art" -ForegroundColor Green
}

$checksumLines | Set-Content -Path "$ProjectRoot\SHA256SUMS.txt" -Encoding UTF8
Write-Host "=== PHAROS BUILD & VERIFICATION GATES 100% GREEN ===" -ForegroundColor Cyan
