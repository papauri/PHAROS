package org.pharos.core.storage

import org.pharos.core.crypto.NativeCryptoBridge
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.protocol.PacketFlag
import java.util.LinkedHashMap

/**
 * 50 MB / 72-Hour FIFO Blind Mule Store-and-Forward Ring Buffer.
 *
 * Stores uniform 256-byte encrypted [BlindPacket] capsules for delay-tolerant Firefly mesh relaying
 * without knowing sender, receiver, or plaintext contents.
 */
class BlindMuleRingBuffer(
    val maxCapacityBytes: Long = DEFAULT_MAX_BYTES_50MB,
    val maxRetentionSeconds: Long = DEFAULT_RETENTION_72H_SEC
) {
    companion object {
        const val DEFAULT_MAX_BYTES_50MB: Long = 50L * 1024L * 1024L // 52,428,800 bytes
        const val DEFAULT_RETENTION_72H_SEC: Long = 72L * 3600L       // 259,200 seconds
    }

    data class StoredBlindCapsule(
        val dedupeHex: String,
        val wireBytes256: ByteArray,
        val ingestedAtUtcSec: Long,
        val isPriorityLifeSafety: Boolean
    )

    private val lock = Any()
    private val fifoMap = LinkedHashMap<String, StoredBlindCapsule>(1024, 0.75f, false)
    private var currentBytesUsed: Long = 0L

    /**
     * Inserts a [BlindPacket] into the ring buffer if not already present and TTL > 0.
     * Automatically evicts expired (>72h) and oldest FIFO capsules to stay within [maxCapacityBytes].
     */
    fun ingestPacket(packet: BlindPacket, nowUtcSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        if (packet.ttlHops <= 0) return false
        val dedupeHex = packet.packetDedupeHex()
        val wire = packet.toWireBytes()
        val isLifeSafety = (packet.packetFlags and PacketFlag.LIFE_SAFETY_WAKE) != 0

        synchronized(lock) {
            evictExpiredLocked(nowUtcSec)
            if (fifoMap.containsKey(dedupeHex)) {
                return false
            }

            // Evict oldest FIFO entries until there is room for this 256-byte packet
            while (currentBytesUsed + wire.size > maxCapacityBytes && fifoMap.isNotEmpty()) {
                val oldestKey = fifoMap.entries.iterator().next().key
                val removed = fifoMap.remove(oldestKey)
                if (removed != null) {
                    currentBytesUsed -= removed.wireBytes256.size
                    NativeCryptoBridge.secureZeroize(removed.wireBytes256)
                }
            }

            if (currentBytesUsed + wire.size > maxCapacityBytes) {
                return false
            }

            fifoMap[dedupeHex] = StoredBlindCapsule(
                dedupeHex = dedupeHex,
                wireBytes256 = wire,
                ingestedAtUtcSec = nowUtcSec,
                isPriorityLifeSafety = isLifeSafety
            )
            currentBytesUsed += wire.size
            return true
        }
    }

    /**
     * Purges all capsules older than [maxRetentionSeconds] (72 hours).
     */
    fun evictExpired(nowUtcSec: Long = System.currentTimeMillis() / 1000L): Int {
        synchronized(lock) {
            return evictExpiredLocked(nowUtcSec)
        }
    }

    private fun evictExpiredLocked(nowUtcSec: Long): Int {
        val cutoff = nowUtcSec - maxRetentionSeconds
        val iterator = fifoMap.entries.iterator()
        var evictedCount = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.ingestedAtUtcSec < cutoff) {
                currentBytesUsed -= entry.value.wireBytes256.size
                NativeCryptoBridge.secureZeroize(entry.value.wireBytes256)
                iterator.remove()
                evictedCount++
            } else {
                // LinkedHashMap is insertion-ordered, so once we hit a non-expired capsule we can stop
                break
            }
        }
        return evictedCount
    }

    /**
     * Returns all active capsules missing from [peerKnownDedupeHexSet], ordered with
     * LIFE_SAFETY_WAKE packets first, with TTL hop decremented for relay.
     */
    fun getPacketsForRelay(
        peerKnownDedupeHexSet: Set<String>,
        nowUtcSec: Long = System.currentTimeMillis() / 1000L
    ): List<BlindPacket> {
        synchronized(lock) {
            evictExpiredLocked(nowUtcSec)
            return fifoMap.values
                .filter { it.dedupeHex !in peerKnownDedupeHexSet }
                .sortedByDescending { it.isPriorityLifeSafety }
                .mapNotNull { capsule ->
                    val (pkt, _) = BlindPacket.fromWireBytes(capsule.wireBytes256)
                    pkt.decrementHopTtl()
                }
        }
    }

    fun getAllDedupeHashes(nowUtcSec: Long = System.currentTimeMillis() / 1000L): Set<String> {
        synchronized(lock) {
            evictExpiredLocked(nowUtcSec)
            return fifoMap.keys.toSet()
        }
    }

    fun sizePackets(): Int = synchronized(lock) { fifoMap.size }

    fun bytesUsed(): Long = synchronized(lock) { currentBytesUsed }

    /**
     * Cryptographically wipes all buffered capsules in RAM (<10ms) on Duress PIN.
     */
    fun wipeAllOnDuress() {
        synchronized(lock) {
            for (capsule in fifoMap.values) {
                NativeCryptoBridge.secureZeroize(capsule.wireBytes256)
            }
            fifoMap.clear()
            currentBytesUsed = 0L
        }
    }
}
