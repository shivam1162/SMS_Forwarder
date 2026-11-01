package com.personal.msgforwarder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Base Palette Constants (Raw Colors)
val PaletteDeepBlack = Color(0xFF0A0B0D)
val PaletteSurfaceDark = Color(0xFF121418)
val PaletteSurfaceElevatedDark = Color(0xFF181B20)
val PaletteBorderDark = Color(0xFF22262E)

val PaletteOffWhite = Color(0xFFF8F9FA)
val PaletteSurfaceLight = Color(0xFFFFFFFF)
val PaletteSurfaceElevatedLight = Color(0xFFEEF2F6)
val PaletteBorderLight = Color(0xFFE2E8F0)

val PaletteTextPrimaryDark = Color(0xFFF3F4F6)
val PaletteTextSecondaryDark = Color(0xFF9CA3AF)
val PaletteTextMutedDark = Color(0xFF6B7280)

val PaletteTextPrimaryLight = Color(0xFF0F172A)
val PaletteTextSecondaryLight = Color(0xFF475569)
val PaletteTextMutedLight = Color(0xFF94A3B8)

// Refined Accents (Vibrant in both dark and light modes)
val AccentEmerald = Color(0xFF10B981)
val AccentEmeraldDark = Color(0xFF065F46)
val AccentAmber = Color(0xFFF59E0B)
val AccentRed = Color(0xFFEF4444)

// Dynamic Semantic Theme Tokens
val DeepBlack: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteDeepBlack else PaletteOffWhite

val SurfaceDark: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteSurfaceDark else PaletteSurfaceLight

val SurfaceElevated: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteSurfaceElevatedDark else PaletteSurfaceElevatedLight

val BorderSubtle: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteBorderDark else PaletteBorderLight

val TextPrimary: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteTextPrimaryDark else PaletteTextPrimaryLight

val TextSecondary: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteTextSecondaryDark else PaletteTextSecondaryLight

val TextMuted: Color
    @Composable
    get() = if (LocalThemeIsDark.current) PaletteTextMutedDark else PaletteTextMutedLight

// Legacy aliases for theme compatibility
val DarkBackground: Color @Composable get() = DeepBlack
val DarkSurface: Color @Composable get() = SurfaceDark
val DarkSurfaceVariant: Color @Composable get() = SurfaceElevated
val TextWhite: Color @Composable get() = TextPrimary
val TextGray: Color @Composable get() = TextSecondary
val ActiveGreen = AccentEmerald
val WarningOrange = AccentAmber
val InactiveRed = AccentRed
