package com.weatherlet.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Where a widget tap should land inside the app: "alerts", "hourly", or "day:N" (N = days from today).
 * Each target gets its own URI so Android keeps the PendingIntents for different taps separate.
 */
object DeepLink {
    const val EXTRA = "weatherlet.target"

    fun intent(ctx: Context, target: String): Intent =
        Intent(ctx, MainActivity::class.java)
            .setData(Uri.parse("weatherlet://open/$target"))
            .putExtra(EXTRA, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun dayIndex(target: String?): Int? = target?.removePrefix("day:")?.takeIf { target.startsWith("day:") }?.toIntOrNull()
}
