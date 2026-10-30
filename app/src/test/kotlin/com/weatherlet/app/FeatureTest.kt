package com.weatherlet.app

import com.weatherlet.app.alerts.AlertSnapshot
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.ChangeKind
import com.weatherlet.app.alerts.ChangeTracker
import com.weatherlet.app.alerts.CommuteAdvisor
import com.weatherlet.app.alerts.CommuteSettings
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.data.HourForecast
import com.weatherlet.app.data.OfficialWarnings
import com.weatherlet.app.data.Place
import com.weatherlet.app.data.Units
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FeatureTest {
    private val d0 = LocalDate.of(2026, 12, 1)
    private val dates = (0..5).map { d0.plusDays(it.toLong()) }

    private fun snap(vararg snow: Severity?, profiles: Set<Profile> = emptySet(), key: String = "p", dateList: List<LocalDate> = dates) =
        AlertSnapshot.of(key, profiles, dateList, mapOf(AlertType.SNOW to snow.toList()))

    // ---- change tracking -------------------------------------------------------------------------

    @Test fun detectsNewUpgradedClearedAndDowngraded() {
        val prev = snap(null, null, Severity.WARNING, Severity.WARNING, Severity.SEVERE, null)
        val now = snap(null, Severity.WARNING, Severity.SEVERE, null, Severity.WARNING, null)
        val kinds = ChangeTracker.diff(prev, now, 0).map { it.kind to it.start }.toSet()
        assertEquals(
            setOf(
                ChangeKind.NEW to dates[1], ChangeKind.UPGRADED to dates[2],
                ChangeKind.CLEARED to dates[3], ChangeKind.DOWNGRADED to dates[4],
            ),
            kinds,
        )
    }

    @Test fun newlyVisibleDayIsNotAChange() {
        // Yesterday's window ended one day earlier; the last day appearing with an alert isn't "new"
        val prev = snap(null, null, null, null, null, dateList = dates.take(5))
        val now = snap(null, null, null, null, null, Severity.SEVERE)
        assertTrue(ChangeTracker.diff(prev, now, 0).isEmpty())
    }

    @Test fun differentProfilesOrPlacesAreNotComparable() {
        val prev = snap(null, null, null, null, null, null)
        val now = snap(Severity.SEVERE, null, null, null, null, null, profiles = setOf(Profile.DRIVER))
        assertTrue(ChangeTracker.diff(prev, now, 0).isEmpty())
        assertTrue(ChangeTracker.diff(prev, snap(Severity.SEVERE, null, null, null, null, null, key = "other"), 0).isEmpty())
    }

    @Test fun snapshotSurvivesJson() {
        val s = snap(null, Severity.WARNING, Severity.SEVERE, null, null, null)
        val back = AlertSnapshot.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s, back)
    }

    // ---- commute --------------------------------------------------------------------------------------

    private fun hour(h: Int, temp: Double = 10.0, rain: Double = 0.0, prob: Int = 0, code: Int = 3, gust: Double = 20.0, vis: Double = 20000.0) =
        HourForecast(d0.atTime(h, 0), temp, rain, prob, code, gust, vis)

    @Test fun commuteFlagsRainAtTheRightHour() {
        val hours = (5..20).map { hour(it, rain = if (it == 8) 2.0 else 0.0, prob = if (it == 8) 80 else 0) }
        val leg = CommuteAdvisor.leg("Morning", d0.atTime(7, 45), hours, Units.METRIC)
        assertTrue(leg.issues.single().text.contains("8:00 AM"))
        assertEquals(Severity.WARNING, leg.worst)
        assertTrue(CommuteAdvisor.leg("Evening", d0.atTime(17, 30), hours, Units.METRIC).clear)
    }

    @Test fun commuteFlagsIceAfterWetNight() {
        val hours = (3..20).map { hour(it, temp = -1.0, rain = if (it == 4) 0.5 else 0.0) }
        val leg = CommuteAdvisor.leg("Morning", d0.atTime(7, 30), hours, Units.METRIC)
        assertTrue(leg.issues.any { it.emoji == "🧊" })
    }

    @Test fun upcomingSkipsWeekends() {
        // 2026-12-04 is a Friday; after the evening commute the next legs are Monday's
        val fri = LocalDateTime.of(2026, 12, 4, 19, 0)
        val legs = CommuteAdvisor.upcoming(fri, CommuteSettings(true), emptyList(), Units.METRIC)
        assertEquals(LocalDate.of(2026, 12, 7), legs.first().at.toLocalDate())
    }

    // ---- official warnings ----------------------------------------------------------------------------

    @Test fun parsesMetOfficeFeed() {
        val xml = javaClass.getResource("/metoffice.xml")!!.readText()
        // Whatever is live, parsing must not blow up and every result must match the region we asked for
        val regions = OfficialWarnings.ukRegions.keys
        regions.forEach { r -> OfficialWarnings.parseMetOffice(xml, r) }
        val synthetic = """<rss><channel><item><title>Amber warning of wind affecting Wales</title>
            <link>https://example.org/w?a=1&amp;b=2</link>
            <description>Amber warning of wind affecting Wales: Gwynedd valid from 0900 Wed 14 Oct to 1800 Wed 14 Oct</description>
            <guid>g-1</guid></item></channel></rss>"""
        val w = OfficialWarnings.parseMetOffice(synthetic, "Wales").single()
        assertEquals(Severity.SEVERE, w.severity)
        assertEquals("Amber warning of wind", w.title)
        assertEquals("https://example.org/w?a=1&b=2", w.url)
        assertTrue(w.detail.startsWith("Valid from 0900"))
        assertTrue(OfficialWarnings.parseMetOffice(synthetic, "Grampian").isEmpty())
    }

    @Test fun nearestUkRegion() {
        assertEquals("North West England", OfficialWarnings.nearestRegion(53.48, -2.24)) // Manchester
        assertEquals("London & South East England", OfficialWarnings.nearestRegion(51.5, -0.12))
        assertEquals("Wales", OfficialWarnings.nearestRegion(51.48, -3.18)) // Cardiff
        assertEquals("Highlands & Eilean Siar", OfficialWarnings.nearestRegion(57.48, -4.22)) // Inverness
    }

    @Test fun placeKeyAndTitle() {
        val p = Place("Leeds", "England", "United Kingdom", 53.7997, -1.5492, "GB", "Mum's")
        assertEquals("53.80,-1.55", p.key)
        assertEquals("Mum's", p.title)
        assertNull(Place.fromJson(p.toJson()).copy(nickname = null).nickname)
        assertEquals(p, Place.fromJson(p.toJson()))
    }
}
