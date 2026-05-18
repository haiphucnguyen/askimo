/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.ui.common.preferences

import io.askimo.core.logging.logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Per-account preferences scoped to a specific authenticated user.
 *
 * Stored under the Java Preferences path:
 *   `io/askimo/app/accounts/<accountId>/`
 *
 * Keeps sync cursors, default model, plan inputs, and user-behavior tracking
 * fully isolated between users on the same machine. These are NOT reset when
 * the user clears app preferences — they survive across settings resets.
 *
 * Obtain via [AccountPreferences.forAccount].
 */
class AccountPreferences private constructor(private val prefs: Preferences) {

    companion object {
        private const val ROOT_NODE = "io/askimo/app/accounts"

        private const val MINIMUM_DAYS_BEFORE_PROMPT = 7L

        /**
         * Returns [AccountPreferences] scoped to [accountId] (typically the user's email,
         * lowercased and sanitized for use as a Preferences path segment).
         * Safe to call multiple times — Preferences nodes are singletons per path.
         */
        fun forAccount(accountId: String): AccountPreferences {
            val safeId = accountId.trim().lowercase()
                .replace(Regex("[^a-z0-9._@-]"), "_")
            return AccountPreferences(Preferences.userRoot().node("$ROOT_NODE/$safeId"))
        }

        /**
         * Returns a device-level (no account) [AccountPreferences] node for fields
         * that must persist even before the user logs in (e.g. first-use date,
         * launch count, analytics consent).
         */
        fun device(): AccountPreferences = AccountPreferences(Preferences.userRoot().node("$ROOT_NODE/__device__"))
    }

    private val log = logger<AccountPreferences>()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun safePut(key: String, value: String) = runCatching { prefs.put(key, value) }
        .onFailure { log.warn("AccountPreferences.put failed key='$key': ${it.message}") }

    private fun safeGet(key: String, default: String?): String? = runCatching { prefs.get(key, default) }
        .onFailure { log.warn("AccountPreferences.get failed key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutBoolean(key: String, value: Boolean) = runCatching { prefs.putBoolean(key, value) }
        .onFailure { log.warn("AccountPreferences.putBoolean failed key='$key': ${it.message}") }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean = runCatching { prefs.getBoolean(key, default) }
        .onFailure { log.warn("AccountPreferences.getBoolean failed key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutInt(key: String, value: Int) = runCatching { prefs.putInt(key, value) }
        .onFailure { log.warn("AccountPreferences.putInt failed key='$key': ${it.message}") }

    private fun safeGetInt(key: String, default: Int): Int = runCatching { prefs.getInt(key, default) }
        .onFailure { log.warn("AccountPreferences.getInt failed key='$key': ${it.message}") }
        .getOrDefault(default)

    private fun safePutLong(key: String, value: Long) = runCatching { prefs.putLong(key, value) }
        .onFailure { log.warn("AccountPreferences.putLong failed key='$key': ${it.message}") }

    private fun safeGetLong(key: String, default: Long): Long = runCatching { prefs.getLong(key, default) }
        .onFailure { log.warn("AccountPreferences.getLong failed key='$key': ${it.message}") }
        .getOrDefault(default)

    // ── Conversation sync ─────────────────────────────────────────────────────

    /** Highest conversation seq received from the server; 0 = full resync needed. */
    fun getLastSyncSeq(): Long = safeGetLong("sync.last_seq", 0L)
    fun saveLastSyncSeq(seq: Long) = safePutLong("sync.last_seq", seq)

    /** Highest plan seq received from the server; 0 = full resync needed. */
    fun getLastPlanSyncSeq(): Long = safeGetLong("sync.plan_last_seq", 0L)
    fun saveLastPlanSyncSeq(seq: Long) = safePutLong("sync.plan_last_seq", seq)

    /** Last time conversations were synced, or null if never. */
    fun getLastSyncedAt(): LocalDateTime? = safeGet("sync.last_synced_at", null)
        ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    /** Records the last time conversations were synced. */
    fun saveLastSyncedAt(at: LocalDateTime = LocalDateTime.now()) = safePut("sync.last_synced_at", at.toString())

    /** Last time plans were synced, or null if never. */
    fun getLastPlanSyncedAt(): LocalDateTime? = safeGet("sync.plan_last_synced_at", null)
        ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    /** Records the last time plans were synced. */
    fun saveLastPlanSyncedAt(at: LocalDateTime = LocalDateTime.now()) = safePut("sync.plan_last_synced_at", at.toString())

    // ── AI model ──────────────────────────────────────────────────────────────

    /** The user's chosen default AI model ID, or null if never set. */
    fun getDefaultModel(): String? = safeGet("ui.default_model", null)
    fun setDefaultModel(modelId: String) = safePut("ui.default_model", modelId)

    // ── Plan inputs ───────────────────────────────────────────────────────────

    /** Returns last-used input values for a plan (keyed by input key). */
    fun getPlanInputs(planId: String): Map<String, String> {
        val raw = safeGet("plan.inputs.$planId", null) ?: return emptyMap()
        return raw.split("\u001F").mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
        }.toMap()
    }

    /** Persists current input values for a plan for next-visit restoration. */
    fun setPlanInputs(planId: String, inputs: Map<String, String>) {
        val raw = inputs.entries.joinToString("\u001F") { (k, v) ->
            val sk = k.filter { it != '\u0000' && !it.isLowSurrogate() && !it.isHighSurrogate() }
            val sv = v.filter { it != '\u0000' && !it.isLowSurrogate() && !it.isHighSurrogate() }
            "$sk=$sv"
        }
        safePut(
            "plan.inputs.$planId",
            if (raw.length > Preferences.MAX_VALUE_LENGTH) {
                raw.substring(0, Preferences.MAX_VALUE_LENGTH)
            } else {
                raw
            },
        )
    }

    /** Records the first-use date if not already set. Call once at app startup. */
    fun recordFirstUseIfNeeded() {
        if (getFirstUseDate() == null) setFirstUseDate(LocalDateTime.now())
    }

    private fun getFirstUseDate(): LocalDateTime? = safeGet("star.first_use_date", null)
        ?.let { runCatching { LocalDateTime.parse(it, dateFormatter) }.getOrNull() }

    private fun setFirstUseDate(date: LocalDateTime) = safePut("star.first_use_date", date.format(dateFormatter))

    /** Days elapsed since the very first app launch on this device/account. */
    fun getDaysSinceFirstUse(): Long {
        val firstUse = getFirstUseDate() ?: return 0
        return Duration.between(firstUse, LocalDateTime.now()).toDays()
    }

    /** True if the user has already been shown the "star us on GitHub" prompt. */
    fun hasBeenPrompted(): Boolean = safeGetBoolean("star.has_been_prompted", false)

    /** Marks that the star prompt has been shown — never show again. */
    fun markAsPrompted() = safePutBoolean("star.has_been_prompted", true)

    /**
     * True when the star prompt should be displayed.
     * Requires [MINIMUM_DAYS_BEFORE_PROMPT] days of use and not yet prompted.
     */
    fun shouldShowStarPrompt(): Boolean {
        if (hasBeenPrompted()) return false
        return getDaysSinceFirstUse() >= MINIMUM_DAYS_BEFORE_PROMPT
    }

    // ── Launch count — retention milestones ───────────────────────────────────

    /**
     * Increments the persistent launch counter and returns the new value.
     * Call once per app startup.
     */
    fun incrementLaunchCount(): Int {
        val next = safeGetInt("launch_count", 0) + 1
        safePutInt("launch_count", next)
        return next
    }

    /** Total number of times the app has been launched on this device/account. */
    fun getLaunchCount(): Int = safeGetInt("launch_count", 0)

    // ── Analytics consent ─────────────────────────────────────────────────────

    /** True once the analytics consent dialog has been shown (regardless of answer). */
    fun isAnalyticsConsentAsked(): Boolean = safeGetBoolean("analytics.consent_asked", false)

    /** Marks that the consent dialog has been shown so it is never shown again. */
    fun markAnalyticsConsentAsked() = safePutBoolean("analytics.consent_asked", true)

    // ── Update notifications ──────────────────────────────────────────────────

    /**
     * Returns the latest version string the user has dismissed,
     * or null if they have never dismissed an update notification.
     */
    fun getDismissedUpdateVersion(): String? = safeGet("update.dismissed_version", null)

    /**
     * Persists [version] so the update popup is not auto-shown again for this release.
     */
    fun setDismissedUpdateVersion(version: String) = safePut("update.dismissed_version", version)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Clears all preferences for this account (e.g. on logout or account removal). */
    fun clearAll() = runCatching { prefs.clear() }
        .onFailure { log.warn("AccountPreferences.clear() failed: ${it.message}") }
}
