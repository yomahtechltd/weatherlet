package com.weatherlet.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.icons.WeatherIcon
import kotlinx.coroutines.launch

private const val PAGES = 4

/**
 * First-run guide: what the app does, how alerts work, pick your profiles, and the two things that
 * make alerts reliable (notifications + battery). Every page can be skipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroScreen(
    profiles: Set<Profile>,
    notificationsAllowed: Boolean,
    onToggleProfile: (Profile) -> Unit,
    onAllowNotifications: () -> Unit,
    onBatterySettings: () -> Unit,
    onFinish: () -> Unit,
    finishLabel: String = "Choose your city",
) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish, enabled = !last) {
                    Text(if (last) "" else "Skip")
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (page) {
                        0 -> Page(
                            hero = { Hero { WeatherIcon(2, true, 96.dp) } },
                            title = "Weather that warns you early",
                            body = "Weatherlet looks 5 days ahead and tells you when snow, extreme cold, heavy rain, strong wind or " +
                                "a heat wave is on the way — while there's still time to prepare.",
                        )
                        1 -> Page(
                            hero = { Hero { Text("🔔", fontSize = 64.sp) } },
                            title = "A heads-up, not a flood",
                            body = "You get one notification a day, only when something is coming. If the forecast gets worse " +
                                "later you'll hear about it, and if a warning is issued by the Met Office, NWS or MeteoAlarm it shows up too.\n\n" +
                                "Tick off the \"Prepare\" checklist on each alert, and add a widget to see it all from your home screen.",
                        )
                        2 -> {
                            Page(
                                hero = { Hero(120.dp) { Text("🎯", fontSize = 44.sp) } },
                                title = "Make it yours",
                                body = "Pick anything that applies and the alerts adapt — a cyclist is warned about gusts sooner, " +
                                    "someone with asthma about air quality, dust and cold air. Choose none for standard alerts.",
                            )
                            Spacer(Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Profile.entries.forEach { p ->
                                    FilterChip(
                                        selected = p in profiles,
                                        onClick = { onToggleProfile(p) },
                                        label = { Text("${p.emoji} ${p.label}") },
                                    )
                                }
                            }
                            Text(
                                "You can change this any time in Settings.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        else -> {
                            Page(
                                hero = { Hero { Text("⏰", fontSize = 64.sp) } },
                                title = "Two things for on-time alerts",
                                body = "Weatherlet works quietly in the background, so Android needs your OK to show notifications " +
                                    "and to let it run on time.",
                            )
                            Spacer(Modifier.height(20.dp))
                            if (notificationsAllowed) {
                                Text("✅ Notifications are on", style = MaterialTheme.typography.titleSmall)
                            } else {
                                Button(onClick = onAllowNotifications) { Text("Allow notifications") }
                            }
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = onBatterySettings) { Text("Battery settings") }
                            Text(
                                "Set Weatherlet to \"Don't optimise\" so your morning alert isn't delayed. " +
                                    "Settings will tell you if it ever is.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGES) { i ->
                        val color by animateColorAsState(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            label = "dot",
                        )
                        Box(Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(color))
                    }
                }
                Button(onClick = {
                    if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) { Text(if (last) finishLabel else "Next") }
            }
        }
    }
}

@Composable
private fun Hero(size: androidx.compose.ui.unit.Dp = 180.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF4AA8FF), Color(0xFF1B4FC2)))),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Page(hero: @Composable () -> Unit, title: String, body: String) {
    hero()
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(
        body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
