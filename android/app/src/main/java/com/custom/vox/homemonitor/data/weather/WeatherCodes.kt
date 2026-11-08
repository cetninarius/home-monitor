package com.custom.vox.homemonitor.data.weather

object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm + hail"
        else -> "Conditions"
    }

    /** Drawable resource name key for simple icon mapping. */
    fun iconKind(code: Int): String = when (code) {
        0, 1 -> "clear"
        2 -> "partly"
        3, 45, 48 -> "cloud"
        in 51..67, in 80..82 -> "rain"
        in 71..77, in 85..86 -> "snow"
        in 95..99 -> "storm"
        else -> "cloud"
    }
}
