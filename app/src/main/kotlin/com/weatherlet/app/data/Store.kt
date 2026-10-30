package com.weatherlet.app.data

import android.content.Context
import com.weatherlet.app.alerts.AlertChange
import com.weatherlet.app.alerts.AlertSnapshot
import com.weatherlet.app.alerts.CommuteSettings
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.Sensitivity
import org.json.JSONArray
import org.json.JSONObject

enum class Units { METRIC, IMPERIAL }

class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("weatherlet", Context.MODE_PRIVATE)

    init {
        migrateSinglePlace()
        com.weatherlet.app.alerts.TimeFmt.use24h = sp.getBoolean("use_24h", false)
    }

    /** 12-hour clock by default; true switches every time in the app to 24-hour. */
    var use24h: Boolean
        get() = sp.getBoolean("use_24h", false)
        set(v) {
            sp.edit().putBoolean("use_24h", v).apply()
            com.weatherlet.app.alerts.TimeFmt.use24h = v
        }

    /** v1 stored one city; v2 stores a list. Carry the old city (and its cached forecast) over. */
    private fun migrateSinglePlace() {
        if (sp.contains("places") || !sp.contains("place")) return
        val old = sp.getString("place", null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() } ?: return
        val e = sp.edit()
        e.putString("places", JSONArray().put(old.toJson()).toString()).putString("active", old.key)
        sp.getString("forecast_raw", null)?.let {
            e.putString("fc_${old.key}", it).putLong("ft_${old.key}", sp.getLong("fetched_at", 0))
        }
        e.remove("place").remove("forecast_raw").remove("fetched_at").apply()
    }

    // ---- saved places (index 0 is "home") -------------------------------------------------------

    var places: List<Place>
        get() = runCatching {
            val a = JSONArray(sp.getString("places", "[]"))
            (0 until a.length()).map { Place.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        private set(v) = sp.edit().putString("places", JSONArray(v.map { it.toJson() }).toString()).apply()

    private var activeKey: String?
        get() = sp.getString("active", null)
        set(v) = sp.edit().putString("active", v).apply()

    val place: Place? get() = places.let { l -> l.firstOrNull { it.key == activeKey } ?: l.firstOrNull() }
    val home: Place? get() = places.firstOrNull()

    fun addOrActivate(p: Place) {
        val l = places
        if (l.none { it.key == p.key }) places = l + p
        activeKey = p.key
    }

    fun setActive(p: Place) { activeKey = p.key }

    /** Tap-to-cycle used by the widget. */
    fun cycleActive() {
        val l = places
        if (l.size < 2) return
        val i = l.indexOfFirst { it.key == place?.key }
        activeKey = l[(i + 1) % l.size].key
    }

    fun remove(p: Place) {
        val l = places.filter { it.key != p.key }
        places = l
        if (activeKey == p.key) activeKey = l.firstOrNull()?.key
        dropCaches(p.key)
    }

    private fun dropCaches(key: String) {
        sp.edit().remove("fc_$key").remove("ft_$key").remove("snap_$key")
            .remove("chg_$key").remove("off_$key").apply()
    }

    /** True when a "follow my location" place exists. */
    val followsLocation: Boolean get() = places.any { it.isCurrent }

    /**
     * Record where the user is now. Creates the place if needed; if they've moved more than 5 km,
     * swaps it in place (keeping nickname/position/active state). Returns true if the forecast
     * needs refetching because the location materially changed.
     */
    fun setCurrent(p: Place, makeActive: Boolean = false): Boolean {
        val l = places
        val old = l.firstOrNull { it.isCurrent }
        if (old == null) {
            places = l + p
            if (makeActive || l.isEmpty()) activeKey = p.key
            return true
        }
        val moved = com.weatherlet.app.data.Locator.distanceKm(old, p)
        if (moved < 5.0) {
            // Same spot: only refresh the label (keeps the key and every cache intact)
            if (old.name != p.name || old.countryCode != p.countryCode) {
                places = l.map { if (it.key == old.key) old.copy(name = p.name, admin = p.admin, country = p.country, countryCode = p.countryCode) else it }
            }
            if (makeActive) activeKey = old.key
            return false
        }
        val wasActive = place?.key == old.key
        places = l.map { if (it.key == old.key) p.copy(nickname = old.nickname) else it }
        dropCaches(old.key)
        if (wasActive || makeActive) activeKey = p.key
        return true
    }

    fun rename(p: Place, nickname: String?) {
        places = places.map { if (it.key == p.key) it.copy(nickname = nickname?.trim()?.takeIf { n -> n.isNotEmpty() }) else it }
    }

    fun makeHome(p: Place) {
        places = listOf(p) + places.filter { it.key != p.key }
    }

    // ---- cached forecasts ---------------------------------------------------------------------

    fun cached(p: Place): Forecast? {
        val raw = sp.getString("fc_${p.key}", null) ?: return null
        return runCatching { Forecast.parse(p, raw, sp.getLong("ft_${p.key}", 0)) }.getOrNull()
    }

    fun cachedForecast(): Forecast? = place?.let { cached(it) }

    fun saveRaw(p: Place, raw: String, at: Long) {
        if (places.none { it.key == p.key }) return // removed while fetching
        sp.edit().putString("fc_${p.key}", raw).putLong("ft_${p.key}", at).apply()
    }

    // ---- change tracking ------------------------------------------------------------------------

    fun snapshot(p: Place): AlertSnapshot? =
        sp.getString("snap_${p.key}", null)?.let { runCatching { AlertSnapshot.fromJson(JSONObject(it)) }.getOrNull() }

    fun saveSnapshot(p: Place, s: AlertSnapshot?) {
        sp.edit().putString("snap_${p.key}", s?.toJson()?.toString()).apply()
    }

    /** Changes seen in the last [maxAgeHours]; older ones fall off. */
    fun changes(p: Place, maxAgeHours: Int = 36): List<AlertChange> {
        val cutoff = System.currentTimeMillis() - maxAgeHours * 3_600_000L
        return runCatching {
            val a = JSONArray(sp.getString("chg_${p.key}", "[]"))
            (0 until a.length()).map { AlertChange.fromJson(a.getJSONObject(it)) }.filter { it.at >= cutoff }
        }.getOrDefault(emptyList())
    }

    fun addChanges(p: Place, new: List<AlertChange>) {
        val all = (new + changes(p)).take(12)
        sp.edit().putString("chg_${p.key}", JSONArray(all.map { it.toJson() }).toString()).apply()
    }

    fun clearChanges(p: Place) { sp.edit().remove("chg_${p.key}").apply() }

    /** Dedupe so a forecast that flickers around a threshold doesn't buzz you repeatedly. */
    fun hasNotified(key: String) = sp.getStringSet("notified_keys", emptySet())!!.contains(key)

    fun markNotified(key: String) {
        val set = sp.getStringSet("notified_keys", emptySet())!!.toMutableSet()
        set += key
        // Keys are "<place>|<type>|<date>|<sev>" or "off|<id>"; keep the set small.
        val trimmed = if (set.size > 200) set.toList().takeLast(120).toSet() else set
        sp.edit().putStringSet("notified_keys", trimmed).apply()
    }

    // ---- official warnings ----------------------------------------------------------------------

    fun official(p: Place): List<OfficialWarning> =
        sp.getString("off_${p.key}", null)?.let { OfficialWarning.listFromJson(it) } ?: emptyList()

    fun saveOfficial(p: Place, l: List<OfficialWarning>) {
        sp.edit().putString("off_${p.key}", OfficialWarning.listToJson(l)).apply()
    }

    // ---- settings -------------------------------------------------------------------------------

    var units: Units
        get() = runCatching { Units.valueOf(sp.getString("units", "METRIC")!!) }.getOrDefault(Units.METRIC)
        set(v) = sp.edit().putString("units", v.name).apply()

    var alertsEnabled: Boolean
        get() = sp.getBoolean("alerts_enabled", true)
        set(v) = sp.edit().putBoolean("alerts_enabled", v).apply()

    /** Local hour of day (0-23) after which the daily alert may be sent. */
    var alertHour: Int
        get() = sp.getInt("alert_hour", 7)
        set(v) = sp.edit().putInt("alert_hour", v).apply()

    var lastNotified: String?
        get() = sp.getString("last_notified", null)
        set(v) = sp.edit().putString("last_notified", v).apply()

    var profiles: Set<Profile>
        get() = sp.getStringSet("profiles", emptySet())!!.mapNotNull { runCatching { Profile.valueOf(it) }.getOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("profiles", v.map { it.name }.toSet()).apply()

    var commute: CommuteSettings
        get() = CommuteSettings(
            enabled = sp.getBoolean("commute_on", false),
            morningMin = sp.getInt("commute_am", 8 * 60),
            eveningMin = sp.getInt("commute_pm", 17 * 60 + 30),
        )
        set(v) = sp.edit().putBoolean("commute_on", v.enabled).putInt("commute_am", v.morningMin)
            .putInt("commute_pm", v.eveningMin).apply()

    /** Opt-in: refresh the followed location about hourly, even when the app is closed. */
    var autoLocation: Boolean
        get() = sp.getBoolean("auto_location", false)
        set(v) = sp.edit().putBoolean("auto_location", v).apply()

    var sensitivity: Sensitivity
        get() = runCatching { Sensitivity.valueOf(sp.getString("sensitivity", "NORMAL")!!) }.getOrDefault(Sensitivity.NORMAL)
        set(v) = sp.edit().putString("sensitivity", v.name).apply()

    // ---- quiet hours: no *instant* notifications (changes, corrections, nudges); the daily alert is exempt
    var quietEnabled: Boolean
        get() = sp.getBoolean("quiet_on", true)
        set(v) = sp.edit().putBoolean("quiet_on", v).apply()

    /** Minutes since midnight. Default 22:00–06:00. */
    var quietStart: Int
        get() = sp.getInt("quiet_start", 22 * 60)
        set(v) = sp.edit().putInt("quiet_start", v).apply()

    var quietEnd: Int
        get() = sp.getInt("quiet_end", 6 * 60)
        set(v) = sp.edit().putInt("quiet_end", v).apply()

    fun isQuiet(minutesNow: Int): Boolean {
        if (!quietEnabled) return false
        return com.weatherlet.app.alerts.QuietHours.contains(quietStart, quietEnd, minutesNow)
    }

    /** Android 16+ "Live Update" chip for an active severe alert. */
    var liveUpdates: Boolean
        get() = sp.getBoolean("live_updates", true)
        set(v) = sp.edit().putBoolean("live_updates", v).apply()

    // ---- tickable preparation checklists
    fun isChecked(key: String) = sp.getStringSet("checked", emptySet())!!.contains(key)

    fun setChecked(key: String, on: Boolean) {
        val set = sp.getStringSet("checked", emptySet())!!.toMutableSet()
        if (on) set += key else set -= key
        // Keys end in "|<yyyy-MM-dd>|<n>" — forget ones from well in the past
        val cutoff = java.time.LocalDate.now().minusDays(2).toString()
        set.removeAll { k -> k.split("|").getOrNull(2)?.let { it < cutoff } == true }
        sp.edit().putStringSet("checked", set).apply()
    }

    fun checkedKeys(): Set<String> = sp.getStringSet("checked", emptySet())!!.toSet()

    var bestActivity: String
        get() = sp.getString("best_activity", "RUN") ?: "RUN"
        set(v) = sp.edit().putString("best_activity", v).apply()

    // ---- notification history
    fun history(): List<HistoryEntry> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).map { HistoryEntry.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun addHistory(e: HistoryEntry) {
        val all = (listOf(e) + history()).take(60)
        sp.edit().putString("history", JSONArray(all.map { it.toJson() }).toString()).apply()
    }

    fun clearHistory() { sp.edit().remove("history").apply() }

    // ---- ENSO (El Niño / La Niña) cache
    var enso: EnsoStatus?
        get() = sp.getString("enso", null)?.let { runCatching { EnsoStatus.fromJson(JSONObject(it)) }.getOrNull() }
        set(v) = sp.edit().putString("enso", v?.toJson()?.toString()).apply()

    var ensoAt: Long
        get() = sp.getLong("enso_at", 0)
        set(v) = sp.edit().putLong("enso_at", v).apply()

    /** When we last sent a daily alert, and whether it was built from an old (stale) forecast. */
    var lastAlertSentAt: Long
        get() = sp.getLong("alert_sent_at", 0)
        set(v) = sp.edit().putLong("alert_sent_at", v).apply()

    var lastAlertStale: Boolean
        get() = sp.getBoolean("alert_stale", false)
        set(v) = sp.edit().putBoolean("alert_stale", v).apply()

    var lastNudge: String?
        get() = sp.getString("last_nudge", null)
        set(v) = sp.edit().putString("last_nudge", v).apply()

    /** Has the first-run guide been seen? People who already have a place saved never see it unasked. */
    var introSeen: Boolean
        get() = sp.getBoolean("intro_seen", places.isNotEmpty())
        set(v) = sp.edit().putBoolean("intro_seen", v).apply()

    /** "Rain starting soon" nudges for your current place (checked about every 30 minutes). */
    var rainSoon: Boolean
        get() = sp.getBoolean("rain_soon", true)
        set(v) = sp.edit().putBoolean("rain_soon", v).apply()

    var lastRainSoonAt: Long
        get() = sp.getLong("rain_soon_at", 0)
        set(v) = sp.edit().putLong("rain_soon_at", v).apply()

    var lastRainCheckAt: Long
        get() = sp.getLong("rain_check_at", 0)
        set(v) = sp.edit().putLong("rain_check_at", v).apply()

    /** The place rain-soon watches: where you are if we follow your location, else the active place. */
    val rainPlace: Place? get() = places.firstOrNull { it.isCurrent } ?: place

    // ---- background health: when did Android actually run our jobs?
    var lastSyncAt: Long
        get() = sp.getLong("bg_sync_at", 0)
        set(v) = sp.edit().putLong("bg_sync_at", v).apply()

    /** When the daily alert job was *meant* to run (set when it is scheduled)... */
    var dailyDueAt: Long
        get() = sp.getLong("bg_daily_due", 0)
        set(v) = sp.edit().putLong("bg_daily_due", v).apply()

    /** ...and how late it actually was the last time it ran, in minutes (-1 = never ran). */
    var lastDailyDelayMin: Int
        get() = sp.getInt("bg_daily_delay", -1)
        set(v) = sp.edit().putInt("bg_daily_delay", v).apply()

    var lastDailyRunAt: Long
        get() = sp.getLong("bg_daily_run", 0)
        set(v) = sp.edit().putLong("bg_daily_run", v).apply()

    var lastCommuteKey: String?
        get() = sp.getString("last_commute", null)
        set(v) = sp.edit().putString("last_commute", v).apply()
}

/** One thing we told the user, for the "Alert history" page. */
data class HistoryEntry(val at: Long, val kind: String, val title: String, val text: String) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("k", kind).put("t", title).put("x", text)

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(o.getLong("at"), o.optString("k", "alert"), o.getString("t"), o.optString("x"))
    }
}
