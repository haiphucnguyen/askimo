/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Standardized spacing system following Material Design 3 guidelines.
 * Use these constants throughout the application for consistent spacing.
 *
 * Guidelines:
 * - extraSmall (4.dp): Tight spacing within small components (e.g., label + description pairs)
 * - small (8.dp): Standard spacing between related items within a component
 * - medium (12.dp): Spacing between component groups within a card
 * - large (16.dp): Spacing between cards/panels and padding inside cards
 * - extraLarge (24.dp): Major section spacing
 */
object Spacing {
    /** 4.dp - Tight spacing for closely related elements (label + description) */
    val extraSmall = 4.dp

    /** 8.dp - Standard spacing between related items within a component */
    val small = 8.dp

    /** 12.dp - Spacing between component groups within a card */
    val medium = 12.dp

    /** 16.dp - Card/panel padding and spacing between cards */
    val large = 16.dp

    /** 24.dp - Major section spacing */
    val extraLarge = 24.dp
}

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
private val md_theme_dark_background = Color(0xFF353937)
private val md_theme_dark_onBackground = Color(0xFFE1E3DF)
private val md_theme_dark_surface = Color(0xFF353937)
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
    primary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_primary,
    onPrimary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onPrimary,
    primaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_primaryContainer,
    onPrimaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onPrimaryContainer,
    secondary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_secondary,
    onSecondary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onSecondary,
    secondaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_secondaryContainer,
    onSecondaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onSecondaryContainer,
    tertiary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_tertiary,
    onTertiary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onTertiary,
    tertiaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_tertiaryContainer,
    onTertiaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onTertiaryContainer,
    error = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_error,
    errorContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_errorContainer,
    onError = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onError,
    onErrorContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onErrorContainer,
    background = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_background,
    onBackground = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onBackground,
    surface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_surface,
    onSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onSurface,
    surfaceVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_surfaceVariant,
    onSurfaceVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_onSurfaceVariant,
    outline = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_outline,
    inverseOnSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_inverseOnSurface,
    inverseSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_inverseSurface,
    inversePrimary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_inversePrimary,
    surfaceTint = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_surfaceTint,
    outlineVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_outlineVariant,
    scrim = _root_ide_package_.io.askimo.ui.common.theme.md_theme_light_scrim,
)

val DarkColorScheme = darkColorScheme(
    primary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_primary,
    onPrimary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onPrimary,
    primaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_primaryContainer,
    onPrimaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onPrimaryContainer,
    secondary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_secondary,
    onSecondary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onSecondary,
    secondaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_secondaryContainer,
    onSecondaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onSecondaryContainer,
    tertiary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_tertiary,
    onTertiary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onTertiary,
    tertiaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_tertiaryContainer,
    onTertiaryContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onTertiaryContainer,
    error = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_error,
    errorContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_errorContainer,
    onError = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onError,
    onErrorContainer = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onErrorContainer,
    background = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_background,
    onBackground = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onBackground,
    surface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_surface,
    onSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onSurface,
    surfaceVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_surfaceVariant,
    onSurfaceVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_onSurfaceVariant,
    outline = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_outline,
    inverseOnSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_inverseOnSurface,
    inverseSurface = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_inverseSurface,
    inversePrimary = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_inversePrimary,
    surfaceTint = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_surfaceTint,
    outlineVariant = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_outlineVariant,
    scrim = _root_ide_package_.io.askimo.ui.common.theme.md_theme_dark_scrim,
)

/**
 * Creates a light color scheme with the specified accent color
 */
fun getLightColorScheme(accentColor: io.askimo.ui.common.theme.AccentColor): ColorScheme {
    val baseScheme = _root_ide_package_.io.askimo.ui.common.theme.LightColorScheme
    return baseScheme.copy(
        // Keep primary as accent color for backgrounds/highlights
        primary = accentColor.lightColor,
        onPrimary = Color.White, // White text on primary background
        primaryContainer = accentColor.lightColor.copy(alpha = 0.3f),
        onPrimaryContainer = Color.Black,
        secondaryContainer = accentColor.lightColor.copy(alpha = 0.15f),
        onSecondaryContainer = Color.Black,
        inversePrimary = accentColor.darkColor,
        surfaceTint = accentColor.lightColor,

        // Error colors for danger buttons - darker red with white text
        error = Color(0xFFD32F2F), // Material Design Red 700
        onError = Color.White, // White text on error background

        // Text colors should always be black in light mode
        onSurface = Color.Black,
        onBackground = Color.Black,
        onSurfaceVariant = Color(0xFF424242), // Slightly lighter for secondary text
    )
}

/**
 * Creates a dark color scheme with the specified accent color
 */
fun getDarkColorScheme(accentColor: io.askimo.ui.common.theme.AccentColor): ColorScheme {
    val baseScheme = _root_ide_package_.io.askimo.ui.common.theme.DarkColorScheme
    return baseScheme.copy(
        // Keep primary as accent color for backgrounds/highlights
        primary = accentColor.darkColor,
        onPrimary = Color.White, // White text on primary background
        primaryContainer = accentColor.darkColor.copy(alpha = 0.3f),
        onPrimaryContainer = Color.White,
        secondaryContainer = accentColor.darkColor.copy(alpha = 0.15f),
        onSecondaryContainer = Color.White,
        inversePrimary = accentColor.lightColor,
        surfaceTint = accentColor.darkColor,

        // Error colors for danger buttons - darker red with white text
        error = Color(0xFFD32F2F), // Material Design Red 700 (same as light mode for consistency)
        onError = Color.White, // White text on error background

        // Text colors should always be white in dark mode
        onSurface = Color.White,
        onBackground = Color.White,
        onSurfaceVariant = Color(0xFFE0E0E0), // Slightly darker white for secondary text
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
fun createCustomTypography(fontSettings: io.askimo.ui.common.theme.FontSettings): Typography {
    val fontFamily = if (fontSettings.fontFamily == _root_ide_package_.io.askimo.ui.common.theme.FontSettings.SYSTEM_DEFAULT) {
        FontFamily.Default
    } else {
        _root_ide_package_.io.askimo.ui.common.theme.loadFontFamily(fontSettings.fontFamily)
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
