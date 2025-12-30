/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

private object AppConfigObject
private val log = logger<AppConfigObject>()

data class EmbeddingConfig(
    val maxCharsPerChunk: Int = 4000,
    val chunkOverlap: Int = 100,
    val preferredDim: Int? = null,
)

data class EmbeddingModelsConfig(
    val openai: String = "text-embedding-3-small",
    val docker: String = "ai/qwen3-embedding:0.6B-F16",
    val ollama: String = "nomic-embed-text:latest",
    val localai: String = "nomic-embed-text:latest",
    val lmstudio: String = "nomic-embed-text:latest",
    val gemini: String = "text-embedding-004",
)

data class RetryConfig(
    val attempts: Int = 4,
    val baseDelayMs: Long = 150,
)

data class ThrottleConfig(
    val perRequestSleepMs: Long = 30,
)

data class ProjectType(
    val name: String,
    val markers: Set<String>,
    val excludePaths: Set<String>,
)

data class FilterConfig(
    val gitignore: Boolean = true,
    val dockerignore: Boolean = false,
    val projecttype: Boolean = true,
    val binary: Boolean = true,
    val filesize: Boolean = true,
    val custom: Boolean = true,
)

data class IndexingConfig(
    val maxFileBytes: Long = 5_000_000,
    val concurrentIndexingThreads: Int = 10,
    val filters: FilterConfig = FilterConfig(),
    val customExcludes: Set<String> = emptySet(),
    val supportedExtensions: Set<String> = setOf(
        "java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp",
        "cs", "rb", "php", "swift", "scala", "groovy", "sh", "bash", "yaml", "yml", "json", "xml",
        "md", "txt", "gradle", "properties", "toml", "pdf",
    ),
    val binaryExtensions: Set<String> = setOf(
        // Images
        "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "bmp", "tiff", "tif",
        // Videos
        "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "m4v",
        // Audio
        "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma",
        // Archives
        "zip", "tar", "gz", "bz2", "7z", "rar", "xz", "tgz",
        // Executables
        "exe", "dll", "so", "dylib", "bin", "obj", "o", "a", "lib",
        // Databases
        "db", "sqlite", "sqlite3", "mdb", "accdb",
        // Documents (binary formats - now excluding pdf as we can extract text from it)
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        // Fonts
        "ttf", "otf", "woff", "woff2", "eot",
        // Other binary
        "class", "jar", "war", "ear", "pyc", "pyo",
    ),
    val excludeFileNames: Set<String> = setOf(
        // System files
        ".DS_Store", "Thumbs.db", "desktop.ini",
        // Lock files
        "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "Gemfile.lock",
        // IDE files
        ".project", ".classpath", ".factorypath",
    ),
    val projectTypes: List<ProjectType> = listOf(
        ProjectType(
            name = "Gradle",
            markers = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradlew"),
            excludePaths = setOf("build/", ".gradle/", "out/", "bin/", ".kotlintest/", ".kotlin/"),
        ),
        ProjectType(
            name = "Maven",
            markers = setOf("pom.xml", "mvnw"),
            excludePaths = setOf("target/", ".mvn/", "out/", "bin/"),
        ),
        ProjectType(
            name = "Node.js",
            markers = setOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml"),
            excludePaths = setOf(
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
        ProjectType(
            name = "Python",
            markers = setOf("requirements.txt", "setup.py", "pyproject.toml", "Pipfile", "poetry.lock"),
            excludePaths = setOf(
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
        ProjectType(
            name = "Go",
            markers = setOf("go.mod", "go.sum"),
            excludePaths = setOf("vendor/", "bin/", "pkg/"),
        ),
        ProjectType(
            name = "Rust",
            markers = setOf("Cargo.toml", "Cargo.lock"),
            excludePaths = setOf("target/", "Cargo.lock"),
        ),
        ProjectType(
            name = "Ruby",
            markers = setOf("Gemfile", "Gemfile.lock", "Rakefile"),
            excludePaths = setOf("vendor/", ".bundle/", "tmp/", "log/"),
        ),
        ProjectType(
            name = "PHP/Composer",
            markers = setOf("composer.json", "composer.lock"),
            excludePaths = setOf("vendor/", "var/cache/", "var/log/"),
        ),
        ProjectType(
            name = ".NET",
            markers = setOf("*.csproj", "*.sln", "*.fsproj", "*.vbproj"),
            excludePaths = setOf("bin/", "obj/", "packages/", ".vs/", "Debug/", "Release/"),
        ),
    ),
    val commonExcludes: Set<String> = setOf(
        ".git/", ".svn/", ".hg/", ".idea/", ".vscode/", ".DS_Store",
        "*.log", "*.tmp", "*.temp", "*.swp", "*.bak", ".history/",
    ),
) {
    /**
     * Cached combination of commonExcludes and all ProjectType excludePaths.
     * This is computed once when the config is loaded for better performance.
     */
    val allExcludes: Set<String> by lazy {
        buildSet {
            addAll(commonExcludes)

            projectTypes.forEach { projectType ->
                addAll(projectType.excludePaths)
            }
        }
    }
}

data class DeveloperConfig(
    val enabled: Boolean = true,
    val active: Boolean = false,
)

data class ChatConfig(
    val maxTokens: Int = 8000,
    val summarizationThreshold: Double = 0.75,
    val enableAsyncSummarization: Boolean = true,
)

/**
 * RAG (Retrieval-Augmented Generation) configuration.
 * Controls how relevant documents are retrieved from the knowledge base.
 */
data class RagConfig(
    /** Maximum number of documents to retrieve from vector search */
    val vectorSearchMaxResults: Int = 20,
    /** Minimum similarity score for vector search results (0.0 to 1.0) */
    val vectorSearchMinScore: Double = 0.3,
    /** Maximum number of final documents to return after hybrid fusion */
    val hybridMaxResults: Int = 15,
    /** RRF constant for rank fusion algorithm (standard value is 60) */
    val rankFusionConstant: Int = 60,
    /** Use absolute file paths in citations (true) or relative filenames (false) */
    val useAbsolutePathInCitations: Boolean = true,
)

data class ProxyConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val authToken: String = "",
)

data class AppConfigData(
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    val embeddingModels: EmbeddingModelsConfig = EmbeddingModelsConfig(),
    val retry: RetryConfig = RetryConfig(),
    val throttle: ThrottleConfig = ThrottleConfig(),
    val indexing: IndexingConfig = IndexingConfig(),
    val developer: DeveloperConfig = DeveloperConfig(),
    val chat: ChatConfig = ChatConfig(),
    val rag: RagConfig = RagConfig(),
)

object AppConfig {
    val embedding: EmbeddingConfig get() = delegate.embedding
    val embeddingModels: EmbeddingModelsConfig get() = delegate.embeddingModels
    val retry: RetryConfig get() = delegate.retry
    val indexing: IndexingConfig get() = delegate.indexing
    val developer: DeveloperConfig get() = delegate.developer
    val chat: ChatConfig get() = delegate.chat
    val rag: RagConfig get() = delegate.rag

    val proxy: ProxyConfig by lazy { loadProxyFromEnv() }

    @Volatile private var cached: AppConfigData? = null

    private val mapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    // Default YAML written on first run if no config exists
    private val DEFAULT_YAML =
        """
        # Askimo application configuration
        # This file was auto-generated because none was found.
        # You can override any value via environment variables using ${'$'}{ENV:default} placeholders.


        embedding:
          max_chars_per_chunk: ${'$'}{ASKIMO_EMBED_MAX_CHARS_PER_CHUNK:4000}
          chunk_overlap:       ${'$'}{ASKIMO_EMBED_CHUNK_OVERLAP:200}
          preferred_dim:       ${'$'}{ASKIMO_EMBED_DIM:}

        embedding_models:
          openai:    ${'$'}{ASKIMO_EMBED_MODEL_OPENAI:text-embedding-3-small}
          docker:    ${'$'}{ASKIMO_EMBED_MODEL_DOCKER:text-embedding-3-small}
          ollama:    ${'$'}{ASKIMO_EMBED_MODEL_OLLAMA:nomic-embed-text:latest}
          localai:   ${'$'}{ASKIMO_EMBED_MODEL_LOCALAI:text-embedding-3-small}
          lmstudio:  ${'$'}{ASKIMO_EMBED_MODEL_LMSTUDIO:text-embedding-3-small}

        retry:
          attempts:      ${'$'}{ASKIMO_EMBED_RETRY_ATTEMPTS:4}
          base_delay_ms: ${'$'}{ASKIMO_EMBED_RETRY_BASE_MS:150}

        throttle:
          per_request_sleep_ms: ${'$'}{ASKIMO_EMBED_SLEEP_MS:30}

        indexing:
          max_file_bytes: ${'$'}{ASKIMO_EMBED_MAX_FILE_BYTES:2000000}
          supported_extensions: ${'$'}{ASKIMO_INDEXING_SUPPORTED_EXTENSIONS:java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml}
          binary_extensions: ${'$'}{ASKIMO_INDEXING_BINARY_EXTENSIONS:png,jpg,jpeg,gif,svg,ico,webp,bmp,mp4,avi,mov,mkv,mp3,wav,ogg,flac,zip,tar,gz,7z,rar,exe,dll,so,dylib,bin,db,sqlite,pdf,doc,docx,xls,xlsx,ppt,pptx,ttf,otf,woff,woff2,class,jar,pyc}
          exclude_file_names: ${'$'}{ASKIMO_INDEXING_EXCLUDE_FILE_NAMES:.DS_Store,Thumbs.db,desktop.ini,package-lock.json,yarn.lock,pnpm-lock.yaml,poetry.lock,Gemfile.lock}
          common_excludes: ${'$'}{ASKIMO_INDEXING_COMMON_EXCLUDES:.git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/}
          # Project types are configured with default values and can be customized via environment variables
          # ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_MARKERS and ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_EXCLUDES

        chat:
          max_tokens: ${'$'}{ASKIMO_CHAT_MAX_TOKENS:8000}
          summarization_threshold: ${'$'}{ASKIMO_CHAT_SUMMARIZATION_THRESHOLD:0.75}
          enable_async_summarization: ${'$'}{ASKIMO_CHAT_ENABLE_ASYNC_SUMMARIZATION:true}

        rag:
          vector_search_max_results: ${'$'}{ASKIMO_RAG_VECTOR_SEARCH_MAX_RESULTS:20}
          vector_search_min_score: ${'$'}{ASKIMO_RAG_VECTOR_SEARCH_MIN_SCORE:0.3}
          hybrid_max_results: ${'$'}{ASKIMO_RAG_HYBRID_MAX_RESULTS:15}
          rank_fusion_constant: ${'$'}{ASKIMO_RAG_RANK_FUSION_CONSTANT:60}

        developer:
          enabled: ${'$'}{ASKIMO_DEVELOPER_ENABLED:false}
          active: ${'$'}{ASKIMO_DEVELOPER_ACTIVE:false}
        """.trimIndent()

    // Lazy, thread-safe init
    private val delegate: AppConfigData
        get() =
            cached ?: synchronized(this) {
                cached ?: loadOnce().also { cached = it }
            }

    /** Reload on demand after editing the file. */
    fun reload(): AppConfigData = synchronized(this) {
        cached = null
        val re = loadOnce()
        cached = re
        re
    }

    private fun loadOnce(): AppConfigData {
        val path = resolveOrCreateConfigPath()
        return if (path != null && path.isRegularFile()) {
            val raw = Files.readString(path)
            val interpolated = interpolateEnv(raw)
            try {
                mapper.readValue<AppConfigData>(interpolated)
            } catch (e: Exception) {
                log.displayError("Config parse failed at $path ", e)
                envFallback()
            }
        } else {
            envFallback()
        }
    }

    /**
     * Resolution order:
     *  1) system property: askimo.config
     *  2) env var: ASKIMO_CONFIG
     *  3) ~/.askimo/askimo.yml (will be created if missing)
     *  4) ./askimo.yml (used only if already exists; we don’t auto-create in CWD)
     *
     * If an explicit path (1 or 2) is provided and missing, we create it.
     * Otherwise, if home path is missing, we create ~/.askimo/askimo.yml.
     */
    private fun resolveOrCreateConfigPath(): Path? {
        System.getProperty("askimo.config")?.takeIf { it.isNotBlank() }?.let { p ->
            val path = Paths.get(p)
            if (!path.exists()) writeDefaultConfig(path)
            return path
        }
        System.getenv("ASKIMO_CONFIG")?.takeIf { it.isNotBlank() }?.let { p ->
            val path = Paths.get(p)
            if (!path.exists()) writeDefaultConfig(path)
            return path
        }
        val homeBase = AskimoHome.base()
        val homePath = homeBase.resolve("askimo.yml")
        if (!homePath.exists()) writeDefaultConfig(homePath)
        if (homePath.isRegularFile()) return homePath
        val cwdPath = Paths.get("askimo.yml")
        if (cwdPath.isRegularFile()) return cwdPath
        return null
    }

    private fun writeDefaultConfig(target: Path) {
        try {
            target.parent?.createDirectories()
            val supportsPosix =
                try {
                    FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                } catch (_: Exception) {
                    false
                }

            if (supportsPosix) {
                val attrs =
                    PosixFilePermissions.asFileAttribute(
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    )
                Files.createFile(target, attrs)
            } else {
                Files.createFile(target)
            }
            Files.writeString(target, DEFAULT_YAML)
            log.info("📝 Created default config at $target")
        } catch (e: Exception) {
            log.displayError("Failed to create default config at $target ", e)
        }
    }

    /** Supports ${ENV} or ${ENV:default} inside YAML. */
    private val placeholder = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}".toRegex()

    private fun interpolateEnv(text: String): String = placeholder.replace(text) { m ->
        val key = m.groupValues[1]
        val def = m.groupValues.getOrNull(2)
        propOrEnv(key) ?: def.orEmpty()
    }

    private fun propOrEnv(key: String): String? = System.getProperty(key) ?: System.getenv(key)

    /** Env-only fallback (works even without YAML). */
    private fun envFallback(): AppConfigData {
        fun env(
            k: String,
            def: String,
        ) = System.getenv(k) ?: def

        fun envInt(
            k: String,
            def: Int,
        ) = System.getenv(k)?.toIntOrNull() ?: def

        fun envLong(
            k: String,
            def: Long,
        ) = System.getenv(k)?.toLongOrNull() ?: def

        fun envList(k: String, def: String): Set<String> = System.getenv(k)?.split(",")?.map { it.trim() }?.toSet() ?: def.split(",").map { it.trim() }.toSet()

        fun envNullableInt(k: String) = System.getenv(k)?.toIntOrNull()

        val emb =
            EmbeddingConfig(
                maxCharsPerChunk = envInt("ASKIMO_EMBED_MAX_CHARS_PER_CHUNK", 4000),
                chunkOverlap = envInt("ASKIMO_EMBED_CHUNK_OVERLAP", 200),
                preferredDim = envNullableInt("ASKIMO_EMBED_DIM"),
            )
        val embModels =
            EmbeddingModelsConfig(
                openai = env("ASKIMO_EMBED_MODEL_OPENAI", "text-embedding-3-small"),
                docker = env("ASKIMO_EMBED_MODEL_DOCKER", "text-embedding-3-small"),
                ollama = env("ASKIMO_EMBED_MODEL_OLLAMA", "nomic-embed-text:latest"),
                localai = env("ASKIMO_EMBED_MODEL_LOCALAI", "text-embedding-3-small"),
                lmstudio = env("ASKIMO_EMBED_MODEL_LMSTUDIO", "text-embedding-3-small"),
            )
        val r =
            RetryConfig(
                attempts = envInt("ASKIMO_EMBED_RETRY_ATTEMPTS", 4),
                baseDelayMs = envLong("ASKIMO_EMBED_RETRY_BASE_MS", 150L),
            )
        val t =
            ThrottleConfig(
                perRequestSleepMs = envLong("ASKIMO_EMBED_SLEEP_MS", 30L),
            )
        val idx =
            IndexingConfig(
                maxFileBytes = envLong("ASKIMO_EMBED_MAX_FILE_BYTES", 2_000_000L),
                supportedExtensions = envList("ASKIMO_INDEXING_SUPPORTED_EXTENSIONS", "java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml,pdf"),
                commonExcludes = envList("ASKIMO_INDEXING_COMMON_EXCLUDES", ".git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/"),
            )
        val dev =
            DeveloperConfig(
                enabled = System.getenv("ASKIMO_DEVELOPER_ENABLED")?.toBoolean() ?: false,
                active = System.getenv("ASKIMO_DEVELOPER_ACTIVE")?.toBoolean() ?: false,
            )

        fun envDouble(k: String, def: Double) = System.getenv(k)?.toDoubleOrNull() ?: def

        val chat =
            ChatConfig(
                maxTokens = envInt("ASKIMO_CHAT_MAX_TOKENS", 8000),
                summarizationThreshold = envDouble("ASKIMO_CHAT_SUMMARIZATION_THRESHOLD", 0.75),
                enableAsyncSummarization = System.getenv("ASKIMO_CHAT_ENABLE_ASYNC_SUMMARIZATION")?.toBoolean() ?: true,
            )

        return AppConfigData(emb, embModels, r, t, idx, dev, chat)
    }

    /** Load proxy configuration from environment variables only - never persisted to file */
    private fun loadProxyFromEnv(): ProxyConfig = ProxyConfig(
        enabled = System.getenv("ASKIMO_PROXY_ENABLED")?.toBoolean() ?: false,
        url = System.getenv("ASKIMO_PROXY_URL") ?: "",
        authToken = System.getenv("ASKIMO_PROXY_AUTH_TOKEN") ?: "",
    )

    /**
     * Generic method to update any config field and persist to YAML file.
     *
     * @param path Dot-separated path to the field (e.g., "developer.active", "chat.maxRecentMessages")
     * @param value The new value to set
     *
     * Example: AppConfig.updateField("developer.active", true)
     */
    fun updateField(path: String, value: Any) {
        synchronized(this) {
            val parts = path.split(".")
            if (parts.size != 2) {
                log.displayError("Invalid config path: $path. Must be in format 'section.field'", null)
                return
            }

            val section = parts[0]
            val field = parts[1]

            // Update in-memory cache
            val current = cached ?: loadOnce()
            cached = when (section) {
                "developer" -> current.copy(developer = updateDeveloperField(current.developer, field, value))
                "retry" -> current.copy(retry = updateRetryField(current.retry, field, value))
                "throttle" -> current.copy(throttle = updateThrottleField(current.throttle, field, value))
                "embedding" -> current.copy(embedding = updateEmbeddingField(current.embedding, field, value))
                "embeddingModels" -> current.copy(embeddingModels = updateEmbeddingModelsField(current.embeddingModels, field, value))
                "chat" -> current.copy(chat = updateChatField(current.chat, field, value))
                "rag" -> current.copy(rag = updateRagField(current.rag, field, value))
                else -> {
                    log.displayError("Unknown config section: $section", null)
                    return
                }
            }

            // Persist to YAML file
            val configPath = resolveOrCreateConfigPath()
            if (configPath != null && configPath.exists()) {
                try {
                    val content = Files.readString(configPath)
                    val updatedContent = updateYamlField(content, section, field, value)
                    Files.writeString(configPath, updatedContent)
                    log.info("Updated $path=$value in $configPath")
                } catch (e: Exception) {
                    log.displayError("Failed to persist $path to config file", e)
                }
            }
        }
    }

    private fun updateDeveloperField(config: DeveloperConfig, field: String, value: Any): DeveloperConfig = when (field) {
        "enabled" -> config.copy(enabled = value as Boolean)
        "active" -> config.copy(active = value as Boolean)
        else -> config
    }

    private fun updateRetryField(config: RetryConfig, field: String, value: Any): RetryConfig = when (field) {
        "attempts" -> config.copy(attempts = value as Int)
        "baseDelayMs" -> config.copy(baseDelayMs = value as Long)
        else -> config
    }

    private fun updateThrottleField(config: ThrottleConfig, field: String, value: Any): ThrottleConfig = when (field) {
        "perRequestSleepMs" -> config.copy(perRequestSleepMs = value as Long)
        else -> config
    }

    private fun updateEmbeddingField(config: EmbeddingConfig, field: String, value: Any): EmbeddingConfig = when (field) {
        "maxCharsPerChunk" -> config.copy(maxCharsPerChunk = value as Int)
        "chunkOverlap" -> config.copy(chunkOverlap = value as Int)
        "preferredDim" -> config.copy(preferredDim = if (value is String && value.isBlank()) null else value as? Int)
        else -> config
    }

    private fun updateEmbeddingModelsField(config: EmbeddingModelsConfig, field: String, value: Any): EmbeddingModelsConfig = when (field) {
        "openai" -> config.copy(openai = value as String)
        "docker" -> config.copy(docker = value as String)
        "ollama" -> config.copy(ollama = value as String)
        "localai" -> config.copy(localai = value as String)
        "lmstudio" -> config.copy(lmstudio = value as String)
        "gemini" -> config.copy(gemini = value as String)
        else -> config
    }

    private fun updateChatField(config: ChatConfig, field: String, value: Any): ChatConfig = when (field) {
        "maxTokens" -> config.copy(maxTokens = value as Int)
        "summarizationThreshold" -> config.copy(summarizationThreshold = (value as Number).toDouble())
        "enableAsyncSummarization" -> config.copy(enableAsyncSummarization = value as Boolean)
        else -> config
    }

    private fun updateRagField(config: RagConfig, field: String, value: Any): RagConfig = when (field) {
        "vectorSearchMaxResults" -> config.copy(vectorSearchMaxResults = value as Int)
        "vectorSearchMinScore" -> config.copy(vectorSearchMinScore = (value as Number).toDouble())
        "hybridMaxResults" -> config.copy(hybridMaxResults = value as Int)
        "rankFusionConstant" -> config.copy(rankFusionConstant = value as Int)
        else -> config
    }

    /**
     * Generic YAML field updater that handles nested sections.
     */
    private fun updateYamlField(yamlContent: String, section: String, field: String, value: Any): String {
        val lines = yamlContent.lines().toMutableList()
        var inSection = false
        var fieldLineIndex = -1
        var sectionLineIndex = -1

        // Find the section and field
        for (i in lines.indices) {
            val line = lines[i].trimStart()

            if (line.startsWith("$section:")) {
                inSection = true
                sectionLineIndex = i
            } else if (inSection && line.startsWith("$field:")) {
                fieldLineIndex = i
                break
            } else if (inSection && line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                // We've entered another top-level section
                break
            }
        }

        val formattedValue = when (value) {
            is String -> value
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> value.toString()
        }

        if (fieldLineIndex >= 0) {
            val indent = lines[fieldLineIndex].takeWhile { it.isWhitespace() }
            lines[fieldLineIndex] = "$indent$field: $formattedValue"
        } else if (sectionLineIndex >= 0) {
            val sectionIndent = lines[sectionLineIndex].takeWhile { it.isWhitespace() }
            val fieldIndent = "$sectionIndent  "
            lines.add(sectionLineIndex + 1, "$fieldIndent$field: $formattedValue")
        } else {
            lines.add("")
            lines.add("$section:")
            lines.add("  $field: $formattedValue")
        }

        return lines.joinToString("\n")
    }
}
