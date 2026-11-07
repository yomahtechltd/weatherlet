package com.weatherlet.app.alerts

import android.os.Build

/** Newer emoji show as empty boxes on older Android; use a simpler one there. */
object Emoji {
    fun pick(new: String, old: String, minApi: Int): String = if (Build.VERSION.SDK_INT >= minApi) new else old

    val cold get() = pick("🥶", "❄️", 28)      // Emoji 11 → Android 9
    val ice get() = pick("🧊", "❄️", 29)       // Emoji 12 → Android 10
    val hot get() = pick("🥵", "☀️", 28)
    val basket get() = pick("🧺", "👕", 28)
}
