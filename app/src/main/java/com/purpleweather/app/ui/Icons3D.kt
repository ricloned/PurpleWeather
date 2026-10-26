package com.purpleweather.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import com.purpleweather.app.data.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Монотонные «часы» в секундах. Значение читается только при рисовании,
 * поэтому анимация перерисовывает лишь Canvas, без рекомпозиции экрана.
 * Время не «перематывается» по кругу — нет рывков в конце цикла.
 */
@Composable
fun rememberClock(running: Boolean): State<Float> {
    val st = remember { mutableFloatStateOf(0.6f) }
    if (running) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { st.floatValue = 0.6f + (it - start) / 1_000_000_000f }
    }
    return st
}

private fun fract(x: Float) = x - floor(x)

/** Частицы — генерируются один раз. */
private class Drop(val x: Float, val speed: Float, val phase: Float, val z: Float)

private val RAIN = Random(7).let { r -> List(26) { Drop(r.nextFloat(), 0.9f + r.nextFloat() * 0.6f, r.nextFloat(), 0.45f + r.nextFloat() * 0.55f) } }
private val SNOW = Random(11).let { r -> List(28) { Drop(r.nextFloat(), 0.10f + r.nextFloat() * 0.10f, r.nextFloat(), 0.4f + r.nextFloat() * 0.6f) } }

/**
 * Объёмная иконка погоды: светотень, толщина облаков, падающие капли со следом
 * и брызгами, кружащийся снег, вспышки молний, плывущий туман.
 * parallax — состояние наклона телефона (читается при рисовании).
 */
@Composable
fun WeatherIcon3D(
    sky: Sky,
    isDay: Boolean,
    modifier: Modifier,
    animated: Boolean = true,
    parallax: State<Offset>? = null,
    floor: Boolean = false,
    cat: Boolean = false
) {
    val clock = rememberClock(animated)
    val catPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }
    Spacer(modifier.drawWithCache {
        val s = size.minDimension
        // облака и лучи строятся один раз на размер — дорогие операции с путями не повторяются каждый кадр
        val clouds = HashMap<Int, Path>()
        fun cloudPath(w: Float) = clouds.getOrPut((w * 10).toInt()) { buildCloud(w) }
        val ray = Path().apply {
            val r = s * 0.3f
            moveTo(r * 0.95f, -r * 0.07f); lineTo(r * 2.2f, 0f); lineTo(r * 0.95f, r * 0.07f); close()
        }
        onDrawBehind {
            val time = clock.value
            val tilt = parallax?.value ?: Offset.Zero
            val p = Offset(tilt.x * s * 0.06f, tilt.y * s * 0.06f)
            val back = p * 0.45f
            val front = p * 1.35f + Offset(sin(time * 0.35f) * s * 0.02f, sin(time * 0.6f) * s * 0.01f)
            if (floor) {
                val w = s * (0.6f - 0.03f * sin(time * 0.6f))
                drawOval(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent), Offset(center.x + p.x * 0.3f, s * 0.95f), w / 2),
                    topLeft = Offset(center.x - w / 2 + p.x * 0.3f, s * 0.9f), size = Size(w, s * 0.1f)
                )
            }
            val c = center
            when (sky) {
                Sky.CLEAR -> if (isDay) sun(c + back, s * 0.30f, time, p, ray) else moon(c + back, s * 0.28f, time)
                Sky.PARTLY -> {
                    val sc = Offset(s * 0.37f, s * 0.35f) + back
                    if (isDay) scale(0.74f, sc) { sun(sc, s * 0.30f, time, p, ray) } else moon(sc, s * 0.2f, time)
                    val drift = Offset(sin(time * 0.25f) * s * 0.035f, 0f)
                    cloud(Offset(s * 0.56f, s * 0.6f) + front + drift, s * 0.62f, CloudTone.LIGHT, cloudPath(s * 0.62f), time)
                }
                Sky.CLOUDY -> {
                    cloud(Offset(s * 0.36f, s * 0.42f) + back + Offset(-sin(time * 0.2f) * s * 0.03f, 0f), s * 0.5f, CloudTone.MID, cloudPath(s * 0.5f), time + 3f)
                    cloud(Offset(s * 0.57f, s * 0.6f) + front, s * 0.66f, CloudTone.LIGHT, cloudPath(s * 0.66f), time)
                }
                Sky.FOG -> {
                    cloud(Offset(s * 0.5f, s * 0.42f) + back, s * 0.6f, CloudTone.MID, cloudPath(s * 0.6f), time)
                    fog(s, time, front)
                }
                Sky.DRIZZLE, Sky.RAIN -> {
                    val heavy = sky == Sky.RAIN
                    val cc = Offset(s * 0.5f, s * 0.42f) + front
                    rain(s, time, cc, s * 0.72f, heavy)
                    cloud(cc, s * 0.72f, if (heavy) CloudTone.MID else CloudTone.LIGHT, cloudPath(s * 0.72f), time)
                }
                Sky.SNOW -> {
                    val cc = Offset(s * 0.5f, s * 0.42f) + front
                    snow(s, time, cc, s * 0.72f)
                    cloud(cc, s * 0.72f, CloudTone.LIGHT, cloudPath(s * 0.72f), time)
                }
                Sky.STORM -> {
                    val cc = Offset(s * 0.5f, s * 0.4f) + front
                    val fl = flash(time)
                    rain(s, time, cc, s * 0.74f, true)
                    lightning(s, time, cc, fl)
                    cloud(cc, s * 0.74f, CloudTone.DARK, cloudPath(s * 0.74f), time, fl)
                }
            }
            if (cat) {
                catPaint.textSize = s * 0.22f
                drawContext.canvas.nativeCanvas.drawText("🐱", s * 0.6f + front.x, s * 0.36f + front.y, catPaint)
            }
        }
    })
}

private enum class CloudTone { LIGHT, MID, DARK }

// ---------------- солнце ----------------
private fun DrawScope.sun(c: Offset, r: Float, time: Float, p: Offset, ray: Path) {
    val breathe = 1f + 0.035f * sin(time * 1.3f)
    // корона
    drawCircle(Brush.radialGradient(listOf(P.sun2.copy(alpha = 0.45f), P.sun3.copy(alpha = 0.14f), Color.Transparent), c, r * 2.4f * breathe), r * 2.4f * breathe, c)
    drawCircle(Brush.radialGradient(listOf(P.sun1.copy(alpha = 0.55f), Color.Transparent), c, r * 1.55f), r * 1.55f, c)
    // мягкие лучи: плавное непрерывное вращение (угол растёт без сброса)
    val rayBrush = Brush.radialGradient(listOf(P.sun1.copy(alpha = 0.75f), P.sun2.copy(alpha = 0.35f), Color.Transparent), Offset.Zero, r * 2.2f)
    val base = (time * 7f) % 360f
    translate(c.x, c.y) {
        for (k in 0 until 12) {
            val len = 0.82f + 0.18f * sin(time * 0.9f + k * 1.7f)
            rotate(base + k * 30f, Offset.Zero) {
                scale(len, 1f + 0.4f * (k % 2), Offset.Zero) { drawPath(ray, rayBrush) }
            }
        }
    }
    // шар: свет сверху-слева, плавно «гуляющая» горячая точка
    val hot = Offset(-r * 0.4f + sin(time * 0.5f) * r * 0.05f, -r * 0.4f + cos(time * 0.4f) * r * 0.05f)
    drawCircle(Brush.radialGradient(listOf(P.sun1, P.sun2, Color(0xFFFF8A3D), P.sun3), c + hot, r * 1.75f), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x66FFE2A8)), c, r), r, c, style = Stroke(r * 0.06f))
    val hl = c + Offset(-r * 0.38f, -r * 0.42f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)), hl, r * 0.42f), r * 0.42f, hl)
    // блики объектива — смещаются при наклоне
    val dir = Offset(-p.x, -p.y) * 3f
    for ((k, a) in listOf(0.9f to 0.16f, 1.6f to 0.1f, 2.3f to 0.07f)) {
        val q = c + Offset(r * k * 0.6f, r * k * 0.5f) + dir * k
        drawCircle(P.sun1.copy(alpha = a), r * 0.12f / k, q)
    }
}

// ---------------- луна ----------------
private fun DrawScope.moon(c: Offset, r: Float, time: Float) {
    val breathe = 1f + 0.04f * sin(time * 0.9f)
    drawCircle(Brush.radialGradient(listOf(P.lilac.copy(alpha = 0.38f), Color.Transparent), c, r * 2.3f * breathe), r * 2.3f * breathe, c)
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6DCFF), Color(0xFFA996EE), Color(0xFF6E5BC8)), c + Offset(-r * 0.35f, -r * 0.4f), r * 1.8f), r, c)
    val crater = Color(0xFF7D69D0)
    for ((dx, dy, rr) in listOf(Triple(0.28f, -0.12f, 0.19f), Triple(-0.22f, 0.36f, 0.13f), Triple(0.08f, 0.52f, 0.09f), Triple(-0.36f, -0.2f, 0.08f))) {
        val cc = c + Offset(r * dx, r * dy)
        drawCircle(Brush.radialGradient(listOf(crater.copy(alpha = 0.55f), crater.copy(alpha = 0.15f)), cc + Offset(-r * rr * 0.2f, -r * rr * 0.2f), r * rr), r * rr, cc)
        drawCircle(Color.White.copy(alpha = 0.25f), r * rr, cc + Offset(r * rr * 0.12f, r * rr * 0.14f), style = Stroke(r * 0.02f))
    }
    // мерцающие звёзды
    for (k in 0 until 4) {
        val tw = 0.5f + 0.5f * sin(time * (1.3f + k * 0.4f) + k * 2f)
        val pos = c + Offset(r * (1.35f - k * 0.55f), -r * (1.15f - k * 0.5f))
        val sz = r * (0.05f + 0.03f * tw)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * tw), Color.Transparent), pos, sz * 4), sz * 4, pos)
        drawLine(Color.White.copy(alpha = 0.8f * tw), pos - Offset(sz * 2.5f, 0f), pos + Offset(sz * 2.5f, 0f), sz * 0.35f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.8f * tw), pos - Offset(0f, sz * 2.5f), pos + Offset(0f, sz * 2.5f), sz * 0.35f, StrokeCap.Round)
    }
}

// ---------------- облако ----------------
/** Силуэт облака с центром в (0,0). Строится один раз. */
private fun buildCloud(w: Float): Path {
    val path = Path()
    fun blob(dx: Float, dy: Float, r: Float) {
        val p = Path().apply { addOval(Rect(Offset(dx * w, dy * w), r * w)) }
        path.op(path, p, PathOperation.Union)
    }
    blob(-0.27f, 0.06f, 0.2f)
    blob(-0.05f, -0.09f, 0.28f)
    blob(0.21f, 0.0f, 0.23f)
    blob(0.06f, 0.08f, 0.2f)
    val base = Path().apply { addRoundRect(RoundRect(-w * 0.45f, 0f, w * 0.45f, w * 0.25f, w * 0.125f, w * 0.125f)) }
    path.op(path, base, PathOperation.Union)
    return path
}

private fun DrawScope.cloud(c: Offset, w: Float, tone: CloudTone, body: Path, time: Float, flash: Float = 0f) {
    val (top, mid, bottom, under) = when (tone) {
        CloudTone.LIGHT -> listOf(Color(0xFFFFFFFF), Color(0xFFEDE6FF), Color(0xFFB7A3F2), Color(0xFF7C66D6))
        CloudTone.MID -> listOf(Color(0xFFF1EBFF), Color(0xFFCDBEFA), Color(0xFF8E78E0), Color(0xFF5B45B5))
        CloudTone.DARK -> listOf(Color(0xFFC4B6EE), Color(0xFF8B79CC), Color(0xFF4B3496), Color(0xFF2C1C66))
    }
    // тень на фоне под облаком
    drawOval(Brush.radialGradient(listOf(Color(0x55120530), Color.Transparent), Offset(c.x, c.y + w * 0.36f), w * 0.5f),
        topLeft = Offset(c.x - w * 0.5f, c.y + w * 0.26f), size = Size(w, w * 0.2f))
    val breathe = 1f + 0.012f * sin(time * 0.7f)
    translate(c.x, c.y) {
        scale(breathe, 2f - breathe, Offset(0f, w * 0.2f)) {
            translate(top = w * 0.045f) { drawPath(body, under) }
            drawPath(body, Brush.verticalGradient(listOf(top, mid, bottom), startY = -w * 0.38f, endY = w * 0.26f))
            clipPath(body) {
                val box = Offset(-w, -w); val bs = Size(w * 2, w * 2)
                // мягкая тень внутри снизу
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, under.copy(alpha = 0.45f)), startY = w * 0.05f, endY = w * 0.26f), box, bs)
                // свет сверху-слева, медленно перетекает
                val lc = Offset(-w * 0.12f + sin(time * 0.3f) * w * 0.05f, -w * 0.24f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), lc, w * 0.32f), w * 0.32f, lc)
                // цветной отсвет темы снизу
                val pc = Offset(w * 0.3f, w * 0.25f)
                drawCircle(Brush.radialGradient(listOf(P.pink.copy(alpha = 0.22f), Color.Transparent), pc, w * 0.35f), w * 0.35f, pc)
                // вспышка молнии подсвечивает облако изнутри
                if (flash > 0.01f) {
                    val fc = Offset(w * 0.02f, w * 0.12f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6D8).copy(alpha = 0.85f * flash), Color(0xFFB9A8FF).copy(alpha = 0.3f * flash), Color.Transparent), fc, w * 0.6f), w * 0.6f, fc)
                }
            }
            drawPath(body, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), startY = -w * 0.38f, endY = 0f), style = Stroke(w * 0.012f))
        }
    }
}

// ---------------- дождь ----------------
/** Капли вылетают из-под облака, падают со следом под небольшим углом и разбиваются о «пол». */
private fun DrawScope.rain(s: Float, time: Float, cloudC: Offset, cloudW: Float, heavy: Boolean) {
    val n = if (heavy) RAIN.size else 13
    val top = cloudC.y + cloudW * 0.12f
    val floorY = s * 0.95f
    val fallH = floorY - top
    val wind = s * 0.07f
    for (k in 0 until n) {
        val d = RAIN[k]
        val speed = d.speed * (if (heavy) 1.15f else 0.75f)
        val cyc = fract(time * speed + d.phase)
        val x0 = cloudC.x - cloudW * 0.4f + d.x * cloudW * 0.8f
        val fall = 0.86f
        if (cyc < fall) {
            val p = cyc / fall
            val pe = p * p * 0.35f + p * 0.65f // лёгкое ускорение
            val y = top + fallH * pe
            val x = x0 + wind * pe
            val len = s * (if (heavy) 0.11f else 0.06f) * d.z
            val a = (if (p < 0.12f) p / 0.12f else 1f) * (0.45f + 0.55f * d.z)
            val tail = Offset(x - wind * len / fallH, y - len)
            drawLine(
                Brush.linearGradient(listOf(Color(0x00BFE3FF), Color(0xFFD9F0FF).copy(alpha = a)), tail, Offset(x, y)),
                tail, Offset(x, y), s * (if (heavy) 0.009f else 0.006f) * (0.6f + d.z * 0.6f), StrokeCap.Round
            )
        } else {
            // брызги: расходящееся колечко и две мелкие капельки
            val q = (cyc - fall) / (1f - fall)
            val x = x0 + wind
            val a = (1f - q) * (0.35f + 0.5f * d.z)
            val rw = s * 0.05f * d.z * (0.3f + q)
            drawOval(Color(0xFFCFE6FF).copy(alpha = a), Offset(x - rw, floorY - rw * 0.18f), Size(rw * 2, rw * 0.36f), style = Stroke(s * 0.004f))
            if (heavy) for (sd in listOf(-1f, 1f)) {
                val dx = sd * s * 0.035f * q * d.z
                val dy = -s * 0.05f * d.z * 4f * q * (1f - q)
                drawCircle(Color(0xFFE6F3FF).copy(alpha = a), s * 0.0045f, Offset(x + dx, floorY + dy))
            }
        }
    }
}

// ---------------- снег ----------------
/** Снежинки разного размера: дальние мелкие и медленные, ближние крупные, кружатся и покачиваются. */
private fun DrawScope.snow(s: Float, time: Float, cloudC: Offset, cloudW: Float) {
    val top = cloudC.y + cloudW * 0.1f
    val floorY = s * 0.96f
    for (d in SNOW) {
        val cyc = fract(time * d.speed * (0.6f + d.z * 0.6f) + d.phase)
        val y = top + (floorY - top) * cyc
        val sway = sin(time * (0.9f + d.z) + d.phase * 6.28f) * s * 0.035f * d.z
        val x = cloudC.x - cloudW * 0.45f + d.x * cloudW * 0.9f + sway
        val a = when { cyc < 0.1f -> cyc / 0.1f; cyc > 0.85f -> (1f - cyc) / 0.15f; else -> 1f } * (0.5f + 0.5f * d.z)
        val r = s * (0.008f + 0.016f * d.z)
        if (d.z > 0.78f) {
            // крупная — шестилучевая, медленно вращается
            val ang = time * 40f * (d.phase - 0.5f) + d.phase * 360f
            rotate(ang, Offset(x, y)) {
                for (k in 0 until 3) {
                    val aa = k * PI.toFloat() / 3f
                    val dx = cos(aa) * r * 1.4f; val dy = sin(aa) * r * 1.4f
                    drawLine(Color.White.copy(alpha = a), Offset(x - dx, y - dy), Offset(x + dx, y + dy), r * 0.28f, StrokeCap.Round)
                }
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * a), Color.Transparent), Offset(x, y), r * 2.2f), r * 2.2f, Offset(x, y))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), P.lilac.copy(alpha = a * 0.25f), Color.Transparent), Offset(x, y), r * 1.3f), r * 1.3f, Offset(x, y))
        }
    }
}

// ---------------- туман ----------------
private fun DrawScope.fog(s: Float, time: Float, off: Offset) {
    for (k in 0 until 4) {
        val y = s * (0.58f + 0.09f * k) + off.y + sin(time * 0.4f + k) * s * 0.008f
        val dx = sin(time * (0.18f + k * 0.05f) + k * 1.9f) * s * 0.09f
        val a = 0.65f + 0.35f * sin(time * 0.5f + k * 2.3f)
        val w = s * (0.7f + 0.08f * (k % 2))
        val x = (s - w) / 2 + dx + off.x
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFEFE8FF).copy(alpha = 0.8f * a), Color(0xFFC9B6FF).copy(alpha = 0.55f * a), Color.Transparent), startX = x, endX = x + w),
            topLeft = Offset(x, y), size = Size(w, s * 0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f)
        )
    }
}

// ---------------- гроза ----------------
private const val STORM_PERIOD = 3.7f

/** Яркость вспышки: двойное мигание и плавное затухание, раз в несколько секунд. */
private fun flash(time: Float): Float {
    val lt = time % STORM_PERIOD
    return when {
        lt < 0.06f -> lt / 0.06f
        lt < 0.12f -> 0.25f
        lt < 0.18f -> 1f
        else -> exp(-(lt - 0.18f) * 7f)
    }.coerceIn(0f, 1f)
}

/** Ломаная молния — каждый удар новой формы. */
private fun DrawScope.lightning(s: Float, time: Float, cc: Offset, fl: Float) {
    if (fl < 0.03f) return
    val r = Random(floor(time / STORM_PERIOD).toInt() * 9173 + 1)
    val x0 = cc.x + (r.nextFloat() - 0.5f) * s * 0.25f
    val y0 = cc.y + s * 0.12f
    val len = s * 0.95f - y0
    val main = Path()
    val branch = Path()
    var x = x0; var y = y0
    main.moveTo(x, y)
    val steps = 7
    for (i in 1..steps) {
        x += (r.nextFloat() - 0.5f) * s * 0.11f
        y += len / steps
        main.lineTo(x, y)
        if (i == 3) {
            branch.moveTo(x, y)
            var bx = x; var by = y
            val side = if (r.nextBoolean()) 1f else -1f
            repeat(3) { bx += side * s * (0.03f + r.nextFloat() * 0.04f); by += len / steps * 0.8f; branch.lineTo(bx, by) }
        }
    }
    val glow = Color(0xFFC9B6FF)
    for (p in listOf(main, branch)) {
        drawPath(p, glow.copy(alpha = 0.35f * fl), style = Stroke(s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, Color(0xFFFFF4C2).copy(alpha = 0.9f * fl), style = Stroke(s * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, Color.White.copy(alpha = fl), style = Stroke(s * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // отблеск на «полу»
    drawOval(Brush.radialGradient(listOf(Color(0xFFFFF4C2).copy(alpha = 0.4f * fl), Color.Transparent), Offset(x, s * 0.95f), s * 0.18f),
        Offset(x - s * 0.18f, s * 0.92f), Size(s * 0.36f, s * 0.06f))
}
