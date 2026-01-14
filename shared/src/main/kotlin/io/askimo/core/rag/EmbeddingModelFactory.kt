/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.error.ModelNotAvailableEvent
import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.LocalModelValidator
import io.askimo.core.providers.ModelAvailabilityResult
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.ANTHROPIC
import io.askimo.core.providers.ModelProvider.DOCKER
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.LMSTUDIO
import io.askimo.core.providers.ModelProvider.LOCALAI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ModelProvider.UNKNOWN
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.docker.DockerAiSettings
import io.askimo.core.providers.gemini.GeminiSettings
import io.askimo.core.providers.lmstudio.LmStudioSettings
import io.askimo.core.providers.localai.LocalAiSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.util.ApiKeyUtils.safeApiKey

private val log = logger("EmbeddingModelFactory")

/**
 * Get the maximum token limit for the current embedding model.
 * Returns a safe default if unable to detect.
 *
 * @param appContext The application context containing provider information
 * @return Maximum number of tokens the model can handle
 */
fun getModelTokenLimit(appContext: AppContext): Int = try {
    when (val provider = appContext.getActiveProvider()) {
        OPENAI -> {
            val modelName = AppConfig.models.openai.embeddingModel.lowercase()
            when {
                modelName.contains("text-embedding-3") -> 8191
                modelName.contains("ada-002") -> 8191
                else -> 8191 // Default for OpenAI models
            }
        }

        GEMINI -> {
            val modelName = AppConfig.models.gemini.embeddingModel.lowercase()
            when {
                modelName.contains("embedding-001") -> 2048
                modelName.contains("text-embedding-004") -> 2048
                else -> 2048 // Default for Gemini models
            }
        }

        // Local AI providers (Ollama, Docker AI, LocalAI, LMStudio)
        // They often use the same model names with different prefixes
        OLLAMA, DOCKER, LOCALAI, LMSTUDIO -> {
            val modelName = when (provider) {
                OLLAMA -> AppConfig.models.ollama.embeddingModel
                DOCKER -> AppConfig.models.docker.embeddingModel
                LOCALAI -> AppConfig.models.localai.embeddingModel
                LMSTUDIO -> AppConfig.models.lmstudio.embeddingModel
                else -> ""
            }.lowercase()

            // Common model patterns across local providers
            when {
                // Popular models with known limits
                modelName.contains("nomic-embed") ||
                    modelName.contains("nomic_embed") -> 8192

                modelName.contains("mxbai-embed") ||
                    modelName.contains("mxbai_embed") -> 512

                modelName.contains("bge-") ||
                    modelName.contains("bge_") -> when {
                    modelName.contains("large") -> 512
                    modelName.contains("base") -> 512
                    modelName.contains("small") -> 512
                    else -> 512
                }

                modelName.contains("gte-") ||
                    modelName.contains("gte_") -> 8192

                modelName.contains("e5-") ||
                    modelName.contains("e5_") -> 512

                modelName.contains("all-minilm") ||
                    modelName.contains("all_minilm") -> 512

                modelName.contains("sentence-transformers") -> 512

                // OpenAI-compatible models (text-embedding-3, etc.)
                modelName.contains("text-embedding-3") -> 8191
                modelName.contains("text-embedding") -> 8191

                // Qwen embedding models
                modelName.contains("qwen") && modelName.contains("embed") -> 8192

                else -> 2048
            }
        }

        ANTHROPIC, XAI -> {
            throw UnsupportedOperationException("${provider.name} does not provide embedding models")
        }

        UNKNOWN -> 2048
    }
} catch (e: Exception) {
    log.warn("Failed to detect model token limit, using conservative default: ${e.message}")
    2048
}

fun getEmbeddingModel(appContext: AppContext): EmbeddingModel = when (appContext.getActiveProvider()) {
    OPENAI -> {
        val openAiKey = (appContext.getCurrentProviderSettings() as OpenAiSettings).apiKey
        val modelName = AppConfig.models.openai.embeddingModel
        OpenAiEmbeddingModelBuilder()
            .apiKey(safeApiKey(openAiKey))
            .modelName(modelName)
            .build()
    }

    GEMINI -> {
        val settings = appContext.getCurrentProviderSettings() as GeminiSettings
        buildGeminiEmbeddingModel(settings)
    }

    DOCKER -> {
        val settings = appContext.getCurrentProviderSettings() as DockerAiSettings
        buildDockerEmbeddingModel(settings)
    }

    LOCALAI -> {
        val settings = appContext.getCurrentProviderSettings() as LocalAiSettings
        buildLocalAiEmbeddingModel(settings)
    }

    LMSTUDIO -> {
        val settings = appContext.getCurrentProviderSettings() as LmStudioSettings
        buildLmStudioEmbeddingModel(settings)
    }

    ANTHROPIC -> {
        throw UnsupportedOperationException(
            "Anthropic does not provide embedding models. " +
                "RAG features are not available with Anthropic. " +
                "Please switch to a provider that supports embeddings (OpenAI, Gemini, Ollama, etc.) to use project-based RAG.",
        )
    }

    XAI -> {
        throw UnsupportedOperationException(
            "xAI does not provide embedding models. " +
                "RAG features are not available with xAI. " +
                "Please switch to a provider that supports embeddings (OpenAI, Gemini, Ollama, etc.) to use project-based RAG.",
        )
    }

    OLLAMA -> {
        val settings = appContext.getCurrentProviderSettings() as OllamaSettings
        buildOllamaEmbeddingModel(settings)
    }

    UNKNOWN -> error("Unsupported embedding provider: ${appContext.getActiveProvider()}")
}

private fun buildOllamaEmbeddingModel(settings: OllamaSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.models.ollama.embeddingModel

    ensureModelAvailable(OLLAMA, baseUrl, modelName)

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

private fun buildGeminiEmbeddingModel(settings: GeminiSettings): EmbeddingModel {
    val apiKey = settings.apiKey
    val modelName = AppConfig.models.gemini.embeddingModel

    log.display(
        """
        ℹ️  Using Gemini for embeddings
           • Embedding model: $modelName
           • Configure in askimo.yml: models.gemini.embedding_model
        """.trimIndent(),
    )

    return GoogleAiEmbeddingModel.builder()
        .apiKey(safeApiKey(apiKey))
        .modelName(modelName)
        .build()
}

private fun buildDockerEmbeddingModel(settings: DockerAiSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.models.docker.embeddingModel

    log.display(
        """
        ℹ️  Using Docker AI for embeddings
           • Docker AI URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: models.docker.embedding_model
        """.trimIndent(),
    )

    ensureModelAvailable(DOCKER, baseUrl, modelName)

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

private fun buildLocalAiEmbeddingModel(settings: LocalAiSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.models.localai.embeddingModel

    log.display(
        """
        ℹ️  Using LocalAI for embeddings
           • LocalAI URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: models.localai.embedding_model
        """.trimIndent(),
    )

    ensureModelAvailable(LOCALAI, baseUrl, modelName)

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

private fun buildLmStudioEmbeddingModel(settings: LmStudioSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.models.lmstudio.embeddingModel

    log.display(
        """
        ℹ️  Using LMStudio for embeddings
           • LMStudio URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: models.lmstudio.embedding_model
        """.trimIndent(),
    )
    ensureModelAvailable(LMSTUDIO, baseUrl, modelName)
    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

/**
 * Ensures that a model is available on a local provider.
 * Attempts to pull the model if it's not available (for providers that support auto-pull like Ollama).
 */
private fun ensureModelAvailable(
    provider: ModelProvider,
    baseUrl: String,
    modelName: String,
) {
    val result = LocalModelValidator.checkModelExists(provider, baseUrl, modelName)

    when (result) {
        is ModelAvailabilityResult.Available -> {
            log.display("✅ ${provider.name} model '$modelName' is ready")
        }

        is ModelAvailabilityResult.ProviderUnreachable -> {
            val errorMessage = """
                ❌ ${result.error}

                Please ensure ${provider.name} is running and accessible at: $baseUrl
            """.trimIndent()

            log.displayError(errorMessage)

            EventBus.post(
                ModelNotAvailableEvent(
                    provider = provider,
                    modelName = modelName,
                    isEmbedding = true,
                    reason = "${provider.name} not reachable at $baseUrl",
                ),
            )

            error("${provider.name} not reachable at $baseUrl")
        }

        is ModelAvailabilityResult.NotAvailable -> {
            val errorMessage = """
                ❌ ${result.reason}

                Please ensure the model is available in ${provider.name} at: $baseUrl
            """.trimIndent()

            log.displayError(errorMessage)

            // Emit error event for UI to handle
            EventBus.post(
                ModelNotAvailableEvent(
                    provider = provider,
                    modelName = modelName,
                    isEmbedding = true,
                    reason = result.reason,
                ),
            )

            error("Model '$modelName' not available in ${provider.name}")
        }
    }
}

fun getEmbeddingStore(projectId: String, embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
    val jVectorIndexDir = RagUtils.getProjectJVectorIndexDir(projectId)

    val embeddingStore = JVectorEmbeddingStore.builder()
        .dimension(RagUtils.getDimensionForModel(embeddingModel))
        .persistencePath(jVectorIndexDir.toString())
        .build()
    return embeddingStore
}

fun enrichContentRetrieverWithLucene(classifierChatClient: ChatClient, projectId: String, retriever: ContentRetriever): ContentRetriever {
    val ragConfig = AppConfig.rag
    val telemetry = AppContext.getInstance().telemetry

    return RAGContentProcessor(
        HybridContentRetriever(
            vectorRetriever = retriever,
            keywordRetriever = LuceneKeywordRetriever(projectId),
            maxResults = ragConfig.hybridMaxResults,
            k = ragConfig.rankFusionConstant,
        ),
        classifierChatClient,
        telemetry,
    )
}
