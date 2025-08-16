package io.askimo.cli.model.core

import io.askimo.cli.model.providers.ollama.OllamaSettings
import io.askimo.cli.model.providers.openai.OpenAiSettings
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
            ModelProvider.OPEN_AI ->
                (settings as? OpenAiSettings)?.apiKey?.isNotBlank() == true

            ModelProvider.OLLAMA ->
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
            ModelProvider.OLLAMA ->
                """
                💡 Ollama server not reachable at your configured baseUrl.
                1) Install Ollama: https://ollama.com/download
                2) Start it (default listens on http://localhost:11434)
                3) Verify your baseUrl, e.g.: :setparam ollama.baseUrl http://localhost:11434
                """.trimIndent()

            ModelProvider.OPEN_AI ->
                """
                💡💡 To use OpenAI, you need to provide an API key.
                1. Get your API key from: https://platform.openai.com/account/api-keys
                2. Then set it in the CLI using: :setparam api_key YOUR_API_KEY_HERE

                """.trimIndent()

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
