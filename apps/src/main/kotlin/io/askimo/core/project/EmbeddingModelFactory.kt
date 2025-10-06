/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import io.askimo.core.providers.ModelProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

// Defaults (can be overridden by env)
private const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
private const val DEFAULT_OLLAMA_EMBED_MODEL = "nomic-embed-text"
private const val DEFAULT_OPENAI_EMBED_MODEL = "text-embedding-3-small"

// One-time warning flag so we don't spam the console
private val warnedOllamaOnce = AtomicBoolean(false)

fun getEmbeddingModel(provider: ModelProvider): EmbeddingModel =
    when (provider) {
        ModelProvider.OPEN_AI -> {
            val openAiKey =
                System.getenv("OPENAI_API_KEY")
                    ?: error("OPENAI_API_KEY missing for OpenAI embeddings")
            val modelName = System.getenv("OPENAI_EMBED_MODEL") ?: DEFAULT_OPENAI_EMBED_MODEL
            OpenAiEmbeddingModelBuilder()
                .apiKey(openAiKey)
                .modelName(modelName)
                .build()
        }

        // For providers with no native embeddings, we ALWAYS use local Ollama
        ModelProvider.ANTHROPIC, ModelProvider.GEMINI, ModelProvider.X_AI -> {
            noteOllamaRequired(provider)
            buildOllamaEmbeddingModel()
        }

        // If user explicitly picked OLLAMA for chat, also use Ollama for embeddings
        ModelProvider.OLLAMA -> buildOllamaEmbeddingModel()

        ModelProvider.UNKNOWN -> error("Unsupported embedding provider: $provider")
    }

private fun buildOllamaEmbeddingModel(): EmbeddingModel {
    val url = System.getenv("OLLAMA_URL") ?: DEFAULT_OLLAMA_URL
    val model = System.getenv("OLLAMA_EMBED_MODEL") ?: DEFAULT_OLLAMA_EMBED_MODEL

    ensureOllamaAvailable(url, model)

    return OllamaEmbeddingModelBuilder()
        .baseUrl(url)
        .modelName(model)
        .build()
}

/**
 * Notes to the user (printed once) that Anthropic/Gemini/xAI require a local Ollama
 * installation and an embedding model pulled.
 */
private fun noteOllamaRequired(provider: ModelProvider) {
    if (warnedOllamaOnce.compareAndSet(false, true)) {
        val model = System.getenv("OLLAMA_EMBED_MODEL") ?: DEFAULT_OLLAMA_EMBED_MODEL
        val url = System.getenv("OLLAMA_URL") ?: DEFAULT_OLLAMA_URL
        println(
            """
            ℹ️  ${provider.name} does not provide embeddings. Askimo uses local Ollama for RAG embeddings.
               • Ollama URL: $url
               • Embedding model: $model
               • If not installed: https://ollama.com/download
               • Start server:    ollama serve
               • Pull model:      ollama pull $model
            """.trimIndent(),
        )
    }
}

/**
 * Checks that Ollama is reachable and the model exists.
 * Throws an actionable error if not.
 */
private fun ensureOllamaAvailable(
    baseUrl: String,
    model: String,
) {
    val tagsBody =
        try {
            val conn =
                (URL("${baseUrl.removeSuffix("/")}/api/tags").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 1500
                    requestMethod = "GET"
                    doInput = true
                }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            error(
                """
                ❌ Ollama not reachable at $baseUrl

                To enable embeddings for ${model.ifBlank { DEFAULT_OLLAMA_EMBED_MODEL }}:
                  • Install: https://ollama.com/download
                  • Start:   ollama serve
                  • Pull:    ollama pull $model
                """.trimIndent(),
            )
        }

    if (!tagsBody.contains("\"name\":\"$model\"")) {
        error(
            """
            ❌ Ollama model '$model' not found.

            Pull it first:
              ollama pull $model
            """.trimIndent(),
        )
    }
}
