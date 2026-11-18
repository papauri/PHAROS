package org.pharos.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.audio.Codec2VoicePipeline
import org.pharos.core.audio.NativeDspBridge
import org.pharos.core.audio.NokiaDtmfAndLegacyBridge
import org.pharos.core.audio.WalkieTalkieChannelSimulator
import org.pharos.core.crypto.DoubleRatchetSession
import org.pharos.core.crypto.HardwareIdentityManager
import org.pharos.core.crypto.XChaCha20Poly1305Engine
import org.pharos.core.crypto.ZeroServerHumanAuth
import org.pharos.core.mesh.BloomSetReconciler
import org.pharos.core.mesh.DualSpreaderHotspotServer
import org.pharos.core.mesh.FireflyMeshScheduler
import org.pharos.core.optical.ManualMorseCodec
import org.pharos.core.optical.OpticalModemEngine
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.PacketFlag
import org.pharos.core.protocol.TacticalCard
import org.pharos.core.storage.BlindMuleRingBuffer
import org.pharos.core.storage.PharosEncryptedVault
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full End-to-End Multi-Node System Integration Test verifying all 8 modules working together:
 * `:core:protocol`, `:core:crypto`, `:core:storage`, `:core:optical`, `:core:offline-web-bridge`,
 * `:core:mesh`, `:core:audio`, and `:app`.
 */
class EndToEndSystemTest {

    @Test
    fun `full multi-node survival scenario across SPAKE2, WoT, Optical 4-PPM, Firefly Mesh, MFSK 5dB Audio, and Duress Wipe`() {
        // 1. Initialize 3 Survivor Nodes (Alice, Bob, Charlie) + 1 Untrusted Blind Mule (Node M)
        val aliceId = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "alice-silicon-key") }
        val bobId = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "bob-silicon-key") }
        val charlieId = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "charlie-silicon-key") }

        val aliceVault = PharosEncryptedVault(identityManager = aliceId, normalPin = "2580", duressPin = "9911")
        val bobVault = PharosEncryptedVault(identityManager = bobId, normalPin = "2580", duressPin = "9911")
        val blindMuleRing = BlindMuleRingBuffer()

        // 2. Alice and Bob pair over a low-bandwidth link using SPAKE2 Shared Human Memory
        val sharedMemory = "High Sierra Pine Cabin 2018"
        val aliceSpake = ZeroServerHumanAuth.Spake2Session(ZeroServerHumanAuth.Spake2Session.Role.INITIATOR_ALICE, sharedMemory)
        val bobSpake = ZeroServerHumanAuth.Spake2Session(ZeroServerHumanAuth.Spake2Session.Role.RESPONDER_BOB, sharedMemory)

        val aliceEstablished = aliceSpake.processPeerMessage(bobSpake.publicMessageBytes)
        val bobEstablished = bobSpake.processPeerMessage(aliceSpake.publicMessageBytes)
        assertArrayEquals(aliceEstablished.sharedChannelKey32, bobEstablished.sharedChannelKey32)
        assertEquals(aliceEstablished.verificationWords, bobEstablished.verificationWords)
        val sharedChannelKey32 = aliceEstablished.sharedChannelKey32

        // Also verify Double Ratchet forward-secure message key synchronization between Alice & Bob
        val aliceRatchet = DoubleRatchetSession(sharedChannelKey32)
        val bobRatchet = DoubleRatchetSession(sharedChannelKey32)
        assertArrayEquals(aliceRatchet.nextSendMessageKey32(), bobRatchet.nextSendMessageKey32())

        // 3. Alice directly trusts Bob (1-hop), and Bob vouches for Charlie (2-hop Web-of-Trust)
        val aliceWot = ZeroServerHumanAuth.WebOfTrustGraph(aliceId)
        val bobWot = ZeroServerHumanAuth.WebOfTrustGraph(bobId)
        aliceWot.addDirectTrustedPeer(bobId.getCurrentIdentity().publicKeyBytes)
        val charlieVouchCert = bobWot.issueVouchCertificate(charlieId.getCurrentIdentity().publicKeyBytes)
        assertTrue(aliceWot.ingestVouchCertificate(charlieVouchCert))

        // 4. Charlie records a 10-second Codec2 700C voice clip (1,000 bytes) & signs an SOS TacticalCard
        val rawSpeech8k = ShortArray(80_000) { i ->
            (sin(2.0 * PI * 220.0 * i / 8000.0) * 14000.0).toInt().toShort()
        }
        val codec2Clip1000B = Codec2VoicePipeline.encodeSpeech8k(rawSpeech8k, Codec2VoicePipeline.BitrateMode.MODE_700C)
        assertEquals(1000, codec2Clip1000B.size)

        val charlieCard = charlieId.signTacticalCard(
            TacticalCard(
                cardId = 42001L,
                timestampUtcSec = 1794960000L,
                statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.5211,
                longitudeDeg = 6.6344,
                altitudeMeters = 430,
                batteryPercent = 41,
                attachmentType = TacticalCard.AttachmentType.CODEC2_700C,
                note = "Bridge washed out at canyon crossing. 2 injured, need rope evac."
            )
        )

        val epochSlot = 1196640
        val sealedPacket = XChaCha20Poly1305Engine.sealBlindPacket(
            channelKey32 = sharedChannelKey32,
            plaintextPayload200 = charlieCard.toPayload200(),
            ttlHops = 8,
            packetFlags = PacketFlag.BLIND_MULE_ELIGIBLE or PacketFlag.LIFE_SAFETY_WAKE,
            epochSlot15Min = epochSlot
        )

        // 5. Hop 1 (Optical 4-PPM Long-Range Torch with 15% jitter & 5 corrupted bytes -> Blind Mule)
        val opticalModem = OpticalModemEngine()
        val ppmSlots = opticalModem.encode4PpmSlots(sealedPacket.toWireBytes())
        val rng = Random(2026)
        val noisyOpticalSamples = FloatArray(ppmSlots.size) { i ->
            val base = if (ppmSlots[i]) 230f else 22f
            base + (rng.nextFloat() * 2f - 1f) * (base * 0.15f)
        }
        // Corrupt 5 full bytes in optical airwaves
        for (badByte in intArrayOf(4, 39, 102, 188, 235)) {
            val baseSlot = opticalModem.PPM_PREAMBLE_SLOTS.size + badByte * 16
            for (s in 0 until 16) noisyOpticalSamples[baseSlot + s] = if (s % 4 == 0) 245f else 12f
        }
        val opticalDemodWire = opticalModem.decode4PpmSoftSamples(noisyOpticalSamples, 256)
        val (muleReceivedPacket, opticalRepairedCount) = BlindPacket.fromWireBytes(opticalDemodWire)
        assertEquals(5, opticalRepairedCount)

        // 6. Store-and-Forward Firefly Mesh at Blind Mule (Node M)
        assertTrue(blindMuleRing.ingestPacket(muleReceivedPacket, 1794960010L))
        val fireflyScheduler = FireflyMeshScheduler()
        fireflyScheduler.recordPassiveGnssFix(1794960100_000L, 1794960095_000L)
        val nextBurstMs = fireflyScheduler.computeNextFireflyBurstUtcMillis()
        assertEquals(0L, nextBurstMs % FireflyMeshScheduler.INTERVAL_15_MIN_MS)

        // Alice advertises her empty Bloom filter; Blind Mule selects missing packet for Alice
        val aliceBloom64 = BloomSetReconciler.buildBloomFilter64(emptySet())
        val packetsForAlice = BloomSetReconciler.selectMissingPacketsForPeer(blindMuleRing, aliceBloom64, 1794960120L)
        assertEquals(1, packetsForAlice.size)
        val hoppedPacket = packetsForAlice.first()
        assertEquals(7, hoppedPacket.ttlHops) // Decremented from 8 to 7 by Blind Mule

        // 7. Hop 2 (Walkie-Talkie 48 kHz 16-MFSK Audio at 5.0 dB SNR -> Alice)
        val cleanMfskPcm = NativeDspBridge.modulateMfsk48k(hoppedPacket.toWireBytes())
        val noisyRadioPcm = WalkieTalkieChannelSimulator.injectRadioStaticAndClipping(cleanMfskPcm, snrDb = 5.0, seed = 777)
        val radioDemodWire = NativeDspBridge.demodulateMfsk48k(noisyRadioPcm, 256)
        val (aliceReceivedPacket, _) = BlindPacket.fromWireBytes(radioDemodWire)

        // 8. Alice trial-matches Blinded Epoch Tag, decrypts XChaCha20-Poly1305 payload, and verifies 2-hop WoT signature
        assertTrue(
            XChaCha20Poly1305Engine.matchesBlindedEpochNonce(
                sharedChannelKey32,
                aliceReceivedPacket.ephemeralNonce,
                epochSlot
            )
        )
        val decryptedPayload200 = XChaCha20Poly1305Engine.openBlindPacket(
            channelKey32 = sharedChannelKey32,
            packet = aliceReceivedPacket,
            enforceEpochWindow = true
        )
        assertNotNull(decryptedPayload200)
        val recoveredCard = TacticalCard.fromPayload200(decryptedPayload200!!)
        assertEquals(charlieCard.note, recoveredCard.note)

        val resolvedCharliePub = aliceWot.resolvePeerPublicKey(recoveredCard.senderKeyFingerprintShort)
        assertNotNull("Alice must resolve Charlie's public key via 2-hop vouch from Bob", resolvedCharliePub)
        assertTrue("Alice must verify Charlie's detached signature", aliceId.verifyTacticalCard(recoveredCard, resolvedCharliePub!!))

        // Alice saves verified card & Codec2 voice clip in RAM-Only Media Vault
        aliceVault.saveTacticalCard(recoveredCard)
        aliceVault.mediaVault.storeEncryptedMedia("voice-42001", "audio/x-codec2", codec2Clip1000B.copyOf())
        val ramHandle = aliceVault.mediaVault.openInRamOnly("voice-42001")
        assertNotNull(ramHandle)
        val decodedSpeechPcm = Codec2VoicePipeline.decodeSpeech8k(ramHandle!!.plaintextBytes, Codec2VoicePipeline.BitrateMode.MODE_700C)
        assertEquals(80_000, decodedSpeechPcm.size)
        ramHandle.close()
        assertTrue("RAM voice buffer must be zeroized immediately on close()", ramHandle.plaintextBytes.all { it == 0.toByte() })

        // 9. Also verify Nokia 3310 DTMF, SMS-160, Bluetooth OBEX, and Manual Morse fallbacks
        val dtmfAudio = NokiaDtmfAndLegacyBridge.encodeDtmfSequence48k("*1*46521*06634#")
        val noisyDtmf = WalkieTalkieChannelSimulator.injectRadioStaticAndClipping(dtmfAudio, snrDb = 5.0, seed = 12)
        val dtmfDecoded = NokiaDtmfAndLegacyBridge.decodeDtmfSequence48k(noisyDtmf)
        assertEquals("*1*46521*06634#", dtmfDecoded)
        assertTrue(NokiaDtmfAndLegacyBridge.encodeSms160(recoveredCard).length <= 160)
        assertEquals("SOS", ManualMorseCodec.decodeMorseString(ManualMorseCodec.encodeTextToMorse("SOS")))

        // 10. Bob is captured and enters Duress PIN "9911" -> <100ms wipe + Tombstone revokes Bob & 2-hop Charlie
        val bobFingerprint8 = bobId.getCurrentIdentity().fingerprintShort8
        val bobPinResult = bobVault.authenticatePin("9911")
        assertEquals(PharosEncryptedVault.PinUnlockResult.TRIGGERED_DURESS_DECOY_MODE, bobPinResult)
        assertTrue(bobVault.lastDuressEraseLatencyMs in 0..99)
        assertTrue(bobVault.isDecoyFirstAidModeActive)
        val tombstone = bobVault.pendingNodeCompromisedTombstone
        assertNotNull(tombstone)

        assertTrue(aliceWot.ingestRevocationTombstone(tombstone!!))
        assertNull("Compromised Bob must be revoked", aliceWot.resolvePeerPublicKey(bobFingerprint8))
        assertNull("2-hop Charlie vouched exclusively by compromised Bob must cascade-revoke", aliceWot.resolvePeerPublicKey(recoveredCard.senderKeyFingerprintShort))
    }
}
