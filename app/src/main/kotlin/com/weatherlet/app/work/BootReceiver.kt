package com.weatherlet.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.weatherlet.app.data.Store
import com.weatherlet.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After a restart (or an app update) put everything back: repaint the widgets from the saved forecast
 * straight away, re-queue the background jobs, and fetch fresh data as soon as there's a connection.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val store = Store(app)
                if (store.places.isEmpty()) return@launch
                // Widgets first, so they never sit blank: this uses the forecast already saved on the phone.
                runCatching { Widgets.updateAll(app) }
                WeatherWorker.schedule(app)
                DailyScheduler.scheduleNext(app)
                CommuteScheduler.scheduleNext(app)
                RainSoonWorker.sync(app)
                if (store.autoLocation) LocationWorker.schedule(app)
                // Catch up on anything missed while the phone was off.
                WorkManager.getInstance(app).enqueueUniqueWork(
                    "boot-refresh", ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(connected).build(),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
