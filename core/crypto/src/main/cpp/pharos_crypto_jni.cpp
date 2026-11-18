#include <jni.h>
#include <cstdint>
#include <cstring>
#include <vector>
#include <array>

namespace {

inline uint32_t rotl32(uint32_t x, int n) {
    return (x << n) | (x >> (32 - n));
}

inline uint32_t load32_le(const uint8_t* src) {
    return static_cast<uint32_t>(src[0]) |
           (static_cast<uint32_t>(src[1]) << 8) |
           (static_cast<uint32_t>(src[2]) << 16) |
           (static_cast<uint32_t>(src[3]) << 24);
}

inline void store32_le(uint8_t* dst, uint32_t w) {
    dst[0] = static_cast<uint8_t>(w & 0xFF);
    dst[1] = static_cast<uint8_t>((w >> 8) & 0xFF);
    dst[2] = static_cast<uint8_t>((w >> 16) & 0xFF);
    dst[3] = static_cast<uint8_t>((w >> 24) & 0xFF);
}

inline void quarter_round(uint32_t& a, uint32_t& b, uint32_t& c, uint32_t& d) {
    a += b; d ^= a; d = rotl32(d, 16);
    c += d; b ^= c; b = rotl32(b, 12);
    a += b; d ^= a; d = rotl32(d, 8);
    c += d; b ^= c; b = rotl32(b, 7);
}

// RFC 8439 / draft-irtf-cfrg-xchacha HChaCha20 subkey derivation
void hchacha20(const uint8_t key[32], const uint8_t nonce16[16], uint8_t subkey_out[32]) {
    uint32_t s[16] = {
        0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
        load32_le(key + 0),  load32_le(key + 4),  load32_le(key + 8),  load32_le(key + 12),
        load32_le(key + 16), load32_le(key + 20), load32_le(key + 24), load32_le(key + 28),
        load32_le(nonce16 + 0), load32_le(nonce16 + 4), load32_le(nonce16 + 8), load32_le(nonce16 + 12)
    };

    for (int i = 0; i < 10; ++i) {
        quarter_round(s[0], s[4], s[8],  s[12]);
        quarter_round(s[1], s[5], s[9],  s[13]);
        quarter_round(s[2], s[6], s[10], s[14]);
        quarter_round(s[3], s[7], s[11], s[15]);
        quarter_round(s[0], s[5], s[10], s[15]);
        quarter_round(s[1], s[6], s[11], s[12]);
        quarter_round(s[2], s[7], s[8],  s[13]);
        quarter_round(s[3], s[4], s[9],  s[14]);
    }

    store32_le(subkey_out + 0,  s[0]);
    store32_le(subkey_out + 4,  s[1]);
    store32_le(subkey_out + 8,  s[2]);
    store32_le(subkey_out + 12, s[3]);
    store32_le(subkey_out + 16, s[12]);
    store32_le(subkey_out + 20, s[13]);
    store32_le(subkey_out + 24, s[14]);
    store32_le(subkey_out + 28, s[15]);
}

void chacha20_block(const uint8_t key[32], uint32_t counter, const uint8_t nonce12[12], uint8_t block_out[64]) {
    uint32_t init[16] = {
        0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
        load32_le(key + 0),  load32_le(key + 4),  load32_le(key + 8),  load32_le(key + 12),
        load32_le(key + 16), load32_le(key + 20), load32_le(key + 24), load32_le(key + 28),
        counter, load32_le(nonce12 + 0), load32_le(nonce12 + 4), load32_le(nonce12 + 8)
    };
    uint32_t s[16];
    std::memcpy(s, init, sizeof(init));

    for (int i = 0; i < 10; ++i) {
        quarter_round(s[0], s[4], s[8],  s[12]);
        quarter_round(s[1], s[5], s[9],  s[13]);
        quarter_round(s[2], s[6], s[10], s[14]);
        quarter_round(s[3], s[7], s[11], s[15]);
        quarter_round(s[0], s[5], s[10], s[15]);
        quarter_round(s[1], s[6], s[11], s[12]);
        quarter_round(s[2], s[7], s[8],  s[13]);
        quarter_round(s[3], s[4], s[9],  s[14]);
    }

    for (int i = 0; i < 16; ++i) {
        store32_le(block_out + i * 4, s[i] + init[i]);
    }
}

void chacha20_xor(const uint8_t key[32], uint32_t initial_counter, const uint8_t nonce12[12],
                  const uint8_t* in, uint8_t* out, size_t len) {
    uint8_t block[64];
    uint32_t counter = initial_counter;
    size_t offset = 0;
    while (offset < len) {
        chacha20_block(key, counter++, nonce12, block);
        size_t chunk = (len - offset < 64) ? (len - offset) : 64;
        for (size_t i = 0; i < chunk; ++i) {
            out[offset + i] = in[offset + i] ^ block[i];
        }
        offset += chunk;
    }
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_pharos_core_crypto_NativeCryptoBridge_nativeXChaCha20Xor(
    JNIEnv* env,
    jclass,
    jbyteArray key32,
    jbyteArray nonce24,
    jbyteArray input) {

    jsize keyLen = env->GetArrayLength(key32);
    jsize nonceLen = env->GetArrayLength(nonce24);
    jsize inLen = env->GetArrayLength(input);
    if (keyLen != 32 || nonceLen != 24) {
        return nullptr;
    }

    uint8_t k[32];
    uint8_t n[24];
    env->GetByteArrayRegion(key32, 0, 32, reinterpret_cast<jbyte*>(k));
    env->GetByteArrayRegion(nonce24, 0, 24, reinterpret_cast<jbyte*>(n));

    uint8_t subkey[32];
    hchacha20(k, n, subkey);

    uint8_t chacha_nonce[12] = {0, 0, 0, 0};
    std::memcpy(chacha_nonce + 4, n + 16, 8);

    std::vector<uint8_t> inBuf(inLen);
    std::vector<uint8_t> outBuf(inLen);
    if (inLen > 0) {
        env->GetByteArrayRegion(input, 0, inLen, reinterpret_cast<jbyte*>(inBuf.data()));
        chacha20_xor(subkey, 1, chacha_nonce, inBuf.data(), outBuf.data(), static_cast<size_t>(inLen));
    }

    // Zeroize sensitive subkey in RAM
    volatile uint8_t* vp = subkey;
    for (size_t i = 0; i < sizeof(subkey); ++i) vp[i] = 0;

    jbyteArray result = env->NewByteArray(inLen);
    if (inLen > 0) {
        env->SetByteArrayRegion(result, 0, inLen, reinterpret_cast<const jbyte*>(outBuf.data()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_pharos_core_crypto_NativeCryptoBridge_nativeSecureZeroize(
    JNIEnv* env,
    jclass,
    jbyteArray buffer) {
    if (!buffer) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte* elems = env->GetByteArrayElements(buffer, nullptr);
    if (elems) {
        volatile jbyte* p = elems;
        for (jsize i = 0; i < len; ++i) {
            p[i] = 0;
        }
        env->ReleaseByteArrayElements(buffer, elems, 0);
    }
}
