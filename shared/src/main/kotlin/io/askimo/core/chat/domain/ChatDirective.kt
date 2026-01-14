/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a custom instruction/directive that users can apply to chat sessions
 * to influence AI behavior (tone, format, style, etc.)
 */
data class ChatDirective(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

const val DIRECTIVE_NAME_MAX_LENGTH = 128
const val DIRECTIVE_CONTENT_MAX_LENGTH = 8192

/**
 * Exposed table definition for chat_directives.
 * Co-located with domain class for easier maintenance and foreign key references.
 */
object ChatDirectivesTable : Table("chat_directives") {
    val id = varchar("id", 36)
    val name = varchar("name", DIRECTIVE_NAME_MAX_LENGTH)
    val content = varchar("content", DIRECTIVE_CONTENT_MAX_LENGTH)
    val createdAt = sqliteDatetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
