/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.db.AbstractSQLiteRepository
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

const val SESSION_TITLE_MAX_LENGTH = 256

enum class PaginationDirection {
    FORWARD,
    BACKWARD,
}

/**
 * Exposed table definition for chat_folders.
 */
object ChatFoldersTable : Table("chat_folders") {
    val id = varchar("id", 36)
    val name = varchar("name", 256)
    val parentFolderId = varchar("parent_folder_id", 36).nullable()
    val color = varchar("color", 50).nullable()
    val icon = varchar("icon", 50).nullable()
    val sortOrder = integer("sort_order").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for chat_sessions.
 */
object ChatSessionsTable : Table("chat_sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", SESSION_TITLE_MAX_LENGTH)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    val directiveId = varchar("directive_id", 36).nullable()
    val folderId = varchar("folder_id", 36).nullable()
    val isStarred = integer("is_starred").default(0)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for chat_messages.
 */
object ChatMessagesTable : Table("chat_messages") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val role = varchar("role", 50)
    val content = text("content")
    val createdAt = datetime("created_at")
    val isOutdated = integer("is_outdated").default(0)
    val editParentId = varchar("edit_parent_id", 36).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Exposed table definition for conversation_summaries.
 */
object ConversationSummariesTable : Table("conversation_summaries") {
    val sessionId = varchar("session_id", 36)
    val keyFacts = text("key_facts")
    val mainTopics = text("main_topics")
    val recentContext = text("recent_context")
    val lastSummarizedMessageId = varchar("last_summarized_message_id", 36)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(sessionId)
}

class ChatSessionRepository(
    useInMemory: Boolean = false,
) : AbstractSQLiteRepository(useInMemory) {
    override val databaseFileName: String = "chat_sessions.db"

    private val json = Json { ignoreUnknownKeys = true }

    private val database by lazy {
        Database.connect(dataSource)
    }

    override fun initializeDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_folders (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    parent_folder_id TEXT,
                    color TEXT,
                    icon TEXT,
                    sort_order INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (parent_folder_id) REFERENCES chat_folders (id)
                )
            """,
            )

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    directive_id TEXT,
                    folder_id TEXT,
                    is_starred INTEGER DEFAULT 0,
                    sort_order INTEGER DEFAULT 0,
                    FOREIGN KEY (folder_id) REFERENCES chat_folders (id)
                )
            """,
            )

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    is_outdated INTEGER DEFAULT 0,
                    edit_parent_id TEXT,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id),
                    FOREIGN KEY (edit_parent_id) REFERENCES chat_messages (id)
                )
            """,
            )

            // Add migration for existing databases - add new columns if they don't exist
            try {
                stmt.executeUpdate("ALTER TABLE chat_messages ADD COLUMN is_outdated INTEGER DEFAULT 0")
            } catch (e: Exception) {
                // Column already exists, ignore
            }
            try {
                stmt.executeUpdate("ALTER TABLE chat_messages ADD COLUMN edit_parent_id TEXT")
            } catch (e: Exception) {
                // Column already exists, ignore
            }

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversation_summaries (
                    session_id TEXT PRIMARY KEY,
                    key_facts TEXT NOT NULL,
                    main_topics TEXT NOT NULL,
                    recent_context TEXT NOT NULL,
                    last_summarized_message_id TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
                )
            """,
            )
        }
    }

    fun createSession(session: ChatSession): ChatSession {
        // Inject server-controlled field: UUID
        // createdAt and updatedAt will use the values from session (which default to LocalDateTime.now())
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
            .map { row ->
                ChatSession(
                    id = row[ChatSessionsTable.id],
                    title = row[ChatSessionsTable.title],
                    createdAt = row[ChatSessionsTable.createdAt],
                    updatedAt = row[ChatSessionsTable.updatedAt],
                    directiveId = row[ChatSessionsTable.directiveId],
                    folderId = row[ChatSessionsTable.folderId],
                    isStarred = row[ChatSessionsTable.isStarred] == 1,
                    sortOrder = row[ChatSessionsTable.sortOrder],
                )
            }
    }

    fun getSession(sessionId: String): ChatSession? = transaction(database) {
        ChatSessionsTable
            .selectAll()
            .where { ChatSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.let { row ->
                ChatSession(
                    id = row[ChatSessionsTable.id],
                    title = row[ChatSessionsTable.title],
                    createdAt = row[ChatSessionsTable.createdAt],
                    updatedAt = row[ChatSessionsTable.updatedAt],
                    directiveId = row[ChatSessionsTable.directiveId],
                    folderId = row[ChatSessionsTable.folderId],
                    isStarred = row[ChatSessionsTable.isStarred] == 1,
                    sortOrder = row[ChatSessionsTable.sortOrder],
                )
            }
    }
    fun addMessage(message: ChatMessage): ChatMessage {
        // Inject server-controlled field: UUID
        // createdAt will use the value from message (which defaults to LocalDateTime.now())
        val messageWithInjectedFields = message.copy(
            id = UUID.randomUUID().toString(),
        )

        transaction(database) {
            // Insert message
            ChatMessagesTable.insert {
                it[id] = messageWithInjectedFields.id
                it[ChatMessagesTable.sessionId] = messageWithInjectedFields.sessionId
                it[ChatMessagesTable.role] = messageWithInjectedFields.role.value
                it[ChatMessagesTable.content] = messageWithInjectedFields.content
                it[createdAt] = messageWithInjectedFields.createdAt
                it[ChatMessagesTable.isOutdated] = if (messageWithInjectedFields.isOutdated) 1 else 0
                it[ChatMessagesTable.editParentId] = messageWithInjectedFields.editParentId
            }

            // Update session's updated_at
            ChatSessionsTable.update({ ChatSessionsTable.id eq messageWithInjectedFields.sessionId }) {
                it[updatedAt] = LocalDateTime.now()
            }
        }

        return messageWithInjectedFields
    }

    fun getMessages(sessionId: String): List<ChatMessage> = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }
    }

    fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessage> = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }.reversed() // Return in chronological order
    }

    /**
     * Get recent active (non-outdated) messages with filtering at database level.
     * This guarantees we get exactly the requested number of active messages.
     *
     * @param sessionId The session ID
     * @param limit Number of active messages to retrieve (default: 20)
     * @return List of active messages, ordered by creation time (oldest first)
     */
    fun getRecentActiveMessages(sessionId: String, limit: Int = 20): List<ChatMessage> = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where { (ChatMessagesTable.sessionId eq sessionId) and (ChatMessagesTable.isOutdated eq 0) }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }.reversed() // Return in chronological order
    }

    /**
     * Get messages with cursor-based pagination
     * @param sessionId The session ID
     * @param limit Number of messages to retrieve (default: 20)
     * @param cursor The timestamp cursor for pagination. If null, starts from the beginning (oldest messages)
     * @param direction Direction of pagination: FORWARD (newer messages) or BACKWARD (older messages)
     * @return A pair of messages list and the next cursor (null if no more messages)
     */
    fun getMessagesPaginated(
        sessionId: String,
        limit: Int = 20,
        cursor: LocalDateTime? = null,
        direction: PaginationDirection = PaginationDirection.FORWARD,
    ): Pair<List<ChatMessage>, LocalDateTime?> = transaction(database) {
        val query = ChatMessagesTable.selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }

        // Apply cursor filtering and ordering based on direction
        val orderedQuery = when {
            cursor == null && direction == PaginationDirection.FORWARD -> {
                // Start from the beginning (oldest messages)
                query.orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            }
            cursor == null && direction == PaginationDirection.BACKWARD -> {
                // Start from the end (newest messages)
                query.orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            }
            direction == PaginationDirection.FORWARD -> {
                // Get messages after the cursor (newer messages)
                query
                    .andWhere { ChatMessagesTable.createdAt.greater(cursor!!) }
                    .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            }
            else -> {
                // Get messages before the cursor (older messages)
                query
                    .andWhere { ChatMessagesTable.createdAt.less(cursor!!) }
                    .orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            }
        }

        // Fetch one extra to determine if there are more
        val messages = orderedQuery
            .limit(limit + 1)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }

        // Check if there are more messages
        val hasMore = messages.size > limit
        val resultMessages = if (hasMore) messages.take(limit) else messages

        // Reverse if we fetched in backward direction to maintain chronological order
        val orderedMessages = if (direction == PaginationDirection.BACKWARD) resultMessages.reversed() else resultMessages

        // Calculate next cursor
        val nextCursor = if (hasMore && orderedMessages.isNotEmpty()) {
            if (direction == PaginationDirection.FORWARD) {
                orderedMessages.last().createdAt
            } else {
                orderedMessages.first().createdAt
            }
        } else {
            null
        }

        Pair(orderedMessages, nextCursor)
    }

    fun getMessageCount(sessionId: String): Int = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }
            .count()
            .toInt()
    }

    /**
     * Search messages in a session by content.
     *
     * @param sessionId The session ID to search in
     * @param searchQuery The search query (case-insensitive)
     * @param limit Maximum number of results to return
     * @return List of messages matching the search query, ordered by creation time (oldest first)
     */
    fun searchMessages(
        sessionId: String,
        searchQuery: String,
        limit: Int = 100,
    ): List<ChatMessage> {
        if (searchQuery.isBlank()) return emptyList()

        return transaction(database) {
            ChatMessagesTable
                .selectAll()
                .where {
                    (ChatMessagesTable.sessionId eq sessionId) and
                        ChatMessagesTable.content.lowerCase().like("%${searchQuery.lowercase()}%")
                }
                .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    ChatMessage(
                        id = row[ChatMessagesTable.id],
                        sessionId = row[ChatMessagesTable.sessionId],
                        role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                        content = row[ChatMessagesTable.content],
                        createdAt = row[ChatMessagesTable.createdAt],
                        isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                        editParentId = row[ChatMessagesTable.editParentId],
                    )
                }
        }
    }

    fun getMessagesAfter(sessionId: String, afterMessageId: String, limit: Int): List<ChatMessage> = transaction(database) {
        // First get the timestamp of the after message
        val afterTimestamp = ChatMessagesTable
            .select(ChatMessagesTable.createdAt)
            .where { ChatMessagesTable.id eq afterMessageId }
            .singleOrNull()
            ?.get(ChatMessagesTable.createdAt)
            ?: return@transaction emptyList()

        // Then get messages after that timestamp
        ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    ChatMessagesTable.createdAt.greater(afterTimestamp)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }
    }

    /**
     * Get active (non-outdated) messages after a specific message with filtering at database level.
     * This guarantees we get exactly the requested number of active messages.
     *
     * @param sessionId The session ID
     * @param afterMessageId The message ID to start from (exclusive)
     * @param limit Number of active messages to retrieve
     * @return List of active messages after the specified message
     */
    fun getActiveMessagesAfter(sessionId: String, afterMessageId: String, limit: Int): List<ChatMessage> = transaction(database) {
        // First get the timestamp of the after message
        val afterTimestamp = ChatMessagesTable
            .select(ChatMessagesTable.createdAt)
            .where { ChatMessagesTable.id eq afterMessageId }
            .singleOrNull()
            ?.get(ChatMessagesTable.createdAt)
            ?: return@transaction emptyList()

        // Then get active messages after that timestamp
        ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    (ChatMessagesTable.isOutdated eq 0) and
                    ChatMessagesTable.createdAt.greater(afterTimestamp)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }
    }

    /**
     * Mark a single message as outdated.
     * This is used when editing a message to mark the original message as outdated.
     *
     * @param messageId The message ID to mark as outdated
     * @return Number of messages marked (should be 1)
     */
    fun markMessageAsOutdated(messageId: String): Int = transaction(database) {
        ChatMessagesTable.update({ ChatMessagesTable.id eq messageId }) {
            it[isOutdated] = 1
        }
    }

    /**
     * Mark messages as outdated starting from a specific message (exclusive).
     * This is used when editing a message to mark all subsequent messages as outdated.
     *
     * @param sessionId The session ID
     * @param fromMessageId The message ID from which to start marking as outdated (this message itself is not marked)
     * @return Number of messages marked as outdated
     */
    fun markMessagesAsOutdatedAfter(sessionId: String, fromMessageId: String): Int = transaction(database) {
        // First get the timestamp of the from message
        val fromTimestamp = ChatMessagesTable
            .select(ChatMessagesTable.createdAt)
            .where { ChatMessagesTable.id eq fromMessageId }
            .singleOrNull()
            ?.get(ChatMessagesTable.createdAt)
            ?: return@transaction 0

        // Then mark all messages after that timestamp as outdated
        ChatMessagesTable.update({
            (ChatMessagesTable.sessionId eq sessionId) and
                ChatMessagesTable.createdAt.greater(fromTimestamp)
        }) {
            it[isOutdated] = 1
        }
    }

    /**
     * Update a message's content and set its edit parent.
     * Used when editing a message.
     *
     * @param messageId The ID of the message to update
     * @param newContent The new content
     * @param editParentId The ID of the original message that was edited
     */
    fun updateMessageContent(messageId: String, newContent: String, editParentId: String?) {
        transaction(database) {
            ChatMessagesTable.update({ ChatMessagesTable.id eq messageId }) {
                it[content] = newContent
                it[ChatMessagesTable.editParentId] = editParentId
            }
        }
    }

    /**
     * Get only active (non-outdated) messages for a session.
     * This is used when building context for AI responses.
     *
     * @param sessionId The session ID
     * @return List of active messages, ordered by creation time
     */
    fun getActiveMessages(sessionId: String): List<ChatMessage> = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    (ChatMessagesTable.isOutdated eq 0)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }
    }

    /**
     * Get outdated messages for a session.
     * Used to show collapsed outdated branches in the UI.
     *
     * @param sessionId The session ID
     * @return List of outdated messages, ordered by creation time
     */
    fun getOutdatedMessages(sessionId: String): List<ChatMessage> = transaction(database) {
        ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    (ChatMessagesTable.isOutdated eq 1)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .map { row ->
                ChatMessage(
                    id = row[ChatMessagesTable.id],
                    sessionId = row[ChatMessagesTable.sessionId],
                    role = MessageRole.entries.find { it.value == row[ChatMessagesTable.role] } ?: MessageRole.USER,
                    content = row[ChatMessagesTable.content],
                    createdAt = row[ChatMessagesTable.createdAt],
                    isOutdated = row[ChatMessagesTable.isOutdated] == 1,
                    editParentId = row[ChatMessagesTable.editParentId],
                )
            }
    }

    fun saveSummary(summary: ConversationSummary) {
        transaction(database) {
            ConversationSummariesTable.upsert {
                it[sessionId] = summary.sessionId
                it[keyFacts] = json.encodeToString(summary.keyFacts)
                it[mainTopics] = json.encodeToString(summary.mainTopics)
                it[recentContext] = summary.recentContext
                it[lastSummarizedMessageId] = summary.lastSummarizedMessageId
                it[createdAt] = summary.createdAt
            }
        }
    }

    fun getConversationSummary(sessionId: String): ConversationSummary? = transaction(database) {
        ConversationSummariesTable
            .selectAll()
            .where { ConversationSummariesTable.sessionId eq sessionId }
            .singleOrNull()
            ?.let { row ->
                try {
                    ConversationSummary(
                        sessionId = row[ConversationSummariesTable.sessionId],
                        keyFacts = json.decodeFromString<Map<String, String>>(row[ConversationSummariesTable.keyFacts]),
                        mainTopics = json.decodeFromString<List<String>>(row[ConversationSummariesTable.mainTopics]),
                        recentContext = row[ConversationSummariesTable.recentContext],
                        lastSummarizedMessageId = row[ConversationSummariesTable.lastSummarizedMessageId],
                        createdAt = row[ConversationSummariesTable.createdAt],
                    )
                } catch (e: Exception) {
                    null // Return null if JSON parsing fails
                }
            }
    }

    private fun generateTitle(firstMessage: String): String {
        // Simple title generation - take first SESSION_TITLE_MAX_LENGTH chars or first sentence
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
     * Delete a chat session and all its related data (messages and summaries)
     */
    fun deleteSession(sessionId: String): Boolean = transaction(database) {
        // Delete conversation summaries
        ConversationSummariesTable.deleteWhere { ConversationSummariesTable.sessionId eq sessionId }

        // Delete chat messages
        ChatMessagesTable.deleteWhere { ChatMessagesTable.sessionId eq sessionId }

        // Delete the session itself
        ChatSessionsTable.deleteWhere { ChatSessionsTable.id eq sessionId } > 0
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
            .map { row ->
                ChatSession(
                    id = row[ChatSessionsTable.id],
                    title = row[ChatSessionsTable.title],
                    createdAt = row[ChatSessionsTable.createdAt],
                    updatedAt = row[ChatSessionsTable.updatedAt],
                    directiveId = row[ChatSessionsTable.directiveId],
                    folderId = row[ChatSessionsTable.folderId],
                    isStarred = row[ChatSessionsTable.isStarred] == 1,
                    sortOrder = row[ChatSessionsTable.sortOrder],
                )
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
            .map { row ->
                ChatSession(
                    id = row[ChatSessionsTable.id],
                    title = row[ChatSessionsTable.title],
                    createdAt = row[ChatSessionsTable.createdAt],
                    updatedAt = row[ChatSessionsTable.updatedAt],
                    directiveId = row[ChatSessionsTable.directiveId],
                    folderId = row[ChatSessionsTable.folderId],
                    isStarred = row[ChatSessionsTable.isStarred] == 1,
                    sortOrder = row[ChatSessionsTable.sortOrder],
                )
            }
    }

    /**
     * Create a new folder
     */
    fun createFolder(
        name: String,
        parentFolderId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int = 0,
    ): ChatFolder {
        val folder = ChatFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            parentFolderId = parentFolderId,
            color = color,
            icon = icon,
            sortOrder = sortOrder,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        transaction(database) {
            ChatFoldersTable.insert {
                it[id] = folder.id
                it[ChatFoldersTable.name] = folder.name
                it[ChatFoldersTable.parentFolderId] = folder.parentFolderId
                it[ChatFoldersTable.color] = folder.color
                it[ChatFoldersTable.icon] = folder.icon
                it[ChatFoldersTable.sortOrder] = folder.sortOrder
                it[createdAt] = folder.createdAt
                it[updatedAt] = folder.updatedAt
            }
        }

        return folder
    }

    /**
     * Get all folders
     */
    fun getAllFolders(): List<ChatFolder> = transaction(database) {
        ChatFoldersTable
            .selectAll()
            .orderBy(
                ChatFoldersTable.sortOrder to SortOrder.ASC,
                ChatFoldersTable.name to SortOrder.ASC,
            )
            .map { row ->
                ChatFolder(
                    id = row[ChatFoldersTable.id],
                    name = row[ChatFoldersTable.name],
                    parentFolderId = row[ChatFoldersTable.parentFolderId],
                    color = row[ChatFoldersTable.color],
                    icon = row[ChatFoldersTable.icon],
                    sortOrder = row[ChatFoldersTable.sortOrder],
                    createdAt = row[ChatFoldersTable.createdAt],
                    updatedAt = row[ChatFoldersTable.updatedAt],
                )
            }
    }

    /**
     * Get a folder by ID
     */
    fun getFolder(folderId: String): ChatFolder? = transaction(database) {
        ChatFoldersTable
            .selectAll()
            .where { ChatFoldersTable.id eq folderId }
            .singleOrNull()
            ?.let { row ->
                ChatFolder(
                    id = row[ChatFoldersTable.id],
                    name = row[ChatFoldersTable.name],
                    parentFolderId = row[ChatFoldersTable.parentFolderId],
                    color = row[ChatFoldersTable.color],
                    icon = row[ChatFoldersTable.icon],
                    sortOrder = row[ChatFoldersTable.sortOrder],
                    createdAt = row[ChatFoldersTable.createdAt],
                    updatedAt = row[ChatFoldersTable.updatedAt],
                )
            }
    }

    /**
     * Update folder properties
     */
    fun updateFolder(
        folderId: String,
        name: String? = null,
        parentFolderId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int? = null,
    ): Boolean {
        // Check if there's anything to update
        if (name == null && parentFolderId === null && color === null && icon === null && sortOrder == null) {
            return false
        }

        return transaction(database) {
            ChatFoldersTable.update({ ChatFoldersTable.id eq folderId }) {
                if (name != null) it[ChatFoldersTable.name] = name
                if (parentFolderId !== null) it[ChatFoldersTable.parentFolderId] = parentFolderId
                if (color !== null) it[ChatFoldersTable.color] = color
                if (icon !== null) it[ChatFoldersTable.icon] = icon
                if (sortOrder != null) it[ChatFoldersTable.sortOrder] = sortOrder
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
    }

    /**
     * Delete a folder (moves sessions to root)
     */
    fun deleteFolder(folderId: String): Boolean = transaction(database) {
        // Move sessions to root (null folder_id)
        ChatSessionsTable.update({ ChatSessionsTable.folderId eq folderId }) {
            it[ChatSessionsTable.folderId] = null
        }

        // Move child folders to root
        ChatFoldersTable.update({ ChatFoldersTable.parentFolderId eq folderId }) {
            it[ChatFoldersTable.parentFolderId] = null
        }

        // Delete the folder
        ChatFoldersTable.deleteWhere { ChatFoldersTable.id eq folderId } > 0
    }
}
