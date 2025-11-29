/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.logging.LogLevel
import io.askimo.core.logging.LoggingService
import io.askimo.desktop.i18n.LocalizationManager
import io.askimo.desktop.model.AccentColor
import io.askimo.desktop.model.FontSettings
import io.askimo.desktop.model.FontSize
import io.askimo.desktop.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.prefs.Preferences

object ThemePreferences {
    private const val THEME_MODE_KEY = "theme_mode"
    private const val ACCENT_COLOR_KEY = "accent_color"
    private const val FONT_FAMILY_KEY = "font_family"
    private const val FONT_SIZE_KEY = "font_size"
    private const val LOCALE_KEY = "locale"
    private const val LOG_LEVEL_KEY = "log_level"
    private const val WINDOW_WIDTH_KEY = "window_width"
    private const val WINDOW_HEIGHT_KEY = "window_height"
    private const val WINDOW_X_KEY = "window_x"
    private const val WINDOW_Y_KEY = "window_y"
    private const val WINDOW_IS_MAXIMIZED_KEY = "window_is_maximized"
    private val prefs = Preferences.userNodeForPackage(ThemePreferences::class.java)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    private val _fontSettings = MutableStateFlow(loadFontSettings())
    val fontSettings: StateFlow<FontSettings> = _fontSettings.asStateFlow()

    private val _locale = MutableStateFlow(loadLocale())
    val locale: StateFlow<Locale> = _locale.asStateFlow()

    private val _logLevel = MutableStateFlow(loadLogLevel())
    val logLevel: StateFlow<LogLevel> = _logLevel.asStateFlow()

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

    private fun loadLocale(): Locale {
        val localeTag = prefs.get(LOCALE_KEY, Locale.getDefault().toLanguageTag())
        return try {
            Locale.forLanguageTag(localeTag)
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }

    private fun loadLogLevel(): LogLevel {
        val levelName = prefs.get(LOG_LEVEL_KEY, LogLevel.INFO.name)
        val level = try {
            LogLevel.valueOf(levelName)
        } catch (e: IllegalArgumentException) {
            LogLevel.INFO
        }

        // Apply saved log level on startup
        LoggingService.updateLogLevel(level)

        return level
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

    fun setLocale(locale: Locale) {
        _locale.value = locale
        prefs.put(LOCALE_KEY, locale.toLanguageTag())
        LocalizationManager.setLocale(locale)
    }

    fun setLogLevel(level: LogLevel) {
        _logLevel.value = level
        prefs.put(LOG_LEVEL_KEY, level.name)

        // Apply log level change immediately using shared LoggingService
        LoggingService.updateLogLevel(level)
    }

    fun getAvailableSystemFonts(): List<String> {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val fonts = ge.availableFontFamilyNames.toList()
        return listOf(FontSettings.SYSTEM_DEFAULT) + fonts.sorted()
    }

    // Window state management
    fun saveWindowState(width: Int, height: Int, x: Int, y: Int, isMaximized: Boolean) {
        prefs.putInt(WINDOW_WIDTH_KEY, width)
        prefs.putInt(WINDOW_HEIGHT_KEY, height)
        prefs.putInt(WINDOW_X_KEY, x)
        prefs.putInt(WINDOW_Y_KEY, y)
        prefs.putBoolean(WINDOW_IS_MAXIMIZED_KEY, isMaximized)
    }

    fun getWindowWidth(): Int = prefs.getInt(WINDOW_WIDTH_KEY, -1)
    fun getWindowHeight(): Int = prefs.getInt(WINDOW_HEIGHT_KEY, -1)
    fun getWindowX(): Int = prefs.getInt(WINDOW_X_KEY, -1)
    fun getWindowY(): Int = prefs.getInt(WINDOW_Y_KEY, -1)
    fun isWindowMaximized(): Boolean = prefs.getBoolean(WINDOW_IS_MAXIMIZED_KEY, true) // Default to maximized
}
