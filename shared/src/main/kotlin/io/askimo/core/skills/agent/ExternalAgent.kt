/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.skills.agent

import io.askimo.core.analytics.Analytics
import io.askimo.core.analytics.AnalyticsEvent

/**
 * Represents an external CLI agent capable of running a skill non-interactively.
 *
 * Implementations compose the OS command, pipe the combined prompt (system + user)
 * via stdin, and stream stdout back to the caller line by line.
 *
 * The skill document is agent-agnostic — the agent is chosen at run time.
 */
interface ExternalAgent {

    /** Stable identifier, e.g. `"claude"` or `"gemini"`. */
    val id: String

    /** Human-readable display name shown in the UI. */
    val name: String

    /** URL to the installation guide for this agent, shown when the agent is not installed. */
    val installUrl: String

    /**
     * Slash-commands supported by this agent, shown in the UI command picker
     * when the user types `/` in the context input field.
     * Defaults to an empty list — agents that support no special commands omit this.
     */
    val commands: List<AgentCommand> get() = emptyList()

    /**
     * Whether Askimo can collect an API key from the user and inject it into the agent process.
     * `true`  → show inline API key input when [isConfigured] returns false (e.g. Gemini CLI).
     * `false` → show [configurationHint] as a read-only banner (e.g. Claude Code uses OAuth login).
     */
    val requiresApiKey: Boolean get() = false

    /**
     * Human-readable hint shown below the agent picker when [isBinaryAvailable] is `true`
     * but [isConfigured] returns `false`.
     * Only relevant when [requiresApiKey] is `false`; for key-based agents the UI renders
     * an inline key input instead.
     */
    val configurationHint: String get() = ""

    /**
     * Returns `true` if the agent binary is installed and reachable on `PATH`.
     * Cheap check only — no API call.
     */
    fun isBinaryAvailable(): Boolean

    /**
     * Returns `true` if the agent is fully configured and ready to run
     * (binary installed + any required credentials present).
     * Defaults to [isBinaryAvailable] — override when credentials are also required.
     */
    fun isConfigured(): Boolean = isBinaryAvailable()

    /**
     * Returns `true` if the agent is both installed and configured.
     * Convenience combining [isBinaryAvailable] and [isConfigured].
     */
    fun isAvailable(): Boolean = isBinaryAvailable() && isConfigured()

    /**
     * Saves an API key for this agent via [io.askimo.core.security.SecureKeyManager]
     * and optionally syncs it to the matching chat provider settings in AppContext.
     * Only called when [requiresApiKey] is `true`.
     * Default implementation is a no-op — override in agents that need key injection.
     */
    fun saveApiKey(key: String) {}

    /**
     * Runs the skill non-interactively.
     *
     * The agent receives the combined prompt via **stdin**:
     * ```
     * <systemPrompt>
     *
     * ---
     *
     * <userInput>
     * ```
     * Stdout is read line by line and delivered through [onToken].
     * The full accumulated output is returned when the process exits.
     *
     * @param systemPrompt  The skill's raw markdown body (its system prompt).
     * @param userInput     Optional context supplied by the user at run time.
     * @param workDir       Working directory for the agent process. File writes land here.
     *                      Defaults to `null` — agents use their own CWD.
     * @param onToken       Called for each content token as it arrives (pure response text).
     * @param onStatus      Called with a short human-readable status string when the agent
     *                      performs a tool call (e.g. "Using tool: readFile"). Defaults to no-op.
     * @return The complete stdout output, or a [Result.failure] on error.
     */
    fun run(
        systemPrompt: String,
        userInput: String,
        workDir: java.io.File? = null,
        onToken: (String) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): Result<String>

    /**
     * Wraps [run] with automatic timing and analytics tracking.
     * All new agents automatically get tracking without any extra code.
     */
    fun runTracked(
        systemPrompt: String,
        userInput: String,
        workDir: java.io.File? = null,
        onToken: (String) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): Result<String> {
        val startMs = System.currentTimeMillis()
        val result = run(systemPrompt, userInput, workDir, onToken, onStatus)
        val durationMs = System.currentTimeMillis() - startMs
        Analytics.track(
            AnalyticsEvent.SKILL_AGENT_RUN,
            mapOf(
                "agent" to id,
                "has_user_input" to (userInput.isNotBlank()).toString(),
                "success" to result.isSuccess.toString(),
                "duration_bucket" to when {
                    durationMs < 5_000 -> "<5s"
                    durationMs < 30_000 -> "5-30s"
                    durationMs < 120_000 -> "30-120s"
                    else -> ">120s"
                },
            ),
        )
        return result
    }
}
