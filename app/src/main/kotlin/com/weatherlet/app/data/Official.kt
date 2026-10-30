package com.weatherlet.app.data

import com.weatherlet.app.alerts.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/** A warning issued by a national weather service (as opposed to our own model-based alerts). */
data class OfficialWarning(
    val id: String,
    val source: String,
    /** "Yellow", "Amber", "Red" for the Met Office; NWS severity words in the US. */
    val level: String,
    val severity: Severity,
    val title: String,
    val detail: String,
    val url: String?,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("src", source).put("lvl", level)
        .put("sev", severity.name).put("title", title).put("detail", detail).put("url", url)

    companion object {
        fun fromJson(o: JSONObject) = OfficialWarning(
            o.getString("id"), o.getString("src"), o.getString("lvl"), Severity.valueOf(o.getString("sev")),
            o.getString("title"), o.optString("detail"), o.optString("url").takeIf { it.isNotBlank() && it != "null" },
        )

        fun listToJson(l: List<OfficialWarning>) = JSONArray(l.map { it.toJson() }).toString()
        fun listFromJson(s: String): List<OfficialWarning> = runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/**
 * Official warnings. Covered today: United States (NWS, exact to the point) and United Kingdom
 * (Met Office, matched to the nearest warning region). Elsewhere there is simply none.
 */
object OfficialWarnings {
    private const val UA = "Weatherlet/1.0 (weatherlet-app)"

    internal val meteoAlarmCountries = setOf(
        "AT", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IL",
        "IT", "LV", "LT", "LU", "MT", "MD", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RS", "SK", "SI", "ES", "SE", "CH",
    )

    fun isSupported(p: Place) = p.countryCode == "US" || p.countryCode == "GB" || p.countryCode in meteoAlarmCountries

    suspend fun fetch(p: Place): List<OfficialWarning> = withContext(Dispatchers.IO) {
        when (p.countryCode) {
            "US" -> nws(p)
            "GB" -> metOffice(p)
            in meteoAlarmCountries -> meteoAlarm(p)
            else -> emptyList()
        }
    }

    // ---- United States -------------------------------------------------------------------------

    private fun nws(p: Place): List<OfficialWarning> {
        val point = URLEncoder.encode(String.format(Locale.US, "%.4f,%.4f", p.lat, p.lon), "UTF-8")
        val json = JSONObject(OpenMeteo.get("https://api.weather.gov/alerts/active?point=$point", UA))
        val f = json.optJSONArray("features") ?: return emptyList()
        return (0 until f.length()).mapNotNull { i ->
            val pr = f.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
            val level = pr.optString("severity", "Unknown")
            val sev = when (level) {
                "Extreme", "Severe" -> Severity.SEVERE
                "Moderate" -> Severity.WARNING
                else -> return@mapNotNull null // Minor/Unknown advisories are too noisy
            }
            OfficialWarning(
                id = pr.optString("id", "nws-$i"),
                source = "US National Weather Service",
                level = level,
                severity = sev,
                title = pr.optString("event", "Weather alert"),
                detail = pr.optString("headline"),
                url = "https://www.weather.gov/",
            )
        }
    }

    // ---- Europe (MeteoAlarm) ---------------------------------------------------------------------

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun meteoAlarm(p: Place): List<OfficialWarning> {
        val slug = fold(p.country).replace(' ', '-')
        val json = OpenMeteo.get("https://feeds.meteoalarm.org/api/v1/warnings/feeds-$slug", UA)
        return parseMeteoAlarm(json, listOfNotNull(p.admin2, p.name, p.admin), System.currentTimeMillis())
    }

    /**
     * MeteoAlarm areas come as names (districts, counties), not polygons, so we match the user's
     * district/city against them. This can miss a warning, but rarely invents one.
     */
    internal fun parseMeteoAlarm(json: String, placeNames: List<String>, nowMs: Long): List<OfficialWarning> {
        val keys = placeNames.map(::fold).filter { it.length >= 4 }.distinct()
        if (keys.isEmpty()) return emptyList()
        val warnings = JSONObject(json).optJSONArray("warnings") ?: return emptyList()
        val out = mutableListOf<OfficialWarning>()
        for (i in 0 until warnings.length()) {
            val alert = warnings.getJSONObject(i).optJSONObject("alert") ?: continue
            if (alert.optString("status") != "Actual" || alert.optString("msgType") == "Cancel") continue
            val infos = alert.optJSONArray("info") ?: continue
            val list = (0 until infos.length()).map { infos.getJSONObject(it) }
            val info = list.firstOrNull { it.optString("language").startsWith("en") } ?: list.firstOrNull() ?: continue

            val expires = runCatching { java.time.OffsetDateTime.parse(info.optString("expires")).toInstant().toEpochMilli() }.getOrNull()
            val onset = runCatching { java.time.OffsetDateTime.parse(info.optString("onset")).toInstant().toEpochMilli() }.getOrNull()
            if (expires != null && expires < nowMs) continue
            if (onset != null && onset > nowMs + 5 * 24 * 3_600_000L) continue

            val areas = info.optJSONArray("area") ?: continue
            val hit = (0 until areas.length()).any { a ->
                val desc = fold(areas.getJSONObject(a).optString("areaDesc"))
                keys.any { k -> desc.contains(k) }
            }
            if (!hit) continue

            var level = 0
            var colour = ""
            var impacts = ""
            info.optJSONArray("parameter")?.let { ps ->
                for (j in 0 until ps.length()) {
                    val pr = ps.getJSONObject(j)
                    when (pr.optString("valueName")) {
                        "awareness_level" -> pr.optString("value").split(";").map { it.trim() }.let {
                            level = it.getOrNull(0)?.toIntOrNull() ?: 0
                            colour = it.getOrNull(1).orEmpty()
                        }
                        "impacts" -> impacts = pr.optString("value")
                    }
                }
            }
            if (level < 2) continue // 1 = green = nothing to see
            val event = info.optString("event").replaceFirstChar { it.uppercase() }
            val detail = impacts.ifBlank {
                info.optString("description").replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            }
            out += OfficialWarning(
                id = alert.optString("identifier", "ma-$i"),
                source = "MeteoAlarm (${info.optString("senderName").ifBlank { "national weather service" }})",
                level = colour.replaceFirstChar { it.uppercase() },
                severity = if (level >= 3) Severity.SEVERE else Severity.WARNING,
                title = "${colour.replaceFirstChar { it.uppercase() }} warning: $event",
                detail = detail,
                url = "https://meteoalarm.org/",
            )
        }
        return out.distinctBy { it.id }
    }

    // ---- United Kingdom ------------------------------------------------------------------------

    /**
     * Met Office warning region (as named in RSS titles) -> anchor towns. Several anchors per region
     * because a single centroid misplaces coastal cities (e.g. Cardiff vs. mid-Wales).
     */
    internal val ukRegions: Map<String, List<Pair<Double, Double>>> = mapOf(
        "North East England" to listOf(54.97 to -1.61, 54.57 to -1.24),
        "North West England" to listOf(53.48 to -2.24, 53.41 to -2.99, 54.05 to -2.80, 54.89 to -2.94),
        "Yorkshire & Humber" to listOf(53.80 to -1.55, 53.38 to -1.47, 53.74 to -0.33, 53.96 to -1.08),
        "East Midlands" to listOf(52.95 to -1.15, 52.64 to -1.13, 53.23 to -0.54, 52.24 to -0.90),
        "West Midlands" to listOf(52.48 to -1.90, 52.59 to -2.13, 52.19 to -2.22, 52.71 to -2.75),
        "East of England" to listOf(52.21 to 0.12, 52.63 to 1.30, 51.89 to 0.90, 52.57 to -0.24, 51.75 to -0.34),
        "London & South East England" to listOf(51.51 to -0.12, 51.28 to 1.08, 50.82 to -0.14, 50.90 to -1.40, 51.45 to -1.00, 51.75 to -1.26),
        "South West England" to listOf(50.72 to -3.53, 50.37 to -4.14, 51.45 to -2.58, 50.26 to -5.05, 51.38 to -2.36, 50.71 to -2.44),
        "Wales" to listOf(51.48 to -3.18, 51.62 to -3.94, 52.41 to -4.08, 53.14 to -4.27, 53.05 to -2.99),
        "Central, Tayside & Fife" to listOf(56.46 to -2.97, 56.12 to -3.94, 56.34 to -2.80, 56.40 to -3.43),
        "Strathclyde" to listOf(55.86 to -4.25, 56.41 to -5.47, 55.61 to -4.50, 55.95 to -4.77),
        "SW Scotland, Lothian Borders" to listOf(55.95 to -3.19, 55.07 to -3.61, 55.61 to -2.79),
        "Grampian" to listOf(57.15 to -2.09, 57.65 to -3.32),
        "Highlands & Eilean Siar" to listOf(57.48 to -4.22, 58.44 to -3.09, 57.60 to -7.00, 56.82 to -5.11),
        "Orkney & Shetland" to listOf(58.98 to -2.96, 60.15 to -1.15),
        "Northern Ireland" to listOf(54.60 to -5.93, 54.99 to -7.31, 54.35 to -6.65),
    )

    internal fun nearestRegion(lat: Double, lon: Double): String =
        ukRegions.minBy { (_, anchors) ->
            anchors.minOf { (aLat, aLon) -> hypot(aLat - lat, (aLon - lon) * cos(Math.toRadians(lat))) }
        }.key

    private fun metOffice(p: Place): List<OfficialWarning> {
        val xml = OpenMeteo.get("https://www.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/UK", UA)
        return parseMetOffice(xml, nearestRegion(p.lat, p.lon))
    }

    internal fun parseMetOffice(xml: String, region: String): List<OfficialWarning> {
        fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
        fun tag(item: String, name: String) =
            Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)?.trim()?.let(::unescape)

        val titleRe = Regex("""(Yellow|Amber|Red) warning of (.+?) affecting (.+)""")
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val item = m.groupValues[1]
            val title = tag(item, "title") ?: return@mapNotNull null
            val t = titleRe.find(title) ?: return@mapNotNull null
            val (level, hazard, where) = t.destructured
            if (where.trim() != region) return@mapNotNull null
            val desc = tag(item, "description").orEmpty()
            OfficialWarning(
                id = tag(item, "guid") ?: title,
                source = "Met Office",
                level = level,
                severity = if (level == "Yellow") Severity.WARNING else Severity.SEVERE,
                title = "$level warning of $hazard",
                detail = desc.substringAfter(" valid ", "").let { if (it.isBlank()) desc else "Valid $it" },
                url = tag(item, "link"),
            )
        }.toList()
    }
}
