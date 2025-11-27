/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

/**
 * Abstract base class for SQLite repositories using HikariCP connection pooling.
 *
 * This class provides common functionality for:
 * - Lazy initialization of HikariCP datasource
 * - Proper resource cleanup via AutoCloseable
 * - Support for in-memory databases (useful for testing)
 * - Database initialization on first connection
 *
 * Subclasses must implement:
 * - [databaseFileName] - The name of the SQLite database file
 * - [initializeDatabase] - Database schema initialization logic
 *
 * @param useInMemory If true, uses an in-memory database instead of file-based storage.
 *                    Useful for fast, isolated testing. Defaults to false.
 */
abstract class AbstractSQLiteRepository(
    private val useInMemory: Boolean = false,
) : AutoCloseable {
    /**
     * The name of the SQLite database file (e.g., "chat_sessions.db").
     * Ignored if [useInMemory] is true.
     */
    protected abstract val databaseFileName: String

    /**
     * Initialize the database schema. Called automatically on first connection.
     *
     * This method should create all necessary tables, indexes, and initial data.
     * It's only called once per repository instance, on the first database access.
     *
     * @param conn An open database connection for executing initialization SQL
     */
    protected abstract fun initializeDatabase(conn: Connection)

    /**
     * Lazy-initialized HikariCP datasource.
     * Created on first access, using the configured database file or in-memory mode.
     */
    private val hikariDataSource: HikariDataSource by lazy {
        DatabaseConnectionFactory.createSQLiteDataSource(
            databaseFileName = databaseFileName,
            initializeDatabase = ::initializeDatabase,
            useInMemory = useInMemory,
        )
    }

    /**
     * The datasource for obtaining database connections.
     * Protected to allow subclasses to access for query execution.
     */
    protected val dataSource: DataSource get() = hikariDataSource

    /**
     * Closes the HikariCP connection pool and releases all database resources.
     *
     * This method is called automatically when using the repository in a `use { }` block.
     * It's safe to call multiple times or even if the datasource was never initialized.
     */
    override fun close() {
        try {
            if (!hikariDataSource.isClosed) {
                hikariDataSource.close()
            }
        } catch (_: UninitializedPropertyAccessException) {
            // DataSource was never initialized, nothing to close
        }
    }
}
