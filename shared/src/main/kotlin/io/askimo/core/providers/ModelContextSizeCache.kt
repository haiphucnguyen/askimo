/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.event.EventBus
import io.askimo.core.event.system.InvalidateCacheEvent
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.JsonUtils
import io.askimo.core.util.JsonUtils.prettyJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * Tracks learned context sizes for models with file-based persistence.
 * Context sizes are learned at runtime when errors occur and stored separately
 * from user configuration as this is runtime state.
 *
 * Uses provider-specific defaults (cloud providers get larger contexts, local models are conservative)
 * and binary search reduction (0.5 factor) for fast convergence to optimal size.
 *
 * Cache file location: ~/.askimo/model-context-cache.json
 */
object ModelContextSizeCache {
    private val cache = ConcurrentHashMap<String, Int>()
    private val cacheFile: Path = AskimoHome.base().resolve("model-context-cache.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Binary search reduction - halve the context size each time for fast convergence
    private const val REDUCTION_FACTOR = 0.5

    // Provider-specific defaults (all power of 2 values)
    // Cloud providers: aggressive (large contexts)
    // Local models: conservative (smaller contexts)
    private val PROVIDER_DEFAULTS: Map<ModelProvider, Int> = mapOf(
        ModelProvider.ANTHROPIC to 262_144,
        ModelProvider.GEMINI to 1_048_576,
        ModelProvider.OPENAI to 262_144,
        ModelProvider.XAI to 262_144,
        ModelProvider.OLLAMA to 262_144,
        ModelProvider.DOCKER to 262_144,
        ModelProvider.LOCALAI to 262_144,
        ModelProvider.LMSTUDIO to 262_144,
    )

    // Fallback default for unknown providers (power of 2: 128K)
    private const val FALLBACK_DEFAULT = 131_072

    private val log = logger<ModelContextSizeCache>()

    init {
        loadFromFile()

        // Listen for cache invalidation events
        scope.launch {
            EventBus.internalEvents.collect { event ->
                if (event is InvalidateCacheEvent) {
                    invalidateCache()
                }
            }
        }
    }

    /**
     * Get the context size for a given model key.
     * Returns cached size if available, otherwise returns provider-specific default.
     * All sizes are powers of 2 for optimal performance.
     *
     * @param modelKey The model key in format "provider:model"
     * @return The context size in tokens (always a power of 2)
     */
    fun get(modelKey: String): Int {
        // Check cache first
        cache[modelKey]?.let { return it }

        // Extract provider from modelKey (format: "provider:model")
        val provider = modelKey.substringBefore(":")
            .let { providerKey ->
                ModelProvider.entries.find { it.providerKey() == providerKey }
            }

        return PROVIDER_DEFAULTS[provider] ?: FALLBACK_DEFAULT
    }

    /**
     * Reduce the context size using binary search (halve the size).
     * Since all defaults are power of 2, halving always produces another power of 2.
     *
     * Example convergence (128K → 4K in 5 steps):
     * 131,072 → 65,536 → 32,768 → 16,384 → 8,192 → 4,096
     *
     * @param modelKey The model key in format "provider:model"
     * @param currentSize The current context size that was exceeded
     * @return The new reduced context size (power of 2)
     */
    fun reduce(modelKey: String, currentSize: Int): Int {
        val newSize = (currentSize * REDUCTION_FACTOR).toInt().coerceAtLeast(4096)

        cache[modelKey] = newSize
        log.info("Reduced context size for $modelKey: $currentSize → $newSize tokens (binary search)")
        saveToFile()
        return newSize
    }

    /**
     * Create a model key from provider and model name.
     *
     * @param provider The ModelProvider enum value
     * @param model The model name (e.g., "gpt-4", "llama3")
     * @return The model key in format "provider:model"
     */
    fun modelKey(provider: ModelProvider, model: String): String = "${provider.providerKey()}:$model"

    /**
     * Clear all cached sizes (useful for testing or reset).
     */
    fun clear() {
        cache.clear()
        saveToFile()
    }

    /**
     * Invalidate the cache by clearing all entries and deleting the cache file.
     * This is typically triggered by user action to reset cached model context sizes.
     */
    private fun invalidateCache() {
        try {
            cache.clear()
            cacheFile.deleteIfExists()
            log.info("Model context size cache invalidated and file deleted")
        } catch (e: Exception) {
            log.warn("Failed to invalidate model context cache", e)
        }
    }

    private fun loadFromFile() {
        try {
            if (cacheFile.exists()) {
                val json = Files.readString(cacheFile)
                val cacheData = JsonUtils.json.decodeFromString<CacheData>(json)
                cache.putAll(cacheData.contextSizes)
                log.info("Loaded ${cache.size} cached model context sizes from $cacheFile")
            } else {
                log.debug("No cached model context sizes found, using defaults")
            }
        } catch (e: Exception) {
            log.warn("Failed to load model context cache from $cacheFile, using defaults", e)
        }
    }

    private fun saveToFile() {
        try {
            cacheFile.parent?.let { Files.createDirectories(it) }
            val data = CacheData(contextSizes = cache.toMap())
            val json = prettyJson.encodeToString(data)
            Files.writeString(cacheFile, json)
            if (log.isDebugEnabled) {
                log.debug("Saved ${cache.size} model context sizes to $cacheFile")
            }
        } catch (e: Exception) {
            log.warn("Failed to save model context cache to $cacheFile", e)
        }
    }

    @Serializable
    private data class CacheData(
        val contextSizes: Map<String, Int>,
    )
}

/**
 * Extension function to detect if an exception is due to context length issues.
 * Checks for common error messages from various AI providers.
 */
fun Throwable.isContextLengthError(): Boolean {
    val message = this.message?.lowercase() ?: ""
    return message.contains("context") && (
        message.contains("length") ||
            message.contains("limit") ||
            message.contains("exceeded") ||
            message.contains("too long") ||
            message.contains("maximum context") ||
            message.contains("token limit") ||
            message.contains("exceed")
        ) || message.contains("413") // HTTP 413 Payload Too Large
}
