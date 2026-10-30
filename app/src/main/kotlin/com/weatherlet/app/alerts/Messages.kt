package com.weatherlet.app.alerts

import com.weatherlet.app.data.Units
import java.time.LocalDate
import java.time.LocalDateTime

/** Pure text for the daily notification, so it can be unit-tested for every profile. */
object Messages {
    /** [note] is a short "how old is this" line, shown as the notification's sub-text. */
    data class Msg(val title: String, val text: String, val big: String, val severe: Boolean, val note: String? = null)

    /** Plain text for the share sheet. */
    fun share(a: WeatherAlert, city: String, today: LocalDate, units: Units, profiles: Set<Profile>): String =
        "${a.type.emoji} ${a.type.title} ${AlertEngine.range(a, today).let { if (it == "Today" || it == "Tomorrow") it.lowercase() else "on $it" }} in $city — " +
            "${AlertEngine.detail(a, units)}.\n\n" +
            "How to prepare: ${a.type.tip(profiles)}\n\n— via Weatherlet"

    /** [official] is a list of (source, title). */
    fun daily(
        city: String,
        today: LocalDate,
        alerts: List<WeatherAlert>,
        official: List<Pair<String, String>>,
        units: Units,
        profiles: Set<Profile>,
        /** When the forecast was fetched; null means "just now". */
        asOf: LocalDateTime? = null,
        /** True if a refresh was attempted this time and failed. */
        offline: Boolean = false,
        now: LocalDateTime = LocalDateTime.now(),
    ): Msg {
        val first = alerts.firstOrNull()
        val title = when {
            alerts.isEmpty() && official.isNotEmpty() -> "🏛️ ${official.first().second} · $city"
            alerts.isEmpty() -> "All clear in $city"
            alerts.size == 1 -> "${first!!.type.emoji} ${first.type.title} · ${AlertEngine.range(first, today)} · $city"
            else -> "⚠️ ${alerts.size} weather alerts for $city"
        }
        val lines = alerts.map {
            "${it.type.emoji} ${it.type.title} (${AlertEngine.range(it, today)}): ${AlertEngine.detail(it, units)}"
        } + official.map { "🏛️ ${it.first}: ${it.second}" }

        val text = when {
            alerts.isEmpty() && official.isNotEmpty() -> "${official.first().first}. Open the app for details."
            first == null -> "Test notification — no adverse weather expected in the next 5 days."
            alerts.size == 1 -> "${AlertEngine.detail(first, units)}. ${first.type.tip(profiles)}"
            // Several alerts: name them all in the collapsed text instead of only the first
            else -> alerts.joinToString(" · ") { "${it.type.emoji} ${it.type.title} ${AlertEngine.range(it, today)}" }
        }
        val big = when {
            alerts.size > 1 -> (lines + "" + "Top tip: ${first!!.type.tip(profiles)}").joinToString("\n")
            else -> text
        }
        val severe = alerts.any { it.severity == Severity.SEVERE }

        val tier = asOf?.let {
            FreshnessPolicy.classify(java.time.Duration.between(it, now).toMillis())
        } ?: Freshness.FRESH
        val note = when {
            asOf == null -> null
            tier == Freshness.STALE ->
                "Forecast from ${FreshnessPolicy.label(asOf, now)}" + if (offline) " — you appear to be offline" else ""
            tier == Freshness.FRESH && offline -> "Updated ${FreshnessPolicy.label(asOf, now)}"
            else -> null
        }
        val bigWithNote = if (note != null && tier == Freshness.STALE)
            "$big\n\n$note. Open the app when you're online to refresh."
        else big
        return Msg(title, text, bigWithNote, severe, note)
    }
}
