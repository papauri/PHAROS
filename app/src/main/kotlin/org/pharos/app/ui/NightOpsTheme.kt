package org.pharos.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pharos.core.protocol.TacticalCard
import java.security.MessageDigest

/**
 * Minimalist #000000 OLED Palette with 1-Tap Red Night Mode.
 * Designed for maximum legibility and zero visual clutter.
 */
data class NightOpsPalette(
    val bgPureBlack: Color = Color(0xFF000000),
    val surfaceDark: Color = Color(0xFF0C0C0C),
    val primarySignal: Color,
    val secondaryAmber: Color,
    val verifiedGreen: Color,
    val quarantineAmber: Color,
    val textPrimary: Color,
    val mutedText: Color,
    val borderStroke: Color
)

fun getNightOpsPalette(isRed660nmMode: Boolean): NightOpsPalette {
    return if (isRed660nmMode) {
        NightOpsPalette(
            primarySignal = Color(0xFFFF3B30),
            secondaryAmber = Color(0xFFD92626),
            verifiedGreen = Color(0xFFFF5252),
            quarantineAmber = Color(0xFFC62828),
            textPrimary = Color(0xFFFF453A),
            mutedText = Color(0xFF993333),
            borderStroke = Color(0xFF3A1212)
        )
    } else {
        NightOpsPalette(
            primarySignal = Color(0xFFFFFFFF),
            secondaryAmber = Color(0xFFFFB300),
            verifiedGreen = Color(0xFF00E676),
            quarantineAmber = Color(0xFFFFB300),
            textPrimary = Color(0xFFF5F5F5),
            mutedText = Color(0xFF8E8E93),
            borderStroke = Color(0xFF2C2C2E)
        )
    }
}

@Composable
fun PharosNightOpsTheme(
    isRed660nmMode: Boolean,
    content: @Composable () -> Unit
) {
    val palette = getNightOpsPalette(isRed660nmMode)
    val scheme = darkColorScheme(
        primary = palette.primarySignal,
        secondary = palette.secondaryAmber,
        tertiary = palette.verifiedGreen,
        background = palette.bgPureBlack,
        surface = palette.surfaceDark,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

@Composable
fun TacticalQrCodeCanvas(
    payload: String,
    palette: NightOpsPalette,
    sizeDp: Dp = 148.dp
) {
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(Color.Black)
            .border(1.dp, palette.borderStroke)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 21
            val cellW = size.width / grid
            val cellH = size.height / grid

            fun isFinderPattern(r: Int, c: Int): Boolean {
                val inTopLeft = r in 0..6 && c in 0..6
                val inTopRight = r in 0..6 && c in (grid - 7) until grid
                val inBottomLeft = r in (grid - 7) until grid && c in 0..6
                return inTopLeft || inTopRight || inBottomLeft
            }

            fun drawFinder(startR: Int, startC: Int) {
                for (r in 0..6) {
                    for (c in 0..6) {
                        val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                        val isCenter = r in 2..4 && c in 2..4
                        if (isBorder || isCenter) {
                            drawRect(
                                color = palette.textPrimary,
                                topLeft = Offset((startC + c) * cellW, (startR + r) * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }

            drawFinder(0, 0)
            drawFinder(0, grid - 7)
            drawFinder(grid - 7, 0)

            var bitIdx = 0
            for (r in 0 until grid) {
                for (c in 0 until grid) {
                    if (isFinderPattern(r, c)) continue
                    val byteVal = digest[(bitIdx / 8) % digest.size].toInt() and 0xFF
                    val bit = (byteVal ushr (bitIdx % 8)) and 1
                    bitIdx++
                    if (bit == 1) {
                        drawRect(
                            color = palette.textPrimary,
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW * 0.9f, cellH * 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineVectorTacticalMap(
    verifiedCards: List<TacticalCard>,
    quarantineCards: List<TacticalCard>,
    palette: NightOpsPalette,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF060606))
            .border(1.dp, palette.borderStroke)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Simple range rings
        listOf(0.22f, 0.42f).forEach { ratio ->
            drawCircle(
                color = palette.borderStroke,
                radius = minOf(w, h) * ratio,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }

        val refLat = 46.5197
        val refLon = 6.6323
        verifiedCards.forEach { card ->
            val dx = ((card.longitudeDeg - refLon) * 8500f).toFloat().coerceIn(-w * 0.42f, w * 0.42f)
            val dy = (-(card.latitudeDeg - refLat) * 8500f).toFloat().coerceIn(-h * 0.42f, h * 0.42f)
            drawCircle(
                color = palette.verifiedGreen,
                radius = 8f,
                center = Offset(cx + dx, cy + dy)
            )
        }

        quarantineCards.forEach { card ->
            val dx = ((card.longitudeDeg - refLon) * 8500f).toFloat().coerceIn(-w * 0.42f, w * 0.42f)
            val dy = (-(card.latitudeDeg - refLat) * 8500f).toFloat().coerceIn(-h * 0.42f, h * 0.42f)
            drawCircle(
                color = palette.quarantineAmber,
                radius = 10f,
                center = Offset(cx + dx, cy + dy),
                style = Stroke(width = 2.5f)
            )
        }
    }
}
