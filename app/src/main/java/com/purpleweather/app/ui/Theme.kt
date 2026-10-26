package com.purpleweather.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Фиолетовая палитра. */
object P {
    val night0 = Color(0xFF0B0320)
    val night1 = Color(0xFF1A0B3D)
    val deep = Color(0xFF2A0E61)
    val violet = Color(0xFF5B2BC4)
    val orchid = Color(0xFF9D5CFF)
    val lilac = Color(0xFFC9B6FF)
    val pink = Color(0xFFFF7AD9)
    val text = Color(0xFFF4EEFF)
    val dim = Color(0xFFB9A8E8)
    val glass = Color(0x1FFFFFFF)
    val sun1 = Color(0xFFFFF3B0)
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

/** «Объёмный» текст: несколько слоёв со сдвигом + градиент сверху. */
@Composable
fun Text3D(text: String, size: TextUnit, modifier: Modifier = Modifier, depth: Int = 6) {
    Box(modifier) {
        for (k in depth downTo 1) {
            Text(
                text, modifier = Modifier.offset(x = (k * 0.8f).dp, y = (k * 1.2f).dp),
                style = TextStyle(fontFamily = Display, fontSize = size, color = lerpColor(P.deep, P.violet, 1f - k / depth.toFloat()).copy(alpha = 0.9f))
            )
        }
        Text(
            text,
            style = TextStyle(
                fontFamily = Display, fontSize = size,
                brush = Brush.verticalGradient(listOf(Color.White, P.lilac, P.orchid))
            )
        )
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t
)

/** Наклон телефона (−1..1 по осям) для параллакса и 3D-поворота карточек. */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var sx = 0f
        var sy = 0f
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                // телефон обычно держат под наклоном ~45°: это «ноль»
                val y = ((e.values[1] / 9.81f) - 0.7f).coerceIn(-1f, 1f)
                sx += (x - sx) * 0.12f
                sy += (y - sy) * 0.12f
                state.value = Offset(sx, sy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        else state.value = Offset.Zero
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

/** Стеклянная карточка с объёмом: тень, градиентная рамка и лёгкий 3D-поворот от наклона. */
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
            .graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 7f * depth
                rotationX = -t.y * 7f * depth
                cameraDistance = 14f * density
            }
            .shadow(18.dp, shape, ambientColor = P.violet, spotColor = Color(0xFF6A2BFF))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x10FFFFFF))), shape)
            .padding(padding),
        content = content
    )
}
