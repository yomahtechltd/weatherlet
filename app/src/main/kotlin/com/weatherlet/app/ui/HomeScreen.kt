package com.weatherlet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import com.weatherlet.app.alerts.Activity
import com.weatherlet.app.alerts.BestDay
import com.weatherlet.app.alerts.Checklist
import com.weatherlet.app.alerts.DayScore
import com.weatherlet.app.alerts.Messages
import com.weatherlet.app.data.Enso
import com.weatherlet.app.data.EnsoPhase
import com.weatherlet.app.data.EnsoStatus
import com.weatherlet.app.icons.AllClearIcon
import com.weatherlet.app.icons.SkyAnimation
import com.weatherlet.app.icons.WeatherIcon
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.Severity
import com.weatherlet.app.alerts.WeatherAlert
import com.weatherlet.app.alerts.AlertChange
import com.weatherlet.app.alerts.ChangeTracker
import com.weatherlet.app.alerts.CommuteLeg
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.tip
import com.weatherlet.app.data.OfficialWarning
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.TextButton
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.alerts.wind
import com.weatherlet.app.alerts.rain
import com.weatherlet.app.alerts.snow
import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Units
import com.weatherlet.app.data.Wmo
import java.util.concurrent.TimeUnit

private fun skyGradient(code: Int, isDay: Boolean): List<Color> = when {
    !isDay -> listOf(Color(0xFF0F1B3D), Color(0xFF34457A))
    code in 95..99 -> listOf(Color(0xFF2A2A3D), Color(0xFF5A5A85))
    code in setOf(71, 73, 75, 77, 85, 86) -> listOf(Color(0xFF6C86A3), Color(0xFFA9C0D8))
    code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) -> listOf(Color(0xFF2F4858), Color(0xFF55778E))
    code in setOf(45, 48) -> listOf(Color(0xFF6E7B85), Color(0xFF9AA7B1))
    code in 2..3 -> listOf(Color(0xFF4A6A8F), Color(0xFF86A5C6))
    else -> listOf(Color(0xFF2D7DD2), Color(0xFF6FB1FC))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onRefresh: () -> Unit,
    onPlaces: () -> Unit,
    onSettings: () -> Unit,
    onDismissChanges: () -> Unit,
    onToggleCheck: (String) -> Unit,
    onBestActivity: (Activity) -> Unit,
    target: String? = null,
    onTargetHandled: () -> Unit = {},
    onHourly: () -> Unit = {},
) {
    val f = state.forecast
    val ctx = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var expandedDay by rememberSaveable { mutableStateOf<String?>(null) }

    // Widget taps: jump to the alerts, or open that day's details.
    androidx.compose.runtime.LaunchedEffect(target, f) {
        if (target == null || f == null) return@LaunchedEffect
        val alertCount = AlertEngine.evaluate(f.days, f.past, state.profiles, state.sensitivity).size
        // header(0) hero(1) [changes] [official…] → "Alerts" heading
        val alertsAt = 2 + (if (state.changes.isNotEmpty()) 1 else 0) + state.official.size
        val forecastHeadingAt = alertsAt + 1 + maxOf(1, alertCount) +
            (if (state.commute.enabled && state.commuteLegs.isNotEmpty()) 2 else 0)
        val day = DeepLink.dayIndex(target)
        when {
            target == "alerts" -> listState.animateScrollToItem(alertsAt)
            day != null && day in f.days.indices -> {
                expandedDay = f.days[day].date.toString()
                listState.animateScrollToItem(forecastHeadingAt + 1)
            }
        }
        if (target != "hourly") onTargetHandled()
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.statusBarsPadding(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onPlaces,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 16.sp)
                            Text(
                                state.place?.title ?: "",
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(Icons.Default.KeyboardArrowDown, "Your places")
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            }

            if (f == null) {
                item {
                    Text(
                        state.error ?: "Loading forecast…",
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val alerts = AlertEngine.evaluate(f.days, f.past, state.profiles, state.sensitivity)
                val today = f.days.first().date
                item { Hero(f, state.units, state.error, onHourly) }
                if (state.changes.isNotEmpty()) item { ChangesCard(state.changes, today, onDismissChanges) }
                state.official.forEach { w -> item { OfficialCard(w) } }
                item {
                    Text(
                        if (alerts.isEmpty()) "Alerts" else "Heads-up · next 5 days",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (alerts.isEmpty()) item { AllClear(state.officialSupported && state.official.isNotEmpty()) }
                else alerts.forEach { a ->
                    item(key = "${a.type}-${a.start}") {
                        AlertCard(
                            a, today, state.units, state.profiles,
                            placeKey = f.place.key, checked = state.checked, onToggle = onToggleCheck,
                            onShare = {
                                val text = Messages.share(a, f.place.title, today, state.units, state.profiles)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Share this alert").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                        )
                    }
                }
                if (state.commute.enabled && state.commuteLegs.isNotEmpty()) {
                    item {
                        Text("Your commute", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                    item { CommuteCard(state.commuteLegs, state.units) }
                }
                item {
                    Text("Forecast", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                item { DayList(f, alerts, state.units, expandedDay) { expandedDay = if (expandedDay == it) null else it } }
                item {
                    Text("Plan your week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                item { BestDayCard(f, state.bestActivity, state.units, onBestActivity) }
                state.enso?.let { e ->
                    item { EnsoCard(e, f.place.lat, f.place.lon) }
                }
                item {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun Hero(f: Forecast, units: Units, note: String?, onClick: () -> Unit) {
    val c = f.current
    val today = f.days.first()
    Box(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(skyGradient(c.code, c.isDay)))
            .clickable(onClick = onClick),
    ) {
        SkyAnimation(c.code, c.isDay, Modifier.matchParentSize())
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(f.place.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text(
                        note ?: updatedAgo(f.fetchedAt),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
                WeatherIcon(c.code, c.isDay, 76.dp)
            }
            Text(
                units.temp(c.temp),
                color = Color.White,
                fontSize = 92.sp,
                fontWeight = FontWeight.Light,
            )
            Text(Wmo.describe(c.code), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Feels like", units.temp(c.feels), Modifier.weight(1f))
                Stat("Wind", units.wind(c.windKmh), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Humidity", "${c.humidity}%", Modifier.weight(1f))
                Stat("High / Low", "${units.temp(today.tMax)} / ${units.temp(today.tMin)}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AllClear(hasOfficial: Boolean) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AllClearIcon(40.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text("All clear", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    if (hasOfficial) "Our model-based alerts are clear, but see the official warning above."
                    else "No adverse weather expected in the next 5 days — no snow, ice, flooding, high winds, extreme temperatures or poor air.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    a: WeatherAlert, today: java.time.LocalDate, units: Units, profiles: Set<Profile>,
    placeKey: String, checked: Set<String>, onToggle: (String) -> Unit, onShare: () -> Unit,
) {
    val p = alertPalette(a.severity)
    val items = remember(a.type, profiles) { Checklist.items(a.type, profiles) }
    val keys = items.indices.map { Checklist.key(placeKey, a, it) }
    val done = keys.count { it in checked }
    var open by rememberSaveable("${a.type}-${a.start}") { mutableStateOf(false) }

    Surface(shape = MaterialTheme.shapes.large, color = p.container) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row {
                Text(a.type.emoji, fontSize = 34.sp)
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.type.title, style = MaterialTheme.typography.titleMedium, color = p.onContainer, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (a.severity == Severity.SEVERE) "SEVERE" else "WARNING",
                            modifier = Modifier.padding(start = 10.dp)
                                .clip(CircleShape).background(p.accent.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            color = p.onContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                        )
                    }
                    Text(
                        "${AlertEngine.range(a, today)} · ${AlertEngine.detail(a, units)}",
                        style = MaterialTheme.typography.bodyLarge, color = p.onContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        a.type.tip(profiles),
                        style = MaterialTheme.typography.bodyMedium, color = p.onContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = !open }) {
                    Text(
                        "Prepare · $done/${items.size} ${if (open) "▴" else "▾"}",
                        color = p.onContainer, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share this alert", tint = p.onContainer) }
            }
            if (open) {
                items.forEachIndexed { idx, label ->
                    val key = keys[idx]
                    val on = key in checked
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(key) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on, onCheckedChange = { onToggle(key) },
                            colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.onContainer, checkmarkColor = p.container),
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium, color = p.onContainer,
                            textDecoration = if (on) TextDecoration.LineThrough else null,
                            modifier = Modifier.alpha(if (on) 0.6f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayList(f: Forecast, alerts: List<WeatherAlert>, units: Units, expanded: String?, onToggle: (String) -> Unit) {
    val lo = f.days.minOf { it.tMin }
    val hi = f.days.maxOf { it.tMax }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Daily low and high · 💧 chance of rain or snow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            f.days.forEach { d ->
                DayRow(d, f.days.first().date, lo, hi, alerts.filter { d.date in it.start..it.end }, units, d.date.toString() == expanded) { onToggle(d.date.toString()) }
            }
        }
    }
}

/**
 * One line per day, left to right: day + conditions (+ alert chip), icon with chance of precipitation
 * underneath, then the low, a bar showing where the day sits in the week's range, and the high.
 */
@Composable
private fun DayRow(
    d: DayForecast, today: java.time.LocalDate, lo: Double, hi: Double, alerts: List<WeatherAlert>, units: Units,
    expanded: Boolean, onClick: () -> Unit,
) {
    val span = (hi - lo).coerceAtLeast(1.0)
    val start = ((d.tMin - lo) / span).toFloat()
    val end = ((d.tMax - lo) / span).toFloat()
    val trackWidth = 56.dp
    Column(Modifier.clickable(onClick = onClick)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(AlertEngine.dayName(d.date, today), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                Wmo.describe(d.code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (alerts.isNotEmpty()) {
                val worst = alerts.maxOf { it.severity }
                val pal = alertPalette(worst)
                Text(
                    alerts.take(2).joinToString(" · ") { "${it.type.emoji} ${it.type.title}" } +
                        if (alerts.size > 2) " +${alerts.size - 2}" else "",
                    modifier = Modifier.padding(top = 4.dp)
                        .clip(CircleShape).background(pal.container)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = pal.onContainer,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherIcon(d.code, true, 30.dp)
            Text(
                if (d.precipProb >= 20) "💧${d.precipProb}%" else " ",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            units.temp(d.tMin), Modifier.width(34.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Box(
            Modifier.padding(horizontal = 6.dp).width(trackWidth).height(6.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier.offset(x = trackWidth * start)
                    .width((trackWidth * (end - start)).coerceAtLeast(8.dp))
                    .height(6.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(units.temp(d.tMax), Modifier.width(34.dp), fontWeight = FontWeight.SemiBold)
    }
    androidx.compose.animation.AnimatedVisibility(expanded) { DayDetails(d, units) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetails(d: DayForecast, units: Units) {
    val items = buildList {
        add("${com.weatherlet.app.alerts.Emoji.cold} Feels as low as ${units.temp(d.feelsMin)}")
        add("💨 Wind ${units.wind(d.windKmh)}, gusts ${units.wind(d.gustKmh)}")
        if (d.precipMm >= 0.1) add("🌧️ ${units.rain(d.precipMm)} of rain or melt")
        if (d.snowCm >= 0.1) add("❄️ ${units.snow(d.snowCm)} of snow")
        if (d.uvMax >= 1) add("🕶️ UV index ${d.uvMax.toInt()}")
        if (d.aqiMax > 0) add("😷 Air quality ${d.aqiMax}")
        if (d.minVisM < 5000) add("🌫️ Visibility down to ${d.minVisM.toInt()} m")
    }
    FlowRow(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach {
            Text(
                it, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private fun updatedAgo(ts: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - ts)
    return when {
        ts == 0L -> ""
        mins < 1 -> "Updated just now"
        mins < 60 -> "Updated $mins min ago"
        else -> "Updated ${mins / 60} h ago"
    }
}

@Composable
private fun ChangesCard(changes: List<AlertChange>, today: java.time.LocalDate, onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "What changed",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            changes.take(5).forEach { c ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ChangeTracker.symbol(c), fontSize = 16.sp)
                    Text(
                        ChangeTracker.describe(c, today),
                        Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OfficialCard(w: OfficialWarning) {
    val p = alertPalette(w.severity)
    val uri = LocalUriHandler.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = p.container,
        onClick = { w.url?.let { runCatching { uri.openUri(it) } } },
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("🏛️", fontSize = 30.sp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    "${w.source} · official",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.onContainer.copy(alpha = 0.8f),
                )
                Text(w.title, style = MaterialTheme.typography.titleMedium, color = p.onContainer, fontWeight = FontWeight.SemiBold)
                if (w.detail.isNotBlank()) {
                    Text(
                        w.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.onContainer,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommuteCard(legs: List<CommuteLeg>, units: Units) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            legs.forEach { leg ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                        when {
                            leg.worst == com.weatherlet.app.alerts.Severity.SEVERE -> Text("🚨", fontSize = 24.sp)
                            leg.worst != null -> Text("☂️", fontSize = 24.sp)
                            else -> AllClearIcon(26.dp)
                        }
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${leg.label} · ${leg.at.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${com.weatherlet.app.alerts.TimeFmt.time(leg.at)}" + (leg.temp?.let { " · ${units.temp(it)}" } ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            leg.code?.let { WeatherIcon(it, leg.at.hour in 6..19, 22.dp, Modifier.padding(start = 6.dp)) }
                        }
                        if (leg.clear) {
                            Text(
                                if (leg.temp == null) "Forecast not available for this hour yet" else "Clear run — nothing to worry about",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else leg.issues.forEach {
                            Text(
                                "${it.emoji} ${it.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestDayCard(f: Forecast, selected: Activity, units: Units, onSelect: (Activity) -> Unit) {
    val scores = remember(f, selected) { BestDay.scores(selected, f.days) }
    val best = remember(scores) { scores.maxWithOrNull(compareBy<DayScore> { it.score }.thenByDescending { it.day.date }) }
    val today = f.days.first().date
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Best day to…", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Activity.entries.forEach { a ->
                    FilterChip(
                        selected = a == selected, onClick = { onSelect(a) },
                        label = { Text("${a.emoji} ${a.label}") },
                    )
                }
            }
            if (best != null && best.score >= 40) {
                Text(
                    "${AlertEngine.dayName(best.day.date, today)} · ${BestDay.verdict(best.score)}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    BestDay.reason(best.day, units),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "No good day for this in the next 5 days",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Weather looks unsuitable — check back as the forecast updates.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A tiny bar chart so the whole week can be compared at a glance
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                scores.forEach { s ->
                    val isBest = s == best && s.score >= 40
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.score}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            Modifier.padding(vertical = 4.dp).fillMaxWidth().height((6 + 44 * s.score / 100).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBest) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ),
                        )
                        Text(
                            if (s.day.date == today) "Today"
                            else s.day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnsoCard(e: EnsoStatus, lat: Double, lon: Double) {
    val phase = e.phase
    val headline = when (phase) {
        EnsoPhase.EL_NINO -> "El Niño is active"
        EnsoPhase.LA_NINA -> "La Niña is active"
        EnsoPhase.NEUTRAL -> "Neutral conditions"
    }
    val trend = when {
        e.trend > 0.3 -> "strengthening"
        e.trend < -0.3 -> "weakening"
        else -> "holding steady"
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (phase == EnsoPhase.NEUTRAL) "🌊" else if (phase == EnsoPhase.EL_NINO) "🔥" else "❄️", fontSize = 28.sp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        (if (e.strength.isNotEmpty()) "${e.strength.replaceFirstChar { it.uppercase() }} · " else "") +
                            "index ${"%+.1f".format(e.oni)} (${e.season} ${e.year}) · $trend",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                if (phase == EnsoPhase.NEUTRAL) "No strong El Niño or La Niña signal in the Pacific right now, so it isn't pushing weather patterns either way."
                else Enso.regionalNote(lat, lon, phase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Seasonal tendencies, not a forecast for any particular day. Source: NOAA Climate Prediction Center.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
