package com.weatherlet.app.alerts

import com.weatherlet.app.data.DayForecast
import com.weatherlet.app.data.Units
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Severity { WARNING, SEVERE }

enum class AlertType(val title: String, val emoji: String, val tip: String) {
    SNOW("Snow", "❄️", "Allow extra travel time, check the car, and keep a shovel or grit handy."),
    COLD("Extreme cold", Emoji.cold, "Protect pipes, check on vulnerable neighbours, wrap up warm and bring pets in."),
    ICE("Black ice & frost", Emoji.ice, "Surfaces will freeze overnight. Leave extra time, walk and drive gently, and de-ice before you set off."),
    RAIN("Heavy rain", "🌧️", "Clear drains and gutters, avoid flood-prone routes, lift valuables off the floor."),
    FLOOD("Flood risk", "🌊", "Ground is already soaked. Avoid low-lying roads and underpasses, and move valuables upstairs."),
    WIND("High winds", "💨", "Secure garden furniture and bins, expect travel disruption, avoid parking under trees."),
    HEAT("Heat", Emoji.hot, "Stay hydrated, plan outdoor activity for mornings, keep your home cool, check on others."),
    SWING("Temperature swing", "🌡️", "A sharp change in a day. Dress in layers, protect plants and pipes, and take care if you're heat- or cold-sensitive."),
    STORM("Thunderstorm", "⛈️", "Unplug sensitive electronics, avoid open areas and delay outdoor plans."),
    FOG("Dense fog", "🌫️", "Visibility will be poor. Allow extra time, use dipped headlights and keep your distance."),
    UV("Strong UV", "🕶️", "Strong sun even if it's cool. Use SPF 30+, cover up and seek shade around midday."),
    AIR("Poor air quality", "😷", "Limit strenuous outdoor activity, keep windows closed at peak times, and check on anyone with breathing problems."),
    POLLEN("High pollen", "🤧", "Take antihistamines early, keep windows shut, shower after being outside."),
    DUST("Dust haze", "🏜️", "Fine dust in the air. Keep windows closed, wear a mask outdoors and avoid hard exercise outside."),
    PRESSURE("Falling pressure", "📉", "A fast drop in air pressure can trigger migraines and aching joints. Drink plenty of water, keep medication handy and rest if you can."),
}

private val profileTips: Map<AlertType, Map<Profile, String>> = mapOf(
    AlertType.SNOW to mapOf(
        Profile.DRIVER to "Clear every window and the roof, pack a blanket and charged phone, and leave early — or reconsider the trip.",
        Profile.CYCLIST to "Snow-covered roads are treacherous on two wheels. Plan public transport and keep the bike indoors.",
        Profile.PET_OWNER to "Check paws for ice and salt after walks, and keep outdoor time short.",
        Profile.OUTDOOR_WORKER to "Expect slippery ground and delays; check your site and kit before setting off.",
    ),
    AlertType.COLD to mapOf(
        Profile.PET_OWNER to "Keep walks short, dry paws afterwards, and don't leave pets outside for long.",
        Profile.GARDENER to "Fleece tender plants, bring pots in and drain outdoor taps.",
        Profile.OUTDOOR_WORKER to "Layer up, keep spare gloves, take warm-up breaks and watch for numbness.",
        Profile.ALLERGY to "Cold air can trigger asthma — cover your nose and mouth with a scarf outdoors and keep your reliever with you.",
    ),
    AlertType.ICE to mapOf(
        Profile.DRIVER to "Allow double time, fully de-ice, and drive gently — bridges and shaded roads freeze first.",
        Profile.CYCLIST to "Black ice is invisible. Delay the ride until it's thawed or take another route.",
        Profile.GARDENER to "Frost tonight: cover tender plants and move pots against the house wall.",
        Profile.PET_OWNER to "Icy paths are risky for you and older dogs — walk slowly and keep to gritted routes.",
    ),
    AlertType.RAIN to mapOf(
        Profile.CYCLIST to "Pack mudguards, lights and waterproofs — or switch to transit.",
        Profile.DRIVER to "Allow double stopping distance and never drive through floodwater.",
        Profile.GARDENER to "Skip watering, check drainage and protect seedlings.",
        Profile.OUTDOOR_WORKER to "Waterproofs, spare dry socks and covered materials; watch for slippery surfaces.",
    ),
    AlertType.FLOOD to mapOf(
        Profile.DRIVER to "Plan a route that avoids dips and underpasses. Turn around, don't drown.",
        Profile.PET_OWNER to "Keep pets away from floodwater and river banks — it's often contaminated.",
    ),
    AlertType.WIND to mapOf(
        Profile.CYCLIST to "Gusts make riding dangerous — choose another day or a sheltered route.",
        Profile.DRIVER to "Take care on exposed roads and bridges; high-sided vehicles are most at risk.",
        Profile.GARDENER to "Stake tall plants, secure greenhouse panels and put pots away.",
        Profile.OUTDOOR_WORKER to "Secure loose materials and avoid working at height.",
    ),
    AlertType.HEAT to mapOf(
        Profile.PET_OWNER to "Walk early or late, never leave pets in cars, and keep fresh water out.",
        Profile.OUTDOOR_WORKER to "Start earlier, take shade breaks every hour and drink before you're thirsty.",
        Profile.GARDENER to "Water at dawn or dusk, mulch beds and shade young plants.",
        Profile.ALLERGY to "Heat and humidity can make breathing harder — stay cool, hydrated and keep your reliever close.",
    ),
    AlertType.SWING to mapOf(
        Profile.MIGRAINE to "Sharp temperature changes can set off migraines and aching joints — dress in layers and stay hydrated.",
        Profile.ALLERGY to "Sudden temperature changes can trigger symptoms — keep your medication handy.",
        Profile.GARDENER to "Harden off seedlings and cover tender plants if it's dropping.",
    ),
    AlertType.STORM to mapOf(
        Profile.MIGRAINE to "Storms and the pressure change before them can trigger migraines — stay hydrated and keep medication handy.",
        Profile.ALLERGY to "Thunderstorms can set off sudden asthma, especially in pollen season — stay in with windows shut and keep your reliever close.",
        Profile.PET_OWNER to "Storms frighten many pets — keep them indoors, with a safe space and ID tags on.",
        Profile.OUTDOOR_WORKER to "Stop work in open areas and at height, and shelter well away from trees and metal.",
        Profile.CYCLIST to "Don't ride in a thunderstorm — shelter indoors until it has passed.",
    ),
    AlertType.FOG to mapOf(
        Profile.DRIVER to "Use dipped headlights, slow right down and leave big gaps.",
        Profile.CYCLIST to "Use front and rear lights, wear hi-vis and avoid busy roads.",
    ),
    AlertType.UV to mapOf(
        Profile.MIGRAINE to "Bright light can trigger migraines — wear sunglasses and a hat.",
        Profile.OUTDOOR_WORKER to "Cover up, wear a hat, reapply sunscreen every 2 hours and rotate into shade.",
        Profile.PET_OWNER to "Hot pavements burn paws — test with the back of your hand and walk in the shade.",
    ),
    AlertType.AIR to mapOf(
        Profile.ALLERGY to "Keep your reliever inhaler handy, stay in during peak hours and set the car air to recirculate.",
        Profile.CYCLIST to "Heavy breathing pulls in more pollution — ride a quieter route or go easy today.",
    ),
    AlertType.POLLEN to mapOf(
        Profile.ALLERGY to "Start medication a day early, dry washing indoors and wipe your pet's coat after walks.",
    ),
    AlertType.PRESSURE to mapOf(
        Profile.MIGRAINE to "Falling pressure is a common migraine and joint-pain trigger — drink plenty of water, eat regularly, keep medication handy and rest early if you can.",
        Profile.ALLERGY to "Pressure swings can tighten airways — keep your reliever close.",
        Profile.PET_OWNER to "Many pets get restless before a storm — keep them in with a quiet space.",
        Profile.CYCLIST to "A front is on its way — expect wind and rain to arrive quickly; plan your ride early.",
    ),
    AlertType.DUST to mapOf(
        Profile.ALLERGY to "Dust is a common asthma trigger — keep windows shut, wear a mask outside and keep your reliever close.",
        Profile.CYCLIST to "Wear a dust mask or avoid riding until the haze clears.",
        Profile.DRIVER to "Visibility can drop suddenly — use headlights and keep your distance.",
    ),
)

/**
 * Advice for this alert. If the user has several profiles that have something to say, up to two
 * are shown (each tagged with its emoji so it's clear why); otherwise the generic tip.
 */
fun AlertType.tip(profiles: Set<Profile>): String {
    val specific = profileTips[this].orEmpty()
        .filterKeys { it in profiles }
        .entries.sortedBy { it.key.ordinal }
        .take(2)
    if (specific.isEmpty()) return tip
    return specific.joinToString(" ") { "${it.key.emoji} ${it.value}" }
}

data class WeatherAlert(
    val type: AlertType,
    val severity: Severity,
    val start: LocalDate,
    val end: LocalDate,
    /** Worst value across the span (units depend on type — see [AlertEngine.detail]). */
    val peak: Double,
)

/** All numeric thresholds in one place; profiles tighten them. Absolute values, so they work worldwide. */
data class Thresholds(
    val snowW: Double = 1.0, val snowS: Double = 8.0,           // cm
    val coldW: Double = -5.0, val coldS: Double = -12.0,        // feels-like °C
    val rainW: Double = 20.0, val rainS: Double = 40.0,         // mm / day
    val gustW: Double = 65.0, val gustS: Double = 90.0,         // km/h
    val heatW: Double = 30.0, val heatS: Double = 35.0, val heatRun: Double = 27.0, // °C
    val floodW: Double = 50.0, val floodS: Double = 90.0,       // mm over 3 days
    val swingW: Double = 10.0, val swingS: Double = 15.0,       // °C change in daily mean
    val iceTmin: Double = 1.0, val frostWhenDry: Boolean = false,
    val fogW: Double = 300.0, val fogS: Double = 100.0,         // metres
    val uvW: Double = 8.0, val uvS: Double = 11.0,
    val aqiW: Int = 80, val aqiS: Int = 100,                    // European AQI
    val pollenW: Double = 50.0, val pollenS: Double = 150.0, val pollenOn: Boolean = false, // grains/m³
    val dustW: Double = 200.0, val dustS: Double = 400.0,       // µg/m³
    val pressW: Double = 16.0, val pressS: Double = 24.0, val pressOn: Boolean = false, // hPa fall in 24 h; opt-in
) {
    /** Relaxed needs more extreme weather to fire; Sensitive fires earlier. */
    fun scaled(s: Sensitivity): Thresholds {
        if (s == Sensitivity.NORMAL) return this
        val relaxed = s == Sensitivity.RELAXED
        val m = if (relaxed) 1.25 else 0.8          // amounts where bigger = worse
        val d = if (relaxed) 1.0 else -1.0          // °C shift direction
        val vis = if (relaxed) 0.7 else 1.4         // visibility: smaller = worse
        return copy(
            snowW = snowW * m, snowS = snowS * m,
            coldW = coldW - 3 * d, coldS = coldS - 3 * d,
            rainW = rainW * m, rainS = rainS * m,
            gustW = gustW * m, gustS = gustS * m,
            heatW = heatW + 2 * d, heatS = heatS + 2 * d, heatRun = heatRun + 2 * d,
            floodW = floodW * m, floodS = floodS * m,
            swingW = swingW + 3 * d, swingS = swingS + 3 * d,
            iceTmin = iceTmin - d,
            fogW = fogW * vis, fogS = fogS * vis,
            uvW = uvW + d, uvS = uvS + d,
            aqiW = (aqiW * m).roundToInt(), aqiS = (aqiS * m).roundToInt(),
            pollenW = pollenW * m, pollenS = pollenS * m,
            dustW = dustW * m, dustS = dustS * m,
            pressW = pressW * m, pressS = pressS * m,
        )
    }

    companion object {
        fun forProfiles(profiles: Set<Profile>, sensitivity: Sensitivity = Sensitivity.NORMAL): Thresholds =
            build(profiles).scaled(sensitivity)

        private fun build(profiles: Set<Profile>): Thresholds {
            var t = Thresholds()
            for (p in profiles) t = when (p) {
                Profile.DRIVER -> t.copy(
                    snowW = minOf(t.snowW, 0.5), rainW = minOf(t.rainW, 15.0),
                    iceTmin = maxOf(t.iceTmin, 2.0), fogW = maxOf(t.fogW, 1000.0), fogS = maxOf(t.fogS, 200.0),
                )
                Profile.CYCLIST -> t.copy(
                    gustW = minOf(t.gustW, 40.0), gustS = minOf(t.gustS, 65.0), rainW = minOf(t.rainW, 10.0),
                    coldW = maxOf(t.coldW, 0.0), coldS = maxOf(t.coldS, -5.0),
                    iceTmin = maxOf(t.iceTmin, 2.0), fogW = maxOf(t.fogW, 500.0),
                )
                Profile.GARDENER -> t.copy(
                    iceTmin = maxOf(t.iceTmin, 2.0), frostWhenDry = true,
                    heatW = minOf(t.heatW, 28.0), gustW = minOf(t.gustW, 55.0),
                )
                Profile.PET_OWNER -> t.copy(
                    heatW = minOf(t.heatW, 25.0), heatS = minOf(t.heatS, 32.0), coldW = maxOf(t.coldW, -2.0),
                )
                Profile.OUTDOOR_WORKER -> t.copy(
                    heatW = minOf(t.heatW, 27.0), heatS = minOf(t.heatS, 33.0), heatRun = minOf(t.heatRun, 25.0),
                    uvW = minOf(t.uvW, 7.0), uvS = minOf(t.uvS, 10.0),
                    coldW = maxOf(t.coldW, 0.0), gustW = minOf(t.gustW, 50.0),
                )
                Profile.ALLERGY -> t.copy(
                    // Asthma triggers: pollen, dirty air, dust, cold air, thunderstorms and rapid temperature changes
                    pollenOn = true, pollenW = minOf(t.pollenW, 30.0), aqiW = minOf(t.aqiW, 60), aqiS = minOf(t.aqiS, 80),
                    dustW = minOf(t.dustW, 80.0), dustS = minOf(t.dustS, 150.0),
                    coldW = maxOf(t.coldW, 0.0), swingW = minOf(t.swingW, 7.0), swingS = minOf(t.swingS, 12.0),
                )
                Profile.MIGRAINE -> t.copy(
                    pressOn = true, pressW = minOf(t.pressW, 8.0), pressS = minOf(t.pressS, 16.0),
                    swingW = minOf(t.swingW, 8.0), swingS = minOf(t.swingS, 13.0),
                    uvW = minOf(t.uvW, 7.0), heatW = minOf(t.heatW, 29.0),
                )
            }
            return t
        }
    }
}

object AlertEngine {
    private fun sev(v: Double, w: Double, s: Double, higherIsWorse: Boolean = true): Severity? = when {
        higherIsWorse && v >= s -> Severity.SEVERE
        higherIsWorse && v >= w -> Severity.WARNING
        !higherIsWorse && v <= s -> Severity.SEVERE
        !higherIsWorse && v <= w -> Severity.WARNING
        else -> null
    }

    private class Series(
        val sev: List<Severity?>,
        val values: List<Double>,
        /** Which value in a span is the "worst" one to show. */
        val pick: (List<Double>) -> Double,
    )

    /**
     * Per-day severity for every alert type. Also the basis for change tracking.
     * [past] are the days immediately before days[0] (oldest first).
     */
    fun daily(
        days: List<DayForecast>, past: List<DayForecast>, profiles: Set<Profile> = emptySet(),
        sensitivity: Sensitivity = Sensitivity.NORMAL,
    ): Map<AlertType, List<Severity?>> =
        series(days, past, Thresholds.forProfiles(profiles, sensitivity)).mapValues { it.value.sev }

    private fun series(days: List<DayForecast>, past: List<DayForecast>, th: Thresholds): Map<AlertType, Series> {
        val out = linkedMapOf<AlertType, Series>()
        fun add(type: AlertType, sev: List<Severity?>, values: List<Double>, pick: (List<Double>) -> Double = { it.max() }) {
            out[type] = Series(sev, values, pick)
        }
        val n = days.size
        val all = past + days
        val off = past.size

        // Straight daily thresholds
        add(AlertType.SNOW, days.map { sev(it.snowCm, th.snowW, th.snowS) }, days.map { it.snowCm })
        add(AlertType.COLD, days.map { sev(it.feelsMin, th.coldW, th.coldS, higherIsWorse = false) }, days.map { it.feelsMin }, { it.min() })
        add(AlertType.WIND, days.map { sev(it.gustKmh, th.gustW, th.gustS) }, days.map { it.gustKmh })
        add(
            AlertType.RAIN,
            days.map {
                when {
                    it.precipMm >= th.rainS -> Severity.SEVERE
                    it.precipMm >= th.rainW && it.precipProb >= 40 -> Severity.WARNING
                    else -> null
                }
            },
            days.map { it.precipMm },
        )
        add(
            AlertType.STORM,
            days.map { when (it.code) { 96, 99 -> Severity.SEVERE; 95 -> Severity.WARNING; else -> null } },
            days.map { 0.0 },
        )
        add(AlertType.FOG, days.map { sev(it.minVisM, th.fogW, th.fogS, higherIsWorse = false) }, days.map { it.minVisM }, { it.min() })
        add(AlertType.UV, days.map { sev(it.uvMax, th.uvW, th.uvS) }, days.map { it.uvMax })
        add(AlertType.AIR, days.map { sev(it.aqiMax.toDouble(), th.aqiW.toDouble(), th.aqiS.toDouble()) }, days.map { it.aqiMax.toDouble() })
        if (th.pressOn) {
            add(AlertType.PRESSURE, days.map { sev(it.pressureFall, th.pressW, th.pressS) }, days.map { it.pressureFall })
        }
        add(AlertType.DUST, days.map { sev(it.dustMax, th.dustW, th.dustS) }, days.map { it.dustMax })
        if (th.pollenOn) {
            add(AlertType.POLLEN, days.map { sev(it.pollenMax, th.pollenW, th.pollenS) }, days.map { it.pollenMax })
        }

        // Heat: absolute threshold, or 3+ consecutive warm days
        val heat = days.map { sev(it.tMax, th.heatW, th.heatS) }.toMutableList()
        var i = 0
        while (i < n) {
            if (days[i].tMax >= th.heatRun) {
                var j = i
                while (j < n && days[j].tMax >= th.heatRun) j++
                if (j - i >= 3) for (k in i until j) if (heat[k] == null) heat[k] = Severity.WARNING
                i = j
            } else i++
        }
        add(AlertType.HEAT, heat, days.map { it.tMax })

        // Black ice / frost: freezing night after wet weather (yesterday's or today's), or any frost for gardeners
        out[AlertType.ICE] = iceSeries(days, past, th)

        // Flood: rain accumulated over the last 3 days incl. today, and it's still raining today
        val flood = days.mapIndexed { idx, d ->
            val g = off + idx
            val sum = (maxOf(0, g - 2)..g).sumOf { all[it].precipMm }
            if (d.precipMm >= 5.0) sev(sum, th.floodW, th.floodS) else null
        }
        val floodSum = days.mapIndexed { idx, _ -> val g = off + idx; (maxOf(0, g - 2)..g).sumOf { all[it].precipMm } }
        add(AlertType.FLOOD, flood, floodSum)

        // Temperature swing vs the previous day's mean
        val mean = { d: DayForecast -> (d.tMax + d.tMin) / 2 }
        val swingVals = days.mapIndexed { idx, d ->
            val prev = all.getOrNull(off + idx - 1)
            if (prev == null) 0.0 else mean(d) - mean(prev)
        }
        add(
            AlertType.SWING,
            swingVals.map { sev(abs(it), th.swingW, th.swingS) },
            swingVals,
            { v -> v.maxByOrNull { abs(it) } ?: 0.0 },
        )

        // One card is enough: a flood alert subsumes a plain heavy-rain alert of equal or lower severity
        val rain = out[AlertType.RAIN]!!
        val fl = out[AlertType.FLOOD]!!
        val rainAdj = rain.sev.mapIndexed { idx, r ->
            val f = fl.sev[idx]
            if (r != null && f != null && f >= r) null else r
        }
        out[AlertType.RAIN] = Series(rainAdj, rain.values, rain.pick)
        // Don't pile ice on top of a snow alert
        val snow = out[AlertType.SNOW]!!
        val ic = out[AlertType.ICE]!!
        out[AlertType.ICE] = Series(ic.sev.mapIndexed { idx, s -> if (snow.sev[idx] != null) null else s }, ic.values, ic.pick)
        return out
    }

    private fun iceSeries(days: List<DayForecast>, past: List<DayForecast>, th: Thresholds): Series {
        val sev = days.mapIndexed { idx, d ->
            val prev = if (idx == 0) past.lastOrNull() else days[idx - 1]
            val wet = d.precipMm + (prev?.precipMm ?: 0.0) >= 0.5 || d.code in setOf(56, 57, 66, 67)
            when {
                d.code in setOf(66, 67) -> Severity.SEVERE
                d.tMin <= th.iceTmin && (wet || th.frostWhenDry) -> Severity.WARNING
                else -> null
            }
        }
        return Series(sev, days.map { it.tMin }, { it.min() })
    }

    fun evaluate(
        days: List<DayForecast>, past: List<DayForecast> = emptyList(), profiles: Set<Profile> = emptySet(),
        sensitivity: Sensitivity = Sensitivity.NORMAL,
    ): List<WeatherAlert> {
        if (days.isEmpty()) return emptyList()
        val out = mutableListOf<WeatherAlert>()
        for ((type, s) in series(days, past, Thresholds.forProfiles(profiles, sensitivity))) {
            var i = 0
            while (i < days.size) {
                if (s.sev[i] == null) { i++; continue }
                var j = i
                while (j < days.size && s.sev[j] != null) j++
                val span = i until j
                out += WeatherAlert(
                    type = type,
                    severity = Severity.entries[span.maxOf { s.sev[it]!!.ordinal }],
                    start = days[i].date,
                    end = days[j - 1].date,
                    peak = s.pick(span.map { s.values[it] }),
                )
                i = j
            }
        }
        return out.sortedWith(compareByDescending<WeatherAlert> { it.severity }.thenBy { it.start })
    }

    fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    fun range(a: WeatherAlert, today: LocalDate): String = range(a.start, a.end, today)

    fun range(start: LocalDate, end: LocalDate, today: LocalDate): String =
        if (start == end) dayName(start, today) else "${dayName(start, today)}–${dayName(end, today)}"

    fun detail(a: WeatherAlert, units: Units): String = when (a.type) {
        AlertType.SNOW -> "Up to ${units.snow(a.peak)} of snow"
        AlertType.COLD -> "Feels as low as ${units.temp(a.peak)}"
        AlertType.ICE -> "Overnight low of ${units.temp(a.peak)} on wet surfaces"
        AlertType.RAIN -> "Up to ${units.rain(a.peak)} of rain in a day"
        AlertType.FLOOD -> "${units.rain(a.peak)} over 3 days on already-soaked ground"
        AlertType.WIND -> "Gusts up to ${units.wind(a.peak)}"
        AlertType.HEAT -> "Highs up to ${units.temp(a.peak)}"
        AlertType.SWING ->
            if (a.peak < 0) "Temperature drops ${units.delta(-a.peak)} in a day" else "Temperature jumps ${units.delta(a.peak)} in a day"
        AlertType.STORM -> "Thunderstorms likely"
        AlertType.FOG -> "Visibility down to ${a.peak.roundToInt()} m"
        AlertType.UV -> "UV index up to ${a.peak.roundToInt()}"
        AlertType.AIR -> "Air quality index up to ${a.peak.roundToInt()} (${aqiWord(a.peak)})"
        AlertType.POLLEN -> "Up to ${a.peak.roundToInt()} grains/m³"
        AlertType.DUST -> "Airborne dust up to ${a.peak.roundToInt()} µg/m³"
        AlertType.PRESSURE -> "Air pressure dropping fast (${a.peak.roundToInt()} hPa in a day) — a common migraine and joint-pain trigger"
    }

    private fun aqiWord(v: Double) = when {
        v >= 100 -> "extremely poor"
        v >= 80 -> "very poor"
        else -> "poor"
    }
}

fun Units.temp(c: Double): String =
    "${(if (this == Units.METRIC) c else c * 9 / 5 + 32).roundToInt()}°"

/** A temperature *difference* (no +32 offset in °F). */
fun Units.delta(c: Double): String =
    "${(if (this == Units.METRIC) c else c * 9 / 5).roundToInt()}°"

fun Units.wind(kmh: Double): String =
    if (this == Units.METRIC) "${kmh.roundToInt()} km/h" else "${(kmh * 0.621371).roundToInt()} mph"

fun Units.rain(mm: Double): String =
    if (this == Units.METRIC) "${mm.roundToInt()} mm" else String.format(Locale.US, "%.1f in", mm / 25.4)

fun Units.snow(cm: Double): String =
    if (this == Units.METRIC) "${cm.roundToInt().coerceAtLeast(1)} cm" else String.format(Locale.US, "%.1f in", cm / 2.54)
