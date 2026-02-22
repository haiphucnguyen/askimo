/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.lmstudio

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
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
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class LmStudioModelFactory : ChatModelFactory<LmStudioSettings> {

    private val log = logger<LmStudioModelFactory>()

    override fun getProvider(): ModelProvider = ModelProvider.LMSTUDIO

    /**
     * Creates a JdkHttpClient builder configured with proxy settings.
     * Automatically skips proxy for localhost URLs.
     */
    private fun createHttpClientBuilder(baseUrl: String) = JdkHttpClient.builder().httpClientBuilder(
        ProxyUtil.configureProxy(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1),
            baseUrl,
        ),
    )

    override fun availableModels(settings: LmStudioSettings): List<String> = fetchModels(
        apiKey = "lm-studio",
        url = "${settings.baseUrl}/v1/models",
        providerName = ModelProvider.LMSTUDIO,
    )

    override fun defaultSettings(): LmStudioSettings = LmStudioSettings()

    override fun create(
        sessionId: String?,
        model: String,
        settings: LmStudioSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry

        // Configure HTTP client with proxy (automatically skips proxy for localhost)
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .apiKey("lm-studio")
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .logResponses(log.isTraceEnabled)
                .timeout(Duration.ofMinutes(5))
                .httpClientBuilder(jdkHttpClientBuilder)
                .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.LMSTUDIO.name.lowercase())))
                .build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.LMSTUDIO,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            toolProvider = toolProvider,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    override fun createImageModel(
        settings: LmStudioSettings,
    ): ImageModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)

        return OpenAiImageModel.builder()
            .apiKey("lmstudio")
            .baseUrl(settings.baseUrl)
            .modelName(AppConfig.models.lmstudio.imageModel)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    private fun createSecondaryChatModel(
        settings: LmStudioSettings,
    ): ChatModel {
        // Configure HTTP client with proxy (automatically skips proxy for localhost)
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)

        return OpenAiChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("lm-studio")
            .modelName(AppContext.getInstance().params.model)
            .timeout(Duration.ofSeconds(AppConfig.models.lmstudio.utilityModelTimeoutSeconds))
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    override fun createUtilityClient(
        settings: LmStudioSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
