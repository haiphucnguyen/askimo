/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.ChatModel

/**
 * Flexible memory configuration that works across all AI providers.
 *
 * This configuration allows provider-specific optimizations (like OpenAI's token estimator)
 * while maintaining a unified interface for memory creation.
 *
 * Example usage:
 * ```kotlin
 * // OpenAI with accurate token counting and AI summarization
 * val config = MemoryConfig(
 *     maxTokens = 8000,
 *     tokenEstimator = OpenAiTokenCountEstimator(model)::estimateTokenCountInMessage,
 *     summarizerModel = OpenAiChatModel.builder()
 *         .modelName("gpt-4o-mini")  // Cheaper model for background tasks
 *         .build()
 * )
 *
 * // Ollama without AI summarization (basic mode)
 * val config = MemoryConfig(
 *     maxTokens = 4000,
 *     summarizerModel = null  // No AI summarization
 * )
 * ```
 */
data class MemoryConfig(
    /**
     * Maximum number of tokens to keep in memory (excluding summary).
     * Default: 8000 tokens (suitable for most models)
     */
    val maxTokens: Int = 8000,

    /**
     * Percentage (0.0-1.0) of maxTokens at which to trigger summarization.
     * Default: 0.75 (summarize when reaching 75% of capacity)
     */
    val summarizationThreshold: Double = 0.75,

    /**
     * Optional provider-specific token estimator for accurate token counting.
     * If null, uses default word count * 1.3 estimation.
     *
     * Examples:
     * - OpenAI: `OpenAiTokenCountEstimator(modelName)::estimateTokenCountInMessage`
     * - Default: `null` (uses word count heuristic)
     */
    val tokenEstimator: ((ChatMessage) -> Int)? = null,

    /**
     * Optional AI model for intelligent summarization.
     *
     * Best practices:
     * - Use a cheaper/faster model than the chat model (e.g., gpt-4o-mini vs gpt-4o)
     * - Use non-streaming model for background tasks
     * - Set to null to disable AI summarization (uses basic extractive summary)
     *
     * Examples:
     * - OpenAI: Use `gpt-4o-mini` (17x cheaper than gpt-4o)
     * - Anthropic: Use `claude-3-5-haiku` (15x cheaper than sonnet)
     * - Ollama: Use smaller model if multiple available, or null to save for chat
     */
    val summarizerModel: ChatModel? = null,

    /**
     * Enable async summarization (non-blocking).
     *
     * - true: Summarization happens in background thread (recommended for production)
     * - false: Summarization blocks message addition (useful for testing)
     *
     * Default: true
     */
    val enableAsyncSummarization: Boolean = true,

    /**
     * Timeout for summarization operations in seconds.
     * Default: 30 seconds
     */
    val summarizationTimeoutSeconds: Long = 30,
) {
    /**
     * Create a TokenAwareSummarizingMemory instance with this configuration.
     */
    fun createMemory(): TokenAwareSummarizingMemory {
        val builder = TokenAwareSummarizingMemory.builder()
            .maxTokens(maxTokens)
            .summarizationThreshold(summarizationThreshold)
            .asyncSummarization(enableAsyncSummarization)
            .summarizationTimeout(summarizationTimeoutSeconds)

        // Add provider-specific token estimator if provided
        if (tokenEstimator != null) {
            builder.tokenEstimator(tokenEstimator)
        }

        // Add AI-powered summarization if model provided
        if (summarizerModel != null) {
            builder.summarizer { text ->
                DefaultConversationSummarizer(summarizerModel).summarize(text)
            }
        }

        return builder.build()
    }

    companion object {
        /**
         * Basic configuration without AI summarization (free, fast, reliable)
         */
        fun basic(maxTokens: Int = 4000) = MemoryConfig(
            maxTokens = maxTokens,
            summarizerModel = null,
        )

        /**
         * Configuration with AI summarization
         */
        fun withAI(
            maxTokens: Int = 8000,
            summarizerModel: ChatModel,
            tokenEstimator: ((ChatMessage) -> Int)? = null,
        ) = MemoryConfig(
            maxTokens = maxTokens,
            summarizerModel = summarizerModel,
            tokenEstimator = tokenEstimator,
        )
    }
}
