/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatFolder
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Exposed table definition for chat_folders.
 */
object ChatFoldersTable : Table("chat_folders") {
    val id = varchar("id", 36)
    val name = varchar("name", 256)
    val parentFolderId = varchar("parent_folder_id", 36).nullable()
    val color = varchar("color", 50).nullable()
    val icon = varchar("icon", 50).nullable()
    val sortOrder = integer("sort_order").default(0)
    val createdAt = sqliteDatetime("created_at")
    val updatedAt = sqliteDatetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Extension function to map an Exposed ResultRow to a ChatFolder object.
 */
private fun ResultRow.toChatFolder(): ChatFolder = ChatFolder(
    id = this[ChatFoldersTable.id],
    name = this[ChatFoldersTable.name],
    parentFolderId = this[ChatFoldersTable.parentFolderId],
    color = this[ChatFoldersTable.color],
    icon = this[ChatFoldersTable.icon],
    sortOrder = this[ChatFoldersTable.sortOrder],
    createdAt = this[ChatFoldersTable.createdAt],
    updatedAt = this[ChatFoldersTable.updatedAt],
)

class ChatFolderRepository internal constructor(
    databaseManager: io.askimo.core.db.DatabaseManager = io.askimo.core.db.DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    /**
     * Create a new folder
     */
    fun createFolder(
        name: String,
        parentFolderId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int = 0,
    ): ChatFolder {
        val folder = ChatFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            parentFolderId = parentFolderId,
            color = color,
            icon = icon,
            sortOrder = sortOrder,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        transaction(database) {
            ChatFoldersTable.insert {
                it[id] = folder.id
                it[ChatFoldersTable.name] = folder.name
                it[ChatFoldersTable.parentFolderId] = folder.parentFolderId
                it[ChatFoldersTable.color] = folder.color
                it[ChatFoldersTable.icon] = folder.icon
                it[ChatFoldersTable.sortOrder] = folder.sortOrder
                it[createdAt] = folder.createdAt
                it[updatedAt] = folder.updatedAt
            }
        }

        return folder
    }

    /**
     * Get all folders
     */
    fun getAllFolders(): List<ChatFolder> = transaction(database) {
        ChatFoldersTable
            .selectAll()
            .orderBy(
                ChatFoldersTable.sortOrder to SortOrder.ASC,
                ChatFoldersTable.name to SortOrder.ASC,
            )
            .map { it.toChatFolder() }
    }

    /**
     * Get a folder by ID
     */
    fun getFolder(folderId: String): ChatFolder? = transaction(database) {
        ChatFoldersTable
            .selectAll()
            .where { ChatFoldersTable.id eq folderId }
            .singleOrNull()
            ?.toChatFolder()
    }

    /**
     * Update folder properties
     */
    fun updateFolder(
        folderId: String,
        name: String? = null,
        parentFolderId: String? = null,
        color: String? = null,
        icon: String? = null,
        sortOrder: Int? = null,
    ): Boolean {
        // Check if there's anything to update
        if (name == null && parentFolderId === null && color === null && icon === null && sortOrder == null) {
            return false
        }

        return transaction(database) {
            ChatFoldersTable.update({ ChatFoldersTable.id eq folderId }) {
                if (name != null) it[ChatFoldersTable.name] = name
                if (parentFolderId !== null) it[ChatFoldersTable.parentFolderId] = parentFolderId
                if (color !== null) it[ChatFoldersTable.color] = color
                if (icon !== null) it[ChatFoldersTable.icon] = icon
                if (sortOrder != null) it[ChatFoldersTable.sortOrder] = sortOrder
                it[updatedAt] = LocalDateTime.now()
            } > 0
        }
    }

    /**
     * Delete a folder
     * Note: Caller should handle moving sessions and child folders before deleting
     */
    fun deleteFolder(folderId: String): Boolean = transaction(database) {
        ChatFoldersTable.deleteWhere { ChatFoldersTable.id eq folderId } > 0
    }

    /**
     * Delete all folders (useful for testing).
     * Deletes all records from the chat_folders table.
     * @return Number of deleted records
     */
    fun deleteAll(): Int = transaction(database) {
        ChatFoldersTable.deleteAll()
    }

    /**
     * Move child folders to root (null parent)
     */
    fun moveChildFoldersToRoot(folderId: String): Int = transaction(database) {
        ChatFoldersTable.update({ ChatFoldersTable.parentFolderId eq folderId }) {
            it[ChatFoldersTable.parentFolderId] = null
        }
    }
}
