/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.Project
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.mapper.ChatMessageMapper.toDTOs
import io.askimo.core.chat.mapper.ChatMessageMapper.toDomain
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.PaginationDirection
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.chat.util.constructMessageWithAttachments
import io.askimo.core.context.AppContext
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.event.internal.SessionCreatedEvent
import io.askimo.core.logging.logger
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.rag.jvector.JVectorIndexer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

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
 * @param directiveRepository The chat directive repository
 * @param sessionMemoryRepository The session memory repository
 * @param appContext The application context
 */
class ChatSessionService(
    private val sessionRepository: ChatSessionRepository = DatabaseManager.getInstance().getChatSessionRepository(),
    private val messageRepository: ChatMessageRepository = DatabaseManager.getInstance().getChatMessageRepository(),
    private val directiveRepository: ChatDirectiveRepository = DatabaseManager.getInstance().getChatDirectiveRepository(),
    private val sessionMemoryRepository: SessionMemoryRepository = DatabaseManager.getInstance().getSessionMemoryRepository(),
    private val projectRepository: ProjectRepository = DatabaseManager.getInstance().getProjectRepository(),
    private val appContext: AppContext,
) {
    private val log = logger<ChatSessionService>()

    // Coroutine scope for event subscriptions
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Cache of session-specific ChatClient instances.
     * Each session gets its own client with integrated memory.
     */
    private val clientCache = ConcurrentHashMap<String, ChatClient>()

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
        clientCache.clear()
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
    public fun getOrCreateClientForSession(sessionId: String): ChatClient = clientCache.getOrPut(sessionId) {
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
            maxTokens = 4000,
            summarizationThreshold = 0.75,
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

        appContext.createStatefulChatSession(retriever = retriever, memory = memory)
    }

    /**
     * Create a content retriever for a project if it has indexed paths.
     *
     * @param project The project to create a retriever for
     * @return Content retriever if project has indexed paths, null otherwise
     */
    private fun createRetrieverForProject(project: Project): ContentRetriever? {
        try {
            val indexedPaths = parseIndexedPaths(project.indexedPaths)

            if (indexedPaths.isEmpty()) {
                log.debug("Project ${project.id} has no indexed paths, RAG disabled")
                return null
            }

            log.debug("Creating RAG retriever for project ${project.id} with ${indexedPaths.size} indexed paths")

            // Get cached indexer instance for this project (singleton per project)
            val indexer = JVectorIndexer.getInstance(
                projectId = project.id,
                appContext = appContext,
            )

            if (!indexer.ensureIndexed(indexedPaths, watchForChanges = true)) {
                log.error("Failed to ensure index for project ${project.id}")
                return null
            }

            return EmbeddingStoreContentRetriever
                .builder()
                .embeddingStore(indexer.embeddingStore)
                .embeddingModel(indexer.embeddingModel)
                .maxResults(5)
                .minScore(0.7)
                .build()
        } catch (e: Exception) {
            log.error("Failed to create content retriever for project ${project.id}", e)
            return null
        }
    }

    /**
     * Parse indexed paths from JSON array string.
     *
     * @param indexedPathsJson JSON array string of paths, e.g., "['/path1', '/path2']"
     * @return List of Path objects
     */
    private fun parseIndexedPaths(indexedPathsJson: String): List<Path> = try {
        val json = Json { ignoreUnknownKeys = true }
        val paths = json.decodeFromString<List<String>>(indexedPathsJson)
        paths.map { Paths.get(it) }
    } catch (e: Exception) {
        log.error("Failed to parse indexed paths: $indexedPathsJson", e)
        emptyList()
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
        clientCache[sessionId]?.clearMemory()
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
        // Query only sessions without projects at database level
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
    fun createSession(session: ChatSession): ChatSession {
        val createdSession = sessionRepository.createSession(session)

        getOrCreateClientForSession(createdSession.id)

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
        // Remove from client cache and clean up
        clientCache.remove(sessionId)

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
     * Resume a chat session by ID and return the result with messages.
     *
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(sessionId: String): ResumeSessionResult {
        getOrCreateClientForSession(sessionId)

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
    fun resumeSessionPaginated(sessionId: String, limit: Int): ResumeSessionPaginatedResult {
        val existingSession = sessionRepository.getSession(sessionId)

        return if (existingSession != null) {
            // Get or create client for this session
            getOrCreateClientForSession(sessionId)

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

        // Generate title from first user message
        val messages = messageRepository.getMessages(sessionId)
        if (messages.count { it.role == MessageRole.USER } == 1) {
            val titlePrompt = if (attachments.isNotEmpty()) {
                val fileNames = attachments.joinToString(", ") { it.fileName }
                "$userMessage [Attached: $fileNames]"
            } else {
                userMessage
            }
            sessionRepository.generateAndUpdateTitle(sessionId, titlePrompt)
        }

        val directivePrompt = buildDirectivePrompt(sessionId)

        val messageWithAttachments = constructMessageWithAttachments(userMessage, attachments)

        return if (directivePrompt != null) {
            "$directivePrompt\n\n$messageWithAttachments"
        } else {
            messageWithAttachments
        }
    }

    private fun buildDirectivePrompt(sessionId: String): String? {
        val parts = mutableListOf<String>()

        appContext.systemDirective?.let { sysDir ->
            if (sysDir.isNotBlank()) {
                parts.add(sysDir.trim())
            }
        }

        val directive = directiveRepository.findDirectiveBySessionId(sessionId)
        if (directive != null) {
            val sessionDirectiveText = buildString {
                appendLine("USER DIRECTIVE: ${directive.name}")
                appendLine(directive.content.trim())
            }.trim()
            parts.add(sessionDirectiveText)
        }

        return if (parts.isEmpty()) {
            null
        } else {
            parts.joinToString("\n\n---\n\n")
        }
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
