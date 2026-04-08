/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.lmstudio

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
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.LocalEmbeddingTokenLimits
import io.askimo.core.providers.ModelDTO
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.LMSTUDIO
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ensureLocalEmbeddingModelAvailable
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class LmStudioModelFactory : ChatModelFactory<LmStudioSettings> {

    private val log = logger<LmStudioModelFactory>()

    override fun getProvider(): ModelProvider = LMSTUDIO

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

    override fun availableModels(settings: LmStudioSettings): List<ModelDTO> = fetchModels(
        apiKey = "lm-studio",
        url = "${settings.baseUrl}/models",
        providerName = LMSTUDIO,
        httpVersion = HttpClient.Version.HTTP_1_1,
    ).map { ModelDTO.of(LMSTUDIO, it) }

    override fun defaultSettings(): LmStudioSettings = LmStudioSettings()

    override fun create(
        sessionId: String?,
        settings: LmStudioSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient = AiServiceBuilder.buildChatClient(
        sessionId = sessionId,
        settings = settings,
        provider = LMSTUDIO,
        chatModel = createStreamingModel(settings),
        secondaryChatModel = createSecondaryModel(settings),
        chatMemory = chatMemory,
        toolProvider = toolProvider,
        retriever = retriever,
        executionMode = executionMode,
    )

    override fun createImageModel(
        settings: LmStudioSettings,
    ): ImageModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)

        return OpenAiImageModel.builder()
            .apiKey("lmstudio")
            .baseUrl(settings.baseUrl)
            .modelName(AppConfig.models[LMSTUDIO].imageModel)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    override fun createStreamingModel(settings: LmStudioSettings): StreamingChatModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiStreamingChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("lm-studio")
            .modelName(settings.defaultModel)
            .temperature(AppConfig.chat.samplingTemperature)
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .timeout(Duration.ofMinutes(5))
            .httpClientBuilder(jdkHttpClientBuilder)
            .listeners(listOf(TelemetryChatModelListener(telemetry, LMSTUDIO.name.lowercase())))
            .build()
    }

    override fun createSecondaryModel(settings: LmStudioSettings): ChatModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)
        return OpenAiChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("lm-studio")
            .modelName(AppConfig.models[LMSTUDIO].utilityModel.ifBlank { AppContext.getInstance().params.model })
            .timeout(Duration.ofSeconds(AppConfig.models[LMSTUDIO].utilityModelTimeoutSeconds))
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    override fun createModel(settings: LmStudioSettings): ChatModel {
        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("lm-studio")
            .modelName(settings.defaultModel)
            .temperature(AppConfig.chat.samplingTemperature)
            .timeout(Duration.ofMinutes(5))
            .httpClientBuilder(jdkHttpClientBuilder)
            .listeners(listOf(TelemetryChatModelListener(telemetry, LMSTUDIO.name.lowercase())))
            .build()
    }

    override fun createUtilityClient(
        settings: LmStudioSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: LmStudioSettings): EmbeddingModel {
        val baseUrl = settings.baseUrl.removeSuffix("/")
        val modelName = AppConfig.models[LMSTUDIO].embeddingModel

        log.display(
            """
            ℹ️  Using LMStudio for embeddings
               • LMStudio URL: $baseUrl
               • Embedding model: $modelName
               • Configure in askimo.yml: models.lmstudio.embedding_model
            """.trimIndent(),
        )

        ensureLocalEmbeddingModelAvailable(LMSTUDIO, baseUrl, modelName)

        val jdkHttpClientBuilder = createHttpClientBuilder(settings.baseUrl)

        return OpenAiEmbeddingModelBuilder()
            .apiKey("not-needed")
            .baseUrl(baseUrl)
            .modelName(modelName)
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    override fun getEmbeddingTokenLimit(settings: LmStudioSettings): Int = LocalEmbeddingTokenLimits.resolve(AppConfig.models[LMSTUDIO].embeddingModel)
}
