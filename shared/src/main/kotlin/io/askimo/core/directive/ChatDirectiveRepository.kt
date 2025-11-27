/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.directive

import io.askimo.core.db.AbstractSQLiteRepository
import java.sql.Connection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val DIRECTIVE_NAME_MAX_LENGTH = 128
const val DIRECTIVE_CONTENT_MAX_LENGTH = 8192

/**
 * Repository for managing chat directives stored in SQLite database.
 */
class ChatDirectiveRepository(
    useInMemory: Boolean = false,
) : AbstractSQLiteRepository(useInMemory) {
    override val databaseFileName: String = "chat_directives.db"

    override fun initializeDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Check if old table exists
            val oldTableExists = conn.metaData.getTables(null, null, "chat_directives", null).use { rs ->
                rs.next()
            }

            if (oldTableExists) {
                // Check if table has id column
                val hasIdColumn = conn.metaData.getColumns(null, null, "chat_directives", "id").use { rs ->
                    rs.next()
                }

                if (!hasIdColumn) {
                    // Migrate old table to new schema
                    migrateToNewSchema(conn)
                }
            } else {
                // Create new table with id as primary key
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
    }

    private fun migrateToNewSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Create new table with id column
            stmt.executeUpdate(
                """
                CREATE TABLE chat_directives_new (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """,
            )

            // Migrate data from old table to new table, generating UUIDs
            stmt.executeUpdate(
                """
                INSERT INTO chat_directives_new (id, name, content, created_at)
                SELECT
                    lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                          substr(hex(randomblob(2)), 2) || '-' ||
                          substr('89ab', abs(random()) % 4 + 1, 1) ||
                          substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) as id,
                    name,
                    content,
                    created_at
                FROM chat_directives
            """,
            )

            // Drop old table
            stmt.executeUpdate("DROP TABLE chat_directives")

            // Rename new table
            stmt.executeUpdate("ALTER TABLE chat_directives_new RENAME TO chat_directives")
        }
    }

    /**
     * Save a new directive or update existing one.
     * @throws IllegalArgumentException if name or content exceed max length
     */
    fun save(directive: ChatDirective): ChatDirective {
        require(directive.name.length <= DIRECTIVE_NAME_MAX_LENGTH) {
            "Directive name cannot exceed $DIRECTIVE_NAME_MAX_LENGTH characters"
        }
        require(directive.content.length <= DIRECTIVE_CONTENT_MAX_LENGTH) {
            "Directive content cannot exceed $DIRECTIVE_CONTENT_MAX_LENGTH characters"
        }

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO chat_directives (id, name, content, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    content = excluded.content
            """,
            ).use { stmt ->
                stmt.setString(1, directive.id)
                stmt.setString(2, directive.name)
                stmt.setString(3, directive.content)
                stmt.setString(4, directive.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                stmt.executeUpdate()
            }
        }

        return directive
    }

    /**
     * Get a directive by id.
     */
    fun get(id: String): ChatDirective? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, name, content, created_at
                FROM chat_directives
                WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                return if (rs.next()) {
                    ChatDirective(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        content = rs.getString("content"),
                        createdAt = LocalDateTime.parse(rs.getString("created_at")),
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * List all directives, ordered by name.
     */
    fun list(): List<ChatDirective> {
        val directives = mutableListOf<ChatDirective>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    """
                    SELECT id, name, content, created_at
                    FROM chat_directives
                    ORDER BY name ASC
                """,
                )
                while (rs.next()) {
                    directives.add(
                        ChatDirective(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return directives
    }

    /**
     * Update an existing directive.
     * @return true if updated, false if directive doesn't exist
     */
    fun update(directive: ChatDirective): Boolean {
        require(directive.name.length <= DIRECTIVE_NAME_MAX_LENGTH) {
            "Directive name cannot exceed $DIRECTIVE_NAME_MAX_LENGTH characters"
        }
        require(directive.content.length <= DIRECTIVE_CONTENT_MAX_LENGTH) {
            "Directive content cannot exceed $DIRECTIVE_CONTENT_MAX_LENGTH characters"
        }

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE chat_directives
                SET name = ?, content = ?
                WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, directive.name)
                stmt.setString(2, directive.content)
                stmt.setString(3, directive.id)
                val rowsAffected = stmt.executeUpdate()
                return rowsAffected > 0
            }
        }
    }

    /**
     * Delete a directive by id.
     * @return true if deleted, false if directive doesn't exist
     */
    fun delete(id: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                DELETE FROM chat_directives WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, id)
                val rowsAffected = stmt.executeUpdate()
                return rowsAffected > 0
            }
        }
    }

    /**
     * Check if a directive exists by id.
     */
    fun exists(id: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT 1 FROM chat_directives WHERE id = ? LIMIT 1
            """,
            ).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    /**
     * Check if a directive exists by name.
     */
    fun existsByName(name: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT 1 FROM chat_directives WHERE name = ? LIMIT 1
            """,
            ).use { stmt ->
                stmt.setString(1, name)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    /**
     * Get multiple directives by ids.
     */
    fun getByIds(ids: List<String>): List<ChatDirective> {
        if (ids.isEmpty()) return emptyList()

        val directives = mutableListOf<ChatDirective>()
        dataSource.connection.use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            conn.prepareStatement(
                """
                SELECT id, name, content, created_at
                FROM chat_directives
                WHERE id IN ($placeholders)
                ORDER BY name ASC
            """,
            ).use { stmt ->
                ids.forEachIndexed { index, id ->
                    stmt.setString(index + 1, id)
                }
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    directives.add(
                        ChatDirective(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return directives
    }

    /**
     * Get multiple directives by names.
     */
    fun getByNames(names: List<String>): List<ChatDirective> {
        if (names.isEmpty()) return emptyList()

        val directives = mutableListOf<ChatDirective>()
        dataSource.connection.use { conn ->
            val placeholders = names.joinToString(",") { "?" }
            conn.prepareStatement(
                """
                SELECT id, name, content, created_at
                FROM chat_directives
                WHERE name IN ($placeholders)
                ORDER BY name ASC
            """,
            ).use { stmt ->
                names.forEachIndexed { index, name ->
                    stmt.setString(index + 1, name)
                }
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    directives.add(
                        ChatDirective(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            content = rs.getString("content"),
                            createdAt = LocalDateTime.parse(rs.getString("created_at")),
                        ),
                    )
                }
            }
        }
        return directives
    }
}
