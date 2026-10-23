package com.promptly.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PromptlyGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F2B5),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF55624C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CA),
    onSecondaryContainer = Color(0xFF131F0C),
    tertiary = PromptlyAccent,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFDDD3C4),
)

private val DarkColors = darkColorScheme(
    primary = PromptlyGreenLight,
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFB8F2B5),
    secondary = Color(0xFFBDCBA5),
    onSecondary = Color(0xFF283419),
    secondaryContainer = Color(0xFF3E4B2E),
    onSecondaryContainer = Color(0xFFD9E7CA),
    tertiary = PromptlyAccent,
    onTertiary = Color(0xFF3D2C00),
    tertiaryContainer = Color(0xFF6E4F00),
    onTertiaryContainer = Color(0xFFFFE08A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF4D4639),
)

/**
 * App theme. Honors user-chosen dark mode (system / on / off) and optionally
 * Material You dynamic colors on Android 12+.
 */
@Composable
fun PromptlyTheme(
    darkMode: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkMode -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PromptlyTypography,
        content = content
    )
}
