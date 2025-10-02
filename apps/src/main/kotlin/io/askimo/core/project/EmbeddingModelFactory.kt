/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ModelProvider.X_AI

fun getEmbeddingModel(provider: ModelProvider): EmbeddingModel =
    when (provider) {
        OPEN_AI -> {
            OpenAiEmbeddingModelBuilder().modelName("text-similarity-3-large").build()
        }

        OLLAMA -> {
            val url = System.getenv("OLLAMA_URL") ?: "http://localhost:11434"
            OllamaEmbeddingModelBuilder()
                .baseUrl(url)
                .modelName("all-mpnet-base-v2")
                .build()
        }

        X_AI, GEMINI -> {
            error("${provider.name} does not support RAG embeddings yet")
        }

        ModelProvider.UNKNOWN -> error("Unsupported embedding provider: $provider")
    }
