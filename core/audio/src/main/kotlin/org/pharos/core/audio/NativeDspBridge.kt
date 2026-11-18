package org.pharos.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * JNI Bridge to C++20 NDK libpharos_dsp.so (16 KB ELF page-aligned) with bit-exact
 * 48 kHz DSP Kotlin engine for host JVM unit tests.
 */
object NativeDspBridge {
    const val SAMPLE_RATE_48K = 48000
    const val MFSK_SYMBOL_SAMPLES = 960 // 20 ms per nibble symbol (50 baud)

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("pharos_dsp")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeModulateMfsk48k(payloadBytes: ByteArray): ShortArray?

    @JvmStatic
    external fun nativeDemodulateMfsk48k(pcmSamples: ShortArray, expectedBytes: Int): ByteArray?

    @JvmStatic
    external fun nativeDecodeDtmf48k(pcmSamples: ShortArray): String?

    fun goertzelPower(samples: ShortArray, offset: Int, count: Int, targetFreq: Double, sampleRate: Int = SAMPLE_RATE_48K): Double {
        val k = 0.5 + (count.toDouble() * targetFreq) / sampleRate.toDouble()
        val omega = (2.0 * PI * k) / count.toDouble()
        val coeff = 2.0 * cos(omega)
        var q0: Double
        var q1 = 0.0
        var q2 = 0.0
        for (i in 0 until count) {
            q0 = coeff * q1 - q2 + samples[offset + i].toDouble()
            q2 = q1
            q1 = q0
        }
        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }

    fun modulateMfsk48k(payloadBytes: ByteArray): ShortArray {
        if (isNativeLoaded) {
            try {
                nativeModulateMfsk48k(payloadBytes)?.let { return it }
            } catch (_: Throwable) {
            }
        }
        val totalSymbols = 4 + payloadBytes.size * 2
        val pcm = ShortArray(totalSymbols * MFSK_SYMBOL_SAMPLES)
        val preambleFreqs = doubleArrayOf(600.0, 2500.0, 600.0, 2500.0)
        val ramp = MFSK_SYMBOL_SAMPLES / 20

        for (s in 0 until totalSymbols) {
            val freq = if (s < 4) {
                preambleFreqs[s]
            } else {
                val nibIdx = s - 4
                val b = payloadBytes[nibIdx / 2].toInt() and 0xFF
                val nibble = if (nibIdx % 2 == 0) (b ushr 4) and 0x0F else b and 0x0F
                800.0 + nibble * 100.0
            }
            val base = s * MFSK_SYMBOL_SAMPLES
            for (i in 0 until MFSK_SYMBOL_SAMPLES) {
                val envGain = when {
                    i < ramp -> 0.5 * (1.0 - cos(PI * i / ramp))
                    i >= MFSK_SYMBOL_SAMPLES - ramp -> 0.5 * (1.0 - cos(PI * (MFSK_SYMBOL_SAMPLES - 1 - i) / ramp))
                    else -> 1.0
                }
                val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE_48K) * envGain * 24000.0
                pcm[base + i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return pcm
    }

    fun demodulateMfsk48k(pcmSamples: ShortArray, expectedBytes: Int): ByteArray {
        if (isNativeLoaded) {
            try {
                nativeDemodulateMfsk48k(pcmSamples, expectedBytes)?.let { return it }
            } catch (_: Throwable) {
            }
        }
        val totalSymbols = 4 + expectedBytes * 2
        require(pcmSamples.size >= totalSymbols * MFSK_SYMBOL_SAMPLES) {
            "PCM buffer too short for $expectedBytes MFSK bytes"
        }

        val out = ByteArray(expectedBytes)
        for (b in 0 until expectedBytes) {
            val hiSym = 4 + b * 2
            val loSym = 4 + b * 2 + 1

            var bestHi = 0
            var maxHi = -1.0
            for (n in 0 until 16) {
                val p = goertzelPower(pcmSamples, hiSym * MFSK_SYMBOL_SAMPLES, MFSK_SYMBOL_SAMPLES, 800.0 + n * 100.0)
                if (p > maxHi) {
                    maxHi = p
                    bestHi = n
                }
            }

            var bestLo = 0
            var maxLo = -1.0
            for (n in 0 until 16) {
                val p = goertzelPower(pcmSamples, loSym * MFSK_SYMBOL_SAMPLES, MFSK_SYMBOL_SAMPLES, 800.0 + n * 100.0)
                if (p > maxLo) {
                    maxLo = p
                    bestLo = n
                }
            }

            out[b] = ((bestHi shl 4) or bestLo).toByte()
        }
        return out
    }
}
