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
    val tilt = rememberTilt(vm.tilt)
    CompositionLocalProvider(LocalTilt provides tilt) {
        val cur = vm.cities.getOrNull(vm.page)
        val f = cur?.let { vm.forecasts[it.id] }
        Box(Modifier.fillMaxSize().background(P.night0)) {
            Backdrop(f?.let { Wmo.sky(it.current.code) } ?: Sky.CLOUDY, f?.current?.isDay ?: true)
            when (vm.screen) {
                Screen.MAIN -> MainScreen(vm)
                Screen.SEARCH -> SearchScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
            if (vm.screen == Screen.MAIN) UpdateBanner(vm)
            val msg = vm.message
            LaunchedEffect(msg) {
                if (msg != null) { delay(2600); if (vm.message == msg) vm.message = null }
            }
            if (msg != null) {
                Box(
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xE62A0E61)).padding(horizontal = 18.dp, vertical = 12.dp)
                ) { T(msg, 14.sp, maxLines = 3) }
            }
        }
    }
    BackHandler(enabled = vm.screen != Screen.MAIN) { vm.screen = Screen.MAIN }
}

// ---------------- фон ----------------

/** Глубокий фиолетовый фон с «туманностями», звёздами ночью и бликами. */
@Composable
private fun Backdrop(sky: Sky, day: Boolean) {
    val tr = rememberInfiniteTransition(label = "bg")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val tw by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "tw")
    val tilt = LocalTilt.current
    val stars = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat() * 0.6f, Random.nextFloat()) } }
    val top = when {
        !day -> P.night0
        sky == Sky.CLEAR || sky == Sky.PARTLY -> Color(0xFF3A1680)
        sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM -> Color(0xFF1C1350)
        else -> Color(0xFF2A1866)
    }
    val bottom = if (day) Color(0xFF12052E) else Color(0xFF05010F)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(top, P.night1, bottom)))
        val t = tilt.value
        val px = t.x * size.width * 0.03f
        val py = t.y * size.height * 0.02f
        fun blob(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(size.width * (0.15f + 0.2f * a) + px, size.height * 0.18f + py, size.width * 0.7f, P.orchid.copy(alpha = 0.35f))
        blob(size.width * (0.9f - 0.15f * a) + px * 1.5f, size.height * 0.45f, size.width * 0.6f, P.pink.copy(alpha = 0.18f))
        blob(size.width * 0.5f - px, size.height * (0.85f - 0.05f * a), size.width * 0.8f, P.violet.copy(alpha = 0.35f))
        if (!day) for ((x, y, b) in stars) {
            drawCircle(Color.White.copy(alpha = (0.3f + 0.7f * b) * (if (b > 0.5f) tw else 1f)), 1.2f + 1.6f * b,
                Offset(x * size.width + px * 0.5f, y * size.height + py * 0.5f))
        }
    }
}

// ---------------- главный экран ----------------

@Composable
private fun MainScreen(vm: WeatherViewModel) {
    val pager = rememberPagerState(initialPage = vm.page.coerceIn(0, maxOf(0, vm.cities.lastIndex))) { vm.cities.size }
    LaunchedEffect(vm.page, vm.cities.size) {
        if (vm.page in vm.cities.indices && pager.currentPage != vm.page) pager.scrollToPage(vm.page)
    }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.page = it } }
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
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                val c = vm.cities[i]
                CityPage(vm, c, vm.forecasts[c.id])
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        // ---- герой: 3D-иконка и температура ----
        WeatherIcon3D(
            sky, cur.isDay,
            Modifier.size(230.dp).graphicsLayer {
                val t = tilt.value
                rotationY = t.x * 14f; rotationX = -t.y * 10f; cameraDistance = 12f * density
            },
            parallax = tilt.value
        )
        Text3D(vm.t(cur.temp), 96.sp, Modifier.padding(start = 18.dp))
        T(Wmo.text(cur.code), 20.sp, weight = FontWeight.Bold)
        val today = f.days.firstOrNull()
        if (today != null) {
            T("Макс ${vm.t(today.max)} · Мин ${vm.t(today.min)} · Ощущается ${vm.t(cur.feels)}", 13.sp, P.dim)
        }
        Spacer(Modifier.height(6.dp))
        val age = (System.currentTimeMillis() - f.fetchedAt) / 60_000
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(if (age < 1) "обновлено только что" else "обновлено $age мин назад", 11.sp, P.dim.copy(alpha = 0.8f))
            Spacer(Modifier.width(8.dp))
            T(if (vm.loadingIds[c.id] == true) "⟳ …" else "⟳ обновить", 11.sp, P.lilac,
                modifier = Modifier.clickable { vm.refresh(c, force = true) })
        }
        Spacer(Modifier.height(18.dp))

        // ---- по часам ----
        Glass(Modifier.fillMaxWidth(), padding = 12.dp) {
            Column {
                T("ПО ЧАСАМ", 12.sp, P.dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(f.hours.withIndex().toList()) { (k, h) ->
                        val hour = h.time.substringAfter('T').take(5)
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (k == 0) Brush.verticalGradient(listOf(P.orchid.copy(alpha = 0.55f), P.violet.copy(alpha = 0.25f))) else Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            T(if (k == 0) "Сейчас" else hour, 12.sp, P.dim)
                            WeatherIcon3D(Wmo.sky(h.code), h.isDay, Modifier.size(42.dp), animated = false)
                            T(vm.t(h.temp), 16.sp, weight = FontWeight.Bold)
                            T(if (h.pop >= 10) "💧${h.pop}%" else " ", 10.sp, Color(0xFF9FD8FF))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- 10 дней ----
        Glass(Modifier.fillMaxWidth(), padding = 14.dp) {
            Column {
                T("НА 10 ДНЕЙ", 12.sp, P.dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val lo = f.days.minOfOrNull { it.min } ?: 0.0
                val hi = f.days.maxOfOrNull { it.max } ?: 1.0
                f.days.forEachIndexed { k, d -> DayRow(vm, d, k == 0, lo, hi, cur.temp) }
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
        for (row in tiles.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((title, value, sub) in row) {
                    Glass(Modifier.weight(1f).height(118.dp), depth = 1.4f, padding = 14.dp) {
                        Column {
                            T(title, 11.sp, P.dim, weight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(value, 26.sp, display = true)
                                if (title == "ВЕТЕР") { Spacer(Modifier.width(8.dp)); WindArrow(cur.windDir) }
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

@Composable
private fun DayRow(vm: WeatherViewModel, d: Day, today: Boolean, lo: Double, hi: Double, now: Double) {
    val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
    val name = when {
        today -> "Сегодня"
        date == null -> d.date
        else -> date.dayOfWeek.getDisplayName(JTextStyle.SHORT, RU).replaceFirstChar { it.uppercase() } + " " + date.dayOfMonth
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(name, 15.sp, modifier = Modifier.width(78.dp), weight = if (today) FontWeight.Bold else FontWeight.Normal)
        WeatherIcon3D(Wmo.sky(d.code), true, Modifier.size(34.dp), animated = false)
        T(if (d.pop >= 20) "${d.pop}%" else "", 11.sp, Color(0xFF9FD8FF), modifier = Modifier.width(34.dp).padding(start = 4.dp))
        T(vm.t(d.min), 15.sp, P.dim, modifier = Modifier.width(40.dp))
        // полоска диапазона температур, общая шкала для всех дней
        Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 4.dp)) {
            val span = (hi - lo).coerceAtLeast(1.0)
            val x0 = ((d.min - lo) / span * size.width).toFloat()
            val x1 = ((d.max - lo) / span * size.width).toFloat()
            drawLine(Color(0x22FFFFFF), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(Color(0xFF7FB8FF), P.orchid, P.sun2), startX = 0f, endX = size.width),
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
                    cursorBrush = SolidColor(P.pink), modifier = Modifier.fillMaxWidth().focusRequester(fr)
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
                Toggle("Градусы Фаренгейта", vm.fahrenheit, "°F вместо °C") { vm.setFahrenheit(it) }
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
