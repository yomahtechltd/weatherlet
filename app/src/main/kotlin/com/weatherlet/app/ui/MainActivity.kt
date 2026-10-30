package com.weatherlet.app.ui

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.weatherlet.app.data.Locator
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()
    private var target by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        target = intent?.getStringExtra(DeepLink.EXTRA)
        setContent {
            WeatherletTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    App(vm, target) { target = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(DeepLink.EXTRA)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}

@Composable
private fun App(vm: WeatherViewModel, target: String?, onTargetHandled: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showHourly by rememberSaveable { mutableStateOf(false) }
    var showIntro by rememberSaveable { mutableStateOf(!vm.introSeen) }
    LaunchedEffect(target) {
        if (target == "hourly") { showHourly = true; onTargetHandled() }
    }

    val ctx = LocalContext.current
    var infoPage by rememberSaveable { mutableStateOf<InfoPage?>(null) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useMyLocation() else vm.locationDenied()
    }
    // "Update hourly" needs foreground location first, then "allow all the time" (a separate system step)
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setAutoLocation(true) else vm.setAutoLocation(false).also {
            Toast.makeText(ctx, "Turn on \"Allow all the time\" for location to update hourly", Toast.LENGTH_LONG).show()
        }
    }
    val askForeground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else vm.locationDenied()
    }
    val onAutoLocation = { on: Boolean ->
        when {
            !on -> vm.setAutoLocation(false)
            Locator.hasBackgroundPermission(ctx) -> vm.setAutoLocation(true)
            Locator.hasPermission(ctx) -> askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> askForeground.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    val useLocation = {
        if (Locator.hasPermission(ctx)) vm.useMyLocation()
        else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    LaunchedEffect(home.place) {
        if (home.place != null) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val firstRun = home.place == null
    BackHandler(enabled = searching && !firstRun && infoPage == null) { searching = false; vm.clearSearch() }
    BackHandler(enabled = infoPage != null) { infoPage = null }
    BackHandler(enabled = showHistory && infoPage == null) { showHistory = false }

    val notificationsOn = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var notifGranted by rememberSaveable { mutableStateOf(notificationsOn) }
    val askNotificationsIntro = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    if (showIntro) {
        BackHandler { if (!firstRun) { vm.finishIntro(); showIntro = false } }
        IntroScreen(
            profiles = home.profiles,
            notificationsAllowed = notifGranted,
            onToggleProfile = vm::toggleProfile,
            onAllowNotifications = { askNotificationsIntro.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onBatterySettings = {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onFinish = { vm.finishIntro(); showIntro = false },
            finishLabel = if (firstRun) "Choose your city" else "Done",
        )
        return
    }

    Crossfade(targetState = firstRun || searching, label = "screen") { showSearch ->
        if (showSearch) {
            SearchScreen(
                firstRun = firstRun,
                state = search,
                onQuery = vm::onQuery,
                onPick = { vm.selectPlace(it); searching = false },
                onBack = { searching = false; vm.clearSearch() },
                onUseLocation = { useLocation() },
            )
        } else {
            HomeScreen(
                state = home,
                onRefresh = vm::refresh,
                onPlaces = { showPlaces = true },
                onSettings = { showSettings = true },
                onDismissChanges = vm::dismissChanges,
                onToggleCheck = vm::toggleCheck,
                onBestActivity = vm::setBestActivity,
                target = target,
                onTargetHandled = onTargetHandled,
                onHourly = { showHourly = true },
            )
        }
    }

    if (showPlaces) {
        PlacesSheet(
            places = home.places,
            onDismiss = { showPlaces = false },
            onPick = { vm.switchPlace(it); showPlaces = false },
            onAdd = { showPlaces = false; searching = true },
            onRemove = vm::removePlace,
            onRename = vm::renamePlace,
            onMakeHome = vm::makeHome,
        )
    }

    if (showSettings) {
        SettingsSheet(
            state = home,
            onDismiss = { showSettings = false },
            onUnits = vm::setUnits,
            onAlerts = vm::setAlertsEnabled,
            onHour = vm::setAlertHour,
            onProfile = vm::toggleProfile,
            onCommute = vm::setCommute,
            onTest = vm::sendTest,
            onInfo = { infoPage = it; showSettings = false },
            onAutoLocation = onAutoLocation,
            onSensitivity = vm::setSensitivity,
            onQuiet = { e, s, en -> vm.setQuiet(e, s, en) },
            onLiveUpdates = vm::setLiveUpdates,
            onRainSoon = vm::setRainSoon,
            on24h = vm::setUse24h,
            onIntro = { showSettings = false; showIntro = true },
            onHistory = { showHistory = true; showSettings = false },
        )
    }

    if (showHourly) home.forecast?.let { HourlySheet(it, home.units, onDismiss = { showHourly = false }) }
    if (showHistory) HistoryScreen(home.history, onBack = { showHistory = false }, onClear = vm::clearHistory)
    infoPage?.let { InfoScreen(it, onBack = { infoPage = null }) }
}
