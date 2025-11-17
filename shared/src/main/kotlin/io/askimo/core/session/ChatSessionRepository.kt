/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.util.AskimoHome
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

const val SESSION_TITLE_MAX_LENGTH = 256

class ChatSessionRepository {
    private val hikariDataSource: HikariDataSource by lazy {
        val dbPath = AskimoHome.base().resolve("chat_sessions.db").toString()

        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$dbPath"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            // SQLite specific optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        HikariDataSource(config).also { ds ->
            // Initialize database schema
            ds.connection.use { conn ->
                initializeDatabase(conn)
            }
        }
    }

    private val dataSource: DataSource get() = hikariDataSource
    private val json = Json { ignoreUnknownKeys = true }

    private fun initializeDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
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
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
                )
            """,
            )

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

    fun createSession(title: String): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO chat_sessions (id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?)
            """,
            ).use { stmt ->
                stmt.setString(1, session.id)
                stmt.setString(2, session.title)
                stmt.setString(3, session.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                stmt.setString(4, session.updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                stmt.executeUpdate()
            }
        }

        return session
    }

    fun getAllSessions(): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    """
                    SELECT id, title, created_at, updated_at
                    FROM chat_sessions
                    ORDER BY updated_at DESC
                """,
                )
                while (rs.next()) {
                    sessions.add(
                        ChatSession(
                            id = rs.getString("id"),
                            title = rs.getString("title"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                            updatedAt = LocalDateTime.parse(rs.getString("updated_at")),
                        ),
                    )
                }
            }
        }
        return sessions
    }

    fun getSession(sessionId: String): ChatSession? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, title, created_at, updated_at
                FROM chat_sessions
                WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                val rs = stmt.executeQuery()
                return if (rs.next()) {
                    ChatSession(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        updatedAt = LocalDateTime.parse(rs.getString("updated_at")),
                    )
                } else {
                    null
                }
            }
        }
    }

    fun addMessage(sessionId: String, role: MessageRole, content: String): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            createdAt = LocalDateTime.now(),
        )

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Insert message
                conn.prepareStatement(
                    """
                    INSERT INTO chat_messages (id, session_id, role, content, created_at)
                    VALUES (?, ?, ?, ?, ?)
                """,
                ).use { stmt ->
                    stmt.setString(1, message.id)
                    stmt.setString(2, message.sessionId)
                    stmt.setString(3, message.role.value)
                    stmt.setString(4, message.content)
                    stmt.setString(5, message.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    stmt.executeUpdate()
                }

                // Update session's updated_at
                conn.prepareStatement(
                    """
                    UPDATE chat_sessions SET updated_at = ? WHERE id = ?
                """,
                ).use { stmt ->
                    stmt.setString(1, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    stmt.setString(2, sessionId)
                    stmt.executeUpdate()
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

        return message
    }

    fun getMessages(sessionId: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, session_id, role, content, created_at
                FROM chat_messages
                WHERE session_id = ?
                ORDER BY created_at ASC
            """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.entries.find { it.value == rs.getString("role") } ?: MessageRole.USER,
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return messages
    }

    fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, session_id, role, content, created_at
                FROM chat_messages
                WHERE session_id = ?
                ORDER BY created_at DESC
                LIMIT ?
            """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.entries.find { it.value == rs.getString("role") } ?: MessageRole.USER,
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return messages.reversed() // Return in chronological order
    }

    /**
     * Get messages with cursor-based pagination
     * @param sessionId The session ID
     * @param limit Number of messages to retrieve (default: 20)
     * @param cursor The timestamp cursor for pagination. If null, starts from the beginning (oldest messages)
     * @param direction Direction of pagination: "forward" (newer messages) or "backward" (older messages)
     * @return A pair of messages list and the next cursor (null if no more messages)
     */
    fun getMessagesPaginated(
        sessionId: String,
        limit: Int = 20,
        cursor: LocalDateTime? = null,
        direction: String = "forward",
    ): Pair<List<ChatMessage>, LocalDateTime?> {
        val messages = mutableListOf<ChatMessage>()
        dataSource.connection.use { conn ->
            val query = when {
                cursor == null && direction == "forward" -> {
                    // Start from the beginning (oldest messages)
                    """
                    SELECT id, session_id, role, content, created_at
                    FROM chat_messages
                    WHERE session_id = ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    """
                }
                cursor == null && direction == "backward" -> {
                    // Start from the end (newest messages)
                    """
                    SELECT id, session_id, role, content, created_at
                    FROM chat_messages
                    WHERE session_id = ?
                    ORDER BY created_at DESC
                    LIMIT ?
                    """
                }
                direction == "forward" -> {
                    // Get messages after the cursor (newer messages)
                    """
                    SELECT id, session_id, role, content, created_at
                    FROM chat_messages
                    WHERE session_id = ? AND created_at > ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    """
                }
                else -> {
                    // Get messages before the cursor (older messages)
                    """
                    SELECT id, session_id, role, content, created_at
                    FROM chat_messages
                    WHERE session_id = ? AND created_at < ?
                    ORDER BY created_at DESC
                    LIMIT ?
                    """
                }
            }

            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, sessionId)
                if (cursor != null) {
                    stmt.setString(2, cursor.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    stmt.setInt(3, limit + 1) // Fetch one extra to determine if there are more
                } else {
                    stmt.setInt(2, limit + 1) // Fetch one extra to determine if there are more
                }

                val rs = stmt.executeQuery()
                while (rs.next()) {
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.entries.find { it.value == rs.getString("role") } ?: MessageRole.USER,
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }

        // Check if there are more messages
        val hasMore = messages.size > limit
        val resultMessages = if (hasMore) messages.take(limit) else messages

        // Reverse if we fetched in backward direction to maintain chronological order
        val orderedMessages = if (direction == "backward") resultMessages.reversed() else resultMessages

        // Calculate next cursor
        val nextCursor = if (hasMore && orderedMessages.isNotEmpty()) {
            if (direction == "forward") {
                orderedMessages.last().createdAt
            } else {
                orderedMessages.first().createdAt
            }
        } else {
            null
        }

        return Pair(orderedMessages, nextCursor)
    }

    fun getMessageCount(sessionId: String): Int = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM chat_messages WHERE session_id = ?").use { stmt ->
            stmt.setString(1, sessionId)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    /**
     * Search messages in a session by content.
     *
     * @param sessionId The session ID to search in
     * @param searchQuery The search query (case-insensitive)
     * @param limit Maximum number of results to return
     * @return List of messages matching the search query, ordered by creation time (newest first)
     */
    fun searchMessages(
        sessionId: String,
        searchQuery: String,
        limit: Int = 100,
    ): List<ChatMessage> {
        if (searchQuery.isBlank()) return emptyList()

        val messages = mutableListOf<ChatMessage>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, session_id, role, content, created_at
                FROM chat_messages
                WHERE session_id = ? AND LOWER(content) LIKE LOWER(?)
                ORDER BY created_at DESC
                LIMIT ?
                """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, "%$searchQuery%")
                stmt.setInt(3, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.entries.find { it.value == rs.getString("role") } ?: MessageRole.USER,
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return messages
    }

    fun getMessagesAfter(sessionId: String, afterMessageId: String, limit: Int): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, session_id, role, content, created_at
                FROM chat_messages
                WHERE session_id = ? AND created_at > (
                    SELECT created_at FROM chat_messages WHERE id = ?
                )
                ORDER BY created_at ASC
                LIMIT ?
            """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, afterMessageId)
                stmt.setInt(3, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    messages.add(
                        ChatMessage(
                            id = rs.getString("id"),
                            sessionId = rs.getString("session_id"),
                            role = MessageRole.entries.find { it.value == rs.getString("role") } ?: MessageRole.USER,
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return messages
    }

    fun saveSummary(summary: ConversationSummary) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO conversation_summaries
                (session_id, key_facts, main_topics, recent_context, last_summarized_message_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).use { stmt ->
                stmt.setString(1, summary.sessionId)
                stmt.setString(2, json.encodeToString(summary.keyFacts))
                stmt.setString(3, json.encodeToString(summary.mainTopics))
                stmt.setString(4, summary.recentContext)
                stmt.setString(5, summary.lastSummarizedMessageId)
                stmt.setString(6, summary.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                stmt.executeUpdate()
            }
        }
    }

    fun getConversationSummary(sessionId: String): ConversationSummary? = dataSource.connection.use { conn ->
        conn.prepareStatement(
            """
                SELECT session_id, key_facts, main_topics, recent_context, last_summarized_message_id, created_at
                FROM conversation_summaries
                WHERE session_id = ?
            """,
        ).use { stmt ->
            stmt.setString(1, sessionId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                try {
                    ConversationSummary(
                        sessionId = rs.getString("session_id"),
                        keyFacts = json.decodeFromString<Map<String, String>>(rs.getString("key_facts")),
                        mainTopics = json.decodeFromString<List<String>>(rs.getString("main_topics")),
                        recentContext = rs.getString("recent_context"),
                        lastSummarizedMessageId = rs.getString("last_summarized_message_id"),
                        createdAt = LocalDateTime.parse(rs.getString("created_at")),
                    )
                } catch (e: Exception) {
                    null // Return null if JSON parsing fails
                }
            } else {
                null
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
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE chat_sessions SET title = ? WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, title)
                stmt.setString(2, sessionId)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Delete a chat session and all its related data (messages and summaries)
     */
    fun deleteSession(sessionId: String): Boolean {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Delete conversation summaries
                conn.prepareStatement(
                    """
                    DELETE FROM conversation_summaries WHERE session_id = ?
                """,
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.executeUpdate()
                }

                // Delete chat messages
                conn.prepareStatement(
                    """
                    DELETE FROM chat_messages WHERE session_id = ?
                """,
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.executeUpdate()
                }

                // Delete the session itself
                val rowsAffected = conn.prepareStatement(
                    """
                    DELETE FROM chat_sessions WHERE id = ?
                """,
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.executeUpdate()
                }

                conn.commit()
                return rowsAffected > 0
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Gracefully close the connection pool when the application shuts down
     */
    fun close() {
        if (hikariDataSource.isRunning) {
            hikariDataSource.close()
        }
    }
}
