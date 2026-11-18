# PHAROS — Autonomous Swarm Execution Tracker

## Phase 0: Foundation & Contracts (`@Orchestrator-Prime` + `@Lead-QA-Verification`)
- [x] Inspect environment, JDK 17, Android SDK 36.1, Build-Tools 36.0.0, Gradle 9.3.1
- [x] Install NDK r28c (`28.2.13676358`) and CMake (`3.22.1`) for 16 KB ELF page alignment
- [x] Initialize multi-module Gradle Kotlin DSL project (`pharos/`) with `libs.versions.toml`
- [x] Configure `minSdk = 23`, `targetSdk = 36`, `compileSdk = 36.1`, ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`), and dual flavors (`playStore` & `standalone`)
- [x] Implement `:core:protocol` — Canonical 256-byte blind packet codec, Reed-Solomon GF(256) `(255, 223)` & `(255, 240)` FEC, binary tactical cards, and CRC-8 framing
- [x] Create CI verification scripts in `scripts/` (`check_16kb_alignment.ps1`/`.sh`, `check_no_internet_perm.ps1`/`.sh`, `build_and_verify.ps1`)

## Phase 1: Cryptographic Core & Local Vault (`@Lead-Crypto-Identity`)
- [x] Implement `:core:crypto` C++20 NDK (`libpharos_crypto.so`, 16 KB aligned) + Kotlin engine:
  - Hardware Keystore binding (`StrongBox` with `TEE` fallback on API 23+, biometric gate + `Argon2id` passphrase fallback)
  - RFC 8439 `XChaCha20-Poly1305` AEAD, deterministic 64-byte detached signing, `DoubleRatchetSession`, rotating blinded epoch tags
  - Zero-server human auth: 4-word physical QR/NFC checksum, `SPAKE2` PAKE, 2-hop Web-of-Trust vouching & revocation tombstones, 6x6 Paper OTP generator
- [x] Implement `:core:storage`:
  - Encrypted SQLite WAL (`FULL` sync) vault + RAM-only media blob decryptor (`240x180` stripped WebP & Codec2)
  - 50 MB / 72-hour FIFO Blind Mule Ring Buffer
  - `<100ms` Duress PIN cryptographic erasure (`KeyStore.deleteEntry()`, header zeroization, decoy first-aid state, `NODE_COMPROMISED` tombstone)
- [x] Verify Gate 1: KATs, SPAKE2 loopback, Ring Buffer eviction, and `<100ms` Duress wipe unit tests

## Phase 2: Physical Transports — Light, Radio & Audio (`@Lead-Optical-Modem`, `@Lead-Firefly-Mesh`, `@Lead-Acoustic-Legacy`)
- [x] Implement `:core:optical` (`libpharos_optical.so`, 16 KB aligned + Kotlin Camera2/VSync):
  - Torch HAL latency calibrator (`CameraManager.setTorchMode`) & 4 Hz / 8 Hz 4-PPM modulator
  - VSync-locked `4x4 RGBW Color Grid` & `1x1 Full-Screen LCD` optical transmitters
  - C++ NDK `240x240` center ROI grayscale processor, perimeter centroid lock, Reed-Solomon FEC decoder, and Manual Morse decoder
- [x] Implement `:core:offline-web-bridge`:
  - Self-contained single-file HTML5/JS/Canvas/WebAudio Offline PWA client capable of flashing/decoding optical packets and MFSK/DTMF audio in a mobile browser
- [x] Implement `:core:mesh`:
  - Passive GNSS UTC atomic clock sync (`LocationManager.GPS_PROVIDER` / `PASSIVE_PROVIDER`) with monotonic drift compensation
  - Firefly Duty Cycler (`:00`, `:15`, `:30`, `:45` 15-second wake bursts via `AlarmManager.setExactAndAllowWhileIdle` + `ForegroundService`)
  - 64-byte Bloom filter set reconciliation capsule burst transport
  - Embedded HTTP server over `LocalOnlyHotspot` serving `.apk` in `standalone` and `:core:offline-web-bridge` PWA in `playStore`
- [x] Implement `:core:audio` (`libpharos_dsp.so`, 16 KB aligned + AudioTrack/DSP):
  - `Codec2` (700C & 1200 bps) voice compressor/decompressor with metadata stripping
  - 48 kHz real-time `800–2300 Hz` 16-MFSK walkie-talkie chirp modem
  - Goertzel-filter `DTMF` (`697–1633 Hz`) Nokia 3310 keypad decoder (`*1*53349*06260#`)
  - `SMS-160` GSM-7/Base64 compact codec and Bluetooth OBEX `.vcf` payload generator
- [x] Verify Gate 2: Optical noisy frame simulation (15% jitter/noise), Audio WAV loopback at `5 dB SNR`, and Hotspot server tests

## Phase 3: Night-Ops Tactical UI & Maps (`@Lead-UI-Tactical`)
- [x] Build `:app` Jetpack Compose Night-Ops Design System (`#000000` true OLED black, 1-tap `660nm` Deep-Red Night Vision mode, wet/gloved-hand touch targets)
- [x] Build 3-Tab Tactical Cockpit (`BEAM`, `PULSE`, `BRIDGE`), Offline Vector Tactical Map, Prominent Permission Disclosures, Duress PIN Vault, and Triple-Volume-Down Stealth Blackout

## Phase 4: Full Verification, Audit & Play Store Readiness (`@Lead-QA-Verification` + `@Lead-PlayStore-Compliance`)
- [x] Run complete unit test suite across all modules (`:core:protocol`, `:core:crypto`, `:core:storage`, `:core:optical`, `:core:mesh`, `:core:audio`, `:app`)
- [x] Build signed release artifacts (`app-playStore-release.aab` and `app-standalone-universal-release.apk`)
- [x] Run automated verification gates (0 `INTERNET` permissions, 0 `REQUEST_INSTALL_PACKAGES` in `playStore`, 70/70 `.so` binaries `>= 0x4000` 16 KB aligned)
- [x] Complete `play-store-assets/` compliance kit (`privacy-policy.html`, `data-safety-and-compliance.json`)

## Phase 5: Brand, Storefront & Advertising Blitz (`@Lead-Growth-Marketing`)
- [x] Complete `play-store-assets/` storefront metadata (`aso-storefront-listings.md` in EN, DE, ES, FR, PL, JP) and visual assets (`feature-graphic-1024x500.svg`)
- [x] Complete `marketing-kit/` (`site/index.html` static landing page, `campaigns-and-launch-kit.md`)
