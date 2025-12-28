/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ResourceSegmentsTable
import io.askimo.core.db.DatabaseManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Repository for managing resource-to-segment mappings in the embedding store.
 * Tracks which segment IDs belong to which resources (files, URLs, etc.) so they can be removed when resources are deleted.
 *
 * Note: "resourceId" is a string identifier that can represent:
 * - File paths (for local files) - converted from Path.toString()
 * - URLs (for web pages)
 * - Document IDs (for SEC filings, etc.)
 *
 * Follows the DatabaseManager pattern - table creation is handled by DatabaseManager.
 */
class ResourceSegmentRepository(
    private val databaseManager: DatabaseManager,
) {
    private val database: Database by lazy { Database.connect(databaseManager.dataSource) }

    /**
     * Save multiple segment mappings in a batch
     * @param resourceId String identifier for the resource (file path, URL, etc.)
     */
    fun saveSegmentMappings(
        projectId: String,
        resourceId: String,
        segmentIds: List<Pair<String, Int>>,
    ) {
        if (segmentIds.isEmpty()) return

        transaction(database) {
            ResourceSegmentsTable.batchInsert(
                data = segmentIds,
                ignore = true,
            ) { (segmentId, chunkIndex) ->
                this[ResourceSegmentsTable.projectId] = projectId
                this[ResourceSegmentsTable.resourceId] = resourceId
                this[ResourceSegmentsTable.segmentId] = segmentId
                this[ResourceSegmentsTable.chunkIndex] = chunkIndex
                this[ResourceSegmentsTable.createdAt] = LocalDateTime.now()
            }
        }
    }

    /**
     * Save multiple segment mappings in a batch (backward compatible Path version)
     * Converts Path to String automatically for backward compatibility
     */
    fun saveSegmentMappings(
        projectId: String,
        filePath: Path,
        segmentIds: List<Pair<String, Int>>,
    ) {
        saveSegmentMappings(projectId, filePath.toString(), segmentIds)
    }

    /**
     * Get all segment IDs for a specific resource
     * @param resourceId String identifier for the resource
     */
    fun getSegmentIdsForResource(
        projectId: String,
        resourceId: String,
    ): List<String> = transaction(database) {
        ResourceSegmentsTable
            .selectAll()
            .where {
                (ResourceSegmentsTable.projectId eq projectId) and
                    (ResourceSegmentsTable.resourceId eq resourceId)
            }
            .orderBy(ResourceSegmentsTable.chunkIndex)
            .map { it[ResourceSegmentsTable.segmentId] }
    }

    /**
     * Get all segment IDs for a specific file (backward compatible Path version)
     */
    fun getSegmentIdsForFile(
        projectId: String,
        filePath: Path,
    ): List<String> = getSegmentIdsForResource(projectId, filePath.toString())

    /**
     * Remove all segment mappings for a specific resource
     * @param resourceId String identifier for the resource
     */
    fun removeSegmentMappingsForResource(
        projectId: String,
        resourceId: String,
    ): Int = transaction(database) {
        ResourceSegmentsTable.deleteWhere {
            (ResourceSegmentsTable.projectId eq projectId) and
                (ResourceSegmentsTable.resourceId eq resourceId)
        }
    }

    /**
     * Remove all segment mappings for a specific file (backward compatible Path version)
     */
    fun removeSegmentMappingsForFile(
        projectId: String,
        filePath: Path,
    ): Int = removeSegmentMappingsForResource(projectId, filePath.toString())
}
