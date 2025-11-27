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
     * @return A configured HikariDataSource
     */
    fun createSQLiteDataSource(
        databaseFileName: String,
        initializeDatabase: ((Connection) -> Unit)? = null,
    ): HikariDataSource {
        val dbPath = AskimoHome.base().resolve(databaseFileName).toString()

        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$dbPath"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 10
            minimumIdle = 2
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
