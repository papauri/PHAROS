package org.pharos.core.optical

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.protocol.BlindPacket
import kotlin.random.Random

class OpticalUnitTest {

    @Test
    fun `4-PPM long range torch modulation decodes 256-byte blind packet under 15 percent jitter and bit errors`() {
        val rng = Random(404)
        val modem = OpticalModemEngine()
        val originalPacket = BlindPacket.create(
            ephemeralNonce = rng.nextBytes(16),
            ttlHops = 7,
            encryptedPayload = rng.nextBytes(200),
            poly1305Tag = rng.nextBytes(16)
        )
        val wire256 = originalPacket.toWireBytes()
        val slots = modem.encode4PpmSlots(wire256)

        // Simulate noisy optical channel with 15% amplitude jitter + 4 leading dark frames
        val noisySamples = FloatArray(slots.size + 4)
        for (i in 0 until 4) noisySamples[i] = 18f
        for (i in slots.indices) {
            val base = if (slots[i]) 220f else 30f
            val jitter = (rng.nextFloat() * 2f - 1f) * (base * 0.15f)
            noisySamples[i + 4] = base + jitter
        }

        // Also invert 5 full bytes (20 symbols) completely so Reed-Solomon (255, 240) must heal them!
        for (badByte in intArrayOf(12, 48, 99, 175, 230)) {
            val slotBase = 4 + modem.PPM_PREAMBLE_SLOTS.size + badByte * 16
            for (s in 0 until 16) {
                noisySamples[slotBase + s] = if (s % 4 == 0) 240f else 10f
            }
        }

        val demodulated256 = modem.decode4PpmSoftSamples(noisySamples, 256)
        val (recoveredPacket, correctedSymbols) = BlindPacket.fromWireBytes(demodulated256)
        assertTrue("Reed-Solomon should repair the injected symbol errors", correctedSymbols in 1..7)
        assertEquals(originalPacket, recoveredPacket)
    }

    @Test
    fun `4x4 matrix VSync renderer and 240x240 ROI stabilizer recover 256-byte blind packet`() {
        val rng = Random(808)
        val modem = OpticalModemEngine()
        val originalPacket = BlindPacket.create(
            ephemeralNonce = rng.nextBytes(16),
            ttlHops = 11,
            encryptedPayload = rng.nextBytes(200),
            poly1305Tag = rng.nextBytes(16)
        )
        val wire256 = originalPacket.toWireBytes()
        val txFrames = modem.encodeMatrix4x4Frames(wire256)
        assertEquals(64, txFrames.size) // 64 frames * 4 bytes/frame = 256 bytes

        // Render 64 synthetic 240x240 grayscale camera ROIs with pixel noise
        val roiFrames = txFrames.map { frame ->
            modem.renderSyntheticRoi240(frame, centerOffsetX = 0, centerOffsetY = 0, noiseAmplitude = 15)
        }

        val decodedWire = modem.decodeMatrix4x4FromRoiFrames(roiFrames, 256)
        val (recoveredPacket, _) = BlindPacket.fromWireBytes(decodedWire)
        assertArrayEquals(wire256, recoveredPacket.toWireBytes())
    }

    @Test
    fun `manual morse decoder translates human flashlight pulses with timing jitter`() {
        val encoded = ManualMorseCodec.encodeTextToMorse("SOS 2026")
        assertEquals("... --- ... / ..--- ----- ..--- -....", encoded)
        assertEquals("SOS 2026", ManualMorseCodec.decodeMorseString(encoded))

        // Simulate human flashlight pulses for "SOS" with +-25ms timing variation
        val sosPulses = listOf(
            140L, -130L, 165L, -145L, 135L,   // S (...)
            -380L,                             // letter gap
            440L, -140L, 470L, -155L, 430L,   // O (---)
            -410L,                             // letter gap
            155L, -140L, 145L, -150L, 160L    // S (...)
        )
        assertEquals("SOS", ManualMorseCodec.decodePulseDurationsMs(sosPulses, baseDotMs = 150L))
    }
}
