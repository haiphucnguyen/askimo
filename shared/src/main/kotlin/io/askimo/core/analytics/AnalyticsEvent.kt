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
 * A single anonymous feature-usage event reported to Askimo analytics.
 *
 * ## Privacy contract — what belongs in [properties]
 * - ✅ Provider name/tier, execution mode, command/recipe name, OS, arch, app version,
 *      JVM version, distribution, locale language, feature flags, bucketed counts/durations
 * - ❌ Prompt text, response text, file paths, session IDs, user identity, API keys,
 *      model IDs (too identifiable for custom models), exact latencies
 *
 * All string values are validated by callers. The ingest endpoint performs a
 * secondary sanitisation pass and drops any property whose key is not on the
 * server-side allow-list.
 */
@Serializable
data class AnalyticsEvent(
    /** Snake_case event name (e.g. `"provider_switched"`, `"recipe_executed"`). */
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
 * Canonical event names for [AnalyticsEvent.event].
 * Using constants keeps call sites consistent and makes refactoring safe.
 */
object AnalyticsEvents {
    /**
     * Fired once per session start.
     * Properties: `mode=desktop|cli`, `has_mcp=true|false`, `has_rag=true|false`.
     */
    const val APP_STARTED = "app_started"

    /**
     * Fired on clean app exit.
     * Properties: `session_duration_bucket=<1m|1-10m|10-60m|>1h`,
     * `message_count_bucket=1-5|6-20|>20`.
     */
    const val APP_SESSION_ENDED = "app_session_ended"

    /**
     * A provider was actively used for a chat message.
     * Properties: `provider=OPENAI`, `model_tier=cloud|local`.
     */
    const val PROVIDER_USED = "provider_used"

    /** User switched provider mid-session. Properties: `from=OLLAMA`, `to=OPENAI`. */
    const val PROVIDER_SWITCHED = "provider_switched"

    /**
     * A RAG project was indexed successfully.
     * Properties: `file_count=42`, `index_duration_bucket=<5s|5-30s|>30s`.
     */
    const val RAG_INDEXED = "rag_indexed"

    /** RAG retrieval fired on a message (classification returned true). */
    const val RAG_TRIGGERED = "rag_triggered"

    /**
     * A recipe was executed.
     * Properties: `recipe=analyze_log` (default recipe names only), `recipe_source=default|custom`,
     * `has_stdin=true|false`.
     */
    const val RECIPE_EXECUTED = "recipe_executed"

    /** An MCP tool was invoked. Property: `scope=global|project`. */
    const val MCP_TOOL_USED = "mcp_tool_used"

    /** A vision (image-input) message was sent. */
    const val VISION_USED = "vision_used"

    /** An image was generated via the image model. */
    const val IMAGE_GENERATED = "image_generated"

    /** A named CLI command was executed. Property: `command=set-provider`. */
    const val CLI_COMMAND = "cli_command"

    /**
     * A categorised error occurred.
     * Properties: `error_type=provider_timeout|rag_index_failure|recipe_parse_error|...`,
     * `provider=OPENAI` (only when relevant).
     */
    const val ERROR_OCCURRED = "error_occurred"

    /** User explicitly opted in via the consent dialog or flag. */
    const val ANALYTICS_OPT_IN = "analytics_opt_in"

    /** User explicitly opted out via settings. */
    const val ANALYTICS_OPT_OUT = "analytics_opt_out"
}
