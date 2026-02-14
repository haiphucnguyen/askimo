/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.tutorial

import java.util.prefs.Preferences

/**
 * Manages tutorial state and preferences.
 */
object TutorialPreferences {
    private const val TUTORIAL_COMPLETED_KEY = "tutorial_completed"
    private const val LANGUAGE_SELECTED_KEY = "language_selected"

    private val prefs = Preferences.userNodeForPackage(TutorialPreferences::class.java)

    /**
     * Check if this is the first time the application is launched.
     * Returns true if language has not been selected yet.
     */
    fun isFirstLaunch(): Boolean = !prefs.getBoolean(LANGUAGE_SELECTED_KEY, false)

    /**
     * Mark language as selected (after first-time language selection).
     */
    fun markLanguageSelected() {
        prefs.putBoolean(LANGUAGE_SELECTED_KEY, true)
    }

    /**
     * Check if the tutorial has been completed.
     */
    fun isTutorialCompleted(): Boolean = prefs.getBoolean(TUTORIAL_COMPLETED_KEY, false)

    /**
     * Mark the tutorial as completed.
     */
    fun markTutorialCompleted() {
        prefs.putBoolean(TUTORIAL_COMPLETED_KEY, true)
    }

    /**
     * Reset tutorial state (for testing purposes).
     */
    fun reset() {
        prefs.remove(TUTORIAL_COMPLETED_KEY)
        prefs.remove(LANGUAGE_SELECTED_KEY)
    }
}
