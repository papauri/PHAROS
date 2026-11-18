# PHAROS — Viral GTM, Short-Form Video Scripts, Paid Ads & Community Launch Kit

## 1. Viral Short-Form Video Script (TikTok / Reels / YouTube Shorts — 30 Seconds)
**Title:** *"The 2km Flashlight Text Challenge (Airplane Mode ON)"*

- **0:00–0:04 (Hook — Split Screen Night Ridge):**
  - *Visual:* Two operators standing on opposite mountain ridges at night, 2.1 km apart. Both phones show **Airplane Mode ON**, SIM cards removed, Wi-Fi OFF.
  - *Voiceover / Caption:* "No SIM card. No internet. No LoRa hardware. Can two regular Android phones text each other across a 2-kilometer canyon using only their camera flashlights?"
- **0:04–0:12 (The Setup — BEAM Tab):**
  - *Visual:* Operator A types `"RIDGE CLEAR - RENDEZVOUS 0600"` in PHAROS `660nm` Red-Ops UI, taps **TRANSMIT 256B BEAM (Torch 4-PPM 8Hz)**. Rear LED strobe fires a rapid, rhythmic 4-PPM optical sequence.
- **0:12–0:22 (The Lock — 240x240 ROI Reticle):**
  - *Visual:* Operator B's phone screen shows the distant pinprick of light inside the `240×240` C++ NDK stabilized reticle. Even as Operator B's hands shake in the wind, the green centroid lock tracks the light, the Reed-Solomon `RS(255,240)` FEC bar hits `100% (3 symbols healed)`, and the XChaCha20-Poly1305 packet decrypts on screen!
- **0:22–0:30 (Walkie-Talkie Twist + CTA):**
  - *Visual:* Operator B holds their phone up to a \$15 FRS walkie-talkie, presses PTT, and sends back a 1,000-byte `Codec2` voice reply + GPS pin over 16-MFSK audio chirps.
  - *CTA:* "PHAROS — Zero internet permission. Search PHAROS on Google Play or grab the Universal APK."

---

## 2. Authentic Community Launch Posts

### Hacker News (`Show HN`)
**Title:** `Show HN: Pharos – Zero-internet Android communicator (Optical 4-PPM, GPS-sync BLE/Wi-Fi mesh, MFSK audio)`
**Body:**
```text
Hi HN, we built PHAROS to answer a practical question: if cellular and internet infrastructure go dark tomorrow, how much resilient communication can you extract from unmodified smartphones and legacy radios people already own?

Key engineering choices:
1. No `android.permission.INTERNET` in AndroidManifest.xml (enforced in CI via merged-manifest check).
2. Fixed 256-byte uniform blind packets: 16B blinded epoch tag, 8B XOR-masked TTL/flags, 200B XChaCha20-Poly1305 payload, 16B Poly1305 tag, and 16B Reed-Solomon RS(255,240) + CRC-8 trailer.
3. Three physical transports:
   - Optical (`:core:optical`): C++20 NDK 240x240 ROI tracker with centroid stabilization decoding 4-PPM flashlight pulses (1–5 km at night) and VSync 4x4 RGBW OLED matrices (30–300m).
   - Firefly Mesh (`:core:mesh`): Reads passive 1575.42 MHz GNSS atomic UTC time to wake nodes simultaneously for 15 seconds every 15 minutes (:00, :15, :30, :45), exchanging missing capsules from a 50MB/72h ring buffer via 64-byte Bloom filters.
   - Acoustic (`:core:audio`): 48 kHz 16-MFSK walkie-talkie modem (800–2300 Hz) + Codec2 (700C/1200bps) + Goertzel DTMF decoder for vintage Nokia 3310 keypad sequences (`*1*53349*06260#`).
4. Dual-channel distribution: The Play Store `.aab` serves a single-file offline HTML5/Canvas/WebAudio PWA over LocalOnlyHotspot so bystanders need zero install; the Standalone Universal `.apk` can also serve its own signed APK binary offline.
```

### Reddit (`r/preppers`, `r/meshtastic`, `r/amateurradio`, `r/privacy`)
- **`r/preppers` Headline:** `Built a free, zero-internet Android survival app that turns old drawer phones (Android 6.0+) and cheap walkie-talkies into an encrypted mesh & optical transceiver`
- **`r/meshtastic` Headline:** `When your friends don't have LoRa nodes: how we used passive GPS atomic clock sync (15s wake every 15m) + LocalOnlyHotspot PWA + walkie-talkie MFSK on standard phones`
- **`r/amateurradio` Headline:** `16-MFSK (800-2300 Hz) + Codec2 700C + Goertzel DTMF + RS(255,240) FEC in a 16KB-aligned C++20 Android NDK survival app`

---

## 3. 5 A/B-Tested Paid Ad Campaign Sets (Google App Campaigns / Meta / Reddit)

| Ad Set | Target Audience | Headline A | Headline B | Primary Hook Copy |
| :--- | :--- | :--- | :--- | :--- |
| **Set 1: Grid Down / Prepper** | Emergency Preparedness | No Cell Towers? Use Light & Radio. | Turn Old Phones Into Off-Grid Nodes | Communicate 1–5km via flashlight pulses, walkie-talkie chirps & GPS-synced mesh. Zero internet needed. |
| **Set 2: Backcountry / Alpine** | Hiking, Mountaineering, SAR | Beyond Satellite & Cell Coverage | 4-Day Battery Survival Mesh | Passive GPS clock sync wakes your group's phones for 15s every 15m. Offline vector maps + SOS strobe decoder. |
| **Set 3: Zero-Trust Privacy** | Cybersecurity, Infosec, Journalists | Zero Internet Permission. Literally. | Silicon-Locked StrongBox + Duress PIN | Physically incapable of cloud telemetry. XChaCha20-Poly1305 256B blind packets + `<100ms` Duress PIN wipe. |
| **Set 4: Radio & Comms Gear** | FRS/PMR446/Ham Radio Users | Send Encrypted Text Over Any Walkie | Bridge Analog Radios to Offline Maps | Hold your phone to any FRS/PMR446 radio: 16-MFSK chirps deliver signed GPS pins & Codec2 voice at 5 dB SNR. |
| **Set 5: Drawer Phone Revival** | Tech Enthusiasts, DIY | Don't Recycle Your Old Android 6.0 Phone | Works on 99% of Backup Phones | Runs from Android 6.0 (API 23) to Android 16 (API 36). Share offline via QR Hotspot in 10 seconds. |

---

## 4. Influencer & Press Field-Test Kit
1. **Test 1 (The 30-Second Optical Desk Test):** Put two phones in Airplane Mode. Open `BEAM`, tap `TRANSMIT 256B BEAM`, and watch the Reed-Solomon FEC progress bar heal corrupted symbols in real time.
2. **Test 2 (The Walkie-Talkie Acoustic Test):** Key up any pair of analog walkie-talkies across a building or park, tap `PTT MFSK + DTMF LOOPBACK` on `BRIDGE`, and watch the 256-byte packet decode over acoustic airwaves.
3. **Test 3 (The `<100ms` Duress PIN Test):** Tap `PIN VAULT` in the top bar, enter `9911`, and verify that StrongBox/TEE keys and tactical feeds are cryptographically wiped in `<100ms` and replaced by the First-Aid Decoy screen.
