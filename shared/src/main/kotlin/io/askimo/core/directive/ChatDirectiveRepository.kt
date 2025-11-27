/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.directive

import io.askimo.core.db.AbstractSQLiteRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.sql.Connection

const val DIRECTIVE_NAME_MAX_LENGTH = 128
const val DIRECTIVE_CONTENT_MAX_LENGTH = 8192

/**
 * Exposed table definition for chat_directives.
 */
object ChatDirectivesTable : Table("chat_directives") {
    val id = varchar("id", 36)
    val name = varchar("name", DIRECTIVE_NAME_MAX_LENGTH)
    val content = varchar("content", DIRECTIVE_CONTENT_MAX_LENGTH)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(name)
    }
}

/**
 * Repository for managing chat directives stored in SQLite database.
 */
class ChatDirectiveRepository(
    useInMemory: Boolean = false,
) : AbstractSQLiteRepository(useInMemory) {
    override val databaseFileName: String = "chat_directives.db"

    private val database by lazy {
        Database.connect(dataSource)
    }

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

        transaction(database) {
            ChatDirectivesTable.upsert {
                it[id] = directive.id
                it[name] = directive.name
                it[content] = directive.content
                it[createdAt] = directive.createdAt
            }
        }

        return directive
    }

    /**
     * Get a directive by id.
     */
    fun get(id: String): ChatDirective? = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { ChatDirectivesTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                ChatDirective(
                    id = row[ChatDirectivesTable.id],
                    name = row[ChatDirectivesTable.name],
                    content = row[ChatDirectivesTable.content],
                    createdAt = row[ChatDirectivesTable.createdAt],
                )
            }
    }

    /**
     * List all directives, ordered by name.
     */
    fun list(): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .orderBy(ChatDirectivesTable.name to SortOrder.ASC)
            .map { row ->
                ChatDirective(
                    id = row[ChatDirectivesTable.id],
                    name = row[ChatDirectivesTable.name],
                    content = row[ChatDirectivesTable.content],
                    createdAt = row[ChatDirectivesTable.createdAt],
                )
            }
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

        return transaction(database) {
            ChatDirectivesTable.update({ ChatDirectivesTable.id eq directive.id }) {
                it[name] = directive.name
                it[content] = directive.content
            } > 0
        }
    }

    /**
     * Delete a directive by id.
     * @return true if deleted, false if directive doesn't exist
     */
    fun delete(id: String): Boolean = transaction(database) {
        ChatDirectivesTable.deleteWhere { ChatDirectivesTable.id eq id } > 0
    }

    /**
     * Check if a directive exists by id.
     */
    fun exists(id: String): Boolean = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { ChatDirectivesTable.id eq id }
            .limit(1)
            .count() > 0
    }

    /**
     * Check if a directive exists by name.
     */
    fun existsByName(name: String): Boolean = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { ChatDirectivesTable.name eq name }
            .limit(1)
            .count() > 0
    }

    /**
     * Get multiple directives by ids.
     */
    fun getByIds(ids: List<String>): List<ChatDirective> = getByColumn(
        table = ChatDirectivesTable,
        column = ChatDirectivesTable.id,
        values = ids,
        orderBy = ChatDirectivesTable.name to SortOrder.ASC,
    ) { row ->
        ChatDirective(
            id = row[ChatDirectivesTable.id],
            name = row[ChatDirectivesTable.name],
            content = row[ChatDirectivesTable.content],
            createdAt = row[ChatDirectivesTable.createdAt],
        )
    }

    /**
     * Get multiple directives by names.
     */
    fun getByNames(names: List<String>): List<ChatDirective> = getByColumn(
        table = ChatDirectivesTable,
        column = ChatDirectivesTable.name,
        values = names,
        orderBy = ChatDirectivesTable.name to SortOrder.ASC,
    ) { row ->
        ChatDirective(
            id = row[ChatDirectivesTable.id],
            name = row[ChatDirectivesTable.name],
            content = row[ChatDirectivesTable.content],
            createdAt = row[ChatDirectivesTable.createdAt],
        )
    }
}
