package com.weatherlet.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as SolidColor
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.Checklist
import com.weatherlet.app.alerts.FreshnessPolicy
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.WeatherAlert
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Store
import com.weatherlet.app.data.Units
import com.weatherlet.app.data.Wmo
import com.weatherlet.app.ui.DeepLink
import com.weatherlet.app.ui.MainActivity

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val WIDE_TALLER = DpSize(250.dp, 150.dp)
private val TALL = DpSize(250.dp, 220.dp)

internal val warnBg = ColorProvider(day = Color(0xFFFFE2B8), night = Color(0xFF5A3B00))
internal val warnFg = ColorProvider(day = Color(0xFF3B2600), night = Color(0xFFFFDDB0))
internal val severeBg = ColorProvider(day = Color(0xFFFFD6D2), night = Color(0xFF6B1A12))
internal val severeFg = ColorProvider(day = Color(0xFF410002), night = Color(0xFFFFDAD6))
internal val okBg = ColorProvider(day = Color(0xFFD7F0D9), night = Color(0xFF16391C))
internal val okFg = ColorProvider(day = Color(0xFF0B2A10), night = Color(0xFFBFE8C3))

class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, WIDE_TALLER, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context)
        val forecast = store.cachedForecast()
        val units = store.units
        val profiles = store.profiles
        val alerts = forecast?.let { AlertEngine.evaluate(it.days, it.past, profiles, store.sensitivity) }.orEmpty()
        // Other saved places that currently have something to flag
        val others = store.places.filter { it.key != forecast?.place?.key }.mapNotNull { p ->
            val f = store.cached(p) ?: return@mapNotNull null
            val top = AlertEngine.evaluate(f.days, f.past, profiles, store.sensitivity).firstOrNull() ?: return@mapNotNull null
            Triple(p.title, top, f.days.first().date)
        }
        val checked = store.checkedKeys()
        val todo = forecast?.let { f ->
            alerts.firstOrNull()?.let { a ->
                Checklist.items(a.type, profiles).mapIndexedNotNull { i, label ->
                    val key = Checklist.key(f.place.key, a, i)
                    if (key in checked) null else key to label
                }.take(2)
            }
        }.orEmpty()
        val canCycle = store.places.size > 1
        // Only call out the age once it matters (offline for a while); fresh data stays uncluttered.
        val updated = forecast?.takeIf {
            it.fetchedAt > 0 && System.currentTimeMillis() - it.fetchedAt > FreshnessPolicy.WIDGET_LABEL_HOURS * 3_600_000L
        }?.let {
            FreshnessPolicy.shortLabel(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.fetchedAt), ZoneId.systemDefault()),
                LocalDateTime.now(),
            )
        }
        provideContent {
            GlanceTheme {
                if (forecast == null) Empty() else Content(forecast, units, alerts, others, canCycle, updated, todo)
            }
        }
    }
}

/** Ticks off a preparation task straight from the widget. */
class CheckItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[KEY]?.let { Store(context).setChecked(it, true) }
        Widgets.updateAll(context)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("task")
    }
}

/** Tapping the city name on the widget flips to the next saved place. */
class CyclePlaceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store(context).cycleActive()
        Widgets.updateAll(context)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

@Composable
internal fun Root(content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        content = { content() },
    )
}

@Composable
private fun Empty() = Root {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌤️", style = TextStyle(fontSize = 30.sp))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "Tap to choose your city",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun Content(
    f: Forecast, units: Units, alerts: List<WeatherAlert>,
    others: List<Triple<String, WeatherAlert, java.time.LocalDate>>, canCycle: Boolean, updated: String?,
    todo: List<Pair<String, String>>,
) {
    val size = LocalSize.current
    if (f.days.isEmpty()) return
    val cityMod = if (canCycle) GlanceModifier.clickable(actionRunCallback(CyclePlaceAction::class.java)) else GlanceModifier
    Root {
        when {
            size.height >= 200.dp -> Tall(f, units, alerts, others, cityMod, updated, todo)
            size.width >= 200.dp -> Wide(f, units, alerts, cityMod, updated)
            else -> Small(f, units, alerts, cityMod, updated)
        }
    }
}

@Composable
private fun Small(f: Forecast, units: Units, alerts: List<WeatherAlert>, cityMod: GlanceModifier, updated: String?) {
    val c = f.current
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            f.place.title + (updated?.let { " · 🕒$it" } ?: ""),
            modifier = cityMod,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                units.temp(c.temp),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.defaultWeight())
            WidgetIcon(c.code, c.isDay, 34.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        AlertChip(alerts.firstOrNull(), alerts.size, f.days.first().date, compact = true)
    }
}

@Composable
private fun Wide(f: Forecast, units: Units, alerts: List<WeatherAlert>, cityMod: GlanceModifier, updated: String?) {
    val c = f.current
    val today = f.days.first().date
    // Enough height → status chip on its own row so a long town name is never squeezed.
    val roomy = LocalSize.current.height >= 150.dp
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetIcon(c.code, c.isDay, 32.dp)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                units.temp(c.temp),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    f.place.title, maxLines = 1, modifier = cityMod,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    Wmo.describe(c.code) + (updated?.let { " · 🕒$it" } ?: ""), maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            if (!roomy) {
                Spacer(GlanceModifier.width(6.dp))
                AlertChip(alerts.firstOrNull(), alerts.size, today, compact = true, iconOnly = true)
            }
        }
        if (roomy) {
            Spacer(GlanceModifier.height(8.dp))
            AlertChip(alerts.firstOrNull(), alerts.size, today, compact = false)
        }
        Spacer(GlanceModifier.defaultWeight())
        DayStrip(f, units, alerts)
    }
}

@Composable
private fun Tall(
    f: Forecast, units: Units, alerts: List<WeatherAlert>,
    others: List<Triple<String, WeatherAlert, java.time.LocalDate>>, cityMod: GlanceModifier, updated: String?,
    todo: List<Pair<String, String>>,
) {
    val c = f.current
    val today = f.days.first().date
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    f.place.title, maxLines = 1, modifier = cityMod,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    units.temp(c.temp),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 44.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    Wmo.describe(c.code) + (updated?.let { " · Updated $it" } ?: ""),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            WidgetIcon(c.code, c.isDay, 56.dp)
        }
        Spacer(GlanceModifier.height(10.dp))
        if (alerts.isEmpty()) {
            AlertChip(null, 0, today, compact = false)
        } else {
            alerts.take(2).forEach {
                AlertChip(it, alerts.size, today, compact = false)
                Spacer(GlanceModifier.height(6.dp))
            }
            if (alerts.size > 2) {
                Text(
                    "+${alerts.size - 2} more in the app",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
        todo.forEach { (key, label) ->
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "☐ $label",
                maxLines = 1,
                modifier = GlanceModifier.clickable(
                    actionRunCallback(CheckItemAction::class.java, actionParametersOf(CheckItemAction.KEY to key))
                ),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
            )
        }
        others.firstOrNull()?.let { (name, a, day0) ->
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "📍 $name: ${a.type.emoji} ${a.type.title} · ${AlertEngine.range(a, day0)}",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        DayStrip(f, units, alerts)
    }
}

@Composable
private fun AlertChip(
    alert: WeatherAlert?, total: Int, today: java.time.LocalDate, compact: Boolean, iconOnly: Boolean = false,
) {
    val (bg, fg) = when (alert?.severity) {
        null -> okBg to okFg
        Severity.WARNING -> warnBg to warnFg
        Severity.SEVERE -> severeBg to severeFg
    }
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier.background(bg).cornerRadius(14.dp)
            .padding(horizontal = if (iconOnly) 6.dp else 10.dp, vertical = if (iconOnly) 4.dp else 5.dp)
            .clickable(actionStartActivity(DeepLink.intent(ctx, "alerts"))),
    ) {
        if (iconOnly) {
            // Tight spaces: just the mark, so the town name keeps the width
            if (alert == null) WidgetAllClear(18.dp)
            else Text(
                alert.type.emoji + if (total > 1) "+${total - 1}" else "", maxLines = 1,
                style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        } else if (alert == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetAllClear(16.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (compact) "All clear" else "No alerts in the next 5 days", maxLines = 1,
                    style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        } else {
            val label = when {
                compact && total > 1 -> "${alert.type.emoji} ${alert.type.title} +${total - 1}"
                compact -> "${alert.type.emoji} ${alert.type.title}"
                else -> "${alert.type.emoji} ${alert.type.title} · ${AlertEngine.range(alert, today)}"
            }
            Text(
                label, maxLines = 1,
                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** The 5 days *after* today, with a coloured dot under any day that has an alert. */
@Composable
private fun DayStrip(f: Forecast, units: Units, alerts: List<WeatherAlert>) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth()) {
        f.days.drop(1).take(5).forEachIndexed { i, d ->
            val sev = alerts.filter { d.date in it.start..it.end }.maxByOrNull { it.severity }?.severity
            Column(
                GlanceModifier.defaultWeight().clickable(actionStartActivity(DeepLink.intent(ctx, "day:${i + 1}"))),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
                WidgetIcon(d.code, true, 24.dp)
                Text(
                    units.temp(d.tMax),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    units.temp(d.tMin),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
                Spacer(GlanceModifier.height(3.dp))
                val dot = when (sev) {
                    Severity.SEVERE -> SolidColor(Color(0xFFD93025))
                    Severity.WARNING -> SolidColor(Color(0xFFF29900))
                    null -> SolidColor(Color.Transparent)
                }
                Box(GlanceModifier.size(6.dp).background(dot).cornerRadius(3.dp)) {}
            }
        }
    }
}
