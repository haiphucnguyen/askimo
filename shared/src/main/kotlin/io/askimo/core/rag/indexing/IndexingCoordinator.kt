/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.user.IndexingInProgressEvent
import io.askimo.core.logging.logger
import io.askimo.core.rag.filter.FilterChain
import io.askimo.core.rag.state.IndexProgress
import io.askimo.core.rag.state.IndexStateManager
import io.askimo.core.rag.state.IndexStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Coordinates the indexing process for a project
 */
class IndexingCoordinator(
    private val projectId: String,
    private val projectName: String,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val appContext: AppContext,
) {
    private val log = logger<IndexingCoordinator>()

    private val fileProcessor = FileProcessor(appContext)
    private val stateManager = IndexStateManager(projectId)
    private val hybridIndexer = HybridIndexer(embeddingStore, embeddingModel, projectId)

    private val _progress = MutableStateFlow(IndexProgress())
    val progress: StateFlow<IndexProgress> = _progress

    private val filterChain: FilterChain = FilterChain.DEFAULT

    /**
     * Check if a path should be excluded from indexing using the filter chain.
     * Each path is evaluated independently - supports multiple unrelated paths.
     */
    private fun shouldExcludePath(path: Path): Boolean = filterChain.shouldExcludePath(path)

    /**
     * Index paths with progress tracking.
     * Supports multiple unrelated paths - each is evaluated independently.
     * Uses incremental indexing - only indexes new or modified files.
     * Detects and removes deleted files from the index.
     */
    suspend fun indexPathsWithProgress(
        paths: List<Path>,
    ): Boolean {
        _progress.value = IndexProgress(status = IndexStatus.INDEXING)

        try {
            // Load previous state for incremental indexing
            val previousState = stateManager.loadPersistedState()
            val previousHashes: Map<String, String> = previousState?.fileHashes ?: emptyMap()

            val totalFiles = countIndexableFiles(paths)
            _progress.value = _progress.value.copy(totalFiles = totalFiles)

            log.info("Starting indexing for project $projectId: $totalFiles files")

            val fileHashes = mutableMapOf<String, String>()

            // Index all current files
            for (path in paths) {
                if (!indexPath(path, fileHashes, previousHashes, ::trackSkippedFile)) {
                    _progress.value = _progress.value.copy(
                        status = IndexStatus.FAILED,
                        error = "Failed to index path: $path",
                    )
                    return false
                }
            }

            // Detect and remove deleted files
            val deletedFiles = detectDeletedFiles(previousHashes, fileHashes)
            if (deletedFiles.isNotEmpty()) {
                log.info("Detected ${deletedFiles.size} deleted files, removing from index...")
                removeDeletedFilesFromIndex(deletedFiles)
            }

            val skippedFiles = previousHashes.keys.intersect(fileHashes.keys).count { key ->
                previousHashes[key] == fileHashes[key]
            }

            // Flush any remaining segments
            if (!hybridIndexer.flushRemainingSegments()) {
                _progress.value = _progress.value.copy(
                    status = IndexStatus.FAILED,
                    error = "Failed to flush remaining segments",
                )
                return false
            }

            // Save state (only includes current files, deleted files are removed)
            val processedFiles = fileHashes.size
            stateManager.saveState(processedFiles, fileHashes)

            _progress.value = _progress.value.copy(
                status = IndexStatus.READY,
                processedFiles = processedFiles,
            )

            log.info(
                "Completed indexing for project $projectId: " +
                    "${processedFiles - skippedFiles} files indexed, " +
                    "$skippedFiles files skipped (unchanged), " +
                    "${deletedFiles.size} files removed",
            )
            return true
        } catch (e: Exception) {
            log.error("Indexing failed for project $projectId", e)
            _progress.value = _progress.value.copy(
                status = IndexStatus.FAILED,
                error = e.message ?: "Unknown error",
            )
            return false
        }
    }

    /**
     * Detect files that were previously indexed but are now deleted.
     * Returns list of absolute file paths that no longer exist.
     */
    private fun detectDeletedFiles(
        previousHashes: Map<String, String>,
        currentHashes: Map<String, String>,
    ): List<String> {
        // Files in previous state but not in current state = deleted
        val deletedFiles = previousHashes.keys - currentHashes.keys

        if (deletedFiles.isNotEmpty()) {
            log.debug("Detected ${deletedFiles.size} deleted files:")
            deletedFiles.take(5).forEach { log.debug("  - $it") }
            if (deletedFiles.size > 5) {
                log.debug("  ... and ${deletedFiles.size - 5} more")
            }
        }

        return deletedFiles.toList()
    }

    /**
     * Remove deleted files from the hybrid index (vector store + keyword index).
     */
    private fun removeDeletedFilesFromIndex(deletedFiles: List<String>) {
        try {
            for (absoluteFilePath in deletedFiles) {
                val filePath = Path.of(absoluteFilePath)
                hybridIndexer.removeFileFromIndex(filePath)
                log.debug("Removed deleted file from index: $absoluteFilePath")
            }
        } catch (e: Exception) {
            log.error("Failed to remove deleted files from index", e)
            throw e
        }
    }

    private fun trackSkippedFile() {
        // Placeholder for tracking, actual counting done by comparing hashes
    }

    /**
     * Index a single path (file or directory).
     * Returns true if successful, false if an error occurred.
     */
    private suspend fun indexPath(
        path: Path,
        fileHashes: MutableMap<String, String>,
        previousHashes: Map<String, String>,
        onSkip: () -> Unit,
    ): Boolean {
        if (shouldExcludePath(path)) {
            return true
        }

        return when {
            path.isRegularFile() -> indexFile(path, fileHashes, previousHashes)
            path.isDirectory() -> indexDirectory(path, fileHashes, previousHashes, onSkip)
            else -> {
                log.warn("Skipping non-file, non-directory: $path")
                true
            }
        }
    }

    /**
     * Index a single file.
     * Returns true if file was processed successfully (indexed or skipped), false on error.
     */
    private suspend fun indexFile(
        filePath: Path,
        fileHashes: MutableMap<String, String>,
        previousHashes: Map<String, String>,
    ): Boolean {
        val startTime = System.currentTimeMillis()
        log.debug("Indexing file {}", filePath.fileName)

        try {
            // Calculate file hash first to check if file has changed
            val hash = stateManager.calculateFileHash(filePath)
            val absolutePath = filePath.toAbsolutePath().toString()

            // Store hash regardless of whether we index or skip
            fileHashes[absolutePath] = hash

            // Skip if file hasn't changed (incremental indexing)
            if (previousHashes[absolutePath] == hash) {
                log.debug("Skipping unchanged file: {}", filePath.fileName)
                updateProgress()
                return true
            }

            // Extract text
            val text = fileProcessor.extractTextFromFile(filePath)

            // Skip files that can't be read or have blank content
            if (text.isNullOrBlank()) {
                log.debug("Skipping file with no extractable content: {}", filePath.fileName)
                return true
            }

            // Chunk text
            val chunks = fileProcessor.chunkText(text)

            // Skip if no valid chunks were created
            if (chunks.isEmpty()) {
                log.debug("No valid chunks created for file: {}", filePath.fileName)
                return true
            }

            log.debug("Start indexing {} ({} chunks)", filePath.fileName, chunks.size)
            for ((idx, chunk) in chunks.withIndex()) {
                val segment = fileProcessor.createTextSegment(
                    chunk = chunk,
                    filePath = filePath,
                    chunkIndex = idx,
                    totalChunks = chunks.size,
                )

                if (!hybridIndexer.addSegmentToBatch(segment, filePath)) {
                    return false
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            log.debug("Indexed {} ({} chunks) in {}ms", filePath.fileName, chunks.size, elapsedTime)

            updateProgress()
            return true
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            log.error("Failed to index file {} after {}ms: {}", filePath.fileName, elapsedTime, e.message, e)
            return false
        }
    }

    /**
     * Update progress and emit events
     */
    private suspend fun updateProgress() {
        val processedFiles = _progress.value.processedFiles + 1
        val totalFiles = _progress.value.totalFiles
        _progress.value = _progress.value.copy(processedFiles = processedFiles)

        // Emit progress event every 10 files or at the end
        if (processedFiles % 10 == 0 || processedFiles == totalFiles) {
            EventBus.emit(
                IndexingInProgressEvent(
                    projectId = projectId,
                    projectName = projectName,
                    filesIndexed = processedFiles,
                    totalFiles = totalFiles,
                ),
            )
        }
    }

    /**
     * Index a directory recursively.
     * Returns true if successful, false if an error occurred.
     */
    private suspend fun indexDirectory(
        dir: Path,
        fileHashes: MutableMap<String, String>,
        previousHashes: Map<String, String>,
        onSkip: () -> Unit,
    ): Boolean {
        try {
            val entries = dir.listDirectoryEntries()

            for (entry in entries) {
                if (!indexPath(entry, fileHashes, previousHashes, onSkip)) {
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            log.error("Failed to index directory ${dir.fileName}: ${e.message}", e)
            return false
        }
    }

    /**
     * Count total indexable files
     */
    private fun countIndexableFiles(paths: List<Path>): Int {
        var count = 0

        for (path in paths) {
            count += when {
                path.isRegularFile() -> if (!shouldExcludePath(path)) 1 else 0
                path.isDirectory() -> countFilesInDirectory(path)
                else -> 0
            }
        }

        return count
    }

    /**
     * Count files in a directory recursively
     */
    private fun countFilesInDirectory(dir: Path): Int {
        if (shouldExcludePath(dir)) {
            return 0
        }

        var count = 0

        try {
            val entries = dir.listDirectoryEntries()

            for (entry in entries) {
                count += when {
                    entry.isRegularFile() -> if (!shouldExcludePath(entry)) 1 else 0
                    entry.isDirectory() -> countFilesInDirectory(entry)
                    else -> 0
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to count files in ${dir.fileName}: ${e.message}")
        }

        return count
    }
}
