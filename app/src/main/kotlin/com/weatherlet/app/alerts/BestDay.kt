package com.weatherlet.app.alerts

import com.weatherlet.app.alerts.temp
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Units
import kotlin.math.roundToInt

enum class Activity(val label: String, val emoji: String) {
    RUN("Run or cycle", "🏃"),
    LAUNDRY("Washing", "👕"),
    DRIVE("Drive", "🚗"),
    GARDEN("Garden", "🌱"),
    OUTDOORS("BBQ", "🧺"),
}

data class DayScore(val day: DayForecast, val score: Int)

/** Rates each forecast day 0–100 for an everyday activity so people can plan around the weather. */
object BestDay {
    private fun wet(d: DayForecast) = d.precipMm >= 1.0 || d.precipProb >= 50

    fun score(a: Activity, d: DayForecast): Int {
        var s = 100.0
        fun pen(x: Number) { s -= x.toDouble().coerceAtLeast(0.0) }
        val t = d.tMax
        val icy = d.tMin <= 1.0 && (d.precipMm > 0.2 || d.code in setOf(56, 57, 66, 67))
        when (a) {
            Activity.RUN -> {
                if (t < 8) pen((8 - t) * 4)
                if (t > 18) pen((t - 18) * 5)
                pen(d.precipProb * 0.5); pen(d.precipMm * 6)
                if (d.gustKmh > 35) pen((d.gustKmh - 35) * 1.2)
                if (d.snowCm > 0) pen(40)
                if (icy) pen(20)
                if (d.aqiMax > 60) pen((d.aqiMax - 60) * 0.8)
                if (d.uvMax >= 8) pen(8)
                if (d.code >= 95) pen(40)
            }
            Activity.LAUNDRY -> {
                pen(d.precipProb * 0.9); pen(d.precipMm * 10)
                if (t < 6) pen((6 - t) * 6)
                if (d.snowCm > 0) pen(30)
                if (d.dustMax > 80) pen(25)
                if (d.gustKmh in 12.0..40.0) s += 5
            }
            Activity.DRIVE -> {
                if (d.snowCm > 0) pen(30 + d.snowCm * 4)
                if (icy) pen(25)
                if (d.minVisM < 300) pen(50) else if (d.minVisM < 1000) pen(30)
                if (d.gustKmh > 60) pen((d.gustKmh - 60) * 1.5)
                if (d.precipMm > 15) pen(10 + (d.precipMm - 15))
                if (d.code >= 95) pen(30)
            }
            Activity.GARDEN -> {
                if (d.tMin <= 2) pen(40)
                pen(d.precipProb * 0.6); pen(d.precipMm * 5)
                if (d.gustKmh > 35) pen((d.gustKmh - 35))
                if (t > 30) pen((t - 30) * 4)
                if (t < 8) pen((8 - t) * 4)
            }
            Activity.OUTDOORS -> {
                if (t < 18) pen((18 - t) * 6)
                if (t > 28) pen((t - 28) * 6)
                pen(d.precipProb * 0.9); pen(d.precipMm * 8)
                if (d.gustKmh > 30) pen((d.gustKmh - 30) * 1.5)
                if (d.code >= 95) pen(40)
                if (d.snowCm > 0) pen(40)
            }
        }
        return s.coerceIn(0.0, 100.0).roundToInt()
    }

    fun scores(a: Activity, days: List<DayForecast>) = days.map { DayScore(it, score(a, it)) }

    /** Best day (earliest wins ties). */
    fun best(a: Activity, days: List<DayForecast>): DayScore? =
        scores(a, days).maxWithOrNull(compareBy<DayScore> { it.score }.thenByDescending { it.day.date })

    fun verdict(score: Int) = when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Poor"
    }

    /** A few plain facts that explain the score. */
    fun reason(d: DayForecast, units: Units): String {
        val parts = mutableListOf<String>()
        parts += when {
            d.snowCm > 0 -> "Snow likely"
            d.precipProb >= 60 || d.precipMm >= 3 -> "Rain likely"
            d.precipProb >= 30 -> "Chance of showers"
            else -> "Dry"
        }
        parts += units.temp(d.tMax)
        parts += when {
            d.gustKmh >= 60 -> "very windy"
            d.gustKmh >= 40 -> "windy"
            d.gustKmh >= 20 -> "breezy"
            else -> "light wind"
        }
        return parts.joinToString(" · ")
    }
}
