/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.config.AppConfig
import io.askimo.core.project.FileWatcherManager
import io.askimo.core.project.PgVectorContentRetriever
import io.askimo.core.project.PgVectorIndexer
import io.askimo.core.project.ProjectMeta
import io.askimo.core.project.buildRetrievalAugmentor
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.session.MemoryPolicy.KEEP_PER_PROVIDER_MODEL
import io.askimo.core.session.MemoryPolicy.RESET_FOR_THIS_COMBO
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

/**
 * Controls what happens to the *chat memory* when the active [ChatService] is re-created
 * (e.g., after `:set-param`, switching provider/model, or any programmatic rebuild).
 *
 * The “combo” refers to the pair **(provider, modelName)** used as the memory bucket key.
 *
 * - [KEEP_PER_PROVIDER_MODEL] – Reuse the existing memory bucket for the current combo.
 *   If the provider or model changes, a different bucket is selected automatically.
 *   Choose this to preserve conversation context across minor setting changes.
 *
 * - [RESET_FOR_THIS_COMBO] – Drop and recreate the memory bucket for the current combo
 *   before rebuilding. Choose this when you want a clean slate (benchmarks, prompt iteration,
 *   avoiding context carryover).
 */
enum class MemoryPolicy {
    /**
     * Reuse the existing memory for the current **(provider, modelName)**.
     *
     * If the provider/model changes, the session naturally switches to a different memory bucket
     * keyed by that new combo. Best for normal chat UX where continuity is expected.
     */
    KEEP_PER_PROVIDER_MODEL,

    /**
     * Clear and recreate the memory for the current **(provider, modelName)** before rebuilding.
     *
     * Useful when prior context could bias results or when you want reproducible, clean runs
     * after each parameter change (e.g., style/verbosity tweaks, API key changes, etc.).
     */
    RESET_FOR_THIS_COMBO,
}

data class Scope(
    val projectName: String,
    val projectDir: Path,
)

/**
 * Manages a chat session with language models, handling model creation, provider settings, and conversation memory.
 *
 * The Session class serves as the central coordinator for interactions between the CLI and language model providers.
 * It maintains:
 * - The active chat model instance
 * - Provider-specific settings
 * - Conversation memory for each provider/model combination
 * - Session parameters that control behavior
 *
 * Session instances are responsible for creating and configuring chat models based on the current
 * session parameters, managing conversation history through memory buckets, and providing access
 * to the active model for sending prompts and receiving responses.
 *
 * @property params The parameters that configure this session, including the current provider, model name,
 *                  and provider-specific settings
 */
class Session(
    val params: SessionParams,
) {
    private val memoryMap = mutableMapOf<String, ChatMemory>()

    var lastResponse: String? = null

    // Chat session support with intelligent context management
    val chatSessionRepository = ChatSessionRepository()
    var currentChatSession: ChatSession? = null

    // Configuration for context management
    private val maxRecentMessages = AppConfig.chat.maxRecentMessages
    private val maxTokensForContext = AppConfig.chat.maxTokensForContext
    private val summarizationThreshold = AppConfig.chat.summarizationThreshold

    /**
     * The active chat model for this session.
     * This property is initialized lazily and can only be set through setChatModel().
     */
    lateinit var chatService: ChatService
        private set

    /**
     * Sets the chat model for this session.
     *
     * @param chatService The chat model to use for this session
     */
    fun setChatService(chatService: ChatService) {
        this.chatService = chatService
    }

    /**
     * Checks if a chat service has been initialized for this session.
     *
     * @return true if chat service has been set, false otherwise
     */
    fun hasChatService(): Boolean = ::chatService.isInitialized

    /**
     * Gets the currently active model provider for this session.
     */
    fun getActiveProvider(): ModelProvider = params.currentProvider

    /**
     * Current project scope for this session, if any.
     *
     * When set via setScope(ProjectEntry), it records the human-readable project name
     * and the normalized absolute path to the project's root directory. A null value
     * means the session is not bound to any project (and project-specific RAG features
     * may be disabled).
     *
     * This property is read-only to callers; use setScope(...) to activate a project
     * and clearScope() to remove the association.
     */
    var scope: Scope? = null
        private set

    fun setScope(project: ProjectMeta) {
        scope = Scope(project.name, Paths.get(project.root).toAbsolutePath().normalize())
    }

    fun clearScope() {
        scope = null
        // Stop file watcher when clearing scope
        FileWatcherManager.stopCurrentWatcher()
    }

    /**
     * Gets the current provider's settings.
     */
    fun getCurrentProviderSettings(): ProviderSettings = params.providerSettings[params.currentProvider]
        ?: getModelFactory()?.defaultSettings()
        ?: NoopProviderSettings

    /**
     * Sets the provider-specific settings into the map.
     */
    fun setProviderSetting(
        provider: ModelProvider,
        settings: ProviderSettings,
    ) {
        params.providerSettings[provider] = settings
    }

    /**
     * Returns the registered factory for the current provider.
     */
    fun getModelFactory(): ChatModelFactory? = ProviderRegistry.getFactory(params.currentProvider)

    /**
     * Returns the registered factory for the given provider.
     */
    fun getModelFactory(provider: ModelProvider): ChatModelFactory? = ProviderRegistry.getFactory(provider)

    /**
     * Gets the provider-specific settings map, or creates defaults if missing.
     */
    fun getOrCreateProviderSettings(provider: ModelProvider): ProviderSettings = params.providerSettings.getOrPut(provider) {
        getModelFactory(provider)?.defaultSettings() ?: NoopProviderSettings
    }

    /**
     * Retrieves an existing chat memory for the given provider and model combination,
     * or creates a new one if none exists.
     *
     * @param provider The model provider to get or create memory for
     * @param model The model name to get or create memory for
     * @param settings Provider-specific settings that may influence memory creation
     * @return A [ChatMemory] instance that maintains conversation history for the specified
     *         provider/model combination
     */
    fun getOrCreateMemory(
        provider: ModelProvider,
        model: String,
        settings: ProviderSettings,
    ): ChatMemory {
        val key = "${provider.name}/$model"
        return memoryMap.getOrPut(key) {
            ProviderRegistry.getFactory(provider)?.createMemory(model, settings)
                ?: MessageWindowChatMemory.withMaxMessages(200)
        }
    }

    fun removeMemory(
        provider: ModelProvider,
        modelName: String,
    ) {
        memoryMap.remove("${provider.name}/$modelName")
    }

    /**
     * Rebuilds and returns a new instance of the active chat model based on current session parameters.
     *
     * @param memoryPolicy Controls whether to keep or reset the chat memory for the current
     *                     provider/model combination. Default is [MemoryPolicy.KEEP_PER_PROVIDER_MODEL].
     * @return A newly created [ChatService] instance that becomes the active model for this session.
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun rebuildActiveChatService(memoryPolicy: MemoryPolicy = KEEP_PER_PROVIDER_MODEL): ChatService {
        val provider = params.currentProvider
        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        if (memoryPolicy == RESET_FOR_THIS_COMBO) {
            val key = "${provider.name}/$modelName"
            memoryMap.remove(key)
        }

        val memory = getOrCreateMemory(provider, modelName, settings)
        val newModel = factory.create(modelName, settings, memory)
        setChatService(newModel)
        return newModel
    }

    /**
     * Returns the active [ChatService]. If a model has not been created yet for the
     * current (provider, model) and settings, it will be built now.
     *
     * @param memoryPolicy Controls whether the existing memory bucket for this
     * (provider, model) is reused or reset when building for the first time.
     */
    fun getChatService(memoryPolicy: MemoryPolicy = KEEP_PER_PROVIDER_MODEL): ChatService = if (hasChatService()) chatService else rebuildActiveChatService(memoryPolicy)

    /**
     * Enables Retrieval-Augmented Generation (RAG) for the current session using
     * the provided PgVectorIndexer.
     *
     * This method wires the indexer into a PgVectorContentRetriever and builds a
     * retrieval augmentor, then recreates the active ChatService with the same
     * provider, model, settings, and memory bucket, but augmented with retrieval.
     *
     * Notes:
     * - Memory is preserved; the conversation context for the current (provider, model)
     *   is reused.
     * - Requires that a model factory is registered for the current provider; otherwise
     *   an IllegalStateException is thrown.
     * - Typically called after setScope(...) when switching to a project that has
     *   indexed content.
     *
     * @param indexer The PgVector-backed indexer to use for retrieving relevant context.
     */
    fun enableRagWith(indexer: PgVectorIndexer) {
        val retriever = PgVectorContentRetriever(indexer)
        val rag = buildRetrievalAugmentor(retriever)

        val provider = params.currentProvider
        val model = params.model
        val settings = getCurrentProviderSettings()
        val memory = getOrCreateMemory(provider, model, settings)

        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val upgraded =
            factory.create(
                model = model,
                settings = settings,
                memory = memory,
                retrievalAugmentor = rag,
            )
        info("RAG enabled for $model")
        setChatService(upgraded)
    }

    /**
     * Starts a new chat session and makes it the current active session.
     *
     * @return The newly created ChatSession
     */
    fun startNewChatSession(): ChatSession {
        val session = chatSessionRepository.createSession("New Chat")
        currentChatSession = session
        return session
    }

    /**
     * Resumes an existing chat session by ID.
     *
     * @param sessionId The ID of the session to resume
     * @return true if the session was found and resumed, false otherwise
     */
    fun resumeChatSession(sessionId: String): Boolean {
        val session = chatSessionRepository.getSession(sessionId)
        return if (session != null) {
            currentChatSession = session
            true
        } else {
            false
        }
    }

    /**
     * Adds a message to the current chat session.
     *
     * @param role The role of the message sender ([MessageRole.USER] or [MessageRole.ASSISTANT])
     * @param content The content of the message
     */
    fun addChatMessage(role: MessageRole, content: String) {
        currentChatSession?.let { session ->
            chatSessionRepository.addMessage(session.id, role, content)

            // Generate title from first user message
            if (role == MessageRole.USER) {
                val messages = chatSessionRepository.getMessages(session.id)
                if (messages.count { it.role == MessageRole.USER } == 1) {
                    chatSessionRepository.generateAndUpdateTitle(session.id, content)
                }
            }
        }
    }

    /**
     * Gets intelligent context for the current session including summary + recent messages
     */
    fun getContextForSession(): List<ChatMessage> {
        val sessionId = currentChatSession?.id ?: return emptyList()
        val contextMessages = mutableListOf<ChatMessage>()

        // 1. Add structured summary as system context
        val summary = chatSessionRepository.getConversationSummary(sessionId)
        if (summary != null) {
            val structuredContext = buildStructuredContext(summary)
            contextMessages.add(
                ChatMessage(
                    id = "system-context",
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = structuredContext,
                    createdAt = summary.createdAt,
                ),
            )
        }

        // 2. Add recent messages for conversation flow
        val recentMessages = chatSessionRepository.getRecentMessages(sessionId, maxRecentMessages)
        contextMessages.addAll(recentMessages)

        return trimContextByTokens(contextMessages, maxTokensForContext)
    }

    private fun buildStructuredContext(summary: ConversationSummary): String {
        val contextBuilder = StringBuilder()

        contextBuilder.append("Previous conversation context:\n\n")

        // Add key facts in a structured way
        if (summary.keyFacts.isNotEmpty()) {
            contextBuilder.append("Key Information:\n")
            summary.keyFacts.forEach { (key, value) ->
                contextBuilder.append("- $key: $value\n")
            }
            contextBuilder.append("\n")
        }

        // Add main topics
        if (summary.mainTopics.isNotEmpty()) {
            contextBuilder.append("Main topics discussed: ${summary.mainTopics.joinToString(", ")}\n\n")
        }

        // Add recent context
        if (summary.recentContext.isNotEmpty()) {
            contextBuilder.append("Recent conversation flow: ${summary.recentContext}\n\n")
        }

        contextBuilder.append("Continue the conversation naturally based on this context.")

        return contextBuilder.toString()
    }

    private fun trimContextByTokens(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        var totalChars = 0
        val result = mutableListOf<ChatMessage>()

        // Keep system messages (summaries) and trim from the oldest user/assistant messages
        val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
        val conversationMessages = messages.filter { it.role != MessageRole.SYSTEM }.reversed()

        result.addAll(systemMessages)
        totalChars += systemMessages.sumOf { it.content.length }

        for (message in conversationMessages) {
            val messageChars = message.content.length
            if (totalChars + messageChars > maxTokens * 4) break

            result.add(0, message) // Add to beginning to maintain order
            totalChars += messageChars
        }

        return result
    }

    /**
     * Prepare context and save user message, return the prompt to use for streaming.
     * This allows ChatCli to handle streaming directly while still managing session context.
     */
    fun prepareContextAndGetPrompt(userMessage: String): String {
        if (currentChatSession == null) {
            startNewChatSession()
        }

        // Save user message
        addChatMessage(MessageRole.USER, userMessage)

        // Prepare the prompt with context
        return preparePromptWithContext(userMessage)
    }

    /**
     * Save the AI response after streaming is complete
     */
    fun saveAiResponse(response: String) {
        addChatMessage(MessageRole.ASSISTANT, response)
        triggerSummarizationIfNeeded()
    }

    /**
     * Prepare the prompt with conversation context
     */
    private fun preparePromptWithContext(userMessage: String): String {
        // Get smart context (summary + recent messages)
        val contextMessages = getContextForSession()

        return if (contextMessages.isNotEmpty()) {
            // Convert conversation history into a single prompt for streaming
            val conversationText = contextMessages.joinToString("\n\n") { message ->
                when (message.role) {
                    MessageRole.USER -> "User: ${message.content}"
                    MessageRole.ASSISTANT -> "Assistant: ${message.content}"
                    MessageRole.SYSTEM -> "System: ${message.content}"
                }
            }

            // If we have conversation history, create a prompt that includes context
            if (contextMessages.size > 1) {
                "Previous conversation context:\n$conversationText\n\nPlease continue the conversation naturally."
            } else {
                // If only one message, just use its content
                contextMessages.lastOrNull()?.content ?: userMessage
            }
        } else {
            // Use simple prompt for first message
            userMessage
        }
    }

    private fun triggerSummarizationIfNeeded() {
        val sessionId = currentChatSession?.id ?: return
        val messageCount = chatSessionRepository.getMessageCount(sessionId)

        // Summarize every N messages
        if (messageCount > summarizationThreshold && messageCount % summarizationThreshold == 0) {
            summarizeOlderMessages()
        }
    }

    private fun summarizeOlderMessages() {
        val sessionId = currentChatSession?.id ?: return

        try {
            // Get messages that haven't been summarized yet
            val existingSummary = chatSessionRepository.getConversationSummary(sessionId)
            val lastSummarizedId = existingSummary?.lastSummarizedMessageId ?: ""

            val messagesToSummarize = if (lastSummarizedId.isEmpty()) {
                // First summarization - get older messages, leave recent ones
                val allMessages = chatSessionRepository.getMessages(sessionId)
                allMessages.dropLast(maxRecentMessages).takeLast(40)
            } else {
                chatSessionRepository.getMessagesAfter(sessionId, lastSummarizedId, 40)
            }

            if (messagesToSummarize.size >= 20) { // Only summarize if we have enough content
                val newSummary = createIntelligentSummary(messagesToSummarize)
                val mergedSummary = mergeWithExistingSummary(newSummary, existingSummary)
                chatSessionRepository.saveSummary(mergedSummary)
            }
        } catch (e: Exception) {
            debug("Error while summarizing: ${e.message}", e)
            println("Warning: Failed to create conversation summary: ${e.message}")
        }
    }

    private fun createIntelligentSummary(messages: List<ChatMessage>): ConversationSummary {
        val sessionId = currentChatSession?.id ?: throw IllegalStateException("No active session")

        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }

        // Create a structured summarization prompt
        val summaryPrompt = """
            Analyze this conversation and extract structured information. Focus on factual information and key details while ignoring off-topic content.

            Conversation:
            $conversationText

            Please provide a response in the following JSON format:
            {
                "keyFacts": {
                    // Extract specific factual information as key-value pairs
                    // For aquarium: "tankSize": "20 gallons", "substrate": "sand gravel", "cycled": "false"
                    // For coding: "language": "Python", "framework": "Django", "database": "PostgreSQL"
                    // For cooking: "cuisine": "Italian", "dietaryRestrictions": "vegetarian", "skillLevel": "beginner"
                },
                "mainTopics": [
                    // List of main topics discussed (e.g., ["aquarium cycling", "plant selection", "water parameters"])
                ],
                "recentContext": "Brief summary of the most recent conversation flow and current focus"
            }

            Rules:
            - Only include factual, relevant information in keyFacts
            - Ignore casual conversation, greetings, or off-topic messages
            - If someone mentions specific numbers, names, or technical details, include them
            - Group related facts logically
            - Keep recentContext under 100 words
            - If no relevant facts are found, use empty objects/arrays
        """.trimIndent()

        try {
            val summaryResponse = getChatService().sendMessage(summaryPrompt)
            return parseAISummaryResponse(sessionId, summaryResponse, messages.last().id)
        } catch (e: Exception) {
            debug("Error while generating summary: ${e.message}", e)
            return createFallbackSummary(sessionId, messages)
        }
    }

    private fun parseAISummaryResponse(sessionId: String, response: String, lastMessageId: String): ConversationSummary {
        try {
            // Extract JSON from response (handle cases where AI adds explanation text)
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                return createFallbackSummary(sessionId, emptyList())
            }

            val jsonText = response.substring(jsonStart, jsonEnd)

            // Parse JSON
            val jsonObject = Json.parseToJsonElement(jsonText).jsonObject

            val keyFacts = jsonObject["keyFacts"]?.jsonObject?.mapValues {
                it.value.jsonPrimitive.content
            } ?: emptyMap()

            val mainTopics = jsonObject["mainTopics"]?.jsonArray?.map {
                it.jsonPrimitive.content
            } ?: emptyList()

            val recentContext = jsonObject["recentContext"]?.jsonPrimitive?.content ?: ""

            return ConversationSummary(
                sessionId = sessionId,
                keyFacts = keyFacts,
                mainTopics = mainTopics,
                recentContext = recentContext,
                lastSummarizedMessageId = lastMessageId,
                createdAt = LocalDateTime.now(),
            )
        } catch (e: Exception) {
            return createFallbackSummary(sessionId, emptyList())
        }
    }

    private fun createFallbackSummary(sessionId: String, messages: List<ChatMessage>): ConversationSummary {
        // Simple fallback summary
        val topics = if (messages.isNotEmpty()) {
            listOf("general conversation")
        } else {
            emptyList()
        }

        val context = if (messages.isNotEmpty()) {
            "Ongoing conversation with ${messages.size} messages"
        } else {
            "New conversation"
        }

        return ConversationSummary(
            sessionId = sessionId,
            keyFacts = emptyMap(),
            mainTopics = topics,
            recentContext = context,
            lastSummarizedMessageId = messages.lastOrNull()?.id ?: "",
            createdAt = LocalDateTime.now(),
        )
    }

    private fun mergeWithExistingSummary(newSummary: ConversationSummary, existingSummary: ConversationSummary?): ConversationSummary {
        if (existingSummary == null) return newSummary

        // Merge key facts (new facts override old ones, but keep non-conflicting facts)
        val mergedFacts = existingSummary.keyFacts.toMutableMap()
        newSummary.keyFacts.forEach { (key, value) ->
            // Update existing facts or add new ones
            mergedFacts[key] = value
        }

        // Merge topics (keep unique topics)
        val mergedTopics = (existingSummary.mainTopics + newSummary.mainTopics).distinct()

        return ConversationSummary(
            sessionId = newSummary.sessionId,
            keyFacts = mergedFacts,
            mainTopics = mergedTopics,
            recentContext = newSummary.recentContext,
            lastSummarizedMessageId = newSummary.lastSummarizedMessageId,
            createdAt = newSummary.createdAt,
        )
    }
}
