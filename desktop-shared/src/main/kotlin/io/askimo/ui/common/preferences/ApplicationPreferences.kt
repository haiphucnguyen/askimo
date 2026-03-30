/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.common.preferences

import io.askimo.core.util.MachineId
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.prefs.Preferences

/**
 * Centralized application preferences management.
 * Combines tutorial, star prompt, and version tracking preferences.
 */
object ApplicationPreferences {
    private val prefs = Preferences.userNodeForPackage(ApplicationPreferences::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

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

    private const val DEVICE_ID_KEY = "sync.device_id"
    private const val LAST_SYNC_SEQ_KEY = "sync.last_seq"

    /**
     * Returns a stable device identifier used for echo suppression during sync pull.
     */
    fun getOrCreateDeviceId(): String {
        val cached = prefs.get(DEVICE_ID_KEY, null)
        if (cached != null) return cached

        val id = MachineId.resolve() ?: UUID.randomUUID().toString()
        prefs.put(DEVICE_ID_KEY, id)
        return id
    }

    /**
     * The highest `seq` value received from the server during the last pull.
     * Send this as the `since` parameter on the next pull to fetch only the delta.
     * Defaults to 0 (first pull fetches all history).
     */
    fun getLastSyncSeq(): Long = prefs.getLong(LAST_SYNC_SEQ_KEY, 0L)

    /**
     * Persists [seq] as the new cursor bookmark after a successful pull.
     * Must only be called when the pull succeeded — a failed pull must not
     * advance the cursor so the delta is retried on the next attempt.
     */
    fun saveLastSyncSeq(seq: Long) {
        prefs.putLong(LAST_SYNC_SEQ_KEY, seq)
    }

    // ============================================================
    // UI PREFERENCES
    // ============================================================

    private const val PROJECT_SIDE_PANEL_WIDTH_KEY = "ui.project_side_panel_width"
    private const val DEFAULT_PROJECT_SIDE_PANEL_WIDTH = 400
    private const val PROJECT_SIDE_PANEL_EXPANDED_KEY = "ui.project_side_panel_expanded"
    private const val DEFAULT_PROJECT_SIDE_PANEL_EXPANDED = true
    private const val DEFAULT_MODEL_KEY = "ui.default_model"

    /**
     * Get the saved default AI model ID, or null if never set (first launch).
     */
    fun getDefaultModel(): String? = prefs.get(DEFAULT_MODEL_KEY, null)

    /**
     * Persist the chosen default AI model ID.
     */
    fun setDefaultModel(modelId: String) {
        prefs.put(DEFAULT_MODEL_KEY, modelId)
    }

    /**
     * Get the project side panel width in pixels.
     */
    fun getProjectSidePanelWidth(): Int = prefs.getInt(PROJECT_SIDE_PANEL_WIDTH_KEY, DEFAULT_PROJECT_SIDE_PANEL_WIDTH)

    /**
     * Set the project side panel width in pixels.
     */
    fun setProjectSidePanelWidth(width: Int) {
        prefs.putInt(PROJECT_SIDE_PANEL_WIDTH_KEY, width)
    }

    /**
     * Get the project side panel expanded state.
     */
    fun getProjectSidePanelExpanded(): Boolean = prefs.getBoolean(PROJECT_SIDE_PANEL_EXPANDED_KEY, DEFAULT_PROJECT_SIDE_PANEL_EXPANDED)

    /**
     * Set the project side panel expanded state.
     */
    fun setProjectSidePanelExpanded(expanded: Boolean) {
        prefs.putBoolean(PROJECT_SIDE_PANEL_EXPANDED_KEY, expanded)
    }
}
