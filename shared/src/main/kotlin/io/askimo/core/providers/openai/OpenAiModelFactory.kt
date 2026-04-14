/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
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
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class OpenAiModelFactory : ChatModelFactory<OpenAiSettings> {
    private val log = logger<OpenAiModelFactory>()

    override fun getProvider(): ModelProvider = OPENAI

    override fun availableModels(settings: OpenAiSettings): List<ModelDTO> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()
        return fetchModels(apiKey = apiKey, url = "https://api.openai.com/v1/models", providerName = OPENAI)
            .map { ModelDTO.of(OPENAI, it) }
    }

    override fun defaultSettings(): OpenAiSettings = OpenAiSettings()

    override fun getNoModelsHelpText(): String = """
        One possible reason is that you haven't provided your OpenAI API key yet.

        1. Get your API key from: https://platform.openai.com/account/api-keys
        2. Then set it in the Settings

        Get an API key here: https://platform.openai.com/api-keys
    """.trimIndent()

    override fun create(
        sessionId: String?,
        settings: OpenAiSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient = AiServiceBuilder.buildChatClient(
        sessionId = sessionId,
        settings = settings,
        provider = OPENAI,
        chatModel = createStreamingModel(settings),
        secondaryChatModel = createSecondaryModel(settings),
        chatMemory = chatMemory,
        toolProvider = toolProvider,
        retriever = retriever,
        executionMode = executionMode,
    )

    override fun createImageModel(
        settings: OpenAiSettings,
    ): ImageModel = OpenAiImageModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppConfig.models[OPENAI].imageModel)
        .logger(log)
        .logRequests(log.isDebugEnabled)
        .logResponses(log.isTraceEnabled)
        .build()

    override fun createStreamingModel(settings: OpenAiSettings): StreamingChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .apiKey(safeApiKey(settings.apiKey))
            .modelName(settings.defaultModel)
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.defaultModelTimeoutSeconds))
            .temperature(AppConfig.chat.samplingTemperature)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .listeners(listOf(TelemetryChatModelListener(telemetry, OPENAI.name.lowercase())))
            .build()
    }

    override fun createSecondaryModel(settings: OpenAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .apiKey(safeApiKey(settings.apiKey))
            .modelName(AppConfig.models[OPENAI].utilityModel.ifBlank { settings.defaultModel })
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.utilityModelTimeoutSeconds))
            .build()
    }

    override fun createModel(settings: OpenAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .apiKey(safeApiKey(settings.apiKey))
            .modelName(settings.defaultModel)
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.defaultModelTimeoutSeconds))
            .temperature(AppConfig.chat.samplingTemperature)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .listeners(listOf(TelemetryChatModelListener(telemetry, OPENAI.name.lowercase())))
            .build()
    }

    override fun createUtilityClient(
        settings: OpenAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: OpenAiSettings): EmbeddingModel = OpenAiEmbeddingModelBuilder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppConfig.models[OPENAI].embeddingModel)
        .build()

    override fun getEmbeddingTokenLimit(settings: OpenAiSettings): Int {
        val modelName = AppConfig.models[OPENAI].embeddingModel.lowercase()
        return when {
            modelName.contains("text-embedding-3") -> 8191
            modelName.contains("ada-002") -> 8191
            else -> 8191
        }
    }
}
