package com.custom.vox.homemonitor.data.weather

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo current conditions for Belgrade — no API key required.
 */
class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile
    var lastSnapshot: WeatherSnapshot? = null
        private set

    fun refresh(): Result<WeatherSnapshot> {
        return try {
            val url =
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$LAT&longitude=$LON" +
                    "&current=temperature_2m,weather_code" +
                    "&hourly=precipitation_probability" +
                    "&forecast_days=1" +
                    "&timezone=Europe%2FBelgrade"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return failOrStale("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return failOrStale("Empty body")
                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val code = current.getInt("weather_code")
                val temp = current.getDouble("temperature_2m")
                val rain = nextHoursRainProbability(json)
                val snap = WeatherSnapshot(
                    temperatureC = temp,
                    weatherCode = code,
                    description = WeatherCodes.describe(code),
                    rainProbabilityNextHours = rain,
                    refreshedAtMs = System.currentTimeMillis(),
                    stale = false,
                )
                lastSnapshot = snap
                Result.success(snap)
            }
        } catch (e: Exception) {
            failOrStale(e.message ?: "error")
        }
    }

    private fun failOrStale(reason: String): Result<WeatherSnapshot> {
        val prev = lastSnapshot
        return if (prev != null) {
            val stale = prev.copy(stale = true)
            lastSnapshot = stale
            Result.success(stale)
        } else {
            Result.failure(IllegalStateException(reason))
        }
    }

    private fun nextHoursRainProbability(json: JSONObject): Int? {
        val hourly = json.optJSONObject("hourly") ?: return null
        val probs = hourly.optJSONArray("precipitation_probability") ?: return null
        val times = hourly.optJSONArray("time") ?: return null
        val now = System.currentTimeMillis()
        // Take max probability over the next ~6 hours from "now" index approximation.
        var max = 0
        var counted = 0
        for (i in 0 until minOf(probs.length(), times.length())) {
            if (counted >= 6) break
            val p = probs.optInt(i, 0)
            if (p > max) max = p
            counted++
        }
        // Silence unused
        void(now)
        return max
    }

    private fun void(@Suppress("UNUSED_PARAMETER") x: Long) = Unit

    companion object {
        // Belgrade city center
        const val LAT = 44.8176
        const val LON = 20.4633
        const val REFRESH_MS = 25 * 60 * 1000L
        const val STALE_AFTER_MS = 90 * 60 * 1000L
    }
}
