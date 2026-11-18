package org.pharos.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.TacticalCard
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioUnitTest {

    @Test
    fun `walkie talkie MFSK WAV loopback recovers 256-byte blind packet at 5 dB SNR`() {
        val rng = Random(505)
        val originalPacket = BlindPacket.create(
            ephemeralNonce = rng.nextBytes(16),
            ttlHops = 6,
            encryptedPayload = rng.nextBytes(200),
            poly1305Tag = rng.nextBytes(16)
        )
        val wire256 = originalPacket.toWireBytes()

        // Modulate to 48kHz 16-MFSK audio
        val cleanPcm = NativeDspBridge.modulateMfsk48k(wire256)
        // Wrap in RIFF WAVE and extract
        val wavBytes = WalkieTalkieChannelSimulator.wrapPcmInWav48kMono(cleanPcm)
        val wavPcm = WalkieTalkieChannelSimulator.extractPcmFromWav48kMono(wavBytes)

        // Inject heavy 5.0 dB SNR radio static and walkie-talkie limiter clipping
        val noisyPcm5dB = WalkieTalkieChannelSimulator.injectRadioStaticAndClipping(wavPcm, snrDb = 5.0, seed = 99)

        val demodulated256 = NativeDspBridge.demodulateMfsk48k(noisyPcm5dB, 256)
        val (recoveredPacket, _) = BlindPacket.fromWireBytes(demodulated256)
        assertArrayEquals(wire256, recoveredPacket.toWireBytes())
    }

    @Test
    fun `nokia 3310 Goertzel DTMF decoder extracts keypad coordinates at 5 dB SNR`() {
        val sequence = "*1*53349*06260#"
        val cleanDtmfPcm = NokiaDtmfAndLegacyBridge.encodeDtmfSequence48k(sequence)
        val noisyDtmfPcm = WalkieTalkieChannelSimulator.injectRadioStaticAndClipping(cleanDtmfPcm, snrDb = 5.0, seed = 42)

        val decodedStr = NokiaDtmfAndLegacyBridge.decodeDtmfSequence48k(noisyDtmfPcm)
        assertEquals(sequence, decodedStr)

        val report = NokiaDtmfAndLegacyBridge.parseNokiaKeypadFrame(decodedStr)
        assertNotNull(report)
        assertEquals(1, report!!.statusCode)
        assertEquals(53.349, report.latitudeDeg, 1e-4)
        assertEquals(6.260, report.longitudeDeg, 1e-4)
    }

    @Test
    fun `codec2 700C and 1200bps compress 10-second speech clip to 1000-1500 bytes with zero metadata`() {
        // Generate 10 seconds of 8kHz synthetic voiced speech (80,000 samples = 160,000 bytes raw PCM)
        val tenSecSamples = ShortArray(80_000) { i ->
            val f0 = 180.0
            (sin(2.0 * PI * f0 * i / 8000.0) * 12000.0 + sin(2.0 * PI * f0 * 2 * i / 8000.0) * 4000.0).toInt().toShort()
        }

        val compressed700c = Codec2VoicePipeline.encodeSpeech8k(tenSecSamples, Codec2VoicePipeline.BitrateMode.MODE_700C)
        val compressed1200 = Codec2VoicePipeline.encodeSpeech8k(tenSecSamples, Codec2VoicePipeline.BitrateMode.MODE_1200BPS)

        assertEquals(1000, compressed700c.size)
        assertEquals(1500, compressed1200.size)

        val synthesizedPcm = Codec2VoicePipeline.decodeSpeech8k(compressed1200, Codec2VoicePipeline.BitrateMode.MODE_1200BPS)
        assertEquals(80_000, synthesizedPcm.size)
        assertTrue("Synthesized speech must have non-zero acoustic energy", synthesizedPcm.any { it > 2000 })
    }

    @Test
    fun `SMS-160 and Bluetooth OBEX vCard formatters respect legacy constraints`() {
        val card = TacticalCard(
            cardId = 777L,
            timestampUtcSec = 1794950000L,
            statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
            verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
            latitudeDeg = 46.5197,
            longitudeDeg = 6.6323,
            altitudeMeters = 410,
            batteryPercent = 52,
            note = "URGENT EVAC AT NORTH RIDGE HUT - HELICOPTER PAD CLEAR AND MARKED WITH STROBE"
        )

        val sms = NokiaDtmfAndLegacyBridge.encodeSms160(card)
        assertTrue("SMS-160 payload must be <= 160 characters (was ${sms.length})", sms.length <= 160)
        assertTrue(sms.startsWith("PH1:"))

        val vcard = NokiaDtmfAndLegacyBridge.formatBluetoothObexVcard(card)
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("GEO:46.51970;6.63230"))
        assertTrue(vcard.contains("END:VCARD"))
    }
}
