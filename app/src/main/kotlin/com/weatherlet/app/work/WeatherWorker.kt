package com.weatherlet.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.weatherlet.app.alerts.CommuteAdvisor
import com.weatherlet.app.alerts.FreshnessPolicy
import com.weatherlet.app.data.Store
import com.weatherlet.app.data.WeatherRepo
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

internal val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Runs every ~3h: refreshes every saved place + the widget, and posts the daily alert when due. */
class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        if (store.places.isEmpty()) return Result.success()
        store.lastSyncAt = System.currentTimeMillis()
        val ok = WeatherRepo.refreshAll(applicationContext)
        // Even if the fetch failed, a saved forecast can still be used (labelled by its age).
        Notifier.maybeNotifyDaily(applicationContext, refreshFailed = ok.size < store.places.size)
        LiveAlert.sync(applicationContext)
        if (ok.isEmpty()) return if (runAttemptCount < 3) Result.retry() else Result.success()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherWorker>(3, TimeUnit.HOURS).setConstraints(connected).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("weather-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Fires ~45 minutes before each configured commute (weekdays), pulls fresh hourly data for the home
 * place, and warns only if something will actually affect the trip. Reschedules itself.
 */
class CommuteWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        try {
            val store = Store(app)
            val s = store.commute
            val home = store.home
            if (!s.enabled || home == null || !store.alertsEnabled) return Result.success()

            // Best effort: no network just means we fall back to what we have.
            val fresh = WeatherRepo.refresh(app, home).getOrNull()
            val f = fresh ?: store.cached(home)
            val now = LocalDateTime.now()
            val key = "${now.toLocalDate()}-${if (now.hour < 12) "AM" else "PM"}"
            if (store.lastCommuteKey == key) return Result.success()

            val ageHours = f?.let { (System.currentTimeMillis() - it.fetchedAt) / 3_600_000.0 }
            if (f == null || (fresh == null && ageHours!! > FreshnessPolicy.COMMUTE_MAX_HOURS)) {
                // Hour-by-hour timing this old isn't worth quoting.
                Notifier.commuteUnavailable(app, home.title)
                store.lastCommuteKey = key
                return Result.success()
            }

            val legs = CommuteAdvisor.upcoming(now, s, f.hours, store.units, count = 2)
                .filter { it.at.toLocalDate() == now.toLocalDate() && it.at.isBefore(now.plusHours(12)) }
            if (legs.any { !it.clear }) {
                val asOf = if (fresh == null) java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(f.fetchedAt), java.time.ZoneId.systemDefault(),
                ) else null
                Notifier.commute(app, legs, home.title, asOf)
                store.lastCommuteKey = key
            }
            return Result.success()
        } finally {
            CommuteScheduler.scheduleNext(app)
        }
    }
}

/**
 * The daily alert. Fires at the chosen hour whether or not there's a connection: it tries a fresh
 * fetch, and if that fails it falls back to the last saved forecast (worded by how old it is).
 */
class DailyAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        try {
            val store = Store(app)
            val ranAt = System.currentTimeMillis()
            if (store.dailyDueAt > 0) {
                store.lastDailyDelayMin = ((ranAt - store.dailyDueAt) / 60_000L).toInt().coerceAtLeast(0)
                store.lastDailyRunAt = ranAt
            }
            if (!store.alertsEnabled || store.places.isEmpty()) return Result.success()
            val refreshed = WeatherRepo.refreshAll(app)
            val failed = refreshed.size < store.places.size
            Notifier.maybeNotifyDaily(app, refreshFailed = failed)
            LiveAlert.sync(app)
            return Result.success()
        } finally {
            DailyScheduler.scheduleNext(app)
        }
    }
}

object DailyScheduler {
    private const val NAME = "daily-alert"

    /** Queue the next run at the user's alert hour (no network constraint — that's the point). */
    fun scheduleNext(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = Store(context)
        if (!s.alertsEnabled || s.places.isEmpty()) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(s.alertHour, 0)
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        s.dailyDueAt = System.currentTimeMillis() + Duration.between(now, at).toMillis()
        wm.enqueueUniqueWork(
            NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyAlertWorker>()
                .setInitialDelay(Duration.between(now, at))
                .build(),
        )
    }
}

/**
 * Opt-in hourly check of the user's approximate location. Cheap when nothing changed: it only
 * re-fetches the forecast if the user has moved to a different place (>5 km).
 */
class LocationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val store = Store(app)
        if (!store.autoLocation || !store.followsLocation) return Result.success()
        if (!com.weatherlet.app.data.Locator.hasBackgroundPermission(app)) {
            // Permission was revoked in system settings: switch the feature off rather than fail silently.
            store.autoLocation = false
            return Result.success()
        }
        val here = com.weatherlet.app.data.Locator.locate(app) ?: return Result.success()
        if (store.setCurrent(here)) {
            store.places.firstOrNull { it.isCurrent }?.let { WeatherRepo.refresh(app, it) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "location-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<LocationWorker>(1, TimeUnit.HOURS).setConstraints(connected).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

object CommuteScheduler {
    private const val NAME = "commute-check"
    private const val LEAD_MINUTES = 45L

    /** Queue the next weekday pre-commute check (or cancel if commute alerts are off). */
    fun scheduleNext(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = Store(context).commute
        if (!s.enabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val now = LocalDateTime.now()
        var best: LocalDateTime? = null
        for (d in 0..7) {
            val day = now.toLocalDate().plusDays(d.toLong())
            if (!CommuteAdvisor.isWeekday(day)) continue
            for (m in listOf(s.morningMin, s.eveningMin)) {
                val t = day.atTime(m / 60, m % 60).minusMinutes(LEAD_MINUTES)
                if (t.isAfter(now.plusMinutes(1)) && (best == null || t.isBefore(best))) best = t
            }
        }
        val at = best ?: return
        wm.enqueueUniqueWork(
            NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CommuteWorker>()
                .setInitialDelay(Duration.between(now, at))
                .build(),
        )
    }
}

/**
 * "Rain soon": a tiny request every ~30 minutes for the place you're at. Posts at most one nudge per
 * 3 hours, only when it's dry now and a real spell starts within 90 minutes. Android may batch these
 * checks when the phone is dozing, so timing is approximate.
 */
class RainSoonWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val store = Store(app)
        if (!store.rainSoon || !store.alertsEnabled) return Result.success()
        val place = store.rainPlace ?: return Result.success()
        val now = System.currentTimeMillis()
        if (now - store.lastRainSoonAt < RainSoonNudge.COOLDOWN_MS) return Result.success()
        val outlook = runCatching { com.weatherlet.app.data.OpenMeteo.fetchSoon(place) }.getOrNull()
            ?: return Result.success()
        store.lastRainCheckAt = now
        val soon = com.weatherlet.app.alerts.RainSoonDetector.detect(outlook) ?: return Result.success()
        if (Notifier.rainSoon(app, place.title, soon)) store.lastRainSoonAt = now
        return Result.success()
    }

    companion object {
        private const val NAME = "rain-soon"

        fun sync(context: Context) {
            val wm = WorkManager.getInstance(context)
            val s = Store(context)
            if (s.rainSoon && s.alertsEnabled && s.places.isNotEmpty()) {
                val req = PeriodicWorkRequestBuilder<RainSoonWorker>(30, TimeUnit.MINUTES).setConstraints(connected).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } else wm.cancelUniqueWork(NAME)
        }
    }
}

object RainSoonNudge {
    const val COOLDOWN_MS = 3 * 3_600_000L
}
