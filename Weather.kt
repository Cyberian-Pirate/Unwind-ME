package com.unwind.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// Current weather for Seri Kembangan from Open-Meteo (no key), in the
// same cache-first style as Prayer.kt: the screensaver shows the cached
// reading while a fresh one is fetched, and never goes empty on a bad
// network day. Refreshed when the cache is older than 30 minutes.
private const val WEATHER_CACHE = "weather.json"
private const val WEATHER_TTL_MS = 30L * 60L * 1000L
private const val WEATHER_URL =
    "https://api.open-meteo.com/v1/forecast?latitude=3.02&longitude=101.70" +
        "&current=temperature_2m,weather_code&timezone=Asia%2FSingapore"

enum class WeatherIcon { SUN, CLOUD, RAIN, SNOW, THUNDER }

class WeatherNow(
    val tempC: Double,
    val code: Int,
) {
    val degrees: String get() = "${tempC.roundToInt()}\u00b0"
    val text: String get() = wmoText(code)
    val icon: WeatherIcon get() = wmoIcon(code)
}

fun wmoText(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80 -> "Light showers"
    81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Cloudy"
}

fun wmoIcon(code: Int): WeatherIcon = when (code) {
    0, 1 -> WeatherIcon.SUN
    2, 3, 45, 48 -> WeatherIcon.CLOUD
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherIcon.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherIcon.SNOW
    95, 96, 99 -> WeatherIcon.THUNDER
    else -> WeatherIcon.CLOUD
}

private fun parseWeather(json: String): WeatherNow? {
    return try {
        val cur = JSONObject(json).getJSONObject("current")
        WeatherNow(cur.getDouble("temperature_2m"), cur.getInt("weather_code"))
    } catch (e: Exception) {
        null
    }
}

private fun fetchWeatherJson(): String? {
    return try {
        val c = URL(WEATHER_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "UnwindLauncher/2.2")
        if (c.responseCode != HttpURLConnection.HTTP_OK) return null
        c.inputStream.bufferedReader().readText().also { c.disconnect() }
    } catch (e: Exception) {
        null
    }
}

fun loadWeather(ctx: Context): WeatherNow? {
    val file = File(ctx.filesDir, WEATHER_CACHE)
    var now: WeatherNow? = null
    if (file.exists()) {
        now = try {
            parseWeather(file.readText())
        } catch (e: Exception) {
            null
        }
    }
    val stale = now == null || System.currentTimeMillis() - file.lastModified() >= WEATHER_TTL_MS
    if (stale) {
        val fresh = fetchWeatherJson()
        if (fresh != null) {
            try {
                file.writeText(fresh)
            } catch (e: Exception) {
                // keep the old cache
            }
            parseWeather(fresh)?.let { now = it }
        }
    }
    return now
}
