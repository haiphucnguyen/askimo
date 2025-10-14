/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.ANTHROPIC
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ModelProvider.UNKNOWN
import io.askimo.core.providers.ModelProvider.X_AI
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
private const val DEFAULT_OLLAMA_EMBED_MODEL = "nomic-embed-text:latest"
private const val DEFAULT_OPENAI_EMBED_MODEL = "text-embedding-3-small"

private val warnedOllamaOnce = AtomicBoolean(false)

fun getEmbeddingModel(provider: ModelProvider): EmbeddingModel =
    when (provider) {
        OPEN_AI -> {
            val openAiKey =
                System.getenv("OPENAI_API_KEY")
                    ?: error("OPENAI_API_KEY missing for OpenAI embeddings")
            val modelName = System.getenv("OPENAI_EMBED_MODEL") ?: DEFAULT_OPENAI_EMBED_MODEL
            OpenAiEmbeddingModelBuilder()
                .apiKey(openAiKey)
                .modelName(modelName)
                .build()
        }

        ANTHROPIC, GEMINI, X_AI -> {
            noteOllamaRequired(provider)
            buildOllamaEmbeddingModel()
        }

        OLLAMA -> buildOllamaEmbeddingModel()

        UNKNOWN -> error("Unsupported embedding provider: $provider")
    }

private fun buildOllamaEmbeddingModel(): EmbeddingModel {
    val url = System.getProperty("OLLAMA_URL") ?: System.getenv("OLLAMA_URL") ?: DEFAULT_OLLAMA_URL
    val model = System.getProperty("OLLAMA_EMBED_MODEL") ?: System.getenv("OLLAMA_EMBED_MODEL") ?: DEFAULT_OLLAMA_EMBED_MODEL

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
    val tags = getTags(baseUrl, readMs = 8_000) ?: error(notReachable(baseUrl, model))
    if (hasModel(tags, model)) return

    // ⛓️ Pull synchronously; returns only when the model is ready
    if (!pullSync(baseUrl, model)) {
        error("❌ Failed to pull Ollama model '$model'. Try: ollama pull $model")
    }

    // One re-check (no polling needed since pullSync blocks)
    val after = getTags(baseUrl, readMs = 8_000) ?: error(notReachable(baseUrl, model))
    if (!hasModel(after, model)) {
        error("❌ Ollama model '$model' still not listed after synchronous pull.")
    }
}

private fun pullSync(
    baseUrl: String,
    model: String,
): Boolean =
    try {
        val url = URI("${baseUrl.removeSuffix("/")}/api/pull").toURL()
        val conn =
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                // Big models can take a while; keep generous but finite:
                readTimeout = 600_000 // 10 minutes
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        val payload = """{"name":"$model","stream":false}"""
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
        code in 200..299
    } catch (_: Exception) {
        false
    }

private fun getTags(
    baseUrl: String,
    readMs: Int,
): String? =
    try {
        val url = URI("${baseUrl.removeSuffix("/")}/api/tags").toURL()
        val conn =
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = readMs
                requestMethod = "GET"
                doInput = true
            }
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }

private fun hasModel(
    tagsJson: String,
    model: String,
): Boolean = tagsJson.contains("\"name\":\"$model\"") || tagsJson.contains("\"name\":\"$model:")

private fun notReachable(
    baseUrl: String,
    model: String,
) = """
    ❌ Ollama not reachable at $baseUrl

    To enable embeddings for ${model.ifBlank { DEFAULT_OLLAMA_EMBED_MODEL }}:
      • Install: https://ollama.com/download
      • Start:   ollama serve
      • Pull:    ollama pull $model
    """.trimIndent()
