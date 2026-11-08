package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCodesTest {
    @Test
    fun describesCommonCodes() {
        assertEquals("Clear", WeatherCodes.describe(0))
        assertEquals("Partly cloudy", WeatherCodes.describe(2))
        assertEquals("Rain", WeatherCodes.describe(61))
        assertTrue(WeatherCodes.iconKind(0) == "clear")
        assertTrue(WeatherCodes.iconKind(61) == "rain")
    }
}
