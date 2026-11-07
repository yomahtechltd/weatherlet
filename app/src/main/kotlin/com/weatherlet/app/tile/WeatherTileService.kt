package com.weatherlet.app.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.weatherlet.app.R
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.data.Store
import com.weatherlet.app.data.Wmo
import com.weatherlet.app.ui.DeepLink
import com.weatherlet.app.ui.MainActivity

/**
 * Quick Settings tile: "17° Leeds" with the next alert (or the conditions) underneath. Lit up when
 * there is an alert, so a pull of the shade answers "do I need to worry?". Tap opens the app.
 */
class WeatherTileService : TileService() {
    override fun onStartListening() = refresh(this, qsTile)

    override fun onClick() {
        val store = Store(this)
        val hasAlert = store.cachedForecast()?.let {
            AlertEngine.evaluate(it.days, it.past, store.profiles, store.sensitivity).isNotEmpty()
        } == true
        val intent = if (hasAlert) DeepLink.intent(this, "alerts") else Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Ask the system to re-read the tile (no-op if the user hasn't added it). */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, WeatherTileService::class.java)) }
        }

        fun refresh(ctx: Context, tile: Tile?) {
            tile ?: return
            val store = Store(ctx)
            val f = store.cachedForecast()
            tile.icon = Icon.createWithResource(ctx, R.drawable.ic_stat_weather)
            if (f == null) {
                tile.label = "Weatherlet"
                if (Build.VERSION.SDK_INT >= 29) tile.subtitle = "Tap to choose a city"
                tile.state = Tile.STATE_INACTIVE
            } else {
                val alerts = AlertEngine.evaluate(f.days, f.past, store.profiles, store.sensitivity)
                val first = alerts.firstOrNull()
                tile.label = "${store.units.temp(f.current.temp)} ${f.place.title.substringBefore(" and ").take(9).trim()}"
                val sub = first?.let {
                    "${it.type.emoji} ${it.type.title} · ${AlertEngine.range(it, f.days.first().date)}" +
                        if (alerts.size > 1) " +${alerts.size - 1}" else ""
                } ?: "No alerts · ${Wmo.describe(f.current.code)}"
                if (Build.VERSION.SDK_INT >= 29) tile.subtitle = sub
                tile.state = if (first != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            }
            tile.updateTile()
        }
    }
}
