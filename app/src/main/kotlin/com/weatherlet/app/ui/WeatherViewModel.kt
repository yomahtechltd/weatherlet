package com.weatherlet.app.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weatherlet.app.alerts.Activity
import com.weatherlet.app.alerts.AlertChange
import com.weatherlet.app.alerts.Sensitivity
import com.weatherlet.app.data.Enso
import com.weatherlet.app.data.EnsoStatus
import com.weatherlet.app.data.HistoryEntry
import com.weatherlet.app.work.LiveAlert
import com.weatherlet.app.alerts.AlertEngine
import com.weatherlet.app.alerts.CommuteAdvisor
import com.weatherlet.app.alerts.CommuteLeg
import com.weatherlet.app.alerts.CommuteSettings
import com.weatherlet.app.alerts.Profile
import com.weatherlet.app.alerts.WeatherAlert
import com.weatherlet.app.data.Forecast
import com.weatherlet.app.data.Locator
import com.weatherlet.app.data.OfficialWarning
import com.weatherlet.app.data.OfficialWarnings
import com.weatherlet.app.data.OpenMeteo
import com.weatherlet.app.data.Place
import com.weatherlet.app.data.Store
import com.weatherlet.app.data.Units
import com.weatherlet.app.data.WeatherRepo
import com.weatherlet.app.widget.WeatherWidget
import com.weatherlet.app.work.CommuteScheduler
import com.weatherlet.app.work.DailyScheduler
import com.weatherlet.app.work.LocationWorker
import com.weatherlet.app.work.Notifier
import com.weatherlet.app.work.RainSoonWorker
import com.weatherlet.app.work.WeatherWorker
import com.weatherlet.app.alerts.BackgroundHealth
import com.weatherlet.app.alerts.BackgroundReport
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class PlaceSummary(val place: Place, val alertCount: Int, val top: WeatherAlert?, val isHome: Boolean, val isActive: Boolean)

data class HomeState(
    val place: Place? = null,
    val forecast: Forecast? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val units: Units = Units.METRIC,
    val alertsEnabled: Boolean = true,
    val alertHour: Int = 7,
    val profiles: Set<Profile> = emptySet(),
    val commute: CommuteSettings = CommuteSettings(),
    val commuteLegs: List<CommuteLeg> = emptyList(),
    val places: List<PlaceSummary> = emptyList(),
    val changes: List<AlertChange> = emptyList(),
    val official: List<OfficialWarning> = emptyList(),
    val officialSupported: Boolean = false,
    val autoLocation: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val quietEnabled: Boolean = true,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 6 * 60,
    val liveUpdates: Boolean = true,
    val checked: Set<String> = emptySet(),
    val bestActivity: Activity = Activity.RUN,
    val enso: EnsoStatus? = null,
    val history: List<HistoryEntry> = emptyList(),
    val rainSoon: Boolean = true,
    val use24h: Boolean = false,
    val background: BackgroundReport? = null,
)

data class SearchState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
    val locating: Boolean = false,
)

@OptIn(FlowPreview::class)
class WeatherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    private val _home = MutableStateFlow(build(refreshing = false, error = null))
    val home: StateFlow<HomeState> = _home

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search

    private val queries = MutableStateFlow("")

    private fun backgroundReport(): BackgroundReport {
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        val bucket = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching {
            app.getSystemService(android.app.usage.UsageStatsManager::class.java).appStandbyBucket
        }.getOrDefault(-1) else -1
        return BackgroundHealth.report(
            now = System.currentTimeMillis(), lastSyncAt = store.lastSyncAt,
            dailyDelayMin = store.lastDailyDelayMin, dailyRunAt = store.lastDailyRunAt,
            alertsOn = store.alertsEnabled,
            unrestricted = pm?.isIgnoringBatteryOptimizations(app.packageName) == true,
            bucket = bucket,
        )
    }

    /** Everything on the home screen is derived from the store, so it can't drift from the widget. */
    private fun build(refreshing: Boolean, error: String?): HomeState {
        val place = store.place
        val forecast = store.cachedForecast()
        val profiles = store.profiles
        val units = store.units
        val commute = store.commute
        val home = store.home
        val legs = if (commute.enabled && home != null) {
            store.cached(home)?.let { CommuteAdvisor.upcoming(LocalDateTime.now(), commute, it.hours, units) }.orEmpty()
        } else emptyList()
        return HomeState(
            place = place,
            forecast = forecast,
            refreshing = refreshing,
            error = error,
            units = units,
            alertsEnabled = store.alertsEnabled,
            alertHour = store.alertHour,
            profiles = profiles,
            commute = commute,
            commuteLegs = legs,
            places = store.places.map { p ->
                val alerts = store.cached(p)?.let { AlertEngine.evaluate(it.days, it.past, profiles, store.sensitivity) }.orEmpty()
                PlaceSummary(p, alerts.size, alerts.firstOrNull(), p.key == home?.key, p.key == place?.key)
            },
            changes = place?.let { store.changes(it) }.orEmpty(),
            official = place?.let { store.official(it) }.orEmpty(),
            officialSupported = place?.let { OfficialWarnings.isSupported(it) } ?: false,
            autoLocation = store.autoLocation,
            sensitivity = store.sensitivity,
            quietEnabled = store.quietEnabled,
            quietStart = store.quietStart,
            quietEnd = store.quietEnd,
            liveUpdates = store.liveUpdates,
            checked = store.checkedKeys(),
            bestActivity = runCatching { Activity.valueOf(store.bestActivity) }.getOrDefault(Activity.RUN),
            enso = store.enso,
            history = store.history(),
            rainSoon = store.rainSoon,
            use24h = store.use24h,
            background = backgroundReport(),
        )
    }

    private fun rebuild(refreshing: Boolean = _home.value.refreshing, error: String? = _home.value.error) {
        _home.value = build(refreshing, error)
    }

    private fun updateWidget() {
        viewModelScope.launch { com.weatherlet.app.widget.Widgets.updateAll(app) }
    }

    init {
        viewModelScope.launch {
            queries.debounce(350).distinctUntilChanged().collectLatest { q ->
                if (q.trim().length < 2) {
                    _search.update { it.copy(results = emptyList(), searching = false, error = null) }
                    return@collectLatest
                }
                _search.update { it.copy(searching = true, error = null) }
                runCatching { OpenMeteo.search(q) }
                    .onSuccess { r -> _search.update { it.copy(results = r, searching = false) } }
                    .onFailure { _search.update { s -> s.copy(searching = false, error = "Couldn't search. Check your connection.") } }
            }
        }
        if (store.places.isNotEmpty()) {
            WeatherWorker.schedule(app)
            DailyScheduler.scheduleNext(app)
            CommuteScheduler.scheduleNext(app)
            RainSoonWorker.sync(app)
        }
        if (store.autoLocation) {
            if (Locator.hasBackgroundPermission(app)) LocationWorker.schedule(app)
            else store.autoLocation = false // permission was revoked while we weren't looking
        }
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    /** The widget can change the active place while the app is in the background. */
    fun onForeground() {
        LiveAlert.sync(app)
        rebuild()
        viewModelScope.launch {
            // Travelling? If the user follows their location, re-check where they are first.
            if (store.followsLocation && Locator.hasPermission(app)) {
                Locator.locate(app)?.let { if (store.setCurrent(it)) updateWidget() }
                rebuild()
            }
            refresh()
        }
    }

    /** Called once location permission is granted: add/refresh the 📍 place and make it active. */
    fun useMyLocation() {
        viewModelScope.launch {
            _search.update { it.copy(locating = true, error = null) }
            val here = Locator.locate(app)
            if (here == null) {
                _search.update {
                    it.copy(locating = false, error = "Couldn't find your location. Check that location is switched on, or search for a city.")
                }
                return@launch
            }
            store.setCurrent(here, makeActive = true)
            _search.update { it.copy(locating = false) }
            clearSearch()
            WeatherWorker.schedule(app)
            DailyScheduler.scheduleNext(app)
            CommuteScheduler.scheduleNext(app)
            rebuild(error = null)
            refresh()
        }
    }

    /** Called after the user accepted the disclosure and granted "allow all the time". */
    fun setAutoLocation(on: Boolean) {
        store.autoLocation = on
        if (on) {
            LocationWorker.schedule(app)
            if (store.followsLocation) onForeground() else useMyLocation()
        } else {
            LocationWorker.cancel(app)
        }
        rebuild()
    }

    fun locationDenied() {
        _search.update { it.copy(error = "Location permission was declined. You can still search for a city.") }
    }

    fun refresh() {
        val active = store.place ?: return
        viewModelScope.launch {
            rebuild(refreshing = true, error = null)
            var error: String? = null
            WeatherRepo.refresh(app, active, updateWidget = false).onFailure {
                error = if (_home.value.forecast == null) "Couldn't load the forecast. Pull down to retry."
                else "Offline — showing last update."
            }
            rebuild(refreshing = false, error = error)
            // Other saved places refresh quietly afterwards.
            store.places.filter { it.key != active.key }.forEach { WeatherRepo.refresh(app, it, updateWidget = false) }
            updateWidget()
            refreshEnso()
            rebuild(refreshing = false, error = error)
        }
    }

    /** El Niño / La Niña status changes over months, so once every half day is plenty. */
    private suspend fun refreshEnso() {
        if (System.currentTimeMillis() - store.ensoAt < 12 * 3_600_000L && store.enso != null) return
        Enso.fetch()?.let {
            store.enso = it
            store.ensoAt = System.currentTimeMillis()
        }
    }

    // ---- search ---------------------------------------------------------------------------------

    fun onQuery(q: String) {
        _search.update { it.copy(query = q) }
        queries.value = q
    }

    fun clearSearch() = onQuery("")

    // ---- places ---------------------------------------------------------------------------------

    fun selectPlace(p: Place) {
        store.addOrActivate(p)
        clearSearch()
        WeatherWorker.schedule(app)
        DailyScheduler.scheduleNext(app)
        CommuteScheduler.scheduleNext(app)
        RainSoonWorker.sync(app)
        rebuild(error = null)
        refresh()
    }

    fun switchPlace(p: Place) {
        store.setActive(p)
        rebuild(error = null)
        updateWidget()
        refresh()
    }

    fun removePlace(p: Place) {
        store.remove(p)
        DailyScheduler.scheduleNext(app)
        if (p.isCurrent) setAutoLocation(false) // nothing left to follow
        CommuteScheduler.scheduleNext(app)
        rebuild(error = null)
        updateWidget()
    }

    fun renamePlace(p: Place, nickname: String) {
        store.rename(p, nickname)
        rebuild()
        updateWidget()
    }

    fun makeHome(p: Place) {
        store.makeHome(p)
        rebuild()
        refresh()
    }

    // ---- insight cards --------------------------------------------------------------------------

    fun dismissChanges() {
        store.place?.let { store.clearChanges(it) }
        rebuild()
    }

    // ---- settings -------------------------------------------------------------------------------

    fun setUse24h(on: Boolean) {
        store.use24h = on
        rebuild()
        updateWidget()
    }

    fun setUnits(u: Units) {
        store.units = u
        rebuild()
        updateWidget()
    }

    fun setAlertsEnabled(on: Boolean) {
        store.alertsEnabled = on
        DailyScheduler.scheduleNext(app)
        RainSoonWorker.sync(app)
        rebuild()
    }

    fun setAlertHour(h: Int) {
        store.alertHour = h
        store.lastNotified = null
        DailyScheduler.scheduleNext(app)
        rebuild()
    }

    val introSeen: Boolean get() = store.introSeen

    fun finishIntro() { store.introSeen = true }

    fun toggleProfile(p: Profile) {
        val cur = store.profiles
        store.profiles = if (p in cur) cur - p else cur + p
        // Different thresholds → the old snapshot isn't comparable; drop displayed changes.
        store.place?.let { store.clearChanges(it) }
        rebuild()
        updateWidget()
    }

    fun setSensitivity(s: Sensitivity) {
        store.sensitivity = s
        store.place?.let { store.clearChanges(it) }
        rebuild()
        updateWidget()
        LiveAlert.sync(app)
    }

    fun setQuiet(enabled: Boolean = store.quietEnabled, start: Int = store.quietStart, end: Int = store.quietEnd) {
        store.quietEnabled = enabled
        store.quietStart = start
        store.quietEnd = end
        rebuild()
    }

    fun setRainSoon(on: Boolean) {
        store.rainSoon = on
        RainSoonWorker.sync(app)
        rebuild()
    }

    fun setLiveUpdates(on: Boolean) {
        store.liveUpdates = on
        LiveAlert.sync(app)
        rebuild()
    }

    fun toggleCheck(key: String) {
        store.setChecked(key, !store.isChecked(key))
        rebuild()
        updateWidget()
    }

    fun setBestActivity(a: Activity) {
        store.bestActivity = a.name
        rebuild()
    }

    fun clearHistory() {
        store.clearHistory()
        rebuild()
    }

    fun setCommute(s: CommuteSettings) {
        store.commute = s
        CommuteScheduler.scheduleNext(app)
        rebuild()
    }

    fun sendTest() {
        _home.value.forecast?.let { Notifier.sendTest(app, it) }
    }
}
