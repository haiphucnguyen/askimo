/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.user.IndexingInProgressEvent
import io.askimo.core.logging.logger
import io.askimo.core.rag.filter.FilterChain
import io.askimo.core.rag.state.IndexProgress
import io.askimo.core.rag.state.IndexStateManager
import io.askimo.core.rag.state.IndexStatus
import io.askimo.core.rag.watching.FileChangeHandler
import io.askimo.core.rag.watching.FileWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Coordinates the indexing process for local files.
 * Implements IndexingCoordinator to provide lifecycle management including
 * indexing and file watching.
 */
class LocalFilesIndexingCoordinator(
    private val projectId: String,
    private val projectName: String,
    private val paths: List<Path>,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val appContext: AppContext,
) : IndexingCoordinator {
    private val log = logger<LocalFilesIndexingCoordinator>()

    private val resourceContentProcessor = ResourceContentProcessor(appContext)
    private val stateManager = IndexStateManager(projectId)
    private val hybridIndexer = HybridIndexer(embeddingStore, embeddingModel, projectId)

    private val _progress = MutableStateFlow(IndexProgress())
    override val progress: StateFlow<IndexProgress> = _progress

    private val filterChain: FilterChain = FilterChain.DEFAULT

    private val projectRoots = mutableListOf<Path>()

    private val maxConcurrentFiles = AppConfig.indexing.concurrentIndexingThreads
    private val fileSemaphore = Semaphore(maxConcurrentFiles)

    private val processedFilesCounter = AtomicInteger(0)
    private val totalFilesCounter = AtomicInteger(0)

    // File watcher is owned by this coordinator
    private var fileWatcher: FileWatcher? = null

    /**
     * Check if a path should be excluded from indexing using the filter chain.
     * Uses the appropriate project root for context.
     */
    private fun shouldExcludePath(path: Path): Boolean {
        val absolutePath = path.toAbsolutePath()

        // Find the appropriate project root for this path
        val rootPath = projectRoots.firstOrNull { root ->
            try {
                absolutePath.startsWith(root.toAbsolutePath())
            } catch (e: Exception) {
                log.warn("Failed to check if path $absolutePath starts with project root $root: ${e.message}", e)
                false
            }
        }

        // Never exclude the project root itself
        if (rootPath != null && absolutePath == rootPath.toAbsolutePath()) {
            return false
        }

        return if (rootPath != null) {
            filterChain.shouldExclude(path, rootPath)
        } else {
            filterChain.shouldExcludePath(path)
        }
    }

    /**
     * Index paths with progress tracking.
     * Uses parallel processing for better performance.
     * Uses incremental indexing - only indexes new or modified files.
     * Detects and removes deleted files from the index.
     */
    suspend fun indexPathsWithProgress(
        paths: List<Path>,
    ): Boolean {
        _progress.value = IndexProgress(status = IndexStatus.INDEXING)

        // Store project roots for filtering context
        projectRoots.clear()
        projectRoots.addAll(paths.map { it.toAbsolutePath() })

        // Reset counters
        processedFilesCounter.set(0)
        totalFilesCounter.set(0)

        try {
            val previousState = stateManager.loadPersistedState()
            val previousHashes: Map<String, String> = previousState?.fileHashes ?: emptyMap()

            log.info("Starting parallel indexing for project $projectId with $maxConcurrentFiles concurrent threads")

            val fileHashes = ConcurrentHashMap<String, String>()

            // Collect all files first
            val allFiles = mutableListOf<Path>()
            for (path in paths) {
                collectIndexableFiles(path, allFiles)
            }

            totalFilesCounter.set(allFiles.size)
            _progress.value = _progress.value.copy(totalFiles = allFiles.size)

            log.info("Found ${allFiles.size} indexable files")

            coroutineScope {
                allFiles.map { filePath ->
                    async(Dispatchers.IO) {
                        fileSemaphore.withPermit {
                            indexFile(filePath, fileHashes, previousHashes)
                        }
                    }
                }.awaitAll()
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
     * Collect all indexable files without processing them (fast).
     * This replaces the expensive countIndexableFiles that calculated hashes.
     */
    private fun collectIndexableFiles(path: Path, result: MutableList<Path>) {
        if (shouldExcludePath(path)) {
            return
        }

        when {
            path.isRegularFile() -> result.add(path)
            path.isDirectory() -> {
                try {
                    path.listDirectoryEntries().forEach { entry ->
                        collectIndexableFiles(entry, result)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to list directory ${path.fileName}: ${e.message}")
                }
            }
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
     * Index a single file (optimized for parallel processing).
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
     * Start indexing using the paths provided at construction.
     */
    override suspend fun startIndexing(): Boolean = indexPathsWithProgress(paths)

    /**
     * Start watching for file changes.
     */
    override fun startWatching(scope: CoroutineScope) {
        if (fileWatcher != null) {
            log.debug("File watcher already active for project $projectId")
            return
        }

        val changeHandler = FileChangeHandler(
            projectId = projectId,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )

        fileWatcher = FileWatcher(
            projectId = projectId,
            onFileChange = { path, kind ->
                changeHandler.handleFileChange(path, kind)
            },
        )

        fileWatcher?.startWatching(paths, scope)
        log.info("Started file watching for project $projectId")
    }

    /**
     * Stop watching for file changes.
     */
    override fun stopWatching() {
        fileWatcher?.stopWatching()
        fileWatcher = null
        log.info("Stopped file watching for project $projectId")
    }

    /**
     * Close coordinator and cleanup resources.
     */
    override fun close() {
        stopWatching()
        log.debug("Closed LocalFilesIndexingCoordinator for project $projectId")
    }
}
