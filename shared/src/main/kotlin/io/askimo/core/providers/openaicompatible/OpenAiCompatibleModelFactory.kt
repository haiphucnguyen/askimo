/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openaicompatible

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder
import dev.langchain4j.model.openai.OpenAiImageModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.tool.ToolProvider
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelDTO
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class OpenAiCompatibleModelFactory : ChatModelFactory<OpenAiCompatibleSettings> {
    private val log = logger<OpenAiCompatibleModelFactory>()

    override fun getProvider(): ModelProvider = ModelProvider.OPENAI_COMPATIBLE

    private fun createHttpClientBuilder(baseUrl: String) = JdkHttpClient.builder().httpClientBuilder(
        ProxyUtil.configureProxy(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1),
            baseUrl,
        ),
    )

    override fun availableModels(settings: OpenAiCompatibleSettings): List<ModelDTO> = fetchModels(
        apiKey = settings.apiKey.ifBlank { "not-needed" },
        url = "${settings.baseUrl.removeSuffix("/")}/models",
        providerName = ModelProvider.OPENAI_COMPATIBLE,
    ).map { ModelDTO.of(ModelProvider.OPENAI_COMPATIBLE, it) }

    override fun defaultSettings(): OpenAiCompatibleSettings = OpenAiCompatibleSettings()

    override fun getNoModelsHelpText(): String = """
        One possible reason is that your server URL or API key is not configured.

        1. Set the Base URL for your OpenAI-compatible server (e.g., http://localhost:8000/v1)
        2. Add an API key if your server requires it
    """.trimIndent()

    override fun create(
        sessionId: String?,
        settings: OpenAiCompatibleSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)
        val apiKey = settings.apiKey.ifBlank { "not-needed" }

        val chatModel = OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(apiKey))
            .modelName(settings.defaultModel)
            .temperature(AppConfig.chat.samplingTemperature)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.OPENAI_COMPATIBLE.providerKey())))
            .build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            settings = settings,
            provider = ModelProvider.OPENAI_COMPATIBLE,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            toolProvider = toolProvider,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    override fun createImageModel(settings: OpenAiCompatibleSettings): ImageModel {
        val apiKey = settings.apiKey.ifBlank { "not-needed" }
        return OpenAiImageModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(apiKey))
            .modelName(AppConfig.models[ModelProvider.OPENAI_COMPATIBLE].imageModel)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .build()
    }

    private fun createSecondaryChatModel(settings: OpenAiCompatibleSettings): ChatModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)
        val modelName = AppConfig.models[ModelProvider.OPENAI_COMPATIBLE].utilityModel
            .ifBlank { settings.defaultModel }
        val apiKey = settings.apiKey.ifBlank { "not-needed" }

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(apiKey))
            .modelName(modelName)
            .timeout(Duration.ofSeconds(AppConfig.models[ModelProvider.OPENAI_COMPATIBLE].utilityModelTimeoutSeconds))
            .build()
    }

    override fun createUtilityClient(settings: OpenAiCompatibleSettings): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: OpenAiCompatibleSettings): EmbeddingModel {
        val apiKey = settings.apiKey.ifBlank { "not-needed" }
        return OpenAiEmbeddingModelBuilder()
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(apiKey))
            .modelName(AppConfig.models[ModelProvider.OPENAI_COMPATIBLE].embeddingModel)
            .build()
    }

    override fun getEmbeddingTokenLimit(settings: OpenAiCompatibleSettings): Int {
        val modelName = AppConfig.models[ModelProvider.OPENAI_COMPATIBLE].embeddingModel.lowercase()
        return when {
            modelName.contains("text-embedding-3") -> 8191
            modelName.contains("ada-002") -> 8191
            else -> 8191
        }
    }
}
