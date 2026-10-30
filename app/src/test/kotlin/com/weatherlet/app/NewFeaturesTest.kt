package com.weatherlet.app

import com.weatherlet.app.alerts.Activity
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.BestDay
import com.weatherlet.app.alerts.Checklist
import com.weatherlet.app.alerts.Messages
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.QuietHours
import com.weatherlet.app.alerts.Sensitivity
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.WeatherAlert
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.EnsoPhase
import com.weatherlet.app.data.EnsoStatus
import com.weatherlet.app.data.Enso
import com.weatherlet.app.data.OfficialWarnings
import com.weatherlet.app.data.Place
import com.weatherlet.app.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewFeaturesTest {
    private val base = LocalDate.of(2026, 12, 1)
    private fun day(
        i: Int, tMax: Double = 15.0, tMin: Double = 8.0, rain: Double = 0.0, prob: Int = 0, gust: Double = 20.0,
        snow: Double = 0.0, code: Int = 3, vis: Double = 20000.0, uv: Double = 1.0,
    ) = DayForecast(base.plusDays(i.toLong()), code, tMax, tMin, tMin - 2, rain, snow, prob, gust / 2, gust, uv, 20, 0.0, vis, 0.0)

    // ---- sensitivity -----------------------------------------------------------------------------

    @Test fun sensitivityMovesTheLine() {
        val days = (0..5).map { day(it, gust = if (it == 1) 55.0 else 20.0) }
        assertTrue(AlertEngine.evaluate(days).isEmpty())                                   // 55 < 65 normally
        assertTrue(AlertEngine.evaluate(days, sensitivity = Sensitivity.RELAXED).isEmpty())
        assertEquals(AlertType.WIND, AlertEngine.evaluate(days, sensitivity = Sensitivity.SENSITIVE).single().type)
        val windy = (0..5).map { day(it, gust = if (it == 1) 70.0 else 20.0) }
        assertEquals(1, AlertEngine.evaluate(windy).size)
        assertTrue(AlertEngine.evaluate(windy, sensitivity = Sensitivity.RELAXED).isEmpty()) // 70 < 81
    }

    // ---- checklist -------------------------------------------------------------------------------

    @Test fun checklistPutsProfileTasksFirstAndCapsAtFive() {
        val items = Checklist.items(AlertType.SNOW, setOf(Profile.DRIVER))
        assertTrue(items.first().contains("tank"))
        assertTrue(items.size <= 5)
        assertEquals(items.size, items.distinct().size)
        AlertType.entries.forEach { assertTrue("$it has tasks", Checklist.items(it, emptySet()).isNotEmpty()) }
    }

    @Test fun checklistKeysAreStablePerAlertAndItem() {
        val a = WeatherAlert(AlertType.SNOW, Severity.WARNING, base, base.plusDays(1), 3.0)
        assertEquals("p|SNOW|2026-12-01|2", Checklist.key("p", a, 2))
        assertNotEquals(Checklist.key("p", a, 1), Checklist.key("p", a, 2))
    }

    // ---- best day --------------------------------------------------------------------------------

    @Test fun bestDayPicksTheDryCalmDayForWashing() {
        val days = (0..5).map { day(it, rain = if (it == 3) 0.0 else 6.0, prob = if (it == 3) 5 else 80) }
        assertEquals(base.plusDays(3), BestDay.best(Activity.LAUNDRY, days)!!.day.date)
    }

    @Test fun badWeatherScoresPoorlyAndGoodScoresWell() {
        val storm = day(0, rain = 30.0, prob = 100, gust = 90.0, code = 95, snow = 0.0)
        val perfect = day(1, tMax = 17.0, tMin = 9.0)
        for (a in Activity.entries) assertTrue("$a: perfect beats storm", BestDay.score(a, perfect) > BestDay.score(a, storm))
        assertTrue(BestDay.score(Activity.RUN, perfect) >= 80)
        assertTrue(BestDay.score(Activity.DRIVE, storm) < 60)
    }

    @Test fun drivingIsPenalisedForFogAndSnow() {
        assertTrue(BestDay.score(Activity.DRIVE, day(0, vis = 150.0)) < BestDay.score(Activity.DRIVE, day(0)))
        assertTrue(BestDay.score(Activity.DRIVE, day(0, snow = 6.0)) < 70)
    }

    // ---- quiet hours -----------------------------------------------------------------------------

    @Test fun quietHoursWrapPastMidnight() {
        val start = 22 * 60; val end = 6 * 60
        assertTrue(QuietHours.contains(start, end, 23 * 60))
        assertTrue(QuietHours.contains(start, end, 2 * 60))
        assertFalse(QuietHours.contains(start, end, 6 * 60))
        assertFalse(QuietHours.contains(start, end, 12 * 60))
        assertTrue(QuietHours.contains(13 * 60, 15 * 60, 14 * 60))
        assertFalse(QuietHours.contains(8 * 60, 8 * 60, 8 * 60))
    }

    // ---- share -----------------------------------------------------------------------------------

    @Test fun shareTextIsReadableOutsideTheApp() {
        val a = WeatherAlert(AlertType.SNOW, Severity.WARNING, base.plusDays(2), base.plusDays(3), 5.0)
        val t = Messages.share(a, "Leeds", base, Units.METRIC, emptySet())
        assertTrue(t.contains("Snow"))
        assertTrue(t.contains("Leeds"))
        assertTrue(t.contains("5 cm"))
        assertTrue(t.endsWith("via Weatherlet"))
    }

    // ---- ENSO ------------------------------------------------------------------------------------

    @Test fun parsesRealOniFile() {
        val s = EnsoStatus.parse(javaClass.getResource("/oni.txt")!!.readText())!!
        assertTrue(s.year >= 2025)
        assertTrue(s.oni in -3.5..3.5)
        assertEquals(if (s.oni >= 0.5) EnsoPhase.EL_NINO else if (s.oni <= -0.5) EnsoPhase.LA_NINA else EnsoPhase.NEUTRAL, s.phase)
    }

    @Test fun ensoPhaseAndStrength() {
        assertEquals(EnsoPhase.EL_NINO, EnsoStatus("JAS", 2026, 2.16, 0.4).phase)
        assertEquals("very strong", EnsoStatus("JAS", 2026, 2.16, 0.4).strength)
        assertEquals("weak", EnsoStatus("JAS", 2026, -0.6, 0.0).strength)
        assertEquals(EnsoPhase.NEUTRAL, EnsoStatus("JAS", 2026, 0.2, 0.0).phase)
    }

    @Test fun regionalNotesDifferByPlace() {
        val lagos = Enso.regionalNote(6.5, 3.4, EnsoPhase.EL_NINO)
        val nairobi = Enso.regionalNote(-1.3, 36.8, EnsoPhase.EL_NINO)
        val leeds = Enso.regionalNote(53.8, -1.55, EnsoPhase.EL_NINO)
        assertTrue(lagos.contains("West Africa"))
        assertTrue(nairobi.contains("East Africa"))
        assertTrue(leeds.contains("Europe"))
        assertNotEquals(lagos, Enso.regionalNote(6.5, 3.4, EnsoPhase.LA_NINA))
    }

    // ---- MeteoAlarm ------------------------------------------------------------------------------

    private val json by lazy { javaClass.getResource("/meteoalarm_de.json")!!.readText() }
    private val onset = java.time.OffsetDateTime.parse("2026-10-09T06:00:00+02:00").toInstant().toEpochMilli()

    @Test fun matchesWarningsByDistrictName() {
        val hits = OfficialWarnings.parseMeteoAlarm(json, listOf("Emmendingen"), onset)
        assertTrue("expected an Emmendingen warning", hits.isNotEmpty())
        assertTrue(hits.all { it.source.startsWith("MeteoAlarm") })
        assertTrue(hits.all { it.severity == Severity.WARNING || it.severity == Severity.SEVERE })
    }

    @Test fun unrelatedPlacesGetNothing() {
        assertTrue(OfficialWarnings.parseMeteoAlarm(json, listOf("Kiel", "Schleswig-Holstein"), onset).isEmpty())
    }

    @Test fun expiredWarningsAreDropped() {
        val after = java.time.OffsetDateTime.parse("2026-10-10T12:00:00+02:00").toInstant().toEpochMilli()
        assertTrue(OfficialWarnings.parseMeteoAlarm(json, listOf("Emmendingen"), after).isEmpty())
    }

    @Test fun placeKeepsDistrictForMatching() {
        val p = Place("Freiburg", "Baden-Württemberg", "Germany", 48.0, 7.85, "DE", admin2 = "Freiburg")
        assertEquals(p, Place.fromJson(p.toJson()))
        assertTrue(OfficialWarnings.isSupported(p))
    }
}
