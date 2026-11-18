# PHAROS — Off-Grid Survival Communicator (`org.pharos.mesh`)

**PHAROS** is an off-grid, zero-internet, zero-cloud Android survival communicator (`minSdk 23` to `targetSdk 36`) using optical modulation (**Light**), GPS-clocked micro-burst radio relaying (**Messages / Firefly Mesh**), and acoustic walkie-talkie & dumbphone bridging (**Radio & Share**).

## Key Capabilities
1. **Zero Internet Permission (Rule Zero):** `android.permission.INTERNET` is physically absent from the merged Android manifest.
2. **Optical Modem (`:core:optical`):** 4-PPM LED Torch modulation (4/8 Hz HAL calibrated) for 1–5+ km night line-of-sight, VSync 4×4 Matrix for 30–300m bursts, C++20 NDK `240×240` ROI hand-shake stabilization, Reed-Solomon `RS(255, 240)` FEC, and Manual Morse decoding.
3. **Firefly Delay-Tolerant Mesh (`:core:mesh` & `:core:storage`):** Passive `1575.42 MHz` GNSS atomic clock sync wakes nodes simultaneously for 15 seconds every 15 minutes (`:00`, `:15`, `:30`, `:45`), exchanging 256-byte `XChaCha20-Poly1305` blind packets from a 50 MB / 72-hour FIFO Ring Buffer via 64-byte Bloom filters.
4. **Walkie-Talkie & Legacy Bridge (`:core:audio`):** 48 kHz 16-MFSK (`800–2300 Hz`) audio chirps surviving `5 dB SNR` walkie-talkie static, `Codec2` (`700C` / `1200bps`) voice compression decrypted strictly in volatile RAM, and a Goertzel DTMF decoder (`*1*53349*06260#`) for vintage Nokia 3310 keypads.
5. **Silicon-Locked Security (`:core:crypto`):** Android `StrongBox`/`TEE` hardware keystore binding, `Argon2id` passphrase KDF, `SPAKE2` shared human memory pairing, 2-hop Web-of-Trust vouching, and `<100ms` Emergency Duress PIN (`9911`) wipe + First-Aid Guide decoy state.
6. **8-Step Interactive First-Launch Walkthrough:** Automatically guides new operators through every control with spotlight highlight boxes and directional pointers on first launch.

## Quick Build & Verification
```powershell
gradle testDebugUnitTest :app:testPlayStoreDebugUnitTest :app:assembleStandaloneRelease :app:bundlePlayStoreRelease --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\build_and_verify.ps1
```
