/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.context.MessageRole
import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isOutdated: Boolean = false,
    val editParentId: String? = null,
    val isEdited: Boolean = false,
    val attachments: List<FileAttachment> = emptyList(),
    val isFailed: Boolean = false,
)

/**
 * Exposed table definition for chat_messages.
 * Co-located with domain class for easier maintenance and foreign key references.
 */
object ChatMessagesTable : Table("chat_messages") {
    val id = varchar("id", 36)

    // Foreign key to chat_sessions with CASCADE delete
    val sessionId = varchar("session_id", 36)

    val role = varchar("role", 50)
    val content = text("content")
    val createdAt = sqliteDatetime("created_at")
    val isOutdated = integer("is_outdated").default(0)

    // Self-referencing foreign key with SET NULL on delete
    val editParentId = varchar("edit_parent_id", 36).nullable()

    val isEdited = integer("is_edited").default(0)
    val isFailed = integer("is_failed").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        // Define foreign key constraints
        // When a session is deleted, all its messages are automatically deleted
        foreignKey(sessionId to ChatSessionsTable.id, onDelete = ReferenceOption.CASCADE)

        // When a parent message is deleted, set editParentId to NULL (don't delete the child)
        foreignKey(editParentId to id, onDelete = ReferenceOption.SET_NULL)
    }
}
