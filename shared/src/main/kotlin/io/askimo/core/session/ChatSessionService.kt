/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import java.time.LocalDateTime

/**
 * Result of resuming a chat session.
 */
data class ResumeSessionResult(
    val success: Boolean,
    val sessionId: String,
    val messages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Result of resuming a chat session with pagination.
 */
data class ResumeSessionPaginatedResult(
    val success: Boolean,
    val sessionId: String,
    val messages: List<ChatMessage> = emptyList(),
    val cursor: LocalDateTime? = null,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Service for managing chat sessions with common logic shared between CLI and desktop.
 *
 * This service provides operations for listing, sorting, and paginating chat sessions
 * in a platform-agnostic way.
 */
class ChatSessionService(
    private val repository: ChatSessionRepository = ChatSessionRepository(),
) {
    /**
     * Get all sessions sorted by most recently updated first.
     */
    fun getAllSessionsSorted(): List<ChatSession> = repository.getAllSessions().sortedByDescending { it.updatedAt }

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
    fun getSessionById(sessionId: String): ChatSession? = repository.getSession(sessionId)

    /**
     * Delete a session and all its related data.
     *
     * @param sessionId The ID of the session to delete
     * @return true if the session was deleted, false if it didn't exist
     */
    fun deleteSession(sessionId: String): Boolean = repository.deleteSession(sessionId)

    /**
     * Update the starred status of a session.
     *
     * @param sessionId The ID of the session to update
     * @param isStarred true to star the session, false to unstar
     * @return true if the session was updated, false if it didn't exist
     */
    fun updateSessionStarred(sessionId: String, isStarred: Boolean): Boolean = repository.updateSessionStarred(sessionId, isStarred)

    /**
     * Resume a chat session by ID and return the result with messages.
     *
     * @param session The current Session instance to resume into
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(session: Session, sessionId: String): ResumeSessionResult {
        val success = session.resumeChatSession(sessionId)

        return if (success) {
            val messages = repository.getMessages(sessionId)
            ResumeSessionResult(
                success = true,
                sessionId = sessionId,
                messages = messages,
            )
        } else {
            ResumeSessionResult(
                success = false,
                sessionId = sessionId,
                errorMessage = "Session not found: $sessionId",
            )
        }
    }

    /**
     * Resume a chat session by ID with paginated messages.
     *
     * @param session The current Session instance to resume into
     * @param sessionId The ID of the session to resume
     * @param limit The number of messages to load
     * @return ResumeSessionPaginatedResult containing success status, messages, cursor, and any error
     */
    fun resumeSessionPaginated(session: Session, sessionId: String, limit: Int): ResumeSessionPaginatedResult {
        val success = session.resumeChatSession(sessionId)

        return if (success) {
            // Get the most recent messages in backward direction (newest first)
            val (messages, cursor) = repository.getMessagesPaginated(
                sessionId = sessionId,
                limit = limit,
                cursor = null,
                direction = "backward",
            )
            ResumeSessionPaginatedResult(
                success = true,
                sessionId = sessionId,
                messages = messages,
                cursor = cursor,
                hasMore = cursor != null,
            )
        } else {
            ResumeSessionPaginatedResult(
                success = false,
                sessionId = sessionId,
                messages = emptyList(),
                cursor = null,
                hasMore = false,
                errorMessage = "Session not found: $sessionId",
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
    fun loadPreviousMessages(sessionId: String, cursor: LocalDateTime, limit: Int): Pair<List<ChatMessage>, LocalDateTime?> = repository.getMessagesPaginated(
        sessionId = sessionId,
        limit = limit,
        cursor = cursor,
        direction = "backward",
    )

    /**
     * Search messages in a session by content.
     *
     * @param sessionId The ID of the session to search in
     * @param searchQuery The search query (case-insensitive)
     * @param limit Maximum number of results to return
     * @return List of messages matching the search query
     */
    fun searchMessages(sessionId: String, searchQuery: String, limit: Int = 100): List<ChatMessage> = repository.searchMessages(sessionId, searchQuery, limit)

    /**
     * Close the repository connection.
     */
    fun close() {
        repository.close()
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
