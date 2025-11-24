/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties

/**
 * Manages localization for the desktop application.
 * Uses Kotlin's resource loading with UTF-8 support instead of Java's ResourceBundle.
 */
object LocalizationManager {
    private var currentLocale by mutableStateOf(Locale.getDefault())
    private val loadedBundles = mutableMapOf<String, Properties>()

    /**
     * Available locales with their display names.
     * Dynamically discovers locales based on existing properties files.
     * Supports both language_country (e.g., ja_JP, zh_CN, zh_TW) and language-only formats.
     */
    val availableLocales: Map<Locale, String> by lazy {
        val localesMap = mutableMapOf<Locale, String>()

        // Track which locale combinations we've already processed
        val seenLocales = mutableSetOf<String>()

        // Scan for all available locale properties files
        val allAvailableLocales = Locale.getAvailableLocales()

        // Always include base English first (no country variant)
        localesMap[Locale.ENGLISH] = "English"
        seenLocales.add("en")

        allAvailableLocales.forEach { locale ->
            val language = locale.language
            val country = locale.country

            // Skip English - already added above
            if (language.isNotEmpty() && language != "en") {
                // Try language_country format first (e.g., ja_JP, zh_CN, zh_TW)
                val localeKey = if (country.isNotEmpty()) {
                    "${language}_$country"
                } else {
                    language
                }

                // Skip if we've already processed this exact locale
                if (!seenLocales.contains(localeKey)) {
                    val resourcePath = "i18n/messages_$localeKey.properties"

                    try {
                        this::class.java.classLoader.getResourceAsStream(resourcePath)?.use {
                            // File exists! Use this locale
                            val displayName = buildDisplayName(locale)
                            localesMap[locale] = displayName
                            seenLocales.add(localeKey)
                        }
                    } catch (_: Exception) {
                        // File doesn't exist, try next locale
                    }
                }
            }
        }

        localesMap.forEach { (locale, displayName) ->
            println("  ${locale.toLanguageTag()} -> $displayName")
        }

        // Sort: English first, then alphabetically by English language name
        val sortedEntries = localesMap.entries.sortedWith(
            compareBy(
                { it.key.language != "en" }, // English first (false < true)
                { it.key.getDisplayLanguage(Locale.ENGLISH) }, // Then by English language name (Japanese, Vietnamese)
                { it.key.getDisplayCountry(Locale.ENGLISH) }, // Then by English country name if same language
            ),
        )

        sortedEntries.forEach { (locale, displayName) ->
            val englishName = locale.getDisplayLanguage(Locale.ENGLISH)
            println("  ${locale.toLanguageTag()} -> $displayName (englishName: $englishName, isEnglish: ${locale.language == "en"})")
        }

        // Convert back to LinkedHashMap to preserve order
        linkedMapOf<Locale, String>().apply {
            sortedEntries.forEach { (locale, displayName) ->
                put(locale, displayName)
            }
        }
    }

    /**
     * Build a display name for a locale showing native name, country, and English translation.
     * Examples:
     * - 日本語 - 日本 (Japanese - Japan)
     * - 中文 - 中国 (Chinese - China)
     * - 中文 - 台灣 (Chinese - Taiwan)
     */
    private fun buildDisplayName(locale: Locale): String {
        val nativeName = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
        val englishName = locale.getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase() }

        // Include country/region if present
        val nativeCountry = if (locale.country.isNotEmpty()) {
            locale.getDisplayCountry(locale)
        } else {
            null
        }

        val englishCountry = if (locale.country.isNotEmpty()) {
            locale.getDisplayCountry(Locale.ENGLISH)
        } else {
            null
        }

        return when {
            nativeCountry != null && englishCountry != null && nativeName != englishName -> {
                // Full format with country: 日本語 - 日本 (Japanese - Japan)
                "$nativeName - $nativeCountry ($englishName - $englishCountry)"
            }
            nativeCountry != null && nativeName == englishName -> {
                // Same language name, show country: English - United States
                "$nativeName - $englishCountry"
            }
            nativeName != englishName -> {
                // No country, different names: 日本語 (Japanese)
                "$nativeName ($englishName)"
            }
            else -> {
                // No country, same name: English
                nativeName
            }
        }
    }

    /**
     * Set the application locale and clear cached bundles.
     */
    fun setLocale(locale: Locale) {
        currentLocale = locale
        Locale.setDefault(locale)
        loadedBundles.clear()
    }

    /**
     * Get a localized string by key.
     * @param key The resource key
     * @param args Optional format arguments
     * @return The localized string, or the key if not found
     */
    fun getString(key: String, vararg args: Any): String {
        val properties = getProperties()
        val message = properties.getProperty(key) ?: return key

        return if (args.isEmpty()) {
            message
        } else {
            try {
                // Convert all arguments to strings to avoid MessageFormat's number formatting
                // (which adds thousand separators like "2,025" instead of "2025")
                val stringArgs = args.map { it.toString() }.toTypedArray()
                MessageFormat(message, currentLocale).format(stringArgs)
            } catch (e: Exception) {
                message
            }
        }
    }

    /**
     * Load properties file for the current locale with UTF-8 encoding.
     * Supports both language_country (ja_JP) and language-only (ja) formats.
     */
    private fun getProperties(): Properties {
        val language = currentLocale.language
        val country = currentLocale.country

        // Use language_country format if country is present, otherwise just language
        val localeKey = if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }

        return loadedBundles.getOrPut(localeKey) {
            val properties = Properties()

            // Try to load locale-specific file first (e.g., messages_ja_JP.properties)
            val localizedPath = if (localeKey.isNotEmpty() && localeKey != "en") {
                "i18n/messages_$localeKey.properties"
            } else {
                null
            }

            // Load localized file if it exists
            if (localizedPath != null) {
                try {
                    this::class.java.classLoader.getResourceAsStream(localizedPath)?.use { stream ->
                        stream.reader(Charsets.UTF_8).use { reader ->
                            properties.load(reader)
                        }
                    }
                } catch (e: Exception) {
                    println("ERROR: Failed to load $localizedPath: ${e.message}")
                }
            }

            // If no translations found, load default (English)
            if (properties.isEmpty) {
                try {
                    this::class.java.classLoader.getResourceAsStream("i18n/messages.properties")?.use { stream ->
                        stream.reader(Charsets.UTF_8).use { reader ->
                            properties.load(reader)
                        }
                    }
                } catch (e: Exception) {
                    // Return empty properties if even default fails
                }
            }

            properties
        }
    }
}

/**
 * CompositionLocal for providing the current locale to composables.
 */
val LocalLocalization = compositionLocalOf { Locale.getDefault() }

/**
 * Provides localization context to child composables.
 * When the locale changes, all child composables using stringResource will recompose.
 */
@Composable
fun provideLocalization(
    locale: Locale = Locale.getDefault(),
    content: @Composable () -> Unit,
) {
    DisposableEffect(locale) {
        LocalizationManager.setLocale(locale)
        onDispose { }
    }

    CompositionLocalProvider(
        LocalLocalization provides locale,
    ) {
        content()
    }
}

/**
 * Get a localized string resource in a Composable.
 * This will trigger recomposition when the locale changes.
 *
 * @param key The resource key
 * @param args Optional format arguments
 * @return The localized string
 */
@Composable
fun stringResource(key: String, vararg args: Any): String {
    // Access the locale to trigger recomposition when it changes
    val currentLocale = LocalLocalization.current

    // Trigger recomposition when locale changes by using it in a derived state
    return remember(key, currentLocale, *args) {
        LocalizationManager.getString(key, *args)
    }
}
