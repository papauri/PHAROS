package org.pharos.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.protocol.TacticalCard

class CryptoUnitTest {

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(Regex("[^0-9a-fA-F]"), "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun `KAT draft-irtf-cfrg-xchacha section 2_2_1 HChaCha20 test vector`() {
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f"
        )
        val nonce16 = hexToBytes("000000090000004a0000000031415927")
        val expectedSubkey = hexToBytes(
            "82413b4227b27bfed30e42508a877d73" +
                "a0f9e4d58a74a853c12ec41326d3ecdc"
        )

        val actualSubkey = XChaCha20Poly1305Engine.hChaCha20(key, nonce16)
        assertArrayEquals("HChaCha20 must match official IETF CFRG test vector", expectedSubkey, actualSubkey)
    }

    @Test
    fun `KAT RFC 8439 section 2_5_2 Poly1305 test vector`() {
        val key32 = hexToBytes(
            "85d6be7857556d337f4452fe42d506a8" +
                "0103808afb0db2fd4abff6af4149f51b"
        )
        val msg = "Cryptographic Forum Research Group".toByteArray(Charsets.US_ASCII)
        val expectedTag = hexToBytes("a8061dc1305136c6c22b8baf0c0127a9")

        val actualTag = XChaCha20Poly1305Engine.poly1305Mac(msg, key32)
        assertArrayEquals("Poly1305 MAC must match RFC 8439 Section 2.5.2 vector", expectedTag, actualTag)
    }

    @Test
    fun `KAT XChaCha20-Poly1305 AEAD encrypt and decrypt with associated data`() {
        val key32 = hexToBytes(
            "808182838485868788898a8b8c8d8e8f" +
                "909192939495969798999a9b9c9d9e9f"
        )
        val nonce24 = hexToBytes("404142434445464748494a4b4c4d4e4f5051525354555657")
        val aad = hexToBytes("50515253c0c1c2c3c4c5c6c7")
        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
            .toByteArray(Charsets.US_ASCII)

        val (ciphertext, tag) = XChaCha20Poly1305Engine.encryptXChaCha20Poly1305(key32, nonce24, plaintext, aad)
        val expectedCiphertext = hexToBytes(
            "bd6d179d3e83d43b9576579493c0e939" +
                "572a1700252bfaccbed2902c21396cbb" +
                "731c7f1b0b4aa6440bf3a82f4eda7e39" +
                "ae64c6708c54c216cb96b72e1213b452" +
                "2f8c9ba40db5d945b11b69b982c1bb9e" +
                "3f3fac2bc369488f76b2383565d3fff9" +
                "21f9664c97637da9768812f615c68b13" +
                "b52e"
        )
        val expectedTag = hexToBytes("c0875924c1c7987947deafd8780acf49")

        assertArrayEquals("XChaCha20 ciphertext must match IETF CFRG vector", expectedCiphertext, ciphertext)
        assertArrayEquals("XChaCha20 Poly1305 tag must match IETF CFRG vector", expectedTag, tag)

        val decrypted = XChaCha20Poly1305Engine.decryptXChaCha20Poly1305(key32, nonce24, ciphertext, tag, aad)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)

        // Tamper with 1 bit in tag -> must return null
        val badTag = tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(XChaCha20Poly1305Engine.decryptXChaCha20Poly1305(key32, nonce24, ciphertext, badTag, aad))
    }

    @Test
    fun `blind packet AEAD seal and trial decryption survives multi-hop TTL decrement`() {
        val channelKey = Argon2idKdf.deriveKey32(
            "RESCUE_ALPHA_CHANNEL".toByteArray(),
            "PHAROS_SALT_2026".toByteArray()
        )
        val wrongKey = Argon2idKdf.deriveKey32(
            "WRONG_CHANNEL_KEY".toByteArray(),
            "PHAROS_SALT_2026".toByteArray()
        )

        val identity = HardwareIdentityManager()
        identity.initializeHardwareIdentity(fallbackPassphrase = "alpha-node-secret-seed")

        val card = identity.signTacticalCard(
            TacticalCard(
                cardId = 9001L,
                timestampUtcSec = 1794950000L,
                statusTier = TacticalCard.StatusTier.LIFE_SAFETY_SOS,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 47.3769,
                longitudeDeg = 8.5417,
                altitudeMeters = 408,
                batteryPercent = 64,
                note = "TRAPPED IN BASEMENT LEVEL 2 - WATER RISING"
            )
        )

        val packet = XChaCha20Poly1305Engine.sealBlindPacket(
            channelKey32 = channelKey,
            plaintextPayload200 = card.toPayload200(),
            ttlHops = 10,
            epochSlot15Min = 12345
        )

        // Intermediate Blind Mule decrements TTL hop twice without knowing channelKey
        val relayedPacket = packet.decrementHopTtl()!!.decrementHopTtl()!!
        assertEquals(8, relayedPacket.ttlHops)

        // Wrong channel key fails fast via blinded epoch tag check
        assertNull(XChaCha20Poly1305Engine.openBlindPacket(wrongKey, relayedPacket, enforceEpochWindow = true))

        // Correct channel key matches blinded epoch tag and authenticates payload
        val openedPayload = XChaCha20Poly1305Engine.openBlindPacket(channelKey, relayedPacket, enforceEpochWindow = true)
        assertNotNull(openedPayload)

        val recoveredCard = TacticalCard.fromPayload200(openedPayload!!)
        assertEquals("TRAPPED IN BASEMENT LEVEL 2 - WATER RISING", recoveredCard.note)
        assertTrue(identity.verifyTacticalCard(recoveredCard, identity.getCurrentIdentity().publicKeyBytes))
    }

    @Test
    fun `SPAKE2 PAKE derives identical channel key from shared human memory`() {
        val alice = ZeroServerHumanAuth.Spake2Session(
            role = ZeroServerHumanAuth.Spake2Session.Role.INITIATOR_ALICE,
            sharedHumanMemory = "  Grandma Cedar Cabin   1998 "
        )
        val bob = ZeroServerHumanAuth.Spake2Session(
            role = ZeroServerHumanAuth.Spake2Session.Role.RESPONDER_BOB,
            sharedHumanMemory = "grandma cedar cabin 1998"
        )

        val aliceRes = alice.processPeerMessage(bob.publicMessageBytes)
        val bobRes = bob.processPeerMessage(alice.publicMessageBytes)

        assertArrayEquals(aliceRes.sharedChannelKey32, bobRes.sharedChannelKey32)
        assertArrayEquals(aliceRes.localConfirmationMac16, bobRes.expectedPeerConfirmationMac16)
        assertArrayEquals(bobRes.localConfirmationMac16, aliceRes.expectedPeerConfirmationMac16)
        assertEquals(aliceRes.verificationWords, bobRes.verificationWords)

        // Intruder with wrong memory derives completely different key & fails MAC
        val mallory = ZeroServerHumanAuth.Spake2Session(
            role = ZeroServerHumanAuth.Spake2Session.Role.RESPONDER_BOB,
            sharedHumanMemory = "wrong cabin memory 1999"
        )
        val aliceWithMallory = alice.processPeerMessage(mallory.publicMessageBytes)
        val malloryRes = mallory.processPeerMessage(alice.publicMessageBytes)
        assertFalse(aliceWithMallory.localConfirmationMac16.contentEquals(malloryRes.expectedPeerConfirmationMac16))
    }

    @Test
    fun `2-hop web of trust vouching and duress tombstone revocation`() {
        val alice = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "alice-seed") }
        val bob = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "bob-seed") }
        val charlie = HardwareIdentityManager().also { it.initializeHardwareIdentity(fallbackPassphrase = "charlie-seed") }

        val aliceWot = ZeroServerHumanAuth.WebOfTrustGraph(alice)
        // Alice directly trusts Bob (1-hop via physical QR scan)
        val bobPub = bob.getCurrentIdentity().publicKeyBytes
        val charliePub = charlie.getCurrentIdentity().publicKeyBytes
        aliceWot.addDirectTrustedPeer(bobPub)

        // Bob vouches for Charlie (2-hop from Alice's perspective)
        val bobWot = ZeroServerHumanAuth.WebOfTrustGraph(bob)
        val vouchCert = bobWot.issueVouchCertificate(charliePub)

        assertTrue("Alice must accept 2-hop vouch certificate signed by 1-hop Bob", aliceWot.ingestVouchCertificate(vouchCert))
        assertNotNull(aliceWot.resolvePeerPublicKey(charlie.getCurrentIdentity().fingerprintShort8))

        // Bob triggers Duress PIN -> emits signed NODE_COMPROMISED tombstone
        val bobTombstone = bob.executeDuressCryptographicErase()
        assertTrue(bob.isErased())
        assertTrue("Alice must verify and apply Bob's duress revocation tombstone", aliceWot.ingestRevocationTombstone(bobTombstone))
        assertTrue(aliceWot.isRevoked(bob.computeFingerprint8(bobPub)))
        // Cascade revocation: Charlie (vouched only by compromised Bob) is also untrusted now
        assertNull(aliceWot.resolvePeerPublicKey(charlie.getCurrentIdentity().fingerprintShort8))
    }

    @Test
    fun `paper 6x6 OTP codebook generates 36 unique cells`() {
        val seed = ByteArray(32) { it.toByte() }
        val codebook = ZeroServerHumanAuth.generatePaperOtpCodebook(seed)
        assertEquals(36, codebook.cells.size)
        val cellA1 = codebook.lookupByCoord("A1")
        assertNotNull(cellA1)
        assertEquals(cellA1, codebook.lookupByPin(cellA1!!.fourDigitPin))
    }
}
