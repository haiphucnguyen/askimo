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
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

data class PgVectorConfig(
    val url: String = "",
    val user: String = "askimo",
    val password: String = "askimo",
    val table: String = "askimo_embeddings",
)

data class EmbeddingConfig(
    val maxCharsPerChunk: Int = 1500,
    val chunkOverlap: Int = 150,
    val preferredDim: Int? = null,
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

data class IndexingConfig(
    val maxFileBytes: Long = 2_000_000,
    val supportedExtensions: Set<String> = setOf(
        "java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp",
        "cs", "rb", "php", "swift", "scala", "groovy", "sh", "bash", "yaml", "yml", "json", "xml",
        "md", "txt", "gradle", "properties", "toml",
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
            excludePaths = setOf("node_modules/", "dist/", "build/", ".next/", ".nuxt/", "out/", "coverage/", ".cache/", ".parcel-cache/", ".turbo/", ".vite/"),
        ),
        ProjectType(
            name = "Python",
            markers = setOf("requirements.txt", "setup.py", "pyproject.toml", "Pipfile", "poetry.lock"),
            excludePaths = setOf("__pycache__/", "*.pyc", "*.pyo", "*.pyd", ".pytest_cache/", ".mypy_cache/", ".tox/", "venv/", "env/", ".venv/", ".env/", "dist/", "build/", "*.egg-info/", ".eggs/"),
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

    /**
     * Cached set of directory names to skip, extracted from allExcludes patterns.
     * This is computed once when the config is loaded for optimal performance.
     */
    val skipDirectoryNames: Set<String> by lazy {
        allExcludes.mapNotNull { pattern ->
            when {
                pattern.endsWith("/") -> pattern.dropLast(1) // Remove trailing slash
                pattern.startsWith(".") && !pattern.contains("*") -> pattern // Keep dot files/dirs as-is
                !pattern.contains("*") && !pattern.contains("/") -> pattern // Simple directory names
                else -> null // Skip wildcard patterns for now
            }
        }.toSet()
    }
}

data class ChatConfig(
    val maxRecentMessages: Int = 10,
    val maxTokensForContext: Int = 3000,
    val summarizationThreshold: Int = 50,
)

data class ProxyConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val authToken: String = "",
)

data class AppConfigData(
    val pgvector: PgVectorConfig = PgVectorConfig(),
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    val retry: RetryConfig = RetryConfig(),
    val throttle: ThrottleConfig = ThrottleConfig(),
    val indexing: IndexingConfig = IndexingConfig(),
    val chat: ChatConfig = ChatConfig(),
)

object AppConfig {
    val pgVector: PgVectorConfig get() = delegate.pgvector
    val embedding: EmbeddingConfig get() = delegate.embedding
    val retry: RetryConfig get() = delegate.retry
    val throttle: ThrottleConfig get() = delegate.throttle
    val indexing: IndexingConfig get() = delegate.indexing
    val chat: ChatConfig get() = delegate.chat

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

        pgvector:
          url:  ${'$'}{ASKIMO_PG_URL:jdbc:postgresql://localhost:5432/askimo}
          user: ${'$'}{ASKIMO_PG_USER:askimo}
          password: ${'$'}{ASKIMO_PG_PASS:askimo}
          table: ${'$'}{ASKIMO_EMBED_TABLE:askimo_embeddings}

        embedding:
          max_chars_per_chunk: ${'$'}{ASKIMO_EMBED_MAX_CHARS_PER_CHUNK:4000}
          chunk_overlap:       ${'$'}{ASKIMO_EMBED_CHUNK_OVERLAP:200}
          preferred_dim:       ${'$'}{ASKIMO_EMBED_DIM:}

        retry:
          attempts:      ${'$'}{ASKIMO_EMBED_RETRY_ATTEMPTS:4}
          base_delay_ms: ${'$'}{ASKIMO_EMBED_RETRY_BASE_MS:150}

        throttle:
          per_request_sleep_ms: ${'$'}{ASKIMO_EMBED_SLEEP_MS:30}

        indexing:
          max_file_bytes: ${'$'}{ASKIMO_EMBED_MAX_FILE_BYTES:2000000}
          supported_extensions: ${'$'}{ASKIMO_INDEXING_SUPPORTED_EXTENSIONS:java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml}
          common_excludes: ${'$'}{ASKIMO_INDEXING_COMMON_EXCLUDES:.git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/}
          # Project types are configured with default values and can be customized via environment variables
          # ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_MARKERS and ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_EXCLUDES

        chat:
          max_recent_messages: ${'$'}{ASKIMO_CHAT_MAX_RECENT_MESSAGES:10}
          max_tokens_for_context: ${'$'}{ASKIMO_CHAT_MAX_TOKENS_FOR_CONTEXT:3000}
          summarization_threshold: ${'$'}{ASKIMO_CHAT_SUMMARIZATION_THRESHOLD:50}
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
                info("Config parse failed at $path ")
                debug(e)
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
            // Best-effort log
            info("📝 Created default config at $target")
        } catch (e: FileAlreadyExistsException) {
            debug(e)
        } catch (e: Exception) {
            info("Failed to create default config at $target → ${e.message}")
            debug(e)
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

        val pg =
            PgVectorConfig(
                url = env("ASKIMO_PG_URL", "jdbc:postgresql://localhost:5432/askimo"),
                user = env("ASKIMO_PG_USER", "askimo"),
                password = env("ASKIMO_PG_PASS", "askimo"),
                table = env("ASKIMO_EMBED_TABLE", "askimo_embeddings"),
            )
        val emb =
            EmbeddingConfig(
                maxCharsPerChunk = envInt("ASKIMO_EMBED_MAX_CHARS_PER_CHUNK", 4000),
                chunkOverlap = envInt("ASKIMO_EMBED_CHUNK_OVERLAP", 200),
                preferredDim = envNullableInt("ASKIMO_EMBED_DIM"),
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
                supportedExtensions = envList("ASKIMO_INDEXING_SUPPORTED_EXTENSIONS", "java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml"),
                commonExcludes = envList("ASKIMO_INDEXING_COMMON_EXCLUDES", ".git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/"),
            )
        val chat =
            ChatConfig(
                maxRecentMessages = envInt("ASKIMO_CHAT_MAX_RECENT_MESSAGES", 10),
                maxTokensForContext = envInt("ASKIMO_CHAT_MAX_TOKENS_FOR_CONTEXT", 3000),
                summarizationThreshold = envInt("ASKIMO_CHAT_SUMMARIZATION_THRESHOLD", 50),
            )
        return AppConfigData(pg, emb, r, t, idx, chat)
    }

    /** Load proxy configuration from environment variables only - never persisted to file */
    private fun loadProxyFromEnv(): ProxyConfig = ProxyConfig(
        enabled = System.getenv("ASKIMO_PROXY_ENABLED")?.toBoolean() ?: false,
        url = System.getenv("ASKIMO_PROXY_URL") ?: "",
        authToken = System.getenv("ASKIMO_PROXY_AUTH_TOKEN") ?: "",
    )
}
