# PHAROS — Multi-Agent Swarm Architecture & Ownership Rules

## 1. Global Invariants
- **Always Commit & Push to `main`:** Every change, build, or update must be committed and pushed directly to `origin/main` (`https://github.com/papauri/PHAROS.git`).
- **Zero Cloud / Zero Internet:** Never add `android.permission.INTERNET` to any module manifest.
- **16 KB ELF Page Alignment:** All C++20 NDK libraries (`:core:crypto`, `:core:optical`, `:core:audio`) must retain `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384`.
- **Simplified Field UI:** Keep `:app` UI ([PharosCockpitScreen.kt](app/src/main/kotlin/org/pharos/app/ui/PharosCockpitScreen.kt)) utilitarian, high-contrast (`#000000` OLED + `660nm` Red Mode), and free of jargon or decorative clutter, preserving the 8-step interactive first-launch walkthrough with spotlight pointers.

## 2. Module Ownership Boundaries
- **`:core:protocol`**: 256-byte `BlindPacket` codec, `ReedSolomonCodec` (`RS(255,240)` & `RS(255,223)`), `TacticalCard` binary schema.
- **`:core:crypto`**: `libpharos_crypto.so`, `XChaCha20Poly1305Engine`, `HardwareIdentityManager` (`StrongBox`/`TEE` + `Argon2idKdf`), `ZeroServerHumanAuth` (`SPAKE2`, 2-hop WoT, 6×6 Paper OTP).
- **`:core:storage`**: `PharosEncryptedVault` (SQLite WAL + `<100ms` Duress PIN wipe), `BlindMuleRingBuffer` (50 MB / 72h FIFO), `RamOnlyMediaVault`.
- **`:core:optical`**: `libpharos_optical.so`, `NativeOpticalProcessor` (`240×240` ROI tracker), `OpticalModemEngine` (4-PPM Torch, 4×4 VSync Matrix, `ManualMorseCodec`).
- **`:core:mesh` & `:core:offline-web-bridge`**: `FireflyMeshScheduler` (passive GNSS `:00/:15/:30/:45` sync), `BloomSetReconciler` (64B Bloom filter), `DualSpreaderHotspotServer`, `OfflineWebBridgePwa`.
- **`:core:audio`**: `libpharos_dsp.so`, `NativeDspBridge` (48 kHz 16-MFSK), `Codec2VoicePipeline` (700C & 1200bps), `NokiaDtmfAndLegacyBridge` (`5 dB SNR` Goertzel DTMF, `SMS-160`, Bluetooth OBEX `.vcf`).
- **`:app`**: `MainActivity`, `PharosViewModel`, `PharosCockpitScreen`, `NightOpsTheme`, and 8-step first-launch walkthrough.
