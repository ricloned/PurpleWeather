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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import com.purpleweather.app.data.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Объёмная иконка погоды, нарисованная кодом:
 * шары со светотенью и контурным светом, облака с толщиной и подсветкой краёв,
 * капли с бликами, снег, молнии, туман и тень «на полу».
 * parallax — сдвиг слоёв от наклона телефона.
 */
@Composable
fun WeatherIcon3D(
    sky: Sky,
    isDay: Boolean,
    modifier: Modifier,
    animated: Boolean = true,
    parallax: Offset = Offset.Zero,
    floor: Boolean = false,
    cat: Boolean = false
) {
    val tr = rememberInfiniteTransition(label = "wx")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (animated) 2600 else 1_000_000, easing = LinearEasing)), label = "t")
    val slow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (animated) 9000 else 1_000_000, easing = LinearEasing)), label = "s")
    val pulse by tr.animateFloat(0.94f, 1.05f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "p")
    val bob by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val s = size.minDimension
        val p = Offset(parallax.x * s * 0.06f, parallax.y * s * 0.06f)
        val back = p * 0.45f
        val drift = sin(slow * 2 * PI).toFloat() * s * 0.025f
        val front = p * 1.35f + Offset(drift, bob * s * 0.015f)
        if (floor) {
            // тень на «полу» — дышит вместе с иконкой
            val w = s * (0.62f - 0.04f * bob)
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), Offset(center.x + p.x * 0.3f, s * 0.95f), w / 2),
                topLeft = Offset(center.x - w / 2 + p.x * 0.3f, s * 0.9f), size = Size(w, s * 0.1f)
            )
        }
        when (sky) {
            Sky.CLEAR -> if (isDay) sun(center + back, s * 0.30f, pulse, t, p) else moon(center + back, s * 0.28f, pulse, t)
            Sky.PARTLY -> {
                if (isDay) sun(Offset(s * 0.37f, s * 0.35f) + back, s * 0.22f, pulse, t, p) else moon(Offset(s * 0.37f, s * 0.35f) + back, s * 0.2f, pulse, t)
                cloud(Offset(s * 0.56f, s * 0.6f) + front, s * 0.62f, CloudTone.LIGHT)
            }
            Sky.CLOUDY -> {
                cloud(Offset(s * 0.36f, s * 0.42f) + back + Offset(-drift * 0.6f, 0f), s * 0.5f, CloudTone.MID)
                cloud(Offset(s * 0.57f, s * 0.6f) + front, s * 0.66f, CloudTone.LIGHT)
            }
            Sky.FOG -> {
                cloud(Offset(s * 0.5f, s * 0.42f) + back, s * 0.6f, CloudTone.MID)
                fog(s, t, front)
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                rain(s, t, front, heavy = sky == Sky.RAIN)
                cloud(Offset(s * 0.5f, s * 0.42f) + front, s * 0.72f, CloudTone.MID)
            }
            Sky.SNOW -> {
                snow(s, t, front)
                cloud(Offset(s * 0.5f, s * 0.42f) + front, s * 0.72f, CloudTone.LIGHT)
            }
            Sky.STORM -> {
                rain(s, t, front, heavy = true)
                bolt(s, t, front)
                cloud(Offset(s * 0.5f, s * 0.4f) + front, s * 0.74f, CloudTone.DARK)
            }
        }
        if (cat) {
            val paint = android.graphics.Paint().apply { textSize = s * 0.22f; textAlign = android.graphics.Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText("🐱", s * 0.6f + front.x, s * 0.36f + front.y, paint)
        }
    }
}

private enum class CloudTone { LIGHT, MID, DARK }

private fun DrawScope.sun(c: Offset, r: Float, pulse: Float, t: Float, p: Offset) {
    // корона: несколько слоёв свечения
    drawCircle(Brush.radialGradient(listOf(P.sun2.copy(alpha = 0.5f), P.sun3.copy(alpha = 0.18f), Color.Transparent), c, r * 2.3f * pulse), r * 2.3f * pulse, c)
    drawCircle(Brush.radialGradient(listOf(P.sun1.copy(alpha = 0.55f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
    // мягкие лучи-лепестки
    rotate(t * 30f, c) {
        for (k in 0 until 10) {
            val a = 2 * PI * k / 10
            val len = if (k % 2 == 0) 1.62f else 1.42f
            val p1 = Offset(c.x + r * 1.15f * cos(a).toFloat(), c.y + r * 1.15f * sin(a).toFloat())
            val p2 = Offset(c.x + r * len * cos(a).toFloat(), c.y + r * len * sin(a).toFloat())
            drawLine(Brush.linearGradient(listOf(P.sun1, P.sun2.copy(alpha = 0f)), p1, p2), p1, p2, r * 0.11f, StrokeCap.Round)
        }
    }
    // шар: свет сверху-слева, тень справа-снизу
    drawCircle(Brush.radialGradient(listOf(P.sun1, P.sun2, Color(0xFFFF8A3D), P.sun3), c + Offset(-r * 0.4f, -r * 0.4f), r * 1.75f), r, c)
    // контурный свет (rim) снизу
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x55FFE2A8)), c, r), r, c, style = Stroke(r * 0.06f))
    // блик
    val hl = c + Offset(-r * 0.38f, -r * 0.42f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)), hl, r * 0.42f), r * 0.42f, hl)
    // блики объектива — смещаются при наклоне
    val dir = Offset(-p.x, -p.y) * 3f
    for ((k, a) in listOf(0.9f to 0.18f, 1.6f to 0.12f, 2.3f to 0.08f)) {
        val q = c + Offset(r * k * 0.6f, r * k * 0.5f) + dir * k
        drawCircle(P.sun1.copy(alpha = a), r * 0.12f / k, q)
    }
}

private fun DrawScope.moon(c: Offset, r: Float, pulse: Float, t: Float) {
    drawCircle(Brush.radialGradient(listOf(P.lilac.copy(alpha = 0.4f), Color.Transparent), c, r * 2.3f * pulse), r * 2.3f * pulse, c)
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6DCFF), Color(0xFFA996EE), Color(0xFF6E5BC8)), c + Offset(-r * 0.35f, -r * 0.4f), r * 1.8f), r, c)
    val crater = Color(0xFF7D69D0)
    for ((dx, dy, rr) in listOf(Triple(0.28f, -0.12f, 0.19f), Triple(-0.22f, 0.36f, 0.13f), Triple(0.08f, 0.52f, 0.09f), Triple(-0.36f, -0.2f, 0.08f))) {
        val cc = c + Offset(r * dx, r * dy)
        // кратер: тёмная чаша + светлый край снизу
        drawCircle(Brush.radialGradient(listOf(crater.copy(alpha = 0.55f), crater.copy(alpha = 0.15f)), cc + Offset(-r * rr * 0.2f, -r * rr * 0.2f), r * rr), r * rr, cc)
        drawCircle(Color.White.copy(alpha = 0.25f), r * rr, cc + Offset(r * rr * 0.12f, r * rr * 0.14f), style = Stroke(r * 0.02f))
    }
    // звёздочки рядом
    for (k in 0 until 3) {
        val a = ((t + k * 0.33f) % 1f)
        val tw = 0.3f + 0.7f * (1f - kotlin.math.abs(a - 0.5f) * 2)
        drawCircle(Color.White.copy(alpha = tw), r * 0.05f, c + Offset(r * (1.3f - k * 0.5f), -r * (1.1f - k * 0.7f)))
    }
}

/** Пухлое облако с толщиной: тёмный «низ», основное тело, подсветка краёв и блик. */
private fun DrawScope.cloud(c: Offset, w: Float, tone: CloudTone) {
    fun shape(o: Offset): Path {
        val path = Path()
        fun blob(dx: Float, dy: Float, r: Float) {
            val p = Path().apply { addOval(Rect(Offset(o.x + dx * w, o.y + dy * w), r * w)) }
            path.op(path, p, PathOperation.Union)
        }
        blob(-0.27f, 0.06f, 0.2f)
        blob(-0.05f, -0.09f, 0.28f)
        blob(0.21f, 0.0f, 0.23f)
        blob(0.06f, 0.08f, 0.2f)
        val base = Path().apply { addRoundRect(RoundRect(o.x - w * 0.45f, o.y, o.x + w * 0.45f, o.y + w * 0.25f, w * 0.125f, w * 0.125f)) }
        path.op(path, base, PathOperation.Union)
        return path
    }
    val body = shape(c)
    val (top, mid, bottom, under) = when (tone) {
        CloudTone.LIGHT -> listOf(Color(0xFFFFFFFF), Color(0xFFEDE6FF), Color(0xFFB7A3F2), Color(0xFF7C66D6))
        CloudTone.MID -> listOf(Color(0xFFF1EBFF), Color(0xFFCDBEFA), Color(0xFF8E78E0), Color(0xFF5B45B5))
        CloudTone.DARK -> listOf(Color(0xFFC4B6EE), Color(0xFF8B79CC), Color(0xFF4B3496), Color(0xFF2C1C66))
    }
    // тень на фоне под облаком
    drawOval(Brush.radialGradient(listOf(Color(0x66120530), Color.Transparent), Offset(c.x, c.y + w * 0.36f), w * 0.5f),
        topLeft = Offset(c.x - w * 0.5f, c.y + w * 0.26f), size = Size(w, w * 0.2f))
    // толщина: тот же силуэт ниже и темнее
    translate(top = w * 0.045f) { drawPath(body, under) }
    // тело
    drawPath(body, Brush.verticalGradient(listOf(top, mid, bottom), startY = c.y - w * 0.38f, endY = c.y + w * 0.26f))
    clipPath(body) {
        // мягкая тень внутри снизу (ambient occlusion)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, under.copy(alpha = 0.45f)), startY = c.y + w * 0.05f, endY = c.y + w * 0.26f))
        // свет сверху-слева
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), c + Offset(-w * 0.12f, -w * 0.24f), w * 0.3f), w * 0.3f, c + Offset(-w * 0.12f, -w * 0.24f))
        // цветной отсвет темы снизу
        drawCircle(Brush.radialGradient(listOf(P.pink.copy(alpha = 0.25f), Color.Transparent), c + Offset(w * 0.3f, w * 0.25f), w * 0.35f), w * 0.35f, c + Offset(w * 0.3f, w * 0.25f))
    }
    // подсветка контура
    drawPath(body, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), startY = c.y - w * 0.38f, endY = c.y), style = Stroke(w * 0.012f))
}

private fun DrawScope.rain(s: Float, t: Float, off: Offset, heavy: Boolean) {
    val n = if (heavy) 9 else 5
    for (k in 0 until n) {
        val x = s * (0.24f + 0.52f * k / (n - 1).coerceAtLeast(1)) + off.x
        val ph = (t + k * 0.37f) % 1f
        val y = s * (0.55f + 0.38f * ph) + off.y
        val a = (1f - ph).coerceIn(0f, 1f)
        val len = s * if (heavy) 0.095f else 0.065f
        val drop = Path().apply {
            moveTo(x, y)
            cubicTo(x + len * 0.38f, y + len * 0.52f, x + len * 0.3f, y + len, x, y + len)
            cubicTo(x - len * 0.3f, y + len, x - len * 0.38f, y + len * 0.52f, x, y)
            close()
        }
        drawPath(drop, Brush.linearGradient(listOf(Color(0xFFD8F1FF).copy(alpha = a), Color(0xFF6E8BFF).copy(alpha = a), Color(0xFF5B3BE0).copy(alpha = a)),
            Offset(x - len * 0.3f, y), Offset(x + len * 0.3f, y + len)))
        drawCircle(Color.White.copy(alpha = 0.8f * a), len * 0.08f, Offset(x - len * 0.1f, y + len * 0.6f))
        // всплеск у «пола»
        if (ph > 0.85f) drawOval(Color(0xFFB8D8FF).copy(alpha = (ph - 0.85f) * 4f), Offset(x - len * 0.4f, s * 0.95f + off.y), Size(len * 0.8f, len * 0.15f))
    }
}

private fun DrawScope.snow(s: Float, t: Float, off: Offset) {
    for (k in 0 until 9) {
        val ph = (t * 0.6f + k * 0.29f) % 1f
        val x = s * (0.2f + 0.6f * ((k * 37) % 9) / 8f) + off.x + sin((ph * 6 + k).toDouble()).toFloat() * s * 0.025f
        val y = s * (0.55f + 0.4f * ph) + off.y
        val a = (1f - ph).coerceIn(0f, 1f)
        val r = s * (0.02f + 0.008f * (k % 3))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), P.lilac.copy(alpha = a * 0.5f)), Offset(x - r * 0.35f, y - r * 0.35f), r * 1.5f), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = a * 0.9f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}

private fun DrawScope.fog(s: Float, t: Float, off: Offset) {
    for (k in 0 until 3) {
        val y = s * (0.62f + 0.1f * k) + off.y
        val dx = sin((t * 2 * PI + k).toDouble()).toFloat() * s * 0.06f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xD9EFE8FF), Color(0x99C9B6FF), Color.Transparent)),
            topLeft = Offset(s * (0.1f + 0.05f * k) + dx + off.x, y), size = Size(s * 0.76f, s * 0.055f),
            cornerRadius = CornerRadius(s * 0.03f)
        )
    }
}

private fun DrawScope.bolt(s: Float, t: Float, off: Offset) {
    val flash = if (t % 0.5f < 0.07f) 1f else 0.72f
    val o = Offset(s * 0.48f, s * 0.56f) + off
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
    drawCircle(Brush.radialGradient(listOf(P.sun1.copy(alpha = 0.55f * flash), Color.Transparent), o + Offset(0f, s * 0.18f), s * 0.28f), s * 0.28f, o + Offset(0f, s * 0.18f))
    translate(left = s * 0.012f, top = s * 0.012f) { drawPath(p, Color(0xFFB36B00), alpha = flash) }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFFBE0), Color(0xFFFFD25E), Color(0xFFFF9F2E)), startY = o.y, endY = o.y + s * 0.36f), alpha = flash)
}
