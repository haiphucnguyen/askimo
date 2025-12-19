/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.Filter
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo
import io.askimo.core.config.AppConfig
import io.askimo.core.config.ProjectType
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.IndexingErrorEvent
import io.askimo.core.event.internal.IndexingErrorType
import io.askimo.core.event.internal.ProjectDeletedEvent
import io.askimo.core.event.user.IndexingCompletedEvent
import io.askimo.core.event.user.IndexingFailedEvent
import io.askimo.core.event.user.IndexingInProgressEvent
import io.askimo.core.event.user.IndexingStartedEvent
import io.askimo.core.logging.logger
import io.askimo.core.project.getEmbeddingModel
import io.askimo.core.project.getModelTokenLimit
import io.askimo.core.util.AskimoHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchService
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence
import org.apache.tika.metadata.Metadata as TikaMetadata
import dev.langchain4j.exception.ModelNotFoundException
import io.askimo.core.db.DatabaseManager

/**
 * Status of the indexing process for a project.
 */
enum class IndexStatus {
    NOT_STARTED, // Index hasn't been created yet
    INDEXING, // Currently indexing files
    READY, // Indexing completed, ready for queries
    WATCHING, // Index ready + watching for file changes
    FAILED, // Indexing failed
}

/**
 * Track indexing progress for a project.
 */
data class IndexProgress(
    val status: IndexStatus,
    val filesIndexed: Int = 0,
    val filesTotal: Int = 0,
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val error: String? = null,
    val isWatching: Boolean = false,
) {
    val progressPercent: Int
        get() = if (filesTotal > 0) (filesIndexed * 100 / filesTotal).coerceAtMost(100) else 0
}

/**
 * Track which files have been indexed and when.
 */
data class IndexedFileEntry(
    val path: String,
    val lastModified: Long,
    val indexed: LocalDateTime,
)

/**
 * Indexes project files into a JVector-backed store and exposes basic embedding and
 * similarity search utilities.
 *
 * This is a lightweight alternative to PgVectorIndexer that doesn't require Docker/PostgreSQL.
 * Uses file-based JVector indexes stored locally with better performance than Lucene.
 *
 * Use [getInstance] to get a cached instance per project.
 */
class ProjectIndexer private constructor(
    private val projectId: String,
    private val appContext: AppContext,
) {
    companion object {
        private val log = logger<ProjectIndexer>()

        /**
         * Cache of indexer instances per project.
         * Ensures only one indexer exists per project to avoid duplicate file watchers.
         */
        private val instances = ConcurrentHashMap<String, ProjectIndexer>()

        private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    try {
                        log.info("Shutdown hook triggered - cleaning up all ProjectIndexer instances")
                        shutdownAll()
                        log.info("Successfully cleaned up all indexers")
                    } catch (e: Exception) {
                        log.error("Failed to cleanup indexers on shutdown", e)
                    }
                },
            )

            // Listen for project deletion events
            eventScope.launch {
                EventBus.internalEvents
                    .filterIsInstance<ProjectDeletedEvent>()
                    .collect { event ->
                        log.debug("Received ProjectDeletedEvent for project: {}", event.projectId)
                        removeInstance(event.projectId)
                    }
            }
        }

        /**
         * Get or create a ProjectIndexer instance for a project.
         * Instances are cached per project ID.
         *
         * @param projectId The project ID
         * @param appContext The application context
         * @return Cached or new ProjectIndexer instance
         */
        fun getInstance(
            projectId: String,
            appContext: AppContext,
        ): ProjectIndexer = instances.getOrPut(projectId) {
            log.debug("Creating new ProjectIndexer instance for project: $projectId")
            ProjectIndexer(projectId, appContext)
        }

        /**
         * Remove and cleanup indexer for a project.
         * Stops file watching and removes from cache.
         *
         * @param projectId The project ID to cleanup
         */
        fun removeInstance(projectId: String) {
            instances.remove(projectId)?.let { indexer ->
                log.debug("Stopping and removing indexer for project: $projectId")
                indexer.stopWatching()
            }
        }

        /**
         * Stop all file watchers and clear cache.
         * Automatically called by shutdown hook.
         */
        private fun shutdownAll() {
            log.debug("Shutting down all ProjectIndexer instances")
            instances.values.forEach { indexer ->
                indexer.stopWatching()
                try {
                    indexer.stateRepository.close()
                } catch (e: Exception) {
                    log.error("Failed to close state repository", e)
                }
                try {
                    // Close Lucene IndexWriter to release file locks
                    indexer.keywordRetriever.close()
                } catch (e: Exception) {
                    log.error("Failed to close keyword retriever", e)
                }
            }
            instances.clear()
        }
    }

    private val indexPath: Path = AskimoHome.base().resolve("projects").resolve(projectId).resolve("jvector-indexes")

    private val maxCharsPerChunk: Int by lazy {
        calculateSafeMaxChars()
    }

    // Dynamic overlap based on chunk size (5% of chunk size, capped at configured max)
    private val chunkOverlap: Int by lazy {
        val calculatedOverlap = (maxCharsPerChunk * 0.05).toInt()
        val configuredMax = AppConfig.embedding.chunkOverlap
        val minOverlap = 50

        calculatedOverlap.coerceIn(minOverlap, configuredMax).also {
            log.info("Calculated chunk overlap: $it chars (${(it.toFloat() / maxCharsPerChunk * 100).toInt()}% of chunk size)")
        }
    }

    private val maxFileBytes = AppConfig.indexing.maxFileBytes
    private val concurrentIndexingThreads = AppConfig.indexing.concurrentIndexingThreads

    private val defaultCharset: Charset = Charsets.UTF_8

    // Cached project repository and name
    private val projectRepository by lazy {
        DatabaseManager.getInstance().getProjectRepository()
    }

    private val projectName: String by lazy {
        projectRepository.getProject(projectId)?.name ?: projectId
    }

    // Persistent state repository (SQLite database per project)
    private val stateRepository: IndexStateRepository by lazy {
        IndexStateRepository(indexPath)
    }

    // Track indexing status
    @Volatile
    private var indexProgress = IndexProgress(IndexStatus.NOT_STARTED)

    // Track indexed files with their modification times (absolute paths)
    private val indexedFiles = ConcurrentHashMap<String, IndexedFileEntry>()

    // Track if state has been loaded from database
    @Volatile
    private var stateLoaded = false

    // File watcher for detecting changes
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null

    /**
     * Get current indexing status/progress.
     */
    fun getIndexProgress(): IndexProgress = indexProgress

    /**
     * Load persisted state from SQLite database.
     */
    private fun loadPersistedState() {
        try {
            val persistedFiles = stateRepository.getAllIndexedFiles()

            // Restore indexed files (absolute paths)
            indexedFiles.clear()
            persistedFiles.forEach { (absolutePath, info) ->
                indexedFiles[absolutePath] = IndexedFileEntry(
                    path = info.path,
                    lastModified = info.lastModified,
                    indexed = info.indexedAt,
                )
            }

            // Restore progress from metadata
            val status = stateRepository.getMetadata("status")?.let { statusStr ->
                IndexStatus.entries.find { it.name == statusStr } ?: IndexStatus.NOT_STARTED
            } ?: IndexStatus.NOT_STARTED

            if (indexedFiles.isNotEmpty() && status != IndexStatus.FAILED) {
                indexProgress = IndexProgress(
                    status = if (status == IndexStatus.INDEXING) IndexStatus.READY else status,
                    filesIndexed = indexedFiles.size,
                    filesTotal = indexedFiles.size,
                    lastUpdated = LocalDateTime.now(),
                )
            }

            log.debug("Loaded persisted state for project $projectId: ${indexedFiles.size} files, status: ${indexProgress.status}")
        } catch (e: Exception) {
            log.warn("Failed to load persisted state for project $projectId: ${e.message}")
        }
    }

    /**
     * Save current state to SQLite database.
     */
    private fun saveState() {
        try {
            // Save metadata
            stateRepository.setMetadata("status", indexProgress.status.name)
            stateRepository.setMetadata("last_updated", indexProgress.lastUpdated.toString())
            stateRepository.setMetadata("files_indexed", indexedFiles.size.toString())

            log.debug("Saved state for project $projectId: ${indexedFiles.size} files, status: ${indexProgress.status}")
        } catch (e: Exception) {
            log.error("Failed to save state for project $projectId", e)
        }
    }

    /**
     * Detect changes in the file system compared to persisted state.
     * Returns files that need to be added, updated, or removed from the index.
     */
    private fun detectFileSystemChanges(paths: List<Path>): FileChanges {
        val currentFiles = mutableMapOf<String, IndexedFileInfo>()

        paths.forEach { path ->
            if (!Files.exists(path)) return@forEach

            if (Files.isDirectory(path)) {
                val detectedTypes = detectProjectTypes(path)
                Files.walk(path)
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .filter { isIndexableFile(it, path, detectedTypes) }
                    .filter { !tooLargeToIndex(it) }
                    .forEach { filePath ->
                        val absolutePath = filePath.toAbsolutePath().toString()
                        val lastModified = Files.getLastModifiedTime(filePath).toMillis()
                        val size = Files.size(filePath)

                        currentFiles[absolutePath] = IndexedFileInfo(
                            path = absolutePath,
                            lastModified = lastModified,
                            indexedAt = LocalDateTime.now(),
                            size = size,
                        )
                    }
            } else if (Files.isRegularFile(path) && !tooLargeToIndex(path)) {
                val absolutePath = path.toAbsolutePath().toString()
                currentFiles[absolutePath] = IndexedFileInfo(
                    path = absolutePath,
                    lastModified = Files.getLastModifiedTime(path).toMillis(),
                    indexedAt = LocalDateTime.now(),
                    size = Files.size(path),
                )
            }
        }

        return stateRepository.detectChanges(currentFiles)
    }

    /**
     * Apply incremental changes to the index.
     * This is called when changes are detected on app restart.
     */
    private fun applyIncrementalChanges(
        changes: FileChanges,
        rootPaths: List<Path>,
        watchForChanges: Boolean,
    ) {
        Thread {
            try {
                val totalChanges = changes.totalChanges

                indexProgress = IndexProgress(
                    status = IndexStatus.INDEXING,
                    filesTotal = totalChanges,
                )

                log.info("Applying incremental changes for project $projectId: $changes")

                var processed = 0

                // Remove deleted files
                changes.toRemove.forEach { absolutePath ->
                    try {
                        removeFileFromIndex(absolutePath)
                        indexedFiles.remove(absolutePath)
                        stateRepository.removeFile(absolutePath)
                        log.debug("Removed deleted file: $absolutePath")

                        processed++
                        indexProgress = indexProgress.copy(filesIndexed = processed)
                    } catch (e: Exception) {
                        log.error("Failed to remove file from index: $absolutePath", e)
                    }
                }

                // Index new and modified files
                (changes.toAdd + changes.toUpdate).forEach { fileInfo ->
                    try {
                        val filePath = Path.of(fileInfo.path)

                        if (!Files.exists(filePath)) {
                            log.warn("File no longer exists, skipping: $filePath")
                            processed++
                            indexProgress = indexProgress.copy(filesIndexed = processed)
                            return@forEach
                        }

                        // Find which root this file belongs to
                        val root = rootPaths.firstOrNull { filePath.startsWith(it) }
                        if (root == null) {
                            log.warn("File doesn't belong to any indexed root, skipping: $filePath")
                            processed++
                            indexProgress = indexProgress.copy(filesIndexed = processed)
                            return@forEach
                        }

                        // Remove old version if exists
                        removeFileFromIndex(fileInfo.path)

                        // Index new version
                        indexSingleFileWithTracking(filePath, root)

                        // Update in-memory tracking
                        indexedFiles[fileInfo.path] = IndexedFileEntry(
                            path = fileInfo.path,
                            lastModified = fileInfo.lastModified,
                            indexed = LocalDateTime.now(),
                        )

                        // Update database
                        stateRepository.upsertFile(fileInfo.copy(indexedAt = LocalDateTime.now()))

                        log.debug("Indexed changed file: ${fileInfo.path}")

                        processed++
                        indexProgress = indexProgress.copy(filesIndexed = processed)
                    } catch (e: Exception) {
                        log.error("Failed to index file: ${fileInfo.path}", e)
                        processed++
                        indexProgress = indexProgress.copy(filesIndexed = processed)
                    }
                }

                // Save final state
                saveState()

                if (watchForChanges) {
                    startWatching(rootPaths)
                    indexProgress = IndexProgress(
                        status = IndexStatus.WATCHING,
                        filesIndexed = indexedFiles.size,
                        filesTotal = indexedFiles.size,
                        isWatching = true,
                    )
                    log.info("Incremental indexing completed and watching for changes: project $projectId")
                } else {
                    indexProgress = IndexProgress(
                        status = IndexStatus.READY,
                        filesIndexed = indexedFiles.size,
                        filesTotal = indexedFiles.size,
                    )
                    log.info("Incremental indexing completed for project $projectId: ${indexedFiles.size} files")
                }
            } catch (e: Exception) {
                log.error("Incremental indexing failed for project $projectId", e)
                indexProgress = IndexProgress(
                    status = IndexStatus.FAILED,
                    error = e.message ?: "Unknown error",
                )
            }
        }.apply {
            name = "ProjectIndexer-Incremental-$projectId"
            isDaemon = true
        }.start()
    }

    /**
     * Ensure the index is ready by starting indexing if needed.
     * This method implements smart change detection:
     * - If index exists and is up-to-date, just start watching
     * - If files have changed, perform incremental indexing
     * - If no index exists, perform full indexing
     *
     * @param paths List of paths to index
     * @param watchForChanges If true, monitor paths for new/modified files
     * @return true if index is ready or being created, false if indexing failed
     */
    fun ensureIndexed(
        paths: List<Path>,
        watchForChanges: Boolean = true,
    ): Boolean {
        val progress = getIndexProgress()

        return when (progress.status) {
            IndexStatus.NOT_STARTED -> {
                // Check if we have persisted state
                if (indexedFiles.isNotEmpty()) {
                    log.info("Found existing index for project $projectId with ${indexedFiles.size} files")

                    // Detect changes since last indexing
                    val changes = detectFileSystemChanges(paths)

                    if (changes.hasChanges) {
                        log.info("Detected changes for project $projectId: $changes")
                        applyIncrementalChanges(changes, paths, watchForChanges)
                    } else {
                        log.info("Index up-to-date for project $projectId")
                        indexProgress = IndexProgress(
                            status = IndexStatus.READY,
                            filesIndexed = indexedFiles.size,
                            filesTotal = indexedFiles.size,
                        )

                        if (watchForChanges) {
                            startWatching(paths)
                            indexProgress = indexProgress.copy(
                                status = IndexStatus.WATCHING,
                                isWatching = true,
                            )
                        }
                    }
                } else {
                    log.info("No existing index found, performing full indexing for project $projectId")
                    indexPathsAsync(paths, watchForChanges)
                }
                true
            }
            IndexStatus.INDEXING -> {
                log.debug("Indexing in progress for project $projectId: ${progress.progressPercent}%")
                true
            }
            IndexStatus.READY, IndexStatus.WATCHING -> {
                log.debug("Index ready for project {} (status: {})", projectId, progress.status)
                true
            }
            IndexStatus.FAILED -> {
                log.error("Indexing failed for project $projectId: ${progress.error}")
                false
            }
        }
    }

    private val projectTypes = AppConfig.indexing.projectTypes

    private val commonExcludes = AppConfig.indexing.commonExcludes

    private val supportedExtensions = AppConfig.indexing.supportedExtensions

    private fun buildEmbeddingModel(): EmbeddingModel = getEmbeddingModel(appContext)

    /**
     * The embedding model used for creating embeddings.
     * Exposed for creating ContentRetriever in ChatSessionService.
     */
    val embeddingModel: EmbeddingModel by lazy { buildEmbeddingModel() }

    /**
     * Cached embedding store instance to avoid recreating it multiple times.
     * Initialized lazily when first accessed.
     * Exposed for creating ContentRetriever in ChatSessionService.
     */
    val embeddingStore: EmbeddingStore<TextSegment> by lazy { newStore() }

    /**
     * Keyword retriever for BM25 search (complements vector search).
     * Exposed for creating hybrid retriever in ChatSessionService.
     */
    val keywordRetriever: LuceneKeywordRetriever by lazy {
        val keywordIndexPath = indexPath.resolve("lucene-keywords")
        LuceneKeywordRetriever(keywordIndexPath, maxResults = 10)
    }

    /**
     * Hybrid indexer that coordinates indexing into both JVector and Lucene.
     * Encapsulates the dual-indexing logic.
     */
    private val hybridIndexer: HybridIndexer by lazy {
        HybridIndexer(
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            keywordRetriever = keywordRetriever,
        )
    }

    private fun newStore(): EmbeddingStore<TextSegment> {
        Files.createDirectories(indexPath)

        // Get dimension from embedding model
        val dimension = embeddingModel.dimension()

        // Use persistent storage path: ~/.askimo/jvector-indexes/{project_id}/store
        val persistencePath = indexPath.resolve("store").toString()

        log.info("Creating JVector embedding store at: $persistencePath (dimension: $dimension)")

        return JVectorEmbeddingStore.builder()
            .dimension(dimension)
            .persistencePath(persistencePath)
            .build()
    }

    /**
     * Index paths asynchronously and optionally start watching for changes.
     *
     * @param paths List of paths to index
     * @param watchForChanges If true, monitor paths for new/modified files
     */
    fun indexPathsAsync(
        paths: List<Path>,
        watchForChanges: Boolean = true,
    ) {
        if (indexProgress.status == IndexStatus.INDEXING) {
            log.warn("Indexing already in progress for project: $projectId")
            return
        }

        // Check embedding model availability before starting thread
        try {
            checkEmbeddingModelAvailable()
        } catch (e: Exception) {
            val cause = e.cause ?: e
            if (cause is ModelNotFoundException || cause.message?.contains("model not found", ignoreCase = true) == true) {
                val modelName = Regex("model: get model '([^']+)'[:]").find(cause.message ?: "")?.groupValues?.getOrNull(1) ?: "(unknown)"

                // Set failed status
                indexProgress = IndexProgress(
                    status = IndexStatus.FAILED,
                    error = cause.message ?: "Embedding model not found",
                )

                // Publish event for UI/CLI to handle
                EventBus.post(
                    IndexingErrorEvent(
                        projectId = projectId,
                        errorType = IndexingErrorType.EMBEDDING_MODEL_NOT_FOUND,
                        details = mapOf(
                            "modelName" to modelName,
                            "provider" to "your AI provider (e.g., OpenAI, Ollama, Docker AI)"
                        )
                    )
                )
                return
            } else {
                throw e
            }
        }

        Thread {
            try {
                // Load persisted state first if not already loaded
                if (!stateLoaded) {
                    loadPersistedState()
                    stateLoaded = true
                }

                val estimatedTotal = countIndexableFiles(paths)
                indexProgress = IndexProgress(
                    status = IndexStatus.INDEXING,
                    filesTotal = estimatedTotal,
                )

                log.debug("Starting async indexing for project $projectId: ${paths.size} paths, ~$estimatedTotal files")

                // Publish user event for indexing start
                try {

                    EventBus.post(
                        IndexingStartedEvent(
                            projectId = projectId,
                            projectName = projectName,
                            estimatedFiles = estimatedTotal
                        )
                    )
                } catch (e: Exception) {
                    log.error("Failed to publish indexing started event", e)
                }

                val indexed = indexPathsSync(paths)

                if (watchForChanges) {
                    startWatching(paths)
                    indexProgress = IndexProgress(
                        status = IndexStatus.WATCHING,
                        filesIndexed = indexed,
                        filesTotal = indexed,
                        isWatching = true,
                    )
                    log.debug("Indexing completed and watching for changes: project $projectId")
                } else {
                    indexProgress = IndexProgress(
                        status = IndexStatus.READY,
                        filesIndexed = indexed,
                        filesTotal = indexed,
                    )
                    log.debug("Indexing completed for project $projectId: $indexed files")
                }

                // Publish user event for successful indexing
                try {

                    EventBus.post(
                        IndexingCompletedEvent(
                            projectId = projectId,
                            projectName = projectName,
                            filesIndexed = indexed
                        )
                    )
                } catch (e: Exception) {
                    log.error("Failed to publish indexing completed event", e)
                }
            } catch (e: Exception) {
                log.error("Indexing failed for project $projectId", e)
                val errorMessage = e.message ?: "Unknown error"
                indexProgress = IndexProgress(
                    status = IndexStatus.FAILED,
                    error = errorMessage,
                )

                // Publish user event for failed indexing
                try {

                    EventBus.post(
                        IndexingFailedEvent(
                            projectId = projectId,
                            projectName = projectName,
                            errorMessage = errorMessage
                        )
                    )
                } catch (eventError: Exception) {
                    log.error("Failed to publish indexing failed event", eventError)
                }
            }
        }.apply {
            name = "ProjectIndexer-$projectId"
            isDaemon = true
        }.start()
    }

    /**
     * Synchronous indexing (blocking).
     * Use this for testing or when you need to wait for completion.
     */
    private fun indexPathsSync(paths: List<Path>): Int {
        var totalIndexed = 0

        paths.forEach { path ->
            if (!Files.exists(path)) {
                println("⚠️  Path does not exist, skipping: $path")
                return@forEach
            }

            totalIndexed += if (Files.isDirectory(path)) {
                indexProjectWithProgress(path)
            } else if (Files.isRegularFile(path)) {
                indexSingleFileWithTracking(path, path.parent)
                1
            } else {
                0
            }
        }

        return totalIndexed
    }

    /**
     * Count how many files will be indexed (for progress tracking).
     */
    private fun countIndexableFiles(paths: List<Path>): Int {
        var count = 0
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                val detectedTypes = detectProjectTypes(path)
                count += Files.walk(path)
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .filter { isIndexableFile(it, path, detectedTypes) }.count { !tooLargeToIndex(it) }
            } else if (Files.isRegularFile(path) && !tooLargeToIndex(path)) {
                count++
            }
        }
        return count
    }

    /**
     * Stop watching for file changes.
     */
    fun stopWatching() {
        watchThread?.interrupt()
        watchService?.close()
        watchService = null
        watchThread = null

        if (indexProgress.status == IndexStatus.WATCHING) {
            indexProgress = indexProgress.copy(
                status = IndexStatus.READY,
                isWatching = false,
            )
        }
    }

    /**
     * Clear the existing index and perform a fresh re-index.
     * This is useful when the index may be corrupted or out of sync.
     *
     * @param paths List of paths to re-index
     * @param watchForChanges If true, monitor paths for new/modified files after indexing
     */
    fun clearAndReindex(
        paths: List<Path>,
        watchForChanges: Boolean = true,
    ) {
        log.info("Clearing and re-indexing project: $projectId")

        // Stop watching first
        stopWatching()

        // Delete index directory (both JVector and Lucene)
        if (Files.exists(indexPath)) {
            try {
                indexPath.toFile().deleteRecursively()
                log.debug("Deleted index directory: {}", indexPath)
            } catch (e: Exception) {
                log.error("Failed to delete index directory: $indexPath", e)
            }
        }

        // Clear database state
        try {
            stateRepository.clearAll()
            log.debug("Cleared database state")
        } catch (e: Exception) {
            log.error("Failed to clear database state", e)
        }

        // Clear tracked files
        indexedFiles.clear()

        // Reset status
        indexProgress = IndexProgress(IndexStatus.NOT_STARTED)

        // Trigger fresh indexing (will recreate both indexes)
        indexPathsAsync(paths, watchForChanges)

        log.info("Re-index initiated for project: $projectId")
    }

    private fun calculateSafeMaxChars(): Int {
        val tokenLimit = try {
            getModelTokenLimit(appContext)
        } catch (e: Exception) {
            log.warn("Failed to get model token limit, using default from config: ${e.message}")
            return AppConfig.embedding.maxCharsPerChunk
        }

        val safeTokenLimit = (tokenLimit * 0.8).toInt()

        val safeChars = safeTokenLimit * 4

        val configuredMax = AppConfig.embedding.maxCharsPerChunk
        val minChars = 500

        val calculated = safeChars.coerceIn(minChars, configuredMax)

        log.info(
            "Calculated chunk size: $calculated chars " +
            "(model limit: $tokenLimit tokens, safe limit: $safeTokenLimit tokens, configured max: $configuredMax)"
        )

        return calculated
    }

    private fun detectProjectTypes(root: Path): List<ProjectType> {
        val detected = mutableListOf<ProjectType>()

        Files.list(root).use { stream ->
            val rootFiles = stream.map { it.name }.toList().toSet()
            for (projectType in projectTypes) {
                val hasMarker =
                    projectType.markers.any { marker ->
                        if (marker.contains("*")) {
                            val pattern = marker.replace("*", ".*").toRegex()
                            rootFiles.any { pattern.matches(it) }
                        } else {
                            rootFiles.contains(marker)
                        }
                    }
                if (hasMarker) detected.add(projectType)
            }
        }
        return detected
    }

    /**
     * Determine if a file should be indexed based on extension, name, and exclude patterns.
     * This method supports both code projects and generic user-provided paths.
     */
    private fun isIndexableFile(
        path: Path,
        root: Path,
        detectedTypes: List<ProjectType>,
    ): Boolean {
        val fileName = path.fileName.toString()
        val extension = path.extension.lowercase()
        val relativePath = root.relativize(path).toString().replace('\\', '/')

        // Skip hidden files (starting with .)
        if (fileName.startsWith(".")) {
            log.debug("Skipping hidden file: $fileName")
            return false
        }

        // Skip binary/media files (images, videos, archives, etc.)
        if (extension in AppConfig.indexing.binaryExtensions) {
            log.debug("Skipping binary file: $fileName (extension: $extension)")
            return false
        }

        // Skip system and lock files
        if (fileName in AppConfig.indexing.excludeFileNames) {
            log.debug("Skipping excluded file: $fileName")
            return false
        }

        // Check common exclude patterns
        if (shouldExclude(relativePath, fileName, commonExcludes)) {
            log.debug("Skipping file matching common excludes: $relativePath")
            return false
        }

        // Check project-specific exclude patterns
        for (projectType in detectedTypes) {
            if (shouldExclude(relativePath, fileName, projectType.excludePaths)) {
                log.debug("Skipping file matching ${projectType.name} excludes: $relativePath")
                return false
            }
        }

        // Finally, check if extension is in supported list
        if (extension !in supportedExtensions) {
            log.debug("Skipping unsupported extension: $fileName (extension: $extension)")
            return false
        }

        return true
    }

    private fun shouldExclude(
        relativePath: String,
        fileName: String,
        excludePatterns: Set<String>,
    ): Boolean {
        for (pattern in excludePatterns) {
            when {
                pattern.endsWith("/") -> {
                    val dirPattern = pattern.removeSuffix("/")
                    if (relativePath.contains("/$dirPattern/") || relativePath.startsWith("$dirPattern/")) {
                        return true
                    }
                }
                pattern.contains("*") -> {
                    val regex = pattern.replace(".", "\\.").replace("*", ".*").toRegex()
                    if (regex.matches(fileName) || regex.matches(relativePath)) return true
                }
                else -> {
                    if (fileName == pattern ||
                        relativePath.contains("/$pattern/") ||
                        relativePath.endsWith("/$pattern")
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Check if a directory path should be excluded from indexing.
     */
    private fun shouldExcludeDirectory(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')

        // Check common excludes
        for (pattern in commonExcludes) {
            if (pattern.endsWith("/")) {
                val dirPattern = pattern.removeSuffix("/")
                if (normalized.contains("/$dirPattern/") ||
                    normalized.startsWith("$dirPattern/") ||
                    normalized == dirPattern
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun buildFileHeader(
        relativePath: String,
        filePath: Path,
    ): String = buildString {
        appendLine("FILE: $relativePath")
        appendLine("NAME: ${filePath.fileName}")
        appendLine("EXT: ${filePath.extension.lowercase()}")
        appendLine("---")
    }

    /**
     * Create a TextSegment with standardized metadata for indexing.
     * Uses absolute path for file_path to support multiple project roots.
     */
    private fun createTextSegment(
        chunk: String,
        filePath: Path,
        absolutePath: String,
        chunkIndex: Int,
    ): TextSegment = TextSegment.from(
        chunk,
        Metadata(
            mapOf(
                "file_path" to absolutePath,
                "file_name" to filePath.fileName.toString(),
                "extension" to filePath.extension,
                "chunk_index" to chunkIndex.toString(),
            ),
        ),
    )

    /** Prefer reading as UTF-8; if it fails, try platform default; then ASCII fallback. */
    private fun safeReadText(path: Path): String = try {
        path.readText(defaultCharset)
    } catch (_: Exception) {
        try {
            path.readText(Charset.defaultCharset())
        } catch (_: Exception) {
            String(Files.readAllBytes(path), Charsets.US_ASCII)
        }
    }

    /**
     * Extract text from a file. For PDF files, uses Apache Tika to extract text.
     * For other files, uses standard text reading.
     */
    private fun extractTextFromFile(path: Path): String {
        val extension = path.extension.lowercase()

        return when (extension) {
            "pdf" -> extractPdfText(path)
            else -> safeReadText(path)
        }
    }

    /**
     * Extract text from a PDF file using Apache Tika.
     */
    private fun extractPdfText(path: Path): String = try {
        val parser = AutoDetectParser()
        val handler = BodyContentHandler(-1) // No character limit
        val metadata = TikaMetadata()
        val parseContext = ParseContext()

        FileInputStream(path.toFile()).use { stream ->
            parser.parse(stream, handler, metadata, parseContext)
            handler.toString()
        }
    } catch (e: Exception) {
        log.error("Failed to extract text from PDF: ${path.fileName}", e)
        ""
    }

    private fun tooLargeToIndex(path: Path): Boolean = try {
        Files.size(path) > maxFileBytes
    } catch (_: Exception) {
        false
    }

    /**
     * Simple, fast character-based chunker with a tiny bit of format awareness.
     */
    private fun chunkText(
        s: String,
        maxChars: Int,
        overlapChars: Int,
        extLower: String,
    ): List<String> {
        val effectiveMax =
            when (extLower) {
                "json", "xml" -> max(1500, (maxChars * 0.75).toInt())
                else -> maxChars
            }
        val effectiveOverlap = min(overlapChars, effectiveMax / 4)

        if (s.length <= effectiveMax) return listOf(s)

        val chunks = ArrayList<String>()
        var start = 0
        while (start < s.length) {
            var end = min(start + effectiveMax, s.length)

            // Try to end on a newline boundary if reasonable
            if (end < s.length) {
                val lastNl = s.lastIndexOf('\n', end)
                if (lastNl >= start + (effectiveMax / 2)) {
                    end = min(lastNl + 1, s.length)
                }
            }

            // Safety: avoid zero-advance
            if (end <= start) end = min(start + effectiveMax, s.length)

            chunks.add(s.substring(start, end))
            if (end == s.length) break
            start = max(0, end - effectiveOverlap)
        }
        return chunks
    }

    // ============================================
    // File Watching Methods
    // ============================================

    /**
     * Start watching paths for file changes.
     */
    private fun startWatching(paths: List<Path>) {
        try {
            watchService = FileSystems.getDefault().newWatchService()

            // Register all directories for watching
            paths.forEach { path ->
                if (Files.isDirectory(path)) {
                    registerDirectoryTree(path)
                }
            }

            // Start watch thread
            watchThread = Thread {
                watchForChanges(paths)
            }.apply {
                name = "FileWatcher-$projectId"
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            log.error("Failed to start file watching for project $projectId", e)
        }
    }

    /**
     * Register a directory and all its subdirectories for watching.
     */
    private fun registerDirectoryTree(root: Path) {
        Files.walk(root)
            .asSequence()
            .filter { Files.isDirectory(it) }
            .filter { !shouldExcludeDirectory(root.relativize(it).toString()) }
            .forEach { dir ->
                try {
                    val ws = watchService ?: return@forEach
                    dir.register(
                        ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                    )
                } catch (e: Exception) {
                    log.debug("Failed to register directory for watching: $dir", e)
                }
            }
    }

    /**
     * Watch for file system changes and update index.
     */
    private fun watchForChanges(rootPaths: List<Path>) {
        val watchService = this.watchService ?: return

        while (indexProgress.status == IndexStatus.WATCHING) {
            try {
                val key = watchService.poll(5, TimeUnit.SECONDS) ?: continue

                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue

                    @Suppress("UNCHECKED_CAST")
                    val filename = (event as WatchEvent<Path>).context()
                    val dir = key.watchable() as Path
                    val fullPath = dir.resolve(filename)

                    when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        -> {
                            handleFileChange(fullPath, rootPaths)
                        }
                        StandardWatchEventKinds.ENTRY_DELETE -> {
                            handleFileDelete(fullPath)
                        }
                    }
                }

                key.reset()
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                log.error("Error in file watcher", e)
            }
        }
    }

    /**
     * Handle file creation or modification.
     */
    private fun handleFileChange(
        path: Path,
        rootPaths: List<Path>,
    ) {
        if (!Files.isRegularFile(path)) return

        // Find which root path this file belongs to
        val root = rootPaths.firstOrNull { path.startsWith(it) } ?: return

        val detectedTypes = detectProjectTypes(root)
        if (!isIndexableFile(path, root, detectedTypes)) return

        val absolutePath = path.toAbsolutePath().toString()
        val lastModified = Files.getLastModifiedTime(path).toMillis()

        // Check if file needs reindexing
        val existing = indexedFiles[absolutePath]
        if (existing != null && existing.lastModified == lastModified) {
            return // No change
        }

        log.debug("Indexing changed file: $absolutePath")

        try {
            // Remove old segments for this file
            removeFileFromIndex(absolutePath)

            // Index the new/modified file
            indexSingleFileWithTracking(path, root)

            // Update tracked files
            indexedFiles[absolutePath] = IndexedFileEntry(
                path = absolutePath,
                lastModified = lastModified,
                indexed = LocalDateTime.now(),
            )

            // Save to database
            stateRepository.upsertFile(
                IndexedFileInfo(
                    path = absolutePath,
                    lastModified = lastModified,
                    indexedAt = LocalDateTime.now(),
                    size = Files.size(path),
                ),
            )

            // Update progress
            indexProgress = indexProgress.copy(
                filesIndexed = indexedFiles.size,
                filesTotal = max(indexedFiles.size, indexProgress.filesTotal),
            )
        } catch (e: Exception) {
            log.error("Failed to index changed file: $absolutePath", e)
        }
    }

    /**
     * Handle file deletion.
     */
    private fun handleFileDelete(path: Path) {
        val absolutePath = path.toAbsolutePath().toString()

        indexedFiles.remove(absolutePath)?.let {
            removeFileFromIndex(it.path)
            stateRepository.removeFile(absolutePath)
            log.debug("Removed deleted file from index: ${it.path}")

            // Update progress
            indexProgress = indexProgress.copy(
                filesIndexed = indexedFiles.size,
                filesTotal = max(indexedFiles.size, indexProgress.filesTotal),
            )
        }
    }

    /**
     * Remove all segments for a file from the index.
     * Note: JVectorEmbeddingStore doesn't support removeAll(Filter) yet,
     * so we only remove from Lucene keyword index. The orphaned embeddings
     * in JVector won't affect search results since we rely on Lucene for
     * file filtering.
     *
     * @param absolutePath The absolute path of the file to remove
     */
    private fun removeFileFromIndex(absolutePath: String) {
        try {
            // Remove from keyword index (this is the primary filter for file-based retrieval)
            keywordRetriever.removeFile(absolutePath)
            log.debug("Removed file from keyword index: $absolutePath")

            // Try to remove from vector store if supported
            // JVectorEmbeddingStore doesn't support this yet, so we catch and log
            try {
                val filter = IsEqualTo("file_path", absolutePath)
                embeddingStore.removeAll(filter as Filter)
                log.debug("Removed file from vector index: $absolutePath")
            } catch (e: UnsupportedOperationException) {
                log.debug("Vector store doesn't support removeAll - embeddings for $absolutePath will remain (harmless)")
            } catch (e: dev.langchain4j.exception.UnsupportedFeatureException) {
                log.debug("Vector store doesn't support removeAll - embeddings for $absolutePath will remain (harmless)")
            }
        } catch (e: Exception) {
            log.warn("Failed to remove file from index: $absolutePath", e)
        }
    }

    /**
     * Index a single file with tracking and root context.
     */
    private fun indexSingleFileWithTracking(
        filePath: Path,
        root: Path,
    ) {
        try {
            if (tooLargeToIndex(filePath)) {
                println("  ⚠️  Skipped ${filePath.fileName}: file too large")
                return
            }

            val content = extractTextFromFile(filePath)
            if (content.isBlank()) {
                return
            }

            log.debug("Start indexing file: {}", filePath.fileName)

            val relativePath = root.relativize(filePath).toString()
            val absolutePath = filePath.toAbsolutePath().toString()
            val header = buildFileHeader(relativePath, filePath)
            val body = header + content

            val chunks = chunkText(body, maxCharsPerChunk, chunkOverlap, filePath.extension.lowercase())

            val textSegments = chunks.mapIndexed { idx, chunk ->
                createTextSegment(
                    chunk = chunk,
                    filePath = filePath,
                    absolutePath = absolutePath,
                    chunkIndex = idx,
                )
            }

            // Use HybridIndexer to index in both JVector and Lucene
            if (textSegments.isNotEmpty()) {
                hybridIndexer.indexSegments(textSegments)
            }

            log.debug("  ✅ Indexed: $relativePath (${chunks.size} chunks)")
        } catch (e: Exception) {
            log.error("  ❌ Failed to index ${filePath.fileName}: ${e.message}", e)
        }
    }

    /**
     * Index project with progress updates.
     * Returns the number of files indexed.
     */
    private fun indexProjectWithProgress(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val detectedTypes = detectProjectTypes(root)
        log.debug("📦 Detected project types: ${detectedTypes.joinToString(", ") { it.name }}")

        // Detect changes compared to persisted state
        val changes = detectFileSystemChanges(listOf(root))

        if (!changes.hasChanges) {
            log.debug("✅ No changes detected for project at $root - skipping indexing")
            return 0
        }

        log.debug("📝 Detected changes: ${changes.totalChanges} files (add: ${changes.toAdd.size}, update: ${changes.toUpdate.size}, remove: ${changes.toRemove.size})")

        val indexedFilesCount = AtomicInteger(0)
        val filesToSave = ConcurrentLinkedQueue<IndexedFileInfo>()

        // Remove deleted files from indexes
        if (changes.toRemove.isNotEmpty()) {
            changes.toRemove.forEach { absolutePath ->
                try {
                    removeFileFromIndex(absolutePath)
                    indexedFiles.remove(absolutePath)

                    log.debug("🗑️  Removed deleted file from indexes: $absolutePath")
                } catch (e: Exception) {
                    log.error("Failed to remove file from indexes: $absolutePath", e)
                }
            }
            // Remove from database
            stateRepository.removeFiles(changes.toRemove)
        }

        val filesToIndex = (changes.toAdd + changes.toUpdate).map { Path.of(it.path) }

        runBlocking {
            val semaphore = Semaphore(concurrentIndexingThreads)

            filesToIndex.map { filePath ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        processFileForIndexing(filePath, root, indexedFilesCount, filesToSave, filesToIndex.size)
                    }
                }
            }.awaitAll()
        }

        if (filesToSave.isNotEmpty()) {
            stateRepository.upsertFiles(filesToSave.toList())
        }

        saveState()

        log.debug("✅ Indexing completed: ${indexedFilesCount.get()} files processed")
        return indexedFilesCount.get()
    }

    /**
     * Process a single file for indexing (used in parallel processing).
     * This method is thread-safe and designed to be called concurrently.
     */
    private fun processFileForIndexing(
        filePath: Path,
        root: Path,
        indexedFilesCount: AtomicInteger,
        filesToSave: ConcurrentLinkedQueue<IndexedFileInfo>,
        totalFiles: Int
    ) {
        try {
            if (!Files.exists(filePath)) {
                log.debug("⚠️  File no longer exists, skipping: $filePath")
                return
            }

            indexSingleFileWithTracking(filePath, root)

            val absolutePath = filePath.toAbsolutePath().toString()
            val lastModified = Files.getLastModifiedTime(filePath).toMillis()
            val size = Files.size(filePath)

            indexedFiles[absolutePath] = IndexedFileEntry(
                path = absolutePath,
                lastModified = lastModified,
                indexed = LocalDateTime.now(),
            )

            filesToSave.add(
                IndexedFileInfo(
                    path = absolutePath,
                    lastModified = lastModified,
                    indexedAt = LocalDateTime.now(),
                    size = size,
                ),
            )

            val count = indexedFilesCount.incrementAndGet()

            // Update progress every 10 files
            if (count % 10 == 0) {
                indexProgress = indexProgress.copy(filesIndexed = count)

                try {
                    EventBus.post(
                        IndexingInProgressEvent(
                            projectId = projectId,
                            projectName = projectName,
                            filesIndexed = count,
                            totalFiles = totalFiles
                        )
                    )
                } catch (e: Exception) {
                    log.error("Failed to publish indexing progress event", e)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to process file ${filePath}: ${e.message}", e)
        }
    }

    /**
     * Checks if the embedding model is available by attempting a dry-run embed.
     * Throws ModelNotFoundException if the model is not available.
     */
    private fun checkEmbeddingModelAvailable() {
        try {
            embeddingModel.embed(TextSegment.from("ping"))
        } catch (e: Exception) {
            if (e is ModelNotFoundException || e.message?.contains("model not found", ignoreCase = true) == true) {
                log.error("Embedding model not found: ${e.message}")
                throw ModelNotFoundException("Embedding model not found: ${e.message}")
            } else {
                throw e
            }
        }
    }
}
