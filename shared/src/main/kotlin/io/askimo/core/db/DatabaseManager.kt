/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatFolderRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ConversationSummaryRepository
import java.sql.Connection
import javax.sql.DataSource

/**
 * Singleton manager for database connections and schema initialization.
 * Maintains a single HikariDataSource per database file for resource efficiency.
 *
 * This centralizes database lifecycle management and ensures all repositories
 * share the same connection pool, avoiding resource waste and test isolation issues.
 */
class DatabaseManager private constructor(
    val databaseFileName: String = "askimo.db",
    useInMemory: Boolean = false,
) : AutoCloseable {

    private val hikariDataSource: HikariDataSource =
        DatabaseConnectionFactory.createSQLiteDataSource(
            databaseFileName = databaseFileName,
            initializeDatabase = ::initializeTables,
            useInMemory = useInMemory,
        )

    /**
     * The datasource for obtaining database connections.
     * All repositories using this manager share this connection pool.
     */
    val dataSource: DataSource get() = hikariDataSource

    /**
     * Initialize all database tables in correct dependency order.
     * This method is called automatically during datasource creation.
     *
     * @param connection An open database connection for executing initialization SQL
     */
    private fun initializeTables(connection: Connection) {
        // Create tables in dependency order (respecting foreign key constraints)
        createFoldersTable(connection)
        createSessionsTable(connection)
        createMessagesTable(connection)
        createSummariesTable(connection)
        createDirectivesTable(connection)
    }

    private fun createFoldersTable(conn: Connection) {
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
                    FOREIGN KEY (parent_folder_id) REFERENCES chat_folders (id) ON DELETE CASCADE
                )
                """,
            )
        }
    }

    private fun createSessionsTable(conn: Connection) {
        conn.createStatement().use { stmt ->
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
                    FOREIGN KEY (folder_id) REFERENCES chat_folders (id) ON DELETE SET NULL
                )
                """,
            )
        }
    }

    private fun createMessagesTable(conn: Connection) {
        conn.createStatement().use { stmt ->
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
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
                    FOREIGN KEY (edit_parent_id) REFERENCES chat_messages (id) ON DELETE SET NULL
                )
                """,
            )
        }
    }

    private fun createSummariesTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversation_summaries (
                    session_id TEXT PRIMARY KEY,
                    key_facts TEXT NOT NULL,
                    main_topics TEXT NOT NULL,
                    recent_context TEXT NOT NULL,
                    last_summarized_message_id TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
                )
                """,
            )
        }
    }

    private fun createDirectivesTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_directives (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """,
            )
        }
    }

    // Lazy singleton repository instances - using _instance suffix to avoid JVM signature clash
    private val _chatSessionRepository: ChatSessionRepository by lazy {
        ChatSessionRepository(this)
    }

    private val _chatMessageRepository: ChatMessageRepository by lazy {
        ChatMessageRepository(this)
    }

    private val _chatFolderRepository: ChatFolderRepository by lazy {
        ChatFolderRepository(this)
    }

    private val _conversationSummaryRepository: ConversationSummaryRepository by lazy {
        ConversationSummaryRepository(this)
    }

    private val _chatDirectiveRepository: ChatDirectiveRepository by lazy {
        ChatDirectiveRepository(this)
    }

    /**
     * Get the singleton ChatSessionRepository instance.
     * All access to chat sessions should go through this repository.
     */
    fun getChatSessionRepository(): ChatSessionRepository = _chatSessionRepository

    /**
     * Get the singleton ChatMessageRepository instance.
     * All access to chat messages should go through this repository.
     */
    fun getChatMessageRepository(): ChatMessageRepository = _chatMessageRepository

    /**
     * Get the singleton ChatFolderRepository instance.
     * All access to chat folders should go through this repository.
     */
    fun getChatFolderRepository(): ChatFolderRepository = _chatFolderRepository

    /**
     * Get the singleton ConversationSummaryRepository instance.
     * All access to conversation summaries should go through this repository.
     */
    fun getConversationSummaryRepository(): ConversationSummaryRepository = _conversationSummaryRepository

    /**
     * Get the singleton ChatDirectiveRepository instance.
     * All access to chat directives should go through this repository.
     */
    fun getChatDirectiveRepository(): ChatDirectiveRepository = _chatDirectiveRepository

    /**
     * Closes the HikariCP connection pool and releases all database resources.
     */
    override fun close() {
        if (!hikariDataSource.isClosed) {
            hikariDataSource.close()
        }
    }

    companion object {
        @Volatile
        private var instance: DatabaseManager? = null

        /**
         * Get the singleton DatabaseManager instance for production use.
         * Uses the default "askimo.db" database file.
         */
        @Synchronized
        fun getInstance(): DatabaseManager = instance ?: DatabaseManager().also { instance = it }

        /**
         * Create a test-scoped DatabaseManager with a unique database file.
         * This allows test isolation by using different database files per test class.
         *
         * @param testScope The test class instance (typically use `this` in companion object)
         * @return A new DatabaseManager instance with a unique database file
         */
        fun getTestInstance(testScope: Any): DatabaseManager {
            val testDbName = "test_${testScope.javaClass.simpleName}_${System.nanoTime()}.db"
            return DatabaseManager(databaseFileName = testDbName)
        }

        /**
         * Reset the singleton instance (for testing purposes only).
         * Closes the current instance and clears the singleton reference.
         */
        @Synchronized
        fun reset() {
            instance?.close()
            instance = null
        }
    }
}
