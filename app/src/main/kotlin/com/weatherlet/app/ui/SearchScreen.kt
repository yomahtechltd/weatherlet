package com.weatherlet.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherlet.app.data.Place

@Composable
fun SearchScreen(
    firstRun: Boolean,
    state: SearchState,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
    onBack: () -> Unit,
    onUseLocation: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 16.dp),
    ) {
        if (firstRun) {
            Spacer(Modifier.height(48.dp))
            Text("🌦️", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Weatherlet",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Search your city and we'll warn you about snow, extreme cold, heavy rain, high winds and heat waves up to 5 days ahead.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        } else {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Add a place", style = MaterialTheme.typography.titleLarge)
            }
        }

        TextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("City name, e.g. Manchester") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                when {
                    state.searching -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    state.query.isNotEmpty() -> IconButton({ onQuery("") }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        Spacer(Modifier.height(12.dp))

        FilledTonalButton(
            onClick = onUseLocation,
            enabled = !state.locating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("📍")
            Text(
                if (state.locating) "Finding you…" else "Use my current location",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            "Approximate location only, used while the app is open — handy when you're travelling.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp),
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }
        if (!state.searching && state.error == null && state.query.trim().length >= 2 && state.results.isEmpty()) {
            Text(
                "No cities found for “${state.query.trim()}”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(state.results) { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPick(p) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(p.admin?.takeIf { it != p.name }, p.country).joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
