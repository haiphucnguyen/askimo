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
import io.askimo.core.chat.domain.SessionMemory
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.logging.logger
import io.askimo.core.util.JsonUtils.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDateTime
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
 * Memory persistence is mandatory - sessionId and sessionMemoryRepository are required.
 * The memory automatically loads from database on creation and saves after every change.
 *
 * @param sessionId The session ID this memory belongs to (required for persistence)
 * @param sessionMemoryRepository Repository for persisting memory state (required)
 * @param maxTokens Maximum number of tokens to keep in memory (excluding summary), default 4000
 * @param tokenEstimator Function to estimate token count for a message (default: words * 1.3)
 * @param summarizationThreshold Percentage (0.0-1.0) of maxTokens at which to trigger summarization, default 0.75
 * @param summarizer Optional function that takes conversation text and returns a ConversationSummary (for AI-powered summarization)
 * @param asyncSummarization Whether to run summarization asynchronously, default true
 * @param summarizationTimeoutSeconds Timeout for summarization operations in seconds, default 30
 */
class TokenAwareSummarizingMemory(
    private val sessionId: String,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val maxTokens: Int = 4000,
    private val tokenEstimator: (ChatMessage) -> Int = defaultTokenEstimator(),
    private val summarizationThreshold: Double = 0.6,
    private val summarizer: ((String) -> ConversationSummary)? = null,
    asyncSummarization: Boolean = true,
    private val summarizationTimeoutSeconds: Long = 30,
) : ChatMemory,
    AutoCloseable {

    private val executorService: ExecutorService = if (asyncSummarization) {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "memory-summarizer").apply {
                isDaemon = true
            }
        }
    } else {
        Executors.newSingleThreadExecutor()
    }
    private val messages = Collections.synchronizedList(mutableListOf<ChatMessage>())

    @Volatile private var structuredSummary: ConversationSummary? = null

    @Volatile private var basicSummary: String? = null

    @Volatile private var summarizationInProgress = false
    private val summarizationLock = ReentrantLock()
    private val log = logger<TokenAwareSummarizingMemory>()

    init {
        loadFromDatabase()
    }

    override fun id(): Any = this.hashCode()

    /**
     * Add message to memory. Non-blocking - triggers async summarization if needed.
     * Automatically persists to database if configured with sessionId and repository.
     */
    override fun add(message: ChatMessage) {
        messages.add(message)
        log.debug("Added message. Total messages: ${messages.size}")

        // Persist to database after adding message
        persistToDatabase()

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
     *
     * System messages are excluded from summarization as they contain instructions,
     * not conversation content. They are preserved in the message list.
     */
    private fun summarizeAndPrune() {
        // Get only user and AI messages (exclude system messages from conversation)
        val conversationMessages = synchronized(messages) {
            messages.filterNot { it is SystemMessage }
        }

        if (conversationMessages.isEmpty()) return

        val messagesToSummarizeCount = (conversationMessages.size * 0.45).toInt().coerceAtLeast(1)

        // Copy messages to avoid holding lock during AI call
        val messagesToSummarize = conversationMessages.take(messagesToSummarizeCount)

        log.info("Summarizing $messagesToSummarizeCount out of ${conversationMessages.size} conversation messages (excluding system messages)")

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

        // Remove the oldest conversation messages from the original list
        // System messages are never removed during pruning
        synchronized(messages) {
            var removed = 0
            val iterator = messages.iterator()
            while (iterator.hasNext() && removed < messagesToSummarizeCount) {
                val msg = iterator.next()
                if (msg !is SystemMessage) {
                    iterator.remove()
                    removed++
                }
            }
        }

        persistToDatabase()

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

    /**
     * Build conversation text from messages for AI summarization.
     * Only includes User and AI messages, excluding system messages as they are instructions.
     */
    private fun buildConversationText(messages: List<ChatMessage>): String = buildString {
        messages.filterNot { it is SystemMessage }.forEach { message ->
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

    /**
     * Load memory state from database.
     * Called automatically during initialization.
     */
    private fun loadFromDatabase() {
        try {
            val savedMemory = sessionMemoryRepository.getBySessionId(sessionId)
            if (savedMemory != null) {
                // Deserialize and restore state
                val state = deserializeMemoryState(savedMemory)
                importState(state)
                log.info("Loaded memory from database for session: $sessionId (${messages.size} messages)")
            } else {
                log.debug("No existing memory found in database for session: $sessionId")
            }
        } catch (e: Exception) {
            log.error("Failed to load memory from database for session: $sessionId", e)
        }
    }

    /**
     * Persist current memory state to database.
     * Called automatically after adding messages or summarizing.
     */
    private fun persistToDatabase() {
        try {
            val state = exportState()
            val sessionMemory = serializeMemoryState(sessionId, state)
            sessionMemoryRepository.saveMemory(sessionMemory)
            log.debug("Persisted memory to database for session: $sessionId")
        } catch (e: Exception) {
            log.error("Failed to persist memory to database for session: $sessionId", e)
        }
    }

    /**
     * Serialize memory state to SessionMemory domain object for database storage.
     */
    private fun serializeMemoryState(
        sessionId: String,
        state: MemoryState,
    ): SessionMemory {
        val summaryJson = state.summary?.let {
            json.encodeToString(
                ConversationSummary.serializer(),
                it,
            )
        }

        // Convert ChatMessage to serializable format
        val serializableMessages = state.messages.map { msg ->
            SerializableMessage(
                type = when (msg) {
                    is UserMessage -> "user"
                    is AiMessage -> "assistant"
                    is SystemMessage -> "system"
                    else -> "unknown"
                },
                content = msg.getTextContent(),
            )
        }

        val messagesJson = json.encodeToString(
            ListSerializer(SerializableMessage.serializer()),
            serializableMessages,
        )

        return SessionMemory(
            sessionId = sessionId,
            memorySummary = summaryJson,
            memoryMessages = messagesJson,
            lastUpdated = LocalDateTime.now(),
        )
    }

    /**
     * Deserialize SessionMemory from database to MemoryState.
     */
    private fun deserializeMemoryState(
        sessionMemory: SessionMemory,
    ): MemoryState {
        val summary = sessionMemory.memorySummary?.let {
            json.decodeFromString(
                ConversationSummary.serializer(),
                it,
            )
        }

        val serializableMessages = json.decodeFromString(
            ListSerializer(SerializableMessage.serializer()),
            sessionMemory.memoryMessages,
        )

        // Convert back to ChatMessage
        val messages = serializableMessages.map { msg ->
            when (msg.type) {
                "user" -> UserMessage.from(msg.content)
                "assistant" -> AiMessage.from(msg.content)
                "system" -> SystemMessage.from(msg.content)
                else -> UserMessage.from(msg.content) // fallback
            }
        }

        return MemoryState(messages = messages, summary = summary)
    }

    /**
     * Serializable representation of a chat message for database storage.
     */
    @Serializable
    private data class SerializableMessage(
        val type: String,
        val content: String,
    )

    companion object {
        private const val MAX_SUMMARY_LENGTH = 500 // characters

        /**
         * Default token estimator that approximates token count as word count * 1.3
         */
        fun defaultTokenEstimator(): (ChatMessage) -> Int = { message ->
            val text = when (message) {
                is UserMessage -> message.singleText() ?: ""
                is AiMessage -> message.text() ?: ""
                is SystemMessage -> message.text() ?: ""
                else -> ""
            }
            (text.split("\\s+".toRegex()).size * 1.3).toInt()
        }
    }
}
