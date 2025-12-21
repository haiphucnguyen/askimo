/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.context.AppContext
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
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val appContext: AppContext,
) {
    private val log = logger<IndexingCoordinator>()

    private val fileProcessor = FileProcessor(appContext)
    private val stateManager = IndexStateManager(projectId)
    private val batchIndexer = HybridIndexer(embeddingStore, embeddingModel, projectId)

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
     */
    suspend fun indexPathsWithProgress(
        paths: List<Path>,
    ): Boolean {
        _progress.value = IndexProgress(status = IndexStatus.INDEXING)

        try {
            val totalFiles = countIndexableFiles(paths)
            _progress.value = _progress.value.copy(totalFiles = totalFiles)

            log.info("Starting indexing for project $projectId: $totalFiles files")

            val fileHashes = mutableMapOf<String, String>()
            var processedFiles = 0

            for (path in paths) {
                if (!indexPath(path, fileHashes)) {
                    _progress.value = _progress.value.copy(
                        status = IndexStatus.FAILED,
                        error = "Failed to index path: $path",
                    )
                    return false
                }

                processedFiles = fileHashes.size
                _progress.value = _progress.value.copy(processedFiles = processedFiles)
            }

            // Flush any remaining segments
            if (!batchIndexer.flushRemainingSegments()) {
                _progress.value = _progress.value.copy(
                    status = IndexStatus.FAILED,
                    error = "Failed to flush remaining segments",
                )
                return false
            }

            // Save state
            stateManager.saveState(processedFiles, fileHashes)

            _progress.value = _progress.value.copy(
                status = IndexStatus.READY,
                processedFiles = processedFiles,
            )

            log.info("Completed indexing for project $projectId: $processedFiles files indexed")
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
     * Index a single path (file or directory)
     */
    private suspend fun indexPath(
        path: Path,
        fileHashes: MutableMap<String, String>,
    ): Boolean {
        if (shouldExcludePath(path)) {
            return true
        }

        return when {
            path.isRegularFile() -> indexFile(path, fileHashes)
            path.isDirectory() -> indexDirectory(path, fileHashes)
            else -> {
                log.warn("Skipping non-file, non-directory: $path")
                true
            }
        }
    }

    /**
     * Index a single file
     */
    private suspend fun indexFile(
        filePath: Path,
        fileHashes: MutableMap<String, String>,
    ): Boolean {
        val startTime = System.currentTimeMillis()
        log.debug("Indexing file {}", filePath.fileName)

        try {
            // Extract text
            val text = fileProcessor.extractTextFromFile(filePath) ?: return true

            // Calculate file hash
            val hash = stateManager.calculateFileHash(filePath)
            val absolutePath = filePath.toAbsolutePath().toString()
            fileHashes[absolutePath] = hash

            // Chunk text
            val chunks = fileProcessor.chunkText(text)

            log.trace("Start indexing {} ({} chunks)", filePath.fileName, chunks.size)
            for ((idx, chunk) in chunks.withIndex()) {
                val segment = fileProcessor.createTextSegment(
                    chunk = chunk,
                    filePath = filePath,
                    chunkIndex = idx,
                    totalChunks = chunks.size,
                )

                if (!batchIndexer.addSegmentToBatch(segment, filePath)) {
                    return false
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            log.debug("Indexed {} ({} chunks) in {}ms", filePath.fileName, chunks.size, elapsedTime)
            return true
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            log.error("Failed to index file {} after {}ms: {}", filePath.fileName, elapsedTime, e.message, e)
            return false
        }
    }

    /**
     * Index a directory recursively
     */
    private suspend fun indexDirectory(
        dir: Path,
        fileHashes: MutableMap<String, String>,
    ): Boolean {
        try {
            val entries = dir.listDirectoryEntries()

            for (entry in entries) {
                if (!indexPath(entry, fileHashes)) {
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
