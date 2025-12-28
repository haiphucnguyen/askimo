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
}
