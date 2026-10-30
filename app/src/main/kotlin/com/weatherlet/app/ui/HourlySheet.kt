package com.weatherlet.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weatherlet.app.alerts.TimeFmt
import com.weatherlet.app.alerts.temp
import com.weatherlet.app.alerts.wind
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Units
import com.weatherlet.app.icons.WeatherIcon
import java.time.LocalDateTime

/** The next 24 hours, hour by hour. Opened from the hero card or by tapping the hourly widget. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlySheet(f: Forecast, units: Units, onDismiss: () -> Unit) {
    val start = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    val hours = f.hours.filter { !it.time.isBefore(start) }.take(24)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Next 24 hours",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                "${f.place.title} · temperature, 💧 chance of rain or snow, wind gusts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
            if (hours.isEmpty()) {
                Text(
                    "Hourly data will appear after the next refresh.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(hours) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (h.time == start) "Now" else TimeFmt.hour(h.time.hour),
                            Modifier.width(52.dp), style = MaterialTheme.typography.titleSmall,
                        )
                        WeatherIcon(h.code, h.time.hour in 6..19, 30.dp)
                        Text(units.temp(h.temp), Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
                        Column(Modifier.weight(1f)) {
                            if (h.precipProb >= 20) {
                                Text(
                                    "💧${h.precipProb}%", maxLines = 1, color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (h.precipMm >= 0.5) Text(
                                    String.format(java.util.Locale.US, if (units == Units.METRIC) "%.1f mm" else "%.2f in",
                                        if (units == Units.METRIC) h.precipMm else h.precipMm / 25.4),
                                    maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Text(
                            "💨 ${units.wind(h.gustKmh)}", maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        }
    }
}
