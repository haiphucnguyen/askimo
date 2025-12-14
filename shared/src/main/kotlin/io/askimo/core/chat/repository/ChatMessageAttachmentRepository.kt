/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatMessageAttachmentsTable
import io.askimo.core.chat.domain.FileAttachment
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Extension function to map an Exposed ResultRow to a FileAttachment object.
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

/**
 * Repository for managing chat message attachments in SQLite database.
 * Stores attachment metadata only; file content is stored on filesystem.
 */
class ChatMessageAttachmentRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    /**
     * Add a new attachment to the database.
     *
     * @param attachment The attachment to add
     * @return The attachment with generated ID if not provided
     */
    fun addAttachment(attachment: FileAttachment): FileAttachment {
        val attachmentWithId = if (attachment.id.isEmpty()) {
            attachment.copy(id = UUID.randomUUID().toString())
        } else {
            attachment
        }

        transaction(database) {
            ChatMessageAttachmentsTable.insert {
                it[id] = attachmentWithId.id
                it[messageId] = attachmentWithId.messageId
                it[sessionId] = attachmentWithId.sessionId
                it[fileName] = attachmentWithId.fileName
                it[mimeType] = attachmentWithId.mimeType
                it[size] = attachmentWithId.size
                it[createdAt] = attachmentWithId.createdAt
            }
        }

        return attachmentWithId
    }

    /**
     * Add multiple attachments in a batch.
     * NOTE: Must be called within a transaction context.
     *
     * @param attachments List of attachments to add
     * @return List of attachments with generated IDs
     */
    fun addAttachments(attachments: List<FileAttachment>): List<FileAttachment> = attachments.map { attachment ->
        val attachmentWithId = if (attachment.id.isEmpty()) {
            attachment.copy(id = UUID.randomUUID().toString())
        } else {
            attachment
        }

        ChatMessageAttachmentsTable.insert {
            it[id] = attachmentWithId.id
            it[messageId] = attachmentWithId.messageId
            it[sessionId] = attachmentWithId.sessionId
            it[fileName] = attachmentWithId.fileName
            it[mimeType] = attachmentWithId.mimeType
            it[size] = attachmentWithId.size
            it[createdAt] = attachmentWithId.createdAt
        }

        attachmentWithId
    }

    /**
     * Get all attachments for a specific message.
     *
     * @param messageId The message ID
     * @return List of attachments (without file content)
     */
    fun getAttachmentsByMessageId(messageId: String): List<FileAttachment> = transaction(database) {
        ChatMessageAttachmentsTable
            .selectAll()
            .where { ChatMessageAttachmentsTable.messageId eq messageId }
            .map { it.toFileAttachment() }
    }

    /**
     * Get all attachments for multiple messages in a single query.
     * Useful for loading attachments for a list of messages efficiently.
     *
     * @param messageIds List of message IDs
     * @return Map of messageId to list of attachments
     */
    fun getAttachmentsByMessageIds(messageIds: List<String>): Map<String, List<FileAttachment>> {
        if (messageIds.isEmpty()) return emptyMap()

        return transaction(database) {
            ChatMessageAttachmentsTable
                .selectAll()
                .where { ChatMessageAttachmentsTable.messageId inList messageIds }
                .map { it.toFileAttachment() }
                .groupBy { it.messageId }
        }
    }

    /**
     * Get all attachments for a session.
     *
     * @param sessionId The session ID
     * @return List of attachments
     */
    fun getAttachmentsBySessionId(sessionId: String): List<FileAttachment> = transaction(database) {
        ChatMessageAttachmentsTable
            .selectAll()
            .where { ChatMessageAttachmentsTable.sessionId eq sessionId }
            .map { it.toFileAttachment() }
    }

    /**
     * Delete attachments for a specific message.
     *
     * @param messageId The message ID
     * @return Number of attachments deleted
     */
    fun deleteAttachmentsByMessageId(messageId: String): Int = transaction(database) {
        ChatMessageAttachmentsTable.deleteWhere {
            ChatMessageAttachmentsTable.messageId eq messageId
        }
    }

    /**
     * Delete all attachments for a session.
     *
     * @param sessionId The session ID
     * @return Number of attachments deleted
     */
    fun deleteAttachmentsBySessionId(sessionId: String): Int = transaction(database) {
        ChatMessageAttachmentsTable.deleteWhere {
            ChatMessageAttachmentsTable.sessionId eq sessionId
        }
    }

    /**
     * Delete a specific attachment by its ID.
     *
     * @param attachmentId The attachment ID
     * @return Number of attachments deleted (0 or 1)
     */
    fun deleteAttachment(attachmentId: String): Int = transaction(database) {
        ChatMessageAttachmentsTable.deleteWhere {
            id eq attachmentId
        }
    }

    /**
     * Get a specific attachment by its ID.
     *
     * @param attachmentId The attachment ID
     * @return The attachment, or null if not found
     */
    fun getAttachmentById(attachmentId: String): FileAttachment? = transaction(database) {
        ChatMessageAttachmentsTable
            .selectAll()
            .where { ChatMessageAttachmentsTable.id eq attachmentId }
            .map { it.toFileAttachment() }
            .singleOrNull()
    }
}
