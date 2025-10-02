/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import io.askimo.core.session.Session
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

/**
 * Indexes project files into a pgvector-backed store and exposes basic embedding and
 * similarity search utilities.
 *
 * Responsibility:
 * - Scan a project directory, select indexable files, and compute text embeddings
 * - Persist embeddings and lightweight metadata to a per-project table in Postgres
 * - Provide helpers to embed ad hoc text and run vector similarity searches for retrieval
 */
class PgVectorIndexer(
    private val pgUrl: String = System.getenv("ASKIMO_PG_URL") ?: "jdbc:postgresql://localhost:5432/askimo",
    private val pgUser: String = System.getenv("ASKIMO_PG_USER") ?: "askimo",
    private val pgPass: String = System.getenv("ASKIMO_PG_PASS") ?: "askimo",
    private val table: String = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings",
    private val projectId: String,
    private val preferredDim: Int? = null,
    private val session: Session,
) {
    private val projectTable: String = "${table}__${slug(projectId)}"

    private fun slug(s: String): String = s.lowercase().replace("""[^a-z0-9]+""".toRegex(), "_").trim('_')

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

    // Project type markers
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

    // Common excludes for all project types
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

    // capture the actual dimension ONCE from the real model unless user overrode it
    private val dimension: Int by lazy {
        preferredDim ?: buildEmbeddingModel().dimension()
    }

    private fun newStore(): EmbeddingStore<TextSegment> =
        PgVectorEmbeddingStore
            .builder()
            .host(extractHost(pgUrl))
            .port(extractPort(pgUrl))
            .database(extractDatabase(pgUrl))
            .user(pgUser)
            .password(pgPass)
            .table(projectTable)
            .dimension(dimension)
            .build()

    fun indexProject(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val detectedTypes = detectProjectTypes(root)
        println("📦 Detected project types: ${detectedTypes.joinToString(", ") { it.name }}")

        val embeddingModel = buildEmbeddingModel()
        val embeddingStore = newStore()

        var indexedCount = 0

        Files
            .walk(root)
            .asSequence()
            .filter { it.isRegularFile() }
            .filter { isIndexableFile(it, root, detectedTypes) }
            .forEach { filePath ->
                try {
                    val content = filePath.readText()
                    if (content.isNotBlank()) {
                        val relativePath = root.relativize(filePath).toString()

                        val segment =
                            TextSegment.from(
                                content,
                                Metadata(
                                    mapOf(
                                        "project_id" to projectId,
                                        "file_path" to relativePath,
                                        "file_name" to filePath.fileName.toString(),
                                        "extension" to filePath.extension,
                                    ),
                                ),
                            )

                        val embedding = embeddingModel.embed(segment)
                        embeddingStore.add(embedding.content(), segment)

                        indexedCount++
                        if (indexedCount % 10 == 0) {
                            println("  Indexed $indexedCount files into $projectTable …")
                        }
                    }
                } catch (e: Exception) {
                    println("  ⚠️  Skipped ${filePath.fileName}: ${e.message}")
                }
            }

        return indexedCount
    }

    private fun detectProjectTypes(root: Path): List<ProjectType> {
        val detected = mutableListOf<ProjectType>()

        // Check for project type markers in the root directory
        Files.list(root).use { stream ->
            val rootFiles =
                stream
                    .map { it.name }
                    .toList()
                    .toSet()

            for (projectType in projectTypes) {
                // Check if any marker file exists
                val hasMarker =
                    projectType.markers.any { marker ->
                        if (marker.contains("*")) {
                            // Handle wildcards (e.g., "*.csproj")
                            val pattern = marker.replace("*", ".*").toRegex()
                            rootFiles.any { pattern.matches(it) }
                        } else {
                            rootFiles.contains(marker)
                        }
                    }

                if (hasMarker) {
                    detected.add(projectType)
                }
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

        // Check against common excludes
        if (shouldExclude(relativePath, fileName, commonExcludes)) {
            return false
        }

        // Check against project-specific excludes
        for (projectType in detectedTypes) {
            if (shouldExclude(relativePath, fileName, projectType.excludePaths)) {
                return false
            }
        }

        // Check file extension
        return path.extension.lowercase() in supportedExtensions
    }

    private fun shouldExclude(
        relativePath: String,
        fileName: String,
        excludePatterns: Set<String>,
    ): Boolean {
        for (pattern in excludePatterns) {
            when {
                // Directory pattern (ends with /)
                pattern.endsWith("/") -> {
                    val dirPattern = pattern.removeSuffix("/")
                    if (relativePath.contains("/$dirPattern/") ||
                        relativePath.startsWith("$dirPattern/")
                    ) {
                        return true
                    }
                }
                // Wildcard pattern (contains *)
                pattern.contains("*") -> {
                    val regex =
                        pattern
                            .replace(".", "\\.")
                            .replace("*", ".*")
                            .toRegex()
                    if (regex.matches(fileName) || regex.matches(relativePath)) {
                        return true
                    }
                }
                // Exact match
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

    fun embed(text: String): List<Float> =
        buildEmbeddingModel()
            .embed(text)
            .content()
            .vector()
            .toList()

    fun similaritySearch(
        embedding: List<Float>,
        topK: Int,
    ): List<String> {
        val embeddingStore = newStore()

        val queryEmbedding =
            dev.langchain4j.data.embedding.Embedding
                .from(embedding.toFloatArray())
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
}
