package com.weatherlet.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Store
import com.weatherlet.app.data.Units
import com.weatherlet.app.ui.DeepLink
import com.weatherlet.app.ui.MainActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One call refreshes every Weatherlet widget. */
object Widgets {
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        WeatherWidget().updateAll(app)
        NextAlertWidget().updateAll(app)
        HourlyWidget().updateAll(app)
        com.weatherlet.app.tile.WeatherTileService.requestUpdate(app)
    }
}

// ---------------------------------------------------------------------------------------------------
// "Next alert": a small tile that is either calm or shouting.
// ---------------------------------------------------------------------------------------------------

class NextAlertWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(60.dp, 60.dp), DpSize(150.dp, 60.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context)
        val f = store.cachedForecast()
        val alert = f?.let { AlertEngine.evaluate(it.days, it.past, store.profiles, store.sensitivity).firstOrNull() }
        val units = store.units
        provideContent {
            GlanceTheme {
                val ctx = LocalContext.current
                val wide = LocalSize.current.width >= 140.dp
                val (bg, fg) = when (alert?.severity) {
                    Severity.SEVERE -> severeBg to severeFg
                    Severity.WARNING -> warnBg to warnFg
                    null -> okBg to okFg
                }
                Box(
                    GlanceModifier.fillMaxSize().appWidgetBackground().background(bg).cornerRadius(24.dp).padding(10.dp)
                        .clickable(actionStartActivity(if (alert != null) DeepLink.intent(ctx, "alerts") else Intent(ctx, MainActivity::class.java))),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        f == null -> Text("Set a city", style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        alert == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetAllClear(if (wide) 26.dp else 30.dp)
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                if (wide) "All clear · ${units.temp(f.current.temp)}" else "All clear",
                                maxLines = 1,
                                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                        wide -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(alert.type.emoji, style = TextStyle(fontSize = 28.sp))
                            Spacer(GlanceModifier.width(10.dp))
                            Column {
                                Text(
                                    alert.type.title, maxLines = 1,
                                    style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    AlertEngine.range(alert, f.days.first().date) + " · " + f.place.title, maxLines = 1,
                                    style = TextStyle(color = fg, fontSize = 12.sp),
                                )
                            }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(alert.type.emoji, style = TextStyle(fontSize = 24.sp))
                            Text(
                                AlertEngine.range(alert, f.days.first().date), maxLines = 1,
                                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

class NextAlertWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlertWidget()
}

// ---------------------------------------------------------------------------------------------------
// "Hourly": the next few hours at a glance.
// ---------------------------------------------------------------------------------------------------

class HourlyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 100.dp), DpSize(290.dp, 100.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context)
        val f = store.cachedForecast()
        val units = store.units
        provideContent {
            GlanceTheme {
                if (f == null) Root {
                    Text(
                        "Tap to choose your city",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, textAlign = TextAlign.Center),
                    )
                } else HourlyContent(f, units)
            }
        }
    }
}

@Composable
private fun HourlyContent(f: Forecast, units: Units) {
    val wide = LocalSize.current.width >= 260.dp
    val count = if (wide) 6 else 4
    val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    val hours = f.hours.filter { !it.time.isBefore(now) }.take(count)
    val today = f.days.firstOrNull()
    val ctx = LocalContext.current
    Root {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetIcon(f.current.code, f.current.isDay, 26.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    units.temp(f.current.temp),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    f.place.title, maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                if (wide && today != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "H ${units.temp(today.tMax)} · L ${units.temp(today.tMin)}", maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                if (hours.isEmpty()) {
                    Text(
                        "Hourly data will appear after the next refresh",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
                hours.forEachIndexed { i, h ->
                    Column(
                        GlanceModifier.defaultWeight().clickable(actionStartActivity(DeepLink.intent(ctx, "hourly"))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (i == 0) "Now" else com.weatherlet.app.alerts.TimeFmt.hour(h.time.hour),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                        )
                        WidgetIcon(h.code, h.time.hour in 6..19, 28.dp)
                        Text(
                            units.temp(h.temp),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            if (h.precipProb >= 20) "💧${h.precipProb}%" else " ",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 10.sp),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyWidget()
}
