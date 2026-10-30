package com.weatherlet.app.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

data class Place(
    val name: String,
    val admin: String?,
    val country: String,
    val lat: Double,
    val lon: Double,
    val countryCode: String? = null,
    val nickname: String? = null,
    /** The "follow my location" place; replaced when the user has moved. */
    val isCurrent: Boolean = false,
    /** County / district / province; helps match EU warning areas. */
    val admin2: String? = null,
) {
    /** Stable identity (≈1 km grid) used for caches and snapshots. */
    val key: String get() = String.format(Locale.US, "%.2f,%.2f", lat, lon)

    /** What the user sees as the name: their nickname, else the city. */
    val title: String get() = nickname?.takeIf { it.isNotBlank() } ?: name

    val label: String
        get() = listOfNotNull(name, admin?.takeIf { it != name }, country.takeIf { it.isNotBlank() })
            .joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("admin", admin).put("country", country)
        .put("lat", lat).put("lon", lon)
        .put("cc", countryCode).put("nick", nickname).put("cur", isCurrent).put("a2", admin2)

    companion object {
        fun fromJson(o: JSONObject): Place {
            val country = o.optString("country", "")
            val cc = o.optString("cc").takeIf { it.isNotBlank() && it != "null" }
                ?: when (country) { // places saved before country codes existed
                    "United Kingdom" -> "GB"
                    "United States" -> "US"
                    else -> null
                }
            return Place(
                name = o.getString("name"),
                admin = o.optString("admin").takeIf { it.isNotBlank() && it != "null" },
                country = country,
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                countryCode = cc,
                nickname = o.optString("nick").takeIf { it.isNotBlank() && it != "null" },
                isCurrent = o.optBoolean("cur", false),
                admin2 = o.optString("a2").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}

data class Current(
    val temp: Double,
    val feels: Double,
    val code: Int,
    val windKmh: Double,
    val humidity: Int,
    val isDay: Boolean,
)

data class HourForecast(
    val time: LocalDateTime,
    val temp: Double,
    val precipMm: Double,
    val precipProb: Int,
    val code: Int,
    val gustKmh: Double,
    val visibilityM: Double,
)

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val feelsMin: Double,
    val precipMm: Double,
    val snowCm: Double,
    val precipProb: Int,
    val windKmh: Double,
    val gustKmh: Double,
    val uvMax: Double = 0.0,
    val aqiMax: Int = 0,
    val pollenMax: Double = 0.0,
    /** Lowest visibility between 05:00 and 22:00, metres. */
    val minVisM: Double = 20_000.0,
    /** Peak airborne dust (µg/m³) — harmattan, Saharan dust, desert storms. */
    val dustMax: Double = 0.0,
    /** Biggest 24-hour fall in sea-level pressure (hPa) ending on this day; 0 when unknown. */
    val pressureFall: Double = 0.0,
)

data class Forecast(
    val place: Place,
    val current: Current,
    /** Today first, then the next 5 days. */
    val days: List<DayForecast>,
    /** The 2 days before today (used for "ground already saturated" and temperature swings). */
    val past: List<DayForecast>,
    val hours: List<HourForecast>,
    val fetchedAt: Long,
) {
    companion object {
        fun parse(place: Place, raw: String, fetchedAt: Long): Forecast {
            val root = JSONObject(raw)
            val o = root.optJSONObject("weather") ?: root // older cache entries were the bare weather object
            val c = o.getJSONObject("current")
            val today = LocalDate.parse(c.getString("time").take(10))

            // --- hourly weather -> list + per-day lowest daytime visibility
            val hours = mutableListOf<HourForecast>()
            val fallByDay = mutableMapOf<LocalDate, Double>()
            o.optJSONObject("hourly")?.let { h ->
                val t = h.getJSONArray("time")
                fun dbl(k: String, i: Int, def: Double) = h.optJSONArray(k)?.optDouble(i, def) ?: def
                val pres = h.optJSONArray("pressure_msl")
                for (i in 0 until t.length()) {
                    val time = LocalDateTime.parse(t.getString(i))
                    // Fall over the previous 24 h (hourly data, so 24 steps back)
                    if (pres != null && i >= 24 && !pres.isNull(i) && !pres.isNull(i - 24)) {
                        val fall = pres.optDouble(i - 24) - pres.optDouble(i)
                        fallByDay.merge(time.toLocalDate(), fall, ::maxOf)
                    }
                    if (time.toLocalDate() < today) continue
                    hours += HourForecast(
                        time = time,
                        temp = dbl("temperature_2m", i, 0.0),
                        precipMm = dbl("precipitation", i, 0.0),
                        precipProb = dbl("precipitation_probability", i, 0.0).toInt(),
                        code = dbl("weather_code", i, 0.0).toInt(),
                        gustKmh = dbl("wind_gusts_10m", i, 0.0),
                        visibilityM = dbl("visibility", i, 20_000.0),
                    )
                }
            }
            val visByDay = hours.filter { it.time.hour in 5..22 }
                .groupBy { it.time.toLocalDate() }
                .mapValues { (_, v) -> v.minOf { it.visibilityM } }

            // --- air quality + pollen -> per-day maxima (pollen is Europe-only, so often null)
            val aqiByDay = mutableMapOf<LocalDate, Int>()
            val pollenByDay = mutableMapOf<LocalDate, Double>()
            val dustByDay = mutableMapOf<LocalDate, Double>()
            root.optJSONObject("air")?.optJSONObject("hourly")?.let { h ->
                val t = h.getJSONArray("time")
                val pollenKeys = listOf("alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen")
                for (i in 0 until t.length()) {
                    val day = LocalDate.parse(t.getString(i).take(10))
                    h.optJSONArray("european_aqi")?.takeIf { !it.isNull(i) }?.let {
                        aqiByDay.merge(day, it.optInt(i, 0), ::maxOf)
                    }
                    h.optJSONArray("dust")?.takeIf { !it.isNull(i) }?.let {
                        dustByDay.merge(day, it.optDouble(i, 0.0), ::maxOf)
                    }
                    for (k in pollenKeys) {
                        h.optJSONArray(k)?.takeIf { !it.isNull(i) }?.let {
                            pollenByDay.merge(day, it.optDouble(i, 0.0), ::maxOf)
                        }
                    }
                }
            }

            // --- daily
            val d = o.getJSONObject("daily")
            val times = d.getJSONArray("time")
            fun dbl(key: String, i: Int, def: Double = 0.0) = d.optJSONArray(key)?.optDouble(i, def) ?: def
            val all = (0 until times.length()).map { i ->
                val date = LocalDate.parse(times.getString(i))
                DayForecast(
                    date = date,
                    code = dbl("weather_code", i).toInt(),
                    tMax = dbl("temperature_2m_max", i),
                    tMin = dbl("temperature_2m_min", i),
                    feelsMin = dbl("apparent_temperature_min", i),
                    precipMm = dbl("precipitation_sum", i),
                    snowCm = dbl("snowfall_sum", i),
                    precipProb = dbl("precipitation_probability_max", i).toInt(),
                    windKmh = dbl("wind_speed_10m_max", i),
                    gustKmh = dbl("wind_gusts_10m_max", i),
                    uvMax = dbl("uv_index_max", i),
                    aqiMax = aqiByDay[date] ?: 0,
                    pollenMax = pollenByDay[date] ?: 0.0,
                    minVisM = visByDay[date] ?: 20_000.0,
                    dustMax = dustByDay[date] ?: 0.0,
                    pressureFall = (fallByDay[date] ?: 0.0).coerceAtLeast(0.0),
                )
            }
            return Forecast(
                place = place,
                current = Current(
                    temp = c.optDouble("temperature_2m", 0.0),
                    feels = c.optDouble("apparent_temperature", 0.0),
                    code = c.optInt("weather_code", 0),
                    windKmh = c.optDouble("wind_speed_10m", 0.0),
                    humidity = c.optInt("relative_humidity_2m", 0),
                    isDay = c.optInt("is_day", 1) == 1,
                ),
                days = all.filter { it.date >= today },
                past = all.filter { it.date < today },
                hours = hours,
                fetchedAt = fetchedAt,
            )
        }
    }
}
