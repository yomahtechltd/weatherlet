package com.weatherlet.app.alerts

enum class Health { GOOD, WARN, BAD }

data class HealthLine(val label: String, val value: String, val level: Health)

/** Plain-language report on whether Android is letting Weatherlet's background jobs run on time. */
data class BackgroundReport(val lines: List<HealthLine>, val verdict: Health, val advice: String?)

object BackgroundHealth {
    const val BUCKET_ACTIVE = 10
    const val BUCKET_WORKING_SET = 20
    const val BUCKET_FREQUENT = 30
    const val BUCKET_RARE = 40
    const val BUCKET_RESTRICTED = 45

    fun ago(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 2 -> "just now"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} days ago"
        }
    }

    fun bucketName(b: Int) = when {
        b <= BUCKET_ACTIVE -> "Active"
        b <= BUCKET_WORKING_SET -> "Working set"
        b <= BUCKET_FREQUENT -> "Frequent"
        b < BUCKET_RESTRICTED -> "Rare"
        else -> "Restricted"
    }

    /**
     * @param lastSyncAt when the ~3-hourly refresh last ran (0 = never)
     * @param dailyDelayMin how late the daily-alert job last started (-1 = hasn't run since we began tracking)
     * @param unrestricted battery optimisation is switched off for the app
     * @param bucket the system's app-standby bucket, or -1 if unknown
     */
    fun report(
        now: Long, lastSyncAt: Long, dailyDelayMin: Int, dailyRunAt: Long, alertsOn: Boolean,
        unrestricted: Boolean, bucket: Int,
    ): BackgroundReport {
        val lines = mutableListOf<HealthLine>()
        val advice = mutableListOf<String>()

        val syncAge = now - lastSyncAt
        lines += when {
            lastSyncAt == 0L -> HealthLine("Last background refresh", "Not yet", Health.WARN)
            syncAge <= 6 * 3_600_000L -> HealthLine("Last background refresh", ago(syncAge), Health.GOOD)
            syncAge <= 12 * 3_600_000L -> HealthLine("Last background refresh", ago(syncAge), Health.WARN)
            else -> HealthLine("Last background refresh", ago(syncAge), Health.BAD).also {
                advice += "Background refreshes aren't running regularly."
            }
        }

        if (alertsOn) {
            lines += when {
                dailyDelayMin < 0 -> HealthLine("Last daily alert run", "Not yet — waiting for the next one", Health.GOOD)
                dailyDelayMin <= 20 -> HealthLine("Last daily alert run", "On time (${ago(now - dailyRunAt)})", Health.GOOD)
                dailyDelayMin <= 90 -> HealthLine("Last daily alert run", "$dailyDelayMin min late (${ago(now - dailyRunAt)})", Health.WARN)
                else -> HealthLine("Last daily alert run", "${dailyDelayMin / 60} h ${dailyDelayMin % 60} min late", Health.BAD).also {
                    advice += "Your daily alert was delayed by Android."
                }
            }
        }

        lines += if (unrestricted) HealthLine("Battery optimisation", "Off for Weatherlet", Health.GOOD)
        else HealthLine("Battery optimisation", "On — Android may delay alerts", Health.WARN)
        if (!unrestricted) advice += "Allow Weatherlet to run unrestricted so alerts arrive on time."

        if (bucket >= 0) {
            val level = when {
                bucket <= BUCKET_FREQUENT -> Health.GOOD
                bucket < BUCKET_RESTRICTED -> Health.WARN
                else -> Health.BAD
            }
            lines += HealthLine("Android's activity level for the app", bucketName(bucket), level)
            if (level != Health.GOOD) advice += "Android has put the app in a low-activity group; opening it more often moves it back."
        }

        val verdict = lines.maxOf { it.level }
        return BackgroundReport(lines, verdict, advice.firstOrNull().takeIf { verdict != Health.GOOD }?.let { advice.joinToString(" ") })
    }
}
