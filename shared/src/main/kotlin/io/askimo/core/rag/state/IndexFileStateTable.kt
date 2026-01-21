/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.state

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.Table

/**
 * Table for storing file indexing state per project.
 * This replaces the JSON-based state file to handle large projects efficiently.
 */
object IndexFileStateTable : Table("index_file_state") {
    val projectId = varchar("project_id", 36)
    val filePath = text("file_path") // Absolute file path or URL
    val fileHash = varchar("file_hash", 64).index() // MD5 hash, indexed for fast lookups
    val sourceType = varchar("source_type", 20) // 'folders', 'files', or 'urls'
    val indexedAt = sqliteDatetime("indexed_at")

    override val primaryKey = PrimaryKey(projectId, filePath)

    init {
        index(isUnique = false, projectId, sourceType)
    }
}
