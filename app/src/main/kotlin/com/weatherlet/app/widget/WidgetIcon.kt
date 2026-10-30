package com.weatherlet.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import com.weatherlet.app.icons.WeatherIcons

/** The same hand-drawn artwork as the app, rendered at the widget's density. */
@Composable
internal fun WidgetIcon(code: Int, isDay: Boolean, size: Dp, modifier: GlanceModifier = GlanceModifier) {
    val density = LocalContext.current.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceIn(24, 256)
    Image(
        provider = ImageProvider(WeatherIcons.bitmap(code, isDay, px)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** The drawn "all clear" tick, for widget chips and tiles. */
@Composable
internal fun WidgetAllClear(size: Dp, modifier: GlanceModifier = GlanceModifier) {
    val density = LocalContext.current.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceIn(24, 256)
    Image(
        provider = ImageProvider(com.weatherlet.app.icons.StatusIcons.allClear(px)),
        contentDescription = "All clear",
        modifier = modifier.size(size),
    )
}
