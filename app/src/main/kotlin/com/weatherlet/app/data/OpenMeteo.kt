package com.weatherlet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.weatherlet.app.alerts.RainOutlook
import com.weatherlet.app.alerts.RainStep
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open-Meteo: free, keyless, worldwide forecasts, air quality and city geocoding. */
object OpenMeteo {
    fun get(url: String, userAgent: String = "Weatherlet/1.0 (Android)"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", userAgent)
        try {
            if (c.responseCode !in 200..299) error("Server returned ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=en&format=json")
        )
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Place(
                name = it.getString("name"),
                admin = it.optString("admin1").takeIf { s -> s.isNotBlank() },
                country = it.optString("country", ""),
                lat = it.getDouble("latitude"),
                lon = it.getDouble("longitude"),
                countryCode = it.optString("country_code").takeIf { s -> s.isNotBlank() },
                admin2 = it.optString("admin2").takeIf { s -> s.isNotBlank() },
            )
        }
    }

    /** Next ~2 hours of precipitation in 15-minute steps, for the "rain soon" nudge. */
    suspend fun fetchSoon(p: Place): RainOutlook = withContext(Dispatchers.IO) {
        val o = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                    "&minutely_15=precipitation&forecast_minutely_15=10&past_minutely_15=2" +
                    "&current=temperature_2m&timezone=auto"
            )
        )
        val m = o.getJSONObject("minutely_15")
        val t = m.getJSONArray("time")
        val v = m.getJSONArray("precipitation")
        val c = o.getJSONObject("current")
        RainOutlook(
            now = java.time.LocalDateTime.parse(c.getString("time")),
            tempC = c.optDouble("temperature_2m", 10.0),
            steps = (0 until t.length()).mapNotNull { i ->
                if (v.isNull(i)) null else RainStep(java.time.LocalDateTime.parse(t.getString(i)), v.getDouble(i))
            },
        )
    }

    /**
     * Weather (daily + hourly, with 2 past days) and air quality, combined into one JSON string so
     * the widget, worker and app all parse exactly the same cached thing. Air quality is optional.
     */
    suspend fun fetchRaw(p: Place): String = withContext(Dispatchers.IO) {
        val daily = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min", "apparent_temperature_min",
            "precipitation_sum", "snowfall_sum", "precipitation_probability_max",
            "wind_speed_10m_max", "wind_gusts_10m_max", "uv_index_max",
        ).joinToString(",")
        val hourly = "temperature_2m,precipitation,precipitation_probability,weather_code,wind_gusts_10m,visibility,pressure_msl"
        val current = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
        val weather = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                "&current=$current&daily=$daily&hourly=$hourly" +
                "&forecast_days=6&past_days=2&timezone=auto&wind_speed_unit=kmh"
        )
        val air = runCatching {
            get(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${p.lat}&longitude=${p.lon}" +
                    "&hourly=european_aqi,dust,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen" +
                    "&forecast_days=6&timezone=auto"
            )
        }.getOrNull()
        JSONObject().put("weather", JSONObject(weather)).also { if (air != null) it.put("air", JSONObject(air)) }.toString()
    }
}
