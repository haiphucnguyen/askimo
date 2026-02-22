/* SPDX-License-Identifier: AGPLv3
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.isRegularFile

/**
 * Coordinates the indexing process for specific local files.
 * Simplified version of LocalFoldersIndexingCoordinator that only indexes
 * individual files without directory traversal.
 */
class LocalFilesIndexingCoordinator(
    private val projectId: String,
    private val projectName: String,
    private val filePaths: List<Path>,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val appContext: AppContext,
) : IndexingCoordinator {
    private val log = logger<LocalFilesIndexingCoordinator>()

    private val resourceContentProcessor = ResourceContentProcessor(appContext)
    private val stateManager = IndexStateManager(projectId, "files")
    private val hybridIndexer = HybridIndexer(embeddingStore, embeddingModel, projectId)

    private val _progress = MutableStateFlow(IndexProgress())
    override val progress: StateFlow<IndexProgress> = _progress

    // Use LOCAL_FILES filter chain which only checks supported extensions
    private val filterChain: FilterChain = FilterChain.LOCAL_FILES

    private val processedFilesCounter = AtomicInteger(0)
    private val totalFilesCounter = AtomicInteger(0)

    /**
     * Check if a file should be excluded from indexing using the filter chain.
     */
    private fun shouldExcludeFile(path: Path): Boolean = filterChain.shouldExcludePath(path)

    /**
     * Index files with progress tracking.
     * Uses incremental indexing - only indexes new or modified files.
     * Detects and removes deleted files from the index.
     */
    private suspend fun indexFilesWithProgress(
        paths: List<Path>,
    ): Boolean {
        _progress.value = IndexProgress(status = IndexStatus.INDEXING)

        // Reset counters
        processedFilesCounter.set(0)
        totalFilesCounter.set(0)

        try {
            val previousState = stateManager.loadPersistedState()
            val previousHashes: Map<String, String> = previousState?.fileHashes ?: emptyMap()

            log.info("Starting indexing for project $projectId with ${paths.size} files")

            val fileHashes = ConcurrentHashMap<String, String>()

            // Filter valid files
            val validFiles = paths.filter { path ->
                when {
                    !path.isRegularFile() -> {
                        log.warn("Skipping non-file path: $path")
                        false
                    }
                    shouldExcludeFile(path) -> {
                        log.debug("Skipping excluded file: $path")
                        false
                    }
                    else -> true
                }
            }

            totalFilesCounter.set(validFiles.size)
            _progress.value = _progress.value.copy(totalFiles = validFiles.size)

            log.info("Found ${validFiles.size} indexable files")

            // Process files sequentially (simpler for file-only indexing)
            for (filePath in validFiles) {
                indexFile(filePath, fileHashes, previousHashes)
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
            stateManager.saveState(processedFiles, fileHashes.toMap())

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

    /**
     * Index a single file.
     * Returns true if file was processed successfully (indexed or skipped), false on error.
     */
    private suspend fun indexFile(
        filePath: Path,
        fileHashes: ConcurrentHashMap<String, String>,
        previousHashes: Map<String, String>,
    ): Boolean {
        val startTime = System.currentTimeMillis()

        try {
            val hash = stateManager.calculateFileHash(filePath)
            val absolutePath = filePath.toAbsolutePath().toString()

            // Store hash regardless of whether we index or skip
            fileHashes[absolutePath] = hash

            // Skip if file hasn't changed (incremental indexing)
            if (previousHashes[absolutePath] == hash) {
                log.debug("Skipping unchanged file: {}", filePath.fileName)
                updateProgressAtomic()
                return true
            }

            val text = resourceContentProcessor.extractTextFromFile(filePath)

            // Skip files that can't be read or have blank content
            if (text.isNullOrBlank()) {
                log.debug("Skipping file with no extractable content: {}", filePath.fileName)
                updateProgressAtomic()
                return true
            }

            // Check if this is a text file where line numbers are meaningful
            val isTextFile = resourceContentProcessor.isTextFile(filePath)

            if (isTextFile) {
                // For text files, use line-aware chunking
                val chunksWithLineNumbers = resourceContentProcessor.chunkTextWithLineNumbers(text)

                // Skip if no valid chunks were created
                if (chunksWithLineNumbers.isEmpty()) {
                    log.debug("No valid chunks created for file: {}", filePath.fileName)
                    updateProgressAtomic()
                    return true
                }

                log.debug("Start indexing {} ({} chunks, text file)", filePath.fileName, chunksWithLineNumbers.size)
                for ((idx, chunkData) in chunksWithLineNumbers.withIndex()) {
                    val segment = resourceContentProcessor.createTextSegmentWithMetadata(
                        chunk = chunkData.text,
                        filePath = filePath,
                        chunkIndex = idx,
                        totalChunks = chunksWithLineNumbers.size,
                        startLine = chunkData.startLine,
                        endLine = chunkData.endLine,
                    )

                    if (!hybridIndexer.addSegmentToBatch(segment, filePath)) {
                        return false
                    }
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                log.debug(
                    "Indexed {} ({} chunks, lines tracked) in {}ms",
                    filePath.fileName,
                    chunksWithLineNumbers.size,
                    elapsedTime,
                )
            } else {
                // For binary files (PDF, DOCX, etc.), use regular chunking without line numbers
                val chunks = resourceContentProcessor.chunkText(text)

                // Skip if no valid chunks were created
                if (chunks.isEmpty()) {
                    log.debug("No valid chunks created for file: {}", filePath.fileName)
                    updateProgressAtomic()
                    return true
                }

                log.debug("Start indexing {} ({} chunks, binary file)", filePath.fileName, chunks.size)
                for ((idx, chunk) in chunks.withIndex()) {
                    val segment = resourceContentProcessor.createTextSegmentWithMetadata(
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
            }

            updateProgressAtomic()
            return true
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            log.error("Failed to index file {} after {}ms: {}", filePath.fileName, elapsedTime, e.message, e)
            return false
        }
    }

    /**
     * Update progress atomically
     */
    private suspend fun updateProgressAtomic() {
        val processedFiles = processedFilesCounter.incrementAndGet()
        val totalFiles = totalFilesCounter.get()
        _progress.value = _progress.value.copy(processedFiles = processedFiles)

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

    // ========== IndexingCoordinator Interface Implementation ==========

    /**
     * Start indexing using the file paths provided at construction.
     */
    override suspend fun startIndexing(): Boolean = indexFilesWithProgress(filePaths)

    /**
     * File-level indexing doesn't support watching (no directory to watch).
     */
    override fun startWatching(scope: CoroutineScope) {
        log.debug("File watching not supported for file-level indexing (project $projectId)")
    }

    /**
     * File-level indexing doesn't support watching.
     */
    override fun stopWatching() {
        // No-op
    }

    /**
     * Close coordinator and cleanup resources.
     */
    override fun close() {
        log.debug("Closed LocalFilesIndexingCoordinator for project $projectId")
    }

    /**
     * Clear all indexed data for this project.
     */
    override fun clearAll() {
        stateManager.clearStates()
        log.info("Cleared all index states for project $projectId (files)")
    }
}
