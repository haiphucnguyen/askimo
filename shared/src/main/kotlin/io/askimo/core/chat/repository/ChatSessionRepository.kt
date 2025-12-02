/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.sqliteDatetime
import io.askimo.core.logging.logger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

const val SESSION_TITLE_MAX_LENGTH = 256

/**
 * Exposed table definition for chat_sessions.
 */
object ChatSessionsTable : Table("chat_sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", SESSION_TITLE_MAX_LENGTH)
    val createdAt = sqliteDatetime("created_at")
    val updatedAt = sqliteDatetime("updated_at")
    val directiveId = varchar("directive_id", 36).nullable()
    val folderId = varchar("folder_id", 36).nullable()
    val isStarred = integer("is_starred").default(0)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Extension function to map an Exposed ResultRow to a ChatSession object.
 * Eliminates duplication of mapping logic throughout the repository.
 */
private fun ResultRow.toChatSession(): ChatSession = ChatSession(
    id = this[ChatSessionsTable.id],
    title = this[ChatSessionsTable.title],
    createdAt = this[ChatSessionsTable.createdAt],
    updatedAt = this[ChatSessionsTable.updatedAt],
    directiveId = this[ChatSessionsTable.directiveId],
    folderId = this[ChatSessionsTable.folderId],
    isStarred = this[ChatSessionsTable.isStarred] == 1,
    sortOrder = this[ChatSessionsTable.sortOrder],
)

/**
 * Repository for managing chat sessions.
 * This repository focuses solely on the chat_sessions table operations.
 */
class ChatSessionRepository internal constructor(
    databaseManager: io.askimo.core.db.DatabaseManager = io.askimo.core.db.DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {
    private val log = logger<ChatSessionRepository>()

    fun createSession(session: ChatSession): ChatSession {
        val sessionWithInjectedFields = session.copy(
            id = UUID.randomUUID().toString(),
        )

        transaction(database) {
            ChatSessionsTable.insert {
                it[id] = sessionWithInjectedFields.id
                it[ChatSessionsTable.title] = sessionWithInjectedFields.title
                it[createdAt] = sessionWithInjectedFields.createdAt
                it[updatedAt] = sessionWithInjectedFields.updatedAt
                it[ChatSessionsTable.directiveId] = sessionWithInjectedFields.directiveId
                it[ChatSessionsTable.folderId] = sessionWithInjectedFields.folderId
                it[ChatSessionsTable.isStarred] = if (sessionWithInjectedFields.isStarred) 1 else 0
                it[ChatSessionsTable.sortOrder] = sessionWithInjectedFields.sortOrder
            }
        }

        return sessionWithInjectedFields
    }

    fun getAllSessions(): List<ChatSession> = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .orderBy(
                ChatSessionsTable.isStarred to SortOrder.DESC,
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
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
            cleaned.contains(". ") -> cleaned.substringBefore(". ") + "."
            cleaned.contains("? ") -> cleaned.substringBefore("? ") + "?"
            cleaned.contains("! ") -> cleaned.substringBefore("! ") + "!"
            else -> cleaned.take(SESSION_TITLE_MAX_LENGTH - 3) + "..."
        }
    }

    fun generateAndUpdateTitle(sessionId: String, firstMessage: String) {
        val title = generateTitle(firstMessage)
        transaction(database) {
            ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
                it[ChatSessionsTable.title] = title
            }
        }
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
     * Move a session to a folder
     */
    fun updateSessionFolder(sessionId: String, folderId: String?): Boolean = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.id eq sessionId }) {
            it[ChatSessionsTable.folderId] = folderId
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
     * Get all sessions in a folder
     */
    fun getSessionsByFolder(folderId: String?): List<ChatSession> = transaction(database) {
        val query = ChatSessionsTable.selectAll()

        val filteredQuery = if (folderId == null) {
            query.where { ChatSessionsTable.folderId.isNull() }
        } else {
            query.where { ChatSessionsTable.folderId eq folderId }
        }

        filteredQuery
            .orderBy(
                ChatSessionsTable.isStarred to SortOrder.DESC,
                ChatSessionsTable.sortOrder to SortOrder.ASC,
                ChatSessionsTable.updatedAt to SortOrder.DESC,
            )
            .map { it.toChatSession() }
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
     * Move all sessions in a folder to root (null folder_id).
     * This is typically called by the service layer when deleting a folder.
     */
    fun moveSessionsToRoot(folderId: String): Int = transaction(database) {
        ChatSessionsTable.update({ ChatSessionsTable.folderId eq folderId }) {
            it[ChatSessionsTable.folderId] = null
        }
    }
}
