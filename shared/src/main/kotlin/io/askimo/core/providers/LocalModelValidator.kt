/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.logging.logger
import java.net.HttpURLConnection
import java.net.URI

/**
 * Result of checking if a model is available on a local provider
 */
sealed class ModelAvailabilityResult {
    object Available : ModelAvailabilityResult()
    data class NotAvailable(val reason: String, val canAutoPull: Boolean = false) : ModelAvailabilityResult()
    data class ProviderUnreachable(val baseUrl: String, val error: String) : ModelAvailabilityResult()
}

/**
 * Utility class for checking if models (including embedding models) are available
 * on local AI providers like Ollama, Docker AI, LocalAI, LMStudio, etc.
 */
object LocalModelValidator {
    private val log = logger("LocalModelValidator")

    /**
     * Check if a model exists on the provider.
     * This is a generic method that works for both chat models and embedding models.
     *
     * @param provider The provider type
     * @param baseUrl The base URL of the provider
     * @param modelName The name of the model to check
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param readTimeoutMs Read timeout in milliseconds
     * @return ModelAvailabilityResult indicating if the model is available
     */
    fun checkModelExists(
        provider: ModelProvider,
        baseUrl: String,
        modelName: String,
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 8_000,
    ): ModelAvailabilityResult = when (provider) {
        ModelProvider.OLLAMA -> checkOllamaModel(baseUrl, modelName, connectTimeoutMs, readTimeoutMs)
        ModelProvider.DOCKER -> checkOpenAiCompatibleModel(
            providerName = "Docker AI",
            baseUrl = baseUrl,
            modelName = modelName,
            apiPath = "/v1/models",
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            canAutoPull = false,
        )
        ModelProvider.LOCALAI -> checkOpenAiCompatibleModel(
            providerName = "LocalAI",
            baseUrl = baseUrl,
            modelName = modelName,
            apiPath = "/v1/models",
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            canAutoPull = false,
        )
        ModelProvider.LMSTUDIO -> checkOpenAiCompatibleModel(
            providerName = "LMStudio",
            baseUrl = baseUrl,
            modelName = modelName,
            apiPath = "/v1/models",
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            canAutoPull = false,
        )
        else -> ModelAvailabilityResult.Available // Cloud providers don't need local model checks
    }

    /**
     * Check if a model exists on Ollama using its native API
     */
    private fun checkOllamaModel(
        baseUrl: String,
        modelName: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): ModelAvailabilityResult {
        try {
            val tags = getOllamaTags(baseUrl, connectTimeoutMs, readTimeoutMs)
                ?: return ModelAvailabilityResult.ProviderUnreachable(
                    baseUrl = baseUrl,
                    error = "Cannot connect to Ollama at $baseUrl. Please ensure Ollama is running: ollama serve",
                )

            val hasModel = tags.contains("\"name\":\"$modelName\"") || tags.contains("\"name\":\"$modelName:")

            return if (hasModel) {
                ModelAvailabilityResult.Available
            } else {
                ModelAvailabilityResult.NotAvailable(
                    reason = "Model '$modelName' not found in Ollama",
                    canAutoPull = true,
                )
            }
        } catch (e: Exception) {
            log.error("Error checking Ollama model: ${e.message}", e)
            return ModelAvailabilityResult.ProviderUnreachable(
                baseUrl = baseUrl,
                error = e.message ?: "Unknown error",
            )
        }
    }

    /**
     * Generic method to check if a model exists on OpenAI-compatible providers
     * (Docker AI, LocalAI, LMStudio, etc.)
     */
    private fun checkOpenAiCompatibleModel(
        providerName: String,
        baseUrl: String,
        modelName: String,
        apiPath: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        canAutoPull: Boolean,
    ): ModelAvailabilityResult {
        try {
            val url = URI("${baseUrl.removeSuffix("/")}$apiPath").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                this.connectTimeout = connectTimeoutMs
                this.readTimeout = readTimeoutMs
                requestMethod = "GET"
                doInput = true
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return ModelAvailabilityResult.ProviderUnreachable(
                    baseUrl = baseUrl,
                    error = "Cannot connect to $providerName at $baseUrl (HTTP $responseCode)",
                )
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val hasModel = response.contains("\"id\":\"$modelName\"") || response.contains("\"id\": \"$modelName\"")

            return if (hasModel) {
                ModelAvailabilityResult.Available
            } else {
                ModelAvailabilityResult.NotAvailable(
                    reason = "Model '$modelName' not found in $providerName",
                    canAutoPull = canAutoPull,
                )
            }
        } catch (e: Exception) {
            log.error("Error checking $providerName model: ${e.message}", e)
            return ModelAvailabilityResult.ProviderUnreachable(
                baseUrl = baseUrl,
                error = e.message ?: "Unknown error",
            )
        }
    }

    /**
     * Get available models/tags from Ollama
     */
    private fun getOllamaTags(
        baseUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): String? = try {
        val url = URI("${baseUrl.removeSuffix("/")}/api/tags").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeoutMs
            this.readTimeout = readTimeoutMs
            requestMethod = "GET"
            doInput = true
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        log.error("Failed to get Ollama tags from $baseUrl: ${e.message}", e)
        null
    }

    /**
     * Attempt to pull a model on Ollama (synchronous)
     */
    fun pullOllamaModel(
        baseUrl: String,
        modelName: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 600_000,
    ): Boolean = try {
        val url = URI("${baseUrl.removeSuffix("/")}/api/pull").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeoutMs
            this.readTimeout = readTimeoutMs
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = """{"name":"$modelName","stream":false}"""
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }

        code in 200..299
    } catch (e: Exception) {
        log.error("Failed to pull Ollama model $modelName from $baseUrl: ${e.message}", e)
        false
    }
}
