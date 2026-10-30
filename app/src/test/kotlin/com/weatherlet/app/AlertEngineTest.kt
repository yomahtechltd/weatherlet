package com.weatherlet.app

import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertEngineTest {
    private val base = LocalDate.of(2026, 12, 1)

    private fun day(
        i: Int, tMax: Double = 15.0, tMin: Double = 8.0, feels: Double = 6.0, rain: Double = 0.0,
        snow: Double = 0.0, prob: Int = 0, gust: Double = 30.0, code: Int = 3,
        uv: Double = 1.0, aqi: Int = 20, pollen: Double = 0.0, vis: Double = 20_000.0,
    ) = DayForecast(base.plusDays(i.toLong()), code, tMax, tMin, feels, rain, snow, prob, gust / 2, gust, uv, aqi, pollen, vis)

    private fun week(f: (Int) -> DayForecast = { day(it) }) = (0..5).map(f)
    private val past = listOf(day(-2), day(-1))

    @Test fun calmWeatherHasNoAlerts() {
        assertTrue(AlertEngine.evaluate(week(), past).isEmpty())
    }

    @Test fun consecutiveSnowDaysMergeIntoOneAlertWithWorstSeverity() {
        val days = week { if (it in 2..3) day(it, snow = if (it == 3) 12.0 else 3.0, tMin = 5.0) else day(it) }
        val a = AlertEngine.evaluate(days, past).single()
        assertEquals(AlertType.SNOW, a.type)
        assertEquals(Severity.SEVERE, a.severity)
        assertEquals(days[2].date, a.start); assertEquals(days[3].date, a.end)
        assertEquals(12.0, a.peak, 0.0)
    }

    @Test fun heatwaveNeedsThreeWarmDays() {
        val two = week { day(it, tMax = if (it in 1..2) 28.0 else 20.0) }
        assertTrue(AlertEngine.evaluate(two, past).isEmpty())
        val three = week { day(it, tMax = if (it in 1..3) 28.0 else 20.0) }
        assertEquals(AlertType.HEAT, AlertEngine.evaluate(three, past).single().type)
    }

    @Test fun windRainColdThresholds() {
        val days = listOf(day(0, gust = 70.0), day(1, rain = 25.0, prob = 80), day(2, feels = -7.0, tMin = 3.0), day(3), day(4), day(5))
        val types = AlertEngine.evaluate(days, past).map { it.type }.toSet()
        assertEquals(setOf(AlertType.WIND, AlertType.RAIN, AlertType.COLD), types)
    }

    @Test fun blackIceNeedsFreezeAfterWetWeather() {
        val dryFrost = week { day(it, tMin = if (it == 2) -1.0 else 8.0) }
        assertTrue(AlertEngine.evaluate(dryFrost, past).none { it.type == AlertType.ICE })
        val wetFrost = week { day(it, tMin = if (it == 2) -1.0 else 8.0, rain = if (it == 1) 4.0 else 0.0) }
        assertEquals(AlertType.ICE, AlertEngine.evaluate(wetFrost, past).single { it.type == AlertType.ICE }.type)
    }

    @Test fun gardenerGetsDryFrostAlert() {
        val dryFrost = week { day(it, tMin = if (it == 2) -1.0 else 8.0) }
        val a = AlertEngine.evaluate(dryFrost, past, setOf(Profile.GARDENER))
        assertTrue(a.any { it.type == AlertType.ICE })
    }

    @Test fun floodUsesRainFromBeforeToday() {
        // 30mm on each of the two previous days + 10mm today = 70mm over 3 days, still raining
        val soaked = listOf(day(-2, rain = 30.0), day(-1, rain = 30.0))
        val days = week { day(it, rain = if (it == 0) 10.0 else 0.0) }
        val a = AlertEngine.evaluate(days, soaked)
        assertEquals(AlertType.FLOOD, a.single().type)
        assertEquals(Severity.WARNING, a.single().severity)
        // Same forecast on dry ground is nothing
        assertTrue(AlertEngine.evaluate(days, past).isEmpty())
    }

    @Test fun floodSubsumesPlainRainAlert() {
        val soaked = listOf(day(-2, rain = 30.0), day(-1, rain = 30.0))
        val days = week { day(it, rain = if (it == 0) 25.0 else 0.0, prob = 90) }
        val types = AlertEngine.evaluate(days, soaked).map { it.type }
        assertTrue(AlertType.FLOOD in types)
        assertTrue(AlertType.RAIN !in types)
    }

    @Test fun temperatureSwingDetectsBigDrop() {
        val days = week { day(it, tMax = if (it >= 2) 4.0 else 18.0, tMin = if (it >= 2) 0.0 else 12.0) } // mean 15 -> 2
        val swing = AlertEngine.evaluate(days, past).single { it.type == AlertType.SWING }
        assertTrue(swing.peak < -10)
    }

    @Test fun cyclistIsWarnedAtLowerWind() {
        val days = week { day(it, gust = if (it == 1) 45.0 else 20.0) }
        assertTrue(AlertEngine.evaluate(days, past).isEmpty())
        assertEquals(AlertType.WIND, AlertEngine.evaluate(days, past, setOf(Profile.CYCLIST)).single { it.type == AlertType.WIND }.type)
    }

    @Test fun pollenOnlyForAllergyProfile() {
        val days = week { day(it, pollen = 80.0) }
        assertTrue(AlertEngine.evaluate(days, past).none { it.type == AlertType.POLLEN })
        assertTrue(AlertEngine.evaluate(days, past, setOf(Profile.ALLERGY)).any { it.type == AlertType.POLLEN })
    }

    @Test fun fogUvAirThresholds() {
        val days = week { day(it, vis = if (it == 0) 150.0 else 20_000.0, uv = if (it == 1) 9.0 else 1.0, aqi = if (it == 2) 90 else 20) }
        val types = AlertEngine.evaluate(days, past).map { it.type }.toSet()
        assertEquals(setOf(AlertType.FOG, AlertType.UV, AlertType.AIR), types)
    }

    @Test fun parsesRealOpenMeteoResponse() {
        val raw = javaClass.getResource("/manchester.json")!!.readText()
        val f = Forecast.parse(Place("Manchester", "England", "United Kingdom", 53.48, -2.24, "GB"), raw, 0)
        assertEquals(6, f.days.size)
        assertEquals(2, f.past.size)
        assertTrue(f.hours.size >= 24 * 6 - 1)
        assertTrue(f.current.temp in -40.0..55.0)
        assertTrue(f.days.all { it.minVisM > 0 })
        AlertEngine.evaluate(f.days, f.past, Profile.entries.toSet()) // must not throw
    }
}
