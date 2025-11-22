/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.desktop.model.AccentColor
import io.askimo.desktop.model.FontSettings
import io.askimo.desktop.model.FontSize
import io.askimo.desktop.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.GraphicsEnvironment
import java.util.prefs.Preferences

object ThemePreferences {
    private const val THEME_MODE_KEY = "theme_mode"
    private const val ACCENT_COLOR_KEY = "accent_color"
    private const val FONT_FAMILY_KEY = "font_family"
    private const val FONT_SIZE_KEY = "font_size"
    private val prefs = Preferences.userNodeForPackage(ThemePreferences::class.java)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    private val _fontSettings = MutableStateFlow(loadFontSettings())
    val fontSettings: StateFlow<FontSettings> = _fontSettings.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val themeName = prefs.get(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadAccentColor(): AccentColor {
        val colorName = prefs.get(ACCENT_COLOR_KEY, AccentColor.OCEAN_BLUE.name)
        return try {
            AccentColor.valueOf(colorName)
        } catch (e: IllegalArgumentException) {
            AccentColor.OCEAN_BLUE
        }
    }

    private fun loadFontSettings(): FontSettings {
        val fontFamily = prefs.get(FONT_FAMILY_KEY, FontSettings.SYSTEM_DEFAULT)
        val fontSizeName = prefs.get(FONT_SIZE_KEY, FontSize.MEDIUM.name)
        val fontSize = try {
            FontSize.valueOf(fontSizeName)
        } catch (e: IllegalArgumentException) {
            FontSize.MEDIUM
        }
        return FontSettings(fontFamily, fontSize)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.put(THEME_MODE_KEY, mode.name)
    }

    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        prefs.put(ACCENT_COLOR_KEY, color.name)
    }

    fun setFontSettings(settings: FontSettings) {
        _fontSettings.value = settings
        prefs.put(FONT_FAMILY_KEY, settings.fontFamily)
        prefs.put(FONT_SIZE_KEY, settings.fontSize.name)
    }

    fun getAvailableSystemFonts(): List<String> {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val fonts = ge.availableFontFamilyNames.toList()
        return listOf(FontSettings.SYSTEM_DEFAULT) + fonts.sorted()
    }
}
