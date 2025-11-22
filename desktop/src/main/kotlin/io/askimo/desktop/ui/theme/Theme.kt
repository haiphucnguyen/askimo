/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.askimo.desktop.model.AccentColor
import io.askimo.desktop.model.FontSettings

// Light Theme Colors
private val md_theme_light_primary = Color(0xFF006C4C)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFF4DB894)
private val md_theme_light_onPrimaryContainer = Color(0xFF002114)
private val md_theme_light_secondary = Color(0xFF4D6357)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFCFE9D9)
private val md_theme_light_onSecondaryContainer = Color(0xFF092016)
private val md_theme_light_tertiary = Color(0xFF3D6373)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFC1E8FB)
private val md_theme_light_onTertiaryContainer = Color(0xFF001F29)
private val md_theme_light_error = Color(0xFFBA1A1A)
private val md_theme_light_errorContainer = Color(0xFFFFDAD6)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_onErrorContainer = Color(0xFF410002)
private val md_theme_light_background = Color(0xFFFBFDF9)
private val md_theme_light_onBackground = Color(0xFF191C1A)
private val md_theme_light_surface = Color(0xFFFBFDF9)
private val md_theme_light_onSurface = Color(0xFF191C1A)
private val md_theme_light_surfaceVariant = Color(0xFFDBE5DD)
private val md_theme_light_onSurfaceVariant = Color(0xFF404943)
private val md_theme_light_outline = Color(0xFF707973)
private val md_theme_light_inverseOnSurface = Color(0xFFEFF1ED)
private val md_theme_light_inverseSurface = Color(0xFF2E312F)
private val md_theme_light_inversePrimary = Color(0xFF6CDBAC)
private val md_theme_light_surfaceTint = Color(0xFF006C4C)
private val md_theme_light_outlineVariant = Color(0xFFBFC9C2)
private val md_theme_light_scrim = Color(0xFF000000)

// Dark Theme Colors
private val md_theme_dark_primary = Color(0xFF6CDBAC)
private val md_theme_dark_onPrimary = Color(0xFF003826)
private val md_theme_dark_primaryContainer = Color(0xFF005138)
private val md_theme_dark_onPrimaryContainer = Color(0xFF89F8C7)
private val md_theme_dark_secondary = Color(0xFFB3CCBE)
private val md_theme_dark_onSecondary = Color(0xFF1F352A)
private val md_theme_dark_secondaryContainer = Color(0xFF354B40)
private val md_theme_dark_onSecondaryContainer = Color(0xFFCFE9D9)
private val md_theme_dark_tertiary = Color(0xFFA5CCDF)
private val md_theme_dark_onTertiary = Color(0xFF073543)
private val md_theme_dark_tertiaryContainer = Color(0xFF244C5B)
private val md_theme_dark_onTertiaryContainer = Color(0xFFC1E8FB)
private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onError = Color(0xFF690005)
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
private val md_theme_dark_background = Color(0xFF2B2F2D)
private val md_theme_dark_onBackground = Color(0xFFE1E3DF)
private val md_theme_dark_surface = Color(0xFF2B2F2D)
private val md_theme_dark_onSurface = Color(0xFFE1E3DF)
private val md_theme_dark_surfaceVariant = Color(0xFF4A524D)
private val md_theme_dark_onSurfaceVariant = Color(0xFFBFC9C2)
private val md_theme_dark_outline = Color(0xFF8A938C)
private val md_theme_dark_inverseOnSurface = Color(0xFF373B39)
private val md_theme_dark_inverseSurface = Color(0xFFE1E3DF)
private val md_theme_dark_inversePrimary = Color(0xFF006C4C)
private val md_theme_dark_surfaceTint = Color(0xFF6CDBAC)
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

/**
 * Creates a light color scheme with the specified accent color
 */
fun getLightColorScheme(accentColor: AccentColor): ColorScheme {
    val baseScheme = LightColorScheme
    return baseScheme.copy(
        primary = accentColor.lightColor,
        primaryContainer = accentColor.lightColor.copy(alpha = 0.3f),
        onPrimaryContainer = Color.Black,
        secondaryContainer = accentColor.lightColor.copy(alpha = 0.15f),
        onSecondaryContainer = Color.Black,
        inversePrimary = accentColor.darkColor,
        surfaceTint = accentColor.lightColor,
    )
}

/**
 * Creates a dark color scheme with the specified accent color
 */
fun getDarkColorScheme(accentColor: AccentColor): ColorScheme {
    val baseScheme = DarkColorScheme
    return baseScheme.copy(
        primary = accentColor.darkColor,
        primaryContainer = accentColor.darkColor.copy(alpha = 0.3f),
        onPrimaryContainer = Color.White,
        secondaryContainer = accentColor.darkColor.copy(alpha = 0.15f),
        onSecondaryContainer = Color.White,
        inversePrimary = accentColor.lightColor,
        surfaceTint = accentColor.darkColor,
    )
}

/**
 * Maps font family names to predefined FontFamily types
 * This provides basic font customization without requiring font file loading
 */
private fun loadFontFamily(fontName: String): FontFamily = when (fontName.lowercase()) {
    // Monospace fonts
    "monospace", "courier", "courier new", "consolas", "monaco", "menlo",
    "dejavu sans mono", "lucida console",
    -> FontFamily.Monospace

    // Serif fonts
    "serif", "times", "times new roman", "georgia", "palatino",
    "garamond", "baskerville", "book antiqua",
    -> FontFamily.Serif

    // Cursive fonts
    "cursive", "comic sans ms", "apple chancery", "brush script mt" -> FontFamily.Cursive

    // Default to SansSerif for all other fonts (Arial, Helvetica, Roboto, etc.)
    else -> FontFamily.SansSerif
}

/**
 * Creates a custom Typography based on font settings
 */
fun createCustomTypography(fontSettings: FontSettings): Typography {
    val fontFamily = if (fontSettings.fontFamily == FontSettings.SYSTEM_DEFAULT) {
        FontFamily.Default
    } else {
        loadFontFamily(fontSettings.fontFamily)
    }

    val scale = fontSettings.fontSize.scale
    val baseTypography = Typography()

    return Typography(
        displayLarge = baseTypography.displayLarge.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.displayLarge.fontSize * scale,
        ),
        displayMedium = baseTypography.displayMedium.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.displayMedium.fontSize * scale,
        ),
        displaySmall = baseTypography.displaySmall.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.displaySmall.fontSize * scale,
        ),
        headlineLarge = baseTypography.headlineLarge.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.headlineLarge.fontSize * scale,
        ),
        headlineMedium = baseTypography.headlineMedium.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.headlineMedium.fontSize * scale,
        ),
        headlineSmall = baseTypography.headlineSmall.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.headlineSmall.fontSize * scale,
        ),
        titleLarge = baseTypography.titleLarge.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.titleLarge.fontSize * scale,
        ),
        titleMedium = baseTypography.titleMedium.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.titleMedium.fontSize * scale,
        ),
        titleSmall = baseTypography.titleSmall.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.titleSmall.fontSize * scale,
        ),
        bodyLarge = baseTypography.bodyLarge.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.bodyLarge.fontSize * scale,
        ),
        bodyMedium = baseTypography.bodyMedium.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.bodyMedium.fontSize * scale,
        ),
        bodySmall = baseTypography.bodySmall.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.bodySmall.fontSize * scale,
        ),
        labelLarge = baseTypography.labelLarge.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.labelLarge.fontSize * scale,
        ),
        labelMedium = baseTypography.labelMedium.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.labelMedium.fontSize * scale,
        ),
        labelSmall = baseTypography.labelSmall.copy(
            fontFamily = fontFamily,
            fontSize = baseTypography.labelSmall.fontSize * scale,
        ),
    )
}
