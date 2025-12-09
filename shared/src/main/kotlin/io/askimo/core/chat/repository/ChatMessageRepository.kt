/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.FileAttachment
import io.askimo.core.context.MessageRole
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

enum class PaginationDirection {
    FORWARD,
    BACKWARD,
}

/**
 * Exposed table definition for chat_messages.
 */
object ChatMessagesTable : Table("chat_messages") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val role = varchar("role", 50)
    val content = text("content")
    val createdAt = sqliteDatetime("created_at")
    val isOutdated = integer("is_outdated").default(0)
    val editParentId = varchar("edit_parent_id", 36).nullable()
    val isEdited = integer("is_edited").default(0)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Extension function to map an Exposed ResultRow to a ChatMessage object.
 */
private fun ResultRow.toChatMessage(): ChatMessage = ChatMessage(
    id = this[ChatMessagesTable.id],
    sessionId = this[ChatMessagesTable.sessionId],
    role = MessageRole.entries.find { it.value == this[ChatMessagesTable.role] } ?: MessageRole.USER,
    content = this[ChatMessagesTable.content],
    createdAt = this[ChatMessagesTable.createdAt],
    isOutdated = this[ChatMessagesTable.isOutdated] == 1,
    editParentId = this[ChatMessagesTable.editParentId],
    isEdited = this[ChatMessagesTable.isEdited] == 1,
)

/**
 * Extension function to map an Exposed ResultRow to a FileAttachment object (from JOIN).
 */
private fun ResultRow.toFileAttachment(): FileAttachment = FileAttachment(
    id = this[ChatMessageAttachmentsTable.id],
    messageId = this[ChatMessageAttachmentsTable.messageId],
    sessionId = this[ChatMessageAttachmentsTable.sessionId],
    fileName = this[ChatMessageAttachmentsTable.fileName],
    mimeType = this[ChatMessageAttachmentsTable.mimeType],
    size = this[ChatMessageAttachmentsTable.size],
    createdAt = this[ChatMessageAttachmentsTable.createdAt],
    content = null, // Content is not stored in DB
)

class ChatMessageRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
    private val attachmentRepository: ChatMessageAttachmentRepository = ChatMessageAttachmentRepository(databaseManager),
) : AbstractSQLiteRepository(databaseManager) {

    fun addMessage(message: ChatMessage): ChatMessage {
        val messageWithInjectedFields = message.copy(
            id = UUID.randomUUID().toString(),
        )

        transaction(database) {
            ChatMessagesTable.insert {
                it[id] = messageWithInjectedFields.id
                it[ChatMessagesTable.sessionId] = messageWithInjectedFields.sessionId
                it[ChatMessagesTable.role] = messageWithInjectedFields.role.value
                it[ChatMessagesTable.content] = messageWithInjectedFields.content
                it[createdAt] = messageWithInjectedFields.createdAt
                it[ChatMessagesTable.isOutdated] = if (messageWithInjectedFields.isOutdated) 1 else 0
                it[ChatMessagesTable.editParentId] = messageWithInjectedFields.editParentId
                it[ChatMessagesTable.isEdited] = if (messageWithInjectedFields.isEdited) 1 else 0
            }

            // Save attachments if any
            if (messageWithInjectedFields.attachments.isNotEmpty()) {
                val attachmentsWithMessageId = messageWithInjectedFields.attachments.map { attachment ->
                    attachment.copy(
                        messageId = messageWithInjectedFields.id,
                        sessionId = messageWithInjectedFields.sessionId,
                    )
                }
                attachmentRepository.addAttachments(attachmentsWithMessageId)
            }
        }

        return messageWithInjectedFields
    }

    fun getMessages(sessionId: String): List<ChatMessage> = transaction(database) {
        val messages = ChatMessagesTable
            .selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .map { it.toChatMessage() }

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
        }
    }

    fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessage> = transaction(database) {
        val messages = ChatMessagesTable
            .selectAll()
            .where { ChatMessagesTable.sessionId eq sessionId }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toChatMessage() }
            .reversed()

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
        }
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
        val messages = ChatMessagesTable
            .selectAll()
            .where { (ChatMessagesTable.sessionId eq sessionId) and (ChatMessagesTable.isOutdated eq 0) }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toChatMessage() }
            .reversed()

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
        }
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

        val messages = orderedQuery
            .limit(limit + 1)
            .map { it.toChatMessage() }

        // Check if there are more messages
        val hasMore = messages.size > limit
        val resultMessages = if (hasMore) messages.take(limit) else messages

        // Reverse if we fetched in backward direction to maintain chronological order
        val orderedMessages = if (direction == PaginationDirection.BACKWARD) resultMessages.reversed() else resultMessages

        // Load attachments using helper
        val messageIds = orderedMessages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        val messagesWithAttachments = orderedMessages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
        }

        // Calculate next cursor
        val nextCursor = if (hasMore && messagesWithAttachments.isNotEmpty()) {
            if (direction == PaginationDirection.FORWARD) {
                messagesWithAttachments.last().createdAt
            } else {
                messagesWithAttachments.first().createdAt
            }
        } else {
            null
        }

        Pair(messagesWithAttachments, nextCursor)
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
            val messages = ChatMessagesTable
                .selectAll()
                .where {
                    (ChatMessagesTable.sessionId eq sessionId) and
                        ChatMessagesTable.content.lowerCase().like("%${searchQuery.lowercase()}%")
                }
                .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .map { it.toChatMessage() }

            val messageIds = messages.map { it.id }
            val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

            messages.map { message ->
                message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
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

        val messages = ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    ChatMessagesTable.createdAt.greater(afterTimestamp)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { it.toChatMessage() }

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
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
        val afterTimestamp = ChatMessagesTable
            .select(ChatMessagesTable.createdAt)
            .where { ChatMessagesTable.id eq afterMessageId }
            .singleOrNull()
            ?.get(ChatMessagesTable.createdAt)
            ?: return@transaction emptyList()

        val messages = ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    (ChatMessagesTable.isOutdated eq 0) and
                    ChatMessagesTable.createdAt.greater(afterTimestamp)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { it.toChatMessage() }

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
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
     * Get only active (non-outdated) messages for a session.
     * This is used when building context for AI responses.
     *
     * @param sessionId The session ID
     * @return List of active messages, ordered by creation time
     */
    fun getActiveMessages(sessionId: String): List<ChatMessage> = transaction(database) {
        val messages = ChatMessagesTable
            .selectAll()
            .where {
                (ChatMessagesTable.sessionId eq sessionId) and
                    (ChatMessagesTable.isOutdated eq 0)
            }
            .orderBy(ChatMessagesTable.createdAt to SortOrder.ASC)
            .map { it.toChatMessage() }

        val messageIds = messages.map { it.id }
        val attachmentsMap = loadAttachmentsForMessageIds(messageIds)

        messages.map { message ->
            message.copy(attachments = attachmentsMap[message.id] ?: emptyList())
        }
    }

    /**
     * Update the content of a message and mark it as edited.
     * This is used when a user edits an AI response message.
     *
     * @param messageId The ID of the message to update
     * @param newContent The new content for the message
     * @return Number of messages updated (should be 1)
     */
    fun updateMessageContent(messageId: String, newContent: String): Int = transaction(database) {
        ChatMessagesTable.update({ ChatMessagesTable.id eq messageId }) {
            it[content] = newContent
            it[isEdited] = 1
        }
    }

    /**
     * Delete all messages for a session.
     * Attachments are automatically deleted via CASCADE foreign key constraint.
     */
    fun deleteMessagesBySession(sessionId: String): Int = transaction(database) {
        ChatMessagesTable.deleteWhere { ChatMessagesTable.sessionId eq sessionId }
    }

    /**
     * Helper method to load attachments for messages using LEFT JOIN.
     * This performs a single database query to efficiently load all attachments.
     *
     * @param messageIds List of message IDs to load attachments for
     * @return Map of message ID to list of attachments
     */
    private fun loadAttachmentsForMessageIds(messageIds: List<String>): Map<String, List<FileAttachment>> {
        if (messageIds.isEmpty()) return emptyMap()

        val messagesMap = mutableMapOf<String, ChatMessage>()
        val attachmentsMap = mutableMapOf<String, MutableList<FileAttachment>>()

        (ChatMessagesTable leftJoin ChatMessageAttachmentsTable)
            .selectAll()
            .where { ChatMessagesTable.id inList messageIds }
            .forEach { row ->
                val messageId = row[ChatMessagesTable.id]

                if (!messagesMap.containsKey(messageId)) {
                    messagesMap[messageId] = row.toChatMessage()
                }

                row.getOrNull(ChatMessageAttachmentsTable.id)?.let {
                    attachmentsMap.getOrPut(messageId) { mutableListOf() }
                        .add(row.toFileAttachment())
                }
            }

        return attachmentsMap.mapValues { it.value.toList() }
    }
}
