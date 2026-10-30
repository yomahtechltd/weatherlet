package com.weatherlet.app.alerts

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Precipitation (mm) that fell in the 15 minutes *ending* at [at] (Open-Meteo's convention). */
data class RainStep(val at: LocalDateTime, val mm: Double)

data class RainOutlook(val now: LocalDateTime, val tempC: Double, val steps: List<RainStep>)

data class RainSoon(val inMinutes: Int, val mmNextHour: Double, val snow: Boolean) {
    /** Light, moderate or heavy — judged on the first hour of the spell. */
    val intensity: String get() = when {
        mmNextHour >= 4.0 -> "Heavy"
        mmNextHour >= 1.5 -> "Moderate"
        else -> "Light"
    }
    val noun: String get() = if (snow) "snow" else "rain"

    fun whenText(): String = if (inMinutes <= 10) "any minute now" else "in about $inMinutes min"

    fun title(place: String) = "${if (snow) "🌨️" else "🌧️"} ${noun.replaceFirstChar { it.uppercase() }} ${whenText()} · $place"

    fun text(): String {
        val amount = if (mmNextHour < 0.95) "a few drops" else "about ${mmNextHour.roundToInt()} mm"
        return "$intensity $noun, $amount in the first hour. " +
            if (snow) "Wrap up and allow extra time." else "Take a coat or umbrella."
    }
}

object RainSoonDetector {
    private const val LOOKAHEAD_MIN = 90
    private const val MIN_STEP_MM = 0.2          // ≈ 0.8 mm/h: more than a passing sprinkle
    private const val MIN_HOUR_MM = 0.6

    /**
     * Rain that is about to start: it's dry right now, and a real spell begins within the next
     * 90 minutes. Returns null if it's already raining, nothing is coming, or it's only a sprinkle.
     */
    fun detect(o: RainOutlook): RainSoon? {
        // The step ending at or before "now" describes what is falling right now.
        val latestPast = o.steps.filter { !it.at.isAfter(o.now) }.maxByOrNull { it.at }
        if (latestPast != null && latestPast.mm >= 0.1) return null

        val future = o.steps.filter { it.at.isAfter(o.now) }.sortedBy { it.at }
        val first = future.firstOrNull { it.mm >= MIN_STEP_MM } ?: return null
        val startsAt = first.at.minusMinutes(15)
        val minutes = Duration.between(o.now, startsAt).toMinutes().toInt().coerceAtLeast(0)
        if (minutes > LOOKAHEAD_MIN) return null

        val hour = future.filter { !it.at.isBefore(first.at) && it.at.isBefore(first.at.plusMinutes(60)) }.sumOf { it.mm }
        if (hour < MIN_HOUR_MM) return null
        return RainSoon((minutes / 5.0).roundToInt() * 5, hour, snow = o.tempC <= 0.5)
    }
}
