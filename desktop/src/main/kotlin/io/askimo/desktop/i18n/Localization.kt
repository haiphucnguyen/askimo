/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import io.askimo.core.i18n.LocalizationManager
import java.util.Locale

/**
 * CompositionLocal for providing the current locale to composables.
 */
val defaultLocale = compositionLocalOf { Locale.getDefault() }

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
        defaultLocale provides locale,
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
    val currentLocale = defaultLocale.current

    // Trigger recomposition when locale changes by using it in a derived state
    return remember(key, currentLocale, *args) {
        LocalizationManager.getString(key, *args)
    }
}
