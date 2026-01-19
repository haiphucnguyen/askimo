/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.util.AskimoHome
import java.nio.file.Path

/**
 * Factory for creating SQLite DataSources with HikariCP connection pooling.
 * Provides consistent configuration across the application.
 */
object SQLiteDataSourceFactory {

    /**
     * Creates a HikariDataSource for a SQLite database file.
     *
     * @param dbPath The absolute path to the database file
     * @param maxPoolSize Maximum number of connections in the pool (default: 10)
     * @param minIdle Minimum number of idle connections (default: 2)
     * @param enableForeignKeys Whether to enable foreign key constraints (default: true)
     * @return A configured HikariDataSource
     */
    fun create(
        dbPath: Path,
        maxPoolSize: Int = 10,
        minIdle: Int = 2,
        enableForeignKeys: Boolean = true,
    ): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$dbPath"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = maxPoolSize
            minimumIdle = minIdle
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            if (enableForeignKeys) {
                connectionInitSql = "PRAGMA foreign_keys = ON;"
            }
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(config)
    }

    /**
     * Creates a HikariDataSource for a SQLite database file in the Askimo home directory.
     *
     * @param databaseFileName The name of the database file (e.g., "askimo.db")
     * @param maxPoolSize Maximum number of connections in the pool (default: 10)
     * @param minIdle Minimum number of idle connections (default: 2)
     * @param enableForeignKeys Whether to enable foreign key constraints (default: true)
     * @return A configured HikariDataSource
     */
    fun createInHome(
        databaseFileName: String,
        maxPoolSize: Int = 10,
        minIdle: Int = 2,
        enableForeignKeys: Boolean = true,
    ): HikariDataSource {
        val dbPath = AskimoHome.base().resolve(databaseFileName)
        return create(dbPath, maxPoolSize, minIdle, enableForeignKeys)
    }

    /**
     * Creates an in-memory HikariDataSource for testing.
     * Uses a unique database name to ensure test isolation.
     *
     * @param testScope The test class instance for generating unique names
     * @return A configured in-memory HikariDataSource
     */
    fun createInMemory(testScope: Any): HikariDataSource {
        val jdbcUrl = "jdbc:sqlite:file:memdb_${testScope.javaClass.simpleName}_${System.nanoTime()}?mode=memory&cache=shared"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1 // Single connection for in-memory
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            connectionInitSql = "PRAGMA foreign_keys = ON;"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(config)
    }

    /**
     * Creates a single-connection HikariDataSource for project-specific databases.
     * This is suitable for RAG index state databases that don't need connection pooling.
     *
     * @param dbPath The absolute path to the database file
     * @param enableForeignKeys Whether to enable foreign key constraints (default: false)
     * @return A configured HikariDataSource with a single connection
     */
    fun createSingleConnection(
        dbPath: Path,
        enableForeignKeys: Boolean = false,
    ): HikariDataSource = create(
        dbPath = dbPath,
        maxPoolSize = 1,
        minIdle = 1,
        enableForeignKeys = enableForeignKeys,
    )
}
