/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.preferences

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Manages preferences for GitHub star prompt behavior.
 * Tracks usage metrics and whether the user has been prompted.
 */
object StarPromptPreferences {
    private const val CHAT_COUNT_KEY = "star_prompt_chat_count"
    private const val HAS_BEEN_PROMPTED_KEY = "star_prompt_has_been_prompted"
    private const val FIRST_USE_DATE_KEY = "star_prompt_first_use_date"
    private const val PROMPT_DISMISSED_KEY = "star_prompt_dismissed"

    private val prefs = Preferences.userNodeForPackage(StarPromptPreferences::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Increment the chat count and return the new count.
     */
    fun incrementChatCount(): Int {
        val currentCount = getChatCount()
        val newCount = currentCount + 1
        prefs.putInt(CHAT_COUNT_KEY, newCount)

        // Record first use date if not set
        if (getFirstUseDate() == null) {
            setFirstUseDate(LocalDateTime.now())
        }

        return newCount
    }

    /**
     * Get the current chat count.
     */
    fun getChatCount(): Int = prefs.getInt(CHAT_COUNT_KEY, 0)

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
     * Check if the user dismissed the prompt.
     */
    fun wasPromptDismissed(): Boolean = prefs.getBoolean(PROMPT_DISMISSED_KEY, false)

    /**
     * Mark that the user dismissed the prompt.
     */
    fun markPromptDismissed() {
        prefs.putBoolean(PROMPT_DISMISSED_KEY, true)
        markAsPrompted()
    }

    /**
     * Get the first use date.
     */
    fun getFirstUseDate(): LocalDateTime? {
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
        return java.time.Duration.between(firstUse, LocalDateTime.now()).toDays()
    }

    /**
     * Check if the user should be prompted to star.
     * Conditions:
     * - Has not been prompted before
     * - Has at least 5 chats OR used for 3+ days
     */
    fun shouldShowStarPrompt(): Boolean {
        if (hasBeenPrompted()) {
            return false
        }

        val chatCount = getChatCount()
        val daysSinceFirstUse = getDaysSinceFirstUse()

        return chatCount >= 5 || daysSinceFirstUse >= 3
    }

    /**
     * Reset all star prompt preferences (for testing).
     */
    fun reset() {
        prefs.remove(CHAT_COUNT_KEY)
        prefs.remove(HAS_BEEN_PROMPTED_KEY)
        prefs.remove(FIRST_USE_DATE_KEY)
        prefs.remove(PROMPT_DISMISSED_KEY)
    }
}
