package com.weatherlet.app.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.weatherlet.app.alerts.AlertChange
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertSnapshot
import com.weatherlet.app.alerts.ChangeTracker
import com.weatherlet.app.widget.WeatherWidget
import com.weatherlet.app.work.Notifier

object WeatherRepo {
    /**
     * Fetch, validate and cache one place; work out what changed since last time; pull official
     * warnings; fire "something changed" notifications; refresh the widget.
     */
    suspend fun refresh(context: Context, place: Place? = null, updateWidget: Boolean = true): Result<Forecast> = runCatching {
        val app = context.applicationContext
        val store = Store(app)
        val p = place ?: store.place ?: error("No location chosen")
        val raw = OpenMeteo.fetchRaw(p)
        val now = System.currentTimeMillis()
        val forecast = Forecast.parse(p, raw, now) // throws if malformed
        store.saveRaw(p, raw, now)

        // What changed since the last refresh?
        val profiles = store.profiles
        val snap = AlertSnapshot.of(
            p.key, profiles, forecast.days.map { it.date },
            AlertEngine.daily(forecast.days, forecast.past, profiles, store.sensitivity),
            store.sensitivity,
        )
        val changes: List<AlertChange> = store.snapshot(p)?.let { ChangeTracker.diff(it, snap, now) } ?: emptyList()
        store.saveSnapshot(p, snap)
        if (changes.isNotEmpty()) store.addChanges(p, changes)

        // Official warnings are best-effort: a failure keeps whatever we had.
        val official = runCatching { OfficialWarnings.fetch(p) }.getOrNull()
        if (official != null) store.saveOfficial(p, official)

        Notifier.afterRefresh(app, forecast, changes, official.orEmpty())
        com.weatherlet.app.work.LiveAlert.sync(app)
        if (updateWidget) com.weatherlet.app.widget.Widgets.updateAll(app)
        forecast
    }

    /** Refresh every saved place (active one first). Failures are skipped. */
    suspend fun refreshAll(context: Context): List<Forecast> {
        val store = Store(context)
        val active = store.place
        val ordered = store.places.sortedBy { if (it.key == active?.key) 0 else 1 }
        val out = ordered.mapNotNull { refresh(context, it, updateWidget = false).getOrNull() }
        com.weatherlet.app.widget.Widgets.updateAll(context.applicationContext)
        return out
    }
}
