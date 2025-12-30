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

    private val LOCAL_PROVIDERS = setOf(
        ModelProvider.OLLAMA,
        ModelProvider.DOCKER,
        ModelProvider.LOCALAI,
        ModelProvider.LMSTUDIO,
    )

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
    ): ModelAvailabilityResult = if (provider in LOCAL_PROVIDERS) {
        checkOpenAiCompatibleModel(
            providerName = provider,
            baseUrl = baseUrl,
            modelName = modelName,
            apiPath = "/models",
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            canAutoPull = false,
        )
    } else {
        ModelAvailabilityResult.Available
    }

    /**
     * Generic method to check if a model exists on OpenAI-compatible providers
     * (Docker AI, LocalAI, LMStudio, etc.)
     */
    private fun checkOpenAiCompatibleModel(
        providerName: ModelProvider,
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
