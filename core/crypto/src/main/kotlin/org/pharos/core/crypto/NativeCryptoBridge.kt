package org.pharos.core.crypto

/**
 * JNI Bridge to C++20 NDK libpharos_crypto.so (compiled with 16 KB ELF page alignment).
 * Automatically falls back to the bit-exact RFC 8439 Kotlin engine when running on host JVM unit tests.
 */
object NativeCryptoBridge {
    val isNativeLoaded: Boolean = try {
        System.loadLibrary("pharos_crypto")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic
    external fun nativeXChaCha20Xor(key32: ByteArray, nonce24: ByteArray, input: ByteArray): ByteArray?

    @JvmStatic
    external fun nativeSecureZeroize(buffer: ByteArray)

    fun secureZeroize(vararg buffers: ByteArray?) {
        for (buf in buffers) {
            if (buf == null) continue
            if (isNativeLoaded) {
                try {
                    nativeSecureZeroize(buf)
                    continue
                } catch (_: Throwable) {
                }
            }
            buf.fill(0)
        }
    }
}
