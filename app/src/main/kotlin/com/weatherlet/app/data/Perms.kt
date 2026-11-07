package com.weatherlet.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/** Permission checks that behave correctly on every supported Android version. */
object Perms {
    /** Android 13+ asks at runtime; older versions just have notifications on or off in system settings. */
    fun canNotify(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Whether we need to show the system permission prompt at all. */
    val needsNotificationPrompt: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** Android 10+ has a separate "all the time" location permission; before that, location is all-or-nothing. */
    val hasSeparateBackgroundLocation: Boolean get() = Build.VERSION.SDK_INT >= 29
}
