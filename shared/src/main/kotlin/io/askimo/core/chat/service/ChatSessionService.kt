/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.Project
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.mapper.ChatMessageMapper.toDTOs
import io.askimo.core.chat.mapper.ChatMessageMapper.toDomain
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.PaginationDirection
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.chat.util.constructMessageWithAttachments
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.event.internal.SessionCreatedEvent
import io.askimo.core.event.internal.SessionTitleUpdatedEvent
import io.askimo.core.logging.logger
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.rag.enrichContentRetrieverWithLucene
import io.askimo.core.rag.getEmbeddingModel
import io.askimo.core.rag.getEmbeddingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Result of resuming a chat session.
 */
data class ResumeSessionResult(
    val success: Boolean,
    val sessionId: String,
    val messages: List<ChatMessageDTO> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Result of resuming a chat session with pagination.
 */
data class ResumeSessionPaginatedResult(
    val success: Boolean,
    val sessionId: String,
    val title: String? = null,
    val directiveId: String?,
    val messages: List<ChatMessageDTO> = emptyList(),
    val cursor: LocalDateTime? = null,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Service for managing chat sessions with common logic shared between CLI and desktop.
 *
 * This service coordinates between multiple repositories to provide high-level
 * operations for chat session management, following the proper layered architecture.
 *
 * @param sessionRepository The chat session repository
 * @param messageRepository The chat message repository
 * @param sessionMemoryRepository The session memory repository
 * @param appContext The application context
 */
class ChatSessionService(
    private val sessionRepository: ChatSessionRepository = DatabaseManager.getInstance().getChatSessionRepository(),
    private val messageRepository: ChatMessageRepository = DatabaseManager.getInstance().getChatMessageRepository(),
    private val sessionMemoryRepository: SessionMemoryRepository = DatabaseManager.getInstance().getSessionMemoryRepository(),
    private val projectRepository: ProjectRepository = DatabaseManager.getInstance().getProjectRepository(),
    private val appContext: AppContext,
) {
    private val log = logger<ChatSessionService>()

    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Cache of session-specific ChatClient instances.
     * Each session gets its own client with integrated memory.
     */
    private val clientCache: Cache<String, ChatClient> = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterAccess(30.minutes.toJavaDuration())
        .removalListener<String, ChatClient> { sessionId, client, cause ->
            if (client != null && sessionId != null) {
                log.debug("Evicting ChatClient for session {} (cause: {})", sessionId, cause)
                sessionMemoryRepository.deleteBySessionId(sessionId)
            }
        }
        .build()

    init {
        subscribeToInternalEvents()
    }

    /**
     * Subscribe to internal events for component-to-component communication.
     */
    private fun subscribeToInternalEvents() {
        eventScope.launch {
            EventBus.internalEvents
                .filterIsInstance<ModelChangedEvent>()
                .collect { event ->
                    handleModelChanged(event)
                }
        }
    }

    /**
     * Handle model change event - clear all cached clients since they use the old model.
     */
    private fun handleModelChanged(event: ModelChangedEvent) {
        log.info("Model changed to ${event.newModel} for provider ${event.provider}, clearing cached clients")
        clientCache.invalidateAll()
    }

    /**
     * Get or create a ChatClient for a specific session.
     * - Creates a self-managing memory that handles its own persistence
     * - Integrates memory directly into LangChain4j AI service
     * - For sessions with a project: Creates a RAG-enabled client if project has indexed paths
     * - Memory automatically loads from database on creation
     * - Memory automatically saves to database when messages are added/summarized
     * - Caches the client for reuse
     */
    fun getOrCreateClientForSession(executionMode: ExecutionMode, sessionId: String): ChatClient = clientCache.get(sessionId) { _ ->
        val project = projectRepository.findProjectBySessionId(sessionId)

        // Get current provider settings to check if AI summarization is enabled
        val provider = appContext.getActiveProvider()
        val factory = appContext.getModelFactory(provider)
        val settings = appContext.getCurrentProviderSettings()

        // Get summarizer from factory if available
        @Suppress("UNCHECKED_CAST")
        val summarizer = (factory as? ChatModelFactory<ProviderSettings>)
            ?.createSummarizer(settings)

        // Create self-managing memory that handles its own persistence
        val memory = TokenAwareSummarizingMemory(
            sessionId = sessionId,
            sessionMemoryRepository = sessionMemoryRepository,
            summarizer = summarizer,
            asyncSummarization = true,
        )

        // Create content retriever if project has indexed paths
        val retriever = if (project != null) {
            log.debug("Session $sessionId belongs to project: ${project.id}")
            createRetrieverForProject(project)
        } else {
            null
        }

        appContext.createStatefulChatSession(sessionId = sessionId, executionMode = executionMode, retriever = retriever, memory = memory)
    }

    /**
     * Create a content retriever for a project if it has indexed paths.
     * Uses hybrid search combining:
     * - JVector for semantic similarity (vector embeddings)
     * - Lucene for keyword matching (BM25)
     * - Reciprocal Rank Fusion to merge results
     *
     * @param project The project to create a retriever for
     * @return Content retriever if project has indexed paths, null otherwise
     */
    private fun createRetrieverForProject(project: Project): ContentRetriever? {
        try {
            val embeddingModel = getEmbeddingModel(appContext)

            val embeddingStore = getEmbeddingStore(project.id, embeddingModel)

            val ragConfig = AppConfig.rag

            val vectorRetriever = enrichContentRetrieverWithLucene(
                project.id,
                EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(ragConfig.vectorSearchMaxResults)
                    .minScore(ragConfig.vectorSearchMinScore)
                    .build(),
            )

            EventBus.post(
                ProjectIndexingRequestedEvent(
                    projectId = project.id,
                    embeddingStore = embeddingStore,
                    embeddingModel = embeddingModel,
                    watchForChanges = true,
                ),
            )

            log.info("✓ RAG retriever created, indexing started in background for project ${project.id}")
            return vectorRetriever
        } catch (e: Exception) {
            log.error("Failed to create content retriever for project ${project.id}", e)
            return null
        }
    }

    /**
     * Get all sessions sorted by most recently updated first.
     */
    fun getAllSessionsSorted(): List<ChatSession> = sessionRepository.getAllSessions().sortedByDescending { it.createdAt }

    /**
     * Clear memory for a specific session.
     * This removes all conversation history from memory but does not delete the session or messages from the database.
     *
     * @param sessionId The ID of the session to clear memory for
     */
    fun clearSessionMemory(sessionId: String) {
        clientCache.getIfPresent(sessionId)?.clearMemory()
        log.debug("Cleared memory for session: $sessionId")
    }

    /**
     * Get all sessions without a project, sorted by star status and updated time.
     * This is used for the sidebar sessions list.
     */
    fun getSessionsWithoutProject(): List<ChatSession> = sessionRepository.getSessionsWithoutProject()

    /**
     * Get sessions with pagination support.
     * Only returns sessions without a project (projectId is null).
     * Sessions with projects are accessed through ProjectView.
     *
     * @param page The page number (1-indexed)
     * @param pageSize The number of sessions per page
     * @return PagedSessions containing the sessions for the requested page and pagination info
     */
    fun getSessionsPaged(page: Int, pageSize: Int): PagedSessions {
        val allSessions = sessionRepository.getSessionsWithoutProject()

        if (allSessions.isEmpty()) {
            return PagedSessions(
                sessions = emptyList(),
                currentPage = 1,
                totalPages = 0,
                totalSessions = 0,
                pageSize = pageSize,
            )
        }

        val totalPages = (allSessions.size + pageSize - 1) / pageSize
        val validPage = page.coerceIn(1, totalPages)

        val startIndex = (validPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, allSessions.size)
        val pageSessions = allSessions.subList(startIndex, endIndex)

        return PagedSessions(
            sessions = pageSessions,
            currentPage = validPage,
            totalPages = totalPages,
            totalSessions = allSessions.size,
            pageSize = pageSize,
        )
    }

    /**
     * Get a session by ID.
     */
    fun getSessionById(sessionId: String): ChatSession? = sessionRepository.getSession(sessionId)

    /**
     * Create a new session.
     *
     * @param session The session to create
     * @return The created session with generated ID (if not provided)
     */
    fun createSession(executionMode: ExecutionMode, session: ChatSession): ChatSession {
        val createdSession = sessionRepository.createSession(session)

        getOrCreateClientForSession(executionMode, createdSession.id)

        eventScope.launch {
            EventBus.emit(
                SessionCreatedEvent(
                    sessionId = createdSession.id,
                    projectId = createdSession.projectId,
                ),
            )
        }

        return createdSession
    }

    /**
     * Delete a session and all its related data (messages and summaries).
     * This method coordinates the deletion across multiple repositories.
     *
     * @param sessionId The ID of the session to delete
     * @return true if the session was deleted, false if it didn't exist
     */
    fun deleteSession(sessionId: String): Boolean {
        clientCache.invalidate(sessionId)

        messageRepository.deleteMessagesBySession(sessionId)
        sessionMemoryRepository.deleteBySessionId(sessionId)

        return sessionRepository.deleteSession(sessionId)
    }

    /**
     * Update the starred status of a session.
     *
     * @param sessionId The ID of the session to update
     * @param isStarred true to star the session, false to unstar
     * @return true if the session was updated, false if it didn't exist
     */
    fun updateSessionStarred(sessionId: String, isStarred: Boolean): Boolean = sessionRepository.updateSessionStarred(sessionId, isStarred)

    /**
     * Rename the title of a chat session.
     *
     * @param sessionId The ID of the session to rename
     * @param newTitle The new title for the session
     * @return true if the session was renamed, false if it didn't exist or the title is invalid
     */
    fun renameTitle(sessionId: String, newTitle: String): Boolean = sessionRepository.updateSessionTitle(sessionId, newTitle)

    /**
     * Update the directive for a chat session.
     *
     * @param sessionId The ID of the session to update
     * @param directiveId The directive ID to set (null to clear directive)
     * @return true if the session was updated, false if it didn't exist
     */
    fun updateSessionDirective(sessionId: String, directiveId: String?): Boolean = sessionRepository.updateSessionDirective(sessionId, directiveId)

    /**
     * Add a message to a session and update the session's timestamp.
     *
     * @param message The message to add
     * @return The created message with generated ID
     */
    fun addMessage(message: ChatMessage): ChatMessage {
        val createdMessage = messageRepository.addMessage(message)
        sessionRepository.touchSession(message.sessionId)
        return createdMessage
    }

    fun saveAiResponse(sessionId: String, response: String, isFailed: Boolean = false): ChatMessage {
        val message = addMessage(
            ChatMessage(
                id = "",
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = response,
                isFailed = isFailed,
            ),
        )

        return message
    }

    /**
     * Get all messages for a session.
     *
     * @param sessionId The session ID
     * @return List of messages in chronological order
     */
    fun getMessages(sessionId: String): List<ChatMessageDTO> = messageRepository.getMessages(sessionId).toDTOs()

    fun deleteMessage(messageId: String) {
        messageRepository.deleteMessage(messageId)
    }

    /**
     * Get recent active (non-outdated) messages for a session.
     *
     * @param sessionId The session ID
     * @param limit Number of active messages to retrieve
     * @return List of recent active messages in chronological order
     */
    fun getRecentActiveMessages(sessionId: String, limit: Int = 20): List<ChatMessage> = messageRepository.getRecentActiveMessages(sessionId, limit)

    /**
     * Mark a message as outdated.
     *
     * @param messageId The message ID
     * @return Number of messages marked (should be 1)
     */
    fun markMessageAsOutdated(messageId: String): Int = messageRepository.markMessageAsOutdated(messageId)

    /**
     * Mark messages as outdated after a specific message.
     *
     * @param sessionId The session ID
     * @param fromMessageId The message ID to start from (exclusive)
     * @return Number of messages marked as outdated
     */
    fun markMessagesAsOutdatedAfter(sessionId: String, fromMessageId: String): Int = messageRepository.markMessagesAsOutdatedAfter(sessionId, fromMessageId)

    /**
     * Update the content of a message and mark it as edited.
     *
     * @param messageId The ID of the message to update
     * @param newContent The new content for the message
     * @return Number of messages updated (should be 1)
     */
    fun updateMessageContent(messageId: String, newContent: String): Int = messageRepository.updateMessageContent(messageId, newContent)

    /**
     * Get only active (non-outdated) messages for a session.
     *
     * @param sessionId The session ID
     * @return List of active messages in chronological order
     */
    fun getActiveMessages(sessionId: String): List<ChatMessage> = messageRepository.getActiveMessages(sessionId)

    /**
     * Resume a chat session by ID.
     *
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(executionMode: ExecutionMode, sessionId: String): ResumeSessionResult {
        // Try to pre-create/cache the chat client for this session
        // This is optional - if it fails (e.g., no model configured in tests), we can still load messages
        try {
            getOrCreateClientForSession(executionMode, sessionId)
        } catch (e: Exception) {
            log.debug("Could not pre-create chat client for session $sessionId: ${e.message}")
        }

        val messages = messageRepository.getMessages(sessionId)
        return ResumeSessionResult(
            success = true,
            sessionId = sessionId,
            messages = messages.toDTOs(),
        )
    }

    /**
     * Resume a chat session by ID with paginated messages.
     *
     * @param appContext The current Session instance to resume into
     * @param sessionId The ID of the session to resume
     * @param limit The number of messages to load
     * @return ResumeSessionPaginatedResult containing success status, messages, cursor, and any error
     */
    fun resumeSessionPaginated(executionMode: ExecutionMode, sessionId: String, limit: Int): ResumeSessionPaginatedResult {
        val existingSession = sessionRepository.getSession(sessionId)

        return if (existingSession != null) {
            // Try to pre-create/cache the chat client for this session
            // This is optional - if it fails (e.g., no model configured in tests), we can still load messages
            try {
                getOrCreateClientForSession(executionMode, sessionId)
            } catch (e: Exception) {
                log.debug("Could not pre-create chat client for session $sessionId: ${e.message}")
            }

            val (messages, cursor) = messageRepository.getMessagesPaginated(
                sessionId = sessionId,
                limit = limit,
                cursor = null,
                direction = PaginationDirection.BACKWARD,
            )
            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
                title = existingSession.title,
                directiveId = existingSession.directiveId,
                messages = messages.toDTOs(),
                cursor = cursor,
                hasMore = cursor != null,
            )
        } else {
            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
                title = null,
                directiveId = null,
                messages = emptyList(),
                cursor = null,
                hasMore = false,
            )
        }
    }

    /**
     * Load previous messages for a session using pagination.
     *
     * @param sessionId The ID of the session
     * @param cursor The cursor to start from (timestamp of the oldest currently loaded message)
     * @param limit The number of messages to load
     * @return Pair of messages list and next cursor
     */
    fun loadPreviousMessages(sessionId: String, cursor: LocalDateTime, limit: Int): Pair<List<ChatMessageDTO>, LocalDateTime?> {
        val (messages, nextCursor) = messageRepository.getMessagesPaginated(
            sessionId = sessionId,
            limit = limit,
            cursor = cursor,
            direction = PaginationDirection.BACKWARD,
        )
        return Pair(messages.toDTOs(), nextCursor)
    }

    /**
     * Search messages in a session by content.
     *
     * @param sessionId The ID of the session to search in
     * @param searchQuery The search query (case-insensitive)
     * @param limit Maximum number of results to return
     * @return List of messages matching the search query
     */
    fun searchMessages(sessionId: String, searchQuery: String, limit: Int = 100): List<ChatMessageDTO> = messageRepository.searchMessages(sessionId, searchQuery, limit).toDTOs()

    /**
     * Get paginated messages for a session.
     *
     * @param sessionId The session ID
     * @param limit Number of messages to retrieve
     * @param cursor The cursor for pagination
     * @param direction Direction of pagination (FORWARD or BACKWARD)
     * @return Pair of messages list and next cursor
     */
    fun getMessagesPaginated(
        sessionId: String,
        limit: Int = 20,
        cursor: LocalDateTime? = null,
        direction: PaginationDirection = PaginationDirection.FORWARD,
    ): Pair<List<ChatMessageDTO>, LocalDateTime?> {
        val (messages, nextCursor) = messageRepository.getMessagesPaginated(sessionId, limit, cursor, direction)
        return Pair(messages.toDTOs(), nextCursor)
    }

    /**
     * Get all starred sessions.
     */
    fun getStarredSessions(): List<ChatSession> = sessionRepository.getStarredSessions()

    /**
     * Prepares user message with attachments and returns combined prompt including any active directive.
     *
     * The directive (system instructions + session-specific directive) is prepended to the user message
     * to act as system-level instructions. While ideally these would be separate system messages,
     * the current LangChain4j AI Services architecture sets system messages at build time,
     * so we prepend directives to user messages to allow per-session customization.
     *
     * The format is:
     * ```
     * [System Directive]
     *
     * ---
     *
     * [Session-Specific Directive]
     *
     * ---
     *
     * [User Message with Attachments]
     * ```
     *
     * If attachments are present, they will be included inline in the message using file:// format.
     *
     * @return The complete prompt string ready to send to the AI (directive prepended if present)
     */
    fun prepareContextAndGetPromptForChat(
        sessionId: String,
        userMessage: String,
        attachments: List<FileAttachmentDTO> = emptyList(),
    ): String {
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = userMessage,
                attachments = attachments.toDomain(sessionId),
            ),
        )
        sessionRepository.touchSession(sessionId)

        // Generate title only if session doesn't have one yet
        val session = sessionRepository.getSession(sessionId)
        if (session?.title.isNullOrBlank()) {
            val titlePrompt = if (attachments.isNotEmpty()) {
                val fileNames = attachments.joinToString(", ") { it.fileName }
                "$userMessage [Attached: $fileNames]"
            } else {
                userMessage
            }
            val generatedTitle = sessionRepository.generateAndUpdateTitle(sessionId, titlePrompt)

            eventScope.launch {
                EventBus.emit(
                    SessionTitleUpdatedEvent(
                        sessionId = sessionId,
                        newTitle = generatedTitle,
                    ),
                )
            }
        }

        return constructMessageWithAttachments(userMessage, attachments)
    }
}

/**
 * Container for paginated session results.
 */
data class PagedSessions(
    val sessions: List<ChatSession>,
    val currentPage: Int,
    val totalPages: Int,
    val totalSessions: Int,
    val pageSize: Int,
) {
    val hasNextPage: Boolean get() = currentPage < totalPages
    val hasPreviousPage: Boolean get() = currentPage > 1
    val isEmpty: Boolean get() = sessions.isEmpty()
}
