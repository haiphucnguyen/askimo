/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.state

import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

/**
 * Manages the persisted state of an index.
 * Now uses database-backed storage instead of JSON files for better scalability.
 */
class IndexStateManager(
    private val projectId: String,
    private val sourceType: String, // 'folders', 'files', or 'urls'
) {
    private val log = logger<IndexStateManager>()
    private val repository = IndexStateRepository(DatabaseManager.getInstance())

    /**
     * Load persisted state from database
     */
    fun loadPersistedState(): IndexPersistedState? {
        return try {
            val fileHashes = repository.getHashesForSourceType(projectId, sourceType)

            if (fileHashes.isEmpty()) {
                log.debug("No persisted state found for project $projectId, source type $sourceType")
                return null
            }

            IndexPersistedState(
                totalFilesIndexed = fileHashes.size,
                lastIndexedTimestamp = System.currentTimeMillis(), // Not stored in DB anymore
                fileHashes = fileHashes,
            )
        } catch (e: Exception) {
            log.error("Failed to load persisted state for project $projectId, source type $sourceType", e)
            null
        }
    }

    /**
     * Save state to database using batch insert for performance
     */
    fun saveState(
        totalFilesIndexed: Int,
        fileHashes: Map<String, String>,
    ) {
        try {
            repository.batchSaveFileStates(projectId, fileHashes, sourceType)
            log.debug("Saved index state for project $projectId, source type $sourceType: $totalFilesIndexed files")
        } catch (e: Exception) {
            log.error("Failed to save index state for project $projectId, source type $sourceType", e)
        }
    }

    /**
     * Calculate MD5 hash of a file
     */
    fun calculateFileHash(filePath: Path): String = try {
        val bytes = filePath.readBytes()
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        hash.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        log.warn("Failed to calculate hash for ${filePath.fileName}: ${e.message}")
        ""
    }

    /**
     * Clear all index states for this project.
     * Deletes all records that have the projectId field equals to this projectId.
     */
    fun clearStates() {
        try {
            repository.clearProjectState(projectId)
            log.info("Cleared all index states for project $projectId")
        } catch (e: Exception) {
            log.error("Failed to clear index states for project $projectId", e)
        }
    }
}
