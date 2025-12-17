/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.jvector

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
import io.askimo.core.logging.logger
import io.askimo.core.project.getEmbeddingModel
import io.askimo.core.rag.lucene.LuceneKeywordRetriever
import io.askimo.core.util.AskimoHome
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
class JVectorIndexer private constructor(
    private val projectId: String,
    private val appContext: AppContext,
) {
    companion object {
        private val log = logger<JVectorIndexer>()

        /**
         * Cache of indexer instances per project.
         * Ensures only one indexer exists per project to avoid duplicate file watchers.
         */
        private val instances = ConcurrentHashMap<String, JVectorIndexer>()

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    try {
                        log.info("Shutdown hook triggered - cleaning up all JVectorIndexer instances")
                        shutdownAll()
                        log.info("Successfully cleaned up all indexers")
                    } catch (e: Exception) {
                        log.error("Failed to cleanup indexers on shutdown", e)
                    }
                },
            )
        }

        /**
         * Get or create a JVectorIndexer instance for a project.
         * Instances are cached per project ID.
         *
         * @param projectId The project ID
         * @param appContext The application context
         * @return Cached or new JVectorIndexer instance
         */
        fun getInstance(
            projectId: String,
            appContext: AppContext,
        ): JVectorIndexer = instances.getOrPut(projectId) {
            log.debug("Creating new JVectorIndexer instance for project: $projectId")
            JVectorIndexer(projectId, appContext)
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
            log.debug("Shutting down all JVectorIndexer instances")
            instances.values.forEach { it.stopWatching() }
            instances.clear()
        }
    }

    private fun slug(s: String): String = s.lowercase().replace("""[^a-z0-9]+""".toRegex(), "_").trim('_')

    private val indexPath: Path = AskimoHome.base().resolve("projects").resolve(projectId).resolve("jvector-indexes")

    private val maxCharsPerChunk = AppConfig.embedding.maxCharsPerChunk
    private val chunkOverlap = AppConfig.embedding.chunkOverlap
    private val maxFileBytes = AppConfig.indexing.maxFileBytes

    private val defaultCharset: Charset = Charsets.UTF_8

    // Track indexing status
    @Volatile
    private var indexProgress = IndexProgress(IndexStatus.NOT_STARTED)

    // Track indexed files with their modification times
    private val indexedFiles = ConcurrentHashMap<String, IndexedFileEntry>()

    // File watcher for detecting changes
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null

    /**
     * Get current indexing status/progress.
     */
    fun getIndexProgress(): IndexProgress = indexProgress

    /**
     * Ensure the index is ready by starting indexing if needed.
     * This method encapsulates the indexing decision logic.
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
                log.debug("Starting indexing for project $projectId")
                indexPathsAsync(paths, watchForChanges)
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

    private fun newStore(): EmbeddingStore<TextSegment> {
        Files.createDirectories(indexPath)

        // Get dimension from embedding model
        val dimension = embeddingModel.dimension()

        // Use persistent storage path: ~/.askimo/jvector-indexes/{project_id}/store
        val persistencePath = indexPath.resolve("store").toString()

        log.info("Creating JVector embedding store at: $persistencePath (dimension: $dimension)")

        // JVector embedding store with file-based persistence
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

        Thread {
            try {
                val estimatedTotal = countIndexableFiles(paths)
                indexProgress = IndexProgress(
                    status = IndexStatus.INDEXING,
                    filesTotal = estimatedTotal,
                )

                log.debug("Starting async indexing for project $projectId: ${paths.size} paths, ~$estimatedTotal files")

                val indexed = indexPathsSync(paths)

                if (watchForChanges) {
                    // Start watching for changes
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
            } catch (e: Exception) {
                log.error("Indexing failed for project $projectId", e)
                indexProgress = IndexProgress(
                    status = IndexStatus.FAILED,
                    error = e.message ?: "Unknown error",
                )
            }
        }.apply {
            name = "JVectorIndexer-$projectId"
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
                    .filter { isIndexableFile(it, path, detectedTypes) }
                    .filter { !tooLargeToIndex(it) }
                    .count()
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

        // Clear tracked files
        indexedFiles.clear()

        // Reset status
        indexProgress = IndexProgress(IndexStatus.NOT_STARTED)

        // Trigger fresh indexing (will recreate both indexes)
        indexPathsAsync(paths, watchForChanges)

        log.info("Re-index initiated for project: $projectId")
    }

    // ---------- Internals ----------
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
     */
    private fun createTextSegment(
        chunk: String,
        filePath: Path,
        relativePath: String,
        chunkIndex: Int,
    ): TextSegment = TextSegment.from(
        chunk,
        Metadata(
            mapOf(
                "file_path" to relativePath,
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

        val relativePath = root.relativize(path).toString()
        val lastModified = Files.getLastModifiedTime(path).toMillis()

        // Check if file needs reindexing
        val existing = indexedFiles[relativePath]
        if (existing != null && existing.lastModified == lastModified) {
            return // No change
        }

        log.debug("Indexing changed file: $relativePath")

        try {
            // Remove old segments for this file
            removeFileFromIndex(relativePath)

            // Index the new/modified file
            indexSingleFileWithTracking(path, root)

            // Update tracked files
            indexedFiles[relativePath] = IndexedFileEntry(
                path = relativePath,
                lastModified = lastModified,
                indexed = LocalDateTime.now(),
            )

            // Update progress
            indexProgress = indexProgress.copy(
                filesIndexed = indexedFiles.size,
                filesTotal = max(indexedFiles.size, indexProgress.filesTotal),
            )
        } catch (e: Exception) {
            log.error("Failed to index changed file: $relativePath", e)
        }
    }

    /**
     * Handle file deletion.
     */
    private fun handleFileDelete(path: Path) {
        val fileName = path.fileName.toString()
        indexedFiles.remove(fileName)?.let {
            removeFileFromIndex(it.path)
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
     */
    private fun removeFileFromIndex(relativePath: String) {
        try {
            val filter = IsEqualTo("file_path", relativePath)
            embeddingStore.removeAll(filter as Filter)
        } catch (e: Exception) {
            log.debug("Failed to remove file from index: $relativePath", e)
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

            val relativePath = root.relativize(filePath).toString()
            val header = buildFileHeader(relativePath, filePath)
            val body = header + content

            val chunks = chunkText(body, maxCharsPerChunk, chunkOverlap, filePath.extension.lowercase())

            val textSegments = mutableListOf<TextSegment>()

            chunks.forEachIndexed { idx, chunk ->
                val textSegment = createTextSegment(
                    chunk = chunk,
                    filePath = filePath,
                    relativePath = relativePath,
                    chunkIndex = idx,
                )

                // Add to vector store (JVector)
                val embedding = embeddingModel.embed(textSegment).content()
                embeddingStore.add(embedding, textSegment)

                // Collect for batch keyword indexing
                textSegments.add(textSegment)
            }

            // Batch index in Lucene for keyword search
            if (textSegments.isNotEmpty()) {
                keywordRetriever.indexSegments(textSegments)
            }

            log.debug("  ✅ Indexed: $relativePath (${chunks.size} chunks)")
        } catch (e: Exception) {
            log.error("  ❌ Failed to index ${filePath.fileName}: ${e.message}", e)
        }
    }

    /**
     * Index project with progress updates.
     */
    private fun indexProjectWithProgress(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val detectedTypes = detectProjectTypes(root)
        log.debug("📦 Detected project types: ${detectedTypes.joinToString(", ") { it.name }}")

        var indexedFilesCount = 0

        Files.walk(root)
            .asSequence()
            .filter { it.isRegularFile() }
            .filter { isIndexableFile(it, root, detectedTypes) }
            .forEach { filePath ->
                indexSingleFileWithTracking(filePath, root)

                val relativePath = root.relativize(filePath).toString()
                val lastModified = Files.getLastModifiedTime(filePath).toMillis()

                // Track indexed file
                indexedFiles[relativePath] = IndexedFileEntry(
                    path = relativePath,
                    lastModified = lastModified,
                    indexed = LocalDateTime.now(),
                )

                indexedFilesCount++

                // Update progress every 10 files
                if (indexedFilesCount % 10 == 0) {
                    indexProgress = indexProgress.copy(filesIndexed = indexedFilesCount)
                }
            }

        return indexedFilesCount
    }
}
