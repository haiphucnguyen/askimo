/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.rag.RetrievalAugmentor
import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.ConversationSummary
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ConversationSummaryRepository
import io.askimo.core.config.AppConfig
import io.askimo.core.context.MemoryPolicy.KEEP_PER_PROVIDER_MODEL
import io.askimo.core.context.MemoryPolicy.RESET_FOR_THIS_COMBO
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.project.FileWatcherManager
import io.askimo.core.project.PgVectorContentRetriever
import io.askimo.core.project.PgVectorIndexer
import io.askimo.core.project.ProjectMeta
import io.askimo.core.project.buildRetrievalAugmentor
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopChatClient
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

/**
 * Controls what happens to the *chat memory* when the active [ChatClient] is re-created
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
 * @property mode The execution mode indicating how the user is running the application
 */
class AppContext(
    val params: AppContextParams,
    val mode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
) {
    private val log = logger<AppContext>()
    private val memoryMap = mutableMapOf<String, ChatMemory>()

    var lastResponse: String? = null

    // Chat session support with intelligent context management
    val chatSessionRepository = ChatSessionRepository()
    val chatMessageRepository = ChatMessageRepository()
    val conversationSummaryRepository = ConversationSummaryRepository()
    var currentChatSession: ChatSession? = null

    /**
     * System directive for the AI, typically used for language instructions or global behavior.
     * This can be updated when the user changes locale or wants to modify AI's behavior.
     */
    var systemDirective: String? = null

    // Configuration for context management
    private val maxRecentMessages = AppConfig.chat.maxRecentMessages
    private val maxTokensForContext = AppConfig.chat.maxTokensForContext
    private val summarizationThreshold = AppConfig.chat.summarizationThreshold

    /**
     * The active chat model for this session.
     * This property is initialized lazily and can only be set through setChatModel().
     */
    lateinit var chatClient: ChatClient
        private set

    /**
     * Sets the chat model for this session.
     *
     * @param chatClient The chat model to use for this session
     */
    fun setChatClient(chatClient: ChatClient) {
        this.chatClient = chatClient
        if (chatClient is NoopChatClient) {
            (this.chatClient as NoopChatClient).appContext = this
        }
    }

    /**
     * Checks if a chat client has been initialized for this session.
     *
     * @return true if chat client has been set, false otherwise
     */
    fun hasChatClient(): Boolean = ::chatClient.isInitialized

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
    fun getModelFactory(): ChatModelFactory<*>? = ProviderRegistry.getFactory(params.currentProvider)

    /**
     * Returns the registered factory for the given provider.
     */
    fun getModelFactory(provider: ModelProvider): ChatModelFactory<*>? = ProviderRegistry.getFactory(provider)

    /**
     * Gets the provider-specific settings map, or creates defaults if missing.
     */
    fun getOrCreateProviderSettings(provider: ModelProvider): ProviderSettings = params.providerSettings.getOrPut(provider) {
        getModelFactory(provider)?.defaultSettings() ?: NoopProviderSettings
    }

    /**
     * Safely calls createMemory on a factory with the given settings.
     * Uses unchecked cast which is safe because the registry ensures
     * factory and settings types match for each provider.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSettings> createMemoryFromFactory(
        factory: ChatModelFactory<*>,
        model: String,
        settings: T,
    ): ChatMemory = (factory as ChatModelFactory<T>).createMemory(model, settings)

    /**
     * Safely calls create on a factory with the given settings.
     * Uses unchecked cast which is safe because the registry ensures
     * factory and settings types match for each provider.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSettings> createChatClientFromFactory(
        factory: ChatModelFactory<*>,
        model: String,
        settings: T,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor? = null,
        executionMode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
    ): ChatClient = (factory as ChatModelFactory<T>).create(model, settings, memory, retrievalAugmentor, executionMode)

    /**
     * Returns the provider-specific chat memory for a given (provider, model) pair,
     * creating one if it doesn't exist yet.
     *
     * @param provider The model provider
     * @param model The name of the model
     * @param settings Provider-specific configuration settings used to create memory if needed
     * @return The chat memory instance for this provider/model combination
     */
    fun getOrCreateMemory(
        provider: ModelProvider,
        model: String,
        settings: ProviderSettings,
    ): ChatMemory {
        val key = "${provider.name}/$model"
        return memoryMap.getOrPut(key) {
            ProviderRegistry.getFactory(provider)?.let { factory ->
                createMemoryFromFactory(factory, model, settings)
            } ?: MessageWindowChatMemory.withMaxMessages(200)
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
     * @return A newly created [ChatClient] instance that becomes the active model for this session.
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun rebuildActiveChatClient(memoryPolicy: MemoryPolicy = KEEP_PER_PROVIDER_MODEL): ChatClient {
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
        val newModel = createChatClientFromFactory(factory, modelName, settings, memory, executionMode = mode)
        setChatClient(newModel)
        return newModel
    }

    /**
     * Returns the active [ChatClient]. If a model has not been created yet for the
     * current (provider, model) and settings, it will be built now.
     *
     * @param memoryPolicy Controls whether the existing memory bucket for this
     * (provider, model) is reused or reset when building for the first time.
     */
    fun getChatClient(memoryPolicy: MemoryPolicy = KEEP_PER_PROVIDER_MODEL): ChatClient = if (hasChatClient()) chatClient else rebuildActiveChatClient(memoryPolicy)

    /**
     * Enables Retrieval-Augmented Generation (RAG) for the current session using
     * the provided PgVectorIndexer.
     *
     * This method wires the indexer into a PgVectorContentRetriever and builds a
     * retrieval augmentor, then recreates the active ChatClient with the same
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

        val upgraded = createChatClientFromFactory(
            factory = factory,
            model = model,
            settings = settings,
            memory = memory,
            retrievalAugmentor = rag,
            executionMode = mode,
        )
        log.display("RAG enabled for $model")
        setChatClient(upgraded)
    }

    /**
     * Starts a new chat session.
     * Only persists session for CLI_INTERACTIVE and DESKTOP modes.
     *
     * @param directiveId Optional directive ID to apply to this session
     * @return The newly created ChatSession
     */
    fun startNewChatSession(directiveId: String? = null): ChatSession {
        val session = when (mode) {
            ExecutionMode.CLI_INTERACTIVE, ExecutionMode.DESKTOP -> {
                // Persist session to database for interactive modes
                chatSessionRepository.createSession(
                    ChatSession(
                        id = "",
                        title = "New Chat",
                        directiveId = directiveId,
                    ),
                )
            }
            ExecutionMode.CLI_PROMPT -> {
                ChatSession(
                    id = "temp-${System.currentTimeMillis()}",
                    title = "Temporary Session",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    directiveId = directiveId,
                )
            }
        }
        currentChatSession = session
        return session
    }

    /**
     * Resumes an existing chat session by ID.
     * Only available for CLI_INTERACTIVE and DESKTOP modes.
     *
     * @param sessionId The ID of the session to resume
     * @return true if the session was found and resumed, false otherwise
     */
    fun resumeChatSession(sessionId: String): Boolean {
        if (mode == ExecutionMode.CLI_PROMPT) {
            return false
        }

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
     * Only persists messages for CLI_INTERACTIVE and DESKTOP modes.
     *
     * @param role The role of the message sender ([MessageRole.USER] or [MessageRole.ASSISTANT])
     * @param content The content of the message
     */
    fun addChatMessage(role: MessageRole, content: String) {
        if (mode == ExecutionMode.CLI_PROMPT) {
            return
        }

        currentChatSession?.let { session ->
            chatMessageRepository.addMessage(
                ChatMessage(
                    id = "", // Will be auto-generated
                    sessionId = session.id,
                    role = role,
                    content = content,
                ),
            )
            chatSessionRepository.touchSession(session.id)

            if (role == MessageRole.USER) {
                val messages = chatMessageRepository.getActiveMessages(session.id)
                if (messages.count { it.role == MessageRole.USER } == 1) {
                    chatSessionRepository.generateAndUpdateTitle(session.id, content)
                }
            }
        }
    }

    /**
     * Set or update the directive for the current chat session.
     * @param directiveId The directive ID to set (null to clear directive)
     * @return true if updated successfully, false if no active session or update failed
     */
    fun setCurrentSessionDirective(directiveId: String?): Boolean {
        val sessionId = currentChatSession?.id ?: return false

        // Skip persistence for CLI_PROMPT mode
        if (mode == ExecutionMode.CLI_PROMPT) {
            // Update in-memory session only
            currentChatSession = currentChatSession?.copy(directiveId = directiveId)
            return true
        }

        // Update in database and reload session
        val success = chatSessionRepository.updateSessionDirective(sessionId, directiveId)
        if (success) {
            // Reload the session to get updated data
            currentChatSession = chatSessionRepository.getSession(sessionId)
        }
        return success
    }

    /**
     * Gets intelligent context for the current session including summary + recent messages.
     * For CLI_PROMPT mode, returns empty list since no context is persisted.
     */
    fun getContextForSession(): List<ChatMessage> {
        // No context for non-interactive CLI mode
        if (mode == ExecutionMode.CLI_PROMPT) {
            return emptyList()
        }

        val sessionId = currentChatSession?.id ?: return emptyList()
        val contextMessages = mutableListOf<ChatMessage>()

        // 1. Add structured summary as system context
        val summary = conversationSummaryRepository.getConversationSummary(sessionId)
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
        // Use database-level filtering to guarantee exact count of active messages
        val recentMessages = chatMessageRepository.getRecentActiveMessages(sessionId, maxRecentMessages)
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

            result.add(0, message)
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
     * Prepare context and get prompt for a SPECIFIC chat ID.
     * This is thread-safe and doesn't rely on currentChatSession.
     *
     * @param userMessage The user's message
     * @param chatId The specific chat ID to save to
     * @return The prompt with context
     */
    fun prepareContextAndGetPromptForChat(userMessage: String, chatId: String): String {
        // Skip persistence for CLI_PROMPT mode
        if (mode == ExecutionMode.CLI_PROMPT) {
            return userMessage
        }

        // Save user message to the SPECIFIC chat ID (not currentChatSession)
        chatMessageRepository.addMessage(
            ChatMessage(
                id = "", // Will be auto-generated
                sessionId = chatId,
                role = MessageRole.USER,
                content = userMessage,
            ),
        )
        chatSessionRepository.touchSession(chatId)

        // Generate title from first user message
        val messages = chatMessageRepository.getMessages(chatId)
        if (messages.count { it.role == MessageRole.USER } == 1) {
            chatSessionRepository.generateAndUpdateTitle(chatId, userMessage)
        }

        // Get context from the SPECIFIC chat
        val chatSession = chatSessionRepository.getSession(chatId) ?: return userMessage

        // 🔍 Count tokens for this chat session (only active messages)
        val activeMessages = messages.filter { !it.isOutdated }

        // Temporarily set currentChatSession to get context (but don't keep it)
        val previousSession = currentChatSession
        try {
            currentChatSession = chatSession
            val prompt = preparePromptWithContext(userMessage)
            return prompt
        } finally {
            currentChatSession = previousSession
        }
    }

    /**
     * Save the AI response after streaming is complete
     */
    fun saveAiResponse(response: String) {
        addChatMessage(MessageRole.ASSISTANT, response)
        triggerSummarizationIfNeeded()
    }

    /**
     * Save the AI response to a specific session (used when session may have changed).
     * This ensures the response is saved to the correct session even if currentChatSession
     * has been switched to a different session.
     *
     * @param response The AI response to save
     * @param sessionId The specific session ID to save to
     */
    fun saveAiResponseToSession(response: String, sessionId: String) {
        // Skip persistence for CLI_PROMPT mode
        if (mode == ExecutionMode.CLI_PROMPT) {
            return
        }

        // Save directly to the specified session ID
        chatMessageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = response,
            ),
        )
        chatSessionRepository.touchSession(sessionId)

        // Trigger summarization for this specific session if needed
        val messageCount = chatMessageRepository.getMessageCount(sessionId)
        if (messageCount > summarizationThreshold && messageCount % summarizationThreshold == 0) {
            val previousSession = currentChatSession
            try {
                currentChatSession = chatSessionRepository.getSession(sessionId)
                summarizeOlderMessages()
            } finally {
                currentChatSession = previousSession
            }
        }
    }

    /**
     * Prepare the prompt with conversation context
     */
    private fun preparePromptWithContext(userMessage: String): String {
        // Build directive system message if applicable
        val directivePrompt = buildDirectivePrompt()

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
                if (directivePrompt != null) {
                    "$directivePrompt\n\nPrevious conversation context:\n$conversationText\n\nPlease continue the conversation naturally."
                } else {
                    "Previous conversation context:\n$conversationText\n\nPlease continue the conversation naturally."
                }
            } else {
                // If only one message, just use its content
                if (directivePrompt != null) {
                    "$directivePrompt\n\n${contextMessages.lastOrNull()?.content ?: userMessage}"
                } else {
                    contextMessages.lastOrNull()?.content ?: userMessage
                }
            }
        } else {
            if (directivePrompt != null) {
                "$directivePrompt\n\n$userMessage"
            } else {
                userMessage
            }
        }
    }

    /**
     * Build directive prompt combining system directive and session directive.
     * System directive includes language instructions and other global directives.
     * Session directive is user's custom directive for the specific conversation.
     * @return The complete directive prompt text, or null if no directives are set
     */
    private fun buildDirectivePrompt(): String? {
        val parts = mutableListOf<String>()

        // 1. Add system directive (language instruction, global settings, etc.)
        systemDirective?.let { sysDir ->
            if (sysDir.isNotBlank()) {
                parts.add(sysDir.trim())
            }
        }

        // 2. Add session-specific directive (user's custom directive)
        val directiveId = currentChatSession?.directiveId
        if (directiveId != null) {
            try {
                val directiveRepository = ChatDirectiveRepository()
                val directive = directiveRepository.get(directiveId)
                if (directive != null) {
                    val sessionDirectiveText = buildString {
                        appendLine("USER DIRECTIVE: ${directive.name}")
                        appendLine(directive.content.trim())
                    }.trim()
                    parts.add(sessionDirectiveText)
                }
            } catch (e: Exception) {
                log.error("Error loading directive: ${e.message}", e)
            }
        }

        // Return combined directives or null if none exist
        return if (parts.isEmpty()) {
            null
        } else {
            parts.joinToString("\n\n---\n\n")
        }
    }

    private fun triggerSummarizationIfNeeded() {
        if (mode == ExecutionMode.CLI_PROMPT) {
            return
        }

        val sessionId = currentChatSession?.id ?: return
        val messageCount = chatMessageRepository.getMessageCount(sessionId)

        // Summarize every N messages
        if (messageCount > summarizationThreshold && messageCount % summarizationThreshold == 0) {
            log.debug("Summarizing older messages for session $sessionId")
            summarizeOlderMessages()
        }
    }

    private fun summarizeOlderMessages() {
        val sessionId = currentChatSession?.id ?: return

        try {
            // Get messages that haven't been summarized yet
            val existingSummary = conversationSummaryRepository.getConversationSummary(sessionId)
            val lastSummarizedId = existingSummary?.lastSummarizedMessageId ?: ""

            val messagesToSummarize = if (lastSummarizedId.isEmpty()) {
                // First summarization - get older messages, leave recent ones
                // Use database-level filtering to get only active messages
                val allMessages = chatMessageRepository.getActiveMessages(sessionId)
                allMessages.dropLast(maxRecentMessages).takeLast(40)
            } else {
                // Use database-level filtering to guarantee exactly 40 active messages
                chatMessageRepository.getActiveMessagesAfter(sessionId, lastSummarizedId, 40)
            }

            if (messagesToSummarize.size >= 20) { // Only summarize if we have enough content
                val newSummary = createIntelligentSummary(messagesToSummarize)
                val mergedSummary = mergeWithExistingSummary(newSummary, existingSummary)
                conversationSummaryRepository.saveSummary(mergedSummary)
            }
        } catch (e: Exception) {
            log.error("Error while summarizing: ${e.message}", e)
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
            val summaryResponse = getChatClient().sendMessage(summaryPrompt)
            return parseAISummaryResponse(sessionId, summaryResponse, messages.last().id)
        } catch (e: Exception) {
            log.error("Error while generating summary: ${e.message}", e)
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
