/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.providers.ModelProvider.ANTHROPIC
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.LMSTUDIO
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.anthropic.AnthropicSettings
import io.askimo.core.providers.gemini.GeminiSettings
import io.askimo.core.providers.lmstudio.LmStudioSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.providers.xai.XAiSettings
import java.net.HttpURLConnection
import java.net.URI

/**
 * Utility for validating and providing setup instructions for model providers.
 *
 * This object handles checking if a model provider is properly configured and ready for use,
 * as well as providing user-friendly instructions for setting up providers that aren't
 * properly configured.
 */
object ProviderValidator {
    /**
     * Validates whether the given provider is ready for use.
     * @return true if the provider is usable, false if configuration/setup is missing.
     */
    fun validate(
        provider: ModelProvider,
        settings: ProviderSettings,
    ): Boolean = when (provider) {
        OPENAI ->
            (settings as? OpenAiSettings)?.let { s ->
                s.apiKey.isNotBlank() && testApiConnection(s.apiKey, "https://api.openai.com/v1/models")
            } == true
        XAI ->
            (settings as? XAiSettings)?.let { s ->
                s.apiKey.isNotBlank() && testApiConnection(s.apiKey, "https://api.x.ai/v1/models")
            } == true
        GEMINI ->
            (settings as? GeminiSettings)?.let { s ->
                s.apiKey.isNotBlank() && testGeminiConnection(s.apiKey)
            } == true
        ANTHROPIC ->
            (settings as? AnthropicSettings)?.let { s ->
                (s.apiKey.isNotBlank() || s.apiKey.equals("default")) &&
                    testAnthropicConnection(s.apiKey)
            } == true

        OLLAMA ->
            (settings as? OllamaSettings)?.let { s ->
                s.baseUrl.isNotBlank() && isHttpReachable(s.baseUrl)
            } == true

        LMSTUDIO ->
            (settings as? LmStudioSettings)?.let { s ->
                s.baseUrl.isNotBlank() && isHttpReachable(s.baseUrl)
            } == true

        else -> true
    }

    /**
     * Returns help instructions for how to set up the given provider.
     */
    fun getHelpText(provider: ModelProvider): String = when (provider) {
        OLLAMA ->
            """
                💡 Ollama server not reachable at your configured baseUrl.
                1) Install Ollama: https://ollama.com/download
                2) Start it (default listens on http://localhost:11434)
                3) Verify your baseUrl, e.g.: :set-param ollama.baseUrl http://localhost:11434
            """.trimIndent()

        LMSTUDIO ->
            """
                💡 LM Studio server not reachable at your configured baseUrl.
                1) Install LM Studio: https://lmstudio.ai/
                2) Start the local server (default listens on http://localhost:1234/v1)
                3) Load a model in LM Studio
                4) Verify your baseUrl matches the server address shown in LM Studio
            """.trimIndent()

        OPENAI ->
            """
                💡💡 To use OpenAI, you need to provide an API key.
                1. Get your API key from: https://platform.openai.com/account/api-keys
                2. Then set it in the CLI using: :set-param api_key YOUR_API_KEY_HERE

            """.trimIndent()
        XAI ->
            """
                💡💡 To use XAI, you need to provide an API key.
                1. Get your API key from: https://console.x.ai/
                2. Then set it in the CLI using: :set-param api_key YOUR_API_KEY_HERE
            """.trimIndent().trimIndent()

        GEMINI ->
            """
                💡 To use Google Gemini, you need a valid API key.
                1) Create or retrieve your key from Google AI Studio: https://makersuite.google.com/
                2) Set it in the CLI using: :set-param api_key YOUR_API_KEY_HERE

            """.trimIndent().trimIndent()

        ANTHROPIC ->
            """
                💡 To use Anthropic (Claude models), you need an API key.
                1) Get your API key from: https://console.anthropic.com/account/keys
                2) Then set it in the CLI using: :set-param api_key YOUR_API_KEY_HERE
                3) Example model: claude-3-5-sonnet-latest

            """.trimIndent().trimIndent()

        else -> "💡 This provider requires custom configuration. Please refer to its documentation."
    }

    private fun isHttpReachable(
        baseUrl: String,
        timeoutMs: Int = 1500,
    ): Boolean = runCatching {
        val uri = URI(baseUrl.trim())
        val url = if (uri.path.isNullOrBlank()) URI("$baseUrl/").toURL() else uri.toURL()
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            val code = runCatching { responseCode }.getOrDefault(0)
            code in 200..499
        }
    }.getOrElse { false }

    /**
     * Tests API connection by making a request to the models endpoint.
     * Works for OpenAI-compatible APIs (OpenAI, XAI, etc.)
     */
    private fun testApiConnection(
        apiKey: String,
        modelsUrl: String,
        timeoutMs: Int = 5000,
    ): Boolean = runCatching {
        val uri = URI(modelsUrl)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")

        val responseCode = connection.responseCode
        connection.disconnect()

        // 200 = success, 401 = unauthorized (invalid API key)
        responseCode == 200
    }.getOrElse { false }

    /**
     * Tests Gemini API connection.
     */
    private fun testGeminiConnection(
        apiKey: String,
        timeoutMs: Int = 5000,
    ): Boolean = runCatching {
        // Gemini uses a different URL pattern with API key in the URL
        val uri = URI("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("Content-Type", "application/json")

        val responseCode = connection.responseCode
        connection.disconnect()

        responseCode == 200
    }.getOrElse { false }

    /**
     * Tests Anthropic API connection.
     */
    private fun testAnthropicConnection(
        apiKey: String,
        timeoutMs: Int = 5000,
    ): Boolean = runCatching {
        // For "default" key used in testing, skip validation
        if (apiKey == "default") {
            return@runCatching true
        }

        // Anthropic doesn't have a models endpoint, so we make a minimal request
        // to check if the API key is valid
        val uri = URI("https://api.anthropic.com/v1/messages")
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Send a minimal invalid request just to test authentication
        // Valid API key will return 400 (bad request), invalid key returns 401
        connection.outputStream.use { it.write("{}".toByteArray()) }

        val responseCode = connection.responseCode
        connection.disconnect()

        // 400 means authenticated but bad request (which is expected)
        // 401 means authentication failed (invalid API key)
        responseCode == 400
    }.getOrElse { false }
}
