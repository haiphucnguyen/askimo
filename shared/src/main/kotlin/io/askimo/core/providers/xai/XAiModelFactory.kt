/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.openai.OpenAiChatModel
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
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class XAiModelFactory : ChatModelFactory<XAiSettings> {
    private val log = logger<XAiModelFactory>()

    override fun getProvider(): ModelProvider = XAI

    override fun availableModels(settings: XAiSettings): List<ModelDTO> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "${settings.baseUrl.trimEnd('/')}/models"
        return fetchModels(apiKey = apiKey, url = url, providerName = XAI)
            .map { ModelDTO.of(XAI, it) }
    }

    override fun defaultSettings(): XAiSettings = XAiSettings()

    override fun create(
        sessionId: String?,
        settings: XAiSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient = AiServiceBuilder.buildChatClient(
        sessionId = sessionId,
        settings = settings,
        provider = XAI,
        chatModel = createStreamingModel(settings),
        secondaryChatModel = createSecondaryModel(settings),
        chatMemory = chatMemory,
        toolProvider = toolProvider,
        retriever = retriever,
        executionMode = executionMode,
    )

    override fun createImageModel(
        settings: XAiSettings,
    ): ImageModel = OpenAiImageModel.builder()
        .baseUrl(settings.baseUrl)
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppConfig.models[XAI].imageModel)
        .logger(log)
        .logRequests(log.isDebugEnabled)
        .logResponses(log.isTraceEnabled)
        .build()

    override fun createStreamingModel(settings: XAiSettings): StreamingChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .apiKey(safeApiKey(settings.apiKey))
            .baseUrl(settings.baseUrl)
            .modelName(settings.defaultModel)
            .temperature(AppConfig.chat.samplingTemperature)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .listeners(listOf(TelemetryChatModelListener(telemetry, XAI.name.lowercase())))
            .build()
    }

    override fun createSecondaryModel(settings: XAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(settings.apiKey))
            .modelName(AppConfig.models[XAI].utilityModel.ifBlank { settings.defaultModel })
            .timeout(Duration.ofSeconds(AppConfig.models[XAI].utilityModelTimeoutSeconds))
            .build()
    }

    override fun createModel(settings: XAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder())
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey(safeApiKey(settings.apiKey))
            .modelName(settings.defaultModel)
            .temperature(AppConfig.chat.samplingTemperature)
            .listeners(listOf(TelemetryChatModelListener(telemetry, XAI.name.lowercase())))
            .build()
    }

    override fun createUtilityClient(
        settings: XAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryModel(settings))
        .build()
}
