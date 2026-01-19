/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.preferences

import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.LogLevel
import io.askimo.core.logging.LoggingService
import io.askimo.desktop.theme.AccentColor
import io.askimo.desktop.theme.FontSettings
import io.askimo.desktop.theme.FontSize
import io.askimo.desktop.theme.ThemeMode
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
    private const val USER_AVATAR_PATH_KEY = "user_avatar_path"
    private const val AI_AVATAR_PATH_KEY = "ai_avatar_path"
    private const val MAIN_SIDEBAR_WIDTH_FRACTION_KEY = "main_sidebar_width_fraction"
    private const val SETTINGS_SIDEBAR_WIDTH_FRACTION_KEY = "settings_sidebar_width_fraction"
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
        // Check if user has saved a locale preference
        val savedLocaleTag = prefs.get(LOCALE_KEY, null)

        if (savedLocaleTag != null) {
            // User has explicitly set a locale, use it
            return try {
                Locale.forLanguageTag(savedLocaleTag)
            } catch (e: Exception) {
                Locale.ENGLISH
            }
        }

        // No saved preference - check if system locale is supported
        val systemLocale = Locale.getDefault()
        val availableLocales = LocalizationManager.availableLocales

        // Check if system locale (or its language) is available
        val supportedLocale = availableLocales.keys.find { locale ->
            // Exact match (language + country)
            locale.language == systemLocale.language && locale.country == systemLocale.country
        } ?: availableLocales.keys.find { locale ->
            // Language-only match
            locale.language == systemLocale.language
        }

        // Return supported locale or default to English
        return supportedLocale ?: Locale.ENGLISH
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

    // Avatar management
    fun getUserAvatarPath(): String? = prefs.get(USER_AVATAR_PATH_KEY, null)
    fun getAIAvatarPath(): String? = prefs.get(AI_AVATAR_PATH_KEY, null)

    fun setUserAvatarPath(path: String?) {
        if (path != null) {
            prefs.put(USER_AVATAR_PATH_KEY, path)
        } else {
            prefs.remove(USER_AVATAR_PATH_KEY)
        }
    }

    fun setAIAvatarPath(path: String?) {
        if (path != null) {
            prefs.put(AI_AVATAR_PATH_KEY, path)
        } else {
            prefs.remove(AI_AVATAR_PATH_KEY)
        }
    }

    // Sidebar width management
    fun getMainSidebarWidthFraction(): Float = prefs.getFloat(MAIN_SIDEBAR_WIDTH_FRACTION_KEY, 0.20f)
    fun getSettingsSidebarWidthFraction(): Float = prefs.getFloat(SETTINGS_SIDEBAR_WIDTH_FRACTION_KEY, 0.18f)

    fun setMainSidebarWidthFraction(fraction: Float) {
        prefs.putFloat(MAIN_SIDEBAR_WIDTH_FRACTION_KEY, fraction)
    }

    fun setSettingsSidebarWidthFraction(fraction: Float) {
        prefs.putFloat(SETTINGS_SIDEBAR_WIDTH_FRACTION_KEY, fraction)
    }
}
