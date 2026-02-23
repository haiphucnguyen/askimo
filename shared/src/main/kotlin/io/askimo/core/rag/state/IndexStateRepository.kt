/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.state

import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Repository for managing file index state in the database.
 * Replaces the JSON-based IndexStateManager for better scalability.
 */
class IndexStateRepository(
    private val databaseManager: DatabaseManager,
) {
    private val log = logger<IndexStateRepository>()

    private val database: Database by lazy {
        Database.connect(databaseManager.dataSource)
    }

    /**
     * Get all file hashes for a specific project and source type.
     * Returns a map of filePath -> hash.
     */
    fun getHashesForSourceType(
        projectId: String,
        sourceType: String,
    ): Map<String, String> = transaction(database) {
        IndexFileStateTable.selectAll()
            .where {
                (IndexFileStateTable.projectId eq projectId) and
                    (IndexFileStateTable.sourceType eq sourceType)
            }
            .associate { row ->
                row[IndexFileStateTable.filePath] to row[IndexFileStateTable.fileHash]
            }
    }

    /**
     * Batch save file states for better performance.
     * This is much faster than individual inserts for large projects.
     */
    fun batchSaveFileStates(
        projectId: String,
        fileHashes: Map<String, String>,
        sourceType: String,
    ) = transaction(database) {
        // Delete existing entries for this project and source type
        IndexFileStateTable.deleteWhere {
            (IndexFileStateTable.projectId eq projectId) and
                (IndexFileStateTable.sourceType eq sourceType)
        }

        // Batch insert new entries
        if (fileHashes.isNotEmpty()) {
            val now = LocalDateTime.now()
            IndexFileStateTable.batchInsert(fileHashes.entries) { (filePath, hash) ->
                this[IndexFileStateTable.projectId] = projectId
                this[IndexFileStateTable.filePath] = filePath
                this[IndexFileStateTable.fileHash] = hash
                this[IndexFileStateTable.sourceType] = sourceType
                this[IndexFileStateTable.indexedAt] = now
            }
        }

        log.trace("Batch saved ${fileHashes.size} file states for project $projectId, source type $sourceType")
    }

    /**
     * Delete all state for a specific project (all source types).
     */
    fun clearProjectState(projectId: String) = transaction(database) {
        val deleted = IndexFileStateTable.deleteWhere {
            IndexFileStateTable.projectId eq projectId
        }
        log.info("Cleared $deleted file states for project $projectId")
    }
}
