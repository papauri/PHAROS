---
name: pharos-engineering
description: Build, verify, audit, and extend the PHAROS off-grid Android survival communicator (`org.pharos.mesh`). Activate this skill when working on PHAROS optical modulation (4-PPM / 4x4 matrix), Firefly GNSS mesh, 16-MFSK/DTMF audio modem, 256-byte blind packet crypto, or Play Store / Universal APK builds.
---

# PHAROS Engineering & Verification Skill

Use this skill when developing, testing, or packaging **PHAROS** (`C:\Users\Admin\.gemini\antigravity\scratch\pharos`).

## 1. Non-Negotiable Architectural Invariants
1. **Rule Zero (Zero Internet):** `android.permission.INTERNET` must NEVER exist in any merged manifest. `app/src/main/AndroidManifest.xml` enforces `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`.
2. **Play Store vs. Standalone Flavors:**
   - `playStore` (`.aab`): Zero `REQUEST_INSTALL_PACKAGES`. Serves `:core:offline-web-bridge` single-file HTML5 PWA over `LocalOnlyHotspot`.
   - `standalone` (`.apk`): Monolithic Universal APK (`arm64-v8a`, `armeabi-v7a`, `x86_64`) that serves both the PWA and its own signed `.apk` offline.
3. **16 KB Native Page Alignment:** All C++20 NDK libraries (`libpharos_crypto.so`, `libpharos_optical.so`, `libpharos_dsp.so`) are compiled with NDK `28.2.13676358` and `-Wl,-z,max-page-size=16384`.

## 2. Single Unified Build & Verification Command
Run from `C:\Users\Admin\.gemini\antigravity\scratch\pharos`:
```powershell
gradle testDebugUnitTest :app:testPlayStoreDebugUnitTest :app:assembleStandaloneRelease :app:bundlePlayStoreRelease --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\build_and_verify.ps1
```
