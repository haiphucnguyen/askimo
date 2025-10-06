/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.providers.ModelProvider.ANTHROPIC
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ModelProvider.X_AI
import io.askimo.core.providers.anthropic.AnthropicSettings
import io.askimo.core.providers.gemini.GeminiSettings
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
    ): Boolean =
        when (provider) {
            OPEN_AI ->
                (settings as? OpenAiSettings)?.apiKey?.isNotBlank() == true
            X_AI ->
                (settings as? XAiSettings)?.apiKey?.isNotBlank() == true
            GEMINI ->
                (settings as? GeminiSettings)?.apiKey?.isNotBlank() == true
            ANTHROPIC ->
                (settings as? AnthropicSettings)?.apiKey?.isNotBlank() == true ||
                    (settings as? AnthropicSettings)?.apiKey.equals("default")

            OLLAMA ->
                (settings as? OllamaSettings)?.let { s ->
                    s.baseUrl.isNotBlank() && isHttpReachable(s.baseUrl)
                } == true

            else -> true
        }

    /**
     * Returns help instructions for how to set up the given provider.
     */
    fun getHelpText(provider: ModelProvider): String =
        when (provider) {
            OLLAMA ->
                """
                💡 Ollama server not reachable at your configured baseUrl.
                1) Install Ollama: https://ollama.com/download
                2) Start it (default listens on http://localhost:11434)
                3) Verify your baseUrl, e.g.: :setparam ollama.baseUrl http://localhost:11434
                """.trimIndent()

            OPEN_AI ->
                """
                💡💡 To use OpenAI, you need to provide an API key.
                1. Get your API key from: https://platform.openai.com/account/api-keys
                2. Then set it in the CLI using: :setparam api_key YOUR_API_KEY_HERE

                """.trimIndent()
            X_AI ->
                """
                💡💡 To use XAI, you need to provide an API key.
                1. Get your API key from: https://console.x.ai/
                2. Then set it in the CLI using: :setparam api_key YOUR_API_KEY_HERE
                """.trimIndent().trimIndent()

            GEMINI ->
                """
                💡 To use Google Gemini, you need a valid API key.
                1) Create or retrieve your key from Google AI Studio: https://makersuite.google.com/
                2) Set it in the CLI using: :setparam api_key YOUR_API_KEY_HERE

                """.trimIndent().trimIndent()

            ANTHROPIC ->
                """
                💡 To use Anthropic (Claude models), you need an API key.
                1) Get your API key from: https://console.anthropic.com/account/keys
                2) Then set it in the CLI using: :setparam api_key YOUR_API_KEY_HERE
                3) Example model: claude-3-5-sonnet-latest

                """.trimIndent().trimIndent()

            else -> "💡 This provider requires custom configuration. Please refer to its documentation."
        }

    private fun isHttpReachable(
        baseUrl: String,
        timeoutMs: Int = 1500,
    ): Boolean =
        runCatching {
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
}
