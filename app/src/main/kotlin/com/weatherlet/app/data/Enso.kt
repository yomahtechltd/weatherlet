package com.weatherlet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class EnsoPhase(val label: String) { EL_NINO("El Niño"), LA_NINA("La Niña"), NEUTRAL("Neutral") }

/**
 * Latest Oceanic Niño Index from NOAA's Climate Prediction Center (a 3-month average of Pacific sea
 * temperature anomaly). ≥ +0.5 is El Niño, ≤ −0.5 is La Niña.
 */
data class EnsoStatus(
    val season: String,
    val year: Int,
    val oni: Double,
    /** Change in ONI over the last three seasons; positive = warming. */
    val trend: Double,
) {
    val phase: EnsoPhase get() = when {
        oni >= 0.5 -> EnsoPhase.EL_NINO
        oni <= -0.5 -> EnsoPhase.LA_NINA
        else -> EnsoPhase.NEUTRAL
    }

    val strength: String get() = when (kotlin.math.abs(oni)) {
        in 0.0..0.49 -> ""
        in 0.5..0.99 -> "weak"
        in 1.0..1.49 -> "moderate"
        in 1.5..1.99 -> "strong"
        else -> "very strong"
    }

    fun toJson(): JSONObject = JSONObject().put("s", season).put("y", year).put("o", oni).put("t", trend)

    companion object {
        fun fromJson(o: JSONObject) = EnsoStatus(o.getString("s"), o.getInt("y"), o.getDouble("o"), o.getDouble("t"))

        /** Parse NOAA's oni.ascii.txt ("SEAS YR TOTAL ANOM" rows). */
        fun parse(text: String): EnsoStatus? {
            val rows = text.lineSequence().mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4 && p[1].toIntOrNull() != null && p[3].toDoubleOrNull() != null)
                    Triple(p[0], p[1].toInt(), p[3].toDouble()) else null
            }.toList()
            val last = rows.lastOrNull() ?: return null
            val back = rows.getOrNull(rows.size - 4) ?: rows.first()
            return EnsoStatus(last.first, last.second, last.third, last.third - back.third)
        }
    }
}

object Enso {
    suspend fun fetch(): EnsoStatus? = withContext(Dispatchers.IO) {
        runCatching {
            EnsoStatus.parse(OpenMeteo.get("https://www.cpc.ncep.noaa.gov/data/indices/oni.ascii.txt"))
        }.getOrNull()
    }

    /** Typical tendencies for the region around [lat],[lon]. These are tendencies, not forecasts. */
    fun regionalNote(lat: Double, lon: Double, phase: EnsoPhase): String {
        val warm = phase == EnsoPhase.EL_NINO
        fun pick(elNino: String, laNina: String) = if (warm) elNino else laNina
        return when {
            lat in -12.0..16.0 && lon in 28.0..52.0 -> pick(
                "East Africa tends to see wetter short rains (Oct–Dec), with flood risk in places.",
                "East Africa tends towards drier short rains and drought risk.",
            )
            lat in 0.0..16.0 && lon in -20.0..28.0 -> pick(
                "West Africa and the Sahel lean towards a drier or erratic rainy season and hotter spells. The link is weaker than in East Africa.",
                "West Africa's rainy season tends to be a little wetter than usual, though the link is weak.",
            )
            lat in -35.0..-12.0 && lon in 10.0..42.0 -> pick(
                "Southern Africa tends towards a hotter, drier summer (Nov–Mar) with drought risk.",
                "Southern Africa tends towards a wetter summer, with flooding risk in places.",
            )
            lat in 5.0..35.0 && lon in 60.0..95.0 -> pick(
                "South Asia often sees a weaker or late monsoon and hotter spells.",
                "South Asia often sees a stronger monsoon and heavier rain.",
            )
            lat in -45.0..20.0 && lon in 95.0..155.0 -> pick(
                "Southeast Asia and Australia tend towards drier conditions, with higher drought and bushfire risk.",
                "Southeast Asia and Australia tend towards wetter conditions and flood risk.",
            )
            lat in -20.0..5.0 && lon in -85.0..-70.0 -> pick(
                "Coastal Peru and Ecuador can see heavy rain and flooding.",
                "Coastal Peru and Ecuador tend to be drier and cooler.",
            )
            lat in -45.0..-20.0 && lon in -75.0..-45.0 -> pick(
                "Southern Brazil, Uruguay and northern Argentina tend to be wetter than normal.",
                "Southern Brazil, Uruguay and northern Argentina tend to be drier than normal.",
            )
            lat in 25.0..50.0 && lon in -125.0..-65.0 -> pick(
                "The southern US and California tend to be wetter in winter; the far north is milder and drier.",
                "The southern US tends to be drier and warmer in winter; the Pacific north-west wetter.",
            )
            lat in 35.0..72.0 && lon in -25.0..45.0 -> pick(
                "Europe's link is weak and uncertain. Some El Niño winters run a little colder and drier in the north-west, but the signal is small and local weather dominates.",
                "Europe's link is weak and uncertain. La Niña winters can lean a little milder and wetter in the north-west, but local weather dominates.",
            )
            else -> pick(
                "Effects vary a lot by region. Globally, El Niño years tend to run warmer than average.",
                "Effects vary a lot by region. Globally, La Niña years tend to run slightly cooler.",
            )
        }
    }
}
