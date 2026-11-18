# PHAROS — Architecture & AI Continuity Reference (`CLAUDE.md`)

- **Package Name:** `org.pharos.mesh`
- **SDK Targets:** `minSdk = 23` (Android 6.0), `targetSdk = 36` (Android 16), `compileSdk = 36.1`, `ndkVersion = "28.2.13676358"`
- **Flavors:** `playStore` (`.aab` for Google Play) and `standalone` (Universal `.apk` for direct/offline sideloading)
- **Default PINs:** Normal PIN = `2580`, Emergency Duress PIN = `9911` (`<100ms` cryptographic wipe + First-Aid Decoy state)
- **Walkthrough Preference Key:** `SharedPreferences("pharos_onboarding")` -> `"walkthrough_completed_v1"`
- **Verification Script:** `powershell -ExecutionPolicy Bypass -File .\scripts\build_and_verify.ps1`
