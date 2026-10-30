package com.weatherlet.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherlet.app.data.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the app has told you, newest first — so a missed notification is never lost. */
@Composable
fun HistoryScreen(entries: List<HistoryEntry>, onBack: () -> Unit, onClear: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Alert history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            }
            if (entries.isEmpty()) {
                Text(
                    "Nothing yet. When Weatherlet alerts you about weather, it will be listed here.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries) { e ->
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(icon(e.kind), fontSize = 22.sp)
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (e.text.isNotBlank()) {
                                        Text(
                                            e.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Text(
                                        whenLabel(e.at),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun icon(kind: String) = when (kind) {
    "daily" -> "🔔"
    "change" -> "⚠️"
    "update" -> "✅"
    "official" -> "🏛️"
    "commute" -> "🚗"
    "status" -> "⏳"
    else -> "🔔"
}

private fun whenLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val day = when (t.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> t.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return "$day, ${com.weatherlet.app.alerts.TimeFmt.time(t.hour, t.minute)}"
}
