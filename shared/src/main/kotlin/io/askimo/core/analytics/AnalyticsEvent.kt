/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.analytics

import io.askimo.core.VersionInfo
import io.askimo.core.util.MachineId
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Wire payload sent to the Askimo ingest endpoint.
 *
 * Built by [Analytics.track] from an [AnalyticsEvent] enum entry + properties map.
 * The ingest endpoint performs a secondary sanitisation pass — see `analytics-ingest.js`.
 *
 * ## Privacy contract — what belongs in [properties]
 * - ✅ Provider name/tier, execution mode, command/recipe name, OS, arch, app version,
 *      JVM version, distribution, locale language, feature flags, bucketed counts/durations
 * - ❌ Prompt text, response text, file paths, session IDs, user identity, API keys,
 *      model IDs (too identifiable for custom models), exact latencies
 */
@Serializable
data class AnalyticsEventPayload(
    /** Snake_case event name derived from [AnalyticsEvent.eventName]. */
    val event: String,
    /** Safe, non-PII key/value pairs describing the event context. */
    val properties: Map<String, String> = emptyMap(),
    val appVersion: String = VersionInfo.version,
    val os: String = System.getProperty("os.name", "unknown"),
    /** Major OS version only (e.g. `"15"` not `"15.3.2"`) to reduce fingerprinting. */
    val osVersion: String = System.getProperty("os.version", "unknown").substringBefore(".").ifBlank { "unknown" },
    val arch: String = System.getProperty("os.arch", "unknown"),
    /** Major JVM version only (e.g. `"21"`). `"native"` when running as GraalVM native image. */
    val jvmVersion: String = AnalyticsDeviceInfo.jvmVersion,
    /** `"native"` for GraalVM binary, `"jvm"` for fat-JAR / Gradle run. */
    val distribution: String = AnalyticsDeviceInfo.distribution,
    /** IETF language tag language subtag only (e.g. `"en"`, `"fr"`). Never the full locale. */
    val localeLanguage: String = Locale.getDefault().language.take(5).ifBlank { "unknown" },
    /**
     * Epoch-millis at the time the event was recorded locally.
     * The ingest endpoint truncates this to the day — individual request timing is never retained.
     */
    val timestampMs: Long = System.currentTimeMillis(),
    /**
     * Stable anonymous device identifier derived from hardware via [MachineId].
     * Used by the ingest endpoint to deduplicate feature-first-use events across
     * sessions — no local tracking file is needed.
     */
    val installId: String = AnalyticsDeviceInfo.installId,
)

/** Cached device-level facts resolved once at class-load time. */
internal object AnalyticsDeviceInfo {
    /**
     * Stable anonymous device identifier derived from hardware via [MachineId].
     * The raw hardware value is SHA-256 hashed inside [MachineId] — nothing
     * identifiable is ever stored or transmitted. Falls back to `"unknown"` only
     * when every platform strategy fails (extremely unlikely).
     */
    val installId: String by lazy {
        MachineId.resolve() ?: "unknown"
    }

    /** `"native"` when running under GraalVM substrate, otherwise the JVM major version. */
    val distribution: String by lazy {
        if (System.getProperty("org.graalvm.nativeimage.kind") != null ||
            runCatching { Class.forName("org.graalvm.nativeimage.ImageInfo") }.isSuccess
        ) {
            "native"
        } else {
            "jvm"
        }
    }

    val jvmVersion: String by lazy {
        if (distribution == "native") {
            "native"
        } else {
            // e.g. "21.0.3" → "21"
            System.getProperty("java.version", "unknown").substringBefore(".").ifBlank { "unknown" }
        }
    }
}

/**
 * All analytics event types emitted by Askimo clients.
 *
 * Each entry carries a stable [eventName] that is sent over the wire and stored
 * in Analytics Engine. The name must match the allow-list in the Cloudflare
 * ingest worker (`analytics-ingest.js`).
 *
 * Using an enum instead of string constants gives:
 * - Compile-time exhaustiveness checks in `when` expressions
 * - No risk of typos at call sites
 * - A single source of truth for the ingest worker allow-list
 */
enum class AnalyticsEvent(
    /** Snake_case wire name stored in Analytics Engine. */
    val eventName: String,
    /** Human-readable description of when this event fires and which properties it carries. */
    val description: String,
) {
    // ── Session lifecycle ────────────────────────────────────────────────────

    /** Fired once per session start. Properties: `mode=desktop|cli`, `has_mcp`, `has_rag`. */
    APP_STARTED(
        "app_started",
        "Session started. Properties: mode=desktop|cli, has_mcp=true|false, has_rag=true|false.",
    ),

    /**
     * Fired on clean app exit.
     * Properties: `session_duration_bucket=<1m|1-10m|10-60m|>1h`, `message_count_bucket=1-5|6-20|>20`.
     */
    APP_SESSION_ENDED(
        "app_session_ended",
        "Session ended. Properties: session_duration_bucket, message_count_bucket.",
    ),

    // ── Provider & model ─────────────────────────────────────────────────────

    /** A provider was actively used for a chat message. Properties: `provider`, `model_tier=cloud|local`. */
    PROVIDER_USED(
        "provider_used",
        "LLM response received. Properties: provider=OPENAI|ANTHROPIC|…, model_tier=cloud|local.",
    ),

    // ── RAG ──────────────────────────────────────────────────────────────────

    /**
     * A RAG project was indexed successfully.
     * Properties: `file_count`, `index_duration_bucket=<5s|5-30s|>30s`.
     */
    RAG_INDEXED(
        "rag_indexed",
        "Project indexed. Properties: file_count, index_duration_bucket.",
    ),

    /** RAG retrieval fired on a message (intent classification returned true). */
    RAG_TRIGGERED(
        "rag_triggered",
        "RAG retrieval fired for a message.",
    ),

    /**
     * User opened the Projects / RAG view.
     * Pair with [RAG_TRIGGERED] to compute RAG activation rate (opened vs. actually used).
     */
    RAG_PANEL_OPENED(
        "rag_panel_opened",
        "User navigated to the Projects/RAG view.",
    ),

    // ── Recipe ───────────────────────────────────────────────────────────────

    /**
     * A recipe was executed.
     * Properties: `recipe` (default names only), `recipe_source=default|custom`, `has_stdin=true|false`.
     */
    RECIPE_EXECUTED(
        "recipe_executed",
        "Recipe executed. Properties: recipe, recipe_source=default|custom, has_stdin.",
    ),

    // ── MCP ──────────────────────────────────────────────────────────────────

    /** An MCP tool was invoked. Properties: `scope=global|project`, `tool_count`. */
    MCP_TOOL_USED(
        "mcp_tool_used",
        "MCP tool selected for a message. Properties: scope=global|project, tool_count.",
    ),

    /**
     * User opened the MCP Servers settings section.
     * Pair with [MCP_TOOL_USED] to compute MCP activation rate (opened vs. actually used).
     */
    MCP_SETTINGS_OPENED(
        "mcp_settings_opened",
        "User opened the MCP Servers settings section.",
    ),

    // ── Plans ────────────────────────────────────────────────────────────────

    /**
     * A Plan execution started.
     * Properties: `step_count`, `has_parallel`, `has_conditional`, `has_interactive`, `tool_count`.
     */
    PLAN_STARTED(
        "plan_started",
        "Plan execution started. Properties: step_count, has_parallel, has_conditional, has_interactive, tool_count.",
    ),

    /**
     * User opened the Plans view.
     * Pair with [PLAN_STARTED] to compute Plan activation rate (opened vs. actually used).
     */
    PLAN_VIEW_OPENED(
        "plan_view_opened",
        "User navigated to the Plans view.",
    ),

    /**
     * A Plan execution completed successfully.
     * Properties: `step_count`, `duration_bucket=<5s|5-30s|30-120s|>120s`, `output_length_bucket=<500|500-2000|>2000`.
     */
    PLAN_COMPLETED(
        "plan_completed",
        "Plan finished successfully. Properties: step_count, duration_bucket, output_length_bucket.",
    ),

    /**
     * A Plan execution failed.
     * Properties: `step_count`, `error_type=missing_input|step_failed|unknown`.
     */
    PLAN_FAILED(
        "plan_failed",
        "Plan execution failed. Properties: step_count, error_type.",
    ),

    // ── Other features ───────────────────────────────────────────────────────

    /**
     * A directive was applied when sending a chat message.
     * Pair with [PROVIDER_USED] to compute the directive adoption rate.
     */
    DIRECTIVE_USED(
        "directive_used",
        "Directive applied to a message.",
    ),

    /** An image was generated via the image model. */
    IMAGE_GENERATED(
        "image_generated",
        "Image generated via image model.",
    ),

    // ── Errors ───────────────────────────────────────────────────────────────

    /**
     * A categorised error occurred.
     * Properties: `error_type=provider_timeout|rate_limit|auth_error|context_length|…`,
     * `provider` (when relevant).
     */
    ERROR_OCCURRED(
        "error_occurred",
        "Categorised error. Properties: error_type, provider (optional).",
    ),

    // ── Consent ──────────────────────────────────────────────────────────────

    /** User explicitly opted in via the consent dialog or flag. */
    ANALYTICS_OPT_IN(
        "analytics_opt_in",
        "User opted in to analytics.",
    ),

    /** User explicitly opted out via settings. */
    ANALYTICS_OPT_OUT(
        "analytics_opt_out",
        "User opted out of analytics.",
    ),
    ;

    override fun toString(): String = eventName

    companion object {
        /** All wire names — use this to keep the ingest worker allow-list in sync. */
        val allEventNames: Set<String> = entries.map { it.eventName }.toSet()

        /** Look up by wire name, or null if unknown. */
        fun fromEventName(name: String): AnalyticsEvent? = entries.firstOrNull { it.eventName == name }
    }
}
