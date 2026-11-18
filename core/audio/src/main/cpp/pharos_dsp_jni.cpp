#include <jni.h>
#include <cstdint>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>

namespace {

constexpr double PI = 3.14159265358979323846;
constexpr int SAMPLE_RATE = 48000;
constexpr int SYMBOL_SAMPLES = 960; // 20 ms per symbol (50 baud -> 25 bytes/sec)

double goertzel_power(const int16_t* samples, int count, double targetFreq, int sampleRate) {
    double k = 0.5 + (static_cast<double>(count) * targetFreq) / static_cast<double>(sampleRate);
    double omega = (2.0 * PI * k) / static_cast<double>(count);
    double coeff = 2.0 * std::cos(omega);
    double q0 = 0.0, q1 = 0.0, q2 = 0.0;
    for (int i = 0; i < count; ++i) {
        q0 = coeff * q1 - q2 + static_cast<double>(samples[i]);
        q2 = q1;
        q1 = q0;
    }
    return q1 * q1 + q2 * q2 - q1 * q2 * coeff;
}

} // namespace

extern "C" JNIEXPORT jshortArray JNICALL
Java_org_pharos_core_audio_NativeDspBridge_nativeModulateMfsk48k(
    JNIEnv* env,
    jclass,
    jbyteArray payloadBytes) {

    jsize len = env->GetArrayLength(payloadBytes);
    std::vector<uint8_t> data(len);
    if (len > 0) {
        env->GetByteArrayRegion(payloadBytes, 0, len, reinterpret_cast<jbyte*>(data.data()));
    }

    // 4 preamble symbols (tones: 600Hz, 2500Hz, 600Hz, 2500Hz) + 2 nibbles per byte
    int totalSymbols = 4 + len * 2;
    std::vector<int16_t> pcm(totalSymbols * SYMBOL_SAMPLES);

    double preambleFreqs[4] = {600.0, 2500.0, 600.0, 2500.0};
    for (int s = 0; s < totalSymbols; ++s) {
        double freq;
        if (s < 4) {
            freq = preambleFreqs[s];
        } else {
            int nibIdx = s - 4;
            uint8_t byteVal = data[nibIdx / 2];
            int nibble = (nibIdx % 2 == 0) ? ((byteVal >> 4) & 0x0F) : (byteVal & 0x0F);
            freq = 800.0 + nibble * 100.0; // 800 Hz .. 2300 Hz
        }

        int base = s * SYMBOL_SAMPLES;
        for (int i = 0; i < SYMBOL_SAMPLES; ++i) {
            // Raised-cosine 5% edge taper to prevent walkie-talkie compander clicks
            double envGain = 1.0;
            int ramp = SYMBOL_SAMPLES / 20;
            if (i < ramp) {
                envGain = 0.5 * (1.0 - std::cos(PI * i / ramp));
            } else if (i >= SYMBOL_SAMPLES - ramp) {
                envGain = 0.5 * (1.0 - std::cos(PI * (SYMBOL_SAMPLES - 1 - i) / ramp));
            }
            double sample = std::sin(2.0 * PI * freq * i / SAMPLE_RATE) * envGain * 24000.0;
            pcm[base + i] = static_cast<int16_t>(sample);
        }
    }

    jshortArray out = env->NewShortArray(static_cast<jsize>(pcm.size()));
    env->SetShortArrayRegion(out, 0, static_cast<jsize>(pcm.size()), pcm.data());
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_pharos_core_audio_NativeDspBridge_nativeDemodulateMfsk48k(
    JNIEnv* env,
    jclass,
    jshortArray pcmSamples,
    jint expectedBytes) {

    jsize pcmLen = env->GetArrayLength(pcmSamples);
    int totalSymbols = 4 + expectedBytes * 2;
    if (pcmLen < totalSymbols * SYMBOL_SAMPLES) {
        return nullptr;
    }

    std::vector<int16_t> pcm(pcmLen);
    env->GetShortArrayRegion(pcmSamples, 0, pcmLen, pcm.data());

    std::vector<uint8_t> out(expectedBytes, 0);
    for (int b = 0; b < expectedBytes; ++b) {
        int hiSymbolIdx = 4 + b * 2;
        int loSymbolIdx = 4 + b * 2 + 1;

        int bestHi = 0;
        double maxHiPower = -1.0;
        for (int n = 0; n < 16; ++n) {
            double p = goertzel_power(pcm.data() + hiSymbolIdx * SYMBOL_SAMPLES, SYMBOL_SAMPLES, 800.0 + n * 100.0, SAMPLE_RATE);
            if (p > maxHiPower) {
                maxHiPower = p;
                bestHi = n;
            }
        }

        int bestLo = 0;
        double maxLoPower = -1.0;
        for (int n = 0; n < 16; ++n) {
            double p = goertzel_power(pcm.data() + loSymbolIdx * SYMBOL_SAMPLES, SYMBOL_SAMPLES, 800.0 + n * 100.0, SAMPLE_RATE);
            if (p > maxLoPower) {
                maxLoPower = p;
                bestLo = n;
            }
        }

        out[b] = static_cast<uint8_t>((bestHi << 4) | bestLo);
    }

    jbyteArray result = env->NewByteArray(expectedBytes);
    env->SetByteArrayRegion(result, 0, expectedBytes, reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_pharos_core_audio_NativeDspBridge_nativeDecodeDtmf48k(
    JNIEnv* env,
    jclass,
    jshortArray pcmSamples) {

    constexpr double lowFreqs[4]  = {697.0, 770.0, 852.0, 941.0};
    constexpr double highFreqs[4] = {1209.0, 1336.0, 1477.0, 1633.0};
    constexpr char dtmfGrid[4][4] = {
        {'1', '2', '3', 'A'},
        {'4', '5', '6', 'B'},
        {'7', '8', '9', 'C'},
        {'*', '0', '#', 'D'}
    };

    jsize len = env->GetArrayLength(pcmSamples);
    std::vector<int16_t> pcm(len);
    env->GetShortArrayRegion(pcmSamples, 0, len, pcm.data());

    constexpr int WINDOW = 1920; // 40 ms window at 48 kHz
    std::string decoded;
    char lastChar = '\0';

    for (int offset = 0; offset + WINDOW <= len; offset += WINDOW) {
        int bestLow = -1, bestHigh = -1;
        double maxLow = 0.0, maxHigh = 0.0;
        for (int r = 0; r < 4; ++r) {
            double p = goertzel_power(pcm.data() + offset, WINDOW, lowFreqs[r], SAMPLE_RATE);
            if (p > maxLow) { maxLow = p; bestLow = r; }
        }
        for (int c = 0; c < 4; ++c) {
            double p = goertzel_power(pcm.data() + offset, WINDOW, highFreqs[c], SAMPLE_RATE);
            if (p > maxHigh) { maxHigh = p; bestHigh = c; }
        }

        if (maxLow > 2.5e13 && maxHigh > 2.5e13 && bestLow >= 0 && bestHigh >= 0) {
            char ch = dtmfGrid[bestLow][bestHigh];
            if (ch != lastChar) {
                decoded.push_back(ch);
                lastChar = ch;
            }
        } else {
            lastChar = '\0';
        }
    }

    return env->NewStringUTF(decoded.c_str());
}
