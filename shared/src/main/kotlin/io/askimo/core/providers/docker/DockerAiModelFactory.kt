/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.docker

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
import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.LocalEmbeddingTokenLimits
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ensureLocalEmbeddingModelAvailable
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ProcessBuilderExt
import io.askimo.core.util.ProxyUtil
import java.net.http.HttpClient
import java.time.Duration

class DockerAiModelFactory : ChatModelFactory<DockerAiSettings> {
    private val log = logger<DockerAiModelFactory>()

    override fun getProvider(): ModelProvider = ModelProvider.DOCKER

    override fun availableModels(settings: DockerAiSettings): List<String> = try {
        val process =
            ProcessBuilderExt("docker", "model", "ls", "--openai")
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        // Parse JSON response from Docker AI
        // Expected format: { "object": "list", "data": [{ "id": "ai/gpt-oss:latest", ... }] }
        val modelIds = mutableListOf<String>()

        // Simple JSON parsing to extract "id" fields from "data" array
        val idPattern = """"id"\s*:\s*"([^"]+)"""".toRegex()
        idPattern.findAll(output).forEach { matchResult ->
            val modelId = matchResult.groupValues[1]
            if (modelId.isNotBlank()) {
                modelIds.add(modelId)
            }
        }

        modelIds.distinct()
    } catch (e: Exception) {
        log.displayError("⚠️ Failed to fetch models from Docker AI: ${e.message}", e)
        emptyList()
    }

    override fun defaultSettings(): DockerAiSettings = DockerAiSettings()

    override fun getNoModelsHelpText(): String = """
        You may not have any models installed yet.

        Make sure Docker AI is running and has models available.
        Visit Docker AI documentation for model installation instructions.
    """.trimIndent()

    override fun create(
        sessionId: String?,
        model: String,
        settings: DockerAiSettings,
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
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .logResponses(log.isTraceEnabled)
                .timeout(Duration.ofMinutes(5))
                .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.DOCKER.name.lowercase())))
                .build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.DOCKER,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            toolProvider = toolProvider,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    override fun createImageModel(
        settings: DockerAiSettings,
    ): ImageModel = OpenAiImageModel.builder()
        .apiKey("dockerai")
        .baseUrl(settings.baseUrl)
        .modelName(AppConfig.models.docker.imageModel)
        .logger(log)
        .logRequests(log.isDebugEnabled)
        .logResponses(log.isTraceEnabled)
        .build()

    private fun createSecondaryChatModel(settings: DockerAiSettings): ChatModel {
        val httpClientBuilder = ProxyUtil.configureProxy(HttpClient.newBuilder(), settings.baseUrl)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

        return OpenAiChatModel.builder()
            .httpClientBuilder(jdkHttpClientBuilder)
            .baseUrl(settings.baseUrl)
            .apiKey("docker-ai")
            .modelName(AppContext.getInstance().params.model)
            .timeout(Duration.ofSeconds(AppConfig.models.docker.utilityModelTimeoutSeconds))
            .build()
    }

    override fun createUtilityClient(
        settings: DockerAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()

    override fun supportsEmbedding(): Boolean = true

    override fun createEmbeddingModel(settings: DockerAiSettings): EmbeddingModel {
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

        ensureLocalEmbeddingModelAvailable(ModelProvider.DOCKER, baseUrl, modelName)

        return OpenAiEmbeddingModelBuilder()
            .apiKey("not-needed")
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build()
    }

    override fun getEmbeddingTokenLimit(settings: DockerAiSettings): Int = LocalEmbeddingTokenLimits.resolve(AppConfig.models.docker.embeddingModel)
}
