/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily

val LocalFontScale = compositionLocalOf { 1f }

data class FontSettings(
    val fontFamily: String = "System Default",
    val fontSize: FontSize = FontSize.MEDIUM,
) {
    companion object {
        const val SYSTEM_DEFAULT = "System Default"
    }
}

enum class FontSize(val displayName: String, val scale: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.35f),
}

/**
 * Maps font family names to predefined [FontFamily] types.
 */
fun loadFontFamily(fontName: String): FontFamily = when (fontName.lowercase()) {
    "monospace", "courier", "courier new", "consolas", "monaco", "menlo",
    "dejavu sans mono", "lucida console",
    -> FontFamily.Monospace

    "serif", "times", "times new roman", "georgia", "palatino",
    "garamond", "baskerville", "book antiqua",
    -> FontFamily.Serif

    "cursive", "comic sans ms", "apple chancery", "brush script mt" -> FontFamily.Cursive

    else -> FontFamily.SansSerif
}

/**
 * Creates a [Typography] scaled and set to the font in [fontSettings].
 */
fun createCustomTypography(fontSettings: FontSettings): Typography {
    val fontFamily = if (fontSettings.fontFamily == FontSettings.SYSTEM_DEFAULT) {
        FontFamily.Default
    } else {
        loadFontFamily(fontSettings.fontFamily)
    }
    val scale = fontSettings.fontSize.scale
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontSize = base.displayLarge.fontSize * scale),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontSize = base.displayMedium.fontSize * scale),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontSize = base.displaySmall.fontSize * scale),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontSize = base.headlineLarge.fontSize * scale),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontSize = base.headlineMedium.fontSize * scale),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontSize = base.headlineSmall.fontSize * scale),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontSize = base.titleLarge.fontSize * scale),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontSize = base.titleMedium.fontSize * scale),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontSize = base.titleSmall.fontSize * scale),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = base.bodyLarge.fontSize * scale),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontSize = base.bodyMedium.fontSize * scale),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontSize = base.bodySmall.fontSize * scale),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontSize = base.labelLarge.fontSize * scale),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontSize = base.labelMedium.fontSize * scale),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontSize = base.labelSmall.fontSize * scale),
    )
}
