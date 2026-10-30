package com.weatherlet.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weatherlet.app.alerts.CommuteAdvisor
import com.weatherlet.app.alerts.CommuteSettings
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.Sensitivity
import com.weatherlet.app.data.Units

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    state: HomeState,
    onDismiss: () -> Unit,
    onUnits: (Units) -> Unit,
    onAlerts: (Boolean) -> Unit,
    onHour: (Int) -> Unit,
    onProfile: (Profile) -> Unit,
    onCommute: (CommuteSettings) -> Unit,
    onTest: () -> Unit,
    onInfo: (InfoPage) -> Unit,
    onAutoLocation: (Boolean) -> Unit,
    onSensitivity: (Sensitivity) -> Unit,
    onQuiet: (Boolean, Int, Int) -> Unit,
    onLiveUpdates: (Boolean) -> Unit,
    onRainSoon: (Boolean) -> Unit,
    on24h: (Boolean) -> Unit = {},
    onHistory: () -> Unit,
    onIntro: () -> Unit = {},
) {
    var showDisclosure by remember { mutableStateOf(false) }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Update my location hourly") },
            text = {
                Text(
                    "If you turn this on, Weatherlet will check your approximate location — your city, not your " +
                        "exact position — about once an hour, even when the app is closed, so the forecast, widget " +
                        "and alerts match where you are when you travel.\n\n" +
                        "Your location is used only to look up the weather. The coordinates of your city are sent to " +
                        "the weather service to get the forecast, and nothing else is shared. You can turn this off " +
                        "at any time.\n\n" +
                        "Android will ask you to choose \"Allow all the time\" for location.",
                )
            },
            confirmButton = {
                TextButton({ showDisclosure = false; onAutoLocation(true) }) { Text("I agree, turn on") }
            },
            dismissButton = { TextButton({ showDisclosure = false }) { Text("Cancel") } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))

            // --- profiles
            Text("What describes you?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alerts get more sensitive where it matters to you — a cyclist is warned at 40 km/h gusts, a driver about fog and black ice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (state.profiles.isEmpty()) "Pick any that apply. None selected means standard alerts."
                else "Several selected? Each hazard uses the most sensitive setting among them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p ->
                    FilterChip(
                        selected = p in state.profiles,
                        onClick = { onProfile(p) },
                        label = { Text("${p.emoji} ${p.label}") },
                    )
                }
            }

            if (Profile.ALLERGY in state.profiles) {
                Text(
                    "Pollen forecasts are only available in Europe. Everywhere, you'll still get air quality, " +
                        "dust, cold-air, thunderstorm and sudden-temperature-change alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (Profile.MIGRAINE in state.profiles) {
                Text(
                    "You'll get a pressure-drop alert when air pressure falls quickly — a common migraine and joint-pain trigger — plus earlier warnings for sharp temperature swings, heat and strong UV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- sensitivity
            Spacer(Modifier.height(8.dp))
            Text("Alert sensitivity", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Sensitivity.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = state.sensitivity == s,
                        onClick = { onSensitivity(s) },
                        shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                    ) { Text(s.label) }
                }
            }
            Text(
                state.sensitivity.blurb + ". Profiles still make the relevant hazards more sensitive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- daily alerts
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily weather alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "One notification a day when adverse weather is forecast in the next 5 days, plus an instant one if it gets worse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.alertsEnabled, onCheckedChange = onAlerts)
            }
            if (state.alertsEnabled) {
                Text("Send it from", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 7, 8, 9, 18).forEach { h ->
                        FilterChip(
                            selected = state.alertHour == h,
                            onClick = { onHour(h) },
                            label = { Text(com.weatherlet.app.alerts.TimeFmt.hour(h)) },
                        )
                    }
                }
                Text(
                    "Checks run roughly every 3 hours, so it arrives soon after this time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- rain soon
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rain-soon heads-up", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "\"Rain in about 40 min\" when it's dry now and a real spell is about to start where you are. " +
                            "Checks about every 30 minutes, at most one nudge every 3 hours, silent during quiet hours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.rainSoon, onCheckedChange = onRainSoon, enabled = state.alertsEnabled)
            }

            // --- quiet hours
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No instant updates overnight. Your daily alert and severe official warnings still come through.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.quietEnabled, onCheckedChange = { onQuiet(it, state.quietStart, state.quietEnd) })
            }
            if (state.quietEnabled) {
                TimeStepper("From", state.quietStart) { onQuiet(true, it, state.quietEnd) }
                TimeStepper("Until", state.quietEnd) { onQuiet(true, state.quietStart, it) }
            }

            // --- commute
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Commute alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A heads-up about 45 minutes before you set off on weekdays — only when weather will actually affect the trip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.commute.enabled,
                    onCheckedChange = { onCommute(state.commute.copy(enabled = it)) },
                )
            }
            if (state.commute.enabled) {
                TimeStepper("Leave for work", state.commute.morningMin) { onCommute(state.commute.copy(morningMin = it)) }
                TimeStepper("Leave for home", state.commute.eveningMin) { onCommute(state.commute.copy(eveningMin = it)) }
                Text(
                    "Uses your home place. Delivery can be a few minutes late when your phone is dozing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- live update chip (Android 16+)
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live weather-alert chip", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "While an alert is active today, show a short chip (like \"72 km/h\") in the status bar and on the lock screen. Android 16 and later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.liveUpdates, onCheckedChange = onLiveUpdates)
                }
            }

            // --- travelling
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Update my location hourly", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Follows you as you travel, even with the app closed. City-level only, not your exact position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoLocation,
                    onCheckedChange = { if (it) showDisclosure = true else onAutoLocation(false) },
                )
            }

            // --- units
            Text("Units", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Units.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = state.units == u,
                        onClick = { onUnits(u) },
                        shape = SegmentedButtonDefaults.itemShape(i, Units.entries.size),
                    ) { Text(if (u == Units.METRIC) "°C · km/h" else "°F · mph") }
                }
            }

            Text("Clock", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "12-hour (8:30 PM)", true to "24-hour (20:30)").forEachIndexed { i, (is24, label) ->
                    SegmentedButton(
                        selected = state.use24h == is24,
                        onClick = { on24h(is24) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label, maxLines = 1) }
                }
            }

            state.background?.let { BackgroundCard(it) }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onTest, enabled = state.forecast != null) {
                Text("Send a test notification")
            }
            Spacer(Modifier.height(8.dp))
            Text("More", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onHistory) { Text("Alert history") }
                FilledTonalButton(onClick = onIntro) { Text("Show intro") }
                InfoPage.entries.forEach { page ->
                    FilledTonalButton(onClick = { onInfo(page) }) { Text(page.title) }
                }
            }
            Text(
                "Weather data by Open-Meteo.com (CC BY 4.0).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    fun wrap(m: Int) = ((m % 1440) + 1440) % 1440
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton({ onChange(wrap(minutes - 15)) }) { Text("−") }
        Text(
            CommuteAdvisor.time(minutes),
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton({ onChange(wrap(minutes + 15)) }) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundCard(r: com.weatherlet.app.alerts.BackgroundReport) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun dot(l: com.weatherlet.app.alerts.Health) = when (l) {
        com.weatherlet.app.alerts.Health.GOOD -> "🟢"
        com.weatherlet.app.alerts.Health.WARN -> "🟠"
        com.weatherlet.app.alerts.Health.BAD -> "🔴"
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (r.verdict == com.weatherlet.app.alerts.Health.GOOD) "Alerts are running smoothly" else if (r.verdict == com.weatherlet.app.alerts.Health.WARN) "Alerts could be delayed" else "Alerts may arrive late",
                style = MaterialTheme.typography.titleMedium,
            )
            r.lines.forEach { l ->
                Column(Modifier.fillMaxWidth()) {
                    Text("${dot(l.level)}  ${l.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        l.value, Modifier.padding(start = 28.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            r.advice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (r.verdict != com.weatherlet.app.alerts.Health.GOOD) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Battery settings") }
                    FilledTonalButton(onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("App settings") }
                }
                Text(
                    "In Battery settings choose \"All apps\", find Weatherlet and set it to Don't optimise (wording varies by phone).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
