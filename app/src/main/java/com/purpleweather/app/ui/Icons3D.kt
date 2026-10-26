package com.purpleweather.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.purpleweather.app.data.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Объёмная иконка погоды, нарисованная кодом: шары с бликами, пухлые облака с тенью,
 * падающие капли и снег, вспышки молний. parallax — сдвиг слоёв от наклона телефона.
 */
@Composable
fun WeatherIcon3D(sky: Sky, isDay: Boolean, modifier: Modifier, animated: Boolean = true, parallax: Offset = Offset.Zero) {
    val tr = rememberInfiniteTransition(label = "wx")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (animated) 2400 else 1_000_000, easing = LinearEasing)), label = "t")
    val pulse by tr.animateFloat(0.92f, 1.05f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "p")
    val bob by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val s = size.minDimension
        val p = Offset(parallax.x * s * 0.05f, parallax.y * s * 0.05f)
        val back = p * 0.5f
        val front = p * 1.3f + Offset(0f, bob * s * 0.012f)
        when (sky) {
            Sky.CLEAR -> if (isDay) sun(center + back, s * 0.30f, pulse, t) else moon(center + back, s * 0.28f, pulse)
            Sky.PARTLY -> {
                if (isDay) sun(Offset(s * 0.38f, s * 0.36f) + back, s * 0.22f, pulse, t) else moon(Offset(s * 0.38f, s * 0.36f) + back, s * 0.2f, pulse)
                cloud(Offset(s * 0.56f, s * 0.6f) + front, s * 0.62f, light = true)
            }
            Sky.CLOUDY -> {
                cloud(Offset(s * 0.38f, s * 0.42f) + back, s * 0.5f, light = false)
                cloud(Offset(s * 0.56f, s * 0.6f) + front, s * 0.66f, light = true)
            }
            Sky.FOG -> {
                cloud(Offset(s * 0.5f, s * 0.42f) + back, s * 0.6f, light = false)
                fog(s, t, front)
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                rain(s, t, front, heavy = sky == Sky.RAIN)
                cloud(Offset(s * 0.5f, s * 0.42f) + front, s * 0.7f, light = false)
            }
            Sky.SNOW -> {
                snow(s, t, front)
                cloud(Offset(s * 0.5f, s * 0.42f) + front, s * 0.7f, light = true)
            }
            Sky.STORM -> {
                rain(s, t, front, heavy = true)
                bolt(s, t, front)
                cloud(Offset(s * 0.5f, s * 0.4f) + front, s * 0.72f, light = false, dark = true)
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, pulse: Float, t: Float) {
    // мягкое свечение
    drawCircle(Brush.radialGradient(listOf(P.sun2.copy(alpha = 0.55f), P.pink.copy(alpha = 0.18f), Color.Transparent), c, r * 2.1f * pulse), r * 2.1f * pulse, c)
    // лучи
    rotate(t * 360f / 6f, c) {
        for (k in 0 until 12) {
            val a = 2 * PI * k / 12
            val p1 = Offset(c.x + (r * 1.18f) * cos(a).toFloat(), c.y + (r * 1.18f) * sin(a).toFloat())
            val p2 = Offset(c.x + (r * 1.45f) * cos(a).toFloat(), c.y + (r * 1.45f) * sin(a).toFloat())
            drawLine(P.sun2.copy(alpha = 0.7f), p1, p2, r * 0.09f, StrokeCap.Round)
        }
    }
    // шар с объёмом: свет сверху-слева
    drawCircle(Brush.radialGradient(listOf(P.sun1, P.sun2, P.sun3), c + Offset(-r * 0.35f, -r * 0.35f), r * 1.5f), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), c + Offset(-r * 0.4f, -r * 0.45f), r * 0.45f), r * 0.45f, c + Offset(-r * 0.4f, -r * 0.45f))
}

private fun DrawScope.moon(c: Offset, r: Float, pulse: Float) {
    drawCircle(Brush.radialGradient(listOf(P.lilac.copy(alpha = 0.45f), Color.Transparent), c, r * 2.2f * pulse), r * 2.2f * pulse, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE2D6FF), Color(0xFF9C86E8)), c + Offset(-r * 0.3f, -r * 0.35f), r * 1.5f), r, c)
    val crater = Color(0xFF8C77D9).copy(alpha = 0.45f)
    drawCircle(crater, r * 0.18f, c + Offset(r * 0.25f, -r * 0.1f))
    drawCircle(crater, r * 0.12f, c + Offset(-r * 0.2f, r * 0.35f))
    drawCircle(crater, r * 0.09f, c + Offset(r * 0.05f, r * 0.5f))
}

/** Пухлое облако: объединение кругов, градиент сверху вниз, тень снизу и блик. */
private fun DrawScope.cloud(c: Offset, w: Float, light: Boolean, dark: Boolean = false) {
    val h = w * 0.55f
    val path = Path()
    fun blob(dx: Float, dy: Float, r: Float) {
        val p = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + dx * w, c.y + dy * w), r * w)) }
        path.op(path, p, PathOperation.Union)
    }
    blob(-0.26f, 0.06f, 0.2f)
    blob(-0.04f, -0.08f, 0.27f)
    blob(0.22f, 0.02f, 0.22f)
    val base = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - w * 0.44f, c.y, c.x + w * 0.44f, c.y + w * 0.24f, w * 0.12f, w * 0.12f))
    }
    path.op(path, base, PathOperation.Union)
    // тень под облаком
    drawOval(Color(0x55180640), topLeft = Offset(c.x - w * 0.42f, c.y + h * 0.42f), size = Size(w * 0.84f, h * 0.18f))
    val top = when {
        dark -> Color(0xFFB4A2E6)
        light -> Color(0xFFFFFFFF)
        else -> Color(0xFFE6DCFF)
    }
    val bottom = when {
        dark -> Color(0xFF4B3496)
        light -> Color(0xFFB9A5F5)
        else -> Color(0xFF8E78E0)
    }
    drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = c.y - w * 0.35f, endY = c.y + w * 0.25f))
    // блик
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), c + Offset(-w * 0.1f, -w * 0.2f), w * 0.2f), w * 0.2f, c + Offset(-w * 0.1f, -w * 0.2f))
}

private fun DrawScope.rain(s: Float, t: Float, off: Offset, heavy: Boolean) {
    val n = if (heavy) 9 else 5
    for (k in 0 until n) {
        val x = s * (0.24f + 0.52f * k / (n - 1).coerceAtLeast(1)) + off.x
        val ph = (t + k * 0.37f) % 1f
        val y = s * (0.55f + 0.38f * ph) + off.y
        val a = (1f - ph).coerceIn(0f, 1f)
        val len = s * if (heavy) 0.09f else 0.06f
        val drop = Path().apply {
            moveTo(x, y)
            cubicTo(x + len * 0.35f, y + len * 0.5f, x + len * 0.28f, y + len, x, y + len)
            cubicTo(x - len * 0.28f, y + len, x - len * 0.35f, y + len * 0.5f, x, y)
            close()
        }
        drawPath(drop, Brush.verticalGradient(listOf(Color(0xFFB7E3FF).copy(alpha = a), Color(0xFF7A6BFF).copy(alpha = a)), startY = y, endY = y + len))
    }
}

private fun DrawScope.snow(s: Float, t: Float, off: Offset) {
    for (k in 0 until 8) {
        val ph = (t * 0.6f + k * 0.29f) % 1f
        val x = s * (0.22f + 0.56f * ((k * 37) % 8) / 7f) + off.x + sin((ph * 6 + k).toDouble()).toFloat() * s * 0.02f
        val y = s * (0.55f + 0.4f * ph) + off.y
        val a = (1f - ph).coerceIn(0f, 1f)
        val r = s * (0.018f + 0.008f * (k % 3))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), P.lilac.copy(alpha = a * 0.6f)), Offset(x - r * 0.3f, y - r * 0.3f), r * 1.4f), r, Offset(x, y))
    }
}

private fun DrawScope.fog(s: Float, t: Float, off: Offset) {
    for (k in 0 until 3) {
        val y = s * (0.62f + 0.1f * k) + off.y
        val dx = sin((t * 2 * PI + k).toDouble()).toFloat() * s * 0.05f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCCE6DCFF), Color.Transparent)),
            topLeft = Offset(s * (0.12f + 0.05f * k) + dx + off.x, y),
            size = Size(s * 0.72f, s * 0.05f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f)
        )
    }
}

private fun DrawScope.bolt(s: Float, t: Float, off: Offset) {
    val flash = if (t % 0.5f < 0.08f) 1f else 0.75f
    val o = Offset(s * 0.48f, s * 0.55f) + off
    val p = Path().apply {
        moveTo(o.x + s * 0.04f, o.y)
        lineTo(o.x - s * 0.08f, o.y + s * 0.18f)
        lineTo(o.x + s * 0.0f, o.y + s * 0.18f)
        lineTo(o.x - s * 0.05f, o.y + s * 0.36f)
        lineTo(o.x + s * 0.12f, o.y + s * 0.12f)
        lineTo(o.x + s * 0.03f, o.y + s * 0.12f)
        lineTo(o.x + s * 0.1f, o.y)
        close()
    }
    drawCircle(Brush.radialGradient(listOf(P.sun1.copy(alpha = 0.5f * flash), Color.Transparent), o + Offset(0f, s * 0.18f), s * 0.25f), s * 0.25f, o + Offset(0f, s * 0.18f))
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF6C2), Color(0xFFFFB347)), startY = o.y, endY = o.y + s * 0.36f), alpha = flash)
}
