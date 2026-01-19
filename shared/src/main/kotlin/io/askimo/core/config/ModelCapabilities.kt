/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.askimo.core.event.EventBus
import io.askimo.core.event.system.InvalidateCacheEvent
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.util.AskimoHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * Persistent cache for model capabilities.
 * Tracks which sampling parameters (temperature, topP) are supported by specific models.
 *
 * Cache is stored separately from AppConfig to avoid polluting user configuration
 * with runtime state. The cache file lives at ~/.askimo/model_capabilities.json
 *
 * Design:
 * - Cache starts empty
 * - Gets populated when models are tested (either successfully or via API errors)
 * - Persists across application restarts
 * - Thread-safe singleton
 */
object ModelCapabilities {
    private val log = logger<ModelCapabilities>()
    private val cacheFile = AskimoHome.base().resolve("model_capabilities.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cache = mutableMapOf<String, Boolean>()
    private val lock = Any()

    init {
        loadCache()

        scope.launch {
            EventBus.internalEvents.collect { event ->
                if (event is InvalidateCacheEvent) {
                    invalidateCache()
                }
            }
        }
    }

    /**
     * Check if a model supports sampling parameters (temperature, topP).
     *
     * @param provider The model provider (OpenAI, Anthropic, etc.)
     * @param model The model name/identifier
     * @return true if known to support, false if known to NOT support, null if unknown
     */
    fun supportsSampling(provider: ModelProvider, model: String): Boolean? {
        val key = createKey(provider, model)
        return synchronized(lock) {
            cache[key]
        }
    }

    /**
     * Update the cache with sampling support information.
     * Typically called after:
     * - Successfully using sampling params with a model (supported = true)
     * - Getting API error about unsupported params (supported = false)
     *
     * @param provider The model provider
     * @param model The model name/identifier
     * @param supported Whether the model supports sampling parameters
     */
    fun setSamplingSupport(provider: ModelProvider, model: String, supported: Boolean) {
        val key = createKey(provider, model)
        synchronized(lock) {
            cache[key] = supported
        }
        saveCache()

        log.debug("Updated model capability: $key -> supportsSampling=$supported")
    }

    /**
     * Clear the entire cache.
     * Useful for testing or when switching environments.
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
        saveCache()

        log.info("Cleared model capabilities cache")
    }

    /**
     * Remove a specific model from the cache.
     * Useful when a model's capabilities may have changed.
     */
    fun invalidate(provider: ModelProvider, model: String) {
        val key = createKey(provider, model)
        synchronized(lock) {
            cache.remove(key)
        }
        saveCache()

        log.debug("Invalidated cache for: $key")
    }

    /**
     * Get the current cache size.
     * Useful for debugging and monitoring.
     */
    fun size(): Int = synchronized(lock) { cache.size }

    /**
     * Invalidate the entire cache by clearing all entries and deleting the cache file.
     * This is typically triggered by InvalidateCacheEvent to reset all cached model capabilities.
     */
    private fun invalidateCache() {
        try {
            synchronized(lock) {
                cache.clear()
            }
            cacheFile.deleteIfExists()
            log.info("Model capabilities cache invalidated and file deleted")
        } catch (e: Exception) {
            log.warn("Failed to invalidate model capabilities cache", e)
        }
    }

    private fun createKey(provider: ModelProvider, model: String): String = "${provider.providerKey()}:$model"

    private fun loadCache() {
        if (!cacheFile.exists()) {
            log.debug("Model capabilities cache file does not exist yet: {}", cacheFile)
            return
        }

        try {
            val json = Files.readString(cacheFile)
            val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            val loaded: Map<String, Boolean> = mapper.readValue(json)

            synchronized(lock) {
                cache.clear()
                cache.putAll(loaded)
            }

            log.debug("Loaded model capabilities cache: {} entries from {}", cache.size, cacheFile)
        } catch (e: Exception) {
            log.displayError("Failed to load model capabilities cache from $cacheFile", e)
        }
    }

    /**
     * Save the cache to disk.
     * Called after any cache modification.
     */
    private fun saveCache() {
        try {
            cacheFile.parent?.createDirectories()

            val cacheSnapshot = synchronized(lock) {
                cache.toMap()
            }

            val mapper = ObjectMapper().writerWithDefaultPrettyPrinter()
            Files.writeString(cacheFile, mapper.writeValueAsString(cacheSnapshot))

            log.debug("Saved model capabilities cache: {} entries to {}", cacheSnapshot.size, cacheFile)
        } catch (e: Exception) {
            log.displayError("Failed to save model capabilities cache to $cacheFile", e)
        }
    }
}
