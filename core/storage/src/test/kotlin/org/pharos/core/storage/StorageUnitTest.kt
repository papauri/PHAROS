package org.pharos.core.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.crypto.HardwareIdentityManager
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.PacketFlag
import org.pharos.core.protocol.TacticalCard
import kotlin.random.Random

class StorageUnitTest {

    @Test
    fun `blind mule ring buffer enforces byte capacity FIFO eviction and 72h age expiry`() {
        // Configure ring buffer for max 3 packets (3 * 256 = 768 bytes) and 72h (259200s) retention
        val ringBuffer = BlindMuleRingBuffer(
            maxCapacityBytes = 3L * 256L,
            maxRetentionSeconds = 72L * 3600L
        )
        val rng = Random(77)

        fun makePacket(id: Byte, flags: Int = PacketFlag.BLIND_MULE_ELIGIBLE): BlindPacket {
            val nonce = ByteArray(16) { id }
            val payload = rng.nextBytes(200)
            val tag = rng.nextBytes(16)
            return BlindPacket.create(
                ephemeralNonce = nonce,
                ttlHops = 5,
                packetFlags = flags,
                encryptedPayload = payload,
                poly1305Tag = tag
            )
        }

        val p1 = makePacket(1)
        val p2 = makePacket(2)
        val p3 = makePacket(3, PacketFlag.BLIND_MULE_ELIGIBLE or PacketFlag.LIFE_SAFETY_WAKE)
        val p4 = makePacket(4)

        val t0 = 1_000_000L
        assertTrue(ringBuffer.ingestPacket(p1, t0))
        assertFalse("Duplicate packet must be ignored", ringBuffer.ingestPacket(p1, t0 + 1))
        assertTrue(ringBuffer.ingestPacket(p2, t0 + 10))
        assertTrue(ringBuffer.ingestPacket(p3, t0 + 20))
        assertEquals(3, ringBuffer.sizePackets())
        assertEquals(768L, ringBuffer.bytesUsed())

        // Ingesting 4th packet must evict oldest (p1)
        assertTrue(ringBuffer.ingestPacket(p4, t0 + 30))
        assertEquals(3, ringBuffer.sizePackets())
        assertFalse(p1.packetDedupeHex() in ringBuffer.getAllDedupeHashes(t0 + 30))
        assertTrue(p4.packetDedupeHex() in ringBuffer.getAllDedupeHashes(t0 + 30))

        // Priority LIFE_SAFETY_WAKE packet (p3) must be returned first for relay
        val relayList = ringBuffer.getPacketsForRelay(emptySet(), t0 + 30)
        assertEquals(3, relayList.size)
        assertEquals(p3.packetDedupeHex(), relayList[0].packetDedupeHex())
        assertEquals(4, relayList[0].ttlHops) // decremented from 5 to 4

        // Advance clock past 72h + 15s so p2 expires while p3 and p4 remain
        val evicted = ringBuffer.evictExpired(t0 + 72L * 3600L + 15L)
        assertEquals(1, evicted)
        assertEquals(2, ringBuffer.sizePackets())
    }

    @Test
    fun `ram only media vault decrypts strictly in memory and zeroizes on close`() {
        val key = ByteArray(32) { (it + 7).toByte() }
        val mediaVault = RamOnlyMediaVault(key)
        val originalVoiceClip = "CODEC2_700C_FRAME_DATA_SAMPLE".toByteArray(Charsets.UTF_8)
        val callerCopy = originalVoiceClip.copyOf()

        mediaVault.storeEncryptedMedia(
            mediaId = "voice-001",
            mimeType = "audio/x-codec2",
            rawPlaintext = callerCopy,
            zeroizeInput = true
        )
        // Caller's input buffer must be zeroized immediately after encryption
        assertTrue(callerCopy.all { it == 0.toByte() })

        val handle = mediaVault.openInRamOnly("voice-001")
        assertNotNull(handle)
        assertArrayEquals(originalVoiceClip, handle!!.plaintextBytes)

        // Closing the RAM handle must overwrite the decrypted bytes with zeros
        handle.close()
        assertTrue(handle.isClosed)
        assertTrue(handle.plaintextBytes.all { it == 0.toByte() })
    }

    @Test
    fun `duress PIN executes cryptographic erasure in under 100ms and activates decoy state`() {
        val identity = HardwareIdentityManager().also {
            it.initializeHardwareIdentity(fallbackPassphrase = "tactical-operator-alpha")
        }
        val vault = PharosEncryptedVault(
            context = null,
            identityManager = identity,
            normalPin = "2580",
            duressPin = "9911"
        )

        val verifiedCard = identity.signTacticalCard(
            TacticalCard(
                cardId = 501L,
                timestampUtcSec = 1794950100L,
                statusTier = TacticalCard.StatusTier.MEDICAL_TRIAGE,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.2044,
                longitudeDeg = 6.1432,
                altitudeMeters = 375,
                batteryPercent = 81,
                note = "FIELD HOSPITAL COORDINATES CONFIRMED"
            )
        )
        val quarantineCard = TacticalCard(
            cardId = 502L,
            timestampUtcSec = 1794950200L,
            statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
            verificationState = TacticalCard.VerificationState.OPEN_QUARANTINE,
            latitudeDeg = 46.2100,
            longitudeDeg = 6.1500,
            altitudeMeters = 390,
            batteryPercent = 19,
            note = "UNVERIFIED SOS FLARE SPOTTED"
        )

        vault.saveTacticalCard(verifiedCard)
        vault.saveTacticalCard(quarantineCard)

        assertEquals(1, vault.loadCardsByVerification(verifiedOnly = true).size)
        assertEquals(1, vault.loadCardsByVerification(verifiedOnly = false).size)
        assertEquals(
            PharosEncryptedVault.PinUnlockResult.UNLOCKED_TACTICAL_VAULT,
            vault.authenticatePin("2580")
        )

        // Trigger Duress PIN ("9911")
        val unlockResult = vault.authenticatePin("9911")
        assertEquals(PharosEncryptedVault.PinUnlockResult.TRIGGERED_DURESS_DECOY_MODE, unlockResult)
        assertTrue("Duress wipe must complete in <100ms (actual: ${vault.lastDuressEraseLatencyMs}ms)", vault.lastDuressEraseLatencyMs in 0..99)
        assertTrue(vault.isDecoyFirstAidModeActive)
        assertNotNull("Signed NODE_COMPROMISED tombstone must be queued", vault.pendingNodeCompromisedTombstone)

        // Verify real cards are gone and benign First-Aid decoy cards are shown
        val decoyCards = vault.loadCardsByVerification(verifiedOnly = true)
        assertEquals(2, decoyCards.size)
        assertTrue(decoyCards.any { it.note.contains("FIRST AID GUIDE") })
        assertTrue(vault.loadCardsByVerification(verifiedOnly = false).isEmpty())
        assertNull(vault.mediaVault.openInRamOnly("voice-001"))
    }
}
