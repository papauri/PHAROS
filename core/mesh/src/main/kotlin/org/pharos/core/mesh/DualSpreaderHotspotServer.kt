package org.pharos.core.mesh

import org.pharos.core.protocol.BlindPacket
import org.pharos.core.storage.BlindMuleRingBuffer
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread
import org.pharos.core.webbridge.OfflineWebBridgePwa

/**
 * 64-Byte (512-bit, 3-Hash) Bloom Filter for Ultra-Fast BLE / Wi-Fi Direct Set Reconciliation
 * during 15-second Firefly micro-bursts, plus Embedded LocalOnlyHotspot Dual-Spreader HTTP Server.
 */
object BloomSetReconciler {
    private const val FILTER_BYTES = 64
    private const val FILTER_BITS = FILTER_BYTES * 8 // 512 bits

    fun buildBloomFilter64(dedupeHexSet: Set<String>): ByteArray {
        val bits = ByteArray(FILTER_BYTES)
        for (hex in dedupeHexSet) {
            for (pos in hashPositions(hex)) {
                val byteIdx = pos ushr 3
                val bitMask = 1 shl (pos and 7)
                bits[byteIdx] = (bits[byteIdx].toInt() or bitMask).toByte()
            }
        }
        return bits
    }

    fun mightContain(bloomFilter64: ByteArray, dedupeHex: String): Boolean {
        if (bloomFilter64.size != FILTER_BYTES) return false
        for (pos in hashPositions(dedupeHex)) {
            val byteIdx = pos ushr 3
            val bitMask = 1 shl (pos and 7)
            if ((bloomFilter64[byteIdx].toInt() and bitMask) == 0) {
                return false
            }
        }
        return true
    }

    /**
     * Given a peer's 64-byte Bloom filter, selects all local [BlindPacket]s from [ringBuffer]
     * that the peer definitely lacks, decrementing their TTL for burst transmission.
     */
    fun selectMissingPacketsForPeer(
        ringBuffer: BlindMuleRingBuffer,
        peerBloom64: ByteArray,
        nowUtcSec: Long = System.currentTimeMillis() / 1000L
    ): List<BlindPacket> {
        val localHashes = ringBuffer.getAllDedupeHashes(nowUtcSec)
        val probablyKnownByPeer = localHashes.filter { mightContain(peerBloom64, it) }.toSet()
        return ringBuffer.getPacketsForRelay(probablyKnownByPeer, nowUtcSec)
    }

    private fun hashPositions(dedupeHex: String): IntArray {
        val md = MessageDigest.getInstance("SHA-256").digest(dedupeHex.toByteArray(Charsets.UTF_8))
        val h1 = ((md[0].toInt() and 0xFF) or ((md[1].toInt() and 0xFF) shl 8)) % FILTER_BITS
        val h2 = ((md[2].toInt() and 0xFF) or ((md[3].toInt() and 0xFF) shl 8)) % FILTER_BITS
        val h3 = ((md[4].toInt() and 0xFF) or ((md[5].toInt() and 0xFF) shl 8)) % FILTER_BITS
        return intArrayOf(h1, h2, h3)
    }
}

/**
 * Dual-Flavor LocalOnlyHotspot HTTP Server:
 * - In `playStore` flavor (`isPlayStoreFlavor = true`): Serves the Zero-Install Offline HTML5 PWA
 *   at `/` and blocks APK binary downloads to comply 100% with Google Play policy.
 * - In `standalone` flavor (`isPlayStoreFlavor = false`): Serves BOTH the Offline HTML5 PWA at `/`
 *   AND streams the full signed Universal `.apk` (`sourceApkFile`) at `/download/pharos-universal.apk`.
 */
class DualSpreaderHotspotServer(
    val isPlayStoreFlavor: Boolean,
    val hostNodeFingerprint: String,
    val sourceApkFile: File? = null,
    private val ringBuffer: BlindMuleRingBuffer = BlindMuleRingBuffer()
) {
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false

    val boundPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start(port: Int = 0, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")): Int {
        if (isRunning) return boundPort
        val ss = ServerSocket(port, 16, bindAddress)
        serverSocket = ss
        isRunning = true

        thread(name = "pharos-hotspot-http", isDaemon = true) {
            while (isRunning) {
                try {
                    val client = ss.accept()
                    handleClient(client)
                } catch (_: Throwable) {
                    if (!isRunning) break
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"
            val out = s.getOutputStream()

            when {
                path == "/" || path.startsWith("/index.html") -> {
                    val html = OfflineWebBridgePwa.renderSingleFileHtml(
                        hostNodeFingerprint = hostNodeFingerprint,
                        isStandaloneApkAvailable = !isPlayStoreFlavor && (sourceApkFile?.exists() == true)
                    )
                    writeHttpResponse(out, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                }
                path == "/download/pharos-universal.apk" -> {
                    if (isPlayStoreFlavor) {
                        val msg = "PLAY_STORE_FLAVOR: Native APK sideload disabled per Google Play Policy. Use Zero-Install Offline Web Node at /"
                        writeHttpResponse(out, 403, "Forbidden", "text/plain; charset=utf-8", msg.toByteArray(Charsets.UTF_8))
                    } else if (sourceApkFile != null && sourceApkFile.exists()) {
                        val apkBytes = FileInputStream(sourceApkFile).use { it.readBytes() }
                        writeHttpResponse(
                            out,
                            200,
                            "OK",
                            "application/vnd.android.package-archive",
                            apkBytes,
                            extraHeaders = mapOf("Content-Disposition" to "attachment; filename=\"pharos-universal.apk\"")
                        )
                    } else {
                        writeHttpResponse(out, 404, "Not Found", "text/plain", "APK file not found".toByteArray())
                    }
                }
                path == "/api/v1/status" -> {
                    val json = """{"node":"$hostNodeFingerprint","playStoreFlavor":$isPlayStoreFlavor,"bufferedPackets":${ringBuffer.sizePackets()}}"""
                    writeHttpResponse(out, 200, "OK", "application/json", json.toByteArray(Charsets.UTF_8))
                }
                else -> {
                    writeHttpResponse(out, 404, "Not Found", "text/plain", "PHAROS 404".toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun writeHttpResponse(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $statusCode $statusText\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in extraHeaders) {
            sb.append("$k: $v\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    companion object {
        fun resolveHotspotOrLanIpv4(): String {
            return try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (addr in nif.inetAddresses) {
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                return ip
                            }
                        }
                    }
                }
                "192.168.49.1"
            } catch (_: Throwable) {
                "192.168.49.1"
            }
        }
    }
}
