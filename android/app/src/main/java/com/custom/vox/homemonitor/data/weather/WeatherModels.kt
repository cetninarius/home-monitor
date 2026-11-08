package com.custom.vox.homemonitor.data.weather

data class WeatherSnapshot(
    val temperatureC: Double,
    val weatherCode: Int,
    val description: String,
    val rainProbabilityNextHours: Int?,
    val refreshedAtMs: Long,
    val stale: Boolean = false,
)
