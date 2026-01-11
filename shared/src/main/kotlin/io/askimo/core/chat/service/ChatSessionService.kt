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
import io.askimo.core.chat.mapper.ChatMessageMapper.toDTOs
import io.askimo.core.chat.mapper.ChatMessageMapper.toDomain
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.PaginationDirection
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.chat.util.ExtractedUrlContent
import io.askimo.core.chat.util.FileContentExtractor
import io.askimo.core.chat.util.UrlContentExtractor
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.event.internal.SessionCreatedEvent
import io.askimo.core.event.internal.SessionTitleUpdatedEvent
import io.askimo.core.logging.logger
import io.askimo.core.memory.MemoryMessage
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.providers.ChatClient
import io.askimo.core.rag.enrichContentRetrieverWithLucene
import io.askimo.core.rag.getEmbeddingModel
import io.askimo.core.rag.getEmbeddingStore
import io.askimo.core.util.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Data class to hold both ChatClient and its associated memory for a session.
 * This allows us to access and update memory directly when needed.
 */
data class SessionChatContext(
    val chatClient: ChatClient,
    val memory: TokenAwareSummarizingMemory,
)

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
    val project: Project? = null,
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
     * Cache of session contexts (ChatClient + TokenAwareSummarizingMemory).
     * Each session gets its own context with integrated memory.
     * Caffeine provides automatic eviction when memory is low or sessions are inactive.
     */
    private val sessionContextCache: Cache<String, SessionChatContext> = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterAccess(30.minutes.toJavaDuration())
        .removalListener<String, SessionChatContext> { sessionId, context, cause ->
            if (context != null && sessionId != null) {
                log.debug("Evicting session context for session {} (cause: {})", sessionId, cause)
            }
        }
        .build()

    init {
        eventScope.launch {
            EventBus.internalEvents
                .filterIsInstance<ModelChangedEvent>()
                .collect { event ->
                    handleModelChanged(event)
                }
        }
    }

    /**
     * Handle model change event - clear all cached contexts since they use the old model.
     */
    private fun handleModelChanged(event: ModelChangedEvent) {
        log.info("Model changed to ${event.newModel} for provider ${event.provider}, clearing cached contexts")
        sessionContextCache.invalidateAll()
    }

    /**
     * Get or create a chat context (client + memory) for a session.
     * The context is cached and will be reused for subsequent requests.
     *
     * @param sessionId The session ID
     * @return SessionChatContext containing the ChatClient and its associated memory
     */
    private fun getOrCreateContextForSession(sessionId: String): SessionChatContext = sessionContextCache.get(sessionId) { _ ->
        val project = projectRepository.findProjectBySessionId(sessionId)

        // Create self-managing memory that handles its own persistence
        val memory = TokenAwareSummarizingMemory(
            appContext,
            sessionId = sessionId,
            sessionMemoryRepository = sessionMemoryRepository,
            asyncSummarization = true,
            summarizationTimeoutSeconds = AppConfig.chat.summarizationTimeoutSeconds,
        )

        // Create content retriever if project has indexed paths
        val retriever = if (project != null) {
            log.debug("Session $sessionId belongs to project: ${project.id}")
            createRetrieverForProject(appContext.createUtilityClient(), project)
        } else {
            null
        }

        val chatClient = appContext.createStatefulChatSession(sessionId = sessionId, retriever = retriever, memory = memory)

        SessionChatContext(chatClient, memory)
    }

    /**
     * Get or create a ChatClient for a session.
     * This is a convenience method that returns just the client from the context.
     *
     * @param sessionId The session ID
     * @return ChatClient for the session
     */
    fun getOrCreateClientForSession(sessionId: String): ChatClient = getOrCreateContextForSession(sessionId).chatClient

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
    private fun createRetrieverForProject(classifierChatClient: ChatClient, project: Project): ContentRetriever? {
        try {
            val embeddingModel = getEmbeddingModel(appContext)

            val embeddingStore = getEmbeddingStore(project.id, embeddingModel)

            val ragConfig = AppConfig.rag

            val vectorRetriever = enrichContentRetrieverWithLucene(
                classifierChatClient,
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
        sessionContextCache.invalidate(sessionId)

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

    /**
     * Mark messages as outdated after a specific message and update memory.
     * Clears the session memory and reloads it with the most recent 50 active messages.
     *
     * @param sessionId The session ID
     * @param fromMessageId The message ID to start from (exclusive)
     * @return Number of messages marked as outdated
     */
    fun markMessagesAsOutdatedAfter(sessionId: String, fromMessageId: String): Int {
        val count = messageRepository.markMessagesAsOutdatedAfter(sessionId, fromMessageId)

        // Get cached context if session is active
        val context = sessionContextCache.getIfPresent(sessionId)

        if (context != null) {
            // 1. Delete session memory from database
            sessionMemoryRepository.deleteBySessionId(sessionId)

            // 2. Get the most recent 50 active messages (sorted and limited in database)
            val remainingMessages = messageRepository.getRecentActiveMessages(sessionId, limit = 50).drop(1)

            // 3. Convert to MemoryMessage and reload memory
            val memoryMessages = remainingMessages.map { msg ->
                MemoryMessage(
                    content = msg.content,
                    type = when (msg.role) {
                        MessageRole.USER -> MessageRole.USER.value
                        MessageRole.ASSISTANT -> MessageRole.ASSISTANT.value
                        MessageRole.SYSTEM -> MessageRole.SYSTEM.value
                    },
                    createdAt = msg.createdAt,
                )
            }

            context.memory.loadFromFilteredMemory(memoryMessages)

            log.debug(
                "Cleared memory and reloaded {} active messages for session {} after marking {} messages as outdated",
                memoryMessages.size,
                sessionId,
                count,
            )
        }

        return count
    }

    /**
     * Update the content of a message and mark it as edited.
     *
     * @param messageId The ID of the message to update
     * @param newContent The new content for the message
     * @return Number of messages updated (should be 1)
     */
    fun updateMessageContent(messageId: String, newContent: String): Int = messageRepository.updateMessageContent(messageId, newContent)

    /**
     * Resume a chat session by ID.
     *
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(sessionId: String): ResumeSessionResult {
        val paginatedResult = resumeSessionPaginated(sessionId, limit = Int.MAX_VALUE)

        return ResumeSessionResult(
            success = paginatedResult.success,
            sessionId = paginatedResult.sessionId,
            messages = paginatedResult.messages,
            errorMessage = paginatedResult.errorMessage,
        )
    }

    /**
     * Resume a chat session by ID with paginated messages.
     *
     * @param sessionId The ID of the session to resume
     * @param limit The number of messages to load
     * @return ResumeSessionPaginatedResult containing success status, messages, cursor, and any error
     */
    fun resumeSessionPaginated(sessionId: String, limit: Int): ResumeSessionPaginatedResult {
        val existingSession = sessionRepository.getSession(sessionId)

        return if (existingSession != null) {
            // Load messages first for fast UI rendering
            val (messages, cursor) = messageRepository.getMessagesPaginated(
                sessionId = sessionId,
                limit = limit,
                cursor = null,
                direction = PaginationDirection.BACKWARD,
            )

            // Fetch project if session belongs to one
            val project = existingSession.projectId?.let { projectId ->
                projectRepository.getProject(projectId)
            }

            // Pre-create/cache the chat client for this session asynchronously in the background
            // This is optional and doesn't block message rendering - if it fails (e.g., no model configured in tests),
            // we can still show messages. The client will be created on-demand when user sends a message.
            eventScope.launch {
                try {
                    getOrCreateClientForSession(sessionId)
                    log.debug("Pre-created chat client for session $sessionId in background")
                } catch (e: Exception) {
                    log.debug("Could not pre-create chat client for session $sessionId: ${e.message}")
                }
            }

            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
                title = existingSession.title,
                directiveId = existingSession.directiveId,
                project = project,
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
                project = null,
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
     * Determines if URL content should be extracted based on explicit user intent.
     *
     * Following the pattern used by ChatGPT, Claude, and other AI services,
     * we only extract URL content when the user explicitly requests it through:
     * - Action verbs: "summarize", "analyze", "explain", "read"
     * - Questions about URL: "what does", "what's on", "what is"
     * - URL followed by a question or request
     *
     * @param userMessage The user's message
     * @param url The URL to check
     * @return true if URL content should be extracted, false otherwise
     */
    internal fun shouldExtractUrlContent(userMessage: String, url: String): Boolean {
        val message = userMessage.lowercase()
        val urlLower = url.lowercase()

        // Find the position of the URL in the message
        val urlIndex = message.indexOf(urlLower)
        if (urlIndex == -1) return false

        // Get text before and after the URL (within reasonable distance)
        val beforeUrl = message.substring(0, urlIndex).takeLast(100) // Look at last 100 chars before URL
        val afterUrl = message.substring(urlIndex + urlLower.length).take(100) // Look at first 100 chars after URL

        // Patterns to check in text BEFORE the URL (action verb before URL)
        val beforePatterns = listOf(
            "\\b(summarize|summarise)\\s+[^.!?]*$", // Action verb not separated by sentence boundary
            "\\banalyze\\s+[^.!?]*$",
            "\\banalyse\\s+[^.!?]*$",
            "\\bexplain\\s+[^.!?]*$",
            "\\bread\\s+[^.!?]*$",
            "\\breview\\s+[^.!?]*$",
            "\\bexamine\\s+[^.!?]*$",
            "\\bcheck\\s+(what|what's|whats|the\\s+content|this)[^.!?]*$",
            // Questions about URL
            "what\\s+does[^.!?]*$",
            "what\\s+is[^.!?]*$",
            "what'?s?\\s+(on|in|at)[^.!?]*$",
            "what[^.!?]*$", // Generic "what" question before URL
        )

        // Patterns to check in text AFTER the URL (URL followed by request)
        val afterPatterns = listOf(
            "^[^.!?]*\\?", // URL followed by question mark (same sentence)
            "^[^.!?]*(tell|show)\\s+me", // URL followed by "tell me" or "show me"
            "^[^.!?]*(summarize|summarise|analyze|analyse|explain|review)", // URL followed by action
        )

        // Check if any before pattern matches
        val hasBeforeMatch = beforePatterns.any { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(beforeUrl)
        }

        // Check if any after pattern matches
        val hasAfterMatch = afterPatterns.any { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(afterUrl)
        }

        return hasBeforeMatch || hasAfterMatch
    }

    /**
     * Prepares user message with attachments and URL contents, returns combined prompt including any active directive.
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
     * [User Message with Attachments and URL Contents]
     * ```
     *
     * If attachments are present, they will be included inline in the message using file:// format.
     * If URLs are detected in the message with explicit intent, their content will be extracted and appended.
     *
     * @return The complete prompt string ready to send to the AI (directive prepended if present)
     */
    fun prepareContextAndGetPromptForChat(
        sessionId: String,
        userMessage: ChatMessageDTO,
        willSaveUserMessage: Boolean,
    ): String {
        val urls = UrlContentExtractor.extractUrls(userMessage.content)
        val urlContents = urls.mapNotNull { url ->
            // Only extract if user explicitly requested it
            if (!shouldExtractUrlContent(userMessage.content, url)) {
                log.debug("Skipping URL content extraction for: $url (no explicit intent detected)")
                return@mapNotNull null
            }

            log.info("Extracting URL content for: $url (explicit user intent detected)")
            try {
                UrlContentExtractor.extractContent(url)
            } catch (e: Exception) {
                log.warn("Failed to fetch URL content for $url: ${e.message}", e)
                null
            }
        }

        if (willSaveUserMessage) {
            messageRepository.addMessage(
                ChatMessage(
                    id = userMessage.id!!,
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = userMessage.content,
                    attachments = userMessage.attachments.toDomain(sessionId),
                ),
            )
        }

        sessionRepository.touchSession(sessionId)

        // Generate title only if session doesn't have one yet
        val session = sessionRepository.getSession(sessionId)
        if (session?.title.isNullOrBlank()) {
            val generatedTitle = sessionRepository.generateAndUpdateTitle(sessionId, userMessage.content)

            eventScope.launch {
                EventBus.emit(
                    SessionTitleUpdatedEvent(
                        sessionId = sessionId,
                        newTitle = generatedTitle,
                    ),
                )
            }
        }

        return constructMessageWithAttachmentsAndUrls(userMessage, urlContents)
    }

    /**
     * Constructs a formatted message with both file attachments and extracted URL contents.
     *
     * @param userMessage The original user message
     * @param attachments List of file attachments
     * @param urlContents List of extracted URL contents
     * @return Formatted message with attachments and URL contents appended
     */
    private fun constructMessageWithAttachmentsAndUrls(
        userMessage: ChatMessageDTO,
        urlContents: List<ExtractedUrlContent>,
    ): String = buildString {
        // First include attachments if present
        userMessage.attachments.forEach { attachment ->
            appendLine("---")
            appendLine("Attached file: ${attachment.fileName}")
            appendLine("File size: ${formatFileSize(attachment.size)}")
            appendLine()

            val content = when {
                attachment.content != null -> attachment.content
                attachment.filePath != null -> {
                    try {
                        val file = File(attachment.filePath)
                        if (!file.exists()) {
                            log.error("File not found: ${attachment.filePath}")
                            "[Error: File not found]"
                        } else if (!FileContentExtractor.isSupported(file)) {
                            log.warn("Unsupported file type: ${attachment.fileName}")
                            "[${FileContentExtractor.getUnsupportedMessage(file)}]"
                        } else {
                            FileContentExtractor.extractContent(file)
                        }
                    } catch (e: Exception) {
                        log.error("Failed to extract content from ${attachment.fileName}: ${e.message}", e)
                        "[Error: Could not read file - ${e.message}]"
                    }
                }
                else -> {
                    log.error("Attachment has neither content nor filePath: ${attachment.fileName}")
                    "[Error: No content available]"
                }
            }

            appendLine(content)
            appendLine("---")
            appendLine()
        }

        // Add URL contents if present
        if (urlContents.isNotEmpty()) {
            urlContents.forEach { content ->
                appendLine("---")
                appendLine("URL: ${content.url}")
                if (content.title != null) {
                    appendLine("Title: ${content.title}")
                }
                appendLine()
                appendLine(content.content)
                appendLine("---")
                appendLine()
            }
        }

        // Then include user's message/question at the end
        appendLine(userMessage.content)
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
