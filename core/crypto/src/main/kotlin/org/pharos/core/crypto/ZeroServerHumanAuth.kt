package org.pharos.core.crypto

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Server Human Authentication Suite for PHAROS:
 * 1. Physical QR / NFC 4-Word Tactical Checksum mutual verification
 * 2. SPAKE2 Password-Authenticated Key Exchange (PAKE) using shared human memories
 * 3. 2-Hop Web-of-Trust (WoT) Vouching Certificates & Key Revocation Tombstones
 * 4. Paper 6x6 One-Time Pad (OTP) Codebook Generator
 */
object ZeroServerHumanAuth {

    private val TACTICAL_WORDLIST_64 = arrayOf(
        "ALPHA", "AMBER", "ANVIL", "APEX", "ARGON", "ATLAS", "AURORA", "AXIOM",
        "BASALT", "BEACON", "BISON", "BLADE", "BUNKER", "CANYON", "CEDAR", "COBALT",
        "COMET", "CONDOR", "COPPER", "CORAL", "CRATER", "CIPHER", "DELTA", "DUNE",
        "EMBER", "FALCON", "FJORD", "FLINT", "FORGE", "FROST", "GARNET", "GLACIER",
        "GRANITE", "HARBOR", "HELIOS", "HORIZON", "IRIDIUM", "JAVELIN", "KESTREL", "KRYPTON",
        "LUMEN", "MAGMA", "MANTIS", "MATRIX", "METEOR", "MONOLITH", "NEBULA", "NEXUS",
        "OBSIDIAN", "ONYX", "ORBIT", "OSPREY", "PHAROS", "PHOTON", "PYLON", "QUARTZ",
        "RADAR", "RAVEN", "RIDGE", "SIERRA", "SOLAR", "SUMMIT", "TITAN", "VORTEX"
    )

    /**
     * Computes a deterministic 4-word spoken/visual verification phrase from two peers'
     * 32-byte public keys (order-independent so Alice and Bob see the exact same 4 words).
     */
    fun computeFourWordChecksum(pubKeyA: ByteArray, pubKeyB: ByteArray): String {
        val hexA = pubKeyA.joinToString("") { "%02x".format(it) }
        val hexB = pubKeyB.joinToString("") { "%02x".format(it) }
        val (first, second) = if (hexA <= hexB) Pair(pubKeyA, pubKeyB) else Pair(pubKeyB, pubKeyA)

        val md = MessageDigest.getInstance("SHA-256")
        md.update("PHAROS_4WORD_SAS_V1".toByteArray(Charsets.UTF_8))
        md.update(first)
        val digest = md.digest(second)

        return (0 until 4).joinToString("-") { idx ->
            val wordIndex = (digest[idx].toInt() and 0xFF) % TACTICAL_WORDLIST_64.size
            TACTICAL_WORDLIST_64[wordIndex]
        }
    }

    /**
     * SPAKE2 Password-Authenticated Key Exchange over a 2048-bit RFC 3526 MODP safe prime group.
     * Enables two survivors sharing a low-entropy human memory (e.g. "lake tahoe blue kayak 2019")
     * to derive a 256-bit authenticated channel key over optical or walkie-talkie links
     * with zero offline dictionary attack exposure.
     */
    class Spake2Session(
        val role: Role,
        sharedHumanMemory: String,
        sessionSalt: String = "PHAROS_SPAKE2_DEFAULT_SALT"
    ) {
        enum class Role { INITIATOR_ALICE, RESPONDER_BOB }

        companion object {
            // RFC 3526 2048-bit MODP Safe Prime
            private val P = BigInteger(
                "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                    "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                    "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                    "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                    "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                    "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                    "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                    "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                    "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
                16
            )
            private val Q = P.subtract(BigInteger.ONE).shiftRight(1)
            private val G = BigInteger.valueOf(2)

            // Nothing-up-my-sleeve group generators M and N derived via SHA-256 hash-to-group
            private val M: BigInteger by lazy { deriveGenerator("PHAROS_SPAKE2_GEN_M") }
            private val N: BigInteger by lazy { deriveGenerator("PHAROS_SPAKE2_GEN_N") }

            private fun deriveGenerator(seedStr: String): BigInteger {
                val md = MessageDigest.getInstance("SHA-256")
                val raw = BigInteger(1, md.digest(seedStr.toByteArray(Charsets.UTF_8)))
                return raw.modPow(BigInteger.valueOf(2), P)
            }
        }

        private val w: BigInteger
        private val scalarSecret: BigInteger
        val publicMessageBytes: ByteArray

        init {
            val normalizedMemory = sharedHumanMemory.trim().lowercase().replace(Regex("\\s+"), " ")
            val wBytes = Argon2idKdf.deriveKey32(
                passphrase = normalizedMemory.toByteArray(Charsets.UTF_8),
                salt16 = sessionSalt.toByteArray(Charsets.UTF_8).copyOf(16),
                passes = 2
            )
            w = BigInteger(1, wBytes).mod(Q).add(BigInteger.ONE)

            val rndBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            scalarSecret = BigInteger(1, rndBytes).mod(Q).add(BigInteger.ONE)

            // Alice computes X* = g^x * M^w mod p; Bob computes Y* = g^y * N^w mod p
            val gx = G.modPow(scalarSecret, P)
            val mask = if (role == Role.INITIATOR_ALICE) M.modPow(w, P) else N.modPow(w, P)
            val blindedElement = gx.multiply(mask).mod(P)
            publicMessageBytes = blindedElement.toByteArray()
        }

        data class Spake2EstablishedKey(
            val sharedChannelKey32: ByteArray,
            val localConfirmationMac16: ByteArray,
            val expectedPeerConfirmationMac16: ByteArray,
            val verificationWords: String
        )

        fun processPeerMessage(peerPublicBytes: ByteArray): Spake2EstablishedKey {
            val peerElement = BigInteger(1, peerPublicBytes).mod(P)
            val unblindBase = if (role == Role.INITIATOR_ALICE) N else M
            val maskInv = unblindBase.modPow(w, P).modInverse(P)
            val unblinded = peerElement.multiply(maskInv).mod(P)
            val kShared = unblinded.modPow(scalarSecret, P)

            val (msgAlice, msgBob) = if (role == Role.INITIATOR_ALICE) {
                Pair(publicMessageBytes, peerPublicBytes)
            } else {
                Pair(peerPublicBytes, publicMessageBytes)
            }

            // Transcript TT = SHA-256(msgAlice || msgBob || kShared || w)
            val md = MessageDigest.getInstance("SHA-256")
            md.update("PHAROS_SPAKE2_TRANSCRIPT_V1".toByteArray(Charsets.UTF_8))
            md.update(msgAlice)
            md.update(msgBob)
            md.update(kShared.toByteArray())
            md.update(w.toByteArray())
            val transcriptHash32 = md.digest()

            val channelKey32 = hmacSha256(transcriptHash32, "SPAKE2_CHANNEL_KEY")
            val macAlice16 = hmacSha256(transcriptHash32, "SPAKE2_CONFIRM_ALICE").copyOfRange(0, 16)
            val macBob16 = hmacSha256(transcriptHash32, "SPAKE2_CONFIRM_BOB").copyOfRange(0, 16)
            val words = computeFourWordChecksum(channelKey32, transcriptHash32)

            return if (role == Role.INITIATOR_ALICE) {
                Spake2EstablishedKey(channelKey32, macAlice16, macBob16, words)
            } else {
                Spake2EstablishedKey(channelKey32, macBob16, macAlice16, words)
            }
        }

        private fun hmacSha256(key: ByteArray, label: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(label.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 2-Hop Web-of-Trust (WoT) Vouching Certificate & Revocation Engine.
     */
    data class WotVouchCertificate(
        val voucherPublicKey32: ByteArray,
        val vouchedPublicKey32: ByteArray,
        val issuedAtUtcSec: Long,
        val signature64: ByteArray
    ) {
        fun signableBytes(): ByteArray {
            val buf = ByteBuffer.allocate(32 + 32 + 8)
            buf.put(voucherPublicKey32)
            buf.put(vouchedPublicKey32)
            buf.putLong(issuedAtUtcSec)
            return buf.array()
        }
    }

    class WebOfTrustGraph(private val identityManager: HardwareIdentityManager) {
        // 1-hop directly verified public keys (hex -> 32-byte pubkey)
        private val directTrustedPeers = mutableMapOf<String, ByteArray>()
        // 2-hop vouched public keys (hex -> pair of pubkey and voucher hex)
        private val twoHopTrustedPeers = mutableMapOf<String, Pair<ByteArray, String>>()
        // Revoked / compromised public key fingerprints (hex8)
        private val revokedFingerprints8 = mutableSetOf<String>()

        fun addDirectTrustedPeer(peerPublicKey32: ByteArray) {
            val fpHex = fpHex8(peerPublicKey32)
            if (fpHex !in revokedFingerprints8) {
                directTrustedPeers[fpHex] = peerPublicKey32.copyOf()
            }
        }

        fun issueVouchCertificate(vouchedPublicKey32: ByteArray, nowUtcSec: Long = System.currentTimeMillis() / 1000L): WotVouchCertificate {
            val myPub = identityManager.getCurrentIdentity().publicKeyBytes
            val unsigned = WotVouchCertificate(myPub, vouchedPublicKey32, nowUtcSec, ByteArray(64))
            val sig = identityManager.signDetached64(unsigned.signableBytes())
            return unsigned.copy(signature64 = sig)
        }

        fun ingestVouchCertificate(cert: WotVouchCertificate): Boolean {
            val voucherFp = fpHex8(cert.voucherPublicKey32)
            val vouchedFp = fpHex8(cert.vouchedPublicKey32)
            if (voucherFp in revokedFingerprints8 || vouchedFp in revokedFingerprints8) return false

            // Only accept vouch certificates signed by a 1-hop directly trusted peer (enforces strict 2-hop bound!)
            val trustedVoucherPub = directTrustedPeers[voucherFp] ?: return false
            if (!identityManager.verifyDetached64(trustedVoucherPub, cert.signableBytes(), cert.signature64)) {
                return false
            }
            twoHopTrustedPeers[vouchedFp] = Pair(cert.vouchedPublicKey32.copyOf(), voucherFp)
            return true
        }

        fun ingestRevocationTombstone(tombstoneBytes: ByteArray): Boolean {
            val prefix = "NODE_COMPROMISED:".toByteArray(Charsets.UTF_8)
            if (tombstoneBytes.size != prefix.size + 8 + 64) return false
            val fp8 = tombstoneBytes.copyOfRange(prefix.size, prefix.size + 8)
            val sig64 = tombstoneBytes.copyOfRange(prefix.size + 8, tombstoneBytes.size)
            val fpHex = fp8.joinToString("") { "%02X".format(it) }

            val knownPub = directTrustedPeers[fpHex] ?: twoHopTrustedPeers[fpHex]?.first ?: return false
            val payload = tombstoneBytes.copyOfRange(0, prefix.size + 8)
            if (!identityManager.verifyDetached64(knownPub, payload, sig64)) return false

            revokedFingerprints8.add(fpHex)
            directTrustedPeers.remove(fpHex)
            twoHopTrustedPeers.remove(fpHex)
            // Also revoke anyone vouched exclusively by this compromised node
            val cascade = twoHopTrustedPeers.filterValues { it.second == fpHex }.keys.toList()
            cascade.forEach { twoHopTrustedPeers.remove(it) }
            return true
        }

        fun resolvePeerPublicKey(fingerprint8: ByteArray): ByteArray? {
            val fpHex = fingerprint8.joinToString("") { "%02X".format(it) }
            if (fpHex in revokedFingerprints8) return null
            return directTrustedPeers[fpHex] ?: twoHopTrustedPeers[fpHex]?.first
        }

        fun isRevoked(fingerprint8: ByteArray): Boolean {
            val fpHex = fingerprint8.joinToString("") { "%02X".format(it) }
            return fpHex in revokedFingerprints8
        }

        private fun fpHex8(pub32: ByteArray): String =
            identityManager.computeFingerprint8(pub32).joinToString("") { "%02X".format(it) }
    }

    /**
     * 6x6 Paper One-Time Pad (OTP) Codebook Generator (Rows A..F, Cols 1..6 = 36 tactical codes).
     * Used with dumbphones, analog walkie-talkies, or pocket FM broadcasts.
     */
    data class OtpGridCell(
        val coord: String,       // e.g. "A1" .. "F6"
        val fourDigitPin: String,// e.g. "4829"
        val tacticalMeaning: String
    )

    data class PaperOtpCodebook(
        val codebookId: String,
        val cells: List<OtpGridCell>
    ) {
        fun lookupByCoord(coord: String): OtpGridCell? =
            cells.firstOrNull { it.coord.equals(coord.trim(), ignoreCase = true) }

        fun lookupByPin(fourDigitPin: String): OtpGridCell? =
            cells.firstOrNull { it.fourDigitPin == fourDigitPin.trim() }
    }

    private val TACTICAL_OTP_MEANINGS = listOf(
        "SOS NEED IMMEDIATE EVAC", "MEDICAL TRAUMA / TOURNIQUET", "WATER SOURCE CONTAMINATED",
        "SAFE AT PRIMARY SHELTER", "MOVING TO WAYPOINT ALPHA", "MOVING TO WAYPOINT BRAVO",
        "BRIDGE / ROAD BLOCKED", "RADIO SILENCE FOR 6 HOURS", "BATTERY CRITICAL <10%",
        "ALL CLEAR AT SECTOR", "SUPPLY DROP LOCATED", "FIRE / SMOKE HAZARD",
        "FLOOD RISING MOVE HIGH", "RENDEZVOUS AT DAWN", "RENDEZVOUS AT DUSK",
        "SWITCH TO CHANNEL 2", "SWITCH TO OPTICAL BEAM", "CONFIRM IDENTITY OK",
        "SHELTER CAPACITY FULL", "NEED INSULIN / ANTIBIOTICS", "GENERATOR FUEL LOW",
        "SOLAR CHARGING ACTIVE", "BLIND MULE RELAYING", "HOSTILE / UNSAFE AREA",
        "CHILDREN / ELDERLY PRESENT", "HELICOPTER / DRONE SPOTTED", "LANDSLIDE BLOCKING TRAIL",
        "WATER PURIFIED AVAILABLE", "HOLD POSITION DO NOT MOVE", "ACKNOWLEDGE LAST PACKET",
        "ABORT MISSION / RETREAT", "REPEAT LAST TRANSMISSION", "TIME SYNC TO GPS :00",
        "KEY COMPROMISED REVOKE", "DECOY ACTIVE STAND BY", "MISSION COMPLETE"
    )

    fun generatePaperOtpCodebook(seedKey32: ByteArray? = null): PaperOtpCodebook {
        val rng = if (seedKey32 != null) {
            val md = MessageDigest.getInstance("SHA-256").digest(seedKey32)
            java.util.Random(ByteBuffer.wrap(md).long)
        } else {
            SecureRandom()
        }
        val rows = charArrayOf('A', 'B', 'C', 'D', 'E', 'F')
        val usedPins = mutableSetOf<String>()
        val cells = mutableListOf<OtpGridCell>()

        var idx = 0
        for (r in rows) {
            for (c in 1..6) {
                var pin: String
                do {
                    pin = "%04d".format(rng.nextInt(10000))
                } while (!usedPins.add(pin))
                cells.add(
                    OtpGridCell(
                        coord = "$r$c",
                        fourDigitPin = pin,
                        tacticalMeaning = TACTICAL_OTP_MEANINGS[idx % TACTICAL_OTP_MEANINGS.size]
                    )
                )
                idx++
            }
        }
        val id = "OTP-" + "%04X".format(rng.nextInt(0x10000))
        return PaperOtpCodebook(codebookId = id, cells = cells)
    }
}
