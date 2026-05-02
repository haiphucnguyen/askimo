/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.common.preferences

import io.askimo.core.logging.logger
import java.util.prefs.Preferences

/**
 * Per-account preferences scoped to a specific authenticated user.
 *
 * Stored under the Java Preferences path:
 *   `io/askimo/app/accounts/<accountId>/`
 *
 * Keeps sync cursors, default model, and plan inputs fully isolated between
 * users on the same machine. Obtain via [AccountPreferences.forAccount].
 */
class AccountPreferences private constructor(private val prefs: Preferences) {

    companion object {
        private const val ROOT_NODE = "io/askimo/app/accounts"

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
    }

    private val log = logger<AccountPreferences>()

    private fun safePut(key: String, value: String) = runCatching { prefs.put(key, value) }
        .onFailure { log.warn("AccountPreferences.put failed key='$key': ${it.message}") }

    private fun safeGet(key: String, default: String?): String? = runCatching { prefs.get(key, default) }
        .onFailure { log.warn("AccountPreferences.get failed key='$key': ${it.message}") }
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
    fun getLastSyncedAt(): java.time.LocalDateTime? = safeGet("sync.last_synced_at", null)
        ?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

    /** Records the last time conversations were synced. */
    fun saveLastSyncedAt(at: java.time.LocalDateTime = java.time.LocalDateTime.now()) = safePut("sync.last_synced_at", at.toString())

    /** Last time plans were synced, or null if never. */
    fun getLastPlanSyncedAt(): java.time.LocalDateTime? = safeGet("sync.plan_last_synced_at", null)
        ?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

    /** Records the last time plans were synced. */
    fun saveLastPlanSyncedAt(at: java.time.LocalDateTime = java.time.LocalDateTime.now()) = safePut("sync.plan_last_synced_at", at.toString())

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

    /** Clears all preferences for this account (e.g. on logout or account removal). */
    fun clearAll() = runCatching { prefs.clear() }
        .onFailure { log.warn("AccountPreferences.clear() failed: ${it.message}") }
}
