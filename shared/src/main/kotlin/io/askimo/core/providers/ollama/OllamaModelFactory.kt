/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

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
import io.askimo.core.providers.LocalEmbeddingTokenLimits
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ensureLocalEmbeddingModelAvailable
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class OllamaModelFactory : ChatModelFactory<OllamaSettings> {
    private val log = logger<OllamaModelFactory>()

    override fun getProvider(): ModelProvider = ModelProvider.OLLAMA

    override fun availableModels(settings: OllamaSettings): List<String> {
        val baseUrl = settings.baseUrl.takeIf { it.isNotBlank() } ?: return emptyList()

        return fetchModels(
            apiKey = "not-needed",
            url = "$baseUrl/models",
            providerName = ModelProvider.OLLAMA,
        )
    }

    override fun defaultSettings(): OllamaSettings = OllamaSettings()

    override fun getNoModelsHelpText(): String = """
        You may not have any models installed yet.

        Visit https://ollama.com/library to browse available models.
        Then run: ollama pull <modelName> to install a model locally.

        Example: ollama pull llama3
    """.trimIndent()

    override fun create(
        sessionId: String?,
        model: String,
        settings: OllamaSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry

        // Configure HTTP client with proxy (automatically skips proxy for localhost)
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .httpClientBuilder(jdkHttpClientBuilder)
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .logResponses(log.isTraceEnabled)
                .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.OLLAMA.name.lowercase()))).build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.OLLAMA,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            toolProvider = toolProvider,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    override fun createImageModel(
        settings: OllamaSettings,
    ): ImageModel = OpenAiImageModel.builder()
        .apiKey("ollama")
        .baseUrl(settings.baseUrl)
        .modelName(AppConfig.models.ollama.imageModel)
        .logger(log)
        .logRequests(log.isDebugEnabled)
        .logResponses(log.isTraceEnabled)
        .build()

    private fun createSecondaryChatModel(settings: OllamaSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey("ollama")
            .modelName(AppContext.getInstance().params.model)
            .timeout(Duration.ofSeconds(AppConfig.models.ollama.utilityModelTimeoutSeconds))
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .build()
    }

    override fun createUtilityClient(
        settings: OllamaSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: OllamaSettings): EmbeddingModel {
        val baseUrl = settings.baseUrl.removeSuffix("/")
        val modelName = AppConfig.models.ollama.embeddingModel
        ensureLocalEmbeddingModelAvailable(ModelProvider.OLLAMA, baseUrl, modelName)
        return OpenAiEmbeddingModelBuilder()
            .apiKey("not-needed")
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build()
    }

    override fun getEmbeddingTokenLimit(settings: OllamaSettings): Int = LocalEmbeddingTokenLimits.resolve(AppConfig.models.ollama.embeddingModel)
}
