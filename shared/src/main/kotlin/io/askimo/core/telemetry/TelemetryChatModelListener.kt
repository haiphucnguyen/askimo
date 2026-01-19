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
import java.util.concurrent.ConcurrentHashMap

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
) : ChatModelListener {
    private val log = logger<TelemetryChatModelListener>()
    private val requestTimes = ConcurrentHashMap<Any, Long>()

    override fun onRequest(context: ChatModelRequestContext) {
        val request = context.chatRequest()
        requestTimes[request] = System.currentTimeMillis()

        log.debug(
            "LLM request to $provider: ${request.messages().size} messages, " +
                "model=${request.modelName() ?: "default"}",
        )
    }

    override fun onResponse(context: ChatModelResponseContext) {
        val request = context.chatRequest()
        val response = context.chatResponse()

        val startTime = requestTimes.remove(request) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime

        val model = request.modelName() ?: response.modelName() ?: "unknown"
        val tokenUsage = response.tokenUsage()

        telemetry.recordLLMCall(
            provider = provider,
            model = model,
            tokenUsage = tokenUsage,
            durationMs = duration,
        )

        log.debug(
            "LLM response from {}:{} in {}ms, tokens={}",
            provider,
            model,
            duration,
            tokenUsage?.totalTokenCount() ?: "unknown",
        )
    }

    override fun onError(context: ChatModelErrorContext) {
        val request = context.chatRequest()
        val error = context.error()

        requestTimes.remove(request)

        val model = request.modelName() ?: "unknown"
        telemetry.recordLLMError(provider, model, error)

        log.warn("LLM error from $provider:$model: ${error.message}", error)
    }
}
