package com.weatherlet.app.alerts

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class ChangeKind { NEW, UPGRADED, DOWNGRADED, CLEARED }

data class AlertChange(
    val kind: ChangeKind,
    val type: AlertType,
    /** Severity after the change (before it, for CLEARED). */
    val severity: Severity,
    val start: LocalDate,
    val end: LocalDate,
    val at: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("k", kind.name).put("t", type.name).put("s", severity.name)
        .put("a", start.toString()).put("b", end.toString()).put("at", at)

    companion object {
        fun fromJson(o: JSONObject) = AlertChange(
            ChangeKind.valueOf(o.getString("k")), AlertType.valueOf(o.getString("t")),
            Severity.valueOf(o.getString("s")), LocalDate.parse(o.getString("a")),
            LocalDate.parse(o.getString("b")), o.getLong("at"),
        )
    }
}

/** What the alert picture looked like at the last refresh — the baseline for "what changed". */
data class AlertSnapshot(
    val placeKey: String,
    /** Profiles in effect; a different set means different thresholds, so no diff is meaningful. */
    val signature: String,
    val dates: List<LocalDate>,
    val severities: Map<AlertType, List<Severity?>>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("p", placeKey).put("sig", signature)
        .put("d", JSONArray(dates.map { it.toString() }))
        .put("s", JSONObject().also { o ->
            severities.forEach { (t, l) -> o.put(t.name, JSONArray(l.map { it?.ordinal ?: -1 })) }
        })

    companion object {
        fun fromJson(o: JSONObject): AlertSnapshot {
            val d = o.getJSONArray("d")
            val s = o.getJSONObject("s")
            return AlertSnapshot(
                o.getString("p"), o.getString("sig"),
                (0 until d.length()).map { LocalDate.parse(d.getString(it)) },
                s.keys().asSequence().mapNotNull { k ->
                    runCatching { AlertType.valueOf(k) }.getOrNull()?.let { t ->
                        val a = s.getJSONArray(k)
                        t to (0 until a.length()).map { i -> a.getInt(i).takeIf { it >= 0 }?.let { Severity.entries[it] } }
                    }
                }.toMap(),
            )
        }

        fun of(
            placeKey: String, profiles: Set<Profile>, days: List<LocalDate>, sev: Map<AlertType, List<Severity?>>,
            sensitivity: Sensitivity = Sensitivity.NORMAL,
        ) = AlertSnapshot(
            placeKey,
            profiles.map { it.name }.sorted().joinToString(",") +
                if (sensitivity == Sensitivity.NORMAL) "" else "|${sensitivity.name}",
            days, sev,
        )
    }
}

object ChangeTracker {
    /**
     * Compare two snapshots over the days both cover. A day that has only just entered the 5-day
     * window is not a "change" — we simply couldn't see it before.
     */
    fun diff(prev: AlertSnapshot, now: AlertSnapshot, at: Long): List<AlertChange> {
        if (prev.placeKey != now.placeKey || prev.signature != now.signature) return emptyList()
        data class Hit(val kind: ChangeKind, val type: AlertType, val sev: Severity, val date: LocalDate)
        val hits = mutableListOf<Hit>()
        val common = now.dates.filter { it in prev.dates }
        for (type in AlertType.entries) {
            val p = prev.severities[type]
            val n = now.severities[type]
            for (date in common) {
                val before = p?.getOrNull(prev.dates.indexOf(date))
                val after = n?.getOrNull(now.dates.indexOf(date))
                when {
                    before == null && after != null -> hits += Hit(ChangeKind.NEW, type, after, date)
                    before != null && after == null -> hits += Hit(ChangeKind.CLEARED, type, before, date)
                    before != null && after != null && after > before -> hits += Hit(ChangeKind.UPGRADED, type, after, date)
                    before != null && after != null && after < before -> hits += Hit(ChangeKind.DOWNGRADED, type, after, date)
                }
            }
        }
        return hits.groupBy { Triple(it.kind, it.type, it.sev) }.map { (k, v) ->
            AlertChange(k.first, k.second, k.third, v.minOf { it.date }, v.maxOf { it.date }, at)
        }.sortedWith(compareByDescending<AlertChange> { it.severity }.thenBy { it.start })
    }

    fun describe(c: AlertChange, today: LocalDate): String {
        val name = c.type.title
        val `when` = AlertEngine.range(c.start, c.end, today)
        return when (c.kind) {
            ChangeKind.NEW -> "New: $name · $`when`"
            ChangeKind.UPGRADED -> "$name upgraded to ${if (c.severity == Severity.SEVERE) "severe" else "warning"} · $`when`"
            ChangeKind.DOWNGRADED -> "$name eased to a warning · $`when`"
            ChangeKind.CLEARED -> "$name has cleared · $`when`"
        }
    }

    fun symbol(c: AlertChange) = when (c.kind) {
        ChangeKind.NEW -> "🆕"
        ChangeKind.UPGRADED -> "⬆️"
        ChangeKind.DOWNGRADED -> "⬇️"
        ChangeKind.CLEARED -> "✅"
    }
}
