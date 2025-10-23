/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import io.askimo.core.config.AppConfig
import io.askimo.core.session.Session
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

/**
 * Indexes project files into a pgvector-backed store and exposes basic embedding and
 * similarity search utilities.
 */
class PgVectorIndexer(
    private val projectId: String,
    private val session: Session,
) {
    private val projectTable: String = "${AppConfig.pgVector.table}__${slug(projectId)}"

    private fun slug(s: String): String = s.lowercase().replace("""[^a-z0-9]+""".toRegex(), "_").trim('_')

    private val maxCharsPerChunk = AppConfig.embedding.max_chars_per_chunk
    private val chunkOverlap = AppConfig.embedding.chunk_overlap
    private val perRequestSleepMs = AppConfig.throttle.per_request_sleep_ms
    private val retryAttempts = AppConfig.retry.attempts
    private val retryBaseDelayMs = AppConfig.retry.base_delay_ms
    private val maxFileBytes = AppConfig.indexing.max_file_bytes

    private val defaultCharset: Charset = Charsets.UTF_8

    private val supportedExtensions =
        setOf(
            "java",
            "kt",
            "kts",
            "py",
            "js",
            "ts",
            "jsx",
            "tsx",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "cs",
            "rb",
            "php",
            "swift",
            "scala",
            "groovy",
            "sh",
            "bash",
            "yaml",
            "yml",
            "json",
            "xml",
            "md",
            "txt",
            "gradle",
            "properties",
            "toml",
        )

    // ---------- Project type markers / excludes ----------
    private data class ProjectType(
        val name: String,
        val markers: Set<String>,
        val excludePaths: Set<String>,
    )

    private val projectTypes =
        listOf(
            // Java/Kotlin projects
            ProjectType(
                name = "Gradle",
                markers = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradlew"),
                excludePaths =
                    setOf(
                        "build/",
                        ".gradle/",
                        "out/",
                        "bin/",
                        ".kotlintest/",
                        ".kotlin/",
                    ),
            ),
            ProjectType(
                name = "Maven",
                markers = setOf("pom.xml", "mvnw"),
                excludePaths =
                    setOf(
                        "target/",
                        ".mvn/",
                        "out/",
                        "bin/",
                    ),
            ),
            // JavaScript/TypeScript projects
            ProjectType(
                name = "Node.js",
                markers = setOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml"),
                excludePaths =
                    setOf(
                        "node_modules/",
                        "dist/",
                        "build/",
                        ".next/",
                        ".nuxt/",
                        "out/",
                        "coverage/",
                        ".cache/",
                        ".parcel-cache/",
                        ".turbo/",
                        ".vite/",
                    ),
            ),
            // Python projects
            ProjectType(
                name = "Python",
                markers = setOf("requirements.txt", "setup.py", "pyproject.toml", "Pipfile", "poetry.lock"),
                excludePaths =
                    setOf(
                        "__pycache__/",
                        "*.pyc",
                        "*.pyo",
                        "*.pyd",
                        ".pytest_cache/",
                        ".mypy_cache/",
                        ".tox/",
                        "venv/",
                        "env/",
                        ".venv/",
                        ".env/",
                        "dist/",
                        "build/",
                        "*.egg-info/",
                        ".eggs/",
                    ),
            ),
            // Go projects
            ProjectType(
                name = "Go",
                markers = setOf("go.mod", "go.sum"),
                excludePaths =
                    setOf(
                        "vendor/",
                        "bin/",
                        "pkg/",
                    ),
            ),
            // Rust projects
            ProjectType(
                name = "Rust",
                markers = setOf("Cargo.toml", "Cargo.lock"),
                excludePaths =
                    setOf(
                        "target/",
                        "Cargo.lock",
                    ),
            ),
            // Ruby projects
            ProjectType(
                name = "Ruby",
                markers = setOf("Gemfile", "Gemfile.lock", "Rakefile"),
                excludePaths =
                    setOf(
                        "vendor/",
                        ".bundle/",
                        "tmp/",
                        "log/",
                    ),
            ),
            // PHP projects
            ProjectType(
                name = "PHP/Composer",
                markers = setOf("composer.json", "composer.lock"),
                excludePaths =
                    setOf(
                        "vendor/",
                        "var/cache/",
                        "var/log/",
                    ),
            ),
            // .NET projects
            ProjectType(
                name = ".NET",
                markers = setOf("*.csproj", "*.sln", "*.fsproj", "*.vbproj"),
                excludePaths =
                    setOf(
                        "bin/",
                        "obj/",
                        "packages/",
                        ".vs/",
                        "Debug/",
                        "Release/",
                    ),
            ),
        )

    private val commonExcludes =
        setOf(
            ".git/",
            ".svn/",
            ".hg/",
            ".idea/",
            ".vscode/",
            ".DS_Store",
            "*.log",
            "*.tmp",
            "*.temp",
            "*.swp",
            "*.bak",
            ".history/",
        )

    private fun buildEmbeddingModel(): EmbeddingModel = getEmbeddingModel(session.getActiveProvider())

    private val embeddingModel: EmbeddingModel by lazy { buildEmbeddingModel() }

    private val dimension: Int by lazy {
        AppConfig.embedding.preferred_dim ?: embeddingModel.dimension()
    }

    /**
     * Cached embedding store instance to avoid recreating it multiple times.
     * Initialized lazily when first accessed.
     */
    private val embeddingStore: EmbeddingStore<TextSegment> by lazy { newStore() }

    private fun newStore(): EmbeddingStore<TextSegment> {
        val embeddingStore =
            PgVectorEmbeddingStore
                .builder()
                .host(extractHost(AppConfig.pgVector.url))
                .port(extractPort(AppConfig.pgVector.url))
                .database(extractDatabase(AppConfig.pgVector.url))
                .user(AppConfig.pgVector.user)
                .password(AppConfig.pgVector.password)
                .table(projectTable)
                .dimension(dimension)
                .build()
        ensureIndexes(AppConfig.pgVector.url, AppConfig.pgVector.user, AppConfig.pgVector.password, projectTable)
        return embeddingStore
    }

    fun indexProject(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val detectedTypes = detectProjectTypes(root)
        println("📦 Detected project types: ${detectedTypes.joinToString(", ") { it.name }}")

        // Use cached embeddingStore instead of creating new one
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
                        // Empty text file; ignore quietly
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
                            println("  ⚠️  Chunk failure ${filePath.fileName}[$idx/$total]: ${e.message}")
                            debug(e)
                        }
                    }

                    if (fileSucceeded) {
                        indexedFiles++
                        if (indexedFiles % 10 == 0) {
                            println("  ✅ Indexed $indexedFiles files, $addedSegments segments → table=$projectTable")
                        }
                    } else {
                        // all chunks failed, treat as skip
                        skippedFiles++
                    }
                } catch (e: Exception) {
                    skippedFiles++
                    println("  ⚠️  Skipped ${filePath.fileName}: ${e.message}")
                    debug(e)
                }
            }

        println(
            "📊 Indexing done: filesIndexed=$indexedFiles, segmentsAdded=$addedSegments, filesSkipped=$skippedFiles, failedChunks=$failedChunks → $projectTable",
        )
        return indexedFiles
    }

    fun embed(text: String): List<Float> =
        embeddingModel
            .embed(text)
            .content()
            .vector()
            .toList()

    fun similaritySearch(
        embedding: List<Float>,
        topK: Int,
    ): List<String> {
        // Use cached embeddingStore instead of creating new one
        val queryEmbedding = Embedding.from(embedding.toFloatArray())
        val results =
            embeddingStore.search(
                dev.langchain4j.store.embedding.EmbeddingSearchRequest
                    .builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .build(),
            )
        return results.matches().map { it.embedded().text() }
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

        // Skip hidden files
        if (fileName.startsWith(".")) return false

        // Common excludes
        if (shouldExclude(relativePath, fileName, commonExcludes)) return false

        // Project-specific excludes
        for (projectType in detectedTypes) {
            if (shouldExclude(relativePath, fileName, projectType.excludePaths)) return false
        }

        // Extension allowlist
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
    ): String =
        buildString {
            appendLine("FILE: $relativePath")
            appendLine("NAME: ${filePath.fileName}")
            appendLine("EXT: ${filePath.extension.lowercase()}")
            appendLine("---")
        }

    /** Prefer reading as UTF-8; if it fails, try platform default; then ASCII fallback. */
    private fun safeReadText(path: Path): String =
        try {
            path.readText(defaultCharset)
        } catch (_: Exception) {
            try {
                path.readText(Charset.defaultCharset())
            } catch (_: Exception) {
                String(Files.readAllBytes(path), Charsets.US_ASCII)
            }
        }

    private fun tooLargeToIndex(path: Path): Boolean =
        try {
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
                "json", "xml" -> max(1500, (maxChars * 0.75).toInt()) // tighten for dense formats
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
        // Covers EOF, timeouts, resets, and common gateway errors from local HTTP servers or proxies
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

    private fun extractHost(jdbcUrl: String): String {
        // jdbc:postgresql://localhost:5432/askimo
        val regex = """://([^:/@]+)""".toRegex()
        return regex.find(jdbcUrl)?.groupValues?.get(1) ?: "localhost"
    }

    private fun extractPort(jdbcUrl: String): Int {
        // jdbc:postgresql://localhost:5432/askimo
        val regex = """:(\d+)/""".toRegex()
        return regex
            .find(jdbcUrl)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: 5432
    }

    private fun extractDatabase(jdbcUrl: String): String {
        val parts = jdbcUrl.split("/")
        return parts.lastOrNull()?.split("?")?.firstOrNull() ?: "askimo"
    }

    private fun ensureIndexes(
        pgUrl: String,
        user: String,
        pass: String,
        table: String,
    ) {
        val stmts =
            listOf(
                "CREATE EXTENSION IF NOT EXISTS vector;",
                "CREATE EXTENSION IF NOT EXISTS pg_trgm;",
                // project_id
                """CREATE INDEX IF NOT EXISTS ${table}_proj_idx
           ON $table ( ((metadata)::jsonb ->> 'project_id') );""",
                // file_name (lowered)
                """CREATE INDEX IF NOT EXISTS ${table}_file_name_idx
           ON $table ( lower(((metadata)::jsonb ->> 'file_name')) );""",
                // file_path btree
                """CREATE INDEX IF NOT EXISTS ${table}_file_path_idx
           ON $table ( ((metadata)::jsonb ->> 'file_path') );""",
                // full metadata GIN
                """CREATE INDEX IF NOT EXISTS ${table}_meta_gin
           ON $table USING GIN ( ((metadata)::jsonb) jsonb_path_ops );""",
                // trigram on file_path
                """CREATE INDEX IF NOT EXISTS ${table}_file_path_trgm
           ON $table USING GIN ( ((metadata)::jsonb ->> 'file_path') gin_trgm_ops );""",
                // ANN index on embedding (requires ANALYZE and some rows to be effective)
                """CREATE INDEX IF NOT EXISTS ${table}_embedding_ivfflat
           ON $table USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);""",
                "ANALYZE $table;",
            )

        DriverManager.getConnection(pgUrl, user, pass).use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                for (sql in stmts) {
                    try {
                        st.execute(sql.trimIndent())
                    } catch (e: Exception) {
                        info("Index ensure failed for: ${sql.lineSequence().firstOrNull()} → ${e.message}")
                        debug(e)
                    }
                }
            }
        }
    }
}
