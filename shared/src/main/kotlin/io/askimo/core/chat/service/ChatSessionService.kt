/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import io.askimo.core.chat.domain.ChatFolder
import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.ConversationSummary
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.mapper.ChatMessageMapper.toDTOs
import io.askimo.core.chat.mapper.ChatMessageMapper.toDomain
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatFolderRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ConversationSummaryRepository
import io.askimo.core.chat.repository.PaginationDirection
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime

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
 * @param conversationSummaryRepository The conversation summary repository
 * @param folderRepository The chat folder repository
 */
class ChatSessionService(
    private val sessionRepository: ChatSessionRepository = DatabaseManager.getInstance().getChatSessionRepository(),
    private val messageRepository: ChatMessageRepository = DatabaseManager.getInstance().getChatMessageRepository(),
    private val conversationSummaryRepository: ConversationSummaryRepository = DatabaseManager.getInstance().getConversationSummaryRepository(),
    private val folderRepository: ChatFolderRepository = DatabaseManager.getInstance().getChatFolderRepository(),
    private val directiveRepository: ChatDirectiveRepository = DatabaseManager.getInstance().getChatDirectiveRepository(),
    private val appContext: AppContext,
) {
    private val log = logger<ChatSessionService>()

    /**
     * Get all sessions sorted by most recently updated first.
     */
    fun getAllSessionsSorted(): List<ChatSession> = sessionRepository.getAllSessions().sortedByDescending { it.createdAt }

    /**
     * Get a paginated list of sessions.
     *
     * @param page The page number (1-based)
     * @param pageSize The number of sessions per page
     * @return PagedSessions containing the sessions for the requested page and pagination info
     */
    fun getSessionsPaged(page: Int, pageSize: Int): PagedSessions {
        val allSessions = getAllSessionsSorted()

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
    fun createSession(session: ChatSession): ChatSession = sessionRepository.createSession(session)

    /**
     * Delete a session and all its related data (messages and summaries).
     * This method coordinates the deletion across multiple repositories.
     *
     * @param sessionId The ID of the session to delete
     * @return true if the session was deleted, false if it didn't exist
     */
    fun deleteSession(sessionId: String): Boolean {
        // Delete related data first
        conversationSummaryRepository.deleteSummaryBySession(sessionId)
        messageRepository.deleteMessagesBySession(sessionId)

        // Then delete the session itself
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

    fun saveAiResponse(sessionId: String, response: String) {
        addMessage(
            ChatMessage(
                id = "",
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = response,
            ),
        )

        val messageCount = messageRepository.getMessageCount(sessionId)
        if (messageCount > AppConfig.chat.summarizationThreshold && messageCount % AppConfig.chat.summarizationThreshold == 0) {
            summarizeOlderMessages(sessionId)
        }
    }

    private fun summarizeOlderMessages(sessionId: String) {
        try {
            // Get messages that haven't been summarized yet
            val existingSummary = conversationSummaryRepository.getConversationSummary(sessionId)
            log.debug("Existing summary: {}", existingSummary)
            val lastSummarizedId = existingSummary?.lastSummarizedMessageId ?: ""

            val messagesToSummarize = if (lastSummarizedId.isEmpty()) {
                // First summarization - get older messages, leave recent ones
                // Use database-level filtering to get only active messages
                val allMessages = messageRepository.getActiveMessages(sessionId)
                allMessages.dropLast(AppConfig.chat.maxRecentMessages).takeLast(40)
            } else {
                // Use database-level filtering to guarantee exactly 40 active messages
                messageRepository.getActiveMessagesAfter(sessionId, lastSummarizedId, 40)
            }

            if (messagesToSummarize.size >= 20) {
                val newSummary = createIntelligentSummary(sessionId, messagesToSummarize)
                val mergedSummary = mergeWithExistingSummary(newSummary, existingSummary)
                conversationSummaryRepository.saveSummary(mergedSummary)
            }
        } catch (e: Exception) {
            log.error("Error while summarizing: ${e.message}", e)
        }
    }

    private fun createIntelligentSummary(sessionId: String, messages: List<ChatMessage>): ConversationSummary {
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
            val summaryResponse = appContext.getChatClient().sendMessage(summaryPrompt)
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
            log.error("Error parsing AI summary response: ${e.message}", e)
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

    /**
     * Get all messages for a session.
     *
     * @param sessionId The session ID
     * @return List of messages in chronological order
     */
    fun getMessages(sessionId: String): List<ChatMessageDTO> = messageRepository.getMessages(sessionId).toDTOs()

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
     * Save a conversation summary.
     *
     * @param summary The summary to save
     */
    fun saveSummary(summary: ConversationSummary) = conversationSummaryRepository.saveSummary(summary)

    /**
     * Get conversation summary for a session.
     *
     * @param sessionId The session ID
     * @return The conversation summary, or null if not found
     */
    fun getConversationSummary(sessionId: String): ConversationSummary? = conversationSummaryRepository.getConversationSummary(sessionId)

    /**
     * TODO: rename this method
     * Resume a chat session by ID and return the result with messages.
     *
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(sessionId: String): ResumeSessionResult {
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
            val (messages, cursor) = messageRepository.getMessagesPaginated(
                sessionId = sessionId,
                limit = limit,
                cursor = null,
                direction = PaginationDirection.BACKWARD,
            )
            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
                directiveId = existingSession.directiveId,
                messages = messages.toDTOs(),
                cursor = cursor,
                hasMore = cursor != null,
            )
        } else {
            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
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
     * Create a new folder.
     */
    fun createFolder(
        name: String,
        parentFolderId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int = 0,
    ): ChatFolder = folderRepository.createFolder(name, parentFolderId, color, icon, sortOrder)

    /**
     * Delete a folder and move its contents to root.
     * This method coordinates the operation across multiple repositories.
     */
    fun deleteFolder(folderId: String): Boolean {
        // Move sessions to root
        sessionRepository.moveSessionsToRoot(folderId)

        // Move child folders to root
        folderRepository.moveChildFoldersToRoot(folderId)

        // Delete the folder
        return folderRepository.deleteFolder(folderId)
    }

    /**
     * Get all sessions in a folder.
     */
    fun getSessionsByFolder(folderId: String?): List<ChatSession> = sessionRepository.getSessionsByFolder(folderId)

    /**
     * Get all starred sessions.
     */
    fun getStarredSessions(): List<ChatSession> = sessionRepository.getStarredSessions()

    fun prepareContextAndGetPromptForChat(
        userMessage: String,
        sessionId: String,
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

        return preparePromptWithContext(sessionId, userMessage)
    }

    private fun preparePromptWithContext(sessionId: String, userMessage: String): String {
        // Build directive system message if applicable
        val directivePrompt = buildDirectivePrompt(sessionId)

        // Get smart context (summary + recent messages)
        val contextMessages = getContextForSession(sessionId)

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

    private fun getContextForSession(sessionId: String): List<ChatMessage> {
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
        val recentMessages = messageRepository.getRecentActiveMessages(sessionId, AppConfig.chat.maxRecentMessages)
        contextMessages.addAll(recentMessages)

        return trimContextByTokens(contextMessages, AppConfig.chat.maxTokensForContext)
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
