/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.chat.domain.ChatDirectivesTable
import io.askimo.core.chat.domain.ChatSessionsTable
import io.askimo.core.chat.domain.DIRECTIVE_CONTENT_MAX_LENGTH
import io.askimo.core.chat.domain.DIRECTIVE_NAME_MAX_LENGTH
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.DirectiveDeletedEvent
import io.askimo.core.event.internal.PushDataToServerEvent
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDateTime

private fun ResultRow.toChatDirective(): ChatDirective = ChatDirective(
    id = this[ChatDirectivesTable.id],
    name = this[ChatDirectivesTable.name],
    content = this[ChatDirectivesTable.content],
    createdAt = this[ChatDirectivesTable.createdAt],
    updatedAt = this[ChatDirectivesTable.updatedAt],
    deletedAt = this[ChatDirectivesTable.deletedAt],
)

/**
 * Repository for managing chat directives stored in SQLite database.
 */
class ChatDirectiveRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    /**
     * Save a new directive or update existing one.
     * Bumps updatedAt so the change is detected as unsynced.
     */
    fun save(directive: ChatDirective): ChatDirective {
        require(directive.name.length <= DIRECTIVE_NAME_MAX_LENGTH) {
            "Directive name cannot exceed $DIRECTIVE_NAME_MAX_LENGTH characters"
        }
        require(directive.content.length <= DIRECTIVE_CONTENT_MAX_LENGTH) {
            "Directive content cannot exceed $DIRECTIVE_CONTENT_MAX_LENGTH characters"
        }

        val now = LocalDateTime.now()
        val saved = directive.copy(updatedAt = now)

        transaction(database) {
            ChatDirectivesTable.upsert {
                it[id] = saved.id
                it[name] = saved.name
                it[content] = saved.content
                it[createdAt] = saved.createdAt
                it[updatedAt] = saved.updatedAt
                it[deletedAt] = saved.deletedAt
            }
        }

        EventBus.post(PushDataToServerEvent(reason = "directive saved"))
        return saved
    }

    /** Get a directive by id (non-deleted only). */
    fun get(id: String): ChatDirective? = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { (ChatDirectivesTable.id eq id) and (ChatDirectivesTable.deletedAt.isNull()) }
            .singleOrNull()
            ?.toChatDirective()
    }

    /** List all active (non-deleted) directives, ordered by name. */
    fun list(): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { ChatDirectivesTable.deletedAt.isNull() }
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

        val updated = transaction(database) {
            ChatDirectivesTable.update({ ChatDirectivesTable.id eq directive.id }) {
                it[name] = directive.name
                it[content] = directive.content
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }

        if (updated) EventBus.post(PushDataToServerEvent(reason = "directive updated"))
        return updated
    }

    /**
     * Soft-delete a directive by id.
     * Sets deletedAt so the deletion propagates to other devices via push/pull.
     * @return true if the row existed and was marked deleted
     */
    fun delete(id: String): Boolean {
        val deleted = transaction(database) {
            ChatDirectivesTable.update({ ChatDirectivesTable.id eq id }) {
                it[deletedAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
        if (deleted) {
            EventBus.post(PushDataToServerEvent(reason = "directive deleted"))
            EventBus.post(DirectiveDeletedEvent(directiveId = id))
        }
        return deleted
    }

    /**
     * Hard-delete a directive by id.
     * Used only when applying a server-side soft-delete locally (pull path).
     * Does NOT emit any events so it cannot trigger a push loop.
     */
    fun hardDelete(id: String): Boolean = transaction(database) {
        ChatDirectivesTable.update({ ChatDirectivesTable.id eq id }) {
            it[deletedAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    /** Check if a directive exists by id (including soft-deleted). */
    fun exists(id: String): Boolean = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { ChatDirectivesTable.id eq id }
            .limit(1)
            .count() > 0
    }

    /** Get multiple non-deleted directives by ids. */
    fun getByIds(ids: List<String>): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { (ChatDirectivesTable.id inList ids) and (ChatDirectivesTable.deletedAt.isNull()) }
            .orderBy(ChatDirectivesTable.name to SortOrder.ASC)
            .map { it.toChatDirective() }
    }

    /** Get multiple non-deleted directives by names. */
    fun getByNames(names: List<String>): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .where { (ChatDirectivesTable.name inList names) and (ChatDirectivesTable.deletedAt.isNull()) }
            .orderBy(ChatDirectivesTable.name to SortOrder.ASC)
            .map { it.toChatDirective() }
    }

    /**
     * Find a directive by session ID via the session's directiveId foreign key.
     */
    fun findDirectiveBySessionId(sessionId: String): ChatDirective? = transaction(database) {
        ChatDirectivesTable
            .join(ChatSessionsTable, JoinType.INNER, ChatDirectivesTable.id, ChatSessionsTable.directiveId)
            .selectAll()
            .where { ChatSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.toChatDirective()
    }

    /**
     * Returns directives that have never been pushed to the sync server (syncedAt IS NULL)
     * or were locally modified after the last push (updatedAt > syncedAt).
     * Includes soft-deleted rows so deletions propagate to the server.
     */
    fun getUnsyncedDirectives(limit: Int = 100): List<ChatDirective> = transaction(database) {
        ChatDirectivesTable
            .selectAll()
            .orderBy(ChatDirectivesTable.updatedAt, SortOrder.ASC)
            .mapNotNull { row ->
                val syncedAt = row[ChatDirectivesTable.syncedAt]
                val updatedAt = row[ChatDirectivesTable.updatedAt].toString()
                if (syncedAt == null || updatedAt > syncedAt) row.toChatDirective() else null
            }
            .take(limit)
    }

    /** Mark a directive as successfully synced to the server. */
    fun markSynced(directiveId: String): Boolean = transaction(database) {
        ChatDirectivesTable.update({ ChatDirectivesTable.id eq directiveId }) {
            it[syncedAt] = LocalDateTime.now().toString()
        } > 0
    }

    /**
     * Upserts a batch of directives received from the server (pull response).
     * Last-writer-wins on updatedAt. Does NOT emit events — this is a pull path.
     */
    fun upsertFromServer(directives: List<ChatDirective>) {
        if (directives.isEmpty()) return
        transaction(database) {
            val nowStr = LocalDateTime.now().toString()
            val existingById = ChatDirectivesTable
                .selectAll()
                .where { ChatDirectivesTable.id inList directives.map { it.id } }
                .associate { row -> row[ChatDirectivesTable.id] to row[ChatDirectivesTable.updatedAt] }

            for (directive in directives) {
                val storedUpdatedAt = existingById[directive.id]
                if (storedUpdatedAt == null) {
                    ChatDirectivesTable.insert {
                        it[id] = directive.id
                        it[name] = directive.name
                        it[content] = directive.content
                        it[createdAt] = directive.createdAt
                        it[updatedAt] = directive.updatedAt
                        it[deletedAt] = directive.deletedAt
                        it[syncedAt] = nowStr
                    }
                } else if (directive.updatedAt.isAfter(storedUpdatedAt)) {
                    ChatDirectivesTable.update({ ChatDirectivesTable.id eq directive.id }) {
                        it[name] = directive.name
                        it[content] = directive.content
                        it[updatedAt] = directive.updatedAt
                        it[deletedAt] = directive.deletedAt
                        it[syncedAt] = nowStr
                    }
                }
            }
        }
    }
}
