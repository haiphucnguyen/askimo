/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.state

import io.askimo.core.logging.logger
import io.askimo.core.rag.RagUtils
import io.askimo.core.util.JsonUtils.json
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Manages the persisted state of an index
 */
class IndexStateManager(
    private val projectId: String,
) {
    private val log = logger<IndexStateManager>()
    private val stateFile = RagUtils.getProjectIndexDir(projectId).resolve("index-state.json")

    @Serializable
    private data class PersistedIndexState(
        val totalFilesIndexed: Int,
        val lastIndexedTimestamp: Long,
        val fileHashes: Map<String, String>,
    )

    /**
     * Load persisted state from disk
     */
    fun loadPersistedState(): IndexPersistedState? {
        return try {
            if (!stateFile.exists()) {
                log.debug("No persisted state found for project $projectId")
                return null
            }

            val jsonContent = stateFile.readText()
            val state = json.decodeFromString<PersistedIndexState>(jsonContent)

            IndexPersistedState(
                totalFilesIndexed = state.totalFilesIndexed,
                lastIndexedTimestamp = state.lastIndexedTimestamp,
                fileHashes = state.fileHashes,
            )
        } catch (e: Exception) {
            log.error("Failed to load persisted state for project $projectId", e)
            null
        }
    }

    /**
     * Save state to disk
     */
    fun saveState(
        totalFilesIndexed: Int,
        fileHashes: Map<String, String>,
    ) {
        try {
            val state = PersistedIndexState(
                totalFilesIndexed = totalFilesIndexed,
                lastIndexedTimestamp = System.currentTimeMillis(),
                fileHashes = fileHashes,
            )

            val jsonContent = json.encodeToString(state)
            stateFile.writeText(jsonContent)

            log.debug("Saved index state for project $projectId: $totalFilesIndexed files")
        } catch (e: Exception) {
            log.error("Failed to save index state for project $projectId", e)
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
}
