package org.pharos.core.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pharos.core.protocol.BlindPacket
import org.pharos.core.storage.BlindMuleRingBuffer
import java.io.File
import java.net.Socket
import kotlin.random.Random

class MeshUnitTest {

    @Test
    fun `passive GNSS clock synchronizes Firefly 15-minute wake bursts`() {
        val scheduler = FireflyMeshScheduler()
        // Suppose local clock is 12 seconds slow compared to atomic GPS UTC
        val systemNow = 1_794_950_000_000L
        val gnssAtomic = systemNow + 12_000L
        scheduler.recordPassiveGnssFix(gnssAtomic, systemNow)

        assertTrue(scheduler.isGnssLocked)
        assertEquals(gnssAtomic, scheduler.currentSynchronizedUtcMillis(systemNow))

        val nextWake = scheduler.computeNextFireflyBurstUtcMillis(gnssAtomic)
        assertEquals("Next Firefly burst must align to 15-minute (900,000ms) boundary", 0L, nextWake % 900_000L)
        assertTrue(scheduler.isInsideBurstWindow(nextWake + 5_000L))
        assertFalse(scheduler.isInsideBurstWindow(nextWake + 20_000L))
        assertTrue(scheduler.estimateFireflyBatteryEnduranceHours(80) >= 72)
    }

    @Test
    fun `bloom filter set reconciliation identifies missing capsules for burst swap`() {
        val rng = Random(303)
        val nodeARing = BlindMuleRingBuffer()
        val nodeBRing = BlindMuleRingBuffer()

        val pkt1 = BlindPacket.create(rng.nextBytes(16), encryptedPayload = rng.nextBytes(200), poly1305Tag = rng.nextBytes(16))
        val pkt2 = BlindPacket.create(rng.nextBytes(16), encryptedPayload = rng.nextBytes(200), poly1305Tag = rng.nextBytes(16))
        val pkt3 = BlindPacket.create(rng.nextBytes(16), encryptedPayload = rng.nextBytes(200), poly1305Tag = rng.nextBytes(16))

        nodeARing.ingestPacket(pkt1, 1000L)
        nodeARing.ingestPacket(pkt2, 1000L)
        nodeARing.ingestPacket(pkt3, 1000L)

        // Node B only has pkt1
        nodeBRing.ingestPacket(pkt1, 1000L)

        val bBloom64 = BloomSetReconciler.buildBloomFilter64(nodeBRing.getAllDedupeHashes(1000L))
        val deltaForB = BloomSetReconciler.selectMissingPacketsForPeer(nodeARing, bBloom64, 1000L)

        assertEquals(2, deltaForB.size)
        val deltaHashes = deltaForB.map { it.packetDedupeHex() }.toSet()
        assertTrue(pkt2.packetDedupeHex() in deltaHashes)
        assertTrue(pkt3.packetDedupeHex() in deltaHashes)
    }

    @Test
    fun `hotspot server serves offline PWA in playStore flavor and full APK in standalone flavor`() {
        val tempApk = File.createTempFile("pharos-test-universal", ".apk")
        val fakeApkBytes = "PK\u0003\u0004PHAROS_SIGNED_UNIVERSAL_APK_BINARY_PAYLOAD".toByteArray(Charsets.UTF_8)
        tempApk.writeBytes(fakeApkBytes)

        try {
            // 1. Test playStore flavor server
            val playServer = DualSpreaderHotspotServer(
                isPlayStoreFlavor = true,
                hostNodeFingerprint = "A1B2C3D4E5F60718",
                sourceApkFile = tempApk
            )
            val playPort = playServer.start(0)
            try {
                val pwaResponse = httpGet("127.0.0.1", playPort, "/")
                assertTrue(pwaResponse.contains("HTTP/1.1 200 OK"))
                assertTrue(pwaResponse.contains("PHAROS // WEB BRIDGE"))
                assertTrue(pwaResponse.contains("ZERO-INSTALL WEB NODE ACTIVE"))

                val apkForbiddenResponse = httpGet("127.0.0.1", playPort, "/download/pharos-universal.apk")
                assertTrue("playStore flavor must block APK download", apkForbiddenResponse.contains("HTTP/1.1 403 Forbidden"))
            } finally {
                playServer.stop()
            }

            // 2. Test standalone flavor server
            val standaloneServer = DualSpreaderHotspotServer(
                isPlayStoreFlavor = false,
                hostNodeFingerprint = "A1B2C3D4E5F60718",
                sourceApkFile = tempApk
            )
            val standalonePort = standaloneServer.start(0)
            try {
                val pwaResponse = httpGet("127.0.0.1", standalonePort, "/")
                assertTrue(pwaResponse.contains("NATIVE UNIVERSAL APK AVAILABLE OFFLINE"))

                val apkResponse = httpGetBytes("127.0.0.1", standalonePort, "/download/pharos-universal.apk")
                val bodyStart = findHttpBodyStart(apkResponse)
                val downloadedBytes = apkResponse.copyOfRange(bodyStart, apkResponse.size)
                assertArrayEquals(fakeApkBytes, downloadedBytes)
            } finally {
                standaloneServer.stop()
            }
        } finally {
            tempApk.delete()
        }
    }

    private fun httpGet(host: String, port: Int, path: String): String =
        String(httpGetBytes(host, port, path), Charsets.UTF_8)

    private fun httpGetBytes(host: String, port: Int, path: String): ByteArray {
        Socket(host, port).use { s ->
            val req = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\n\r\n"
            s.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
            s.getOutputStream().flush()
            return s.getInputStream().readBytes()
        }
    }

    private fun findHttpBodyStart(raw: ByteArray): Int {
        for (i in 0 until raw.size - 3) {
            if (raw[i] == '\r'.code.toByte() && raw[i + 1] == '\n'.code.toByte() &&
                raw[i + 2] == '\r'.code.toByte() && raw[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
        }
        return 0
    }
}
