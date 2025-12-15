/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.lucence

import dev.langchain4j.community.rag.content.retriever.lucene.LuceneEmbeddingStore
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.Filter
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo
import io.askimo.core.config.AppConfig
import io.askimo.core.config.ProjectType
import io.askimo.core.context.AppContext
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.project.getEmbeddingModel
import io.askimo.core.util.AskimoHome
import org.apache.lucene.store.FSDirectory
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

/**
 * Indexes project files into a Lucene-backed store and exposes basic embedding and
 * similarity search utilities.
 *
 * This is a lightweight alternative to PgVectorIndexer that doesn't require Docker/PostgreSQL.
 * Uses file-based Lucene indexes stored locally.
 */
class LuceneIndexer(
    private val projectId: String,
    private val appContext: AppContext,
) {
    private val log = logger<LuceneIndexer>()

    private fun slug(s: String): String = s.lowercase().replace("""[^a-z0-9]+""".toRegex(), "_").trim('_')

    private val indexPath: Path = AskimoHome.base().resolve("lucene-indexes").resolve(slug(projectId))

    private val maxCharsPerChunk = AppConfig.embedding.maxCharsPerChunk
    private val chunkOverlap = AppConfig.embedding.chunkOverlap
    private val perRequestSleepMs = AppConfig.throttle.perRequestSleepMs
    private val retryAttempts = AppConfig.retry.attempts
    private val retryBaseDelayMs = AppConfig.retry.baseDelayMs
    private val maxFileBytes = AppConfig.indexing.maxFileBytes

    private val defaultCharset: Charset = Charsets.UTF_8

    private val supportedExtensions = AppConfig.indexing.supportedExtensions

    private val projectTypes = AppConfig.indexing.projectTypes

    private val commonExcludes = AppConfig.indexing.commonExcludes

    private fun buildEmbeddingModel(): EmbeddingModel = getEmbeddingModel(appContext)

    /**
     * The embedding model used for creating embeddings.
     * Exposed for creating ContentRetriever in ChatSessionService.
     */
    val embeddingModel: EmbeddingModel by lazy { buildEmbeddingModel() }

    private val dimension: Int by lazy {
        AppConfig.embedding.preferredDim ?: embeddingModel.dimension()
    }

    /**
     * Cached embedding store instance to avoid recreating it multiple times.
     * Initialized lazily when first accessed.
     * Exposed for creating ContentRetriever in ChatSessionService.
     */
    val embeddingStore: EmbeddingStore<TextSegment> by lazy { newStore() }

    private fun newStore(): EmbeddingStore<TextSegment> {
        // Ensure index directory exists
        Files.createDirectories(indexPath)

        // Create Lucene FSDirectory from Path
        val luceneDirectory = FSDirectory.open(indexPath)

        return LuceneEmbeddingStore.builder()
            .directory(luceneDirectory)
            .build()
    }

    /**
     * Check if the Lucene index has any content.
     * Returns false if the index is empty or doesn't exist.
     */
    fun isIndexPopulated(): Boolean {
        return try {
            val testEmbedding = embed("test")
            val results = similaritySearch(testEmbedding, 1)
            results.isNotEmpty()
        } catch (e: Exception) {
            log.debug("Index check failed, assuming empty: ${e.message}")
            false
        }
    }

    /**
     * Index multiple paths (files or directories) recursively.
     * This is used when a project has multiple indexed paths configured.
     *
     * @param paths List of paths to index (can be files or directories)
     * @return Number of files indexed
     */
    fun indexPaths(paths: List<Path>): Int {
        var totalIndexedFiles = 0

        paths.forEach { path ->
            if (!Files.exists(path)) {
                println("⚠️  Path does not exist, skipping: $path")
                return@forEach
            }

            totalIndexedFiles += if (Files.isDirectory(path)) {
                indexProject(path)
            } else if (Files.isRegularFile(path)) {
                indexSingleFile(path)
                1
            } else {
                0
            }
        }

        return totalIndexedFiles
    }

    /**
     * Index a single file.
     *
     * @param filePath Path to the file to index
     */
    private fun indexSingleFile(filePath: Path) {
        try {
            if (tooLargeToIndex(filePath)) {
                println("  ⚠️  Skipped ${filePath.fileName}: file too large")
                return
            }

            val content = safeReadText(filePath)
            if (content.isBlank()) {
                return
            }

            val fileName = filePath.fileName.toString()
            val header = buildFileHeader(fileName, filePath)
            val body = header + content

            val chunks = chunkText(body, maxCharsPerChunk, chunkOverlap, filePath.extension.lowercase())

            chunks.forEach { chunk ->
                val textSegment = TextSegment.from(chunk, createMetadata(fileName, filePath))
                val embedding = embeddingModel.embed(textSegment).content()
                embeddingStore.add(embedding, textSegment)
            }

            println("  ✅ Indexed: $fileName (${chunks.size} chunks)")
        } catch (e: Exception) {
            println("  ❌ Failed to index ${filePath.fileName}: ${e.message}")
        }
    }

    /**
     * Indexes an entire project directory recursively.
     *
     * @param root Root directory of the project to index
     * @return Number of files indexed
     */
    fun indexProject(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val detectedTypes = detectProjectTypes(root)
        println("📦 Detected project types: ${detectedTypes.joinToString(", ") { it.name }}")

        val embeddingStore = this.embeddingStore

        var indexedFiles = 0
        var addedSegments = 0
        var skippedFiles = 0
        var failedChunks = 0

        Files
            .walk(root)
            .asSequence()
            .filter { it.isRegularFile() }
            .filter { isIndexableFile(it, root, detectedTypes) }
            .forEach { filePath ->
                try {
                    if (tooLargeToIndex(filePath)) {
                        println(
                            "  ⚠️  Skipped ${filePath.fileName}: file > $maxFileBytes bytes (raise ASKIMO_EMBED_MAX_FILE_BYTES or pre-trim)",
                        )
                        skippedFiles++
                        return@forEach
                    }

                    val content = safeReadText(filePath)
                    if (content.isBlank()) {
                        return@forEach
                    }

                    val relativePath = root.relativize(filePath).toString().replace('\\', '/')
                    val header = buildFileHeader(relativePath, filePath)
                    val body = header + content

                    val chunks = chunkText(body, maxCharsPerChunk, chunkOverlap, filePath.extension.lowercase())
                    val total = chunks.size

                    var fileSucceeded = false

                    chunks.forEachIndexed { idx, chunk ->
                        val seg =
                            TextSegment.from(
                                chunk,
                                Metadata(
                                    mapOf(
                                        "project_id" to projectId,
                                        "file_path" to relativePath,
                                        "file_name" to filePath.fileName.toString(),
                                        "extension" to filePath.extension,
                                        "chunk_index" to idx.toString(),
                                        "chunk_total" to total.toString(),
                                    ),
                                ),
                            )

                        try {
                            val embedding =
                                withRetry(retryAttempts, retryBaseDelayMs) {
                                    embeddingModel.embed(seg)
                                }
                            embeddingStore.add(embedding.content(), seg)
                            addedSegments++
                            fileSucceeded = true
                            throttle()
                        } catch (e: Throwable) {
                            failedChunks++
                            log.displayError("  ⚠️  Chunk failure ${filePath.fileName}[$idx/$total]: ${e.message}", e)
                        }
                    }

                    if (fileSucceeded) {
                        indexedFiles++
                        if (indexedFiles % 10 == 0) {
                            println("  ✅ Indexed $indexedFiles files, $addedSegments segments → Lucene index at $indexPath")
                        }
                    } else {
                        skippedFiles++
                    }
                } catch (e: Exception) {
                    skippedFiles++
                    println("  ⚠️  Skipped ${filePath.fileName}: ${e.message}")
                    log.error("Failed to index ${filePath.fileName}", e)
                }
            }

        println(
            "📊 Indexing done: filesIndexed=$indexedFiles, segmentsAdded=$addedSegments, filesSkipped=$skippedFiles, failedChunks=$failedChunks → $indexPath",
        )
        return indexedFiles
    }

    fun embed(text: String): List<Float> = embeddingModel
        .embed(text)
        .content()
        .vector()
        .toList()

    fun similaritySearch(
        embedding: List<Float>,
        topK: Int,
    ): List<String> {
        val queryEmbedding = Embedding.from(embedding.toFloatArray())
        val results =
            embeddingStore.search(
                EmbeddingSearchRequest
                    .builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .build(),
            )
        return results.matches().map { it.embedded().text() }
    }

    /**
     * Removes all chunks for a specific file from the embedding store.
     */
    fun removeFileFromIndex(relativePath: String) {
        try {
            // Use Lucene's metadata filtering to remove documents
            val filter = Filter.and(
                IsEqualTo("project_id", projectId),
                IsEqualTo("file_path", relativePath),
            )

            embeddingStore.removeAll(filter)
            log.debug("Removed chunks for file: $relativePath")
        } catch (e: Exception) {
            log.displayError("Failed to remove file from index: $relativePath", e)
        }
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

    private fun isIndexableFile(
        path: Path,
        root: Path,
        detectedTypes: List<ProjectType>,
    ): Boolean {
        val fileName = path.fileName.toString()
        val relativePath = root.relativize(path).toString().replace('\\', '/')

        if (fileName.startsWith(".")) return false

        if (shouldExclude(relativePath, fileName, commonExcludes)) return false

        for (projectType in detectedTypes) {
            if (shouldExclude(relativePath, fileName, projectType.excludePaths)) return false
        }

        return path.extension.lowercase() in supportedExtensions
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
     * Create metadata for a text segment.
     */
    private fun createMetadata(
        fileName: String,
        filePath: Path,
    ): Metadata = Metadata(
        mapOf(
            "project_id" to projectId,
            "file_path" to fileName,
            "file_name" to fileName,
            "extension" to filePath.extension,
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

    private fun tooLargeToIndex(path: Path): Boolean = try {
        Files.size(path) > maxFileBytes
    } catch (_: Exception) {
        false
    }

    /**
     * Simple, fast character-based chunker with a tiny bit of format awareness.
     * - JSON/XML get slightly smaller default chunks because they often lack whitespace.
     * - We try to break at a newline boundary to reduce mid-token splits.
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

    private fun throttle() {
        if (perRequestSleepMs > 0) {
            try {
                Thread.sleep(perRequestSleepMs)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun <T> withRetry(
        attempts: Int = 4,
        baseDelayMs: Long = 150,
        block: () -> T,
    ): T {
        var last: Throwable? = null
        for (i in 1..attempts) {
            try {
                return block()
            } catch (e: Throwable) {
                last = e
                if (!isTransientEmbeddingError(e) || i == attempts) break
                val backoff = baseDelayMs * (1L shl (i - 1)) // 150, 300, 600, 1200...
                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                }
            }
        }
        throw last ?: IllegalStateException("Unknown embedding error")
    }

    private fun isTransientEmbeddingError(e: Throwable): Boolean {
        val msg = (e.message ?: "").lowercase()
        return msg.contains("eof") ||
            msg.contains("timeout") ||
            msg.contains("timed out") ||
            msg.contains("connection reset") ||
            msg.contains("connection refused") ||
            msg.contains("bad gateway") ||
            msg.contains("service unavailable") ||
            msg.contains("502") ||
            msg.contains("503") ||
            msg.contains("504")
    }
}
