package com.weatherlet.app.icons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Subtle ambient motion behind the hero card: rain streaks, drifting snow, twinkling stars, a
 * breathing sun glow, drifting cloud banks. A single looping phase drives everything, and the
 * particle count is small so it stays cheap.
 */
@Composable
fun SkyAnimation(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val kind = WeatherIcons.kind(code, isDay)
    val transition = rememberInfiniteTransition(label = "sky")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    // Fixed random layout per kind so particles don't jump between recompositions.
    val seeds = remember(kind) {
        val r = Random(kind.ordinal + 7)
        List(48) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat(), r.nextFloat()) }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            IconKind.RAIN, IconKind.DRIZZLE, IconKind.STORM -> {
                val n = if (kind == IconKind.DRIZZLE) 18 else 34
                val len = if (kind == IconKind.DRIZZLE) 10f else 26f
                for (i in 0 until n) {
                    val s = seeds[i]
                    val y = ((phase * (1.6f + s[2]) * 4f + s[1]) % 1f) * (h + len) - len
                    val x = s[0] * w + y * 0.18f
                    drawLine(
                        Color.White.copy(alpha = 0.28f + 0.2f * s[3]),
                        Offset(x, y), Offset(x - len * 0.25f, y + len),
                        strokeWidth = 2.2f, cap = StrokeCap.Round,
                    )
                }
                if (kind == IconKind.STORM) {
                    val f = (phase * 6f) % 1f
                    if (f < 0.04f) drawRect(Color.White.copy(alpha = 0.22f * (1f - f / 0.04f)))
                }
            }
            IconKind.SNOW -> for (i in 0 until 36) {
                val s = seeds[i]
                val y = ((phase * (0.8f + s[2] * 0.8f) * 2f + s[1]) % 1f) * h
                val x = s[0] * w + sin((phase * 2 * PI + s[3] * 6).toFloat()) * 14f
                drawCircle(Color.White.copy(alpha = 0.55f + 0.3f * s[3]), 2f + 3f * s[2] * s[3], Offset(x, y))
            }
            IconKind.CLEAR_NIGHT, IconKind.PARTLY_NIGHT -> for (i in 0 until 30) {
                val s = seeds[i]
                val tw = 0.25f + 0.75f * abs(sin((phase * 2 * PI * (1 + (i % 3)) + s[3] * 9).toFloat()))
                drawCircle(Color.White.copy(alpha = 0.7f * tw), 1.2f + 1.6f * s[2], Offset(s[0] * w, s[1] * h * 0.8f))
            }
            IconKind.CLEAR_DAY, IconKind.PARTLY_DAY -> {
                val breathe = 1f + 0.06f * sin((phase * 2 * PI).toFloat())
                val r = h * 0.95f * breathe
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x55FFE9A8), Color.Transparent), center = Offset(w * 0.88f, h * 0.05f), radius = r,
                    ),
                    radius = r, center = Offset(w * 0.88f, h * 0.05f),
                )
                if (kind == IconKind.PARTLY_DAY) clouds(phase, w, h, 0.06f)
            }
            IconKind.CLOUDY -> clouds(phase, w, h, 0.08f)
            IconKind.FOG -> for (i in 0 until 5) {
                val y = h * (0.15f + i * 0.17f)
                val x = (((phase + i * 0.2f) % 1f) * 1.4f - 0.2f) * w
                drawRoundRect(
                    Color.White.copy(alpha = 0.10f), Offset(x - w * 0.5f, y), androidx.compose.ui.geometry.Size(w * 1.1f, h * 0.07f),
                    androidx.compose.ui.geometry.CornerRadius(40f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.clouds(phase: Float, w: Float, h: Float, alpha: Float) {
    for (i in 0 until 3) {
        val x = (((phase * (0.5f + i * 0.2f) + i * 0.33f) % 1f) * 1.6f - 0.3f) * w
        val y = h * (0.12f + i * 0.28f)
        drawOval(
            Color.White.copy(alpha = alpha),
            Offset(x - w * 0.3f, y),
            androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.22f),
        )
    }
}
