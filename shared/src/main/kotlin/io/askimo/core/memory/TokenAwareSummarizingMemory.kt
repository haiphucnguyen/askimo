/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.memory.ChatMemory
import io.askimo.core.logging.logger
import kotlinx.serialization.Serializable
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Structured summary format for conversation analysis
 */
@Serializable
data class ConversationSummary(
    val keyFacts: Map<String, String> = emptyMap(),
    val mainTopics: List<String> = emptyList(),
    val recentContext: String = "",
)

/**
 * This memory keeps recent messages in full detail while creating either a structured
 * AI-powered summary or a simple extractive summary of older messages to preserve
 * context without exceeding token limits.
 *
 * @param maxTokens Maximum number of tokens to keep in memory (excluding summary)
 * @param tokenEstimator Function to estimate token count for a message (words * 1.3 is a reasonable approximation)
 * @param summarizationThreshold Percentage (0.0-1.0) of maxTokens at which to trigger summarization
 * @param summarizer Optional function that takes conversation text and returns a ConversationSummary (for AI-powered summarization)
 * @param executorService ExecutorService for async summarization operations
 * @param summarizationTimeoutSeconds Timeout for summarization operations in seconds
 */
class TokenAwareSummarizingMemory private constructor(
    private val maxTokens: Int,
    private val tokenEstimator: (ChatMessage) -> Int,
    private val summarizationThreshold: Double,
    private val summarizer: ((String) -> ConversationSummary)?,
    private val executorService: ExecutorService,
    private val summarizationTimeoutSeconds: Long,
) : ChatMemory,
    AutoCloseable {
    private val messages = Collections.synchronizedList(mutableListOf<ChatMessage>())

    @Volatile private var structuredSummary: ConversationSummary? = null

    @Volatile private var basicSummary: String? = null

    @Volatile private var summarizationInProgress = false
    private val summarizationLock = ReentrantLock()
    private val log = logger<TokenAwareSummarizingMemory>()

    override fun id(): Any = this.hashCode()

    /**
     * Add message to memory. Non-blocking - triggers async summarization if needed.
     */
    override fun add(message: ChatMessage) {
        messages.add(message)
        log.debug("Added message. Total messages: ${messages.size}")

        val totalTokens = estimateTotalTokens()
        val threshold = (maxTokens * summarizationThreshold).toInt()

        log.debug("Current tokens: $totalTokens, Threshold: $threshold, Max: $maxTokens")

        if (totalTokens > threshold && !summarizationInProgress) {
            log.info("Token count ($totalTokens) exceeded threshold ($threshold). Triggering async summarization.")
            triggerAsyncSummarization()
        }
    }

    override fun messages(): List<ChatMessage> = buildList {
        structuredSummary?.let { summary ->
            add(SystemMessage.from(buildStructuredSummaryMessage(summary)))
        } ?: basicSummary?.let { summary ->
            // Fallback to basic summary if structured one isn't available
            add(
                SystemMessage.from(
                    """
                    |Previous conversation summary:
                    |$summary
                    |
                    |Continue the conversation below with this context in mind.
                    """.trimMargin(),
                ),
            )
        }

        synchronized(messages) {
            addAll(messages)
        }
    }

    override fun clear() {
        summarizationLock.withLock {
            log.info("Clearing memory. Removing ${messages.size} messages and summary.")
            messages.clear()
            structuredSummary = null
            basicSummary = null
        }
    }

    /**
     * Shutdown executor and wait for pending summarization
     */
    override fun close() {
        log.info("Closing memory, waiting for pending summarization...")
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
                log.warn("Forced shutdown of summarization executor")
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
            log.warn("Interrupted during shutdown, forced shutdown of summarization executor", e)
        }
    }

    /**
     * Estimates total token count of all messages in memory (thread-safe)
     */
    private fun estimateTotalTokens(): Int = synchronized(messages) {
        messages.sumOf { message ->
            tokenEstimator(message)
        }
    }

    /**
     * Trigger async summarization without blocking caller
     */
    private fun triggerAsyncSummarization() {
        if (!summarizationLock.tryLock()) {
            log.debug("Summarization already in progress, skipping")
            return
        }

        CompletableFuture.runAsync({
            try {
                summarizationInProgress = true
                summarizeAndPrune()
            } catch (e: Exception) {
                log.error("Async summarization failed", e)
            } finally {
                summarizationInProgress = false
                if (summarizationLock.isHeldByCurrentThread) {
                    summarizationLock.unlock()
                }
            }
        }, executorService)
            .orTimeout(summarizationTimeoutSeconds, TimeUnit.SECONDS)
            .exceptionally { throwable ->
                log.error("Summarization timed out or failed", throwable)
                // Ensure cleanup on timeout
                summarizationInProgress = false
                // Only unlock if current thread owns the lock
                if (summarizationLock.isHeldByCurrentThread) {
                    summarizationLock.unlock()
                }
                null
            }
    }

    /**
     * Summarizes the oldest portion of the conversation and removes those messages
     * to free up token space while preserving context.
     */
    private fun summarizeAndPrune() {
        if (messages.isEmpty()) return

        val messagesToSummarizeCount = (messages.size * 0.45).toInt().coerceAtLeast(1)

        // Copy messages to avoid holding lock during AI call
        val messagesToSummarize = synchronized(messages) {
            messages.take(messagesToSummarizeCount).toList()
        }

        log.info("Summarizing $messagesToSummarizeCount out of ${messages.size} messages")

        try {
            if (summarizer != null) {
                // Call AI-powered summarizer
                generateStructuredSummary(messagesToSummarize)
            } else {
                generateBasicSummary(messagesToSummarize)
            }
        } catch (e: Exception) {
            log.error("Summarization failed, using basic fallback", e)
            generateBasicSummary(messagesToSummarize)
        }

        // Remove messages AFTER summarization succeeds
        synchronized(messages) {
            repeat(messagesToSummarizeCount.coerceAtMost(messages.size)) {
                messages.removeAt(0)
            }
        }

        log.info("Summarization complete. Remaining: ${messages.size}, Tokens: ${estimateTotalTokens()}")
    }

    /**
     * Generate structured summary using the provided summarizer function
     */
    private fun generateStructuredSummary(messagesToSummarize: List<ChatMessage>) {
        val conversationText = buildConversationText(messagesToSummarize)
        val newSummary = summarizer!!(conversationText)

        structuredSummary = mergeWithExistingSummary(newSummary)
        log.info("Generated structured summary with ${newSummary.keyFacts.size} facts, ${newSummary.mainTopics.size} topics")
    }

    private fun mergeWithExistingSummary(newSummary: ConversationSummary): ConversationSummary {
        val existing = structuredSummary
        return if (existing != null) {
            ConversationSummary(
                keyFacts = existing.keyFacts + newSummary.keyFacts,
                mainTopics = (existing.mainTopics + newSummary.mainTopics).distinct(),
                recentContext = newSummary.recentContext,
            )
        } else {
            newSummary
        }
    }

    /**
     * Generate basic extractive summary (no AI required)
     */
    private fun generateBasicSummary(messagesToSummarize: List<ChatMessage>) {
        val newSummary = buildString {
            append("Earlier conversation (${messagesToSummarize.size} messages):\n")

            // Extract key exchanges - first and last few messages from the batch
            val keyMessages = if (messagesToSummarize.size <= 6) {
                messagesToSummarize
            } else {
                messagesToSummarize.take(2) + messagesToSummarize.takeLast(2)
            }

            keyMessages.forEach { message ->
                val text = message.getTextContent()
                // Truncate long messages
                val truncated = if (text.length > 150) {
                    text.take(150) + "..."
                } else {
                    text
                }
                appendLine("• ${message.getRoleName()}: $truncated")
            }

            if (messagesToSummarize.size > 6) {
                appendLine("... (${messagesToSummarize.size - 4} more messages)")
            }
        }.take(MAX_SUMMARY_LENGTH)

        basicSummary = if (basicSummary != null) {
            """
            |$basicSummary
            |
            |$newSummary
            """.trimMargin().take(MAX_SUMMARY_LENGTH * 2)
        } else {
            newSummary
        }

        log.info("Generated basic summary (${newSummary.length} chars)")
    }

    /**
     * Extract role name from message
     */
    private fun ChatMessage.getRoleName(): String = when (this) {
        is UserMessage -> "User"
        is AiMessage -> "Assistant"
        is SystemMessage -> "System"
        else -> "Unknown"
    }

    /**
     * Extract text content from message
     */
    private fun ChatMessage.getTextContent(): String = when (this) {
        is UserMessage -> this.singleText() ?: ""
        is AiMessage -> this.text() ?: ""
        is SystemMessage -> this.text() ?: ""
        else -> ""
    }

    private fun buildConversationText(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            appendLine("${message.getRoleName()}: ${message.getTextContent()}")
            appendLine()
        }
    }

    private fun buildStructuredSummaryMessage(summary: ConversationSummary): String = buildString {
        appendLine("=== CONVERSATION CONTEXT ===")
        appendLine()

        if (summary.keyFacts.isNotEmpty()) {
            appendLine("Key Facts:")
            summary.keyFacts.forEach { (key, value) ->
                appendLine("  • $key: $value")
            }
            appendLine()
        }

        if (summary.mainTopics.isNotEmpty()) {
            appendLine("Main Topics: ${summary.mainTopics.joinToString(", ")}")
            appendLine()
        }

        if (summary.recentContext.isNotBlank()) {
            appendLine("Recent Context:")
            appendLine(summary.recentContext)
            appendLine()
        }

        appendLine("Continue the conversation below with this context in mind.")
    }

    /**
     * Export the current memory state for persistence.
     *
     * @return MemoryState containing messages and summary
     */
    fun exportState(): MemoryState = MemoryState(
        messages = messages.toList(),
        summary = structuredSummary,
    )

    /**
     * Import a previously saved memory state.
     *
     * @param state The memory state to restore
     */
    fun importState(state: MemoryState) {
        messages.clear()
        messages.addAll(state.messages)
        structuredSummary = state.summary
        basicSummary = null // Clear basic summary when importing
    }

    /**
     * Data class representing the complete memory state for persistence.
     */
    data class MemoryState(
        val messages: List<ChatMessage>,
        val summary: ConversationSummary?,
    )

    companion object {
        private const val MAX_SUMMARY_LENGTH = 500 // characters

        /**
         * Creates a builder for TokenAwareSummarizingMemory
         */
        fun builder(): Builder = Builder()
    }

    /**
     * Builder for TokenAwareSummarizingMemory with fluent API
     */
    class Builder {
        private var maxTokens: Int = 4000
        private var tokenEstimator: ((ChatMessage) -> Int)? = null
        private var summarizationThreshold: Double = 0.75
        private var summarizer: ((String) -> ConversationSummary)? = null
        private var asyncSummarization: Boolean = true
        private var summarizationTimeoutSeconds: Long = 30

        /**
         * Set maximum tokens to keep in memory (excluding summary)
         */
        fun maxTokens(maxTokens: Int) = apply { this.maxTokens = maxTokens }

        /**
         * Set custom token estimator function. If not set, uses default word count * 1.3 estimation.
         *
         * Example with OpenAI tokenizer:
         * ```
         * val tokenEstimator = OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_4_1_MINI)
         * .tokenEstimator { message -> tokenEstimator.estimateTokenCountInMessage(message) }
         * ```
         *
         * Or simply pass the tokenizer object directly:
         * ```
         * .tokenEstimator(OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_4_1_MINI)::estimateTokenCountInMessage)
         * ```
         */
        fun tokenEstimator(estimator: (ChatMessage) -> Int) = apply { this.tokenEstimator = estimator }

        /**
         * Set summarization threshold (0.0-1.0). Default is 0.75 (75% of maxTokens)
         */
        fun summarizationThreshold(threshold: Double) = apply { this.summarizationThreshold = threshold }

        /**
         * Set optional AI-powered summarizer function
         */
        fun summarizer(summarizer: (String) -> ConversationSummary) = apply { this.summarizer = summarizer }

        /**
         * Enable/disable async summarization. Default: true (async).
         * Disable only for testing or simple use cases.
         */
        fun asyncSummarization(enabled: Boolean) = apply { this.asyncSummarization = enabled }

        /**
         * Timeout for summarization operations in seconds. Default: 30.
         */
        fun summarizationTimeout(seconds: Long) = apply { this.summarizationTimeoutSeconds = seconds }

        /**
         * Build the TokenAwareSummarizingMemory instance
         */
        fun build(): TokenAwareSummarizingMemory {
            val finalTokenEstimator = tokenEstimator ?: { message ->
                // Default: approximate token count as word count * 1.3
                val text = when (message) {
                    is UserMessage -> message.singleText() ?: ""
                    is AiMessage -> message.text() ?: ""
                    is SystemMessage -> message.text() ?: ""
                    else -> ""
                }
                (text.split("\\s+".toRegex()).size * 1.3).toInt()
            }

            val executorService = if (asyncSummarization) {
                Executors.newSingleThreadExecutor { r ->
                    Thread(r, "memory-summarizer").apply {
                        isDaemon = true
                    }
                }
            } else {
                Executors.newSingleThreadExecutor()
            }

            return TokenAwareSummarizingMemory(
                maxTokens = maxTokens,
                tokenEstimator = finalTokenEstimator,
                summarizationThreshold = summarizationThreshold,
                summarizer = summarizer,
                executorService = executorService,
                summarizationTimeoutSeconds = summarizationTimeoutSeconds,
            )
        }
    }
}
