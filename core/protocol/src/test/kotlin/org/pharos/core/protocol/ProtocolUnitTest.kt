package org.pharos.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProtocolUnitTest {

    @Test
    fun `reed solomon repairs up to 7 corrupted bytes in 256-byte blind packet`() {
        val rng = Random(42)
        for (trial in 1..25) {
            val data240 = rng.nextBytes(240)
            val trailer16 = ReedSolomonCodec.computePacketTrailer16(data240)
            val wire256 = data240 + trailer16

            // Corrupt between 1 and 7 distinct positions in [0..254]
            val numErrors = (trial % 7) + 1
            val positions = (0 until 255).shuffled(rng).take(numErrors)
            val corrupted = wire256.copyOf()
            for (pos in positions) {
                val delta = rng.nextInt(1, 256)
                corrupted[pos] = (corrupted[pos].toInt() xor delta).toByte()
            }

            val result = ReedSolomonCodec.decodePacket256(corrupted)
            assertEquals("Trial $trial should repair $numErrors symbols", numErrors, result.correctedSymbols)
            assertTrue("CRC should validate after repair", result.crcValid)
            assertArrayEquals("Repaired 240-byte payload must match original", data240, result.data)
        }
    }

    @Test
    fun `reed solomon outer block RS(255, 223) repairs 16 corrupted bytes`() {
        val rng = Random(1337)
        val data223 = rng.nextBytes(223)
        val parity32 = ReedSolomonCodec.computeParity(data223, 32)
        val codeword255 = data223 + parity32

        val corrupted = codeword255.copyOf()
        val positions = (0 until 255).shuffled(rng).take(16)
        for (pos in positions) {
            corrupted[pos] = (corrupted[pos].toInt() xor 0xA5).toByte()
        }

        val (repaired, count) = ReedSolomonCodec.decodeCodeword(corrupted, 32)
        assertEquals(16, count)
        assertArrayEquals(codeword255, repaired)
    }

    @Test
    fun `blind packet 256-byte wire codec masks header and survives channel bit flips`() {
        val rng = Random(2026)
        val nonce = rng.nextBytes(16)
        val payload = rng.nextBytes(200)
        val tag = rng.nextBytes(16)

        val packet = BlindPacket.create(
            ephemeralNonce = nonce,
            ttlHops = 9,
            packetFlags = PacketFlag.BLIND_MULE_ELIGIBLE or PacketFlag.LIFE_SAFETY_WAKE,
            payloadType = PayloadType.TACTICAL_CARD,
            fragmentIndex = 2,
            fragmentTotal = 5,
            epochSlot15Min = 0x123456,
            encryptedPayload = payload,
            poly1305Tag = tag
        )

        val wire = packet.toWireBytes()
        assertEquals(256, wire.size)

        // Inject 6 random byte errors across header, payload, and parity
        val noisyWire = wire.copyOf()
        intArrayOf(3, 18, 55, 140, 228, 245).forEach { idx ->
            noisyWire[idx] = (noisyWire[idx].toInt() xor 0x7F).toByte()
        }

        val (recovered, correctedCount) = BlindPacket.fromWireBytes(noisyWire)
        assertEquals(6, correctedCount)
        assertEquals(9, recovered.ttlHops)
        assertEquals(PacketFlag.BLIND_MULE_ELIGIBLE or PacketFlag.LIFE_SAFETY_WAKE, recovered.packetFlags)
        assertEquals(PayloadType.TACTICAL_CARD, recovered.payloadType)
        assertEquals(2, recovered.fragmentIndex)
        assertEquals(5, recovered.fragmentTotal)
        assertEquals(0x123456, recovered.epochSlot15Min)
        assertArrayEquals(payload, recovered.encryptedPayload)
        assertArrayEquals(tag, recovered.poly1305Tag)

        // Verify TTL hop decrement preserves deduplication hash and AEAD authenticated data
        val hopped = recovered.decrementHopTtl()
        assertNotNull(hopped)
        assertEquals(8, hopped!!.ttlHops)
        assertEquals(recovered.packetDedupeHex(), hopped.packetDedupeHex())
        assertArrayEquals(recovered.authenticatedData24(), hopped.authenticatedData24())
    }

    @Test
    fun `tactical card binary serialization fits in 200-byte payload`() {
        val fp = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())
        val sig = ByteArray(64) { (it + 1).toByte() }
        val card = TacticalCard(
            cardId = 0x0102030405060708L,
            timestampUtcSec = 1794956280L,
            statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
            verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
            latitudeDeg = 46.519653,
            longitudeDeg = 6.632273,
            altitudeMeters = 412,
            batteryPercent = 78,
            attachmentType = TacticalCard.AttachmentType.CODEC2_700C,
            senderKeyFingerprintShort = fp,
            ed25519Signature = sig,
            note = "RIDGE COLLAPSE NORTH SECTOR - 3 SURVIVORS NEED EVAC"
        )

        val payload200 = card.toPayload200()
        assertEquals(200, payload200.size)

        val decoded = TacticalCard.fromPayload200(payload200)
        assertEquals(card.cardId, decoded.cardId)
        assertEquals(card.timestampUtcSec, decoded.timestampUtcSec)
        assertEquals(card.statusTier, decoded.statusTier)
        assertEquals(card.verificationState, decoded.verificationState)
        assertEquals(card.latitudeDeg, decoded.latitudeDeg, 1e-6)
        assertEquals(card.longitudeDeg, decoded.longitudeDeg, 1e-6)
        assertEquals(card.altitudeMeters, decoded.altitudeMeters)
        assertEquals(card.batteryPercent, decoded.batteryPercent)
        assertEquals(card.attachmentType, decoded.attachmentType)
        assertArrayEquals(fp, decoded.senderKeyFingerprintShort)
        assertArrayEquals(sig, decoded.ed25519Signature)
        assertEquals(card.note, decoded.note)
    }
}
