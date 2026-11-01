package com.personal.msgforwarder.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalThemeIsDark = compositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = AccentEmerald,
    onPrimary = PaletteDeepBlack,
    primaryContainer = AccentEmeraldDark,
    onPrimaryContainer = PaletteTextPrimaryDark,
    secondary = AccentEmerald,
    onSecondary = PaletteDeepBlack,
    background = PaletteDeepBlack,
    onBackground = PaletteTextPrimaryDark,
    surface = PaletteSurfaceDark,
    onSurface = PaletteTextPrimaryDark,
    surfaceVariant = PaletteSurfaceElevatedDark,
    onSurfaceVariant = PaletteTextSecondaryDark,
    outline = PaletteBorderDark,
    error = AccentRed,
    onError = PaletteDeepBlack
)

private val LightColorScheme = lightColorScheme(
    primary = AccentEmerald,
    onPrimary = PaletteOffWhite,
    primaryContainer = AccentEmeraldDark,
    onPrimaryContainer = PaletteOffWhite,
    secondary = AccentEmerald,
    onSecondary = PaletteOffWhite,
    background = PaletteOffWhite,
    onBackground = PaletteTextPrimaryLight,
    surface = PaletteSurfaceLight,
    onSurface = PaletteTextPrimaryLight,
    surfaceVariant = PaletteSurfaceElevatedLight,
    onSurfaceVariant = PaletteTextSecondaryLight,
    outline = PaletteBorderLight,
    error = AccentRed,
    onError = PaletteOffWhite
)

@Composable
fun AppTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalThemeIsDark provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
