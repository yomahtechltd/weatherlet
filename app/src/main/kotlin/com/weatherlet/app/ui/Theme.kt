package com.weatherlet.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weatherlet.app.alerts.Severity

@Composable
fun WeatherletTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Wallpaper-based colours need Android 12; older phones get Weatherlet's own blue palette.
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9DC3FF), onPrimary = Color(0xFF00315F), primaryContainer = Color(0xFF1F4A87), surface = Color(0xFF101319), surfaceContainerLow = Color(0xFF181C23), surfaceContainerHigh = Color(0xFF262A32), surfaceContainerHighest = Color(0xFF30353D))
        else -> lightColorScheme(primary = Color(0xFF1F5FD0), primaryContainer = Color(0xFFD8E6FF), surface = Color(0xFFF8F9FF), surfaceContainerLow = Color(0xFFEEF1F9), surfaceContainerHigh = Color(0xFFE2E6EF), surfaceContainerHighest = Color(0xFFDCE0E9))
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}

data class AlertPalette(val container: Color, val onContainer: Color, val accent: Color)

@Composable
fun alertPalette(severity: Severity): AlertPalette {
    val dark = isSystemInDarkTheme()
    return when (severity) {
        Severity.WARNING ->
            if (dark) AlertPalette(Color(0xFF5A3B00), Color(0xFFFFDDB0), Color(0xFFFFB84D))
            else AlertPalette(Color(0xFFFFE2B8), Color(0xFF3B2600), Color(0xFFB36B00))
        Severity.SEVERE ->
            if (dark) AlertPalette(Color(0xFF6B1A12), Color(0xFFFFDAD6), Color(0xFFFF8A80))
            else AlertPalette(Color(0xFFFFD6D2), Color(0xFF410002), Color(0xFFB3261E))
    }
}
