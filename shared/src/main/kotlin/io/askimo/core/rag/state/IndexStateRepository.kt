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
import org.jetbrains.exposed.sql.upsert
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
     * Get the hash of a specific file for a project and source type.
     */
    fun getFileHash(
        projectId: String,
        filePath: String,
        sourceType: String,
    ): String? = transaction(database) {
        IndexFileStateTable.selectAll()
            .where {
                (IndexFileStateTable.projectId eq projectId) and
                    (IndexFileStateTable.filePath eq filePath) and
                    (IndexFileStateTable.sourceType eq sourceType)
            }
            .singleOrNull()
            ?.get(IndexFileStateTable.fileHash)
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
     * Save or update the state for a single file.
     */
    fun saveFileState(
        projectId: String,
        filePath: String,
        hash: String,
        sourceType: String,
    ) = transaction(database) {
        IndexFileStateTable.upsert {
            it[IndexFileStateTable.projectId] = projectId
            it[IndexFileStateTable.filePath] = filePath
            it[IndexFileStateTable.fileHash] = hash
            it[IndexFileStateTable.sourceType] = sourceType
            it[IndexFileStateTable.indexedAt] = LocalDateTime.now()
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

        log.debug("Batch saved ${fileHashes.size} file states for project $projectId, source type $sourceType")
    }

    /**
     * Remove file states for deleted files.
     */
    fun removeDeletedFiles(
        projectId: String,
        filePaths: List<String>,
        sourceType: String,
    ) = transaction(database) {
        var deleted = 0
        for (filePath in filePaths) {
            deleted += IndexFileStateTable.deleteWhere {
                (IndexFileStateTable.projectId eq projectId) and
                    (IndexFileStateTable.sourceType eq sourceType) and
                    (IndexFileStateTable.filePath eq filePath)
            }
        }
        log.debug("Removed $deleted deleted file states for project $projectId, source type $sourceType")
    }

    /**
     * Get the count of indexed files for a project and source type.
     */
    fun getFileCount(
        projectId: String,
        sourceType: String,
    ): Int = transaction(database) {
        IndexFileStateTable.selectAll()
            .where {
                (IndexFileStateTable.projectId eq projectId) and
                    (IndexFileStateTable.sourceType eq sourceType)
            }
            .count()
            .toInt()
    }

    /**
     * Get all file paths for a project and source type.
     */
    fun getAllFilePaths(
        projectId: String,
        sourceType: String,
    ): List<String> = transaction(database) {
        IndexFileStateTable.selectAll()
            .where {
                (IndexFileStateTable.projectId eq projectId) and
                    (IndexFileStateTable.sourceType eq sourceType)
            }
            .map { it[IndexFileStateTable.filePath] }
    }

    /**
     * Delete all state for a specific project and source type.
     */
    fun clearProjectSourceState(
        projectId: String,
        sourceType: String,
    ) = transaction(database) {
        val deleted = IndexFileStateTable.deleteWhere {
            (IndexFileStateTable.projectId eq projectId) and
                (IndexFileStateTable.sourceType eq sourceType)
        }
        log.info("Cleared $deleted file states for project $projectId, source type $sourceType")
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
