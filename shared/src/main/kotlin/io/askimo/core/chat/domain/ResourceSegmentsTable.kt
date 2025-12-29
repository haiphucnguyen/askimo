/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.Table

object ResourceSegmentsTable : Table("file_segments") {
    val projectId = varchar("project_id", 256) // Project ID - controlled identifier
    val resourceId = varchar("file_path", 2048) // Resource identifier (file path, URL, etc.) - column name kept as 'file_path' for backward compatibility
    val segmentId = varchar("segment_id", 256) // Unique ID of the segment in the embedding store (UUID/hash)
    val chunkIndex = integer("chunk_index") // Index of this chunk within the resource (for ordering)
    val createdAt = sqliteDatetime("created_at") // When this segment was created

    override val primaryKey = PrimaryKey(projectId, resourceId, segmentId)

    init {
        index(isUnique = false, projectId, resourceId)
    }
}
