package com.purpleweather.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.purpleweather.app.Screen
import com.purpleweather.app.UpdateState
import com.purpleweather.app.WeatherViewModel
import com.purpleweather.app.data.City
import com.purpleweather.app.data.Day
import com.purpleweather.app.data.Forecast
import com.purpleweather.app.data.Sky
import com.purpleweather.app.data.Wmo
import com.purpleweather.app.update.Updater
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

private val RU = Locale("ru")

@Composable
fun WeatherApp(vm: WeatherViewModel) {
    val tilt = rememberTilt(vm.tilt) { vm.boom() }
    // диско-режим: тема переливается
    LaunchedEffect(vm.disco) {
        if (!vm.disco) return@LaunchedEffect
        val base = Pal.value
        var h = 0f
        try {
            while (true) {
                h = (h + 9f) % 360f
                fun hue(c: Color, d: Float): Color {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
                    hsv[0] = (hsv[0] + d) % 360f
                    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = c.alpha)
                }
                Pal.state.value = base.copy(
                    top = hue(base.top, h), blob1 = hue(base.blob1, h), blob2 = hue(base.blob2, h), blob3 = hue(base.blob3, h),
                    accent = hue(base.accent, h), accent2 = hue(base.accent2, h)
                )
                delay(90)
            }
        } finally {
            Pal.state.value = base
        }
    }
    CompositionLocalProvider(LocalTilt provides tilt) {
        val cur = vm.cities.getOrNull(vm.page)
        val f = cur?.let { vm.forecasts[it.id] }
        var touches by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxSize().background(P.night0).pointerInput(Unit) {
            // любое касание сбрасывает таймер бездействия (событие не перехватываем)
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press) touches++
                }
            }
        }) {
            Backdrop(f?.let { Wmo.sky(it.current.code) } ?: Sky.CLOUDY, f?.current?.isDay ?: true, vm.seed, vm.snowFx, vm.fallingFx)
            when (vm.screen) {
                Screen.MAIN -> MainScreen(vm)
                Screen.SEARCH -> SearchScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.DAY -> if (cur != null && f != null) DayScreen(vm, cur, f) else LaunchedEffect(Unit) { vm.screen = Screen.MAIN }
            }
            if (vm.screen == Screen.MAIN) UpdateBanner(vm)
            IdleCat(touches + vm.screen.ordinal * 1000 + vm.page * 100000, vm.idleCat && vm.screen == Screen.MAIN)
            Confetti(vm.confetti)
            val msg = vm.message
            LaunchedEffect(msg) {
                if (msg != null) { delay(2600); if (vm.message == msg) vm.message = null }
            }
            if (msg != null) {
                Box(
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)
                        .clip(RoundedCornerShape(18.dp)).background(lerpColor(P.violet, Color.Black, 0.4f).copy(alpha = 0.92f))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) { T(msg, 14.sp, maxLines = 3) }
            }
        }
    }
    BackHandler(enabled = vm.screen != Screen.MAIN) { vm.screen = Screen.MAIN }
}

// ---------------- главный экран ----------------

@Composable
private fun MainScreen(vm: WeatherViewModel) {
    val pager = rememberPagerState(initialPage = vm.page.coerceIn(0, maxOf(0, vm.cities.lastIndex))) { vm.cities.size }
    LaunchedEffect(vm.page, vm.cities.size) {
        if (vm.page in vm.cities.indices && pager.currentPage != vm.page) pager.animateScrollToPage(vm.page)
    }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { if (vm.page != it) { vm.page = it; vm.selectedDay = 0 } } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(vm, pager.currentPage)
        if (vm.cities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Добавь город", 20.sp, display = true)
                    Spacer(Modifier.height(12.dp))
                    Pill("＋ Найти город") { vm.screen = Screen.SEARCH }
                }
            }
        } else {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                val c = vm.cities[i]
                // 3D-куб при перелистывании городов
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction)
                    cameraDistance = 22f * density
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (off > 0) 1f else 0f, 0.5f)
                    rotationY = (off * -70f).coerceIn(-90f, 90f)
                    alpha = (1f - kotlin.math.abs(off) * 0.6f).coerceIn(0f, 1f)
                }) {
                    CityPage(vm, c, vm.forecasts[c.id])
                }
            }
        }
    }
}

@Composable
private fun TopBar(vm: WeatherViewModel, page: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundBtn("☰") { vm.screen = Screen.SETTINGS }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.cities.forEachIndexed { k, c ->
                Box(
                    Modifier.size(if (k == page) 9.dp else 6.dp).clip(CircleShape)
                        .background(if (k == page) Color.White else Color.White.copy(alpha = 0.35f))
                ) {
                    if (c.isMyLocation && k != page) Box(Modifier.fillMaxSize().background(P.pink))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        RoundBtn("＋") { vm.screen = Screen.SEARCH }
    }
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0x26FFFFFF))
            .border(1.dp, Color(0x40FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { T(label, 20.sp) }
}

@Composable
private fun Pill(label: String, primary: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) Brush.horizontalGradient(listOf(P.orchid, P.pink)) else Brush.horizontalGradient(listOf(Color(0x26FFFFFF), Color(0x26FFFFFF))))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp)
    ) { T(label, 15.sp, weight = FontWeight.Bold) }
}

@Composable
private fun CityPage(vm: WeatherViewModel, c: City, f: Forecast?) {
    val tilt = LocalTilt.current
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    var preview by remember { mutableStateOf<Sky?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        T(greeting(vm.seed), 13.sp, P.lilac, modifier = Modifier.appear(0, c.id))
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(1, c.id)) {
            if (c.isMyLocation) { T("➤ ", 16.sp, P.pink) }
            T(c.name, 26.sp, display = true)
        }
        T(c.region, 13.sp, P.dim)
        if (f == null) {
            Spacer(Modifier.height(120.dp))
            T(if (vm.loadingIds[c.id] == true) "Загружаю погоду…" else "Нет данных", 16.sp, P.dim)
            Spacer(Modifier.height(16.dp))
            Pill("Обновить") { vm.refresh(c, force = true) }
            return@Column
        }
        val cur = f.current
        val sky = Wmo.sky(cur.code)
        val shown = preview ?: sky
        // ---- герой: 3D-иконка (тап — крутится и показывает случайную погоду) ----
        Box(contentAlignment = Alignment.Center) {
            WeatherIcon3D(
                shown, if (preview != null) true else cur.isDay,
                Modifier.size(240.dp).appear(2, c.id).graphicsLayer {
                    val t = tilt.value
                    rotationY = t.x * 16f + spin.value
                    rotationX = -t.y * 12f
                    cameraDistance = 12f * density
                }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch {
                        val other = Sky.entries.filter { it != shown }.random()
                        spin.snapTo(0f)
                        spin.animateTo(360f, tween(900))
                        preview = other
                        vm.message = "А вот так выглядит: ${skyName(other)}"
                        delay(2800)
                        spin.snapTo(0f)
                        spin.animateTo(-360f, tween(700))
                        spin.snapTo(0f)
                        preview = null
                    }
                },
                parallax = tilt, floor = true, cat = vm.cat
            )
        }
        // температура «набегает» от нуля
        val anim = remember(c.id) { Animatable(0f) }
        LaunchedEffect(c.id, cur.temp) { anim.animateTo(cur.temp.toFloat(), tween(1100)) }
        Text3D(vm.t(anim.value.toDouble()), 96.sp,
            Modifier.padding(start = 18.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.onTempTap() })
        T(Wmo.text(cur.code), 20.sp, weight = FontWeight.Bold)
        val today = f.days.firstOrNull()
        if (today != null) T("Макс ${vm.t(today.max)} · Мин ${vm.t(today.min)} · Ощущается ${vm.t(cur.feels)}", 13.sp, P.dim)
        Spacer(Modifier.height(4.dp))
        T(phrase(sky, cur.temp, vm.seed), 14.sp, P.lilac)
        Spacer(Modifier.height(6.dp))
        val age = (System.currentTimeMillis() - f.fetchedAt) / 60_000
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(if (age < 1) "обновлено только что" else "обновлено $age мин назад", 11.sp, P.dim.copy(alpha = 0.8f))
            Spacer(Modifier.width(8.dp))
            T(if (vm.loadingIds[c.id] == true) "⟳ …" else "⟳ обновить", 11.sp, P.lilac, modifier = Modifier.clickable { vm.refresh(c, force = true) })
        }
        Spacer(Modifier.height(18.dp))

        // ---- по часам с выбором дня ----
        Glass(Modifier.fillMaxWidth().appear(3, c.id), padding = 12.dp) {
            Column {
                T("ПО ЧАСАМ", 12.sp, P.dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DayChips(vm, f)
                Spacer(Modifier.height(10.dp))
                val hours = hoursFor(f, vm.selectedDay)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hours.withIndex().toList(), key = { it.value.time }) { (k, h) ->
                        val now = vm.selectedDay == 0 && k == 0
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (now) Brush.verticalGradient(listOf(P.orchid.copy(alpha = 0.6f), P.violet.copy(alpha = 0.25f)))
                                else Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            T(if (now) "Сейчас" else h.time.substringAfter('T').take(5), 12.sp, P.dim)
                            WeatherIcon3D(Wmo.sky(h.code), h.isDay, Modifier.size(44.dp), animated = false)
                            T(vm.t(h.temp), 16.sp, weight = FontWeight.Bold)
                            T(if (h.pop >= 10) "💧${h.pop}%" else " ", 10.sp, Color(0xFF9FD8FF))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                T("Подробнее о дне →", 13.sp, P.lilac, modifier = Modifier.clickable { vm.openDay(vm.selectedDay) }.padding(4.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- 10 дней ----
        Glass(Modifier.fillMaxWidth().appear(4, c.id), padding = 14.dp) {
            Column {
                T("НА 10 ДНЕЙ · нажми на день", 12.sp, P.dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val lo = f.days.minOfOrNull { it.min } ?: 0.0
                val hi = f.days.maxOfOrNull { it.max } ?: 1.0
                f.days.forEachIndexed { k, d -> DayRow(vm, d, k, lo, hi, cur.temp) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- детали ----
        val d0 = f.days.firstOrNull()
        val tiles = listOf(
            Triple("ОЩУЩАЕТСЯ", vm.t(cur.feels), feelsText(cur.feels - cur.temp)),
            Triple("ВЛАЖНОСТЬ", "${cur.humidity}%", if (cur.humidity > 80) "Очень влажно" else if (cur.humidity < 30) "Сухо" else "Комфортно"),
            Triple("ВЕТЕР", "${cur.wind.roundToInt()} м/с", "${windDir(cur.windDir)} · порывы ${cur.gusts.roundToInt()} м/с"),
            Triple("ДАВЛЕНИЕ", "${(cur.pressure * 0.750062).roundToInt()}", "мм рт. ст."),
            Triple("УФ-ИНДЕКС", d0?.uv?.let { String.format(RU, "%.0f", it) } ?: "—", uvText(d0?.uv ?: 0.0)),
            Triple("ОБЛАЧНОСТЬ", "${cur.clouds}%", if (d0 != null && d0.pop > 0) "Осадки ${d0.pop}% · ${String.format(RU, "%.1f", d0.rain)} мм" else "Без осадков"),
            Triple("ВОСХОД", d0?.sunrise?.substringAfter('T')?.take(5) ?: "—", "Закат ${d0?.sunset?.substringAfter('T')?.take(5) ?: "—"}"),
            Triple("СВЕТОВОЙ ДЕНЬ", d0?.let { daylight(it) } ?: "—", "")
        )
        tiles.chunked(2).forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((title, value, sub) in row) {
                    Glass(Modifier.weight(1f).height(118.dp).appear(5 + ri, c.id), depth = 1.4f, padding = 14.dp) {
                        Column {
                            T(title, 11.sp, P.dim, weight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(value, 26.sp, display = true)
                                if (title == "ВЕТЕР") { Spacer(Modifier.width(8.dp)); WindArrow(cur.windDir) }
                                if (title == "УФ-ИНДЕКС") { Spacer(Modifier.width(8.dp)); UvDot(d0?.uv ?: 0.0) }
                            }
                            Spacer(Modifier.height(4.dp))
                            T(sub, 12.sp, P.lilac, maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        T("Данные: Open-Meteo.com (CC BY 4.0)", 10.sp, P.dim.copy(alpha = 0.7f))
        Spacer(Modifier.height(28.dp))
    }
}

private fun skyName(s: Sky) = when (s) {
    Sky.CLEAR -> "ясно ☀️"; Sky.PARTLY -> "облачно с прояснениями ⛅"; Sky.CLOUDY -> "пасмурно ☁️"; Sky.FOG -> "туман 🌫️"
    Sky.DRIZZLE -> "морось 🌦️"; Sky.RAIN -> "дождь 🌧️"; Sky.SNOW -> "снег ❄️"; Sky.STORM -> "гроза ⚡"
}

/** Часы выбранного дня (для сегодня — начиная с текущего часа). */
fun hoursFor(f: Forecast, day: Int): List<com.purpleweather.app.data.Hour> {
    if (day == 0) return f.hours
    val date = f.days.getOrNull(day)?.date ?: return f.hours
    return f.allHours.filter { it.time.startsWith(date) }
}

@Composable
private fun DayChips(vm: WeatherViewModel, f: Forecast) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(f.days.indices.toList()) { i ->
            val sel = i == vm.selectedDay
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) Brush.horizontalGradient(listOf(P.orchid, P.pink)) else Brush.horizontalGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF))))
                    .clickable { vm.selectedDay = i }.padding(horizontal = 12.dp, vertical = 7.dp)
            ) { T(dayName(f.days[i].date, i, short = true), 13.sp, weight = if (sel) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

fun dayName(date: String, index: Int, short: Boolean = false): String {
    if (index == 0) return "Сегодня"
    if (index == 1) return "Завтра"
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val wd = d.dayOfWeek.getDisplayName(if (short) JTextStyle.SHORT else JTextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
    return "$wd ${d.dayOfMonth}"
}

@Composable
private fun UvDot(uv: Double) {
    val c = when { uv < 3 -> Color(0xFF6BE58C); uv < 6 -> Color(0xFFFFD84A); uv < 8 -> Color(0xFFFF9F43); else -> Color(0xFFFF5E7A) }
    Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, c))))
}

@Composable
private fun DayRow(vm: WeatherViewModel, d: Day, index: Int, lo: Double, hi: Double, now: Double) {
    val today = index == 0
    val sel = index == vm.selectedDay
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (sel) Color(0x1AFFFFFF) else Color.Transparent)
            .clickable { vm.openDay(index) }.padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(dayName(d.date, index, short = true), 15.sp, modifier = Modifier.width(78.dp), weight = if (today) FontWeight.Bold else FontWeight.Normal)
        WeatherIcon3D(Wmo.sky(d.code), true, Modifier.size(36.dp), animated = false)
        T(if (d.pop >= 20) "${d.pop}%" else "", 11.sp, Color(0xFF9FD8FF), modifier = Modifier.width(34.dp).padding(start = 4.dp))
        T(vm.t(d.min), 15.sp, P.dim, modifier = Modifier.width(40.dp))
        Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 4.dp)) {
            val span = (hi - lo).coerceAtLeast(1.0)
            val x0 = ((d.min - lo) / span * size.width).toFloat()
            val x1 = ((d.max - lo) / span * size.width).toFloat()
            drawLine(Color(0x22FFFFFF), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(Color(0xFF7FB8FF), P.orchid, P.pink, P.sun2), startX = 0f, endX = size.width),
                Offset(x0, size.height / 2), Offset(x1.coerceAtLeast(x0 + 1), size.height / 2), size.height, StrokeCap.Round)
            if (today) {
                val xn = ((now - lo) / span * size.width).toFloat().coerceIn(0f, size.width)
                drawCircle(Color.White, size.height * 0.75f, Offset(xn, size.height / 2))
            }
        }
        T(vm.t(d.max), 15.sp, weight = FontWeight.Bold, modifier = Modifier.width(40.dp).padding(start = 4.dp))
    }
}

@Composable
private fun WindArrow(deg: Int) {
    Canvas(Modifier.size(26.dp).rotate(deg + 180f)) {
        val w = size.width
        val p = Path().apply {
            moveTo(w / 2, 0f); lineTo(w * 0.85f, w * 0.9f); lineTo(w / 2, w * 0.7f); lineTo(w * 0.15f, w * 0.9f); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color.White, P.orchid)))
    }
}

private fun windDir(d: Int): String = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[((d + 22) % 360) / 45]
private fun uvText(u: Double) = when { u < 3 -> "Низкий"; u < 6 -> "Умеренный"; u < 8 -> "Высокий"; u < 11 -> "Очень высокий"; else -> "Экстремальный" }
private fun feelsText(diff: Double) = when { diff < -3 -> "Холоднее из-за ветра"; diff > 3 -> "Теплее из-за влажности"; else -> "Как на градуснике" }
private fun daylight(d: Day): String = runCatching {
    val a = LocalDateTime.parse(d.sunrise); val b = LocalDateTime.parse(d.sunset)
    val m = java.time.Duration.between(a, b).toMinutes()
    "${m / 60} ч ${m % 60} мин"
}.getOrDefault("—")

// ---------------- поиск и список городов ----------------

@Composable
private fun SearchScreen(vm: WeatherViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) vm.locate() else vm.message = "Без доступа к геопозиции не определить место"
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("←") { vm.screen = Screen.MAIN }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0x26FFFFFF))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                if (vm.query.isEmpty()) T("Город в любой точке мира", 16.sp, P.dim)
                BasicTextField(
                    value = vm.query, onValueChange = { vm.onQuery(it) }, singleLine = true,
                    textStyle = TextStyle(color = P.text, fontSize = 16.sp, fontFamily = Body),
                    cursorBrush = SolidColor(P.pink), modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.submit() })
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Pill("➤  Моё местоположение", primary = false) {
            val perms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            if (perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) vm.locate()
            else launcher.launch(perms)
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.query.trim().length >= 2) {
                if (vm.searching) item { T("Ищу…", 14.sp, P.dim) }
                else if (vm.results.isEmpty()) item { T("Ничего не найдено", 14.sp, P.dim) }
                items(vm.results, key = { "r" + it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x1AFFFFFF))
                            .clickable { vm.addCity(c) }.padding(14.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(c.name, 17.sp, weight = FontWeight.Bold)
                            T(c.region, 12.sp, P.dim)
                        }
                        T("＋", 20.sp, P.pink)
                    }
                }
            } else {
                item { T("МОИ ГОРОДА", 12.sp, P.dim, weight = FontWeight.Bold) }
                items(vm.cities, key = { "c" + it.id }) { c -> SavedCityRow(vm, c) }
            }
        }
    }
}

@Composable
private fun SavedCityRow(vm: WeatherViewModel, c: City) {
    val f = vm.forecasts[c.id]
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0x335B2BC4), Color(0x1AFFFFFF))))
            .clickable { vm.page = vm.cities.indexOf(c).coerceAtLeast(0); vm.screen = Screen.MAIN }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (f != null) WeatherIcon3D(Wmo.sky(f.current.code), f.current.isDay, Modifier.size(44.dp), animated = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T((if (c.isMyLocation) "➤ " else "") + c.name, 16.sp, weight = FontWeight.Bold)
            T(f?.let { Wmo.text(it.current.code) } ?: c.region, 12.sp, P.dim)
        }
        if (f != null) T(vm.t(f.current.temp), 24.sp, display = true)
        Spacer(Modifier.width(8.dp))
        Column {
            T("▲", 14.sp, P.dim, modifier = Modifier.clickable { vm.moveCity(c, -1) }.padding(4.dp))
            T("▼", 14.sp, P.dim, modifier = Modifier.clickable { vm.moveCity(c, 1) }.padding(4.dp))
        }
        T("✕", 16.sp, P.pink, modifier = Modifier.clickable { vm.removeCity(c) }.padding(8.dp))
    }
}

// ---------------- настройки ----------------

@Composable
private fun SettingsScreen(vm: WeatherViewModel) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("←") { vm.screen = Screen.MAIN }
            Spacer(Modifier.width(12.dp))
            T("Настройки", 24.sp, display = true)
        }
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column {
                T("ЦВЕТОВАЯ ТЕМА", 12.sp, P.dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                for (row in Palettes.all.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (pal in row) {
                            val sel = !vm.randomTheme && vm.themeId == pal.id
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(pal.top, pal.mid, pal.bottom)))
                                    .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                                    .clickable { vm.changeRandomTheme(false); vm.chooseTheme(pal.id) }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (col in listOf(pal.accent, pal.accent2, pal.blob3)) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, col))))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                T(pal.name, 12.sp, weight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Toggle("Новая тема при каждом запуске", vm.randomTheme, "каждый раз приложение встречает другим цветом") { vm.changeRandomTheme(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column {
                T("ПРОЗРАЧНОСТЬ ПАНЕЛЕЙ · ${(100 - vm.panelAlpha * 100).roundToInt()}%", 12.sp, P.dim, weight = FontWeight.Bold)
                Slider(
                    value = vm.panelAlpha, onValueChange = { vm.changePanelAlpha(it) }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = P.pink, activeTrackColor = P.orchid, inactiveTrackColor = Color(0x33FFFFFF))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    T("прозрачные", 11.sp, P.dim); T("плотные", 11.sp, P.dim)
                }
                Spacer(Modifier.height(8.dp))
                Toggle("Падающие эмодзи и снег", vm.fallingFx, "тыквы, сердечки, снежинки на фоне") { vm.changeFallingFx(it) }
                Toggle("Котик при бездействии", vm.idleCat, "если ничего не трогать 15 секунд, приходит котик и прыгает по панелям") { vm.changeIdleCat(it) }
                Toggle("Градусы Фаренгейта", vm.fahrenheit, "°F вместо °C") { vm.changeFahrenheit(it) }
                Toggle("3D-наклон", vm.tilt, "карточки и иконки поворачиваются вслед за телефоном") { vm.setTiltOn(it) }
                Toggle("Проверять обновления", vm.autoUpdate, "при запуске смотреть новый релиз на GitHub") { vm.setAutoUpdateOn(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column {
                T("Версия 1.${Updater.currentRun}", 16.sp, weight = FontWeight.Bold)
                val st = when (vm.updateState) {
                    UpdateState.CHECKING -> "Проверяю…"
                    UpdateState.AVAILABLE -> "Доступна ${vm.update?.name ?: "новая версия"}"
                    UpdateState.DOWNLOADING -> "Загрузка ${(vm.updateProgress * 100).roundToInt()}%"
                    UpdateState.READY -> "Готово к установке"
                    UpdateState.NONE -> "Последняя версия"
                    else -> "Обновления из релизов ${Updater.repo}"
                }
                T(st, 13.sp, P.dim)
                Spacer(Modifier.height(10.dp))
                Pill(
                    when (vm.updateState) {
                        UpdateState.AVAILABLE -> "Скачать и установить"
                        UpdateState.READY -> "Установить"
                        else -> "Проверить обновления"
                    }
                ) {
                    when (vm.updateState) {
                        UpdateState.AVAILABLE -> vm.downloadUpdate()
                        UpdateState.READY -> vm.installUpdate()
                        else -> vm.checkUpdate()
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Pill("Обновить погоду во всех городах", primary = false) { vm.refreshAll() }
        Spacer(Modifier.height(14.dp))
        T("Прогноз — Open-Meteo.com (CC BY 4.0), поиск мест — Open-Meteo Geocoding и OpenStreetMap. Без рекламы и аккаунтов. " +
            "Последний прогноз сохраняется и доступен без интернета.", 12.sp, P.dim, maxLines = 6)
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, hint: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 16.sp)
            T(hint, 12.sp, P.dim, maxLines = 2)
        }
        Box(
            Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape)
                .background(if (value) Brush.horizontalGradient(listOf(P.orchid, P.pink)) else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) { Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(Color.White)) }
    }
}

// плашка обновления на главном экране
@Composable
fun BoxScope.UpdateBanner(vm: WeatherViewModel) {
    if (vm.updateState != UpdateState.AVAILABLE) return
    Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 64.dp, start = 16.dp, end = 16.dp)) {
        Glass(Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Новая версия ${vm.update?.name ?: ""}", 14.sp, modifier = Modifier.weight(1f))
                T("Позже", 13.sp, P.dim, modifier = Modifier.clickable { vm.dismissUpdate() }.padding(8.dp))
                Pill("Обновить") { vm.downloadUpdate() }
            }
        }
    }
}


// ---------------- экран дня: по часам ----------------
@Composable
private fun DayScreen(vm: WeatherViewModel, c: City, f: Forecast) {
    val idx = vm.selectedDay.coerceIn(0, maxOf(0, f.days.lastIndex))
    val d = f.days.getOrNull(idx)
    val hours = if (idx == 0) f.allHours.filter { it.time.startsWith(d?.date ?: "") }.ifEmpty { f.hours } else hoursFor(f, idx)
    val tilt = LocalTilt.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("← Назад", primary = false) { vm.screen = Screen.MAIN }
            Spacer(Modifier.width(12.dp))
            Column {
                T(c.name, 18.sp, display = true)
                T(d?.let { dayName(it.date, idx) } ?: "", 13.sp, P.lilac)
            }
        }
        DayChips(vm, f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (d == null) { T("Нет данных", 16.sp, P.dim); return@Column }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(0, idx)) {
                WeatherIcon3D(Wmo.sky(d.code), true, Modifier.size(150.dp), parallax = tilt, floor = true, cat = vm.cat)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text3D(vm.t(d.max), 56.sp)
                    T("мин ${vm.t(d.min)}", 16.sp, P.dim)
                    T(Wmo.text(d.code), 16.sp, weight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            // график температуры + осадки
            Glass(Modifier.fillMaxWidth().appear(1, idx), padding = 14.dp) {
                Column {
                    T("ТЕМПЕРАТУРА И ОСАДКИ", 12.sp, P.dim, weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val grow = remember(idx) { Animatable(0f) }
                    LaunchedEffect(idx) { grow.animateTo(1f, tween(1000)) }
                    val orchid = P.orchid; val pink = P.pink; val violet = P.violet
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        val temps = hours.map { it.temp }.filter { !it.isNaN() }
                        if (temps.size < 2) return@Canvas
                        val lo = temps.min() - 1; val hi = temps.max() + 1
                        val n = hours.size
                        val w = size.width; val h = size.height * 0.72f
                        fun x(i: Int) = if (n <= 1) 0f else i * w / (n - 1)
                        fun y(t: Double) = (h - ((t - lo) / (hi - lo) * h * 0.85f).toFloat() - h * 0.08f)
                        // столбики осадков
                        val bw = w / n * 0.6f
                        hours.forEachIndexed { i, hr ->
                            val ph = (hr.pop / 100f) * size.height * 0.26f * grow.value
                            if (ph > 0f) drawRoundRect(
                                Brush.verticalGradient(listOf(Color(0xFF9FD8FF), Color(0x559FD8FF))),
                                topLeft = Offset(x(i) - bw / 2, size.height - ph), size = androidx.compose.ui.geometry.Size(bw, ph),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 3)
                            )
                        }
                        val line = Path(); val fill = Path()
                        var first = true
                        hours.forEachIndexed { i, hr ->
                            if (hr.temp.isNaN()) return@forEachIndexed
                            val px = x(i); val py = h - (h - y(hr.temp)) * grow.value
                            if (first) { line.moveTo(px, py); fill.moveTo(px, h); fill.lineTo(px, py); first = false }
                            else { line.lineTo(px, py); fill.lineTo(px, py) }
                        }
                        fill.lineTo(x(n - 1), h); fill.close()
                        drawPath(fill, Brush.verticalGradient(listOf(pink.copy(alpha = 0.45f), violet.copy(alpha = 0.05f)), 0f, h))
                        drawPath(line, Brush.horizontalGradient(listOf(Color(0xFF7FB8FF), orchid, pink)),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        // точки макс/мин
                        val iMax = hours.indices.maxByOrNull { hours[it].temp.takeIf { t -> !t.isNaN() } ?: -999.0 } ?: 0
                        val iMin = hours.indices.minByOrNull { hours[it].temp.takeIf { t -> !t.isNaN() } ?: 999.0 } ?: 0
                        for (k in listOf(iMax, iMin)) {
                            val py = h - (h - y(hours[k].temp)) * grow.value
                            drawCircle(Color.White, 5.dp.toPx(), Offset(x(k), py))
                            drawCircle(pink, 3.dp.toPx(), Offset(x(k), py))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (k in listOf(0, hours.size / 2, hours.lastIndex)) {
                            T(hours.getOrNull(k)?.time?.substringAfter('T')?.take(5) ?: "", 11.sp, P.dim)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth().appear(2, idx), padding = 12.dp) {
                Column {
                    T("ПО ЧАСАМ", 12.sp, P.dim, weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    hours.forEachIndexed { k, h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(h.time.substringAfter('T').take(5), 14.sp, P.dim, modifier = Modifier.width(52.dp))
                            WeatherIcon3D(Wmo.sky(h.code), h.isDay, Modifier.size(34.dp), animated = false)
                            T(vm.t(h.temp), 16.sp, weight = FontWeight.Bold, modifier = Modifier.width(52.dp).padding(start = 6.dp))
                            T(if (h.pop > 0) "💧${h.pop}%" else "", 12.sp, Color(0xFF9FD8FF), modifier = Modifier.width(58.dp))
                            T("💨${h.wind.roundToInt()} м/с", 12.sp, P.lilac, modifier = Modifier.width(70.dp))
                            T("${h.humidity}%", 12.sp, P.dim)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((title, value) in listOf(
                    "ВОСХОД" to d.sunrise.substringAfter('T').take(5),
                    "ЗАКАТ" to d.sunset.substringAfter('T').take(5),
                    "УФ" to String.format(RU, "%.0f", d.uv)
                )) {
                    Glass(Modifier.weight(1f).appear(3, idx), depth = 1.2f, padding = 12.dp) {
                        Column { T(title, 11.sp, P.dim, weight = FontWeight.Bold); T(value, 20.sp, display = true) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            T("Осадки за день: ${String.format(RU, "%.1f", d.rain)} мм · ветер до ${d.wind.roundToInt()} м/с", 13.sp, P.lilac, maxLines = 2)
            Spacer(Modifier.height(28.dp))
        }
    }
}
