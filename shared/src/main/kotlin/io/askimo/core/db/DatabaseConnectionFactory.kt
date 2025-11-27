/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.util.AskimoHome
import java.sql.Connection

/**
 * Factory for creating HikariCP datasources for SQLite databases.
 */
object DatabaseConnectionFactory {
    /**
     * Creates a HikariDataSource for a SQLite database file in the Askimo home directory.
     *
     * @param databaseFileName The name of the database file (e.g., "chat_sessions.db")
     * @param initializeDatabase Optional callback to initialize the database schema
     * @param useInMemory If true, creates an in-memory database (useful for testing)
     * @return A configured HikariDataSource
     */
    fun createSQLiteDataSource(
        databaseFileName: String,
        initializeDatabase: ((Connection) -> Unit)? = null,
        useInMemory: Boolean = false,
    ): HikariDataSource {
        val jdbcUrl = if (useInMemory) {
            // Use in-memory database for tests to avoid file locking issues on Windows
            // Use file URI with cache=shared so all connections in the pool share the same database
            // Each repository instance will have its own isolated in-memory database
            "jdbc:sqlite:file:memdb_${System.nanoTime()}?mode=memory&cache=shared"
        } else {
            val dbPath = AskimoHome.base().resolve(databaseFileName).toString()
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
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(config).also { ds ->
            initializeDatabase?.let { init ->
                ds.connection.use { conn ->
                    init(conn)
                }
            }
        }
    }
}
