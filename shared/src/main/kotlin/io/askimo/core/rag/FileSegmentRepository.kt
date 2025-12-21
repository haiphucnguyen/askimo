/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import io.askimo.core.db.DatabaseManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Repository for managing file-to-segment mappings in the embedding store.
 * Tracks which segment IDs belong to which files so they can be removed when files are deleted.
 *
 * Follows the DatabaseManager pattern - table creation is handled by DatabaseManager.
 */
class FileSegmentRepository(
    private val databaseManager: DatabaseManager,
) {
    private val database: Database by lazy { Database.connect(databaseManager.dataSource) }

    /**
     * Save multiple segment mappings in a batch
     */
    fun saveSegmentMappings(
        projectId: String,
        filePath: Path,
        segmentIds: List<Pair<String, Int>>,
    ) {
        transaction(database) {
            val absolutePath = filePath.toString()
            for ((segmentId, chunkIndex) in segmentIds) {
                FileSegmentsTable.insert {
                    it[FileSegmentsTable.projectId] = projectId
                    it[FileSegmentsTable.filePath] = absolutePath
                    it[FileSegmentsTable.segmentId] = segmentId
                    it[FileSegmentsTable.chunkIndex] = chunkIndex
                    it[FileSegmentsTable.createdAt] = LocalDateTime.now()
                }
            }
        }
    }

    /**
     * Get all segment IDs for a specific file
     */
    fun getSegmentIdsForFile(
        projectId: String,
        filePath: Path,
    ): List<String> = transaction(database) {
        FileSegmentsTable
            .selectAll()
            .where {
                (FileSegmentsTable.projectId eq projectId) and
                    (FileSegmentsTable.filePath eq filePath.toString())
            }
            .orderBy(FileSegmentsTable.chunkIndex)
            .map { it[FileSegmentsTable.segmentId] }
    }

    /**
     * Remove all segment mappings for a specific file
     */
    fun removeSegmentMappingsForFile(
        projectId: String,
        filePath: Path,
    ): Int = transaction(database) {
        FileSegmentsTable.deleteWhere {
            (FileSegmentsTable.projectId eq projectId) and
                (FileSegmentsTable.filePath eq filePath.toString())
        }
    }
}
