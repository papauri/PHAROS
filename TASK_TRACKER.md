# PHAROS — Task & Verification Tracker

- [x] **Phase 0:** Multi-module Gradle Kotlin DSL repo, NDK r28c (`28.2.13676358`), `:core:protocol` 256B blind packet & `GF(256)` Reed-Solomon FEC (`4/4` tests pass)
- [x] **Phase 1:** `:core:crypto` (`XChaCha20-Poly1305` KATs, `StrongBox`/`TEE`, `SPAKE2`, 2-hop WoT, 6×6 OTP) & `:core:storage` (50MB/72h Ring Buffer, RAM-only media, `<100ms` Duress wipe) (`9/9` tests pass)
- [x] **Phase 2:** `:core:optical` (4-PPM, 4×4 matrix, 240×240 ROI, Morse), `:core:mesh` (Passive GNSS Firefly 15m/15s, 64B Bloom filter, Dual-Flavor Hotspot Server), `:core:offline-web-bridge`, `:core:audio` (16-MFSK, Goertzel DTMF at `5 dB SNR`, Codec2 700C/1200bps, SMS-160, OBEX) (`10/10` tests pass)
- [x] **Phase 3:** Simplified Night-Ops UI (`#000000` OLED + `660nm` Red Mode) + 8-step interactive first-launch walkthrough with spotlight pointers (`2/2` integration & E2E system tests pass)
- [x] **Phase 4 & 5:** `70/70` native `.so` binaries verified `>= 0x4000` (16 KB aligned), `0` `INTERNET` permissions in merged manifests, signed `PHAROS-universal-release.apk` (`3.26 MB`) and `PHAROS-playStore-release.aab` (`4.35 MB`) generated, Play Store compliance & marketing kit complete.
