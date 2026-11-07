package com.weatherlet.app.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.weatherlet.app.R
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.AlertType
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.rain
import com.weatherlet.app.alerts.snow
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.alerts.wind
import com.weatherlet.app.data.Store
import com.weatherlet.app.ui.MainActivity
import java.time.LocalDate

/**
 * Android 16+ "Live Update": while any alert is active today, keep one ongoing, promoted
 * notification so the status bar and lock screen show a short chip ("72 km/h"). It disappears when the alert ends.
 * On older Android versions this does nothing.
 */
object LiveAlert {
    private const val ID = 4700
    private const val CH = "live_alert"

    private fun short(t: AlertType) = when (t) {
        AlertType.SNOW -> "Snow"; AlertType.COLD -> "Cold"; AlertType.ICE -> "Ice"; AlertType.RAIN -> "Rain"
        AlertType.FLOOD -> "Flood"; AlertType.WIND -> "Wind"; AlertType.HEAT -> "Heat"; AlertType.SWING -> "Swing"
        AlertType.STORM -> "Storm"; AlertType.FOG -> "Fog"; AlertType.UV -> "UV"; AlertType.AIR -> "Air"
        AlertType.POLLEN -> "Pollen"; AlertType.DUST -> "Dust"; AlertType.PRESSURE -> "Pressure"
    }

    /** The status-bar chip is tiny (about 7 characters), so lead with the number that matters. */
    internal fun chip(a: com.weatherlet.app.alerts.WeatherAlert, units: com.weatherlet.app.data.Units): String {
        val text = when (a.type) {
            AlertType.WIND -> units.wind(a.peak)
            AlertType.COLD, AlertType.ICE, AlertType.HEAT -> units.temp(a.peak)
            AlertType.RAIN, AlertType.FLOOD -> units.rain(a.peak)
            AlertType.SNOW -> units.snow(a.peak)
            else -> short(a.type)
        }
        return text.take(7)
    }

    fun sync(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val store = Store(ctx)
        val allowed = Build.VERSION.SDK_INT >= 36 && store.liveUpdates && store.alertsEnabled &&
            com.weatherlet.app.data.Perms.canNotify(ctx)
        val place = store.place
        val f = if (allowed && place != null) store.cached(place) else null
        if (f == null) {
            nm.cancel(ID)
            return
        }
        val today = LocalDate.now()
        val own = AlertEngine.evaluate(f.days, f.past, store.profiles, store.sensitivity)
            .filter { it.start <= today && today <= it.end }
        val official = store.official(place!!).filter { it.severity == Severity.SEVERE }
        if (own.isEmpty() && official.isEmpty()) {
            nm.cancel(ID)
            return
        }
        val first = own.firstOrNull()
        val title = first?.let {
            val lead = if (it.severity == Severity.SEVERE) "Severe ${it.type.title.lowercase()}" else it.type.title
            "${it.type.emoji} $lead today · ${place.title}"
        }
            ?: "🏛️ ${official.first().title} · ${place.title}"
        val text = first?.let { AlertEngine.detail(it, store.units) } ?: official.first().detail.ifBlank { official.first().source }
        val chip = first?.let { chip(it, store.units) } ?: "Alert"
        if (Build.VERSION.SDK_INT >= 36) post(ctx, nm, title, text, chip)
    }

    @RequiresApi(36)
    private fun post(ctx: Context, nm: NotificationManager, title: String, text: String, chip: String) {
        nm.createNotificationChannel(
            NotificationChannel(CH, "Live severe-weather chip", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A status-bar chip while a severe alert is active"
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setContentIntent(open)
            .setShortCriticalText(chip)
        // Android 16 opts a notification in to "promoted ongoing" with this extra.
        b.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        nm.notify(ID, b.build())
    }
}
