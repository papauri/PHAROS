#include <jni.h>
#include <cstdint>
#include <cmath>
#include <algorithm>
#include <vector>

/**
 * C++20 NDK Stabilized 240x240 Grayscale & 4x4 Matrix ROI Processor for PHAROS BEAM.
 * Analyzes only the 240x240 center reticle crop at 30-60 fps with adaptive thresholding
 * and hand-shake centroid lock.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_pharos_core_optical_NativeOpticalProcessor_nativeAnalyzeRoi240(
    JNIEnv* env,
    jclass,
    jbyteArray luma240x240,
    jfloat prevCx,
    jfloat prevCy) {

    constexpr int ROI_SIZE = 240;
    constexpr int TOTAL_PIXELS = ROI_SIZE * ROI_SIZE;

    jsize len = env->GetArrayLength(luma240x240);
    if (len < TOTAL_PIXELS) {
        return nullptr;
    }

    std::vector<uint8_t> pixels(TOTAL_PIXELS);
    env->GetByteArrayRegion(luma240x240, 0, TOTAL_PIXELS, reinterpret_cast<jbyte*>(pixels.data()));

    // 1. Compute histogram, mean background, and peak brightness
    uint64_t sumLuma = 0;
    uint8_t maxLuma = 0;
    uint8_t minLuma = 255;
    for (int i = 0; i < TOTAL_PIXELS; ++i) {
        uint8_t v = pixels[i];
        sumLuma += v;
        if (v > maxLuma) maxLuma = v;
        if (v < minLuma) minLuma = v;
    }
    float meanLuma = static_cast<float>(sumLuma) / static_cast<float>(TOTAL_PIXELS);
    float adaptiveThreshold = meanLuma + (static_cast<float>(maxLuma) - meanLuma) * 0.55f;
    if (adaptiveThreshold < 40.0f) adaptiveThreshold = 40.0f;

    // 2. Compute bounding-box center above adaptive threshold for perimeter-locked hand-shake tracking
    int minActX = ROI_SIZE, maxActX = 0;
    int minActY = ROI_SIZE, maxActY = 0;
    int activeCount = 0;

    for (int y = 0; y < ROI_SIZE; ++y) {
        int rowOffset = y * ROI_SIZE;
        for (int x = 0; x < ROI_SIZE; ++x) {
            float v = static_cast<float>(pixels[rowOffset + x]);
            if (v >= adaptiveThreshold) {
                if (x < minActX) minActX = x;
                if (x > maxActX) maxActX = x;
                if (y < minActY) minActY = y;
                if (y > maxActY) maxActY = y;
                activeCount++;
            }
        }
    }

    float cx = (activeCount > 10) ? static_cast<float>(minActX + maxActX + 1) * 0.5f : prevCx;
    float cy = (activeCount > 10) ? static_cast<float>(minActY + maxActY + 1) * 0.5f : prevCy;

    // Exponential smoothing with previous centroid to dampen high-frequency hand tremor
    float lockedCx = std::round(0.7f * cx + 0.3f * prevCx);
    float lockedCy = std::round(0.7f * cy + 0.3f * prevCy);

    // 3. Sample 4x4 grid around locked centroid (box radius = 48px -> 96x96 area -> 24x24 per cell)
    float gridValues[16];
    int startX = std::clamp(static_cast<int>(lockedCx) - 48, 0, ROI_SIZE - 96);
    int startY = std::clamp(static_cast<int>(lockedCy) - 48, 0, ROI_SIZE - 96);

    for (int gy = 0; gy < 4; ++gy) {
        for (int gx = 0; gx < 4; ++gx) {
            uint32_t cellSum = 0;
            int cellX0 = startX + gx * 24;
            int cellY0 = startY + gy * 24;
            for (int py = cellY0 + 4; py < cellY0 + 20; ++py) {
                for (int px = cellX0 + 4; px < cellX0 + 20; ++px) {
                    cellSum += pixels[py * ROI_SIZE + px];
                }
            }
            gridValues[gy * 4 + gx] = static_cast<float>(cellSum) / 256.0f;
        }
    }

    // 4. Calculate optical SNR in dB
    float signalDelta = std::max(1.0f, static_cast<float>(maxLuma) - meanLuma);
    float noiseFloor = std::max(2.0f, (meanLuma - static_cast<float>(minLuma)) * 0.25f);
    float snrDb = 20.0f * std::log10(signalDelta / noiseFloor);

    // Return [lockedCx, lockedCy, meanLuma, maxLuma, adaptiveThreshold, snrDb, cell0..cell15] (22 floats)
    float out[22];
    out[0] = lockedCx;
    out[1] = lockedCy;
    out[2] = meanLuma;
    out[3] = static_cast<float>(maxLuma);
    out[4] = adaptiveThreshold;
    out[5] = snrDb;
    for (int i = 0; i < 16; ++i) {
        out[6 + i] = gridValues[i];
    }

    jfloatArray res = env->NewFloatArray(22);
    env->SetFloatArrayRegion(res, 0, 22, out);
    return res;
}
