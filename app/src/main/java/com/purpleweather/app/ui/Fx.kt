package com.purpleweather.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.purpleweather.app.data.Sky
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Какой сезонный сюрприз сейчас. */
enum class Season { NONE, HALLOWEEN, NEW_YEAR, VALENTINE }

fun currentSeason(today: LocalDate = LocalDate.now()): Season {
    val m = today.monthValue
    val d = today.dayOfMonth
    return when {
        (m == 10 && d >= 24) || (m == 11 && d <= 2) -> Season.HALLOWEEN
        (m == 12 && d >= 15) || (m == 1 && d <= 14) -> Season.NEW_YEAR
        m == 2 && d in 12..15 -> Season.VALENTINE
        else -> Season.NONE
    }
}

/** Приветствие и «фраза дня» — каждый запуск новые. */
fun greeting(seed: Int): String {
    val h = LocalTime.now().hour
    val r = Random(seed)
    val base = when (h) {
        in 5..11 -> listOf("Доброе утро", "С добрым утром", "Утро доброе")
        in 12..17 -> listOf("Добрый день", "Хорошего дня", "Привет")
        in 18..22 -> listOf("Добрый вечер", "Хорошего вечера", "Вечер в хату")
        else -> listOf("Доброй ночи", "Пора спать 🦉", "Ночная смена")
    }
    val emoji = listOf("✨", "💜", "🔮", "🌌", "🪐", "☄️", "🫧", "🌙").random(r)
    return when (currentSeason()) {
        Season.HALLOWEEN -> "Жуткая погодка 🎃"
        Season.NEW_YEAR -> "С наступающим! 🎄"
        Season.VALENTINE -> "Погода для двоих 💘"
        Season.NONE -> "${base.random(r)} $emoji"
    }
}

fun phrase(sky: Sky, temp: Double, seed: Int): String {
    val r = Random(seed + sky.ordinal)
    val list = when {
        sky == Sky.STORM -> listOf("Лучше переждать дома ⚡", "Зарядите телефон заранее", "Гром — это просто облака спорят")
        sky == Sky.RAIN || sky == Sky.DRIZZLE -> listOf("Зонт не забудь ☂️", "Идеально для чая и пледа", "Лужи ждут твоих кроссовок")
        sky == Sky.SNOW -> listOf("Время лепить снеговика ⛄", "Шапку надень!", "Скользко — осторожнее")
        sky == Sky.FOG -> listOf("Как в Сайлент Хилле 🌫️", "Включи фары", "Мир стал загадочнее")
        temp >= 28 -> listOf("Пей воду 💧", "Тень — лучший друг", "Мороженое разрешено 🍦")
        temp <= -10 -> listOf("Одевайся как капуста 🧥", "Нос береги", "Горячий чай — обязательно")
        sky == Sky.CLEAR -> listOf("Отличный день для прогулки", "Солнце в деле ☀️", "Небо чистое — как совесть")
        else -> listOf("Обычный хороший день", "Облака красивые сегодня", "Погода для любимого плейлиста 🎧")
    }
    return list.random(r)
}

/** Фон: градиент темы, «туманности», объёмные сферы (каждый запуск — другие), звёзды, сезонные сюрпризы. */
@Composable
fun Backdrop(sky: Sky, day: Boolean, seed: Int, snowFx: Boolean) {
    val tr = rememberInfiniteTransition(label = "bg")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val slow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "slow")
    val fall by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "fall")
    val tw by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "tw")
    val tilt = LocalTilt.current
    val rnd = remember(seed) { Random(seed) }
    val stars = remember(seed) { List(80) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.7f, rnd.nextFloat()) } }
    // объёмные сферы и кольца: позиция, размер, глубина, тип
    val orbs = remember(seed) {
        List(6) { k ->
            Orb(rnd.nextFloat(), rnd.nextFloat(), 0.05f + rnd.nextFloat() * 0.12f, 0.3f + rnd.nextFloat() * 1.2f, rnd.nextInt(3), rnd.nextFloat() * 6.28f, k % 3)
        }
    }
    val flakes = remember(seed) { List(60) { Triple(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat()) } }
    val season = remember { currentSeason() }
    val pal = Pal.value
    val top = when {
        !day -> lerpColor(pal.bottom, pal.mid, 0.4f)
        sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM -> lerpColor(pal.top, Color(0xFF14204A), 0.45f)
        sky == Sky.SNOW || sky == Sky.FOG -> lerpColor(pal.top, Color(0xFF6E6A9E), 0.3f)
        else -> pal.top
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(top, pal.mid, pal.bottom)))
        val t = tilt.value
        val px = t.x * size.width * 0.03f
        val py = t.y * size.height * 0.02f
        fun blob(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(size.width * (0.15f + 0.2f * a) + px, size.height * 0.16f + py, size.width * 0.75f, pal.blob1.copy(alpha = 0.34f))
        blob(size.width * (0.9f - 0.15f * a) + px * 1.5f, size.height * 0.45f, size.width * 0.6f, pal.blob2.copy(alpha = 0.18f))
        blob(size.width * 0.5f - px, size.height * (0.86f - 0.05f * a), size.width * 0.85f, pal.blob3.copy(alpha = 0.35f))
        if (!day) for ((x, y, b) in stars) {
            drawCircle(Color.White.copy(alpha = (0.3f + 0.7f * b) * (if (b > 0.5f) tw else 1f)), 1.1f + 1.7f * b,
                Offset(x * size.width + px * 0.5f, y * size.height + py * 0.5f))
        }
        for (o in orbs) orb(o, slow, px, py)
        when (season) {
            Season.HALLOWEEN -> emojiRain(listOf("🎃", "🦇", "👻"), flakes, fall, 26f)
            Season.VALENTINE -> emojiRain(listOf("💜", "💖", "💘"), flakes, fall, 22f)
            Season.NEW_YEAR -> { snowfall(flakes, fall, px); garland(slow) }
            Season.NONE -> {}
        }
        if (snowFx && season != Season.NEW_YEAR) snowfall(flakes, fall, px)
    }
}

private class Orb(val x: Float, val y: Float, val r: Float, val depth: Float, val kind: Int, val phase: Float, val colorIdx: Int)

/** Плавающая объёмная сфера / кольцо / «планета» — с параллаксом по глубине. */
private fun DrawScope.orb(o: Orb, slow: Float, px: Float, py: Float) {
    val pal = Pal.value
    val col = when (o.colorIdx) { 0 -> pal.blob1; 1 -> pal.blob2; else -> pal.accent }
    val a = slow * 2 * PI.toFloat() + o.phase
    val c = Offset(
        o.x * size.width + sin(a) * size.width * 0.04f + px * o.depth * 2f,
        o.y * size.height + cos(a * 0.8f) * size.height * 0.03f + py * o.depth * 2f
    )
    val r = o.r * size.width * (0.6f + 0.4f * o.depth)
    val alpha = 0.18f + 0.22f * o.depth.coerceAtMost(1f)
    when (o.kind) {
        0 -> { // глянцевая сфера
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha * 1.6f), col.copy(alpha = alpha), lerpColor(col, Color.Black, 0.6f).copy(alpha = alpha * 0.8f)),
                c + Offset(-r * 0.35f, -r * 0.4f), r * 1.6f), r, c)
            drawCircle(Color.White.copy(alpha = alpha * 1.4f), r * 0.16f, c + Offset(-r * 0.38f, -r * 0.42f))
        }
        1 -> { // планета с кольцом
            drawCircle(Brush.radialGradient(listOf(lerpColor(col, Color.White, 0.4f).copy(alpha = alpha * 1.4f), lerpColor(col, Color.Black, 0.5f).copy(alpha = alpha)),
                c + Offset(-r * 0.3f, -r * 0.3f), r * 1.5f), r * 0.7f, c)
            rotate(-18f, c) {
                drawOval(Color.White.copy(alpha = alpha * 0.9f), topLeft = Offset(c.x - r * 1.25f, c.y - r * 0.28f), size = Size(r * 2.5f, r * 0.56f), style = Stroke(r * 0.07f))
            }
        }
        else -> { // стеклянное кольцо-тор
            drawCircle(Brush.sweepGradient(listOf(col.copy(alpha = alpha), Color.White.copy(alpha = alpha * 1.5f), col.copy(alpha = alpha * 0.4f), col.copy(alpha = alpha)), c),
                r * 0.8f, c, style = Stroke(r * 0.28f))
        }
    }
}

private fun DrawScope.snowfall(flakes: List<Triple<Float, Float, Float>>, fall: Float, px: Float) {
    for ((x, y, s) in flakes) {
        val yy = ((y + fall * s) % 1f) * size.height
        val xx = x * size.width + sin((yy / size.height * 8 + x * 10).toDouble()).toFloat() * 14f + px * s
        drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * (s - 0.4f)), 1.6f + 2.6f * s, Offset(xx, yy))
    }
}

private fun DrawScope.emojiRain(list: List<String>, flakes: List<Triple<Float, Float, Float>>, fall: Float, sizeDp: Float) {
    val paint = android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER }
    flakes.take(12).forEachIndexed { k, (x, y, s) ->
        val yy = ((y + fall * s * 0.6f) % 1f) * size.height
        val xx = x * size.width + sin((yy / size.height * 6 + k).toDouble()).toFloat() * 20f
        paint.textSize = sizeDp * density * (0.6f + 0.4f * s)
        paint.alpha = 140
        drawContext.canvas.nativeCanvas.drawText(list[k % list.size], xx, yy, paint)
    }
}

private fun DrawScope.garland(slow: Float) {
    val colors = listOf(Color(0xFFFF5E8A), Color(0xFFFFD84A), Color(0xFF4CFFB0), Color(0xFF5BC8FF), Color(0xFFB46CFF))
    val n = 14
    for (k in 0 until n) {
        val x = size.width * (k + 0.5f) / n
        val y = 30f + sin((k / n.toFloat() * PI).toFloat()) * 40f
        val on = ((slow * 40).toInt() + k) % 3 != 0
        val c = colors[k % colors.size]
        if (on) drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.6f), Color.Transparent), Offset(x, y), 22f), 22f, Offset(x, y))
        drawCircle(if (on) c else c.copy(alpha = 0.3f), 6f, Offset(x, y))
    }
}

/** Конфетти: вспыхивает при каждом изменении trigger (тряска, пасхалки). */
@Composable
fun Confetti(trigger: Int) {
    if (trigger == 0) return
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val parts = remember(trigger) {
        val r = Random(trigger * 31 + 7)
        List(90) { floatArrayOf(r.nextFloat(), -0.1f - r.nextFloat() * 0.3f, (r.nextFloat() - 0.5f) * 0.6f, 0.3f + r.nextFloat() * 0.7f, r.nextFloat() * 360f, r.nextInt(5).toFloat()) }
    }
    LaunchedEffect(trigger) {
        start = withFrameMillis { it }
        while (true) {
            now = withFrameMillis { it }
            if (now - start > 3200) break
        }
    }
    val el = ((now - start) / 1000f).coerceAtLeast(0f)
    if (el > 3.1f) return
    val pal = Pal.value
    val colors = listOf(pal.accent, pal.accent2, pal.lilac, Color(0xFFFFD84A), Color.White)
    Canvas(Modifier.fillMaxSize()) {
        for (p in parts) {
            val x = (p[0] + p[2] * el * 0.3f) * size.width
            val y = (p[1] + p[3] * el * 0.45f + 0.12f * el * el) * size.height
            if (y > size.height) continue
            rotate(p[4] + el * 300f * p[3], Offset(x, y)) {
                drawRect(colors[p[5].toInt()], topLeft = Offset(x - 6f, y - 10f), size = Size(12f, 20f), alpha = (1f - el / 3.1f).coerceIn(0f, 1f))
            }
        }
    }
}
