/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

/**
 * Represents a project that groups chat sessions and provides RAG context
 * through indexed files/folders.
 *
 * Projects enable knowledge base organization:
 * - Each project has its own Lucene index for RAG
 * - Sessions belong to projects to share project-level context
 * - Indexed paths are used for semantic search across conversations
 */
data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val indexedPaths: String, // JSON array of paths, e.g., "['/path/to/folder1', '/path/to/folder2']"
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * Exposed table definition for projects.
 * Co-located with domain class for easier maintenance and foreign key references.
 */
object ProjectsTable : Table("projects") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val indexedPaths = text("indexed_paths") // JSON array as text
    val createdAt = sqliteDatetime("created_at")
    val updatedAt = sqliteDatetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
