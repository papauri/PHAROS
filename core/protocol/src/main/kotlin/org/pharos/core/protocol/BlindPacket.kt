package org.pharos.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Canonical 256-Byte Blind Packet Wire Specification:
 * [0..15]    16B  Ephemeral Nonce / Blinded Epoch Tag (no sender/receiver ID)
 * [16..23]   8B   TTL Hop Counter + Packet Flags (XOR-masked with SHA-256 stream of [0..15])
 * [24..223]  200B Encrypted Payload (Binary Tactical Card / Codec2 / Micro-AVIF / PAKE step)
 * [224..239] 16B  Poly1305 AEAD Auth Tag
 * [240..255] 16B  Reed-Solomon (255, 240) Parity [240..254] + CRC-8 Framing Byte [255]
 */
data class BlindPacket(
    val ephemeralNonce: ByteArray,     // 16 bytes
    val ttlHops: Int,                  // 0..255
    val packetFlags: Int,              // Bitmask of PacketFlag
    val payloadType: PayloadType,      // 1 byte
    val fragmentIndex: Int,            // 0..255
    val fragmentTotal: Int,            // 1..255
    val epochSlot15Min: Int,           // 24-bit UTC 15-min slot hint (masked)
    val encryptedPayload: ByteArray,   // 200 bytes (padded/fixed)
    val poly1305Tag: ByteArray,        // 16 bytes
    val reedSolomonTrailer: ByteArray  // 16 bytes
) {
    companion object {
        const val PACKET_SIZE = 256
        const val NONCE_SIZE = 16
        const val HEADER_MASKED_SIZE = 8
        const val PAYLOAD_SIZE = 200
        const val AUTH_TAG_SIZE = 16
        const val TRAILER_SIZE = 16
        const val DEFAULT_TTL_HOPS = 12

        fun create(
            ephemeralNonce: ByteArray,
            ttlHops: Int = DEFAULT_TTL_HOPS,
            packetFlags: Int = PacketFlag.BLIND_MULE_ELIGIBLE,
            payloadType: PayloadType = PayloadType.TACTICAL_CARD,
            fragmentIndex: Int = 0,
            fragmentTotal: Int = 1,
            epochSlot15Min: Int = (System.currentTimeMillis() / (15 * 60 * 1000L)).toInt(),
            encryptedPayload: ByteArray,
            poly1305Tag: ByteArray
        ): BlindPacket {
            require(ephemeralNonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes" }
            require(poly1305Tag.size == AUTH_TAG_SIZE) { "Auth tag must be $AUTH_TAG_SIZE bytes" }

            val normalizedPayload = ByteArray(PAYLOAD_SIZE)
            val copyLen = minOf(encryptedPayload.size, PAYLOAD_SIZE)
            System.arraycopy(encryptedPayload, 0, normalizedPayload, 0, copyLen)

            val rawHeader8 = encodeHeader8(
                ttlHops = ttlHops,
                packetFlags = packetFlags,
                payloadType = payloadType,
                fragmentIndex = fragmentIndex,
                fragmentTotal = fragmentTotal,
                epochSlot15Min = epochSlot15Min
            )
            val maskedHeader8 = maskHeader8(ephemeralNonce, rawHeader8)

            val data240 = ByteArray(240)
            System.arraycopy(ephemeralNonce, 0, data240, 0, 16)
            System.arraycopy(maskedHeader8, 0, data240, 16, 8)
            System.arraycopy(normalizedPayload, 0, data240, 24, 200)
            System.arraycopy(poly1305Tag, 0, data240, 224, 16)

            val trailer16 = ReedSolomonCodec.computePacketTrailer16(data240)

            return BlindPacket(
                ephemeralNonce = ephemeralNonce.copyOf(),
                ttlHops = ttlHops.coerceIn(0, 255),
                packetFlags = packetFlags,
                payloadType = payloadType,
                fragmentIndex = fragmentIndex.coerceIn(0, 255),
                fragmentTotal = fragmentTotal.coerceIn(1, 255),
                epochSlot15Min = epochSlot15Min and 0xFFFFFF,
                encryptedPayload = normalizedPayload,
                poly1305Tag = poly1305Tag.copyOf(),
                reedSolomonTrailer = trailer16
            )
        }

        /**
         * Parses a 256-byte wire frame, automatically healing up to 7 arbitrary byte corruptions
         * via Reed-Solomon (255, 240) FEC before extracting the header, payload, and AEAD tag.
         */
        fun fromWireBytes(wireBytes: ByteArray): Pair<BlindPacket, Int> {
            require(wireBytes.size == PACKET_SIZE) {
                "Invalid blind packet length: ${wireBytes.size}, expected $PACKET_SIZE"
            }
            val decoded = ReedSolomonCodec.decodePacket256(wireBytes)
            val data240 = decoded.data
            val nonce = data240.copyOfRange(0, 16)
            val maskedHeader8 = data240.copyOfRange(16, 24)
            val unmaskedHeader8 = maskHeader8(nonce, maskedHeader8)

            val ttlHops = unmaskedHeader8[0].toInt() and 0xFF
            val packetFlags = unmaskedHeader8[1].toInt() and 0xFF
            val payloadType = PayloadType.fromCode(unmaskedHeader8[2].toInt() and 0xFF)
            val fragmentIndex = unmaskedHeader8[3].toInt() and 0xFF
            val fragmentTotal = (unmaskedHeader8[4].toInt() and 0xFF).coerceAtLeast(1)
            val epochSlot = ((unmaskedHeader8[5].toInt() and 0xFF) shl 16) or
                ((unmaskedHeader8[6].toInt() and 0xFF) shl 8) or
                (unmaskedHeader8[7].toInt() and 0xFF)

            val encryptedPayload = data240.copyOfRange(24, 224)
            val poly1305Tag = data240.copyOfRange(224, 240)
            val recomputedTrailer = ReedSolomonCodec.computePacketTrailer16(data240)

            val packet = BlindPacket(
                ephemeralNonce = nonce,
                ttlHops = ttlHops,
                packetFlags = packetFlags,
                payloadType = payloadType,
                fragmentIndex = fragmentIndex,
                fragmentTotal = fragmentTotal,
                epochSlot15Min = epochSlot,
                encryptedPayload = encryptedPayload,
                poly1305Tag = poly1305Tag,
                reedSolomonTrailer = recomputedTrailer
            )
            return Pair(packet, decoded.correctedSymbols)
        }

        fun encodeHeader8(
            ttlHops: Int,
            packetFlags: Int,
            payloadType: PayloadType,
            fragmentIndex: Int,
            fragmentTotal: Int,
            epochSlot15Min: Int
        ): ByteArray {
            return byteArrayOf(
                (ttlHops and 0xFF).toByte(),
                (packetFlags and 0xFF).toByte(),
                (payloadType.code and 0xFF).toByte(),
                (fragmentIndex and 0xFF).toByte(),
                (fragmentTotal.coerceAtLeast(1) and 0xFF).toByte(),
                ((epochSlot15Min ushr 16) and 0xFF).toByte(),
                ((epochSlot15Min ushr 8) and 0xFF).toByte(),
                (epochSlot15Min and 0xFF).toByte()
            )
        }

        fun maskHeader8(nonce16: ByteArray, header8: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update("PHAROS_HDR_MASK_V1".toByteArray(Charsets.UTF_8))
            val mask = md.digest(nonce16)
            return ByteArray(8) { i -> (header8[i].toInt() xor mask[i].toInt()).toByte() }
        }
    }

    /**
     * Returns the 224-byte authenticated header+ciphertext region [0..223]
     * covered by the Poly1305 AEAD tag at [224..239].
     */
    fun authenticatedData24(): ByteArray {
        val rawHeader8 = encodeHeader8(
            ttlHops = ttlHops,
            packetFlags = packetFlags,
            payloadType = payloadType,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            epochSlot15Min = epochSlot15Min
        )
        // Note: TTL hop counter at byte 0 is excluded from AEAD AD so intermediate Blind Mules
        // can decrement TTL without knows keys, while flags/type/fragments/epoch remain authenticated!
        val adHeader8 = rawHeader8.copyOf()
        adHeader8[0] = 0
        val out = ByteArray(24)
        System.arraycopy(ephemeralNonce, 0, out, 0, 16)
        System.arraycopy(adHeader8, 0, out, 16, 8)
        return out
    }

    fun toWireBytes(): ByteArray {
        val rawHeader8 = encodeHeader8(
            ttlHops = ttlHops,
            packetFlags = packetFlags,
            payloadType = payloadType,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            epochSlot15Min = epochSlot15Min
        )
        val maskedHeader8 = maskHeader8(ephemeralNonce, rawHeader8)
        val out = ByteArray(PACKET_SIZE)
        System.arraycopy(ephemeralNonce, 0, out, 0, 16)
        System.arraycopy(maskedHeader8, 0, out, 16, 8)
        System.arraycopy(encryptedPayload, 0, out, 24, 200)
        System.arraycopy(poly1305Tag, 0, out, 224, 16)
        val trailer = ReedSolomonCodec.computePacketTrailer16(out.copyOfRange(0, 240))
        System.arraycopy(trailer, 0, out, 240, 16)
        return out
    }

    /**
     * Decrements TTL hop counter for Blind Mule store-and-forward relaying and updates RS parity.
     */
    fun decrementHopTtl(): BlindPacket? {
        if (ttlHops <= 0) return null
        return create(
            ephemeralNonce = ephemeralNonce,
            ttlHops = ttlHops - 1,
            packetFlags = packetFlags,
            payloadType = payloadType,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            epochSlot15Min = epochSlot15Min,
            encryptedPayload = encryptedPayload,
            poly1305Tag = poly1305Tag
        )
    }

    /**
     * Unique 16-byte deduplication hash of the immutable packet content (ignoring mutable TTL).
     */
    fun packetDedupeHash(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(ephemeralNonce)
        md.update(encryptedPayload)
        md.update(poly1305Tag)
        return md.digest().copyOfRange(0, 16)
    }

    fun packetDedupeHex(): String = packetDedupeHash().joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlindPacket) return false
        return toWireBytes().contentEquals(other.toWireBytes())
    }

    override fun hashCode(): Int = toWireBytes().contentHashCode()
}

object PacketFlag {
    const val BLIND_MULE_ELIGIBLE = 1 shl 0
    const val LIFE_SAFETY_WAKE = 1 shl 1
    const val BROADCAST_OPEN_SOS = 1 shl 2
    const val WOT_VERIFIED_SENDER = 1 shl 3
    const val RATCHET_FORWARD_SEC = 1 shl 4
    const val NODE_COMPROMISED_TOMBSTONE = 1 shl 5
    const val REQUIRES_ACK = 1 shl 6
}

enum class PayloadType(val code: Int) {
    TACTICAL_CARD(0x01),
    CODEC2_VOICE_CHUNK(0x02),
    MICRO_IMAGE_CHUNK(0x03),
    SPAKE2_PAKE_STEP(0x04),
    WOT_VOUCH_CERTIFICATE(0x05),
    KEY_REVOCATION_TOMBSTONE(0x06),
    OTP_GRID_MESSAGE(0x07),
    DTMF_BRIDGE_RELAY(0x08);

    companion object {
        fun fromCode(code: Int): PayloadType = entries.firstOrNull { it.code == code } ?: TACTICAL_CARD
    }
}
