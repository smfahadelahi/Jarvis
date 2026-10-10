package com.jarvis.ai.core.services

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Weather telemetry via the Open-Meteo public API — no API key required.
 * Geocoding cache keeps repeated city lookups off the network.
 */
class WeatherService(private val context: Context) {

    data class Snapshot(
        val city: String,
        val tempC: Double,
        val weatherCode: Int,
        val windKph: Double,
        val precipitationProb: Int?,
        val maxC: Double?,
        val minC: Double?
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val geocodeCache = HashMap<String, Pair<Double, Double>>()
    private val geocodeMutex = Mutex()

    suspend fun current(city: String): Snapshot = withContext(Dispatchers.IO) {
        val (lat, lon) = geocode(city)
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"
        val json = fetchJson(url)
        val current = json.getJSONObject("current")
        val daily = json.optJSONObject("daily")
        Snapshot(
            city = city,
            tempC = current.getDouble("temperature_2m"),
            weatherCode = current.optInt("weather_code", 0),
            windKph = current.optDouble("wind_speed_10m", 0.0),
            precipitationProb = daily?.optJSONArray("precipitation_probability_max")?.optInt(0, -1)?.takeIf { it >= 0 },
            maxC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0),
            minC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)
        )
    }

    /** Best-effort city resolution from coarse location; null when unavailable. */
    fun locationCity(): String? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        last?.let { "${String.format("%.2f", it.latitude)},${String.format("%.2f", it.longitude)}" }
    } catch (_: Exception) {
        null
    }

    fun describe(s: Snapshot): String = buildString {
        append("${s.tempC.toInt()}°C, ${describeCode(s.weatherCode)}")
        s.maxC?.let { append(", high ${it.toInt()}°") }
        s.minC?.let { append(", low ${it.toInt()}°") }
        s.precipitationProb?.let { if (it > 20) append(", $it% chance of precipitation") }
        if (s.windKph > 25) append(", winds ${s.windKph.toInt()} km/h")
    }

    fun describeCode(code: Int): String = when (code) {
        0 -> "clear skies"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "light drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        85, 86 -> "snow showers"
        in 95..99 -> "thunderstorms"
        else -> "unsettled conditions"
    }

    private suspend fun geocode(city: String): Pair<Double, Double> {
        geocodeMutex.withLock {
            geocodeCache[city.lowercase()]?.let { return it }
        }
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${android.net.Uri.encode(city)}&count=1&language=en&format=json"
        val json = fetchJson(url)
        val results = json.optJSONArray("results")
            ?: throw IllegalArgumentException("Location not found: $city")
        val first = results.getJSONObject(0)
        val pair = Pair(first.getDouble("latitude"), first.getDouble("longitude"))
        geocodeMutex.withLock {
            geocodeCache[city.lowercase()] = pair
        }
        return pair
    }

    private fun fetchJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Weather service error ${response.code}")
            val body = response.body?.string() ?: throw RuntimeException("Empty weather response")
            return JSONObject(body)
        }
    }
}
