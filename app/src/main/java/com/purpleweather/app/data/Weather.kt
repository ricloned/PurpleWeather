package com.purpleweather.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Город в списке. id = "lat,lon" для геопозиции. */
data class City(
    val id: String,
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val isMyLocation: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("region", region)
        .put("lat", lat).put("lon", lon).put("my", isMyLocation)

    companion object {
        fun fromJson(o: JSONObject) = City(
            o.optString("id"), o.optString("name"), o.optString("region"),
            o.optDouble("lat"), o.optDouble("lon"), o.optBoolean("my")
        )
    }
}

/** Тип погоды для иконки и фона. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class Current(
    val temp: Double, val feels: Double, val humidity: Int, val code: Int, val isDay: Boolean,
    val wind: Double, val windDir: Int, val gusts: Double, val pressure: Double, val clouds: Int, val precip: Double
)

data class Hour(
    val time: String, val temp: Double, val code: Int, val isDay: Boolean, val pop: Int,
    val precip: Double = 0.0, val wind: Double = 0.0, val humidity: Int = 0, val feels: Double = Double.NaN
)

data class Day(
    val date: String, val code: Int, val max: Double, val min: Double,
    val sunrise: String, val sunset: String, val uv: Double, val pop: Int, val rain: Double, val wind: Double
)

data class Forecast(
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    /** Все часы на 10 дней — для экрана дня. */
    val allHours: List<Hour>,
    val timezone: String,
    val utcOffset: Int,
    val fetchedAt: Long
)

object Wmo {
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun text(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45 -> "Туман"
        48 -> "Изморозь и туман"
        51 -> "Лёгкая морось"
        53 -> "Морось"
        55 -> "Сильная морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежная крупа"
        80 -> "Кратковременный дождь"
        81 -> "Ливень"
        82 -> "Сильный ливень"
        85 -> "Снегопад"
        86 -> "Сильный снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "—"
    }
}

/** Open-Meteo: прогноз и поиск городов. Бесплатно, без ключа. Вызовы блокирующие. */
object WeatherApi {

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "PurpleWeather/1.0 (Android)")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("сервер ответил $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun f(d: Double) = String.format(Locale.US, "%.4f", d)

    fun search(q: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=12&language=ru&format=json&name=" +
            URLEncoder.encode(q.trim(), "UTF-8")
        val arr = JSONObject(http(url)).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val region = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            City(o.optLong("id").toString(), o.optString("name"), region, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** Название места по координатам (Photon/OSM). */
    fun placeName(lat: Double, lon: Double): String? = runCatching {
        val o = JSONObject(http("https://photon.komoot.io/reverse?lat=${f(lat)}&lon=${f(lon)}&limit=1&lang=default"))
        val p = o.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties") ?: return null
        p.optString("city").ifBlank { p.optString("town") }.ifBlank { p.optString("district") }.ifBlank { p.optString("name") }
            .ifBlank { null }
    }.getOrNull()

    fun forecastJson(lat: Double, lon: Double): String {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${f(lat)}&longitude=${f(lon)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
            "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day,precipitation,wind_speed_10m,relative_humidity_2m,apparent_temperature" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max," +
            "precipitation_probability_max,precipitation_sum,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=10&wind_speed_unit=ms"
        return http(url)
    }

    fun parse(json: String, fetchedAt: Long): Forecast {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val cur = Current(
            temp = c.optDouble("temperature_2m"),
            feels = c.optDouble("apparent_temperature"),
            humidity = c.optInt("relative_humidity_2m"),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            wind = c.optDouble("wind_speed_10m"),
            windDir = c.optInt("wind_direction_10m"),
            gusts = c.optDouble("wind_gusts_10m"),
            pressure = c.optDouble("pressure_msl"),
            clouds = c.optInt("cloud_cover"),
            precip = c.optDouble("precipitation")
        )
        val curTime = c.optString("time")
        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        fun ha(k: String): JSONArray? = h.optJSONArray(k)
        val all = (0 until ht.length()).map { i ->
            Hour(
                ht.getString(i), ha("temperature_2m")?.optDouble(i) ?: Double.NaN, ha("weather_code")?.optInt(i) ?: 0,
                (ha("is_day")?.optInt(i, 1) ?: 1) == 1, ha("precipitation_probability")?.optInt(i) ?: 0,
                ha("precipitation")?.optDouble(i, 0.0) ?: 0.0, ha("wind_speed_10m")?.optDouble(i, 0.0) ?: 0.0,
                ha("relative_humidity_2m")?.optInt(i) ?: 0, ha("apparent_temperature")?.optDouble(i) ?: Double.NaN
            )
        }
        var start = 0
        for (i in all.indices) if (all[i].time <= curTime) start = i
        val hours = all.subList(start, minOf(all.size, start + 25)).toList()
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        fun arr(k: String): JSONArray = d.getJSONArray(k)
        val days = (0 until dt.length()).map { i ->
            Day(
                dt.getString(i), arr("weather_code").optInt(i), arr("temperature_2m_max").optDouble(i),
                arr("temperature_2m_min").optDouble(i), arr("sunrise").optString(i), arr("sunset").optString(i),
                arr("uv_index_max").optDouble(i), arr("precipitation_probability_max").optInt(i),
                arr("precipitation_sum").optDouble(i), arr("wind_speed_10m_max").optDouble(i)
            )
        }
        return Forecast(cur, hours, days, all, o.optString("timezone"), o.optInt("utc_offset_seconds"), fetchedAt)
    }
}
