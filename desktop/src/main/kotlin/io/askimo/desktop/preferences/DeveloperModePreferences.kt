/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.preferences

import io.askimo.core.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Manages developer mode preferences for the desktop application.
 *
 * Developer mode has two levels:
 * 1. enabled: Whether developer mode features are available (configured in AppConfig)
 * 2. active: Whether developer mode is currently turned on by the user (user preference)
 *
 * The UI only shows the developer mode toggle if it's enabled in the config.
 */
object DeveloperModePreferences {
    private const val DEVELOPER_MODE_ACTIVE_KEY = "developer_mode_active"

    private val prefs = Preferences.userNodeForPackage(DeveloperModePreferences::class.java)

    private val _isActive = MutableStateFlow(loadActiveState())
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Check if developer mode is enabled in the configuration.
     * This controls whether the developer mode UI is shown.
     */
    fun isEnabled(): Boolean = AppConfig.developer.enabled

    private fun loadActiveState(): Boolean {
        if (!AppConfig.developer.enabled) {
            return false
        }

        return prefs.getBoolean(DEVELOPER_MODE_ACTIVE_KEY, AppConfig.developer.active)
    }

    fun setActive(active: Boolean) {
        if (!isEnabled()) {
            return
        }

        _isActive.value = active
        prefs.putBoolean(DEVELOPER_MODE_ACTIVE_KEY, active)
    }
}
