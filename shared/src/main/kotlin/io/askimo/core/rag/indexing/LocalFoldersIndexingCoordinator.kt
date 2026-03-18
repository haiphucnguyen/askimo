/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.rag.filter.FilterChain
import io.askimo.core.rag.state.IndexProgress
import io.askimo.core.rag.state.IndexStatus
import io.askimo.core.rag.watching.FileChangeHandler
import io.askimo.core.rag.watching.FileWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

/**
 * Coordinates the indexing process for local files.
 * Implements IndexingCoordinator to provide lifecycle management including
 * indexing and file watching.
 */
class LocalFoldersIndexingCoordinator(
    projectId: String,
    projectName: String,
    override val knowledgeSourceConfig: LocalFoldersKnowledgeSourceConfig,
    embeddingStore: EmbeddingStore<TextSegment>,
    embeddingModel: EmbeddingModel,
    appContext: AppContext,
) : BaseLocalIndexingCoordinator<LocalFoldersKnowledgeSourceConfig>(
    projectId = projectId,
    projectName = projectName,
    embeddingStore = embeddingStore,
    embeddingModel = embeddingModel,
    appContext = appContext,
    stateManagerScope = "folders",
) {
    private val log = logger<LocalFoldersIndexingCoordinator>()

    private val filePaths = listOf(Paths.get(knowledgeSourceConfig.resourceIdentifier))

    private val filterChain: FilterChain = FilterChain.DEFAULT

    private val projectRoots = mutableListOf<Path>()

    private val maxConcurrentFiles = AppConfig.indexing.concurrentIndexingThreads
    private val fileSemaphore = Semaphore(maxConcurrentFiles)

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
        updateProgress { IndexProgress(status = IndexStatus.INDEXING) }

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
            updateProgress { copy(totalFiles = allFiles.size) }

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

            return finalizeIndexing(previousHashes, fileHashes)
        } catch (e: Exception) {
            log.error("Indexing failed for project $projectName", e)
            updateProgress { copy(status = IndexStatus.FAILED, error = e.message ?: "Unknown error") }
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
                    log.warn("Failed to list directory ${path.pathString}: ${e.message}")
                }
            }
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
                log.trace("Skipping unchanged file: {}", filePath.pathString)
                updateProgressAtomic()
                return true
            }

            val segments = resourceContentProcessor.createSegmentsForFile(filePath)

            // Skip files that can't be read, have blank content, or produce no valid chunks
            if (segments == null) {
                log.warn("Skipping file with no extractable content: {}", filePath.pathString)
                updateProgressAtomic()
                return true
            }

            if (segments.isEmpty()) {
                log.debug("No valid chunks created for file: {}", filePath.pathString)
                updateProgressAtomic()
                return true
            }

            log.trace("Start indexing {} ({} chunks)", filePath.pathString, segments.size)
            for (segment in segments) {
                if (!hybridIndexer.addSegmentToBatch(segment, filePath)) {
                    return false
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            log.debug("Indexed {} ({} chunks) in {}ms", filePath.pathString, segments.size, elapsedTime)

            updateProgressAtomic()
            return true
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            log.error("Failed to index file {} after {}ms: {}", filePath.pathString, elapsedTime, e.message, e)
            return false
        }
    }

    // ========== IndexingCoordinator Interface Implementation ==========

    /**
     * Start indexing using the paths provided at construction.
     */
    override suspend fun startIndexing(): Boolean = indexPathsWithProgress(filePaths)

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

        fileWatcher?.startWatching(filePaths, scope)
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
     * Clear all indexed data for this project.
     */
    override fun clearAll() {
        stateManager.clearStates()
        log.info("Cleared all index states for project $projectId (folders)")
    }

    /**
     * Close coordinator and cleanup resources.
     */
    override fun close() {
        stopWatching()
        log.debug("Closed LocalFoldersIndexingCoordinator for project $projectId")
    }
}
