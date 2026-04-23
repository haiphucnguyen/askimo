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
    resourceId = knowledgeSourceConfig.resourceIdentifier,
) {
    private val log = logger<LocalFoldersIndexingCoordinator>()

    private val filePath = Paths.get(knowledgeSourceConfig.resourceIdentifier)

    private val filterChain: FilterChain = FilterChain.DEFAULT

    private val projectRoots = mutableListOf<Path>()

    private val maxConcurrentFiles = AppConfig.indexing.concurrentIndexingThreads
    private val fileSemaphore = Semaphore(maxConcurrentFiles)

    private var fileWatcher: FileWatcher? = null

    @Volatile private var cancelled = false

    companion object {
        /** Number of files per chunk. Each chunk loads its own small hash map from DB. */
        private const val CHUNK_SIZE = 20
    }

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
     * Index paths with progress tracking using chunked processing.
     *
     * Memory model: instead of holding ALL file hashes in two maps simultaneously,
     * each chunk loads only its own slice of previous hashes from the DB, accumulates
     * changed hashes in a small ConcurrentHashMap, persists them, then discards the map.
     */
    suspend fun indexPathWithProgress(path: Path): Boolean {
        cancelled = false
        updateProgress { IndexProgress(status = IndexStatus.INDEXING) }

        projectRoots.clear()
        projectRoots.addAll(path.toAbsolutePath())
        processedFilesCounter.set(0)
        totalFilesCounter.set(0)

        try {
            // Collect file paths only — no hashes yet, much smaller footprint
            val allFiles = mutableListOf<Path>()
            collectIndexableFiles(path, allFiles)

            totalFilesCounter.set(allFiles.size)
            updateProgress { copy(totalFiles = allFiles.size) }
            log.info("Found ${allFiles.size} indexable files for project $projectId, processing in chunks of $CHUNK_SIZE")

            // Process one chunk at a time — each chunk's hash maps are discarded after saving
            for (chunk in allFiles.chunked(CHUNK_SIZE)) {
                if (cancelled) break

                val chunkAbsolutePaths = chunk.map { it.toAbsolutePath().toString() }

                // One DB round-trip for this chunk's previous hashes (not the whole project)
                val previousChunkHashes = stateManager.getHashesForFiles(chunkAbsolutePaths)

                // Only holds hashes for files that actually changed in this chunk
                val changedHashes = ConcurrentHashMap<String, String>(chunk.size)

                coroutineScope {
                    chunk.map { file ->
                        async(Dispatchers.IO) {
                            fileSemaphore.withPermit {
                                indexFileIncremental(file, previousChunkHashes, changedHashes)
                            }
                        }
                    }.awaitAll()
                }

                // Persist changed files immediately, then GC the map
                stateManager.saveFileHashesBatch(changedHashes)
            }

            if (!cancelled) {
                // Deleted-file detection: DB paths that are no longer on disk
                val currentPathSet = allFiles.mapTo(HashSet()) { it.toAbsolutePath().toString() }
                val storedPaths = stateManager.getStoredPaths()
                val deletedPaths = storedPaths - currentPathSet

                if (deletedPaths.isNotEmpty()) {
                    log.info("Detected ${deletedPaths.size} deleted files, removing from index...")
                    deletedPaths.forEach { hybridIndexer.removeFileFromIndex(Path.of(it)) }
                    stateManager.removeFilePaths(deletedPaths)
                }
            }

            if (!hybridIndexer.flushRemainingSegments()) {
                updateProgress { copy(status = IndexStatus.FAILED, error = "Failed to flush remaining segments") }
                return false
            }

            updateProgress { copy(status = IndexStatus.READY, processedFiles = processedFilesCounter.get()) }
            log.info("Completed indexing for project $projectName: ${processedFilesCounter.get()} files processed")
            return true
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
     * Index a single file within a chunk.
     * Writes to [changedHashes] only if the file actually changed — unchanged files
     * keep their existing DB entry untouched.
     */
    private suspend fun indexFileIncremental(
        filePath: Path,
        previousChunkHashes: Map<String, String>,
        changedHashes: ConcurrentHashMap<String, String>,
    ): Boolean {
        if (cancelled) {
            log.trace("Indexing cancelled, skipping file: {}", filePath.pathString)
            return true
        }
        val startTime = System.currentTimeMillis()

        try {
            val absolutePath = filePath.toAbsolutePath().toString()
            val hash = stateManager.calculateFileHash(filePath)

            // Unchanged — DB entry is still valid, skip without touching anything
            if (previousChunkHashes[absolutePath] == hash) {
                log.trace("Skipping unchanged file: {}", filePath.pathString)
                updateProgressAtomic()
                return true
            }

            // File changed — remove stale old segments before re-indexing to avoid duplicates
            if (previousChunkHashes.containsKey(absolutePath)) {
                log.trace("File changed, removing old segments: {}", filePath.pathString)
                hybridIndexer.removeFileFromIndex(filePath)
            }

            val segments = resourceContentProcessor.createSegmentsForFile(filePath)

            if (segments == null) {
                log.warn("Skipping file with no extractable content: {}", filePath.pathString)
                changedHashes[absolutePath] = hash // still record so we don't re-visit
                updateProgressAtomic()
                return true
            }

            if (segments.isEmpty()) {
                log.debug("No valid chunks created for file: {}", filePath.pathString)
                changedHashes[absolutePath] = hash
                updateProgressAtomic()
                return true
            }

            log.trace("Start indexing {} ({} chunks)", filePath.pathString, segments.size)
            for (segment in segments) {
                if (!hybridIndexer.addSegmentToBatch(segment, filePath)) return false
            }

            changedHashes[absolutePath] = hash
            log.debug("Indexed {} ({} chunks) in {}ms", filePath.pathString, segments.size, System.currentTimeMillis() - startTime)
            updateProgressAtomic()
            return true
        } catch (e: Exception) {
            log.error("Failed to index file {} after {}ms: {}", filePath.pathString, System.currentTimeMillis() - startTime, e.message, e)
            return false
        }
    }

    /**
     * Start indexing using the paths provided at construction.
     */
    override suspend fun startIndexing(): Boolean = indexPathWithProgress(filePath)

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

        fileWatcher?.startWatching(filePath, scope)
        log.info("Started file watching for project $projectId")
    }

    /**
     * Stop watching for file changes.
     */
    override fun stopWatching() {
        fileWatcher?.stopWatching()
        fileWatcher = null
        log.info("Stopped file watching $filePath for project $projectId")
    }

    /**
     * Close coordinator and cleanup resources.
     * Setting [cancelled] to true signals any in-progress indexing to stop at the
     * next file boundary, preventing stale-dimension segments from being written
     * into a store that is about to be wiped by a re-index.
     */
    override fun close() {
        cancelled = true
        stopWatching()
        log.debug("Closed LocalFoldersIndexingCoordinator for project $projectId (cancelled in-progress indexing)")
    }
}
