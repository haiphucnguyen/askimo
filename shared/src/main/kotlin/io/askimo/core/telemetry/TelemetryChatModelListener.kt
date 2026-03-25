/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.telemetry

import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import io.askimo.core.logging.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Configuration for syncing token usage back to the Askimo Team server.
 *
 * @param syncUrl   Full URL of the sync endpoint, e.g. `https://team.example.com/api/ai/usage/sync`
 * @param tokenSupplier Returns the current JWT access token (nullable — sync is skipped when null)
 */
data class UsageSyncConfig(
    val syncUrl: String,
    val tokenSupplier: () -> String?,
)

/**
 * LangChain4J listener that records LLM call metrics.
 * Integrates with TelemetryCollector to track:
 * - Request/response timing
 * - Token usage
 * - Errors
 */
class TelemetryChatModelListener(
    private val telemetry: TelemetryCollector,
    private val provider: String,
    private val usageSyncConfig: UsageSyncConfig? = null,
) : ChatModelListener {
    private val log = logger<TelemetryChatModelListener>()

    /**
     * Carries the correlationId from [onRequest] to the `customHeaders` lambda.
     *
     * LangChain4j calls [onRequest] **before** invoking the `customHeaders` supplier,
     * both on the same thread. So we generate the UUID in [onRequest], write it to
     * `context.attributes()` for the [onResponse]/[onError] leg, and also park it here
     * so the `customHeaders` lambda (called immediately after on the same thread) can
     * return it as the `X-Correlation-Id` header value.
     */
    private val currentCorrelationId = ThreadLocal<String?>()

    /**
     * Called by the `customHeaders` lambda on the chat model builder, **after** [onRequest].
     */
    fun currentCorrelationIdForHeader(): String? = currentCorrelationId.get()

    override fun onRequest(context: ChatModelRequestContext) {
        val attrs = context.attributes()

        // Clear any leftover value from the previous request on this thread before
        // generating the new one — onRequest is the authoritative "new request" boundary.
        currentCorrelationId.remove()

        // Generate the correlation ID here — onRequest fires before the customHeaders
        // lambda on the same thread, so the ThreadLocal is readable by the header supplier.
        val correlationId = if (usageSyncConfig != null) {
            UUID.randomUUID().toString().also { id ->
                attrs[ATTR_CORRELATION_ID] = id
                currentCorrelationId.set(id)
            }
        } else {
            null
        }

        attrs[ATTR_START_TIME] = System.currentTimeMillis()

        val request = context.chatRequest()
        log.debug(
            "LLM request to $provider: ${request.messages().size} messages, " +
                "model=${request.modelName() ?: "default"}" +
                if (correlationId != null) ", correlationId=$correlationId" else "",
        )
    }

    override fun onResponse(context: ChatModelResponseContext) {
        val attrs = context.attributes()
        val startTime = attrs[ATTR_START_TIME] as? Long ?: System.currentTimeMillis()
        val correlationId = attrs[ATTR_CORRELATION_ID] as? String
        val duration = System.currentTimeMillis() - startTime

        val request = context.chatRequest()
        val response = context.chatResponse()
        val model = request.modelName() ?: response.modelName() ?: "unknown"
        val tokenUsage = response.tokenUsage()

        telemetry.recordLLMCall(
            provider = provider,
            model = model,
            tokenUsage = tokenUsage,
            durationMs = duration,
        )

        log.debug(
            "LLM response from {}:{} in {}ms, tokens={}, correlationId={}",
            provider,
            model,
            duration,
            tokenUsage?.totalTokenCount() ?: "unknown",
            correlationId,
        )

        // Sync token usage back to the team server (fire-and-forget)
        if (usageSyncConfig != null && correlationId != null && tokenUsage != null) {
            syncUsage(
                config = usageSyncConfig,
                correlationId = correlationId,
                promptTokens = tokenUsage.inputTokenCount() ?: 0,
                completionTokens = tokenUsage.outputTokenCount() ?: 0,
                totalTokens = tokenUsage.totalTokenCount() ?: 0,
                durationMs = duration,
            )
        }
    }

    override fun onError(context: ChatModelErrorContext) {
        val request = context.chatRequest()
        val error = context.error()
        val model = request.modelName() ?: "unknown"

        telemetry.recordLLMError(provider, model, error)

        log.warn("LLM error from $provider:$model: ${error.message}", error)
    }

    private fun syncUsage(
        config: UsageSyncConfig,
        correlationId: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        durationMs: Long,
    ) {
        val token = config.tokenSupplier() ?: run {
            log.debug("Usage sync skipped — no access token available (correlationId={})", correlationId)
            return
        }

        val json = """{"correlationId":"$correlationId","promptTokens":$promptTokens,"completionTokens":$completionTokens,"totalTokens":$totalTokens,"durationMs":$durationMs}"""

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(config.syncUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        // Fire-and-forget on a daemon virtual thread (or fallback platform thread)
        Thread.ofVirtual().start {
            runCatching {
                syncHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding())
                log.debug("Usage synced: correlationId={} tokens={}", correlationId, totalTokens)
            }.onFailure { e ->
                log.warn("Usage sync failed for correlationId={}: {}", correlationId, e.message)
            }
        }
    }

    companion object {
        /** Shared HTTP client for all sync calls — keeps connection pool alive across requests. */
        private val syncHttpClient: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        private const val ATTR_START_TIME = "askimo.startTime"
        private const val ATTR_CORRELATION_ID = "askimo.correlationId"
    }
}
