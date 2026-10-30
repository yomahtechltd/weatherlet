package com.weatherlet.app.alerts

import com.weatherlet.app.data.HourForecast
import com.weatherlet.app.data.Units
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class CommuteSettings(
    val enabled: Boolean = false,
    /** Minutes since midnight. */
    val morningMin: Int = 8 * 60,
    val eveningMin: Int = 17 * 60 + 30,
)

data class CommuteIssue(val emoji: String, val text: String, val severity: Severity)

data class CommuteLeg(
    val label: String,
    val at: LocalDateTime,
    val temp: Double?,
    val code: Int?,
    val issues: List<CommuteIssue>,
) {
    val clear get() = issues.isEmpty()
    val worst get() = issues.maxByOrNull { it.severity }?.severity
}

/** Looks at the hours around a commute and says what to do about them. */
object CommuteAdvisor {
    private val snowCodes = setOf(71, 73, 75, 77, 85, 86)

    fun leg(label: String, at: LocalDateTime, hours: List<HourForecast>, units: Units): CommuteLeg {
        val start = at.withMinute(0).withSecond(0).withNano(0)
        // The trip itself (this hour and the next)
        val window = hours.filter { it.time == start || it.time == start.plusHours(1) }
        if (window.isEmpty()) return CommuteLeg(label, at, null, null, emptyList())
        // Whether it rained in the 3 hours before (wet roads freeze)
        val before = hours.filter { it.time >= start.minusHours(3) && it.time < start }
        val wetBefore = before.any { it.precipMm >= 0.2 }

        val issues = mutableListOf<CommuteIssue>()
        fun worstHour(sel: (HourForecast) -> Double) = window.maxByOrNull(sel)!!

        val rain = worstHour { it.precipMm }
        when {
            rain.precipMm >= 4 -> issues += CommuteIssue("🌧️", "Heavy rain around ${TimeFmt.time(rain.time)} (${units.rainRate(rain.precipMm)}) — leave 15 min early", Severity.SEVERE)
            rain.precipMm >= 1 && rain.precipProb >= 50 -> issues += CommuteIssue("🌦️", "Rain around ${TimeFmt.time(rain.time)} — take an umbrella", Severity.WARNING)
        }
        window.firstOrNull { it.code in snowCodes }?.let {
            issues += CommuteIssue("❄️", "Snow during your trip — allow extra time", if (it.code in setOf(75, 86)) Severity.SEVERE else Severity.WARNING)
        }
        val coldest = window.minBy { it.temp }
        if (coldest.temp <= 1.0 && (wetBefore || window.any { it.precipMm > 0 || it.code in setOf(56, 57, 66, 67) })) {
            issues += CommuteIssue("🧊", "Icy surfaces likely (${units.temp(coldest.temp)}) — go slowly", Severity.WARNING)
        }
        val gust = worstHour { it.gustKmh }
        when {
            gust.gustKmh >= 70 -> issues += CommuteIssue("💨", "Violent gusts of ${units.wind(gust.gustKmh)} — avoid exposed routes", Severity.SEVERE)
            gust.gustKmh >= 50 -> issues += CommuteIssue("💨", "Gusty: up to ${units.wind(gust.gustKmh)}", Severity.WARNING)
        }
        val fog = window.minBy { it.visibilityM }
        when {
            fog.visibilityM <= 200 -> issues += CommuteIssue("🌫️", "Dense fog (${fog.visibilityM.roundToInt()} m visibility)", Severity.SEVERE)
            fog.visibilityM <= 500 -> issues += CommuteIssue("🌫️", "Fog — visibility ${fog.visibilityM.roundToInt()} m", Severity.WARNING)
        }
        if (window.any { it.code >= 95 }) issues += CommuteIssue("⛈️", "Thunderstorm during your trip", Severity.SEVERE)
        val hot = window.maxBy { it.temp }
        if (hot.temp >= 32) issues += CommuteIssue("🥵", "Hot trip (${units.temp(hot.temp)}) — carry water", Severity.WARNING)

        val first = window.first()
        return CommuteLeg(label, at, first.temp, first.code, issues.sortedByDescending { it.severity })
    }

    /** Upcoming commutes (weekdays only), starting from [now]; [count] legs. */
    fun upcoming(now: LocalDateTime, s: CommuteSettings, hours: List<HourForecast>, units: Units, count: Int = 2): List<CommuteLeg> {
        val out = mutableListOf<CommuteLeg>()
        var day = now.toLocalDate()
        var guard = 0
        while (out.size < count && guard++ < 8) {
            if (isWeekday(day)) {
                for ((label, mins) in listOf("Morning" to s.morningMin, "Evening" to s.eveningMin).sortedBy { it.second }) {
                    val at = day.atTime(LocalTime.of(mins / 60, mins % 60))
                    if (at.isAfter(now.minusMinutes(30)) && out.size < count) out += leg(label, at, hours, units)
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    fun isWeekday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    fun time(mins: Int): String = TimeFmt.minutes(mins)
}

private fun Units.rainRate(mm: Double) =
    if (this == Units.METRIC) "${(mm * 10).roundToInt() / 10.0} mm/h" else String.format(java.util.Locale.US, "%.2f in/h", mm / 25.4)
