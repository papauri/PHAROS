package org.pharos.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary Tactical Card serialized into the 200-byte encrypted payload of a [BlindPacket].
 *
 * Exact Binary Layout (200 bytes max):
 * [0..7]     8B   cardId (int64)
 * [8..11]    4B   timestampUtcSec (uint32)
 * [12]       1B   statusTier (StatusTier enum)
 * [13]       1B   verificationState (VerificationState enum)
 * [14..17]   4B   latitudeMicroDeg (int32, deg * 1e6)
 * [18..21]   4B   longitudeMicroDeg (int32, deg * 1e6)
 * [22..23]   2B   altitudeMeters (int16)
 * [24]       1B   batteryPercent (uint8, 0..100)
 * [25]       1B   attachmentType (AttachmentType enum)
 * [26..33]   8B   senderKeyFingerprintShort (first 8B of Ed25519 pubkey BLAKE2b/SHA256)
 * [34..97]   64B  ed25519Signature (signs bytes [0..33] + noteUtf8)
 * [98]       1B   noteLength (0..101)
 * [99..199]  101B noteUtf8 (UTF-8 tactical note, zero-padded to 200 bytes)
 */
data class TacticalCard(
    val cardId: Long,
    val timestampUtcSec: Long,
    val statusTier: StatusTier,
    val verificationState: VerificationState,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMeters: Int,
    val batteryPercent: Int,
    val attachmentType: AttachmentType = AttachmentType.NONE,
    val senderKeyFingerprintShort: ByteArray = ByteArray(8),
    val ed25519Signature: ByteArray = ByteArray(64),
    val note: String
) {
    enum class StatusTier(val code: Int, val label: String, val triggersCriticalWake: Boolean) {
        LIFE_SAFETY_SOS(0x01, "SOS / LIFE SAFETY", true),
        MEDICAL_TRIAGE(0x02, "MEDICAL / EVAC", true),
        HAZARD_ALERT(0x03, "HAZARD / OBSTACLE", true),
        SAFE_CHECKIN(0x04, "SAFE / HOLDING POSITION", false),
        RESOURCE_DROP(0x05, "WATER / SHELTER / SUPPLY", false),
        RADIO_RELAY(0x06, "WALKIE-TALKIE / DTMF RELAY", false),
        NODE_COMPROMISED_TOMBSTONE(0xFF, "KEY REVOKED / DURESS WIPE", true);

        companion object {
            fun fromCode(code: Int): StatusTier = entries.firstOrNull { it.code == code } ?: SAFE_CHECKIN
        }
    }

    enum class VerificationState(val code: Int) {
        VERIFIED_WOT(0x01),
        OPEN_QUARANTINE(0x02),
        REVOKED_COMPROMISED(0x03);

        companion object {
            fun fromCode(code: Int): VerificationState = entries.firstOrNull { it.code == code } ?: OPEN_QUARANTINE
        }
    }

    enum class AttachmentType(val code: Int, val label: String) {
        NONE(0x00, "No Attachment"),
        CODEC2_700C(0x01, "Codec2 700C Voice (10s / 900B)"),
        CODEC2_1200(0x02, "Codec2 1200bps Voice (10s / 1.5KB)"),
        MICRO_WEBP_240X180(0x03, "RAM-Only 240x180 Stripped Image"),
        OTP_CODEBOOK_REF(0x04, "6x6 Paper OTP Code"),
        SPAKE2_HANDSHAKE(0x05, "SPAKE2 Shared Memory Auth");

        companion object {
            fun fromCode(code: Int): AttachmentType = entries.firstOrNull { it.code == code } ?: NONE
        }
    }

    /**
     * Returns the canonical byte sequence over which the 64-byte Ed25519 signature is computed.
     */
    fun signableBytes(): ByteArray {
        val noteBytes = truncateUtf8(note, MAX_NOTE_BYTES)
        val buf = ByteBuffer.allocate(34 + 1 + noteBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(cardId)
        buf.putInt((timestampUtcSec and 0xFFFFFFFFL).toInt())
        buf.put(statusTier.code.toByte())
        buf.put(verificationState.code.toByte())
        buf.putInt((latitudeDeg * 1_000_000.0).toInt())
        buf.putInt((longitudeDeg * 1_000_000.0).toInt())
        buf.putShort(altitudeMeters.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        buf.put(batteryPercent.coerceIn(0, 100).toByte())
        buf.put(attachmentType.code.toByte())
        val fp = ByteArray(8)
        System.arraycopy(senderKeyFingerprintShort, 0, fp, 0, minOf(senderKeyFingerprintShort.size, 8))
        buf.put(fp)
        buf.put(noteBytes.size.toByte())
        buf.put(noteBytes)
        return buf.array()
    }

    /**
     * Serializes the card into a fixed 200-byte payload buffer ready for XChaCha20-Poly1305 encryption.
     */
    fun toPayload200(): ByteArray {
        val noteBytes = truncateUtf8(note, MAX_NOTE_BYTES)
        val buf = ByteBuffer.allocate(BlindPacket.PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(cardId)
        buf.putInt((timestampUtcSec and 0xFFFFFFFFL).toInt())
        buf.put(statusTier.code.toByte())
        buf.put(verificationState.code.toByte())
        buf.putInt((latitudeDeg * 1_000_000.0).toInt())
        buf.putInt((longitudeDeg * 1_000_000.0).toInt())
        buf.putShort(altitudeMeters.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        buf.put(batteryPercent.coerceIn(0, 100).toByte())
        buf.put(attachmentType.code.toByte())

        val fp = ByteArray(8)
        System.arraycopy(senderKeyFingerprintShort, 0, fp, 0, minOf(senderKeyFingerprintShort.size, 8))
        buf.put(fp)

        val sig = ByteArray(64)
        System.arraycopy(ed25519Signature, 0, sig, 0, minOf(ed25519Signature.size, 64))
        buf.put(sig)

        buf.put(noteBytes.size.toByte())
        buf.put(noteBytes)
        return buf.array()
    }

    fun senderFingerprintHex(): String =
        senderKeyFingerprintShort.joinToString("") { "%02X".format(it) }

    companion object {
        const val MAX_NOTE_BYTES = 101

        fun fromPayload200(bytes: ByteArray): TacticalCard {
            require(bytes.size >= 99) { "Payload too short for TacticalCard: ${bytes.size}" }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val cardId = buf.long
            val timestampUtcSec = buf.int.toLong() and 0xFFFFFFFFL
            val statusTier = StatusTier.fromCode(buf.get().toInt() and 0xFF)
            val verificationState = VerificationState.fromCode(buf.get().toInt() and 0xFF)
            val latMicro = buf.int
            val lonMicro = buf.int
            val altMeters = buf.short.toInt()
            val battery = (buf.get().toInt() and 0xFF).coerceIn(0, 100)
            val attachmentType = AttachmentType.fromCode(buf.get().toInt() and 0xFF)

            val fp = ByteArray(8)
            buf.get(fp)
            val sig = ByteArray(64)
            buf.get(sig)

            val noteLen = (buf.get().toInt() and 0xFF).coerceIn(0, minOf(MAX_NOTE_BYTES, buf.remaining()))
            val noteBytes = ByteArray(noteLen)
            buf.get(noteBytes)
            val note = String(noteBytes, Charsets.UTF_8)

            return TacticalCard(
                cardId = cardId,
                timestampUtcSec = timestampUtcSec,
                statusTier = statusTier,
                verificationState = verificationState,
                latitudeDeg = latMicro / 1_000_000.0,
                longitudeDeg = lonMicro / 1_000_000.0,
                altitudeMeters = altMeters,
                batteryPercent = battery,
                attachmentType = attachmentType,
                senderKeyFingerprintShort = fp,
                ed25519Signature = sig,
                note = note
            )
        }

        private fun truncateUtf8(input: String, maxBytes: Int): ByteArray {
            val raw = input.toByteArray(Charsets.UTF_8)
            if (raw.size <= maxBytes) return raw
            var len = maxBytes
            while (len > 0 && (raw[len].toInt() and 0xC0) == 0x80) {
                len--
            }
            return raw.copyOfRange(0, len)
        }
    }
}
