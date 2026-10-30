package com.weatherlet.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weatherlet.app.data.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesSheet(
    places: List<PlaceSummary>,
    onDismiss: () -> Unit,
    onPick: (Place) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Place) -> Unit,
    onRename: (Place, String) -> Unit,
    onMakeHome: (Place) -> Unit,
) {
    var renaming by remember { mutableStateOf<Place?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Your places", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "Every place is checked for alerts. Tap the city name on the widget to flip between them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            places.forEach { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPick(s.place) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.place.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (s.isActive) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (s.place.isCurrent) Text("📍", Modifier.padding(start = 8.dp))
                            if (s.isHome) {
                                Icon(
                                    Icons.Default.Home, "Home",
                                    Modifier.padding(start = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        val status = s.top?.let {
                            "${it.type.emoji} ${it.type.title}" + if (s.alertCount > 1) " +${s.alertCount - 1}" else ""
                        } ?: "All clear"
                        Text(
                            "$status · " + if (s.place.isCurrent) "Following your location" else s.place.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (!s.place.isCurrent) IconButton({ renaming = s.place }) { Icon(Icons.Default.Edit, "Rename") }
                    if (!s.isHome) IconButton({ onMakeHome(s.place) }) { Icon(Icons.Default.Home, "Make home") }
                    IconButton({ onRemove(s.place) }) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Add, null)
                Text("Add a place", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "Home is your first place — commute alerts use it. A 📍 place follows you as you travel; remove it to stop.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    renaming?.let { p ->
        var text by remember(p.key) { mutableStateOf(p.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename ${p.name}") },
            text = {
                TextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("e.g. Mum's, Work, Cabin") },
                )
            },
            confirmButton = { TextButton({ onRename(p, text); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
}
