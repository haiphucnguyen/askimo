/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.providers.docker

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
import io.askimo.core.providers.ModelProvider.DOCKER
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ensureLocalEmbeddingModelAvailable
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class DockerAiModelFactory : ChatModelFactory<DockerAiSettings> {
    private val log = logger<DockerAiModelFactory>()

    override fun getProvider(): ModelProvider = DOCKER

    override fun availableModels(settings: DockerAiSettings): List<ModelDTO> = fetchModels(
        apiKey = "docker-ai",
        url = "${settings.baseUrl}/models",
        providerName = DOCKER,
        httpVersion = HttpClient.Version.HTTP_1_1,
    ).map { ModelDTO.of(DOCKER, it) }

    override fun defaultSettings(): DockerAiSettings = DockerAiSettings()

    override fun getNoModelsHelpText(): String = """
        You may not have any models installed yet.

        Make sure Docker AI is running and has models available.
        Visit Docker AI documentation for model installation instructions.
    """.trimIndent()

    override fun create(
        sessionId: String?,
        settings: DockerAiSettings,
        toolProvider: ToolProvider?,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient = AiServiceBuilder.buildChatClient(
        sessionId = sessionId,
        settings = settings,
        provider = DOCKER,
        chatModel = createStreamingModel(settings),
        secondaryChatModel = createSecondaryModel(settings),
        chatMemory = chatMemory,
        toolProvider = toolProvider,
        retriever = retriever,
        executionMode = executionMode,
    )

    override fun createImageModel(
        settings: DockerAiSettings,
    ): ImageModel = OpenAiImageModel.builder()
        .apiKey("docker-ai")
        .baseUrl(settings.baseUrl)
        .modelName(AppConfig.models[DOCKER].imageModel)
        .logger(log)
        .logRequests(log.isDebugEnabled)
        .logResponses(log.isTraceEnabled)
        .build()

    override fun createStreamingModel(settings: DockerAiSettings): StreamingChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .modelName(settings.defaultModel)
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.defaultModelTimeoutSeconds))
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .logResponses(log.isTraceEnabled)
            .listeners(listOf(TelemetryChatModelListener(telemetry, DOCKER.name.lowercase())))
            .build()
    }

    override fun createSecondaryModel(settings: DockerAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey("docker-ai")
            .modelName(AppConfig.models[DOCKER].utilityModel.ifBlank { AppContext.getInstance().params.model })
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.utilityModelTimeoutSeconds))
            .build()
    }

    override fun createModel(settings: DockerAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)
        val telemetry = AppContext.getInstance().telemetry

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey("docker-ai")
            .modelName(settings.defaultModel)
            .timeout(Duration.ofSeconds(AppConfig.models.timeouts.defaultModelTimeoutSeconds))
            .listeners(listOf(TelemetryChatModelListener(telemetry, DOCKER.name.lowercase())))
            .build()
    }

    override fun createUtilityClient(
        settings: DockerAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: DockerAiSettings): EmbeddingModel {
        val baseUrl = settings.baseUrl.removeSuffix("/")
        val modelName = AppConfig.models[DOCKER].embeddingModel

        log.display(
            """
            ℹ️  Using Docker AI for embeddings
               • Docker AI URL: $baseUrl
               • Embedding model: $modelName
               • Configure in askimo.yml: models.docker.embedding_model
            """.trimIndent(),
        )

        ensureLocalEmbeddingModelAvailable(DOCKER, baseUrl, modelName)

        return OpenAiEmbeddingModelBuilder()
            .apiKey("not-needed")
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build()
    }

    override fun getEmbeddingTokenLimit(settings: DockerAiSettings): Int = LocalEmbeddingTokenLimits.resolve(AppConfig.models[DOCKER].embeddingModel)
}
