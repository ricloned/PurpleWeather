package com.purpleweather.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purpleweather.app.R
import kotlin.math.sqrt

/** Цветовая тема. */
data class Palette(
    val id: String,
    val name: String,
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
    val accent: Color,
    val accent2: Color,
    val lilac: Color,
    val dim: Color
)

object Palettes {
    val VIOLET = Palette("violet", "Фиолетовая", Color(0xFF3A1680), Color(0xFF1A0B3D), Color(0xFF0B0320),
        Color(0xFF9D5CFF), Color(0xFFFF7AD9), Color(0xFF5B2BC4), Color(0xFF9D5CFF), Color(0xFFFF7AD9), Color(0xFFC9B6FF), Color(0xFFB9A8E8))
    val SUNSET = Palette("sunset", "Закат", Color(0xFF5E1A4F), Color(0xFF2B0A3D), Color(0xFF12041F),
        Color(0xFFFF8A65), Color(0xFFFF4FA3), Color(0xFF8E2DE2), Color(0xFFFF7A9E), Color(0xFFFFB36B), Color(0xFFFFD3E8), Color(0xFFE5B3D0))
    val OCEAN = Palette("ocean", "Океан", Color(0xFF123C7A), Color(0xFF0B1E4A), Color(0xFF040B22),
        Color(0xFF4FD1FF), Color(0xFF9D7BFF), Color(0xFF1E5BFF), Color(0xFF5BC8FF), Color(0xFF9D7BFF), Color(0xFFC6E9FF), Color(0xFFA5BEE8))
    val AURORA = Palette("aurora", "Аврора", Color(0xFF0F3B4A), Color(0xFF1B0F3F), Color(0xFF050915),
        Color(0xFF4CFFB0), Color(0xFFB46CFF), Color(0xFF1FA2FF), Color(0xFF4CFFB0), Color(0xFFB46CFF), Color(0xFFCFFFEA), Color(0xFFA9C9D8))
    val NEON = Palette("neon", "Неон", Color(0xFF2A004F), Color(0xFF12002A), Color(0xFF05000F),
        Color(0xFFFF2BD6), Color(0xFF2BF0FF), Color(0xFF7A00FF), Color(0xFFFF2BD6), Color(0xFF2BF0FF), Color(0xFFFFC6F5), Color(0xFFC9A3FF))
    val CANDY = Palette("candy", "Конфета", Color(0xFF7A3FB8), Color(0xFF4A2382), Color(0xFF241046),
        Color(0xFFFFD1F7), Color(0xFFA6F0FF), Color(0xFFFF9BD5), Color(0xFFFF9BD5), Color(0xFFA6F0FF), Color(0xFFFFE6FA), Color(0xFFE2CCF5))
    val RAINBOW = Palette("rainbow", "Радуга", Color(0xFF3A1680), Color(0xFF1A0B3D), Color(0xFF0B0320),
        Color(0xFFFF5E5E), Color(0xFFFFD84A), Color(0xFF4CFFB0), Color(0xFF5BC8FF), Color(0xFFFF7AD9), Color(0xFFFFFFFF), Color(0xFFD8CCF5))

    val all = listOf(VIOLET, SUNSET, OCEAN, AURORA, NEON, CANDY)
    fun byId(id: String) = (all + RAINBOW).firstOrNull { it.id == id } ?: VIOLET
}

/** Текущая тема — обычное состояние Compose: смена темы перерисовывает всё. */
/** Плотность стеклянных панелей (0..1), меняется в настройках. */
object GlassStyle {
    val opacity = mutableFloatStateOf(0.5f)
}

/** Где на экране сейчас лежат панели — по ним прыгает котик. */
object Blocks {
    val rects = mutableStateMapOf<Any, Rect>()
}

fun Modifier.catBlock(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(key) { onDispose { Blocks.rects.remove(key) } }
    onGloballyPositioned { Blocks.rects[key] = it.boundsInRoot() }
}

object Pal {
    val state = mutableStateOf(Palettes.VIOLET)
    val value: Palette get() = state.value
}

/** Цвета интерфейса (берутся из текущей темы). */
object P {
    val night0 get() = Pal.value.bottom
    val night1 get() = Pal.value.mid
    val deep get() = lerpColor(Pal.value.mid, Color.Black, 0.2f)
    val violet get() = Pal.value.blob3
    val orchid get() = Pal.value.accent
    val pink get() = Pal.value.accent2
    val lilac get() = Pal.value.lilac
    val dim get() = Pal.value.dim
    val text = Color(0xFFF7F2FF)
    val sun1 = Color(0xFFFFF6C9)
    val sun2 = Color(0xFFFFB347)
    val sun3 = Color(0xFFFF6F91)
}

val Display = FontFamily(Font(R.font.russo_one))
val Body = FontFamily(Font(R.font.exo2))

@Composable
fun T(
    text: String,
    size: TextUnit = 15.sp,
    color: Color = P.text,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    display: Boolean = false,
    maxLines: Int = 1
) {
    Text(
        text, modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = if (display) Display else Body, fontSize = size, fontWeight = weight)
    )
}

/** «Объёмный» текст: слои глубины + градиент + блик сверху. */
@Composable
fun Text3D(text: String, size: TextUnit, modifier: Modifier = Modifier, depth: Int = 7) {
    Box(modifier) {
        // мягкая тень на «полу»
        Text(text, modifier = Modifier.offset(x = 6.dp, y = 14.dp),
            style = TextStyle(fontFamily = Display, fontSize = size, color = Color.Black.copy(alpha = 0.25f)))
        for (k in depth downTo 1) {
            Text(
                text, modifier = Modifier.offset(x = (k * 0.7f).dp, y = (k * 1.1f).dp),
                style = TextStyle(fontFamily = Display, fontSize = size, color = lerpColor(lerpColor(P.violet, Color.Black, 0.55f), P.orchid, 1f - k / depth.toFloat()))
            )
        }
        Text(text, style = TextStyle(fontFamily = Display, fontSize = size,
            brush = Brush.verticalGradient(listOf(Color.White, P.lilac, P.orchid))))
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    (a.red + (b.red - a.red) * t).coerceIn(0f, 1f), (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f), (a.alpha + (b.alpha - a.alpha) * t).coerceIn(0f, 1f)
)

/** Наклон телефона (−1..1 по осям) для параллакса и 3D-поворота. */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun rememberTilt(enabled: Boolean, onShake: () -> Unit): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    val shake = rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var sx = 0f
        var sy = 0f
        var lastShake = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                val g = sqrt(gx * gx + gy * gy + gz * gz) / 9.81f
                val now = System.currentTimeMillis()
                if (g > 2.4f && now - lastShake > 1500) { lastShake = now; shake.value() }
                if (!enabled) return
                val x = (-gx / 9.81f).coerceIn(-1f, 1f)
                val y = ((gy / 9.81f) - 0.7f).coerceIn(-1f, 1f)
                sx += (x - sx) * 0.1f
                sy += (y - sy) * 0.1f
                state.value = Offset(sx, sy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        if (!enabled) state.value = Offset.Zero
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

/**
 * Стеклянная плитка с толщиной: снизу «торец» стекла, сверху блик,
 * лёгкий 3D-поворот от наклона телефона. Без системной тени (она просвечивала тёмными блоками).
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    depth: Float = 1f,
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val tilt = LocalTilt.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .catBlock()
            .graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 6f * depth
                rotationX = -t.y * 6f * depth
                cameraDistance = 16f * density
            }
            .drawBehind {
                val r = corner.toPx()
                val thick = 5.dp.toPx()
                // всё, что «за» стеклом, рисуем только вне лицевой плитки,
                // иначе оно просвечивает сквозь полупрозрачное стекло тёмным блоком
                val face = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r)) }
                clipPath(face, ClipOp.Difference) {
                    // мягкое свечение вокруг
                    drawRoundRect(
                        Brush.radialGradient(listOf(P.orchid.copy(alpha = 0.22f), Color.Transparent), center, size.maxDimension * 0.7f),
                        topLeft = Offset(-12.dp.toPx(), -8.dp.toPx()), size = Size(size.width + 24.dp.toPx(), size.height + 24.dp.toPx()),
                        cornerRadius = CornerRadius(r + 12.dp.toPx())
                    )
                    // торец стекла (толщина) — видна только полоска снизу
                    drawRoundRect(
                        Brush.verticalGradient(listOf(P.orchid.copy(alpha = 0.55f), P.violet.copy(alpha = 0.5f)), startY = size.height - r, endY = size.height + thick),
                        topLeft = Offset(0f, thick), size = size, cornerRadius = CornerRadius(r)
                    )
                }
            }
            .clip(shape)
            .drawBehind {
                // 0..0.5 — стекло становится заметнее, 0.5..1 — добавляется плотная подложка темы
                val o = GlassStyle.opacity.floatValue
                val k = (o * 2f).coerceAtMost(1f)
                val solid = ((o - 0.5f) * 2f).coerceIn(0f, 1f) * 0.92f
                if (solid > 0f) drawRect(Brush.verticalGradient(listOf(P.night1.copy(alpha = solid), P.night0.copy(alpha = solid))))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f * k), Color.White.copy(alpha = 0.10f * k), Color.White.copy(alpha = 0.15f * k))))
            }
            .drawBehind {
                // блик по верхнему краю
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), endY = size.height * 0.35f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF))), shape)
            .padding(padding),
        content = content
    )
}

/** Появление элемента: выезжает снизу и проявляется, с задержкой по номеру. */
fun Modifier.appear(index: Int, key: Any? = Unit): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(60L * index)
        a.animateTo(1f, tween(520))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 60f
        val s = 0.94f + 0.06f * a.value
        scaleX = s; scaleY = s
        rotationX = (1f - a.value) * 18f
    }
}
