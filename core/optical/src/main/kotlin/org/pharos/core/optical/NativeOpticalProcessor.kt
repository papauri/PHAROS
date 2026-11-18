package org.pharos.core.optical

import kotlin.math.log10
import kotlin.math.max

/**
 * JNI Bridge to C++20 NDK libpharos_optical.so (16 KB ELF page-aligned) with bit-exact
 * JVM fallback for host unit testing.
 */
object NativeOpticalProcessor {
    val isNativeLoaded: Boolean = try {
        System.loadLibrary("pharos_optical")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic
    external fun nativeAnalyzeRoi240(
        luma240x240: ByteArray,
        prevCx: Float,
        prevCy: Float
    ): FloatArray?

    data class RoiFrameMetrics(
        val lockedCentroidX: Float,
        val lockedCentroidY: Float,
        val meanLuma: Float,
        val maxLuma: Float,
        val adaptiveThreshold: Float,
        val snrDb: Float,
        val grid4x4Luma: FloatArray // 16 cells
    )

    fun analyzeRoi240(
        luma240x240: ByteArray,
        prevCx: Float = 120f,
        prevCy: Float = 120f
    ): RoiFrameMetrics {
        require(luma240x240.size >= 240 * 240) { "ROI buffer must be 240x240 (57600 bytes)" }
        if (isNativeLoaded) {
            try {
                val res = nativeAnalyzeRoi240(luma240x240, prevCx, prevCy)
                if (res != null && res.size == 22) {
                    return RoiFrameMetrics(
                        lockedCentroidX = res[0],
                        lockedCentroidY = res[1],
                        meanLuma = res[2],
                        maxLuma = res[3],
                        adaptiveThreshold = res[4],
                        snrDb = res[5],
                        grid4x4Luma = res.copyOfRange(6, 22)
                    )
                }
            } catch (_: Throwable) {
            }
        }

        val roiSize = 240
        val total = roiSize * roiSize
        var sum = 0L
        var maxV = 0
        var minV = 255
        for (i in 0 until total) {
            val v = luma240x240[i].toInt() and 0xFF
            sum += v
            if (v > maxV) maxV = v
            if (v < minV) minV = v
        }
        val mean = sum.toFloat() / total.toFloat()
        val threshold = max(40f, mean + (maxV.toFloat() - mean) * 0.55f)

        var minActX = roiSize
        var maxActX = 0
        var minActY = roiSize
        var maxActY = 0
        var activeCount = 0
        for (y in 0 until roiSize) {
            val row = y * roiSize
            for (x in 0 until roiSize) {
                val v = (luma240x240[row + x].toInt() and 0xFF).toFloat()
                if (v >= threshold) {
                    if (x < minActX) minActX = x
                    if (x > maxActX) maxActX = x
                    if (y < minActY) minActY = y
                    if (y > maxActY) maxActY = y
                    activeCount++
                }
            }
        }
        val cx = if (activeCount > 10) (minActX + maxActX + 1) * 0.5f else prevCx
        val cy = if (activeCount > 10) (minActY + maxActY + 1) * 0.5f else prevCy
        val lockedCx = kotlin.math.round(0.7f * cx + 0.3f * prevCx)
        val lockedCy = kotlin.math.round(0.7f * cy + 0.3f * prevCy)

        val startX = (lockedCx.toInt() - 48).coerceIn(0, roiSize - 96)
        val startY = (lockedCy.toInt() - 48).coerceIn(0, roiSize - 96)
        val grid = FloatArray(16)
        for (gy in 0 until 4) {
            for (gx in 0 until 4) {
                var cellSum = 0
                val cx0 = startX + gx * 24
                val cy0 = startY + gy * 24
                for (py in (cy0 + 4) until (cy0 + 20)) {
                    for (px in (cx0 + 4) until (cx0 + 20)) {
                        cellSum += luma240x240[py * roiSize + px].toInt() and 0xFF
                    }
                }
                grid[gy * 4 + gx] = cellSum / 256f
            }
        }

        val signalDelta = max(1f, maxV.toFloat() - mean)
        val noiseFloor = max(2f, (mean - minV.toFloat()) * 0.25f)
        val snrDb = 20f * log10(signalDelta / noiseFloor)

        return RoiFrameMetrics(
            lockedCentroidX = lockedCx,
            lockedCentroidY = lockedCy,
            meanLuma = mean,
            maxLuma = maxV.toFloat(),
            adaptiveThreshold = threshold,
            snrDb = snrDb,
            grid4x4Luma = grid
        )
    }
}
