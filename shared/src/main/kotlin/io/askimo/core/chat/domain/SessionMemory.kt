/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

/**
 * Domain model for session memory persistence.
 * Stores the serialized state of TokenAwareSummarizingMemory for a chat session.
 *
 * @property sessionId Unique identifier for the chat session
 * @property memorySummary JSON serialized ConversationSummary (nullable)
 * @property memoryMessages JSON serialized List<ChatMessage> from LangChain4j
 * @property lastUpdated Timestamp of last memory update
 * @property createdAt Timestamp when memory was first created
 */
data class SessionMemory(
    val sessionId: String,
    val memorySummary: String?,
    val memoryMessages: String,
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Exposed table definition for session_memory.
 */
object SessionMemoryTable : Table("session_memory") {
    val sessionId = varchar("session_id", 255).references(
        ChatSessionsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
    val memorySummary = text("memory_summary").nullable()
    val memoryMessages = text("memory_messages")
    val lastUpdated = sqliteDatetime("last_updated")
    val createdAt = sqliteDatetime("created_at")

    override val primaryKey = PrimaryKey(sessionId)
}
