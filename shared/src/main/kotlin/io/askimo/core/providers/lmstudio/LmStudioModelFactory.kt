/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.lmstudio

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.telemetry.TelemetryChatModelListener
import java.net.http.HttpClient
import java.time.Duration

class LmStudioModelFactory : ChatModelFactory<LmStudioSettings> {

    private val log = logger<LmStudioModelFactory>()

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
        presets: Presets,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry

        // LMStudio requires HTTP/1.1
        val httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .apiKey("lm-studio")
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .timeout(Duration.ofMinutes(5))
                .httpClientBuilder(jdkHttpClientBuilder)
                .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.LMSTUDIO.name.lowercase())))
                .apply {
                    val s = samplingFor(presets.style)
                    temperature(s.temperature)
                    topP(s.topP)
                }.build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.LMSTUDIO,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(
        settings: LmStudioSettings,
    ): ChatModel {
        // LMStudio requires HTTP/1.1
        val httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

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
