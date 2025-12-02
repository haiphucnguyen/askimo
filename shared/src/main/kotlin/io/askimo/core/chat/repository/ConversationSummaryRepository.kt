/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ConversationSummary
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.sqliteDatetime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

/**
 * Exposed table definition for conversation_summaries.
 */
object ConversationSummariesTable : Table("conversation_summaries") {
    val sessionId = varchar("session_id", 36)
    val keyFacts = text("key_facts")
    val mainTopics = text("main_topics")
    val recentContext = text("recent_context")
    val lastSummarizedMessageId = varchar("last_summarized_message_id", 36)
    val createdAt = sqliteDatetime("created_at")

    override val primaryKey = PrimaryKey(sessionId)
}

class ConversationSummaryRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    private val json = Json { ignoreUnknownKeys = true }

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
                } catch (_: Exception) {
                    // Log error if needed
                    null
                }
            }
    }

    /**
     * Delete summary for a session
     */
    fun deleteSummaryBySession(sessionId: String): Int = transaction(database) {
        ConversationSummariesTable.deleteWhere { ConversationSummariesTable.sessionId eq sessionId }
    }
}
