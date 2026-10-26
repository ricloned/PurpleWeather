package com.purpleweather.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purpleweather.app.data.City
import com.purpleweather.app.data.Forecast
import com.purpleweather.app.data.WeatherApi
import com.purpleweather.app.update.Release
import com.purpleweather.app.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Locale

enum class Screen { MAIN, SEARCH, SETTINGS, DAY }
enum class UpdateState { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, NONE, ERROR }

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val sp = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val cacheDir = File(app.filesDir, "forecasts").apply { mkdirs() }

    var screen by mutableStateOf(Screen.MAIN)
    var cities by mutableStateOf(loadCities())
        private set
    var page by mutableIntStateOf(0)
    val forecasts = mutableStateMapOf<String, Forecast>()
    val loadingIds = mutableStateMapOf<String, Boolean>()
    var message by mutableStateOf<String?>(null)

    var fahrenheit by mutableStateOf(sp.getBoolean("fahrenheit", false))
        private set
    var tilt by mutableStateOf(sp.getBoolean("tilt", true))
        private set
    var autoUpdate by mutableStateOf(sp.getBoolean("autoUpdate", true))
        private set

    // ---------------- темы и сюрпризы ----------------

    var themeId by mutableStateOf(sp.getString("theme", "violet") ?: "violet")
        private set
    var randomTheme by mutableStateOf(sp.getBoolean("randomTheme", true))
        private set
    /** Случайное «зерно» запуска: фон, приветствие и фраза дня каждый раз другие. */
    val seed = kotlin.random.Random.nextInt()
    /** Выбранный день (индекс в прогнозе) для почасового прогноза и экрана дня. */
    var selectedDay by mutableIntStateOf(0)
    var disco by mutableStateOf(false)
    var snowFx by mutableStateOf(false)
    var cat by mutableStateOf(false)
    var confetti by mutableIntStateOf(0)
        private set
    var tempTaps = 0

    fun applyTheme() {
        val id = if (randomTheme) {
            val pool = com.purpleweather.app.ui.Palettes.all.map { it.id }
            // каждый запуск — новая тема (но не та же, что в прошлый раз)
            val last = sp.getString("lastTheme", null)
            val pick = pool.filter { it != last }.random(kotlin.random.Random(seed))
            sp.edit().putString("lastTheme", pick).apply()
            pick
        } else themeId
        com.purpleweather.app.ui.Pal.state.value = com.purpleweather.app.ui.Palettes.byId(id)
    }

    fun chooseTheme(id: String) {
        themeId = id
        sp.edit().putString("theme", id).apply()
        com.purpleweather.app.ui.Pal.state.value = com.purpleweather.app.ui.Palettes.byId(id)
    }

    fun changeRandomTheme(v: Boolean) {
        randomTheme = v
        sp.edit().putBoolean("randomTheme", v).apply()
        if (!v) chooseTheme(themeId)
    }

    fun boom() { confetti++ }

    fun onTempTap() {
        tempTaps++
        if (tempTaps >= 7) {
            tempTaps = 0
            disco = !disco
            message = if (disco) "🪩 Диско-режим включён!" else "Диско-режим выключен"
            if (disco) boom()
        }
    }

    fun openDay(i: Int) {
        selectedDay = i
        screen = Screen.DAY
    }

    /** Секретные слова в поиске. true — слово сработало. */
    private fun easterEgg(q: String): Boolean {
        val w = q.trim().lowercase()
        when (w) {
            "радуга", "rainbow" -> { com.purpleweather.app.ui.Pal.state.value = com.purpleweather.app.ui.Palettes.RAINBOW; message = "🌈 Радужная тема!" }
            "диско", "disco" -> { disco = true; boom(); message = "🪩 Диско!" }
            "снег", "snow", "let it snow" -> { snowFx = !snowFx; message = if (snowFx) "❄ Пусть идёт снег" else "Снег остановлен" }
            "котик", "кот", "cat" -> { cat = !cat; message = if (cat) "🐱 Котик теперь живёт на облаке" else "Котик ушёл гулять" }
            "42" -> message = "Ответ на главный вопрос жизни, вселенной и погоды: +42°. Но это не точно."
            "конфетти", "ура", "праздник" -> { boom(); message = "🎉" }
            else -> return false
        }
        screen = Screen.MAIN
        query = ""
        results = emptyList()
        return true
    }

    // поиск
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<City>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    // ---------------- города ----------------

    private fun loadCities(): List<City> = try {
        val arr = JSONArray(sp.getString("cities", null) ?: DEFAULT)
        (0 until arr.length()).map { City.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveCities() {
        val arr = JSONArray()
        cities.forEach { arr.put(it.toJson()) }
        sp.edit().putString("cities", arr.toString()).apply()
    }

    fun addCity(c: City) {
        val existing = cities.indexOfFirst { it.id == c.id || (kotlin.math.abs(it.lat - c.lat) < 0.01 && kotlin.math.abs(it.lon - c.lon) < 0.01) }
        if (existing >= 0) {
            page = existing
        } else {
            cities = cities + c
            saveCities()
            page = cities.lastIndex
            loadCached(c)
            refresh(c, force = true)
        }
        screen = Screen.MAIN
        query = ""
        results = emptyList()
    }

    fun removeCity(c: City) {
        cities = cities.filter { it.id != c.id }
        saveCities()
        forecasts.remove(c.id)
        File(cacheDir, file(c.id)).delete()
        page = page.coerceIn(0, maxOf(0, cities.lastIndex))
    }

    fun moveCity(c: City, delta: Int) {
        val i = cities.indexOfFirst { it.id == c.id }
        val j = i + delta
        if (i < 0 || j !in cities.indices) return
        val list = cities.toMutableList()
        list[i] = list[j].also { list[j] = list[i] }
        cities = list
        saveCities()
    }

    // ---------------- прогноз ----------------

    private fun file(id: String) = "f_" + id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json"

    private fun loadCached(c: City) {
        val f = File(cacheDir, file(c.id))
        if (!f.exists()) return
        runCatching { forecasts[c.id] = WeatherApi.parse(f.readText(), f.lastModified()) }
    }

    fun refresh(c: City, force: Boolean) {
        val f = forecasts[c.id]
        if (!force && f != null && System.currentTimeMillis() - f.fetchedAt < 20 * 60_000) return
        if (loadingIds[c.id] == true) return
        loadingIds[c.id] = true
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { WeatherApi.forecastJson(c.lat, c.lon) }
                val now = System.currentTimeMillis()
                forecasts[c.id] = withContext(Dispatchers.Default) { WeatherApi.parse(json, now) }
                withContext(Dispatchers.IO) { File(cacheDir, file(c.id)).writeText(json) }
            } catch (e: Exception) {
                if (forecasts[c.id] == null) message = "Нет связи — не удалось загрузить «${c.name}»"
                else if (force) message = "Нет связи — показан сохранённый прогноз"
            } finally {
                loadingIds.remove(c.id)
            }
        }
    }

    fun refreshAll() = cities.forEach { refresh(it, force = true) }

    // ---------------- поиск ----------------

    /** Кнопка «Найти» на клавиатуре: проверяем секретные слова. */
    fun submit() {
        if (easterEgg(query)) return
        onQuery(query)
    }

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.trim().length < 2) { results = emptyList(); searching = false; return }
        searchJob = viewModelScope.launch {
            delay(350)
            searching = true
            try {
                results = withContext(Dispatchers.IO) { WeatherApi.search(q) }
            } catch (e: Exception) {
                message = "Поиск не удался: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    // ---------------- геопозиция ----------------

    @SuppressLint("MissingPermission")
    fun locate() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000) { onLocation(last); return }
        message = "Определяю местоположение…"
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            if (last != null) onLocation(last) else message = "Включи геолокацию"
            return
        }
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { lm.removeUpdates(this) }
                onLocation(location)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        runCatching { lm.requestLocationUpdates(provider, 0L, 0f, l, Looper.getMainLooper()) }
    }

    private fun onLocation(l: Location) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { WeatherApi.placeName(l.latitude, l.longitude) } ?: "Моё место"
            val me = City("my", name, "Моё местоположение", l.latitude, l.longitude, isMyLocation = true)
            val without = cities.filter { !it.isMyLocation }
            cities = listOf(me) + without
            saveCities()
            page = 0
            screen = Screen.MAIN
            refresh(me, force = true)
        }
    }

    // ---------------- настройки ----------------

    fun changeFahrenheit(v: Boolean) { fahrenheit = v; sp.edit().putBoolean("fahrenheit", v).apply() }
    fun setTiltOn(v: Boolean) { tilt = v; sp.edit().putBoolean("tilt", v).apply() }
    fun setAutoUpdateOn(v: Boolean) { autoUpdate = v; sp.edit().putBoolean("autoUpdate", v).apply() }

    /** Плотность панелей: 0 — полностью прозрачные, 1 — почти непрозрачные. */
    var panelAlpha by mutableStateOf(sp.getFloat("panelAlpha", 0.5f))
        private set
    /** Падающие эмодзи и снег на фоне. */
    var fallingFx by mutableStateOf(sp.getBoolean("fallingFx", true))
        private set
    /** Котик, который приходит, если долго ничего не трогать. */
    var idleCat by mutableStateOf(sp.getBoolean("idleCat", true))
        private set

    fun changePanelAlpha(v: Float) {
        panelAlpha = v
        com.purpleweather.app.ui.GlassStyle.opacity.floatValue = v
        sp.edit().putFloat("panelAlpha", v).apply()
    }
    fun changeFallingFx(v: Boolean) { fallingFx = v; sp.edit().putBoolean("fallingFx", v).apply() }
    fun changeIdleCat(v: Boolean) { idleCat = v; sp.edit().putBoolean("idleCat", v).apply() }

    fun t(c: Double): String {
        if (c.isNaN()) return "—"
        val v = if (fahrenheit) c * 9 / 5 + 32 else c
        return String.format(Locale.US, "%d°", Math.round(v))
    }

    // ---------------- обновления ----------------

    var update by mutableStateOf<Release?>(null)
        private set
    var updateState by mutableStateOf(UpdateState.IDLE)
        private set
    var updateProgress by mutableFloatStateOf(0f)
        private set
    private var updateFile: File? = null

    fun checkUpdate(silent: Boolean = false) {
        if (updateState == UpdateState.CHECKING || updateState == UpdateState.DOWNLOADING) return
        viewModelScope.launch {
            updateState = UpdateState.CHECKING
            try {
                val r = withContext(Dispatchers.IO) { Updater.latest() }
                if (r != null && Updater.isNewer(r)) { update = r; updateState = UpdateState.AVAILABLE }
                else { updateState = UpdateState.NONE; if (!silent) message = "Установлена последняя версия" }
            } catch (e: Exception) {
                updateState = if (silent) UpdateState.IDLE else UpdateState.ERROR
                if (!silent) message = "Не удалось проверить: ${e.message}"
            }
        }
    }

    fun downloadUpdate() {
        val r = update ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            updateState = UpdateState.DOWNLOADING
            updateProgress = 0f
            try {
                updateFile = withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> updateProgress = p } }
                updateState = UpdateState.READY
                installUpdate()
            } catch (e: Exception) {
                updateState = UpdateState.AVAILABLE
                message = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    fun installUpdate() {
        val f = updateFile ?: return
        if (!Updater.install(getApplication(), f)) message = "Разреши установку из Purple Weather и нажми «Установить» ещё раз"
    }

    fun dismissUpdate() { updateState = UpdateState.IDLE }

    // init в конце: к этому моменту все поля уже созданы
    init {
        com.purpleweather.app.ui.GlassStyle.opacity.floatValue = panelAlpha
        applyTheme()
        cities.forEach { c -> loadCached(c); refresh(c, force = false) }
        if (autoUpdate) checkUpdate(silent = true)
    }

    companion object {
        private const val DEFAULT = """[{"id":"524901","name":"Москва","region":"Москва, Россия","lat":55.7522,"lon":37.6156}]"""
    }
}
