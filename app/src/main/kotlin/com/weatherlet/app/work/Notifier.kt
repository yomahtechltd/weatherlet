package com.weatherlet.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.weatherlet.app.R
import com.weatherlet.app.alerts.AlertChange
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.ChangeKind
import com.weatherlet.app.alerts.ChangeTracker
import com.weatherlet.app.alerts.CommuteLeg
import com.weatherlet.app.alerts.Freshness
import com.weatherlet.app.alerts.FreshnessPolicy
import com.weatherlet.app.alerts.Messages
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.TimeFmt
import com.weatherlet.app.alerts.tip
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.HistoryEntry
import com.weatherlet.app.data.OfficialWarning
import com.weatherlet.app.data.Store
import com.weatherlet.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime

object Notifier {
    private const val CH_ALERTS = "weather_alerts"
    private const val CH_COMMUTE = "commute"
    private const val CH_STATUS = "forecast_status"
    private const val ID_DAILY = 4200
    private const val ID_COMMUTE = 4500
    private const val ID_NUDGE = 4600
    private const val ID_RAIN = 4800

    private const val RED = 0xFFB3261E.toInt()
    private const val AMBER = 0xFFE08A00.toInt()

    private fun canPost(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Quiet hours suppress *instant* notifications (changes, corrections, nudges); the in-app card still shows them. */
    private fun quietNow(store: Store): Boolean =
        LocalDateTime.now().let { store.isQuiet(it.hour * 60 + it.minute) }

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Weather alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Daily heads-up, forecast changes and official warnings"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_COMMUTE, "Commute alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Weather that will affect your journey to work or home"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Forecast status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet reminders when the forecast couldn't be refreshed"
            }
        )
    }

    private fun post(
        ctx: Context, id: Int, channel: String, title: String, text: String,
        bigText: String = text, color: Int = AMBER, note: String? = null,
        kind: String = "alert", record: Boolean = true,
    ) {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(note)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(
                if (channel == CH_STATUS) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(color)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
        if (record) Store(ctx).addHistory(HistoryEntry(System.currentTimeMillis(), kind, title, listOfNotNull(text, note).joinToString(" · ")))
    }

    private fun idFor(key: String, salt: Int) = salt + (key.hashCode() and 0x7FFF)

    // ---- the once-a-day heads-up ---------------------------------------------------------------

    /**
     * One notification per saved place that has something to say; at most one round per day.
     * Works from the cached forecast when the network is unavailable: [refreshFailed] says a refresh
     * was just attempted and failed, which changes how we word it (see [FreshnessPolicy]).
     */
    fun maybeNotifyDaily(ctx: Context, refreshFailed: Boolean = false) {
        val store = Store(ctx)
        val today = LocalDate.now()
        if (!store.alertsEnabled || !canPost(ctx)) return
        if (LocalDateTime.now().hour < store.alertHour) return
        if (store.lastNotified == today.toString()) return
        var sent = false
        var anyExpired: Forecast? = null
        store.places.forEachIndexed { idx, p ->
            val f = store.cached(p) ?: return@forEachIndexed
            val tier = FreshnessPolicy.classify(System.currentTimeMillis() - f.fetchedAt)
            when {
                // Too old to make weather claims; remember it so we can nudge instead.
                tier == Freshness.EXPIRED -> if (anyExpired == null) anyExpired = f
                dailyFor(ctx, f, ID_DAILY + idx, force = false, offline = refreshFailed) -> {
                    sent = true
                    store.lastAlertSentAt = System.currentTimeMillis()
                    store.lastAlertStale = tier == Freshness.STALE
                }
            }
        }
        if (sent) {
            store.lastNotified = today.toString()
        } else if (anyExpired != null && store.lastNudge != today.toString() && !quietNow(store)) {
            // Don't mark the day as notified: once we're online again the real alert should still go out.
            store.lastNudge = today.toString()
            post(
                ctx, ID_NUDGE, CH_STATUS,
                "Forecast out of date · ${anyExpired!!.place.title}",
                "Couldn't refresh the weather. Connect to the internet and open Weatherlet to see your alerts.",
                note = "Last updated ${FreshnessPolicy.label(
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(anyExpired!!.fetchedAt), java.time.ZoneId.systemDefault()),
                    LocalDateTime.now(),
                )}",
                color = AMBER,
            )
        }
    }

    /** Settings → "Send a test notification": always sends something for the active place. */
    fun sendTest(ctx: Context, f: Forecast) {
        if (!canPost(ctx)) return
        dailyFor(ctx, f, ID_DAILY + 99, force = true, offline = false)
    }

    private fun dailyFor(ctx: Context, f: Forecast, id: Int, force: Boolean, offline: Boolean): Boolean {
        val store = Store(ctx)
        val units = store.units
        val profiles = store.profiles
        val alerts = AlertEngine.evaluate(f.days, f.past, profiles, store.sensitivity)
        val official = store.official(f.place)
        if (alerts.isEmpty() && official.isEmpty() && !force) return false
        val asOf = if (f.fetchedAt > 0)
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(f.fetchedAt), java.time.ZoneId.systemDefault())
        else null
        val msg = Messages.daily(
            city = f.place.title, today = f.days.first().date, alerts = alerts,
            official = official.map { it.source to it.title }, units = units, profiles = profiles,
            asOf = asOf, offline = offline,
        )
        val severe = msg.severe || official.any { it.severity == Severity.SEVERE }
        post(
            ctx, id, CH_ALERTS, msg.title, msg.text, bigText = msg.big, color = if (severe) RED else AMBER,
            note = msg.note, kind = "daily", record = !force,
        )
        return true
    }

    // ---- reacting to a refresh: forecast changes + new official warnings -----------------------

    fun afterRefresh(ctx: Context, f: Forecast, changes: List<AlertChange>, official: List<OfficialWarning>) {
        val store = Store(ctx)
        if (!store.alertsEnabled || !canPost(ctx)) return
        val quiet = quietNow(store)
        val today = f.days.first().date
        val city = f.place.title

        // Only things that got worse, and only if they're near or serious; flicker is deduped.
        val worthy = if (quiet) emptyList() else changes.filter {
            (it.kind == ChangeKind.NEW || it.kind == ChangeKind.UPGRADED) &&
                (it.severity == Severity.SEVERE || !it.start.isAfter(today.plusDays(2)))
        }.filter { c ->
            val key = "${f.place.key}|${c.type}|${c.start}|${c.severity}"
            !store.hasNotified(key).also { seen -> if (!seen) store.markNotified(key) }
        }
        if (worthy.isNotEmpty()) {
            val first = worthy.first()
            val title = if (worthy.size == 1) "${ChangeTracker.symbol(first)} ${ChangeTracker.describe(first, today)}"
            else "⚠️ Forecast worsened for $city"
            val lines = worthy.map { "${ChangeTracker.symbol(it)} ${ChangeTracker.describe(it, today)}" }
            post(
                ctx, idFor(f.place.key, 10_000), CH_ALERTS, title,
                "$city · ${first.type.tip(store.profiles)}",
                bigText = (listOf(city) + lines + first.type.tip(store.profiles)).joinToString("\n"),
                color = if (worthy.any { it.severity == Severity.SEVERE }) RED else AMBER,
                kind = "change",
            )
        }

        // We sent an alert built from an old forecast; the fresh one says it eased or cleared → say so.
        if (!quiet && store.lastAlertStale && System.currentTimeMillis() - store.lastAlertSentAt < 24 * 3_600_000L) {
            val fixes = changes.filter {
                (it.kind == ChangeKind.CLEARED || it.kind == ChangeKind.DOWNGRADED) && !it.start.isAfter(today.plusDays(3))
            }.filter { c ->
                val key = "fix|${f.place.key}|${c.type}|${c.start}|${c.kind}"
                !store.hasNotified(key).also { seen -> if (!seen) store.markNotified(key) }
            }
            if (fixes.isNotEmpty()) {
                val lines = fixes.map { "${ChangeTracker.symbol(it)} ${ChangeTracker.describe(it, today)}" }
                post(
                    ctx, idFor(f.place.key, 20_000), CH_ALERTS,
                    "Update for $city: ${ChangeTracker.describe(fixes.first(), today)}"
                        .let { if (fixes.size > 1) "Update for $city: forecast eased" else it },
                    "Fresh forecast — this changed since your earlier alert.",
                    bigText = (listOf("Fresh forecast for $city — these changed since your earlier alert:") + lines).joinToString("\n"),
                    color = AMBER,
                    kind = "update",
                )
            }
        }

        // Official amber/red/severe warnings: serious enough to break through quiet hours.
        official.filter { it.severity == Severity.SEVERE }.forEach { w ->
            val key = "off|${f.place.key}|${w.id}"
            if (store.hasNotified(key)) return@forEach
            store.markNotified(key)
            post(
                ctx, idFor(key, 100_000), CH_ALERTS,
                "🏛️ ${w.title} · $city", "${w.source}. ${w.detail}".trim(),
                bigText = "${w.source}\n${w.title}\n${w.detail}".trim(), color = RED,
                kind = "official",
            )
        }
    }

    // ---- rain starting soon ---------------------------------------------------------------------

    /** Returns true if a notification was actually posted (not suppressed by quiet hours / permission). */
    fun rainSoon(ctx: Context, place: String, soon: com.weatherlet.app.alerts.RainSoon): Boolean {
        val store = Store(ctx)
        if (!canPost(ctx) || quietNow(store)) return false
        post(
            ctx, ID_RAIN, CH_ALERTS, soon.title(place), soon.text(), kind = "rain-soon",
            color = if (soon.intensity == "Heavy") RED else AMBER,
        )
        return true
    }

    // ---- commute ---------------------------------------------------------------------------------

    /** Hour-level timing is too old to trust: say so instead of guessing. */
    fun commuteUnavailable(ctx: Context, place: String) {
        if (!canPost(ctx)) return
        post(
            ctx, ID_NUDGE + 1, CH_STATUS, "Couldn't check your commute",
            "No recent forecast for $place. Open Weatherlet when you're online.",
        )
    }

    fun commute(ctx: Context, legs: List<CommuteLeg>, place: String, asOf: LocalDateTime? = null) {
        if (!canPost(ctx)) return
        val problems = legs.filter { !it.clear }
        if (problems.isEmpty()) return
        val severe = problems.any { it.worst == Severity.SEVERE }
        val first = problems.first()
        val title = (if (severe) "🚨 Rough commute" else "☂️ Commute heads-up") +
            " · ${first.label} ${TimeFmt.time(first.at)}"
        val lines = problems.flatMap { leg ->
            leg.issues.map { "${leg.label}: ${it.emoji} ${it.text}" }
        }
        post(
            ctx, ID_COMMUTE, CH_COMMUTE, title, lines.first(), kind = "commute",
            bigText = (lines + "📍 $place" + listOfNotNull(asOf?.let {
                "As of ${FreshnessPolicy.label(it, LocalDateTime.now())} — you appear to be offline"
            })).joinToString("\n"),
            color = if (severe) RED else AMBER,
            note = asOf?.let { "As of ${FreshnessPolicy.label(it, LocalDateTime.now())} · offline" },
        )
    }
}
