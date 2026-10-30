package com.weatherlet.app

import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.Messages
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.WeatherAlert
import com.weatherlet.app.alerts.tip
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Locator
import com.weatherlet.app.data.Place
import com.weatherlet.app.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotificationAuditTest {
    private val today = LocalDate.of(2026, 12, 1)
    private val out = java.io.File(System.getProperty("java.io.tmpdir"), "weatherlet_audit.txt")
    private fun alert(t: AlertType, sev: Severity, peak: Double, d: Long = 1) =
        WeatherAlert(t, sev, today.plusDays(d), today.plusDays(d), peak)

    private fun print(label: String, profiles: Set<Profile>, alerts: List<WeatherAlert>) {
        val m = Messages.daily("Leeds", today, alerts, emptyList(), Units.METRIC, profiles)
        out.appendText("[$label]\n  TITLE: ${m.title}\n  TEXT : ${m.text}\n  BIG  : ${m.big.replace("\n", "\n         ")}\n\n", Charsets.UTF_8)
    }

    @Test fun auditEveryProfile() {
        out.writeText("", Charsets.UTF_8)
        val singles = listOf(
            alert(AlertType.AIR, Severity.WARNING, 72.0),
            alert(AlertType.POLLEN, Severity.WARNING, 60.0),
            alert(AlertType.DUST, Severity.WARNING, 180.0),
            alert(AlertType.COLD, Severity.WARNING, -3.0),
            alert(AlertType.STORM, Severity.WARNING, 0.0),
        )
        out.appendText("======== No profile\n", Charsets.UTF_8)
        singles.take(2).forEach { print("none/${it.type}", emptySet(), listOf(it)) }
        out.appendText("======== ALLERGY / ASTHMA\n", Charsets.UTF_8)
        singles.forEach { print("allergy/${it.type}", setOf(Profile.ALLERGY), listOf(it)) }
        print("allergy/multi", setOf(Profile.ALLERGY), singles.take(3))
        out.appendText("======== Other profiles, one alert each\n", Charsets.UTF_8)
        print("driver/FOG", setOf(Profile.DRIVER), listOf(alert(AlertType.FOG, Severity.WARNING, 180.0)))
        print("cyclist/WIND", setOf(Profile.CYCLIST), listOf(alert(AlertType.WIND, Severity.WARNING, 48.0)))
        print("gardener/ICE", setOf(Profile.GARDENER), listOf(alert(AlertType.ICE, Severity.WARNING, -1.0)))
        print("pets/HEAT", setOf(Profile.PET_OWNER), listOf(alert(AlertType.HEAT, Severity.WARNING, 27.0)))
        print("worker/UV", setOf(Profile.OUTDOOR_WORKER), listOf(alert(AlertType.UV, Severity.WARNING, 7.0)))
        out.appendText("======== Two profiles\n", Charsets.UTF_8)
        print("allergy+driver/DUST", setOf(Profile.ALLERGY, Profile.DRIVER), listOf(alert(AlertType.DUST, Severity.WARNING, 180.0)))
        print("cyclist+allergy/AIR", setOf(Profile.CYCLIST, Profile.ALLERGY), listOf(alert(AlertType.AIR, Severity.WARNING, 72.0)))
    }

    @Test fun allergyProfileGetsAsthmaSpecificAdvice() {
        val p = setOf(Profile.ALLERGY)
        for (t in listOf(AlertType.AIR, AlertType.POLLEN, AlertType.DUST, AlertType.COLD, AlertType.STORM, AlertType.HEAT, AlertType.SWING)) {
            assertTrue("$t has an allergy-specific tip", t.tip(p).startsWith(Profile.ALLERGY.emoji))
        }
        // No profile → plain generic text, no emoji prefix
        assertEquals(AlertType.AIR.tip, AlertType.AIR.tip(emptySet()))
    }

    @Test fun twoProfilesShowBothTipsAtMostTwo() {
        val tip = AlertType.DUST.tip(setOf(Profile.ALLERGY, Profile.CYCLIST, Profile.DRIVER))
        assertEquals(2, Regex("[🤧🚴🚗]").findAll(tip).count())
    }

    @Test fun allergyProfileEnablesDustPollenAndColdAirAlerts() {
        fun day(i: Int, dust: Double = 0.0, pollen: Double = 0.0, feels: Double = 5.0) =
            DayForecast(today.plusDays(i.toLong()), 3, 12.0, 4.0, feels, 0.0, 0.0, 0, 10.0, 20.0, 1.0, 20, pollen, 20_000.0, dust)
        val days = (0..5).map { day(it, dust = if (it == 1) 120.0 else 0.0, pollen = if (it == 2) 40.0 else 0.0, feels = if (it == 3) -1.0 else 5.0) }
        assertTrue(AlertEngine.evaluate(days).isEmpty())
        val types = AlertEngine.evaluate(days, emptyList(), setOf(Profile.ALLERGY)).map { it.type }.toSet()
        assertEquals(setOf(AlertType.DUST, AlertType.POLLEN, AlertType.COLD), types)
    }

    @Test fun distanceBetweenPlaces() {
        val leeds = Place("Leeds", null, "UK", 53.80, -1.55)
        val manchester = Place("Manchester", null, "UK", 53.48, -2.24)
        assertEquals(58.0, Locator.distanceKm(leeds, manchester), 4.0)
        assertTrue(Locator.distanceKm(leeds, leeds) < 0.01)
    }
}

class FreshnessTest {
    private val h = 3_600_000L
    private val now = java.time.LocalDateTime.of(2026, 12, 2, 6, 0)

    @Test fun tiers() {
        assertEquals(com.weatherlet.app.alerts.Freshness.FRESH, com.weatherlet.app.alerts.FreshnessPolicy.classify(5 * h))
        assertEquals(com.weatherlet.app.alerts.Freshness.STALE, com.weatherlet.app.alerts.FreshnessPolicy.classify(8 * h))
        assertEquals(com.weatherlet.app.alerts.Freshness.EXPIRED, com.weatherlet.app.alerts.FreshnessPolicy.classify(30 * h))
    }

    @Test fun labelsReadNaturally() {
        val p = com.weatherlet.app.alerts.FreshnessPolicy
        assertEquals("10:10 PM last night", p.label(java.time.LocalDateTime.of(2026, 12, 1, 22, 10), now))
        assertEquals("9:00 AM yesterday", p.label(java.time.LocalDateTime.of(2026, 12, 1, 9, 0), now))
        assertEquals("5:30 AM today", p.label(java.time.LocalDateTime.of(2026, 12, 2, 5, 30), now))
    }

    private val alert = WeatherAlert(AlertType.SNOW, Severity.WARNING, now.toLocalDate().plusDays(1), now.toLocalDate().plusDays(1), 4.0)
    private fun msg(asOf: java.time.LocalDateTime?, offline: Boolean) =
        Messages.daily("Leeds", now.toLocalDate(), listOf(alert), emptyList(), Units.METRIC, emptySet(), asOf, offline, now)

    @Test fun staleAlertSaysHowOldAndOffline() {
        val m = msg(java.time.LocalDateTime.of(2026, 12, 1, 22, 10), offline = true)
        assertEquals("Forecast from 10:10 PM last night — you appear to be offline", m.note)
        assertTrue(m.big.contains("Open the app when you're online"))
    }

    @Test fun freshAlertHasNoBanner() {
        assertEquals(null, msg(null, false).note)
        assertEquals(null, msg(java.time.LocalDateTime.of(2026, 12, 2, 5, 55), false).note)
    }

    @Test fun recentButOfflineJustShowsTime() {
        assertEquals("Updated 3:00 AM today", msg(java.time.LocalDateTime.of(2026, 12, 2, 3, 0), true).note)
    }
}
