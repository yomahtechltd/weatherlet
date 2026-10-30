package com.weatherlet.app.alerts

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class Freshness { FRESH, STALE, EXPIRED }

/**
 * How old a forecast may be before we change how we talk about it.
 * - FRESH   (≤ 6 h): safe to present normally.
 * - STALE   (6–24 h): still useful for a 5-day outlook, but say how old it is.
 * - EXPIRED (> 24 h): don't make weather claims from it.
 */
object FreshnessPolicy {
    const val FRESH_HOURS = 6L
    const val EXPIRED_HOURS = 24L
    /** Hour-level commute timing goes out of date faster than a daily outlook. */
    const val COMMUTE_MAX_HOURS = 6L
    /** Widget starts showing "Updated …" after this long. */
    const val WIDGET_LABEL_HOURS = 3L

    private const val HOUR_MS = 3_600_000L

    fun classify(ageMs: Long): Freshness = when {
        ageMs < 0 || ageMs <= FRESH_HOURS * HOUR_MS -> Freshness.FRESH
        ageMs <= EXPIRED_HOURS * HOUR_MS -> Freshness.STALE
        else -> Freshness.EXPIRED
    }

    /** "10:10 PM last night", "7:15 AM today", "9:00 AM yesterday", "Tue 2:30 PM". */
    fun label(asOf: LocalDateTime, now: LocalDateTime): String {
        val time = TimeFmt.time(asOf)
        return when (asOf.toLocalDate()) {
            now.toLocalDate() -> "$time today"
            now.toLocalDate().minusDays(1) -> if (asOf.hour >= 18) "$time last night" else "$time yesterday"
            else -> "${asOf.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $time"
        }
    }

    /** Short form for the widget: "10:10 PM" today, otherwise "Tue 10:10 PM". */
    fun shortLabel(asOf: LocalDateTime, now: LocalDateTime): String =
        if (asOf.toLocalDate() == now.toLocalDate()) TimeFmt.time(asOf)
        else "${asOf.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${TimeFmt.time(asOf)}"
}
