/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light Theme Colors
private val md_theme_light_primary = Color(0xFF707070) // Modern Gray
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFF707070).copy(alpha = 0.3f)
private val md_theme_light_onPrimaryContainer = Color.Black
private val md_theme_light_secondary = Color(0xFF4D6357)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFF707070).copy(alpha = 0.15f)
private val md_theme_light_onSecondaryContainer = Color.Black
private val md_theme_light_tertiary = Color(0xFF3D6373)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFC1E8FB)
private val md_theme_light_onTertiaryContainer = Color(0xFF001F29)
private val md_theme_light_error = Color(0xFFD32F2F) // Material Design Red 700
private val md_theme_light_errorContainer = Color(0xFFFFDAD6)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_onErrorContainer = Color(0xFF410002)
private val md_theme_light_background = Color(0xFFFBFDF9)
private val md_theme_light_onBackground = Color.Black
private val md_theme_light_surface = Color(0xFFFBFDF9)
private val md_theme_light_onSurface = Color.Black
private val md_theme_light_surfaceVariant = Color(0xFFDBE5DD)
private val md_theme_light_onSurfaceVariant = Color(0xFF424242) // Slightly lighter for secondary text
private val md_theme_light_outline = Color(0xFF707973)
private val md_theme_light_inverseOnSurface = Color(0xFFEFF1ED)
private val md_theme_light_inverseSurface = Color(0xFF2E312F)
private val md_theme_light_inversePrimary = Color(0xFF0D0D0D) // Modern Gray dark
private val md_theme_light_surfaceTint = Color(0xFF707070) // Modern Gray
private val md_theme_light_outlineVariant = Color(0xFFBFC9C2)
private val md_theme_light_scrim = Color(0xFF000000)

// Dark Theme Colors
private val md_theme_dark_primary = Color(0xFF0D0D0D) // Modern Gray dark
private val md_theme_dark_onPrimary = Color.White
private val md_theme_dark_primaryContainer = Color(0xFF0D0D0D).copy(alpha = 0.3f)
private val md_theme_dark_onPrimaryContainer = Color.White
private val md_theme_dark_secondary = Color(0xFFB3CCBE)
private val md_theme_dark_onSecondary = Color(0xFF1F352A)
private val md_theme_dark_secondaryContainer = Color(0xFF0D0D0D).copy(alpha = 0.15f)
private val md_theme_dark_onSecondaryContainer = Color.White
private val md_theme_dark_tertiary = Color(0xFFA5CCDF)
private val md_theme_dark_onTertiary = Color(0xFF073543)
private val md_theme_dark_tertiaryContainer = Color(0xFF244C5B)
private val md_theme_dark_onTertiaryContainer = Color(0xFFC1E8FB)
private val md_theme_dark_error = Color(0xFFD32F2F) // Material Design Red 700 (consistent with light)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onError = Color.White
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
private val md_theme_dark_background = Color(0xFF353937)
private val md_theme_dark_onBackground = Color.White
private val md_theme_dark_surface = Color(0xFF353937)
private val md_theme_dark_onSurface = Color.White
private val md_theme_dark_surfaceVariant = Color(0xFF4A524D)
private val md_theme_dark_onSurfaceVariant = Color(0xFFE0E0E0) // Slightly darker white for secondary text
private val md_theme_dark_outline = Color(0xFF8A938C)
private val md_theme_dark_inverseOnSurface = Color(0xFF373B39)
private val md_theme_dark_inverseSurface = Color(0xFFE1E3DF)
private val md_theme_dark_inversePrimary = Color(0xFF707070) // Modern Gray light
private val md_theme_dark_surfaceTint = Color(0xFF0D0D0D) // Modern Gray dark
private val md_theme_dark_outlineVariant = Color(0xFF565E59)
private val md_theme_dark_scrim = Color(0xFF000000)

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

private val sepia_primary = Color(0xFF6B4226) // warm brown
private val sepia_onPrimary = Color(0xFFFFF8F0)
private val sepia_primaryContainer = Color(0xFFD4A574).copy(alpha = 0.35f)
private val sepia_onPrimaryContainer = Color(0xFF3B1E0A)
private val sepia_secondary = Color(0xFF8B6347)
private val sepia_onSecondary = Color(0xFFFFF8F0)
private val sepia_secondaryContainer = Color(0xFFD4A574).copy(alpha = 0.20f)
private val sepia_onSecondaryContainer = Color(0xFF3B1E0A)
private val sepia_tertiary = Color(0xFF7A6B4F)
private val sepia_onTertiary = Color(0xFFFFF8F0)
private val sepia_tertiaryContainer = Color(0xFFD6C5A0).copy(alpha = 0.30f)
private val sepia_onTertiaryContainer = Color(0xFF3B2D10)
private val sepia_error = Color(0xFFB00020)
private val sepia_onError = Color(0xFFFFF8F0)
private val sepia_errorContainer = Color(0xFFFFDAD6)
private val sepia_onErrorContainer = Color(0xFF410002)
private val sepia_background = Color(0xFFF5ECD7) // classic parchment
private val sepia_onBackground = Color(0xFF2C1A0E) // very dark brown text
private val sepia_surface = Color(0xFFF5ECD7)
private val sepia_onSurface = Color(0xFF2C1A0E)
private val sepia_surfaceVariant = Color(0xFFE8D9BC)
private val sepia_onSurfaceVariant = Color(0xFF4A3728)
private val sepia_outline = Color(0xFF9C7B5E)
private val sepia_inverseOnSurface = Color(0xFFF5ECD7)
private val sepia_inverseSurface = Color(0xFF2C1A0E)
private val sepia_inversePrimary = Color(0xFFD4A574)
private val sepia_surfaceTint = Color(0xFF6B4226)
private val sepia_outlineVariant = Color(0xFFCCB08A)
private val sepia_scrim = Color(0xFF000000)

val SepiaColorScheme = lightColorScheme(
    primary = sepia_primary,
    onPrimary = sepia_onPrimary,
    primaryContainer = sepia_primaryContainer,
    onPrimaryContainer = sepia_onPrimaryContainer,
    secondary = sepia_secondary,
    onSecondary = sepia_onSecondary,
    secondaryContainer = sepia_secondaryContainer,
    onSecondaryContainer = sepia_onSecondaryContainer,
    tertiary = sepia_tertiary,
    onTertiary = sepia_onTertiary,
    tertiaryContainer = sepia_tertiaryContainer,
    onTertiaryContainer = sepia_onTertiaryContainer,
    error = sepia_error,
    errorContainer = sepia_errorContainer,
    onError = sepia_onError,
    onErrorContainer = sepia_onErrorContainer,
    background = sepia_background,
    onBackground = sepia_onBackground,
    surface = sepia_surface,
    onSurface = sepia_onSurface,
    surfaceVariant = sepia_surfaceVariant,
    onSurfaceVariant = sepia_onSurfaceVariant,
    outline = sepia_outline,
    inverseOnSurface = sepia_inverseOnSurface,
    inverseSurface = sepia_inverseSurface,
    inversePrimary = sepia_inversePrimary,
    surfaceTint = sepia_surfaceTint,
    outlineVariant = sepia_outlineVariant,
    scrim = sepia_scrim,
)

// ── Ocean ─────────────────────────────────────────────────────────────────────
// Light theme — soft sky-blue backgrounds, ocean-blue primary
private val ocean_primary = Color(0xFF0284C7) // sky-600
private val ocean_onPrimary = Color(0xFFFFFFFF)
private val ocean_primaryContainer = Color(0xFFBAE6FD) // sky-200
private val ocean_onPrimaryContainer = Color(0xFF0C3A53)
private val ocean_secondary = Color(0xFF0E7490) // cyan-700
private val ocean_onSecondary = Color(0xFFFFFFFF)
private val ocean_secondaryContainer = Color(0xFF9EDBEE) // richer sky-blue — visible as a banner
private val ocean_onSecondaryContainer = Color(0xFF062535) // deep navy for contrast
private val ocean_tertiary = Color(0xFF1D4ED8) // blue-700
private val ocean_onTertiary = Color(0xFFFFFFFF)
private val ocean_tertiaryContainer = Color(0xFFDBEAFE) // blue-100
private val ocean_onTertiaryContainer = Color(0xFF1E3A5F)
private val ocean_error = Color(0xFFD32F2F)
private val ocean_onError = Color(0xFFFFFFFF)
private val ocean_errorContainer = Color(0xFFFFDAD6)
private val ocean_onErrorContainer = Color(0xFF410002)
private val ocean_background = Color(0xFFF0F9FF) // sky-50
private val ocean_onBackground = Color(0xFF082030)
private val ocean_surface = Color(0xFFF0F9FF)
private val ocean_onSurface = Color(0xFF082030)
private val ocean_surfaceVariant = Color(0xFFDCF0FA)
private val ocean_onSurfaceVariant = Color(0xFF2D4F62)
private val ocean_outline = Color(0xFF5AACCC)
private val ocean_inverseOnSurface = Color(0xFFEFF8FF)
private val ocean_inverseSurface = Color(0xFF082030)
private val ocean_inversePrimary = Color(0xFF7DD3FC)
private val ocean_surfaceTint = Color(0xFF0284C7)
private val ocean_outlineVariant = Color(0xFFB3D9EC)
private val ocean_scrim = Color(0xFF000000)

val OceanColorScheme = lightColorScheme(
    primary = ocean_primary,
    onPrimary = ocean_onPrimary,
    primaryContainer = ocean_primaryContainer,
    onPrimaryContainer = ocean_onPrimaryContainer,
    secondary = ocean_secondary,
    onSecondary = ocean_onSecondary,
    secondaryContainer = ocean_secondaryContainer,
    onSecondaryContainer = ocean_onSecondaryContainer,
    tertiary = ocean_tertiary,
    onTertiary = ocean_onTertiary,
    tertiaryContainer = ocean_tertiaryContainer,
    onTertiaryContainer = ocean_onTertiaryContainer,
    error = ocean_error,
    errorContainer = ocean_errorContainer,
    onError = ocean_onError,
    onErrorContainer = ocean_onErrorContainer,
    background = ocean_background,
    onBackground = ocean_onBackground,
    surface = ocean_surface,
    onSurface = ocean_onSurface,
    surfaceVariant = ocean_surfaceVariant,
    onSurfaceVariant = ocean_onSurfaceVariant,
    outline = ocean_outline,
    inverseOnSurface = ocean_inverseOnSurface,
    inverseSurface = ocean_inverseSurface,
    inversePrimary = ocean_inversePrimary,
    surfaceTint = ocean_surfaceTint,
    outlineVariant = ocean_outlineVariant,
    scrim = ocean_scrim,
)

// ── Nord ──────────────────────────────────────────────────────────────────────
// Dark theme — arctic deep navy backgrounds, icy blue primary
private val nord_primary = Color(0xFF88C0D0) // Nord frost light blue
private val nord_onPrimary = Color(0xFF1A2B35)
private val nord_primaryContainer = Color(0xFF3B6070)
private val nord_onPrimaryContainer = Color(0xFFD8EEF5)
private val nord_secondary = Color(0xFF81A1C1) // Nord frost medium blue
private val nord_onSecondary = Color(0xFF1E2D3D)
private val nord_secondaryContainer = Color(0xFF2E4057)
private val nord_onSecondaryContainer = Color(0xFFCCDFEF)
private val nord_tertiary = Color(0xFF5E81AC) // Nord frost dark blue
private val nord_onTertiary = Color(0xFFECEFF4)
private val nord_tertiaryContainer = Color(0xFF243348)
private val nord_onTertiaryContainer = Color(0xFFBFCFE4)
private val nord_error = Color(0xFFBF616A) // Nord aurora red
private val nord_onError = Color(0xFF2E1415)
private val nord_errorContainer = Color(0xFF4A1C20)
private val nord_onErrorContainer = Color(0xFFFFDAD6)
private val nord_background = Color(0xFF2E3440) // Nord polar night darkest
private val nord_onBackground = Color(0xFFECEFF4) // Nord snow storm
private val nord_surface = Color(0xFF2E3440)
private val nord_onSurface = Color(0xFFECEFF4)
private val nord_surfaceVariant = Color(0xFF3B4252) // Nord polar night 2
private val nord_onSurfaceVariant = Color(0xFFD8DEE9) // Nord snow storm mid
private val nord_outline = Color(0xFF616E88)
private val nord_inverseOnSurface = Color(0xFF2E3440)
private val nord_inverseSurface = Color(0xFFECEFF4)
private val nord_inversePrimary = Color(0xFF0284C7)
private val nord_surfaceTint = Color(0xFF88C0D0)
private val nord_outlineVariant = Color(0xFF4C566A) // Nord polar night 4
private val nord_scrim = Color(0xFF000000)

val NordColorScheme = darkColorScheme(
    primary = nord_primary,
    onPrimary = nord_onPrimary,
    primaryContainer = nord_primaryContainer,
    onPrimaryContainer = nord_onPrimaryContainer,
    secondary = nord_secondary,
    onSecondary = nord_onSecondary,
    secondaryContainer = nord_secondaryContainer,
    onSecondaryContainer = nord_onSecondaryContainer,
    tertiary = nord_tertiary,
    onTertiary = nord_onTertiary,
    tertiaryContainer = nord_tertiaryContainer,
    onTertiaryContainer = nord_onTertiaryContainer,
    error = nord_error,
    errorContainer = nord_errorContainer,
    onError = nord_onError,
    onErrorContainer = nord_onErrorContainer,
    background = nord_background,
    onBackground = nord_onBackground,
    surface = nord_surface,
    onSurface = nord_onSurface,
    surfaceVariant = nord_surfaceVariant,
    onSurfaceVariant = nord_onSurfaceVariant,
    outline = nord_outline,
    inverseOnSurface = nord_inverseOnSurface,
    inverseSurface = nord_inverseSurface,
    inversePrimary = nord_inversePrimary,
    surfaceTint = nord_surfaceTint,
    outlineVariant = nord_outlineVariant,
    scrim = nord_scrim,
)
