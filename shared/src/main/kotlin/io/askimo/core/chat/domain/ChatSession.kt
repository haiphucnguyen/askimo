/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val projectId: String? = null, // null = no project (general chat)
    val directiveId: String? = null,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0,
)

const val SESSION_TITLE_MAX_LENGTH = 256

/**
 * Exposed table definition for chat_sessions.
 * Co-located with domain class for easier maintenance and foreign key references.
 */
object ChatSessionsTable : Table("chat_sessions") {
    val id = varchar("id", 36)
    val title = varchar("title", SESSION_TITLE_MAX_LENGTH)
    val createdAt = sqliteDatetime("created_at")
    val updatedAt = sqliteDatetime("updated_at")

    // Foreign key to projects with CASCADE delete
    val projectId = varchar("project_id", 36).nullable()

    val directiveId = varchar("directive_id", 36).nullable()
    val isStarred = integer("is_starred").default(0)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        foreignKey(projectId to ProjectsTable.id, onDelete = ReferenceOption.CASCADE)
    }
}
