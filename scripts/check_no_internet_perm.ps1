# PHAROS Zero-Internet & Play Store Policy Manifest Audit Script (PowerShell)
param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"
$mergedManifests = Get-ChildItem -Path "$ProjectRoot\app\build\intermediates\merged_manifest" -Recurse -Filter "AndroidManifest.xml" -ErrorAction SilentlyContinue

if ($mergedManifests.Count -eq 0) {
    Write-Error "No merged AndroidManifest.xml files found in app/build/intermediates/merged_manifest"
    exit 1
}

foreach ($manifest in $mergedManifests) {
    $content = Get-Content -Path $manifest.FullName -Raw
    if ($content -match "android\.permission\.INTERNET") {
        Write-Error "[POLICY VIOLATION] Found forbidden android.permission.INTERNET in $($manifest.FullName)"
        exit 1
    }
    if ($manifest.FullName -match "playStore" -and $content -match "android\.permission\.REQUEST_INSTALL_PACKAGES") {
        Write-Error "[PLAY STORE VIOLATION] Found forbidden REQUEST_INSTALL_PACKAGES in $($manifest.FullName)"
        exit 1
    }
    Write-Host "[PASS MANIFEST AUDIT] $($manifest.FullName) -> ZERO INTERNET, POLICY COMPLIANT" -ForegroundColor Green
}

Write-Host "=== ALL $($mergedManifests.Count) MERGED MANIFESTS PASSED ZERO-INTERNET & PLAY POLICY AUDIT ===" -ForegroundColor Cyan
