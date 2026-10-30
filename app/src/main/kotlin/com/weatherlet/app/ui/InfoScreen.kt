package com.weatherlet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherlet.app.alerts.Thresholds

enum class InfoPage(val title: String) {
    ABOUT("About"),
    PRIVACY("Privacy policy"),
    TERMS("Terms of use"),
}

/** Shown on the Privacy and Terms pages; the line is hidden while this is empty. */
private const val CONTACT_EMAIL = "yomahtechuk@outlook.com"
private const val AUTHOR_EMAIL = "yomahtechuk@outlook.com"
private const val LAST_UPDATED = "30 October 2026"

private data class Section(val heading: String, val body: String)

@Composable
fun InfoScreen(page: InfoPage, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val sections = when (page) {
        InfoPage.ABOUT -> about()
        InfoPage.PRIVACY -> privacy()
        InfoPage.TERMS -> terms()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(page.title, style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                if (page == InfoPage.ABOUT) {
                    Text("🌦️", fontSize = 56.sp)
                    Text("Weatherlet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (version.isNotBlank()) {
                        Text(
                            "Version $version",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Made with ❤️ by $AUTHOR_EMAIL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            ctx.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$AUTHOR_EMAIL"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(
                        "Last updated $LAST_UPDATED",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                sections.forEach { s ->
                    Text(s.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                }
                if (CONTACT_EMAIL.isNotBlank()) {
                    Text("Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(CONTACT_EMAIL, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun about(): List<Section> {
    val t = Thresholds()
    return listOf(
        Section(
            "What it does",
            "Weatherlet watches the next 5 days for adverse or disruptive weather — snow, ice, extreme cold, " +
                "heavy rain and flooding, high winds, heat waves, storms, fog, strong UV, poor air quality, dust and pollen — " +
                "and tells you early enough to prepare.",
        ),
        Section(
            "How alerts are decided",
            "Each day's forecast is checked against fixed thresholds. By default:\n" +
                "• Snow: ${t.snowW.toInt()} cm (severe ${t.snowS.toInt()} cm)\n" +
                "• Extreme cold: feels like ${t.coldW.toInt()}° or below (severe ${t.coldS.toInt()}°)\n" +
                "• Heavy rain: ${t.rainW.toInt()} mm in a day (severe ${t.rainS.toInt()} mm)\n" +
                "• Flood risk: ${t.floodW.toInt()} mm over 3 days on soaked ground\n" +
                "• High winds: gusts ${t.gustW.toInt()} km/h (severe ${t.gustS.toInt()} km/h)\n" +
                "• Heat: ${t.heatW.toInt()}° or 3+ days at ${t.heatRun.toInt()}° (severe ${t.heatS.toInt()}°)\n" +
                "• Black ice and frost: freezing night after wet weather\n" +
                "• Temperature swing: ${t.swingW.toInt()}° change in a day\n" +
                "• Fog, UV, air quality, dust: at \"very poor\" levels\n\n" +
                "Choosing profiles in Settings (for example cyclist or asthma) makes the relevant thresholds more " +
                "sensitive. With several profiles, the most sensitive setting for each hazard wins.",
        ),
        Section(
            "Where the data comes from",
            "• Weather forecasts and air quality: Open-Meteo.com, licensed CC BY 4.0.\n" +
                "• Official warnings: UK Met Office (UK) and the US National Weather Service (US).\n" +
                "• City search: Open-Meteo geocoding.\n\n" +
                "Alerts from Weatherlet are generated from forecast data by this app; they are not issued by any " +
                "weather service.",
        ),
        Section(
            "Please note",
            "Forecasts can be wrong, especially several days out. Weatherlet is a planning aid, not a substitute " +
                "for official warnings. In an emergency follow the guidance of your local authorities.",
        ),
    )
}

private fun privacy() = listOf(
    Section(
        "The short version",
        "Weatherlet has no accounts, no analytics, no advertising and no tracking. We don't collect or sell " +
            "personal information. Your places and settings stay on your phone.",
    ),
    Section(
        "What is stored on your device",
        "Your saved places, settings (profiles, units, alert and commute times) and the most recent forecasts, " +
            "kept in the app's private storage. Uninstalling the app deletes all of it.",
    ),
    Section(
        "What is sent over the network",
        "To get a forecast the app sends the coordinates of each saved place to Open-Meteo.com. Text you type " +
            "when searching for a city is sent to Open-Meteo's geocoding service. For places in the United States, " +
            "coordinates are also sent to the US National Weather Service to look up official warnings. " +
            "UK warnings (Met Office), European warnings (MeteoAlarm) and El Niño data (NOAA) come from national or " +
            "global feeds that are downloaded in full and filtered on your device, so no location is sent for them.\n\n" +
            "As with any internet request, these services can see your device's IP address. Their own privacy " +
            "policies apply to what they do with it.",
    ),
    Section(
        "Location",
        "Location is optional. If you choose \"Use my current location\", the app asks for approximate " +
            "location permission and reads it only while the app is open, to update the forecast when you travel. " +
            "To turn your coordinates into a readable city name, your phone's built-in geocoder (usually " +
            "provided by Google Play services) may be used. You can revoke the permission any time in system " +
            "settings, or remove the 📍 place in the app to stop following your location.\n\n" +
            "Optionally, you can turn on \"Update my location hourly\". Then the app also checks your approximate " +
            "location about once an hour while it is closed, which requires the \"Allow all the time\" location " +
            "permission. This is off by default, only city-level location is used, and it only keeps the forecast " +
            "matching where you are. Turn it off in Settings at any time.",
    ),
    Section(
        "Notifications",
        "Alerts are created on your device from forecast data. No notification service or push server is used.",
    ),
    Section(
        "Children",
        "Weatherlet is a general-audience app and does not knowingly collect personal information from anyone, " +
            "including children.",
    ),
    Section(
        "Changes",
        "If this policy changes — for example if advertising is added — the updated policy will be shown in the " +
            "app and the date above will change.",
    ),
)

private fun terms() = listOf(
    Section(
        "Using Weatherlet",
        "By using the app you agree to these terms. If you don't agree, please don't use it.",
    ),
    Section(
        "Information only — not a safety service",
        "Weatherlet provides weather information and automatically generated alerts. Forecasts are " +
            "predictions and may be wrong, late or unavailable. Do not rely on the app as your only source of " +
            "information where life, health or property may be at risk. Always follow official warnings and the " +
            "guidance of emergency services and local authorities.",
    ),
    Section(
        "Notifications",
        "Delivery of notifications depends on your phone's settings, battery optimisation, network connection and " +
            "the operating system. Alerts, including commute alerts, may be delayed or not delivered.",
    ),
    Section(
        "No warranty",
        "The app and its data are provided \"as is\" and \"as available\", without warranties of any kind, " +
            "express or implied, including accuracy, reliability, availability or fitness for a particular purpose.",
    ),
    Section(
        "Limitation of liability",
        "To the fullest extent permitted by law, the developer is not liable for any loss or damage — direct or " +
            "indirect — arising from your use of, or inability to use, the app or reliance on its information.",
    ),
    Section(
        "Third-party data",
        "Forecast and air-quality data are provided by Open-Meteo.com under the Creative Commons Attribution 4.0 " +
            "licence (CC BY 4.0). Official warnings are provided by the UK Met Office, the US National Weather " +
            "Service and MeteoAlarm under their respective terms. These providers are independent of Weatherlet.",
    ),
    Section(
        "Acceptable use",
        "Don't misuse the app, attempt to disrupt its data providers, or copy and resell the app's content.",
    ),
    Section(
        "Changes",
        "These terms may be updated from time to time. Continued use after an update means you accept the " +
            "revised terms.",
    ),
)
