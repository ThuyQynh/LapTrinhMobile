package com.team.smartnutrition.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Theme chính của Smart Nutrition.
 * Dùng MaterialTheme.colorScheme.xxx trong Composable để lấy màu.
 *
 * VÍ DỤ:
 *   Text(color = MaterialTheme.colorScheme.primary)        // Emerald Green
 *   Card(colors = CardDefaults.cardColors(
 *       containerColor = MaterialTheme.colorScheme.surface  // Nền card
 *   ))
 */

private val LightColorScheme = lightColorScheme(
    primary = Emerald500,
    onPrimary = LightSurface,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,

    secondary = Sky500,
    onSecondary = LightSurface,
    secondaryContainer = Sky400.copy(alpha = 0.2f),
    onSecondaryContainer = Sky600,

    tertiary = Amber500,
    onTertiary = LightSurface,
    tertiaryContainer = Amber400.copy(alpha = 0.2f),
    onTertiaryContainer = Amber600,

    error = ErrorRed,
    onError = LightSurface,

    background = LightBackground,
    onBackground = LightOnBackground,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Emerald900,
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,

    secondary = Sky400,
    onSecondary = Sky600,
    secondaryContainer = Sky600.copy(alpha = 0.3f),
    onSecondaryContainer = Sky400,

    tertiary = Amber400,
    onTertiary = Amber600,
    tertiaryContainer = Amber600.copy(alpha = 0.3f),
    onTertiaryContainer = Amber400,

    error = ErrorRedDark,
    onError = DarkBackground,

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
)

@Composable
fun SmartNutritionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Đổi màu status bar cho phù hợp theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmartNutritionTypography,
        content = content
    )
}
