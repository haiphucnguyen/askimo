/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.common.preferences

import io.askimo.core.logging.logger
import io.askimo.core.util.MachineId
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.jvm.java

/**
 * Centralized application preferences management.
 * Combines tutorial, star prompt, and version tracking preferences.
 */
object ApplicationPreferences {
    private val prefs = Preferences.userNodeForPackage(ApplicationPreferences::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val log = logger<ApplicationPreferences>()

    // ── Safe preference accessors ─────────────────────────────────────────────
    // All reads/writes are wrapped so that any IllegalArgumentException (e.g. a value
    // containing U+0000) or BackingStoreException is caught silently. On read failure
    // the supplied default is returned; on write failure the value is simply not stored.

    private fun safePut(key: String, value: String) {
        runCatching { prefs.put(key, value) }
            .onFailure { log.warn("Preferences.put failed for key='$key': ${it.message}") }
    }

    private fun safeGet(key: String, default: String?): String? = runCatching { prefs.get(key, default) }
        .onFailure { log.warn("Preferences.get failed for key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutBoolean(key: String, value: Boolean) {
        runCatching { prefs.putBoolean(key, value) }
            .onFailure { log.warn("Preferences.putBoolean failed for key='$key': ${it.message}") }
    }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean = runCatching { prefs.getBoolean(key, default) }
        .onFailure { log.warn("Preferences.getBoolean failed for key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutInt(key: String, value: Int) {
        runCatching { prefs.putInt(key, value) }
            .onFailure { log.warn("Preferences.putInt failed for key='$key': ${it.message}") }
    }

    private fun safeGetInt(key: String, default: Int): Int = runCatching { prefs.getInt(key, default) }
        .onFailure { log.warn("Preferences.getInt failed for key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutLong(key: String, value: Long) {
        runCatching { prefs.putLong(key, value) }
            .onFailure { log.warn("Preferences.putLong failed for key='$key': ${it.message}") }
    }

    private fun safeGetLong(key: String, default: Long): Long = runCatching { prefs.getLong(key, default) }
        .onFailure { log.warn("Preferences.getLong failed for key='$key': ${it.message}") }
        .getOrDefault(default)

    // ============================================================
    // TUTORIAL & ONBOARDING
    // ============================================================

    private const val TUTORIAL_COMPLETED_KEY = "tutorial_completed"
    private const val LANGUAGE_SELECTED_KEY = "language_selected"

    /**
     * Check if this is the first time the application is launched.
     * Returns true if language has not been selected yet.
     */
    fun isFirstLaunch(): Boolean = !safeGetBoolean(LANGUAGE_SELECTED_KEY, false)

    /**
     * Mark language as selected (after first-time language selection).
     */
    fun markLanguageSelected() {
        safePutBoolean(LANGUAGE_SELECTED_KEY, true)
    }

    /**
     * Check if the tutorial has been completed.
     */
    fun isTutorialCompleted(): Boolean = safeGetBoolean(TUTORIAL_COMPLETED_KEY, false)

    /**
     * Mark the tutorial as completed.
     */
    fun markTutorialCompleted() {
        safePutBoolean(TUTORIAL_COMPLETED_KEY, true)
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
    fun hasBeenPrompted(): Boolean = safeGetBoolean(HAS_BEEN_PROMPTED_KEY, false)

    /**
     * Mark that the user has been prompted.
     */
    fun markAsPrompted() {
        safePutBoolean(HAS_BEEN_PROMPTED_KEY, true)
    }

    /**
     * Get the first use date.
     */
    private fun getFirstUseDate(): LocalDateTime? {
        val dateString = safeGet(FIRST_USE_DATE_KEY, null)
        return dateString?.let { runCatching { LocalDateTime.parse(it, dateFormatter) }.getOrNull() }
    }

    /**
     * Set the first use date.
     */
    private fun setFirstUseDate(date: LocalDateTime) {
        safePut(FIRST_USE_DATE_KEY, date.format(dateFormatter))
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
        if (hasBeenPrompted()) return false
        return getDaysSinceFirstUse() >= MINIMUM_DAYS_BEFORE_PROMPT
    }

    // ============================================================
    // ANALYTICS CONSENT
    // ============================================================
    private const val ANALYTICS_CONSENT_ASKED_KEY = "analytics_consent_asked"

    /** True once the consent dialog has been shown (regardless of the user's answer). */
    fun isAnalyticsConsentAsked(): Boolean = safeGetBoolean(ANALYTICS_CONSENT_ASKED_KEY, false)

    /** Mark that the consent dialog has been shown so it is never shown again. */
    fun markAnalyticsConsentAsked() {
        safePutBoolean(ANALYTICS_CONSENT_ASKED_KEY, true)
    }

    // ============================================================
    // CONVERSATION SYNC
    // ============================================================
    private const val DEVICE_ID_KEY = "sync.device_id"
    private const val LAST_SYNC_SEQ_KEY = "sync.last_seq"

    /**
     * Returns a stable device identifier used for echo suppression during sync pull.
     */
    fun getOrCreateDeviceId(): String {
        val cached = safeGet(DEVICE_ID_KEY, null)
        if (cached != null) return cached

        val id = MachineId.resolve() ?: UUID.randomUUID().toString()
        safePut(DEVICE_ID_KEY, id)
        return id
    }

    /**
     * The highest `seq` value received from the server during the last pull.
     * Send this as the `since` parameter on the next pull to fetch only the delta.
     * Defaults to 0 (first pull fetches all history).
     */
    fun getLastSyncSeq(): Long = safeGetLong(LAST_SYNC_SEQ_KEY, 0L)

    /**
     * Persists [seq] as the new cursor bookmark after a successful pull.
     * Must only be called when the pull succeeded — a failed pull must not
     * advance the cursor so the delta is retried on the next attempt.
     */
    fun saveLastSyncSeq(seq: Long) {
        safePutLong(LAST_SYNC_SEQ_KEY, seq)
    }

    // ============================================================
    // UI PREFERENCES
    // ============================================================

    private const val PROJECT_SIDE_PANEL_WIDTH_KEY = "ui.project_side_panel_width"
    private const val DEFAULT_PROJECT_SIDE_PANEL_WIDTH = 400
    private const val PROJECT_SIDE_PANEL_EXPANDED_KEY = "ui.project_side_panel_expanded"
    private const val DEFAULT_PROJECT_SIDE_PANEL_EXPANDED = true
    private const val PLAN_HISTORY_SIDE_PANEL_WIDTH_KEY = "ui.plan_history_side_panel_width"
    private const val DEFAULT_PLAN_HISTORY_SIDE_PANEL_WIDTH = 320
    private const val PLAN_HISTORY_SIDE_PANEL_EXPANDED_KEY = "ui.plan_history_side_panel_expanded"
    private const val DEFAULT_MODEL_KEY = "ui.default_model"

    /**
     * Get the saved default AI model ID, or null if never set (first launch).
     */
    fun getDefaultModel(): String? = safeGet(DEFAULT_MODEL_KEY, null)

    /**
     * Persist the chosen default AI model ID.
     */
    fun setDefaultModel(modelId: String) {
        safePut(DEFAULT_MODEL_KEY, modelId)
    }

    /**
     * Get the project side panel width in pixels.
     */
    fun getProjectSidePanelWidth(): Int = safeGetInt(PROJECT_SIDE_PANEL_WIDTH_KEY, DEFAULT_PROJECT_SIDE_PANEL_WIDTH)

    /**
     * Set the project side panel width in pixels.
     */
    fun setProjectSidePanelWidth(width: Int) {
        safePutInt(PROJECT_SIDE_PANEL_WIDTH_KEY, width)
    }

    /**
     * Get the project side panel expanded state.
     */
    fun getProjectSidePanelExpanded(): Boolean = safeGetBoolean(PROJECT_SIDE_PANEL_EXPANDED_KEY, DEFAULT_PROJECT_SIDE_PANEL_EXPANDED)

    /**
     * Set the project side panel expanded state.
     */
    fun setProjectSidePanelExpanded(expanded: Boolean) {
        safePutBoolean(PROJECT_SIDE_PANEL_EXPANDED_KEY, expanded)
    }

    /**
     * Get the plan history side panel width in pixels.
     */
    fun getPlanHistorySidePanelWidth(): Int = safeGetInt(PLAN_HISTORY_SIDE_PANEL_WIDTH_KEY, DEFAULT_PLAN_HISTORY_SIDE_PANEL_WIDTH)

    /**
     * Set the plan history side panel width in pixels.
     */
    fun setPlanHistorySidePanelWidth(width: Int) {
        safePutInt(PLAN_HISTORY_SIDE_PANEL_WIDTH_KEY, width)
    }

    /**
     * Get the plan history side panel expanded state.
     */
    fun getPlanHistorySidePanelExpanded(): Boolean = safeGetBoolean(PLAN_HISTORY_SIDE_PANEL_EXPANDED_KEY, true)

    /**
     * Set the plan history side panel expanded state.
     */
    fun setPlanHistorySidePanelExpanded(expanded: Boolean) {
        safePutBoolean(PLAN_HISTORY_SIDE_PANEL_EXPANDED_KEY, expanded)
    }

    // ============================================================
    // PLAN INPUT PERSISTENCE
    // ============================================================

    /**
     * Returns the last-used input values for a plan, keyed by input key.
     * Stored as a single string using U+001F (Unit Separator) between entries
     * and '=' between key and value — both characters safe for [Preferences.put].
     * Returns an empty map if no values have been saved for this plan.
     */
    fun getPlanInputs(planId: String): Map<String, String> {
        val raw = safeGet("plan.inputs.$planId", null) ?: return emptyMap()
        return raw.split("\u001F")
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
            }
            .toMap()
    }

    /**
     * Persists the current input values for a plan so they can be restored on next visit.
     * Values are serialised as "key1=value1\u001Fkey2=value2" (U+001F Unit Separator).
     * U+0000 is stripped from keys and values because [Preferences.put] rejects it.
     */
    fun setPlanInputs(planId: String, inputs: Map<String, String>) {
        val raw = inputs.entries.joinToString("\u001F") { (k, v) ->
            // Strip any characters that Preferences.put rejects (null bytes, surrogates)
            val safeKey = k.filter { it != '\u0000' && !it.isLowSurrogate() && !it.isHighSurrogate() }
            val safeVal = v.filter { it != '\u0000' && !it.isLowSurrogate() && !it.isHighSurrogate() }
            "$safeKey=$safeVal"
        }
        val truncated = if (raw.length > Preferences.MAX_VALUE_LENGTH) raw.substring(0, Preferences.MAX_VALUE_LENGTH) else raw
        safePut("plan.inputs.$planId", truncated)
    }

    /**
     * Clears ALL application preferences, effectively resetting the app to a
     * fresh-install state. Useful for testing onboarding flows and first-launch
     * behaviour without uninstalling. The running process is NOT restarted.
     */
    fun clearAll() {
        runCatching { prefs.clear() }
            .onFailure { log.warn("Preferences.clear() failed: ${it.message}") }
        log.info("All application preferences cleared")
    }
}
