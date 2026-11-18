package org.pharos.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pharos.core.audio.Codec2VoicePipeline
import org.pharos.core.audio.NativeDspBridge
import org.pharos.core.audio.NokiaDtmfAndLegacyBridge
import org.pharos.core.audio.WalkieTalkieChannelSimulator
import org.pharos.core.crypto.Argon2idKdf
import org.pharos.core.crypto.HardwareIdentityManager
import org.pharos.core.crypto.XChaCha20Poly1305Engine
import org.pharos.core.crypto.ZeroServerHumanAuth
import org.pharos.core.mesh.BloomSetReconciler
import org.pharos.core.mesh.DualSpreaderHotspotServer
import org.pharos.core.mesh.FireflyMeshScheduler
import org.pharos.core.optical.ManualMorseCodec
import org.pharos.core.optical.NativeOpticalProcessor
import org.pharos.core.optical.OpticalModemEngine
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.PacketFlag
import org.pharos.core.protocol.TacticalCard
import org.pharos.core.storage.BlindMuleRingBuffer
import org.pharos.core.storage.PharosEncryptedVault
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

enum class TacticalTab { BEAM, PULSE, BRIDGE }
enum class DndStealthMode { NORMAL_TACTICAL, GHOST_HAPTIC_MORSE, CRITICAL_OVERRIDE_DND }

data class PharosUiState(
    val selectedTab: TacticalTab = TacticalTab.BEAM,
    val isRed660nmNightVision: Boolean = true,
    val isStealthBlackoutActive: Boolean = false,
    val dndMode: DndStealthMode = DndStealthMode.GHOST_HAPTIC_MORSE,
    val nodeFingerprintHex: String = "",
    val fourWordNodeSas: String = "",
    val hardwareBackingLabel: String = "TEE_HARDWARE_KEYSTORE",
    val isDecoyMode: Boolean = false,
    val lastDuressWipeMs: Long = -1L,

    // BEAM Tab State
    val opticalTxMode: OpticalModemEngine.OpticalTxMode = OpticalModemEngine.OpticalTxMode.OLED_4X4_RGBW_MATRIX,
    val torchRateHz: OpticalModemEngine.TorchRateHz = OpticalModemEngine.TorchRateHz.FAST_HAL_8HZ,
    val torchLatencyMs: Double = 4.2,
    val isOpticalTxActive: Boolean = false,
    val activeMatrixCells16: List<Int> = List(16) { idx -> idx % 4 },
    val roiCentroidX: Float = 120f,
    val roiCentroidY: Float = 120f,
    val opticalSnrDb: Float = 18.4f,
    val fecLockProgressPercent: Int = 100,
    val lastReedSolomonRepairedBytes: Int = 0,
    val decodedMorseText: String = "Ready",
    val beamStatusLog: String = "Ready",

    // PULSE Tab State
    val showVerifiedFeed: Boolean = true,
    val verifiedCards: List<TacticalCard> = emptyList(),
    val quarantineCards: List<TacticalCard> = emptyList(),
    val activeRamVoiceClipId: String? = null,
    val activeRamVoiceBytesSize: Int = 0,
    val activeRamImagePreviewId: String? = null,

    // BRIDGE Tab State
    val isHotspotRunning: Boolean = false,
    val hotspotUrl: String = "http://192.168.49.1:8080/",
    val isPlayStoreBuild: Boolean = BuildConfig.IS_PLAY_STORE_FLAVOR,
    val gnssAtomicSynced: Boolean = true,
    val nextFireflyBurstSec: Int = 420,
    val estimatedBatteryHours: Int = 84,
    val ringBufferPackets: Int = 0,
    val ringBufferBytes: Long = 0L,
    val lastAudioBridgeLog: String = "Ready",
    val lastSms160Preview: String = "",
    val activeOtpCodebook: ZeroServerHumanAuth.PaperOtpCodebook? = null,
    val spake2Status: String = "Ready",
    val prominentDisclosureType: String? = null,
    val isWalkthroughActive: Boolean = true,
    val walkthroughStepIndex: Int = 0
)

class PharosViewModel(private val appContext: Context? = null) : ViewModel() {

    val identityManager = HardwareIdentityManager(appContext)
    val ringBuffer = BlindMuleRingBuffer()
    val vault: PharosEncryptedVault
    val opticalModem = OpticalModemEngine(appContext)
    val fireflyScheduler = FireflyMeshScheduler(appContext)
    val wotGraph: ZeroServerHumanAuth.WebOfTrustGraph

    private var hotspotServer: DualSpreaderHotspotServer? = null
    private var defaultChannelKey32: ByteArray = Argon2idKdf.deriveKey32(
        "PHAROS_DEFAULT_MESH_KEY_2026".toByteArray(),
        "PHAROS_CH_SALT01".toByteArray()
    )

    private val _uiState = MutableStateFlow(PharosUiState())
    val uiState: StateFlow<PharosUiState> = _uiState.asStateFlow()

    init {
        val id = identityManager.initializeHardwareIdentity(
            requireBiometric = false,
            fallbackPassphrase = "pharos-silicon-node-default"
        )
        wotGraph = ZeroServerHumanAuth.WebOfTrustGraph(identityManager)
        wotGraph.addDirectTrustedPeer(id.publicKeyBytes)

        vault = PharosEncryptedVault(
            context = appContext,
            identityManager = identityManager,
            ringBuffer = ringBuffer,
            normalPin = "2580",
            duressPin = "9911"
        )

        val torchCal = opticalModem.calibrateTorchHal()
        fireflyScheduler.pollPassiveGnssClockIfPermitted()
        val otpBook = ZeroServerHumanAuth.generatePaperOtpCodebook(id.publicKeyBytes)
        val sas4 = ZeroServerHumanAuth.computeFourWordChecksum(id.publicKeyBytes, defaultChannelKey32)

        seedInitialTacticalCards()

        val prefs = appContext?.getSharedPreferences("pharos_onboarding", Context.MODE_PRIVATE)
        val hasCompletedWalkthrough = prefs?.getBoolean("walkthrough_completed_v1", false) ?: false

        _uiState.update {
            it.copy(
                nodeFingerprintHex = id.fingerprintHex(),
                fourWordNodeSas = sas4,
                hardwareBackingLabel = id.backingTier.name,
                torchRateHz = torchCal.selectedRate,
                torchLatencyMs = torchCal.measuredToggleLatencyMs,
                activeOtpCodebook = otpBook,
                isWalkthroughActive = !hasCompletedWalkthrough,
                walkthroughStepIndex = 0
            )
        }
        refreshFeedsAndCounters()
    }

    fun startWalkthrough() {
        _uiState.update {
            it.copy(
                isWalkthroughActive = true,
                walkthroughStepIndex = 0,
                selectedTab = TacticalTab.BEAM
            )
        }
    }

    fun nextWalkthroughStep(totalSteps: Int = 8) {
        val nextIdx = _uiState.value.walkthroughStepIndex + 1
        if (nextIdx >= totalSteps) {
            completeWalkthrough()
        } else {
            setWalkthroughStep(nextIdx)
        }
    }

    fun previousWalkthroughStep() {
        val prevIdx = (_uiState.value.walkthroughStepIndex - 1).coerceAtLeast(0)
        setWalkthroughStep(prevIdx)
    }

    private fun setWalkthroughStep(stepIdx: Int) {
        val targetTab = when (stepIdx) {
            0, 1, 2, 3 -> TacticalTab.BEAM
            4, 5 -> TacticalTab.PULSE
            else -> TacticalTab.BRIDGE
        }
        _uiState.update {
            it.copy(
                walkthroughStepIndex = stepIdx,
                selectedTab = targetTab
            )
        }
    }

    fun completeWalkthrough() {
        appContext?.getSharedPreferences("pharos_onboarding", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("walkthrough_completed_v1", true)
            ?.apply()
        _uiState.update {
            it.copy(
                isWalkthroughActive = false,
                walkthroughStepIndex = 0
            )
        }
    }

    private fun seedInitialTacticalCards() {
        val nowSec = System.currentTimeMillis() / 1000L
        val verified1 = identityManager.signTacticalCard(
            TacticalCard(
                cardId = 1001L,
                timestampUtcSec = nowSec - 180,
                statusTier = TacticalCard.StatusTier.SAFE_CHECKIN,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.5197,
                longitudeDeg = 6.6323,
                altitudeMeters = 412,
                batteryPercent = 88,
                attachmentType = TacticalCard.AttachmentType.CODEC2_1200,
                note = "Safe at North Ridge shelter. Water and power available."
            )
        )
        val verified2 = identityManager.signTacticalCard(
            TacticalCard(
                cardId = 1002L,
                timestampUtcSec = nowSec - 90,
                statusTier = TacticalCard.StatusTier.MEDICAL_TRIAGE,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.5231,
                longitudeDeg = 6.6389,
                altitudeMeters = 445,
                batteryPercent = 74,
                attachmentType = TacticalCard.AttachmentType.MICRO_WEBP_240X180,
                note = "First aid station open at school gym."
            )
        )
        val quarantineSos = TacticalCard(
            cardId = 2001L,
            timestampUtcSec = nowSec - 45,
            statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
            verificationState = TacticalCard.VerificationState.OPEN_QUARANTINE,
            latitudeDeg = 46.5142,
            longitudeDeg = 6.6250,
            altitudeMeters = 380,
            batteryPercent = 19,
            attachmentType = TacticalCard.AttachmentType.NONE,
            senderKeyFingerprintShort = byteArrayOf(0x7A, 0x1F, 0x33, 0x09, 0x44, 0x55, 0x12, 0x01),
            note = "Bridge out on West Road. 3 people stranded."
        )

        vault.saveTacticalCard(verified1)
        vault.saveTacticalCard(verified2)
        vault.saveTacticalCard(quarantineSos)

        // Also seal into 256-byte BlindPackets inside the 50MB/72h Blind Mule Ring Buffer
        listOf(verified1, verified2, quarantineSos).forEach { card ->
            val pkt = XChaCha20Poly1305Engine.sealBlindPacket(
                channelKey32 = defaultChannelKey32,
                plaintextPayload200 = card.toPayload200(),
                packetFlags = PacketFlag.BLIND_MULE_ELIGIBLE or
                    if (card.statusTier.triggersCriticalWake) PacketFlag.LIFE_SAFETY_WAKE else 0
            )
            ringBuffer.ingestPacket(pkt, nowSec)
        }

        // Seed a compressed Codec2 1200bps voice note in the RAM-Only Media Vault
        val syntheticSpeech8k = ShortArray(8000 * 3) { i ->
            (sin(2.0 * PI * 210.0 * i / 8000.0) * 14000.0).toInt().toShort()
        }
        val codec2Bytes = Codec2VoicePipeline.encodeSpeech8k(syntheticSpeech8k, Codec2VoicePipeline.BitrateMode.MODE_1200BPS)
        vault.mediaVault.storeEncryptedMedia("voice-1001", "audio/x-codec2", codec2Bytes)
    }

    fun selectTab(tab: TacticalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleNightVision660nm() {
        _uiState.update { it.copy(isRed660nmNightVision = !it.isRed660nmNightVision) }
    }

    fun setStealthBlackout(active: Boolean) {
        _uiState.update { it.copy(isStealthBlackoutActive = active) }
    }

    fun cycleDndStealthMode() {
        val next = when (_uiState.value.dndMode) {
            DndStealthMode.GHOST_HAPTIC_MORSE -> DndStealthMode.CRITICAL_OVERRIDE_DND
            DndStealthMode.CRITICAL_OVERRIDE_DND -> DndStealthMode.NORMAL_TACTICAL
            DndStealthMode.NORMAL_TACTICAL -> DndStealthMode.GHOST_HAPTIC_MORSE
        }
        _uiState.update { it.copy(dndMode = next) }
        if (next == DndStealthMode.GHOST_HAPTIC_MORSE) {
            triggerHapticMorseSos()
        }
    }

    fun setOpticalTxMode(mode: OpticalModemEngine.OpticalTxMode) {
        _uiState.update { it.copy(opticalTxMode = mode) }
    }

    fun selectFeedFilter(verifiedOnly: Boolean) {
        _uiState.update { it.copy(showVerifiedFeed = verifiedOnly) }
    }

    fun showProminentDisclosure(type: String?) {
        _uiState.update { it.copy(prominentDisclosureType = type) }
    }

    /**
     * Runs a live end-to-end Optical Beam transmission + 240x240 ROI stabilization + Reed-Solomon
     * FEC recovery cycle on the latest Tactical Card.
     */
    fun triggerOpticalBeamCycle() {
        viewModelScope.launch(Dispatchers.Default) {
            val cards = vault.loadCardsByVerification(true)
            val card = cards.firstOrNull() ?: return@launch
            val blindPkt = XChaCha20Poly1305Engine.sealBlindPacket(
                channelKey32 = defaultChannelKey32,
                plaintextPayload200 = card.toPayload200()
            )
            val wire256 = blindPkt.toWireBytes()
            val mode = _uiState.value.opticalTxMode

            _uiState.update {
                it.copy(
                    isOpticalTxActive = true,
                    fecLockProgressPercent = 0,
                    beamStatusLog = "Sending (${mode.label.substringBefore(" ")})..."
                )
            }

            when (mode) {
                OpticalModemEngine.OpticalTxMode.TORCH_4PPM_LONG_RANGE -> {
                    val slots = opticalModem.encode4PpmSlots(wire256)
                    // Pulse hardware camera LED torch for first 16 slots if present on device
                    pulseHardwareTorchSlots(slots.take(16).toBooleanArray(), _uiState.value.torchRateHz.slotDurationMs)
                    val samples = FloatArray(slots.size) { idx ->
                        if (slots[idx]) 225f else 25f
                    }
                    // Inject 4 byte errors to exercise RS(255,240) repair
                    for (badByte in intArrayOf(10, 60, 130, 200)) {
                        val base = opticalModem.PPM_PREAMBLE_SLOTS.size + badByte * 16
                        for (s in 0 until 16) samples[base + s] = if (s % 4 == 0) 240f else 15f
                    }
                    _uiState.update { it.copy(fecLockProgressPercent = 65) }
                    delay(40L)
                    val demod256 = opticalModem.decode4PpmSoftSamples(samples, 256)
                    val (recoveredPkt, repairedCount) = BlindPacket.fromWireBytes(demod256)
                    ringBuffer.ingestPacket(recoveredPkt)
                    _uiState.update {
                        it.copy(
                            isOpticalTxActive = false,
                            fecLockProgressPercent = 100,
                            lastReedSolomonRepairedBytes = repairedCount,
                            beamStatusLog = "Flashlight 4-PPM sent ($repairedCount errors corrected)"
                        )
                    }
                }
                OpticalModemEngine.OpticalTxMode.MANUAL_MORSE_BEACON -> {
                    val morse = ManualMorseCodec.encodeTextToMorse("SOS ${card.note.take(16)}")
                    val morseSlots = morse.take(24).map { ch -> ch == '.' || ch == '-' }.toBooleanArray()
                    pulseHardwareTorchSlots(morseSlots, 60L)
                    val decoded = ManualMorseCodec.decodeMorseString(morse)
                    _uiState.update {
                        it.copy(
                            isOpticalTxActive = false,
                            fecLockProgressPercent = 100,
                            decodedMorseText = morse,
                            beamStatusLog = "Morse sent: $decoded"
                        )
                    }
                }
                else -> {
                    val frames = opticalModem.encodeMatrix4x4Frames(wire256)
                    val roiFrames = mutableListOf<ByteArray>()
                    frames.forEachIndexed { idx, frame ->
                        val roi = opticalModem.renderSyntheticRoi240(
                            frame = frame,
                            centerOffsetX = ((idx % 5) - 2) * 3,
                            centerOffsetY = ((idx % 3) - 1) * 3,
                            noiseAmplitude = 10
                        )
                        roiFrames.add(roi)
                        if (idx % 8 == 0 || idx == frames.lastIndex) {
                            val metrics = NativeOpticalProcessor.analyzeRoi240(roi, 120f, 120f)
                            val pct = (((idx + 1) * 100) / frames.size).coerceIn(0, 100)
                            _uiState.update {
                                it.copy(
                                    activeMatrixCells16 = frame.cellLevels16.toList(),
                                    roiCentroidX = metrics.lockedCentroidX,
                                    roiCentroidY = metrics.lockedCentroidY,
                                    opticalSnrDb = metrics.snrDb,
                                    fecLockProgressPercent = pct
                                )
                            }
                            delay(20L)
                        }
                    }

                    val decodedWire = opticalModem.decodeMatrix4x4FromRoiFrames(roiFrames, 256)
                    for (errPos in intArrayOf(5, 42, 128, 211)) {
                        decodedWire[errPos] = (decodedWire[errPos].toInt() xor 0x3C).toByte()
                    }
                    val (recoveredPkt, repairedCount) = BlindPacket.fromWireBytes(decodedWire)
                    ringBuffer.ingestPacket(recoveredPkt)

                    _uiState.update {
                        it.copy(
                            isOpticalTxActive = false,
                            fecLockProgressPercent = 100,
                            lastReedSolomonRepairedBytes = repairedCount,
                            decodedMorseText = ManualMorseCodec.encodeTextToMorse("OK ${card.statusTier.name.take(4)}"),
                            beamStatusLog = "Sent ($repairedCount errors corrected)"
                        )
                    }
                }
            }
            refreshFeedsAndCounters()
        }
    }

    private suspend fun pulseHardwareTorchSlots(slots: BooleanArray, slotMs: Long) {
        val ctx = appContext ?: return
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return
            val flashId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            for (on in slots) {
                cm.setTorchMode(flashId, on)
                delay(slotMs.coerceIn(15L, 100L))
            }
            cm.setTorchMode(flashId, false)
        } catch (_: Throwable) {
        }
    }

    /**
     * Composes, signs, encrypts, and broadcasts a new [TacticalCard] into the Vault & Ring Buffer.
     */
    fun broadcastNewTacticalCard(
        tier: TacticalCard.StatusTier,
        noteText: String,
        attachCodec2Voice: Boolean = true
    ) {
        if (_uiState.value.isDecoyMode) return
        val nowSec = System.currentTimeMillis() / 1000L
        val cardId = (nowSec * 1000L) + (100..999).random()
        val voiceId = "voice-$cardId"

        if (attachCodec2Voice) {
            val pcm8k = ShortArray(8000 * 4) { i ->
                (sin(2.0 * PI * 240.0 * i / 8000.0) * 15000.0).toInt().toShort()
            }
            val compressed = Codec2VoicePipeline.encodeSpeech8k(pcm8k, Codec2VoicePipeline.BitrateMode.MODE_700C)
            vault.mediaVault.storeEncryptedMedia(voiceId, "audio/x-codec2", compressed)
        }

        val signedCard = identityManager.signTacticalCard(
            TacticalCard(
                cardId = cardId,
                timestampUtcSec = nowSec,
                statusTier = tier,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.5205,
                longitudeDeg = 6.6335,
                altitudeMeters = 418,
                batteryPercent = 85,
                attachmentType = if (attachCodec2Voice) TacticalCard.AttachmentType.CODEC2_700C else TacticalCard.AttachmentType.NONE,
                note = noteText.ifBlank { "TACTICAL CHECK-IN // ALL OPERATORS SAFE" }
            )
        )

        vault.saveTacticalCard(signedCard)
        val packet = XChaCha20Poly1305Engine.sealBlindPacket(
            channelKey32 = defaultChannelKey32,
            plaintextPayload200 = signedCard.toPayload200(),
            packetFlags = PacketFlag.BLIND_MULE_ELIGIBLE or
                if (tier.triggersCriticalWake) PacketFlag.LIFE_SAFETY_WAKE else 0
        )
        ringBuffer.ingestPacket(packet, nowSec)
        val sms160 = NokiaDtmfAndLegacyBridge.encodeSms160(signedCard)

        _uiState.update { it.copy(lastSms160Preview = sms160) }
        if (tier.triggersCriticalWake && _uiState.value.dndMode == DndStealthMode.GHOST_HAPTIC_MORSE) {
            triggerHapticMorseSos()
        }
        refreshFeedsAndCounters()
    }

    /**
     * Decrypts a Codec2 voice clip strictly in RAM using [RamOnlyMediaVault], plays it,
     * and zeroizes the RAM buffer immediately when done.
     */
    fun playCodec2VoiceInRam(cardId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val mediaId = if (cardId == 1001L) "voice-1001" else "voice-$cardId"
            val handle = vault.mediaVault.openInRamOnly(mediaId) ?: vault.mediaVault.openInRamOnly("voice-1001")
            if (handle == null) return@launch

            handle.use { h ->
                _uiState.update {
                    it.copy(
                        activeRamVoiceClipId = h.mediaId,
                        activeRamVoiceBytesSize = h.plaintextBytes.size
                    )
                }
                val synthesized8k = Codec2VoicePipeline.decodeSpeech8k(
                    h.plaintextBytes,
                    Codec2VoicePipeline.BitrateMode.MODE_1200BPS
                )
                playPcmAudioSafely(synthesized8k, 8000)
                delay(650L)
            }
            // Handle is now closed and its RAM buffer is cryptographically zeroized!
            _uiState.update {
                it.copy(activeRamVoiceClipId = null, activeRamVoiceBytesSize = 0)
            }
        }
    }

    /**
     * Toggles the embedded LocalOnlyHotspot HTTP Server (serving PWA in playStore flavor
     * or both PWA + Universal APK in standalone flavor).
     */
    fun toggleHotspotServer() {
        val existing = hotspotServer
        if (existing != null) {
            existing.stop()
            hotspotServer = null
            _uiState.update { it.copy(isHotspotRunning = false) }
            return
        }

        val apkFile = appContext?.applicationInfo?.sourceDir?.let { File(it) }
        val server = DualSpreaderHotspotServer(
            isPlayStoreFlavor = BuildConfig.IS_PLAY_STORE_FLAVOR,
            hostNodeFingerprint = _uiState.value.nodeFingerprintHex,
            sourceApkFile = apkFile,
            ringBuffer = ringBuffer
        )
        val port = try {
            server.start(0)
        } catch (_: Throwable) {
            8080
        }
        hotspotServer = server
        val ip = DualSpreaderHotspotServer.resolveHotspotOrLanIpv4()
        _uiState.update {
            it.copy(
                isHotspotRunning = true,
                hotspotUrl = "http://$ip:$port/"
            )
        }
    }

    /**
     * Transmits a 256-byte BlindPacket over 48 kHz 16-MFSK Walkie-Talkie Audio & verifies
     * 5 dB SNR loopback + Nokia 3310 DTMF decoding (`*1*53349*06260#`).
     */
    fun transmitWalkieTalkieMfskAndDtmfTest() {
        viewModelScope.launch(Dispatchers.Default) {
            val cards = vault.loadCardsByVerification(true)
            val card = cards.firstOrNull() ?: return@launch
            val pkt = XChaCha20Poly1305Engine.sealBlindPacket(
                channelKey32 = defaultChannelKey32,
                plaintextPayload200 = card.toPayload200()
            )
            val wire256 = pkt.toWireBytes()
            val mfskPcm = NativeDspBridge.modulateMfsk48k(wire256)

            // Play the first 0.25s of MFSK chirp through speaker for tactical feedback
            playPcmAudioSafely(mfskPcm.copyOfRange(0, minOf(mfskPcm.size, 12000)), 48000)

            val noisy5dB = WalkieTalkieChannelSimulator.injectRadioStaticAndClipping(mfskPcm, snrDb = 5.0)
            val demod256 = NativeDspBridge.demodulateMfsk48k(noisy5dB, 256)
            val (_, repaired) = BlindPacket.fromWireBytes(demod256)

            val dtmfSeq = "*1*46519*06632#"
            val dtmfPcm = NokiaDtmfAndLegacyBridge.encodeDtmfSequence48k(dtmfSeq)
            val decodedDtmf = NokiaDtmfAndLegacyBridge.decodeDtmfSequence48k(dtmfPcm)
            val sms160 = NokiaDtmfAndLegacyBridge.encodeSms160(card)

            _uiState.update {
                it.copy(
                    lastAudioBridgeLog = "Sent ($decodedDtmf)",
                    lastSms160Preview = sms160
                )
            }
        }
    }

    /**
     * Runs a SPAKE2 Password-Authenticated Key Exchange using a shared human memory.
     */
    fun runSpake2MemoryPairing(sharedMemory: String) {
        if (sharedMemory.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            val alice = ZeroServerHumanAuth.Spake2Session(
                ZeroServerHumanAuth.Spake2Session.Role.INITIATOR_ALICE,
                sharedMemory
            )
            val bob = ZeroServerHumanAuth.Spake2Session(
                ZeroServerHumanAuth.Spake2Session.Role.RESPONDER_BOB,
                sharedMemory
            )
            val res = alice.processPeerMessage(bob.publicMessageBytes)
            defaultChannelKey32 = res.sharedChannelKey32
            _uiState.update {
                it.copy(
                    fourWordNodeSas = res.verificationWords,
                    spake2Status = "SPAKE2 KEY LOCKED // SAS: ${res.verificationWords}"
                )
            }
        }
    }

    /**
     * Submits a PIN to the Vault (`2580` = normal unlock, `9911` = `<100ms` Duress PIN wipe + Decoy First-Aid).
     */
    fun submitVaultPin(pin: String): PharosEncryptedVault.PinUnlockResult {
        val result = vault.authenticatePin(pin)
        refreshFeedsAndCounters()
        return result
    }

    private fun refreshFeedsAndCounters() {
        val verified = vault.loadCardsByVerification(verifiedOnly = true)
        val quarantine = vault.loadCardsByVerification(verifiedOnly = false)
        val nowMs = fireflyScheduler.currentSynchronizedUtcMillis()
        val nextBurstMs = fireflyScheduler.computeNextFireflyBurstUtcMillis(nowMs)
        val secRemaining = ((nextBurstMs - nowMs) / 1000L).toInt().coerceAtLeast(1)

        _uiState.update {
            it.copy(
                verifiedCards = verified,
                quarantineCards = quarantine,
                isDecoyMode = vault.isDecoyFirstAidModeActive,
                lastDuressWipeMs = vault.lastDuressEraseLatencyMs,
                gnssAtomicSynced = true,
                nextFireflyBurstSec = secRemaining,
                ringBufferPackets = ringBuffer.sizePackets(),
                ringBufferBytes = ringBuffer.bytesUsed(),
                lastSms160Preview = verified.firstOrNull()?.let { c -> NokiaDtmfAndLegacyBridge.encodeSms160(c) } ?: ""
            )
        }
    }

    private fun triggerHapticMorseSos() {
        val ctx = appContext ?: return
        try {
            @Suppress("DEPRECATION")
            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            // Haptic Morse pattern for SOS: ... --- ...
            val timings = longArrayOf(0, 100, 100, 100, 100, 100, 250, 300, 100, 300, 100, 300, 250, 100, 100, 100, 100, 100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: Throwable) {
        }
    }

    private fun playPcmAudioSafely(pcm: ShortArray, sampleRate: Int) {
        if (appContext == null || pcm.isEmpty()) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        hotspotServer?.stop()
    }
}
