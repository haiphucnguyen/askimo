/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
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
fun getModelTokenLimit(appContext: AppContext): Int {
    return try {
        val provider = appContext.getActiveProvider()

        when (provider) {
            OPENAI -> {
                val modelName = AppConfig.embeddingModels.openai.lowercase()
                when {
                    modelName.contains("text-embedding-3") -> 8191
                    modelName.contains("ada-002") -> 8191
                    else -> 8191 // Default for OpenAI models
                }
            }

            GEMINI -> {
                val modelName = AppConfig.embeddingModels.gemini.lowercase()
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
                    OLLAMA -> AppConfig.embeddingModels.ollama
                    DOCKER -> AppConfig.embeddingModels.docker
                    LOCALAI -> AppConfig.embeddingModels.localai
                    LMSTUDIO -> AppConfig.embeddingModels.lmstudio
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
}

fun getEmbeddingModel(appContext: AppContext): EmbeddingModel = when (appContext.getActiveProvider()) {
    OPENAI -> {
        val openAiKey = (appContext.getCurrentProviderSettings() as OpenAiSettings).apiKey
        val modelName = AppConfig.embeddingModels.openai

        val baseUrl = if (AppConfig.proxy.enabled && AppConfig.proxy.url.isNotBlank()) {
            "${AppConfig.proxy.url}/openai"
        } else {
            "https://api.openai.com/v1"
        }
        OpenAiEmbeddingModelBuilder()
            .apiKey(safeApiKey(openAiKey))
            .baseUrl(baseUrl)
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
    val modelName = AppConfig.embeddingModels.ollama

    ensureModelAvailable(ModelProvider.OLLAMA, baseUrl, modelName)

    return OllamaEmbeddingModelBuilder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

private fun buildGeminiEmbeddingModel(settings: GeminiSettings): EmbeddingModel {
    val apiKey = settings.apiKey
    val modelName = AppConfig.embeddingModels.gemini

    log.display(
        """
        ℹ️  Using Gemini for embeddings
           • Embedding model: $modelName
           • Configure in askimo.yml: embedding_models.gemini
        """.trimIndent(),
    )

    return GoogleAiEmbeddingModel.builder()
        .apiKey(safeApiKey(apiKey))
        .modelName(modelName)
        .build()
}

private fun buildDockerEmbeddingModel(settings: DockerAiSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.embeddingModels.docker

    log.display(
        """
        ℹ️  Using Docker AI for embeddings
           • Docker AI URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: embedding_models.docker
        """.trimIndent(),
    )

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl("$baseUrl/v1")
        .modelName(modelName)
        .build()
}

private fun buildLocalAiEmbeddingModel(settings: LocalAiSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.embeddingModels.localai

    log.display(
        """
        ℹ️  Using LocalAI for embeddings
           • LocalAI URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: embedding_models.localai
        """.trimIndent(),
    )

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()
}

private fun buildLmStudioEmbeddingModel(settings: LmStudioSettings): EmbeddingModel {
    val baseUrl = settings.baseUrl.removeSuffix("/")
    val modelName = AppConfig.embeddingModels.lmstudio

    log.display(
        """
        ℹ️  Using LMStudio for embeddings
           • LMStudio URL: $baseUrl
           • Embedding model: $modelName
           • Configure in askimo.yml: embedding_models.lmstudio
        """.trimIndent(),
    )

    return OpenAiEmbeddingModelBuilder()
        .apiKey("not-needed")
        .baseUrl("$baseUrl/v1")
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
            log.displayError(
                """
                ❌ ${result.error}

                Please ensure ${provider.name} is running and accessible at: $baseUrl
                """.trimIndent(),
            )
            error("${provider.name} not reachable at $baseUrl")
        }

        is ModelAvailabilityResult.NotAvailable -> {
            if (result.canAutoPull && provider == ModelProvider.OLLAMA) {
                log.display("⏳ Model '$modelName' not found. Attempting to download...")
                if (LocalModelValidator.pullOllamaModel(baseUrl, modelName)) {
                    log.display("✅ Successfully downloaded model '$modelName'")
                } else {
                    log.displayError(
                        """
                        ❌ Failed to download model '$modelName'

                        Please download it manually:
                          ollama pull $modelName
                        """.trimIndent(),
                    )
                    error("Failed to pull ${provider.name} model '$modelName'")
                }
            } else {
                log.displayError(
                    """
                    ❌ ${result.reason}

                    Please ensure the model is available in ${provider.name} at: $baseUrl
                    """.trimIndent(),
                )
                error("Model '$modelName' not available in ${provider.name}")
            }
        }
    }
}
