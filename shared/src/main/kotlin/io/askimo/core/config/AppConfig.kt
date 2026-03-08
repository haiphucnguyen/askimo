/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.LanguageDirectiveChangedEvent
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.security.SecureKeyManager
import io.askimo.core.security.SecureKeyManager.StorageMethod
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
    val maxCharsPerChunk: Int = 3000,
    val chunkOverlap: Int = 100,
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

data class FilterConfig(
    val gitignore: Boolean = true,
    val dockerignore: Boolean = false,
    val projecttype: Boolean = true,
    val binary: Boolean = true,
    val filesize: Boolean = true,
    val custom: Boolean = true,
)

/**
 * Splits a comma-separated YAML string or YAML sequence into a list of tokens.
 * Handles both `"java,kt,py"` and proper YAML sequences.
 */
private fun parseCommaSeparated(p: JsonParser): List<String> = when (p.currentToken) {
    JsonToken.VALUE_STRING -> {
        val raw = p.text.trim()
        if (raw.isEmpty()) {
            emptyList()
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
    JsonToken.START_ARRAY -> {
        val items = mutableListOf<String>()
        while (p.nextToken() != JsonToken.END_ARRAY) items.add(p.text.trim())
        items
    }
    else -> emptyList()
}

private class CommaSeparatedSetDeserializer : StdDeserializer<Set<String>>(Set::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Set<String> = parseCommaSeparated(p).toSet()
}

private class CommaSeparatedListDeserializer : StdDeserializer<List<String>>(List::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<String> = parseCommaSeparated(p)
}

data class IndexingConfig(
    val maxFileBytes: Long = 5_000_000,
    val concurrentIndexingThreads: Int = 10,
    val filters: FilterConfig = FilterConfig(),
    val customExcludes: Set<String> = emptySet(),
    @field:JsonDeserialize(using = CommaSeparatedSetDeserializer::class)
    val supportedExtensions: Set<String> = setOf(
        "java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp",
        "cs", "rb", "php", "swift", "scala", "groovy", "sh", "bash", "yaml", "yml", "json", "xml",
        "md", "txt", "gradle", "properties", "toml", "pdf",
    ),
    @field:JsonDeserialize(using = CommaSeparatedSetDeserializer::class)
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
    @field:JsonDeserialize(using = CommaSeparatedSetDeserializer::class)
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
    @field:JsonDeserialize(using = CommaSeparatedSetDeserializer::class)
    val commonExcludes: Set<String> = setOf(
        ".git/", ".svn/", ".hg/", ".idea/", ".vscode/", ".DS_Store",
        "*.log", "*.tmp", "*.temp", "*.swp", "*.bak", ".history/",
    ),
)

data class DeveloperConfig(
    val enabled: Boolean = true,
    val active: Boolean = false,
)

data class SamplingConfig(
    val temperature: Double = 0.7, // Balanced default: not too strict, not too creative
    val topP: Double = 1.0, // Keep at 1.0 (no nucleus sampling by default)
    val enabled: Boolean = true, // Allow users to disable sampling parameters
)

enum class ProxyType {
    NONE,
    HTTP,
    HTTPS,
    SOCKS5,
    SYSTEM,
}

data class ProxyConfig(
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
) {
    companion object {
        private const val KEYCHAIN_PASSWORD_PLACEHOLDER = "***keychain***"

        private fun getStorageKey(proxyType: ProxyType): String = "proxy.${proxyType.name.lowercase()}.password"

        fun isActualPassword(password: String): Boolean = password.isNotBlank() && password != KEYCHAIN_PASSWORD_PLACEHOLDER

        fun getSecurePassword(proxyType: ProxyType): String? = SecureKeyManager.retrieveSecretKey(getStorageKey(proxyType))

        fun setSecurePassword(proxyType: ProxyType, password: String): SecureKeyManager.StorageResult = if (password.isEmpty()) {
            // Remove password if empty
            SecureKeyManager.removeSecretKey(getStorageKey(proxyType))
            SecureKeyManager.StorageResult(
                success = true,
                method = StorageMethod.KEYCHAIN,
            )
        } else {
            SecureKeyManager.storeSecuredKey(getStorageKey(proxyType), password)
        }

        /**
         * Get the placeholder to use in YAML for securely stored password.
         */
        fun getPasswordPlaceholder(): String = KEYCHAIN_PASSWORD_PLACEHOLDER
    }
}

data class ChatConfig(
    val maxTokens: Int = 8000,
    val summarizationThreshold: Double = 0.75,
    val enableAsyncSummarization: Boolean = true,
    val summarizationTimeoutSeconds: Long = 60,
    val sampling: SamplingConfig = SamplingConfig(),
    val defaultResponseAILocale: String? = null,
)

/**
 * Backup and restore configuration
 */
data class BackupConfig(
    val autoBackupEnabled: Boolean = true,
    val autoBackupOnStartup: Boolean = false,
    val autoBackupIntervalHours: Int = 24,
    val maxAutoBackupsToKeep: Int = 5,
    val createBackupOnUpgrade: Boolean = true,
    val createBackupBeforeRestore: Boolean = true,
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

data class ProviderModelConfig(
    @field:JsonDeserialize(using = CommaSeparatedListDeserializer::class)
    val availableModels: List<String> = emptyList(),
    val utilityModel: String = "",
    val utilityModelTimeoutSeconds: Long = 45,
    val embeddingModel: String = "",
    val visionModel: String = "",
    val imageModel: String = "",
)

data class ModelsConfig(
    val anthropic: ProviderModelConfig = ProviderModelConfig(),
    val gemini: ProviderModelConfig = ProviderModelConfig(),
    val openai: ProviderModelConfig = ProviderModelConfig(),
    val ollama: ProviderModelConfig = ProviderModelConfig(),
    val docker: ProviderModelConfig = ProviderModelConfig(),
    val localai: ProviderModelConfig = ProviderModelConfig(),
    val lmstudio: ProviderModelConfig = ProviderModelConfig(),
    val xai: ProviderModelConfig = ProviderModelConfig(),
) {
    operator fun get(provider: ModelProvider): ProviderModelConfig = when (provider) {
        ModelProvider.OPENAI -> openai
        ModelProvider.ANTHROPIC -> anthropic
        ModelProvider.GEMINI -> gemini
        ModelProvider.XAI -> xai
        ModelProvider.OLLAMA -> ollama
        ModelProvider.DOCKER -> docker
        ModelProvider.LOCALAI -> localai
        ModelProvider.LMSTUDIO -> lmstudio
        ModelProvider.UNKNOWN -> ProviderModelConfig()
    }

    fun update(provider: ModelProvider, updated: ProviderModelConfig): ModelsConfig = when (provider) {
        ModelProvider.OPENAI -> copy(openai = updated)
        ModelProvider.ANTHROPIC -> copy(anthropic = updated)
        ModelProvider.GEMINI -> copy(gemini = updated)
        ModelProvider.XAI -> copy(xai = updated)
        ModelProvider.OLLAMA -> copy(ollama = updated)
        ModelProvider.DOCKER -> copy(docker = updated)
        ModelProvider.LOCALAI -> copy(localai = updated)
        ModelProvider.LMSTUDIO -> copy(lmstudio = updated)
        ModelProvider.UNKNOWN -> this
    }
}

data class AppConfigData(
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    val retry: RetryConfig = RetryConfig(),
    val throttle: ThrottleConfig = ThrottleConfig(),
    val indexing: IndexingConfig = IndexingConfig(),
    val developer: DeveloperConfig = DeveloperConfig(),
    val chat: ChatConfig = ChatConfig(),
    val backup: BackupConfig = BackupConfig(),
    val rag: RagConfig = RagConfig(),
    val models: ModelsConfig = ModelsConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
)

object AppConfig {
    val embedding: EmbeddingConfig get() = delegate.embedding
    val retry: RetryConfig get() = delegate.retry
    val indexing: IndexingConfig get() = delegate.indexing
    val developer: DeveloperConfig get() = delegate.developer
    val chat: ChatConfig get() = delegate.chat
    val backup: BackupConfig get() = delegate.backup
    val rag: RagConfig get() = delegate.rag
    val models: ModelsConfig get() = delegate.models

    /**
     * Proxy configuration with password loaded from secure storage.
     * If password is a placeholder (***keychain***), loads actual password from keychain/encrypted storage.
     */
    val proxy: ProxyConfig
        get() {
            val config = delegate.proxy
            val currentPassword = config.password

            // If password is a placeholder, load from secure storage
            if (!ProxyConfig.isActualPassword(currentPassword)) {
                val securePassword = ProxyConfig.getSecurePassword(config.type)
                if (securePassword != null) {
                    return config.copy(password = securePassword)
                }
            }

            return config
        }

    @Volatile private var cached: AppConfigData? = null

    /**
     * Clears the cached configuration, forcing it to be reloaded on next access.
     * Useful for testing to ensure clean state between tests.
     */
    @Synchronized
    fun reset() {
        cached = null
    }

    /**
     * Initialises AppConfig for tests by writing the DEFAULT_YAML to
     * [configDir]/askimo.yml and resetting the cache so the next access
     * reads from that file with all default values populated.
     *
     * Called automatically by @AskimoTestHome — you do not need to call this manually.
     */
    @Synchronized
    fun initForTest(configDir: Path) {
        val configFile = configDir.resolve("askimo.yml")
        writeDefaultConfig(configFile)
        cached = null
    }

    private val mapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, true)
                    .build(),
            )
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

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


        retry:
          attempts:      ${'$'}{ASKIMO_EMBED_RETRY_ATTEMPTS:4}
          base_delay_ms: ${'$'}{ASKIMO_EMBED_RETRY_BASE_MS:150}

        throttle:
          per_request_sleep_ms: ${'$'}{ASKIMO_EMBED_SLEEP_MS:30}

        indexing:
          max_file_bytes:              ${'$'}{ASKIMO_EMBED_MAX_FILE_BYTES:2000000}
          concurrent_indexing_threads: ${'$'}{ASKIMO_INDEXING_CONCURRENT_THREADS:10}
          supported_extensions: ${'$'}{ASKIMO_INDEXING_SUPPORTED_EXTENSIONS:java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml}
          binary_extensions: ${'$'}{ASKIMO_INDEXING_BINARY_EXTENSIONS:png,jpg,jpeg,gif,svg,ico,webp,bmp,mp4,avi,mov,mkv,mp3,wav,ogg,flac,zip,tar,gz,7z,rar,exe,dll,so,dylib,bin,db,sqlite,pdf,doc,docx,xls,xlsx,ppt,pptx,ttf,otf,woff,woff2,class,jar,pyc}
          exclude_file_names: ${'$'}{ASKIMO_INDEXING_EXCLUDE_FILE_NAMES:.DS_Store,Thumbs.db,desktop.ini,package-lock.json,yarn.lock,pnpm-lock.yaml,poetry.lock,Gemfile.lock}
          common_excludes: ${'$'}{ASKIMO_INDEXING_COMMON_EXCLUDES:.git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/}
          filters:
            gitignore:    ${'$'}{ASKIMO_INDEXING_FILTER_GITIGNORE:true}
            dockerignore: ${'$'}{ASKIMO_INDEXING_FILTER_DOCKERIGNORE:false}
            projecttype:  ${'$'}{ASKIMO_INDEXING_FILTER_PROJECTTYPE:true}
            binary:       ${'$'}{ASKIMO_INDEXING_FILTER_BINARY:true}
            filesize:     ${'$'}{ASKIMO_INDEXING_FILTER_FILESIZE:true}
            custom:       ${'$'}{ASKIMO_INDEXING_FILTER_CUSTOM:true}
          # Project types are configured with default values and can be customized via environment variables
          # ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_MARKERS and ASKIMO_INDEXING_PROJECT_TYPES_<TYPE>_EXCLUDES

        chat:
          max_tokens:                    ${'$'}{ASKIMO_CHAT_MAX_TOKENS:8000}
          summarization_threshold:       ${'$'}{ASKIMO_CHAT_SUMMARIZATION_THRESHOLD:0.75}
          summarization_timeout_seconds: ${'$'}{ASKIMO_CHAT_SUMMARIZATION_TIMEOUT:60}
          enable_async_summarization:    ${'$'}{ASKIMO_CHAT_ENABLE_ASYNC_SUMMARIZATION:true}
          default_response_ai_locale:    ${'$'}{ASKIMO_CHAT_DEFAULT_RESPONSE_LOCALE:}
          sampling:
            temperature: ${'$'}{ASKIMO_CHAT_SAMPLING_TEMPERATURE:1.0}
            top_p:       ${'$'}{ASKIMO_CHAT_SAMPLING_TOP_P:1.0}
            enabled:     ${'$'}{ASKIMO_CHAT_SAMPLING_ENABLED:true}

        rag:
          vector_search_max_results:      ${'$'}{ASKIMO_RAG_VECTOR_SEARCH_MAX_RESULTS:20}
          vector_search_min_score:        ${'$'}{ASKIMO_RAG_VECTOR_SEARCH_MIN_SCORE:0.3}
          hybrid_max_results:             ${'$'}{ASKIMO_RAG_HYBRID_MAX_RESULTS:15}
          rank_fusion_constant:           ${'$'}{ASKIMO_RAG_RANK_FUSION_CONSTANT:60}
          use_absolute_path_in_citations: ${'$'}{ASKIMO_RAG_USE_ABSOLUTE_PATH:true}

        backup:
          auto_backup_enabled:          ${'$'}{ASKIMO_BACKUP_AUTO_ENABLED:true}
          auto_backup_on_startup:       ${'$'}{ASKIMO_BACKUP_ON_STARTUP:false}
          auto_backup_interval_hours:   ${'$'}{ASKIMO_BACKUP_INTERVAL_HOURS:24}
          max_auto_backups_to_keep:     ${'$'}{ASKIMO_BACKUP_MAX_TO_KEEP:5}
          create_backup_on_upgrade:     ${'$'}{ASKIMO_BACKUP_ON_UPGRADE:true}
          create_backup_before_restore: ${'$'}{ASKIMO_BACKUP_BEFORE_RESTORE:true}

        models:
          anthropic:
            available_models: ${'$'}{ASKIMO_ANTHROPIC_MODELS:claude-opus-4-6,claude-sonnet-4-6}
            utility_model: ${'$'}{ASKIMO_ANTHROPIC_UTILITY_MODEL:claude-sonnet-4-6}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_ANTHROPIC_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_ANTHROPIC_EMBEDDING_MODEL:}
            vision_model: ${'$'}{ASKIMO_ANTHROPIC_VISION_MODEL:claude-sonnet-4-6}
            image_model: ${'$'}{ASKIMO_ANTHROPIC_IMAGE_MODEL:claude-sonnet-4-6}
          gemini:
            available_models: ${'$'}{ASKIMO_GEMINI_MODELS:}
            utility_model: ${'$'}{ASKIMO_GEMINI_UTILITY_MODEL:gemini-2.5-flash-lite}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_GEMINI_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_GEMINI_EMBEDDING_MODEL:gemini-embedding-001}
            vision_model: ${'$'}{ASKIMO_GEMINI_VISION_MODEL:gemini-1.5-pro}
            image_model: ${'$'}{ASKIMO_GEMINI_IMAGE_MODEL:gemini-2.0-flash-exp}
          openai:
            available_models: ${'$'}{ASKIMO_OPENAI_MODELS:}
            utility_model: ${'$'}{ASKIMO_OPENAI_UTILITY_MODEL:gpt-3.5-turbo}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_OPENAI_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
            vision_model: ${'$'}{ASKIMO_OPENAI_VISION_MODEL:gpt-4o}
            image_model: ${'$'}{ASKIMO_OPENAI_IMAGE_MODEL:dall-e-3}
          ollama:
            available_models: ${'$'}{ASKIMO_OLLAMA_MODELS:}
            utility_model: ${'$'}{ASKIMO_OLLAMA_UTILITY_MODEL:}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_OLLAMA_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_OLLAMA_EMBEDDING_MODEL:nomic-embed-text:latest}
            vision_model: ${'$'}{ASKIMO_OLLAMA_VISION_MODEL:llava:latest}
            image_model: ${'$'}{ASKIMO_OLLAMA_IMAGE_MODEL:stable-diffusion:latest}
          docker:
            available_models: ${'$'}{ASKIMO_DOCKER_MODELS:}
            utility_model: ${'$'}{ASKIMO_DOCKER_UTILITY_MODEL:}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_DOCKER_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_DOCKER_EMBEDDING_MODEL:ai/qwen3-embedding:0.6B-F16}
            vision_model: ${'$'}{ASKIMO_DOCKER_VISION_MODEL:llava:latest}
            image_model: ${'$'}{ASKIMO_DOCKER_IMAGE_MODEL:stable-diffusion:latest}
          localai:
            available_models: ${'$'}{ASKIMO_LOCALAI_MODELS:}
            utility_model: ${'$'}{ASKIMO_LOCALAI_UTILITY_MODEL:}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_LOCALAI_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_LOCALAI_EMBEDDING_MODEL:nomic-embed-text:latest}
            vision_model: ${'$'}{ASKIMO_LOCALAI_VISION_MODEL:bakllava}
            image_model: ${'$'}{ASKIMO_LOCALAI_IMAGE_MODEL:stable-diffusion}
          lmstudio:
            available_models: ${'$'}{ASKIMO_LMSTUDIO_MODELS:}
            utility_model: ${'$'}{ASKIMO_LMSTUDIO_UTILITY_MODEL:}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_LMSTUDIO_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_LMSTUDIO_EMBEDDING_MODEL:nomic-embed-text:latest}
            vision_model: ${'$'}{ASKIMO_LMSTUDIO_VISION_MODEL:llava-v1.6-mistral-7b}
            image_model: ${'$'}{ASKIMO_LMSTUDIO_IMAGE_MODEL:stable-diffusion}
          xai:
            available_models: ${'$'}{ASKIMO_XAI_MODELS:}
            utility_model: ${'$'}{ASKIMO_XAI_UTILITY_MODEL:grok-3-mini}
            utility_model_timeout_seconds: ${'$'}{ASKIMO_XAI_UTILITY_TIMEOUT:45}
            embedding_model: ${'$'}{ASKIMO_XAI_EMBEDDING_MODEL:}
            vision_model: ${'$'}{ASKIMO_XAI_VISION_MODEL:grok-2-vision-latest}
            image_model: ${'$'}{ASKIMO_XAI_IMAGE_MODEL:grok-2-vision-latest}

        proxy:
          type: ${'$'}{ASKIMO_PROXY_TYPE:NONE}
          host: ${'$'}{ASKIMO_PROXY_HOST:}
          port: ${'$'}{ASKIMO_PROXY_PORT:8080}
          username: ${'$'}{ASKIMO_PROXY_USERNAME:}
          password: ${'$'}{ASKIMO_PROXY_PASSWORD:}

        developer:
          enabled: ${'$'}{ASKIMO_DEVELOPER_ENABLED:true}
          active:  ${'$'}{ASKIMO_DEVELOPER_ACTIVE:false}
        """.trimIndent()

    // Lazy, thread-safe init
    private val delegate: AppConfigData
        get() =
            cached ?: synchronized(this) {
                cached ?: loadOnce().also { cached = it }
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
                concurrentIndexingThreads = envInt("ASKIMO_INDEXING_CONCURRENT_THREADS", 10),
                supportedExtensions = envList("ASKIMO_INDEXING_SUPPORTED_EXTENSIONS", "java,kt,kts,py,js,ts,jsx,tsx,go,rs,c,cpp,h,hpp,cs,rb,php,swift,scala,groovy,sh,bash,yaml,yml,json,xml,md,txt,gradle,properties,toml,pdf"),
                binaryExtensions = envList("ASKIMO_INDEXING_BINARY_EXTENSIONS", "png,jpg,jpeg,gif,svg,ico,webp,bmp,mp4,avi,mov,mkv,mp3,wav,ogg,flac,zip,tar,gz,7z,rar,exe,dll,so,dylib,bin,db,sqlite,pdf,doc,docx,xls,xlsx,ppt,pptx,ttf,otf,woff,woff2,class,jar,pyc"),
                excludeFileNames = envList("ASKIMO_INDEXING_EXCLUDE_FILE_NAMES", ".DS_Store,Thumbs.db,desktop.ini,package-lock.json,yarn.lock,pnpm-lock.yaml,poetry.lock,Gemfile.lock"),
                commonExcludes = envList("ASKIMO_INDEXING_COMMON_EXCLUDES", ".git/,.svn/,.hg/,.idea/,.vscode/,.DS_Store,*.log,*.tmp,*.temp,*.swp,*.bak,.history/"),
                filters = FilterConfig(
                    gitignore = System.getenv("ASKIMO_INDEXING_FILTER_GITIGNORE")?.toBoolean() ?: true,
                    dockerignore = System.getenv("ASKIMO_INDEXING_FILTER_DOCKERIGNORE")?.toBoolean() ?: false,
                    projecttype = System.getenv("ASKIMO_INDEXING_FILTER_PROJECTTYPE")?.toBoolean() ?: true,
                    binary = System.getenv("ASKIMO_INDEXING_FILTER_BINARY")?.toBoolean() ?: true,
                    filesize = System.getenv("ASKIMO_INDEXING_FILTER_FILESIZE")?.toBoolean() ?: true,
                    custom = System.getenv("ASKIMO_INDEXING_FILTER_CUSTOM")?.toBoolean() ?: true,
                ),
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
                summarizationTimeoutSeconds = envLong("ASKIMO_CHAT_SUMMARIZATION_TIMEOUT", 60L),
                enableAsyncSummarization = System.getenv("ASKIMO_CHAT_ENABLE_ASYNC_SUMMARIZATION")?.toBoolean() ?: true,
                defaultResponseAILocale = System.getenv("ASKIMO_CHAT_DEFAULT_RESPONSE_LOCALE")?.takeIf { it.isNotBlank() },
                sampling = SamplingConfig(
                    temperature = envDouble("ASKIMO_CHAT_SAMPLING_TEMPERATURE", 1.0),
                    topP = envDouble("ASKIMO_CHAT_SAMPLING_TOP_P", 1.0),
                    enabled = System.getenv("ASKIMO_CHAT_SAMPLING_ENABLED")?.toBoolean() ?: true,
                ),
            )

        val backup =
            BackupConfig(
                autoBackupEnabled = System.getenv("ASKIMO_BACKUP_AUTO_ENABLED")?.toBoolean() ?: true,
                autoBackupOnStartup = System.getenv("ASKIMO_BACKUP_ON_STARTUP")?.toBoolean() ?: false,
                autoBackupIntervalHours = envInt("ASKIMO_BACKUP_INTERVAL_HOURS", 24),
                maxAutoBackupsToKeep = envInt("ASKIMO_BACKUP_MAX_TO_KEEP", 5),
                createBackupOnUpgrade = System.getenv("ASKIMO_BACKUP_ON_UPGRADE")?.toBoolean() ?: true,
                createBackupBeforeRestore = System.getenv("ASKIMO_BACKUP_BEFORE_RESTORE")?.toBoolean() ?: true,
            )

        val rag =
            RagConfig(
                vectorSearchMaxResults = envInt("ASKIMO_RAG_VECTOR_SEARCH_MAX_RESULTS", 20),
                vectorSearchMinScore = envDouble("ASKIMO_RAG_VECTOR_SEARCH_MIN_SCORE", 0.3),
                hybridMaxResults = envInt("ASKIMO_RAG_HYBRID_MAX_RESULTS", 15),
                rankFusionConstant = envInt("ASKIMO_RAG_RANK_FUSION_CONSTANT", 60),
                useAbsolutePathInCitations = System.getenv("ASKIMO_RAG_USE_ABSOLUTE_PATH")?.toBoolean() ?: true,
            )

        val models = ModelsConfig(
            anthropic = ProviderModelConfig(
                availableModels = envList("ASKIMO_ANTHROPIC_MODELS", "claude-opus-4-6,claude-sonnet-4-6").toList(),
                utilityModel = env("ASKIMO_ANTHROPIC_UTILITY_MODEL", "claude-sonnet-4-6"),
                utilityModelTimeoutSeconds = envLong("ASKIMO_ANTHROPIC_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_ANTHROPIC_EMBEDDING_MODEL", ""),
                visionModel = env("ASKIMO_ANTHROPIC_VISION_MODEL", "claude-sonnet-4-6"),
                imageModel = env("ASKIMO_ANTHROPIC_IMAGE_MODEL", "claude-sonnet-4-6"),
            ),
            gemini = ProviderModelConfig(
                utilityModel = env("ASKIMO_GEMINI_UTILITY_MODEL", "gemini-2.5-flash-lite"),
                utilityModelTimeoutSeconds = envLong("ASKIMO_GEMINI_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_GEMINI_EMBEDDING_MODEL", "gemini-embedding-001"),
                visionModel = env("ASKIMO_GEMINI_VISION_MODEL", "gemini-1.5-pro"),
                imageModel = env("ASKIMO_GEMINI_IMAGE_MODEL", "gemini-2.0-flash-exp"),
            ),
            openai = ProviderModelConfig(
                utilityModel = env("ASKIMO_OPENAI_UTILITY_MODEL", "gpt-3.5-turbo"),
                utilityModelTimeoutSeconds = envLong("ASKIMO_OPENAI_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_OPENAI_EMBEDDING_MODEL", "text-embedding-3-small"),
                visionModel = env("ASKIMO_OPENAI_VISION_MODEL", "gpt-4o"),
                imageModel = env("ASKIMO_OPENAI_IMAGE_MODEL", "dall-e-3"),
            ),
            ollama = ProviderModelConfig(
                utilityModel = env("ASKIMO_OLLAMA_UTILITY_MODEL", ""),
                utilityModelTimeoutSeconds = envLong("ASKIMO_OLLAMA_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_OLLAMA_EMBEDDING_MODEL", "nomic-embed-text:latest"),
                visionModel = env("ASKIMO_OLLAMA_VISION_MODEL", "llava:latest"),
                imageModel = env("ASKIMO_OLLAMA_IMAGE_MODEL", "stable-diffusion:latest"),
            ),
            docker = ProviderModelConfig(
                utilityModel = env("ASKIMO_DOCKER_UTILITY_MODEL", ""),
                utilityModelTimeoutSeconds = envLong("ASKIMO_DOCKER_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_DOCKER_EMBEDDING_MODEL", "ai/qwen3-embedding:0.6B-F16"),
                visionModel = env("ASKIMO_DOCKER_VISION_MODEL", "llava:latest"),
                imageModel = env("ASKIMO_DOCKER_IMAGE_MODEL", "stable-diffusion:latest"),
            ),
            localai = ProviderModelConfig(
                utilityModel = env("ASKIMO_LOCALAI_UTILITY_MODEL", ""),
                utilityModelTimeoutSeconds = envLong("ASKIMO_LOCALAI_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_LOCALAI_EMBEDDING_MODEL", "nomic-embed-text:latest"),
                visionModel = env("ASKIMO_LOCALAI_VISION_MODEL", "bakllava"),
                imageModel = env("ASKIMO_LOCALAI_IMAGE_MODEL", "stable-diffusion"),
            ),
            lmstudio = ProviderModelConfig(
                utilityModel = env("ASKIMO_LMSTUDIO_UTILITY_MODEL", ""),
                utilityModelTimeoutSeconds = envLong("ASKIMO_LMSTUDIO_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_LMSTUDIO_EMBEDDING_MODEL", "nomic-embed-text:latest"),
                visionModel = env("ASKIMO_LMSTUDIO_VISION_MODEL", "llava-v1.6-mistral-7b"),
                imageModel = env("ASKIMO_LMSTUDIO_IMAGE_MODEL", "stable-diffusion"),
            ),
            xai = ProviderModelConfig(
                utilityModel = env("ASKIMO_XAI_UTILITY_MODEL", "grok-3-mini"),
                utilityModelTimeoutSeconds = envLong("ASKIMO_XAI_UTILITY_TIMEOUT", 45L),
                embeddingModel = env("ASKIMO_XAI_EMBEDDING_MODEL", ""),
                visionModel = env("ASKIMO_XAI_VISION_MODEL", "grok-2-vision-latest"),
                imageModel = env("ASKIMO_XAI_IMAGE_MODEL", "grok-2-vision-latest"),
            ),
        )

        val proxy =
            ProxyConfig(
                type = System.getenv("ASKIMO_PROXY_TYPE")?.let { ProxyType.valueOf(it) } ?: ProxyType.NONE,
                host = env("ASKIMO_PROXY_HOST", ""),
                port = envInt("ASKIMO_PROXY_PORT", 8080),
                username = env("ASKIMO_PROXY_USERNAME", ""),
                password = env("ASKIMO_PROXY_PASSWORD", ""),
            )

        return AppConfigData(emb, r, t, idx, dev, chat, backup, rag, models, proxy)
    }

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

            if (parts.size !in 2..3) {
                log.displayError("Invalid config path: $path. Must be in format 'section.field' or 'models.provider.field'", null)
                return
            }

            val section = parts[0]
            val field = if (parts.size == 2) parts[1] else "${parts[1]}.${parts[2]}"

            // Update in-memory cache
            val current = cached ?: loadOnce()
            cached = when (section) {
                "developer" -> current.copy(developer = updateDeveloperField(current.developer, field, value))
                "retry" -> current.copy(retry = updateRetryField(current.retry, field, value))
                "throttle" -> current.copy(throttle = updateThrottleField(current.throttle, field, value))
                "embedding" -> current.copy(embedding = updateEmbeddingField(current.embedding, field, value))
                "chat" -> current.copy(chat = updateChatField(current.chat, field, value))
                "backup" -> current.copy(backup = updateBackupField(current.backup, field, value))
                "rag" -> current.copy(rag = updateRagField(current.rag, field, value))
                "models" -> current.copy(models = updateModelsField(current.models, field, value))
                "proxy" -> current.copy(proxy = updateProxyField(current.proxy, field, value))
                else -> {
                    log.displayError("Unknown config section: $section", null)
                    return
                }
            }

            val configPath = resolveOrCreateConfigPath()
            if (configPath != null && configPath.exists()) {
                try {
                    val updatedYaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cached)
                    Files.writeString(configPath, updatedYaml)

                    log.debug("Updated $path=$value in $configPath")
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

    private fun updateChatField(config: ChatConfig, field: String, value: Any): ChatConfig = when (field) {
        "maxTokens" -> config.copy(maxTokens = value as Int)
        "summarizationThreshold" -> config.copy(summarizationThreshold = (value as Number).toDouble())
        "enableAsyncSummarization" -> config.copy(enableAsyncSummarization = value as Boolean)
        "sampling.temperature" -> config.copy(sampling = config.sampling.copy(temperature = (value as Number).toDouble()))
        "sampling.topP" -> config.copy(sampling = config.sampling.copy(topP = (value as Number).toDouble()))
        "sampling.enabled" -> config.copy(sampling = config.sampling.copy(enabled = value as Boolean))
        "defaultResponseAILocale" -> {
            val newLocale = if (value is String && value.isBlank()) null else value as? String
            EventBus.post(
                LanguageDirectiveChangedEvent(localeString = newLocale),
            )
            config.copy(defaultResponseAILocale = newLocale)
        }
        else -> config
    }

    private fun updateBackupField(config: BackupConfig, field: String, value: Any): BackupConfig = when (field) {
        "autoBackupEnabled" -> config.copy(autoBackupEnabled = value as Boolean)
        "autoBackupOnStartup" -> config.copy(autoBackupOnStartup = value as Boolean)
        "autoBackupIntervalHours" -> config.copy(autoBackupIntervalHours = value as Int)
        "maxAutoBackupsToKeep" -> config.copy(maxAutoBackupsToKeep = value as Int)
        "createBackupOnUpgrade" -> config.copy(createBackupOnUpgrade = value as Boolean)
        "createBackupBeforeRestore" -> config.copy(createBackupBeforeRestore = value as Boolean)
        else -> config
    }

    private fun updateRagField(config: RagConfig, field: String, value: Any): RagConfig = when (field) {
        "vectorSearchMaxResults" -> config.copy(vectorSearchMaxResults = value as Int)
        "vectorSearchMinScore" -> config.copy(vectorSearchMinScore = (value as Number).toDouble())
        "hybridMaxResults" -> config.copy(hybridMaxResults = value as Int)
        "rankFusionConstant" -> config.copy(rankFusionConstant = value as Int)
        "useAbsolutePathInCitations" -> config.copy(useAbsolutePathInCitations = value as Boolean)
        else -> config
    }

    private fun updateModelsField(config: ModelsConfig, field: String, value: Any): ModelsConfig {
        val parts = field.split(".")
        if (parts.size != 2) {
            log.displayError("Models config requires nested path format: provider.field (e.g., openai.visionModel)", null)
            return config
        }

        val providerKey = parts[0]
        val modelField = parts[1]
        val stringValue = value as? String ?: value.toString()

        val provider = ModelProvider.entries.firstOrNull { it.name.lowercase() == providerKey }
            ?: run {
                log.displayError("Unknown provider: $providerKey", null)
                return config
            }

        val current = config[provider]
        val updated = when (modelField) {
            "utilityModel" -> current.copy(utilityModel = stringValue)
            "utilityModelTimeoutSeconds" -> current.copy(utilityModelTimeoutSeconds = stringValue.toLongOrNull() ?: current.utilityModelTimeoutSeconds)
            "embeddingModel" -> current.copy(embeddingModel = stringValue)
            "visionModel" -> current.copy(visionModel = stringValue)
            "imageModel" -> current.copy(imageModel = stringValue)
            else -> {
                log.displayError("Unknown model field '$modelField' for provider $providerKey", null)
                return config
            }
        }
        return config.update(provider, updated)
    }

    private fun updateProxyField(config: ProxyConfig, field: String, value: Any): ProxyConfig = when (field) {
        "type" -> config.copy(type = if (value is String) ProxyType.valueOf(value) else value as ProxyType)
        "host" -> config.copy(host = value as String)
        "port" -> config.copy(port = value as Int)
        "username" -> config.copy(username = value as String)
        "password" -> {
            val password = value as String

            // Only store if it's an actual password (not a placeholder)
            if (ProxyConfig.isActualPassword(password)) {
                val result = ProxyConfig.setSecurePassword(config.type, password)

                when (result.method) {
                    StorageMethod.KEYCHAIN -> {
                        log.debug("Proxy password stored securely in system keychain")
                    }
                    StorageMethod.ENCRYPTED -> {
                        log.warn("Proxy password stored with encryption (${result.warningMessage})")
                    }
                    StorageMethod.INSECURE_FALLBACK -> {
                        log.warn("⚠️ Proxy password storage: ${result.warningMessage}")
                    }
                }

                config.copy(password = ProxyConfig.getPasswordPlaceholder())
            } else {
                // Keep placeholder or empty as-is
                config.copy(password = password)
            }
        }
        else -> {
            log.displayError("Unknown proxy field: $field", null)
            config
        }
    }
}
