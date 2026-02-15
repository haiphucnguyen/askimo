/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.common.preferences

import io.askimo.core.VersionInfo
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Centralized application preferences management.
 * Combines tutorial, star prompt, and version tracking preferences.
 */
object ApplicationPreferences {
    private val prefs = Preferences.userNodeForPackage(ApplicationPreferences::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // ============================================================
    // VERSION TRACKING
    // ============================================================

    private const val LAST_LAUNCHED_VERSION_KEY = "app.last_launched_version"

    /**
     * Get the last launched version of the application.
     */
    fun getLastLaunchedVersion(): String? = prefs.get(LAST_LAUNCHED_VERSION_KEY, null)

    /**
     * Check if this is the first time the application is launched.
     */
    fun isFirstInstall(): Boolean = getLastLaunchedVersion() == null

    /**
     * Check if the application was upgraded to a new version.
     */
    fun isUpgrade(): Boolean {
        val lastVersion = getLastLaunchedVersion()
        return lastVersion != null && lastVersion != VersionInfo.version
    }

    /**
     * Record the current version as the last launched version.
     * Should be called once during app startup.
     */
    fun recordAppLaunch() {
        prefs.put(LAST_LAUNCHED_VERSION_KEY, VersionInfo.version)
    }

    // ============================================================
    // TUTORIAL & ONBOARDING
    // ============================================================

    private const val TUTORIAL_COMPLETED_KEY = "tutorial_completed"
    private const val LANGUAGE_SELECTED_KEY = "language_selected"

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

    // ============================================================
    // STAR PROMPT (GitHub)
    // ============================================================

    private const val HAS_BEEN_PROMPTED_KEY = "star_prompt_has_been_prompted"
    private const val FIRST_USE_DATE_KEY = "star_prompt_first_use_date"
    private const val MINIMUM_DAYS_BEFORE_PROMPT = 7L

    /**
     * Initialize first use date if not already set.
     * Should be called once when the app starts.
     */
    fun recordFirstUseIfNeeded() {
        if (getFirstUseDate() == null) {
            setFirstUseDate(LocalDateTime.now())
        }
    }

    /**
     * Check if the user has been prompted to star.
     */
    fun hasBeenPrompted(): Boolean = prefs.getBoolean(HAS_BEEN_PROMPTED_KEY, false)

    /**
     * Mark that the user has been prompted.
     */
    fun markAsPrompted() {
        prefs.putBoolean(HAS_BEEN_PROMPTED_KEY, true)
    }

    /**
     * Get the first use date.
     */
    private fun getFirstUseDate(): LocalDateTime? {
        val dateString = prefs.get(FIRST_USE_DATE_KEY, null)
        return dateString?.let { LocalDateTime.parse(it, dateFormatter) }
    }

    /**
     * Set the first use date.
     */
    private fun setFirstUseDate(date: LocalDateTime) {
        prefs.put(FIRST_USE_DATE_KEY, date.format(dateFormatter))
    }

    /**
     * Get days since first use.
     */
    fun getDaysSinceFirstUse(): Long {
        val firstUse = getFirstUseDate() ?: return 0
        return Duration.between(firstUse, LocalDateTime.now()).toDays()
    }

    /**
     * Check if the user should be prompted to star.
     * Shows the prompt after MINIMUM_DAYS_BEFORE_PROMPT days of use.
     */
    fun shouldShowStarPrompt(): Boolean {
        if (hasBeenPrompted()) {
            return false
        }

        val daysSinceFirstUse = getDaysSinceFirstUse()
        return daysSinceFirstUse >= MINIMUM_DAYS_BEFORE_PROMPT
    }

    // ============================================================
    // RESET & TESTING
    // ============================================================

    /**
     * Reset all tutorial-related preferences (for testing).
     */
    fun resetTutorial() {
        prefs.remove(TUTORIAL_COMPLETED_KEY)
        prefs.remove(LANGUAGE_SELECTED_KEY)
    }

    /**
     * Reset all star prompt preferences (for testing).
     */
    fun resetStarPrompt() {
        prefs.remove(HAS_BEEN_PROMPTED_KEY)
        prefs.remove(FIRST_USE_DATE_KEY)
    }

    /**
     * Reset version tracking (for testing).
     */
    fun resetVersion() {
        prefs.remove(LAST_LAUNCHED_VERSION_KEY)
    }

    /**
     * Reset all preferences (for testing).
     */
    fun resetAll() {
        resetTutorial()
        resetStarPrompt()
        resetVersion()
    }
}
