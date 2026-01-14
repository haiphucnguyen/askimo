/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatMessageAttachmentRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.chat.repository.ResourceSegmentRepository
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.util.AskimoHome
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

    private val hikariDataSource: HikariDataSource = createSQLiteDataSource(
        databaseFileName = databaseFileName,
        useInMemory = useInMemory,
    )

    /**
     * Creates a HikariDataSource for a SQLite database file in the Askimo home directory.
     *
     * @param databaseFileName The name of the database file (e.g., "askimo.db")
     * @param useInMemory If true, creates an in-memory database (useful for testing)
     * @return A configured HikariDataSource
     */
    private fun createSQLiteDataSource(
        databaseFileName: String,
        useInMemory: Boolean,
    ): HikariDataSource {
        val jdbcUrl = if (useInMemory) {
            "jdbc:sqlite:file:memdb_${System.nanoTime()}?mode=memory&cache=shared"
        } else {
            val askimoHome = AskimoHome.base()
            if (!askimoHome.toFile().exists()) {
                askimoHome.toFile().mkdirs()
            }
            val dbPath = askimoHome.resolve(databaseFileName).toString()
            "jdbc:sqlite:$dbPath"
        }

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = if (useInMemory) 1 else 10 // Single connection for in-memory
            minimumIdle = if (useInMemory) 1 else 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            connectionInitSql = "PRAGMA foreign_keys = ON;"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(config).also { ds ->
            ds.connection.use { conn ->
                initializeTables(conn)
            }
        }
    }

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
        createProjectsTable(connection)
        createSessionsTable(connection)
        createMessagesTable(connection)
        createAttachmentsTable(connection)
        createSummariesTable(connection)
        createDirectivesTable(connection)
        createSessionMemoryTable(connection)
        createFileSegmentsTable(connection)
    }

    private fun createProjectsTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS projects (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    indexed_paths TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """,
            )
        }
    }

    private fun createSessionsTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA foreign_keys = ON")

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
                    sort_order INTEGER DEFAULT 0
                )
                """,
            )

            // Migration: Add project_id column if it doesn't exist (for existing databases)
            try {
                stmt.executeUpdate(
                    """
                    ALTER TABLE chat_sessions ADD COLUMN project_id TEXT REFERENCES projects(id) ON DELETE CASCADE
                    """,
                )
            } catch (e: Exception) {
            }
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
                    is_edited INTEGER DEFAULT 0,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
                    FOREIGN KEY (edit_parent_id) REFERENCES chat_messages (id) ON DELETE SET NULL
                )
                """,
            )

            // Migration: Add is_edited column if it doesn't exist (for existing databases)
            try {
                stmt.executeUpdate(
                    """
                    ALTER TABLE chat_messages ADD COLUMN is_edited INTEGER DEFAULT 0
                    """,
                )
            } catch (e: Exception) {
                // Column already exists, ignore the error
            }

            // Migration: Add is_failed column if it doesn't exist (for existing databases)
            try {
                stmt.executeUpdate(
                    """
                    ALTER TABLE chat_messages ADD COLUMN is_failed INTEGER DEFAULT 0
                    """,
                )
            } catch (e: Exception) {
                // Column already exists, ignore the error
            }

            // Create composite index for efficient session-based queries with time ordering
            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_session_created
                ON chat_messages (session_id, created_at)
                """,
            )

            // Create composite index for efficient active messages queries
            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_session_outdated_created
                ON chat_messages (session_id, is_outdated, created_at)
                """,
            )
        }
    }

    private fun createAttachmentsTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_message_attachments (
                    id TEXT PRIMARY KEY,
                    message_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (message_id) REFERENCES chat_messages (id) ON DELETE CASCADE,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
                )
                """,
            )

            // Create index for efficient lookups by message_id
            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_attachments_message_id
                ON chat_message_attachments (message_id)
                """,
            )

            // Create index for efficient lookups by session_id
            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_attachments_session_id
                ON chat_message_attachments (session_id)
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
                    name TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """,
            )

            // Migration: Drop unique index on name if it exists (for existing databases)
            try {
                stmt.executeUpdate("DROP INDEX IF EXISTS chat_directives_name")
            } catch (e: Exception) {
                // Ignore - index might not exist
            }
        }
    }

    private fun createSessionMemoryTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS session_memory (
                    session_id TEXT PRIMARY KEY,
                    memory_summary TEXT,
                    memory_messages TEXT NOT NULL,
                    last_updated TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE ON UPDATE CASCADE
                )
                """,
            )
        }
    }

    private fun createFileSegmentsTable(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS file_segments (
                    project_id TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    segment_id TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (project_id, file_path, segment_id),
                    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
                )
                """,
            )

            // Create index for fast lookups by project and file
            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_file_segments_project_file
                ON file_segments (project_id, file_path)
                """,
            )
        }
    }

    private val _chatSessionRepository: ChatSessionRepository by lazy {
        ChatSessionRepository(this)
    }

    private val _chatMessageAttachmentRepository: ChatMessageAttachmentRepository by lazy {
        ChatMessageAttachmentRepository(this)
    }

    private val _chatMessageRepository: ChatMessageRepository by lazy {
        ChatMessageRepository(this, _chatMessageAttachmentRepository)
    }

    private val _chatDirectiveRepository: ChatDirectiveRepository by lazy {
        ChatDirectiveRepository(this)
    }

    private val _sessionMemoryRepository: SessionMemoryRepository by lazy {
        SessionMemoryRepository(this)
    }

    private val _projectRepository: ProjectRepository by lazy {
        ProjectRepository(this)
    }

    private val _resourceSegmentRepository: ResourceSegmentRepository by lazy {
        ResourceSegmentRepository(this)
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
     * Get the singleton ChatMessageAttachmentRepository instance.
     * All access to chat message attachments should go through this repository.
     */
    fun getChatMessageAttachmentRepository(): ChatMessageAttachmentRepository = _chatMessageAttachmentRepository

    /**
     * Get the singleton ChatDirectiveRepository instance.
     * All access to chat directives should go through this repository.
     */
    fun getChatDirectiveRepository(): ChatDirectiveRepository = _chatDirectiveRepository

    /**
     * Get the singleton SessionMemoryRepository instance.
     * All access to session memory should go through this repository.
     */
    fun getSessionMemoryRepository(): SessionMemoryRepository = _sessionMemoryRepository

    /**
     * Get the singleton ProjectRepository instance.
     * All access to projects should go through this repository.
     */
    fun getProjectRepository(): ProjectRepository = _projectRepository

    /**
     * Get the singleton ResourceSegmentRepository instance.
     * All access to resource-segment mappings should go through this repository.
     */
    fun getResourceSegmentRepository(): ResourceSegmentRepository = _resourceSegmentRepository

    /**
     * Get the singleton FileSegmentRepository instance (deprecated - use getResourceSegmentRepository).
     * All access to file-segment mappings should go through this repository.
     * @deprecated Use getResourceSegmentRepository() instead
     */
    @Deprecated("Use getResourceSegmentRepository() instead", ReplaceWith("getResourceSegmentRepository()"))
    fun getFileSegmentRepository(): ResourceSegmentRepository = _resourceSegmentRepository

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
         * Create an in-memory test DatabaseManager.
         * This is useful for environments where SQLite native libraries are not available
         * or for faster test execution without file I/O.
         *
         * @param testScope The test class instance (typically use `this` in companion object)
         * @return A new DatabaseManager instance with an in-memory database
         */
        fun getInMemoryTestInstance(testScope: Any): DatabaseManager {
            val testDbName = "test_${testScope.javaClass.simpleName}_${System.nanoTime()}_memory.db"
            return DatabaseManager(databaseFileName = testDbName, useInMemory = true)
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
