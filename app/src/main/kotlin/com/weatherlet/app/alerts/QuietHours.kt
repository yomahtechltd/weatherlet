package com.weatherlet.app.alerts

object QuietHours {
    /** Is [now] (minutes since midnight) inside [start, end)? Handles ranges that wrap past midnight. */
    fun contains(start: Int, end: Int, now: Int): Boolean = when {
        start == end -> false
        start < end -> now in start until end
        else -> now >= start || now < end
    }
}
