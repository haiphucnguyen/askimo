/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.ChatSessionsTable
import io.askimo.core.chat.domain.SESSION_TITLE_MAX_LENGTH
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.Pageable
import io.askimo.core.logging.logger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Extension function to map an Exposed ResultRow to a ChatSession object.
 * Eliminates duplication of mapping logic throughout the repository.
 */
private fun ResultRow.toChatSession(): ChatSession = ChatSession(
    id = this[ChatSessionsTable.id],
    title = this[ChatSessionsTable.title],
    createdAt = this[ChatSessionsTable.createdAt],
    updatedAt = this[ChatSessionsTable.updatedAt],
    projectId = this[ChatSessionsTable.projectId],
    directiveId = this[ChatSessionsTable.directiveId],
    isStarred = this[ChatSessionsTable.isStarred] == 1,
    sortOrder = this[ChatSessionsTable.sortOrder],
)

/**
 * Repository for managing chat sessions.
 * This repository focuses solely on the chat_sessions table operations.
 */
class ChatSessionRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {
    private val log = logger<ChatSessionRepository>()

    fun createSession(session: ChatSession): ChatSession {
        val trimmedTitle = generateTitle(session.title)
        val sessionWithInjectedFields = session.copy(
            id = session.id.ifBlank { UUID.randomUUID().toString() },
            title = trimmedTitle,
        )

        transaction(database) {
            ChatSessionsTable.insert {
                it[id] = sessionWithInjectedFields.id
                it[ChatSessionsTable.title] = sessionWithInjectedFields.title
                it[createdAt] = sessionWithInjectedFields.createdAt
                it[updatedAt] = sessionWithInjectedFields.updatedAt
                it[ChatSessionsTable.projectId] = sessionWithInjectedFields.projectId
                it[ChatSessionsTable.directiveId] = sessionWithInjectedFields.directiveId
                it[ChatSessionsTable.isStarred] = if (sessionWithInjectedFields.isStarred) 1 else 0
                it[ChatSessionsTable.sortOrder] = sessionWithInjectedFields.sortOrder
            }
        }

        return sessionWithInjectedFields
    }

    /**
     * Get sessions with a limited number.
     * Sessions are ordered by starred status, sort order, and updated time.
     *
     * @param limit Maximum number of sessions to return
     * @return List of sessions up to the specified limit
     */
    fun getSessions(limit: Int): List<ChatSession> = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .orderBy(
                ChatSessionsTable.isStarred to SortOrder.DESC,
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
            .limit(limit)
            .map { it.toChatSession() }
    }

    /**
     * Get sessions with pagination and optional filtering.
     * Sessions are ordered by starred status, sort order, and updated time.
     *
     * @param page The page number (1-based)
     * @param pageSize Number of sessions per page
     * @param projectFilter Filter by project status:
     *   - null: return all sessions (default)
     *   - true: return only sessions WITH a project
     *   - false: return only sessions WITHOUT a project
     * @return Paginated session results
     */
    fun getSessionsPaged(
        page: Int = 1,
        pageSize: Int = 10,
        projectFilter: Boolean? = null,
    ): Pageable<ChatSession> = transaction(database) {
        // Build base query with optional filter
        val baseQuery = ChatSessionsTable.selectAll().apply {
            when (projectFilter) {
                true -> where { ChatSessionsTable.projectId.isNotNull() }
                false -> where { ChatSessionsTable.projectId.isNull() }
                null -> {} // No filter, get all sessions
            }
        }

        // Get total count
        val totalItems = baseQuery.count().toInt()

        if (totalItems == 0) {
            return@transaction Pageable(
                items = emptyList(),
                currentPage = 1,
                totalPages = 0,
                totalItems = 0,
                pageSize = pageSize,
            )
        }

        val totalPages = (totalItems + pageSize - 1) / pageSize
        val validPage = page.coerceIn(1, totalPages)
        val offset = ((validPage - 1) * pageSize).toLong()

        // Query only the records for the current page
        val pageSessions = ChatSessionsTable.selectAll().apply {
            when (projectFilter) {
                true -> where { ChatSessionsTable.projectId.isNotNull() }
                false -> where { ChatSessionsTable.projectId.isNull() }
                null -> {} // No filter, get all sessions
            }
        }
            .orderBy(
                ChatSessionsTable.isStarred to SortOrder.DESC,
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
            .limit(pageSize)
            .offset(offset)
            .map { it.toChatSession() }

        Pageable(
            items = pageSessions,
            currentPage = validPage,
            totalPages = totalPages,
            totalItems = totalItems,
            pageSize = pageSize,
        )
    }

    /**
     * Get all sessions associated with a specific project.
     * Sessions are ordered by updated time (most recent first).
     *
     * @param projectId The project ID to filter by
     * @return List of sessions belonging to the project
     */
    fun getSessionsByProjectId(projectId: String): List<ChatSession> = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .where { ChatSessionsTable.projectId eq projectId }
            .orderBy(ChatSessionsTable.updatedAt to SortOrder.DESC)
            .map { it.toChatSession() }
    }

    fun getSession(sessionId: String): ChatSession? = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .where { ChatSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.toChatSession()
    }

    /**
     * Update the updatedAt timestamp of a session.
     * This is typically called when a message is added to the session.
     */
    fun touchSession(sessionId: String): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    private fun generateTitle(firstMessage: String): String {
        val cleaned = firstMessage.trim().replace("\n", " ")
        return when {
            cleaned.length <= SESSION_TITLE_MAX_LENGTH -> cleaned
            cleaned.contains(". ") -> {
                val candidate = cleaned.substringBefore(". ") + "."
                if (candidate.length <= SESSION_TITLE_MAX_LENGTH) {
                    candidate
                } else {
                    cleaned.take(SESSION_TITLE_MAX_LENGTH - 3) + "..."
                }
            }
            cleaned.contains("? ") -> {
                val candidate = cleaned.substringBefore("? ") + "?"
                if (candidate.length <= SESSION_TITLE_MAX_LENGTH) {
                    candidate
                } else {
                    cleaned.take(SESSION_TITLE_MAX_LENGTH - 3) + "..."
                }
            }
            cleaned.contains("! ") -> {
                val candidate = cleaned.substringBefore("! ") + "!"
                if (candidate.length <= SESSION_TITLE_MAX_LENGTH) {
                    candidate
                } else {
                    cleaned.take(SESSION_TITLE_MAX_LENGTH - 3) + "..."
                }
            }
            else -> cleaned.take(SESSION_TITLE_MAX_LENGTH - 3) + "..."
        }
    }

    fun generateAndUpdateTitle(sessionId: String, firstMessage: String): String {
        val title = generateTitle(firstMessage)
        transaction(database) {
            ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
                it[ChatSessionsTable.title] = title
            }
        }
        return title
    }

    /**
     * Update the directive for a chat session.
     * @param sessionId The session ID
     * @param directiveId The directive ID to set (null to clear directive)
     * @return true if updated successfully
     */
    fun updateSessionDirective(sessionId: String, directiveId: String?): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[ChatSessionsTable.directiveId] = directiveId
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Delete a chat session.
     * Note: Related data (messages, summaries) should be deleted by the service layer
     * before calling this method to respect the repository pattern.
     */
    fun deleteSession(sessionId: String): Boolean {
        log.debug("Deleting session $sessionId")
        val deleted = transaction(database) {
            ChatSessionsTable.deleteWhere { ChatSessionsTable.id eq sessionId } > 0
        }
        log.debug("Deleted session $sessionId")
        return deleted
    }

    /**
     * Delete all sessions (useful for testing).
     * Deletes all records from the chat_sessions table.
     * @return Number of deleted records
     */
    fun deleteAll(): Int = transaction(database) {
        ChatSessionsTable.deleteAll()
    }

    /**
     * Update the starred status of a session
     */
    fun updateSessionStarred(sessionId: String, isStarred: Boolean): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[ChatSessionsTable.isStarred] = if (isStarred) 1 else 0
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Update the sort order of a session
     */
    fun updateSessionSortOrder(sessionId: String, sortOrder: Int): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[ChatSessionsTable.sortOrder] = sortOrder
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Update the title of a session
     */
    fun updateSessionTitle(sessionId: String, title: String): Boolean {
        val trimmedTitle = title.trim().take(SESSION_TITLE_MAX_LENGTH)
        if (trimmedTitle.isEmpty()) {
            return false
        }

        return transaction(database) {
            ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
                it[ChatSessionsTable.title] = trimmedTitle
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
    }

    /**
     * Get all starred sessions
     */
    fun getStarredSessions(): List<ChatSession> = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .where { ChatSessionsTable.isStarred eq 1 }
            .orderBy(
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
            .map { it.toChatSession() }
    }

    /**
     * Get sessions not belonging to any project (general chat sessions) with a limit.
     * Sessions are ordered by starred status, sort order, and updated time.
     *
     * @param limit Maximum number of sessions to return
     * @return List of sessions up to the specified limit
     */
    fun getSessionsWithoutProject(limit: Int): List<ChatSession> = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .where { ChatSessionsTable.projectId.isNull() }
            .orderBy(
                ChatSessionsTable.isStarred to SortOrder.DESC,
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
            .limit(limit)
            .map { it.toChatSession() }
    }

    /**
     * Update the project of a session.
     */
    fun updateSessionProject(sessionId: String, projectId: String?): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[ChatSessionsTable.projectId] = projectId
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /**
     * Get multiple sessions by their IDs.
     *
     * @param sessionIds List of session IDs to retrieve
     * @return List of sessions matching the IDs
     */
    fun getSessionsByIds(sessionIds: List<String>): List<ChatSession> {
        if (sessionIds.isEmpty()) return emptyList()

        return transaction(database) {
            ChatSessionsTable
                .selectAll()
                .where { ChatSessionsTable.id inList sessionIds }
                .map { it.toChatSession() }
        }
    }
}
