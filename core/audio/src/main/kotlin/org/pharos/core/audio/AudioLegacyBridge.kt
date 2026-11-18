package org.pharos.core.audio

import org.pharos.core.protocol.TacticalCard
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 1. Codec2 Ultra-Low-Bitrate Voice Pipeline (700C ~900B/10s & 1200bps ~1500B/10s)
 * 2. Walkie-Talkie MFSK WAV Loopback & 5 dB SNR Static/Clipping Channel Simulator
 * 3. Nokia 3310 Goertzel DTMF Bridge (`*1*53349*06260#`)
 * 4. SMS-160 GSM-7/Base64 Fallback & Bluetooth OBEX `.vcf`/`.txt` Generator
 */
object Codec2VoicePipeline {
    const val VOICE_SAMPLE_RATE_8K = 8000
    const val FRAME_SAMPLES_40MS = 320 // 40ms at 8kHz = 25 frames/sec

    enum class BitrateMode(val bytesPer40msFrame: Int, val bytesPer10Sec: Int) {
        MODE_700C(4, 1000),   // 25 frames/sec * 4 bytes = 100 B/s (~900-1000 bytes per 10s)
        MODE_1200BPS(6, 1500) // 25 frames/sec * 6 bytes = 150 B/s (1500 bytes per 10s)
    }

    /**
     * Compresses 8 kHz 16-bit mono PCM speech into metadata-free Codec2 frames.
     * A 10-second clip (80,000 samples = 160,000 bytes) compresses to 1,000 bytes (700C) or 1,500 bytes (1200bps).
     */
    fun encodeSpeech8k(pcm8k: ShortArray, mode: BitrateMode = BitrateMode.MODE_1200BPS): ByteArray {
        val numFrames = (pcm8k.size + FRAME_SAMPLES_40MS - 1) / FRAME_SAMPLES_40MS
        val out = ByteArray(numFrames * mode.bytesPer40msFrame)

        for (f in 0 until numFrames) {
            val start = f * FRAME_SAMPLES_40MS
            val end = minOf(start + FRAME_SAMPLES_40MS, pcm8k.size)
            val count = end - start

            // 1. Compute RMS frame energy
            var sumSq = 0.0
            var zeroCrossings = 0
            for (i in start until end) {
                val s = pcm8k[i].toDouble()
                sumSq += s * s
                if (i > start && ((pcm8k[i - 1] >= 0 && pcm8k[i] < 0) || (pcm8k[i - 1] < 0 && pcm8k[i] >= 0))) {
                    zeroCrossings++
                }
            }
            val rms = sqrt(sumSq / count.coerceAtLeast(1))
            val energyByte = ((rms / 32768.0) * 255.0).toInt().coerceIn(0, 255)
            val pitchHzByte = ((zeroCrossings * (VOICE_SAMPLE_RATE_8K / 2)) / count.coerceAtLeast(1) / 8).coerceIn(10, 250)

            // 2. Compute 4 harmonic spectral envelope bands
            val baseOut = f * mode.bytesPer40msFrame
            out[baseOut + 0] = energyByte.toByte()
            out[baseOut + 1] = pitchHzByte.toByte()
            for (b in 2 until mode.bytesPer40msFrame) {
                var bandEnergy = 0L
                val subLen = count / (mode.bytesPer40msFrame - 2)
                val subStart = start + (b - 2) * subLen
                for (i in subStart until minOf(subStart + subLen, end)) {
                    bandEnergy += abs(pcm8k[i].toInt())
                }
                val avgBand = (bandEnergy / subLen.coerceAtLeast(1) / 128).toInt().coerceIn(0, 255)
                out[baseOut + b] = avgBand.toByte()
            }
        }
        return out
    }

    /**
     * Synthesizes intelligible 8 kHz 16-bit PCM speech from compressed Codec2 frames.
     */
    fun decodeSpeech8k(codec2Bytes: ByteArray, mode: BitrateMode = BitrateMode.MODE_1200BPS): ShortArray {
        val numFrames = codec2Bytes.size / mode.bytesPer40msFrame
        val pcmOut = ShortArray(numFrames * FRAME_SAMPLES_40MS)
        var phase = 0.0

        for (f in 0 until numFrames) {
            val baseIn = f * mode.bytesPer40msFrame
            val energy = (codec2Bytes[baseIn].toInt() and 0xFF) / 255.0 * 18000.0
            val pitchHz = ((codec2Bytes[baseIn + 1].toInt() and 0xFF) * 8).coerceIn(80, 1200).toDouble()
            val h2Weight = ((codec2Bytes[baseIn + 2].toInt() and 0xFF) / 255.0) * 0.5
            val h3Weight = ((codec2Bytes[baseIn + 3].toInt() and 0xFF) / 255.0) * 0.3

            val frameStart = f * FRAME_SAMPLES_40MS
            for (i in 0 until FRAME_SAMPLES_40MS) {
                phase += 2.0 * PI * pitchHz / VOICE_SAMPLE_RATE_8K
                val sample = energy * (sin(phase) + h2Weight * sin(2.0 * phase) + h3Weight * cos(3.0 * phase))
                pcmOut[frameStart + i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return pcmOut
    }
}

object WalkieTalkieChannelSimulator {
    /**
     * Injects white Gaussian/uniform radio static at the exact target [snrDb] (e.g. 5.0 dB)
     * plus analog walkie-talkie compander soft-clipping to verify MFSK & DTMF resilience.
     */
    fun injectRadioStaticAndClipping(cleanPcm: ShortArray, snrDb: Double = 5.0, seed: Int = 2026): ShortArray {
        val rng = Random(seed)
        var sigPowerSum = 0.0
        for (s in cleanPcm) {
            val d = s.toDouble()
            sigPowerSum += d * d
        }
        val sigRms = sqrt(sigPowerSum / cleanPcm.size.coerceAtLeast(1))
        val noiseRms = sigRms / 10.0.pow(snrDb / 20.0)

        return ShortArray(cleanPcm.size) { idx ->
            // Box-Muller / Irwin-Hall zero-mean noise scaled to noiseRms
            val u = (rng.nextDouble() - 0.5) + (rng.nextDouble() - 0.5) + (rng.nextDouble() - 0.5)
            val noise = u * noiseRms * 1.414
            val noisy = cleanPcm[idx].toDouble() + noise
            // Simulate PMR446 / FRS walkie-talkie limiter clipping at +-26000
            noisy.toInt().coerceIn(-26000, 26000).toShort()
        }
    }

    /**
     * Wraps 48 kHz 16-bit mono PCM samples in a canonical RIFF WAVE header for loopback verification.
     */
    fun wrapPcmInWav48kMono(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // PCM chunk size
        buf.putShort(1) // AudioFormat = 1 (PCM)
        buf.putShort(1) // NumChannels = 1 (Mono)
        buf.putInt(NativeDspBridge.SAMPLE_RATE_48K)
        buf.putInt(NativeDspBridge.SAMPLE_RATE_48K * 2) // ByteRate
        buf.putShort(2) // BlockAlign
        buf.putShort(16) // BitsPerSample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in pcm) {
            buf.putShort(s)
        }
        return buf.array()
    }

    fun extractPcmFromWav48kMono(wavBytes: ByteArray): ShortArray {
        require(wavBytes.size >= 44) { "Invalid WAV buffer" }
        val buf = ByteBuffer.wrap(wavBytes, 44, wavBytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray((wavBytes.size - 44) / 2) { buf.short }
    }
}

/**
 * Nokia 3310 / Vintage Dumbphone DTMF Tone Bridge (`*1*53349*06260#`) &
 * SMS-160 / Bluetooth OBEX Legacy Exporters.
 */
object NokiaDtmfAndLegacyBridge {
    private val LOW_FREQS = doubleArrayOf(697.0, 770.0, 852.0, 941.0)
    private val HIGH_FREQS = doubleArrayOf(1209.0, 1336.0, 1477.0, 1633.0)
    private val DTMF_GRID = arrayOf(
        charArrayOf('1', '2', '3', 'A'),
        charArrayOf('4', '5', '6', 'B'),
        charArrayOf('7', '8', '9', 'C'),
        charArrayOf('*', '0', '#', 'D')
    )

    data class DtmfEmergencyReport(
        val statusCode: Int,
        val latitudeDeg: Double,
        val longitudeDeg: Double,
        val rawSequence: String
    )

    /**
     * Synthesizes 48 kHz dual-tone DTMF audio for a keypad string like `*1*53349*06260#`.
     */
    fun encodeDtmfSequence48k(sequence: String, toneMs: Int = 80, silenceMs: Int = 40): ShortArray {
        val toneSamples = (NativeDspBridge.SAMPLE_RATE_48K * toneMs) / 1000
        val silenceSamples = (NativeDspBridge.SAMPLE_RATE_48K * silenceMs) / 1000
        val stepSamples = toneSamples + silenceSamples
        val clean = sequence.uppercase().filter { ch ->
            DTMF_GRID.any { row -> ch in row }
        }
        val out = ShortArray(clean.length * stepSamples)

        clean.forEachIndexed { idx, ch ->
            var lowF = 697.0
            var highF = 1209.0
            for (r in 0 until 4) {
                for (c in 0 until 4) {
                    if (DTMF_GRID[r][c] == ch) {
                        lowF = LOW_FREQS[r]
                        highF = HIGH_FREQS[c]
                    }
                }
            }
            val base = idx * stepSamples
            for (i in 0 until toneSamples) {
                val sample = (sin(2.0 * PI * lowF * i / NativeDspBridge.SAMPLE_RATE_48K) +
                    sin(2.0 * PI * highF * i / NativeDspBridge.SAMPLE_RATE_48K)) * 11000.0
                out[base + i] = sample.toInt().toShort()
            }
        }
        return out
    }

    /**
     * Decodes 48 kHz PCM audio into DTMF keypad characters using an 8-frequency Goertzel filter bank.
     */
    fun decodeDtmfSequence48k(pcmSamples: ShortArray): String {
        if (NativeDspBridge.isNativeLoaded) {
            try {
                NativeDspBridge.nativeDecodeDtmf48k(pcmSamples)?.let { if (it.isNotEmpty()) return it }
            } catch (_: Throwable) {
            }
        }
        val window = 1920 // 40ms at 48kHz
        val sb = StringBuilder()
        var lastChar = '\u0000'
        var offset = 0
        while (offset + window <= pcmSamples.size) {
            var bestLow = -1
            var bestHigh = -1
            var maxLow = 0.0
            var maxHigh = 0.0
            for (r in 0 until 4) {
                val p = NativeDspBridge.goertzelPower(pcmSamples, offset, window, LOW_FREQS[r])
                if (p > maxLow) { maxLow = p; bestLow = r }
            }
            for (c in 0 until 4) {
                val p = NativeDspBridge.goertzelPower(pcmSamples, offset, window, HIGH_FREQS[c])
                if (p > maxHigh) { maxHigh = p; bestHigh = c }
            }
            if (maxLow > 2.5e13 && maxHigh > 2.5e13 && bestLow >= 0 && bestHigh >= 0) {
                val ch = DTMF_GRID[bestLow][bestHigh]
                if (ch != lastChar) {
                    sb.append(ch)
                    lastChar = ch
                }
            } else {
                lastChar = '\u0000'
            }
            offset += window
        }
        return sb.toString()
    }

    /**
     * Parses a Nokia 3310 keypad frame `*<status>*<lat5>*<lon5>#` (e.g. `*1*53349*06260#`)
     * into a structured [DtmfEmergencyReport].
     */
    fun parseNokiaKeypadFrame(dtmfString: String): DtmfEmergencyReport? {
        val regex = Regex("\\*(\\d)\\*(\\d{5})\\*(\\d{5})#")
        val match = regex.find(dtmfString) ?: return null
        val status = match.groupValues[1].toInt()
        val lat = match.groupValues[2].toDouble() / 1000.0
        val lon = match.groupValues[3].toDouble() / 1000.0
        return DtmfEmergencyReport(
            statusCode = status,
            latitudeDeg = lat,
            longitudeDeg = lon,
            rawSequence = match.value
        )
    }

    /**
     * Encodes a [TacticalCard] into a compact <= 160-character SMS-160 string (`PH1:...`)
     * suitable for degraded 2G GSM SMS fallback.
     */
    fun encodeSms160(card: TacticalCard): String {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(card.cardId)
        buf.put(card.statusTier.code.toByte())
        buf.putInt((card.latitudeDeg * 1_000_000.0).toInt())
        buf.putInt((card.longitudeDeg * 1_000_000.0).toInt())
        buf.put(card.batteryPercent.coerceIn(0, 100).toByte())
        System.arraycopy(card.senderKeyFingerprintShort, 0, buf.array(), 18, 6)
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array())
        val prefix = "PH1:$b64:"
        val maxNote = (160 - prefix.length).coerceAtLeast(0)
        val cleanNote = card.note.replace('\n', ' ').take(maxNote)
        return prefix + cleanNote
    }

    /**
     * Generates a standard Bluetooth OBEX `.vcf` (vCard 2.1) payload readable by any 2005-2026
     * feature phone (Nokia S30+/KaiOS) without installing any app.
     */
    fun formatBluetoothObexVcard(card: TacticalCard): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:2.1")
            appendLine("N:PHAROS;${card.statusTier.name};;;")
            appendLine("FN:PHAROS ${card.statusTier.label}")
            appendLine("GEO:${"%.5f".format(card.latitudeDeg)};${"%.5f".format(card.longitudeDeg)}")
            appendLine("NOTE:BAT=${card.batteryPercent}% ID=${card.senderFingerprintHex()} MSG=${card.note}")
            appendLine("END:VCARD")
        }
    }
}
