/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.preferences

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Manages preferences for GitHub star prompt behavior.
 * Tracks first use date and whether the user has been prompted.
 */
object StarPromptPreferences {
    private const val HAS_BEEN_PROMPTED_KEY = "star_prompt_has_been_prompted"
    private const val FIRST_USE_DATE_KEY = "star_prompt_first_use_date"
    private const val MINIMUM_DAYS_BEFORE_PROMPT = 7L

    private val prefs = Preferences.userNodeForPackage(StarPromptPreferences::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

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

    /**
     * Reset all star prompt preferences (for testing).
     */
    fun reset() {
        prefs.remove(HAS_BEEN_PROMPTED_KEY)
        prefs.remove(FIRST_USE_DATE_KEY)
    }
}
