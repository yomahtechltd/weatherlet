package com.weatherlet.app

import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.BackgroundHealth
import com.weatherlet.app.alerts.Health
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.RainOutlook
import com.weatherlet.app.alerts.RainSoonDetector
import com.weatherlet.app.alerts.RainStep
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.tip
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RainSoonTest {
    private val now = LocalDateTime.of(2026, 10, 20, 15, 0)

    /** mm per 15-min step for the steps ending at now-15, now, now+15, ... */
    private fun outlook(vararg mm: Double, temp: Double = 12.0) = RainOutlook(
        now, temp, mm.mapIndexed { i, v -> RainStep(now.plusMinutes(15L * (i - 1)), v) },
    )

    @Test fun alreadyRainingIsNotANudge() {
        assertNull(RainSoonDetector.detect(outlook(0.4, 0.5, 0.5, 0.5, 0.5, 0.5)))
    }

    @Test fun dryAllAfternoonIsQuiet() {
        assertNull(RainSoonDetector.detect(outlook(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
    }

    @Test fun rainStartingInFortyFiveMinutesIsReported() {
        // steps end at 14:45, 15:00, 15:15, 15:30, 15:45 (first wet), 16:00 ...
        val soon = RainSoonDetector.detect(outlook(0.0, 0.0, 0.0, 0.0, 0.8, 1.0, 1.0, 0.8, 0.5))!!
        assertEquals(30, soon.inMinutes) // the 15:45 step covers 15:30-15:45
        assertEquals("Moderate", soon.intensity)
        assertTrue(soon.title("Leeds").contains("in about 30 min"))
    }

    @Test fun aSingleSprinkleIsIgnored() {
        assertNull(RainSoonDetector.detect(outlook(0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
    }

    @Test fun rainBeyondNinetyMinutesIsTooEarly() {
        assertNull(RainSoonDetector.detect(outlook(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0)))
    }

    @Test fun freezingMeansSnowWording() {
        val soon = RainSoonDetector.detect(outlook(0.0, 0.0, 0.3, 0.4, 0.4, 0.4, temp = -1.0))!!
        assertTrue(soon.snow)
        assertTrue(soon.title("Oslo").contains("Snow"))
    }

    @Test fun heavyRainIsLabelledHeavy() {
        val soon = RainSoonDetector.detect(outlook(0.0, 0.0, 1.5, 1.5, 1.5, 1.5))!!
        assertEquals("Heavy", soon.intensity)
    }
}

class PressureAlertTest {
    private val base = LocalDate.of(2026, 12, 1)
    private fun day(i: Int, fall: Double) = DayForecast(
        base.plusDays(i.toLong()), 3, 15.0, 8.0, 6.0, 0.0, 0.0, 0, 15.0, 30.0, 1.0, 20, 0.0, 20_000.0, 0.0, fall,
    )
    private val week = (0..5).map { day(it, if (it == 2) 9.0 else 1.0) }

    @Test fun aModestFallIsIgnoredForEveryoneElse() {
        assertTrue(AlertEngine.evaluate(week, emptyList()).none { it.type == AlertType.PRESSURE })
    }

    @Test fun migraineProfileGetsWarnedAtNine() {
        val a = AlertEngine.evaluate(week, emptyList(), setOf(Profile.MIGRAINE)).single { it.type == AlertType.PRESSURE }
        assertEquals(Severity.WARNING, a.severity)
        assertTrue(AlertType.PRESSURE.tip(setOf(Profile.MIGRAINE)).contains("migraine"))
    }

    @Test fun aStormBombIsStillSilentWithoutTheMigraineProfile() {
        val days = week.toMutableList().also { it[3] = day(3, 28.0) }
        assertTrue(AlertEngine.evaluate(days, emptyList(), setOf(Profile.DRIVER, Profile.CYCLIST)).none { it.type == AlertType.PRESSURE })
    }

    @Test fun aBigFallIsSevereForMigraineProfile() {
        val days = week.toMutableList().also { it[3] = day(3, 20.0) }
        val a = AlertEngine.evaluate(days, emptyList(), setOf(Profile.MIGRAINE)).single { it.type == AlertType.PRESSURE }
        assertEquals(Severity.SEVERE, a.severity)
    }

    @Test fun pressureFallIsParsedFromHourlyData() {
        // 3 days of hourly data: pressure steady for 2 days, then drops 12 hPa across day 3
        val times = (0 until 72).map { "\"2026-10-${18 + it / 24}T${"%02d".format(it % 24)}:00\"" }
        val pressure = (0 until 72).map { if (it < 48) 1015.0 else 1015.0 - (it - 48) * 0.5 }
        val zeros = (0 until 72).joinToString(",") { "0" }
        val json = """{"current":{"time":"2026-10-20T10:00","temperature_2m":10,"weather_code":3},
          "hourly":{"time":[${times.joinToString(",")}],"pressure_msl":[${pressure.joinToString(",")}],
            "temperature_2m":[$zeros],"precipitation":[$zeros]},
          "daily":{"time":["2026-10-18","2026-10-19","2026-10-20"],"weather_code":[3,3,3],
            "temperature_2m_max":[10,10,10],"temperature_2m_min":[5,5,5]}}"""
        val f = Forecast.parse(Place("X", null, "", 1.0, 1.0), json, 0)
        assertEquals(11.5, f.days.first().pressureFall, 0.01)
    }
}

class BackgroundHealthTest {
    private val now = 1_000_000_000_000L
    private fun hours(h: Int) = h * 3_600_000L

    @Test fun healthySetupIsGreen() {
        val r = BackgroundHealth.report(now, now - hours(2), 3, now - hours(20), true, true, BackgroundHealth.BUCKET_ACTIVE)
        assertEquals(Health.GOOD, r.verdict)
        assertNull(r.advice)
    }

    @Test fun batteryOptimisationOnIsAWarning() {
        val r = BackgroundHealth.report(now, now - hours(2), 3, now - hours(20), true, false, BackgroundHealth.BUCKET_ACTIVE)
        assertEquals(Health.WARN, r.verdict)
        assertNotNull(r.advice)
        assertTrue(r.advice!!.contains("unrestricted"))
    }

    @Test fun veryLateDailyAlertIsRed() {
        val r = BackgroundHealth.report(now, now - hours(1), 190, now - hours(5), true, true, BackgroundHealth.BUCKET_ACTIVE)
        assertEquals(Health.BAD, r.verdict)
    }

    @Test fun stoppedRefreshesAreRed() {
        val r = BackgroundHealth.report(now, now - hours(30), -1, 0, true, true, BackgroundHealth.BUCKET_WORKING_SET)
        assertEquals(Health.BAD, r.verdict)
    }

    @Test fun restrictedBucketIsRed() {
        val r = BackgroundHealth.report(now, now - hours(1), 2, now, true, true, BackgroundHealth.BUCKET_RESTRICTED)
        assertEquals(Health.BAD, r.verdict)
    }
}
