package org.pharos.core.crypto

import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.PacketFlag
import org.pharos.core.protocol.PayloadType
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 8439 ChaCha20-Poly1305 & draft-irtf-cfrg-xchacha XChaCha20-Poly1305 AEAD Engine,
 * plus Blinded Epoch Tagging for 256-byte PHAROS Blind Packets.
 */
object XChaCha20Poly1305Engine {
    private val P_130_5 = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))
    private val MASK_128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
    private val secureRandom = SecureRandom()

    private fun rotl32(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun load32Le(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun store32Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = rotl32(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = rotl32(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = rotl32(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = rotl32(s[b] xor s[c], 7)
    }

    /**
     * HChaCha20 subkey derivation (draft-irtf-cfrg-xchacha Section 2.2).
     * Takes 32-byte key and 16-byte nonce, returns 32-byte subkey.
     */
    fun hChaCha20(key32: ByteArray, nonce16: ByteArray): ByteArray {
        require(key32.size == 32) { "HChaCha20 requires 32-byte key" }
        require(nonce16.size == 16) { "HChaCha20 requires 16-byte nonce" }

        val s = intArrayOf(
            0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
            load32Le(key32, 0), load32Le(key32, 4), load32Le(key32, 8), load32Le(key32, 12),
            load32Le(key32, 16), load32Le(key32, 20), load32Le(key32, 24), load32Le(key32, 28),
            load32Le(nonce16, 0), load32Le(nonce16, 4), load32Le(nonce16, 8), load32Le(nonce16, 12)
        )

        repeat(10) {
            quarterRound(s, 0, 4, 8, 12)
            quarterRound(s, 1, 5, 9, 13)
            quarterRound(s, 2, 6, 10, 14)
            quarterRound(s, 3, 7, 11, 15)
            quarterRound(s, 0, 5, 10, 15)
            quarterRound(s, 1, 6, 11, 12)
            quarterRound(s, 2, 7, 8, 13)
            quarterRound(s, 3, 4, 9, 14)
        }

        val out = ByteArray(32)
        store32Le(out, 0, s[0])
        store32Le(out, 4, s[1])
        store32Le(out, 8, s[2])
        store32Le(out, 12, s[3])
        store32Le(out, 16, s[12])
        store32Le(out, 20, s[13])
        store32Le(out, 24, s[14])
        store32Le(out, 28, s[15])
        return out
    }

    fun chaCha20Block(key32: ByteArray, counter: Int, nonce12: ByteArray): ByteArray {
        val init = intArrayOf(
            0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
            load32Le(key32, 0), load32Le(key32, 4), load32Le(key32, 8), load32Le(key32, 12),
            load32Le(key32, 16), load32Le(key32, 20), load32Le(key32, 24), load32Le(key32, 28),
            counter, load32Le(nonce12, 0), load32Le(nonce12, 4), load32Le(nonce12, 8)
        )
        val s = init.copyOf()
        repeat(10) {
            quarterRound(s, 0, 4, 8, 12)
            quarterRound(s, 1, 5, 9, 13)
            quarterRound(s, 2, 6, 10, 14)
            quarterRound(s, 3, 7, 11, 15)
            quarterRound(s, 0, 5, 10, 15)
            quarterRound(s, 1, 6, 11, 12)
            quarterRound(s, 2, 7, 8, 13)
            quarterRound(s, 3, 4, 9, 14)
        }
        val out = ByteArray(64)
        for (i in 0 until 16) {
            store32Le(out, i * 4, s[i] + init[i])
        }
        return out
    }

    fun chaCha20Xor(key32: ByteArray, initialCounter: Int, nonce12: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var offset = 0
        var counter = initialCounter
        while (offset < input.size) {
            val block = chaCha20Block(key32, counter++, nonce12)
            val len = minOf(64, input.size - offset)
            for (i in 0 until len) {
                out[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += len
        }
        return out
    }

    /**
     * RFC 8439 Section 2.5 Poly1305 one-time authenticator.
     */
    fun poly1305Mac(msg: ByteArray, oneTimeKey32: ByteArray): ByteArray {
        require(oneTimeKey32.size == 32)
        val rBytes = oneTimeKey32.copyOfRange(0, 16)
        // Clamp r per RFC 8439 Section 2.5
        rBytes[3] = (rBytes[3].toInt() and 15).toByte()
        rBytes[7] = (rBytes[7].toInt() and 15).toByte()
        rBytes[11] = (rBytes[11].toInt() and 15).toByte()
        rBytes[15] = (rBytes[15].toInt() and 15).toByte()
        rBytes[4] = (rBytes[4].toInt() and 252).toByte()
        rBytes[8] = (rBytes[8].toInt() and 252).toByte()
        rBytes[12] = (rBytes[12].toInt() and 252).toByte()

        val r = littleEndianToBigInt(rBytes)
        val s = littleEndianToBigInt(oneTimeKey32.copyOfRange(16, 32))

        var acc = BigInteger.ZERO
        var offset = 0
        while (offset < msg.size) {
            val len = minOf(16, msg.size - offset)
            val blockLe = ByteArray(len + 1)
            System.arraycopy(msg, offset, blockLe, 0, len)
            blockLe[len] = 0x01 // high bit padding
            val n = littleEndianToBigInt(blockLe)
            acc = acc.add(n).multiply(r).mod(P_130_5)
            offset += len
        }
        acc = acc.add(s).and(MASK_128)
        return bigIntToLittleEndian16(acc)
    }

    private fun littleEndianToBigInt(le: ByteArray): BigInteger {
        val be = ByteArray(le.size + 1)
        for (i in le.indices) {
            be[le.size - i] = le[i]
        }
        return BigInteger(be)
    }

    private fun bigIntToLittleEndian16(v: BigInteger): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(16)
        for (i in 0 until 16) {
            val beIdx = be.size - 1 - i
            out[i] = if (beIdx >= 0) be[beIdx] else 0
        }
        return out
    }

    private fun buildPoly1305AeadInput(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val aadPad = (16 - (aad.size % 16)) % 16
        val ctPad = (16 - (ciphertext.size % 16)) % 16
        val buf = ByteBuffer.allocate(aad.size + aadPad + ciphertext.size + ctPad + 16)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(aad)
        repeat(aadPad) { buf.put(0) }
        buf.put(ciphertext)
        repeat(ctPad) { buf.put(0) }
        buf.putLong(aad.size.toLong())
        buf.putLong(ciphertext.size.toLong())
        return buf.array()
    }

    /**
     * Encrypts [plaintext] with 32-byte [key32], 24-byte [nonce24], and [associatedData]
     * using XChaCha20-Poly1305 AEAD. Returns Pair(ciphertext, tag16).
     */
    fun encryptXChaCha20Poly1305(
        key32: ByteArray,
        nonce24: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): Pair<ByteArray, ByteArray> {
        require(key32.size == 32) { "Key must be 32 bytes" }
        require(nonce24.size == 24) { "Nonce must be 24 bytes" }

        val subkey = hChaCha20(key32, nonce24.copyOfRange(0, 16))
        val chachaNonce12 = ByteArray(12)
        System.arraycopy(nonce24, 16, chachaNonce12, 4, 8)

        val polyKeyBlock = chaCha20Block(subkey, 0, chachaNonce12)
        val polyKey32 = polyKeyBlock.copyOfRange(0, 32)

        val ciphertext = if (NativeCryptoBridge.isNativeLoaded) {
            NativeCryptoBridge.nativeXChaCha20Xor(key32, nonce24, plaintext)
                ?: chaCha20Xor(subkey, 1, chachaNonce12, plaintext)
        } else {
            chaCha20Xor(subkey, 1, chachaNonce12, plaintext)
        }

        val aeadInput = buildPoly1305AeadInput(associatedData, ciphertext)
        val tag16 = poly1305Mac(aeadInput, polyKey32)

        NativeCryptoBridge.secureZeroize(subkey, polyKeyBlock, polyKey32)
        return Pair(ciphertext, tag16)
    }

    /**
     * Verifies and decrypts XChaCha20-Poly1305 ciphertext in constant time.
     * Returns decrypted plaintext or null if authentication fails.
     */
    fun decryptXChaCha20Poly1305(
        key32: ByteArray,
        nonce24: ByteArray,
        ciphertext: ByteArray,
        expectedTag16: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray? {
        if (key32.size != 32 || nonce24.size != 24 || expectedTag16.size != 16) return null

        val subkey = hChaCha20(key32, nonce24.copyOfRange(0, 16))
        val chachaNonce12 = ByteArray(12)
        System.arraycopy(nonce24, 16, chachaNonce12, 4, 8)

        val polyKeyBlock = chaCha20Block(subkey, 0, chachaNonce12)
        val polyKey32 = polyKeyBlock.copyOfRange(0, 32)

        val aeadInput = buildPoly1305AeadInput(associatedData, ciphertext)
        val computedTag16 = poly1305Mac(aeadInput, polyKey32)

        val tagMatches = MessageDigest.isEqual(computedTag16, expectedTag16)
        if (!tagMatches) {
            NativeCryptoBridge.secureZeroize(subkey, polyKeyBlock, polyKey32)
            return null
        }

        val plaintext = if (NativeCryptoBridge.isNativeLoaded) {
            NativeCryptoBridge.nativeXChaCha20Xor(key32, nonce24, ciphertext)
                ?: chaCha20Xor(subkey, 1, chachaNonce12, ciphertext)
        } else {
            chaCha20Xor(subkey, 1, chachaNonce12, ciphertext)
        }

        NativeCryptoBridge.secureZeroize(subkey, polyKeyBlock, polyKey32)
        return plaintext
    }

    /**
     * Generates a 16-byte Blinded Epoch Tag for a 15-minute UTC window:
     * [0..7]  = 8-byte cryptographic random salt
     * [8..15] = HMAC-SHA256(channelKey32, epoch15MinSlot || salt8)[0..7]
     */
    fun generateBlindedEpochNonce16(
        channelKey32: ByteArray,
        epochSlot15Min: Int = (System.currentTimeMillis() / (15 * 60 * 1000L)).toInt()
    ): ByteArray {
        val salt8 = ByteArray(8)
        secureRandom.nextBytes(salt8)
        val tag8 = computeEpochTag8(channelKey32, epochSlot15Min, salt8)
        return salt8 + tag8
    }

    fun matchesBlindedEpochNonce(
        channelKey32: ByteArray,
        nonce16: ByteArray,
        referenceEpochSlot15Min: Int = (System.currentTimeMillis() / (15 * 60 * 1000L)).toInt(),
        slotTolerance: Int = 2
    ): Boolean {
        if (nonce16.size != 16) return false
        val salt8 = nonce16.copyOfRange(0, 8)
        val actualTag8 = nonce16.copyOfRange(8, 16)
        for (delta in -slotTolerance..slotTolerance) {
            val candidate = computeEpochTag8(channelKey32, (referenceEpochSlot15Min + delta) and 0xFFFFFF, salt8)
            if (MessageDigest.isEqual(candidate, actualTag8)) return true
        }
        return false
    }

    private fun computeEpochTag8(channelKey32: ByteArray, slot: Int, salt8: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(channelKey32, "HmacSHA256"))
        val slotBytes = ByteBuffer.allocate(4).putInt(slot and 0xFFFFFF).array()
        mac.update("PHAROS_EPOCH_TAG_V1".toByteArray(Charsets.UTF_8))
        mac.update(slotBytes)
        return mac.doFinal(salt8).copyOfRange(0, 8)
    }

    fun expandPacketNonceTo24(nonce16: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("PHAROS_XCHACHA24_EXT".toByteArray(Charsets.UTF_8))
        val ext8 = md.digest(nonce16).copyOfRange(0, 8)
        return nonce16 + ext8
    }

    /**
     * Seals a 200-byte payload into a canonical 256-byte [BlindPacket].
     */
    fun sealBlindPacket(
        channelKey32: ByteArray,
        plaintextPayload200: ByteArray,
        ttlHops: Int = BlindPacket.DEFAULT_TTL_HOPS,
        packetFlags: Int = PacketFlag.BLIND_MULE_ELIGIBLE,
        payloadType: PayloadType = PayloadType.TACTICAL_CARD,
        fragmentIndex: Int = 0,
        fragmentTotal: Int = 1,
        epochSlot15Min: Int = (System.currentTimeMillis() / (15 * 60 * 1000L)).toInt()
    ): BlindPacket {
        val normalizedPlaintext = ByteArray(BlindPacket.PAYLOAD_SIZE)
        System.arraycopy(
            plaintextPayload200,
            0,
            normalizedPlaintext,
            0,
            minOf(plaintextPayload200.size, BlindPacket.PAYLOAD_SIZE)
        )

        val nonce16 = generateBlindedEpochNonce16(channelKey32, epochSlot15Min)
        val nonce24 = expandPacketNonceTo24(nonce16)

        val adHeader8 = BlindPacket.encodeHeader8(
            ttlHops = 0, // zeroed in AD so intermediate Blind Mules can decrement TTL
            packetFlags = packetFlags,
            payloadType = payloadType,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            epochSlot15Min = epochSlot15Min and 0xFFFFFF
        )
        val aad24 = nonce16 + adHeader8

        val (ciphertext200, polyTag16) = encryptXChaCha20Poly1305(
            key32 = channelKey32,
            nonce24 = nonce24,
            plaintext = normalizedPlaintext,
            associatedData = aad24
        )

        return BlindPacket.create(
            ephemeralNonce = nonce16,
            ttlHops = ttlHops,
            packetFlags = packetFlags,
            payloadType = payloadType,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            epochSlot15Min = epochSlot15Min,
            encryptedPayload = ciphertext200,
            poly1305Tag = polyTag16
        )
    }

    /**
     * Trial-decrypts a 256-byte [BlindPacket] against a candidate [channelKey32].
     * Returns the 200-byte plaintext payload if authentic, or null if not for this key.
     */
    fun openBlindPacket(
        channelKey32: ByteArray,
        packet: BlindPacket,
        enforceEpochWindow: Boolean = false
    ): ByteArray? {
        if (enforceEpochWindow && !matchesBlindedEpochNonce(channelKey32, packet.ephemeralNonce, packet.epochSlot15Min)) {
            return null
        }
        val nonce24 = expandPacketNonceTo24(packet.ephemeralNonce)
        val aad24 = packet.authenticatedData24()
        return decryptXChaCha20Poly1305(
            key32 = channelKey32,
            nonce24 = nonce24,
            ciphertext = packet.encryptedPayload,
            expectedTag16 = packet.poly1305Tag,
            associatedData = aad24
        )
    }
}
