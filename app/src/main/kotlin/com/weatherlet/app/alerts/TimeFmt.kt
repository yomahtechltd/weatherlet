package com.weatherlet.app.alerts

import java.time.LocalDateTime

/**
 * Every clock time the app shows goes through here, so the 12-hour / 24-hour choice in Settings
 * applies everywhere: notifications, widgets, lists and pickers. 12-hour is the default.
 */
object TimeFmt {
    /** Mirrors the Settings choice; [com.weatherlet.app.data.Store] keeps it in sync. */
    @Volatile var use24h: Boolean = false

    /** "8:30 PM" or "20:30". */
    fun time(hour: Int, minute: Int): String =
        if (use24h) "%02d:%02d".format(hour, minute)
        else "%d:%02d %s".format(h12(hour), minute, if (hour < 12) "AM" else "PM")

    fun time(t: LocalDateTime): String = time(t.hour, t.minute)

    /** Minutes since midnight → "8:30 PM". */
    fun minutes(mins: Int): String = time(mins / 60 % 24, mins % 60)

    /** A whole hour, compact: "8 PM" or "20:00" (for chips and the hourly widget). */
    fun hour(hour: Int): String =
        if (use24h) "%02d:00".format(hour) else "%d %s".format(h12(hour), if (hour < 12) "AM" else "PM")

    private fun h12(h: Int) = if (h % 12 == 0) 12 else h % 12
}
