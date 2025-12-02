/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

const val DIRECTIVE_NAME_MAX_LENGTH = 128
const val DIRECTIVE_CONTENT_MAX_LENGTH = 8192

/**
 * Exposed table definition for chat_directives.
 */
object ChatDirectivesTable : Table("chat_directives") {
    val id = varchar("id", 36)
    val name = varchar("name", DIRECTIVE_NAME_MAX_LENGTH)
    val content = varchar("content", DIRECTIVE_CONTENT_MAX_LENGTH)
    val createdAt = sqliteDatetime("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(name)
    }
}

/**
 * Extension function to map an Exposed ResultRow to a ChatDirective object.
 * Eliminates duplication of mapping logic throughout the repository.
 */
private fun ResultRow.toChatDirective(): ChatDirective = ChatDirective(
    id = this[ChatDirectivesTable.id],
    name = this[ChatDirectivesTable.name],
    content = this[ChatDirectivesTable.content],
    createdAt = this[ChatDirectivesTable.createdAt],
)

/**
 * Repository for managing chat directives stored in SQLite database.
 */
class ChatDirectiveRepository internal constructor(
    databaseManager: io.askimo.core.db.DatabaseManager = io.askimo.core.db.DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

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
            ?.toChatDirective()
    }

    /**
     * List all directives, ordered by name.
     */
    fun list(): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .orderBy(ChatDirectivesTable.name to SortOrder.ASC)
            .map { it.toChatDirective() }
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
    ) { it.toChatDirective() }

    /**
     * Get multiple directives by names.
     */
    fun getByNames(names: List<String>): List<ChatDirective> = getByColumn(
        table = ChatDirectivesTable,
        column = ChatDirectivesTable.name,
        values = names,
        orderBy = ChatDirectivesTable.name to SortOrder.ASC,
    ) { it.toChatDirective() }
}
