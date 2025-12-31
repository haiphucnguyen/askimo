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
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatRequestTransformers
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ProviderModelUtils.hallucinatedToolHandler
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.rag.MetadataAwareContentInjector
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
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
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
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
                .apply {
                    val s = samplingFor(settings.presets.style)
                    temperature(s.temperature)
                    topP(s.topP)
                }.build()

        val builder =
            AiServices
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .apply {
                    if (chatMemory != null) {
                        chatMemory(chatMemory)
                    }
                    if (executionMode.isToolEnabled()) {
                        tools(LocalFsTools)
                    }
                }
                .hallucinatedToolNameStrategy(::hallucinatedToolHandler)
                .systemMessageProvider {
                    systemMessage(
                        """
                        Tool response format:
                        • All tools return: { "success": boolean, "output": string, "error": string, "metadata": object }
                        • success=true: Tool executed successfully, check "output" for results and "metadata" for structured data
                        • success=false: Tool failed, check "error" for reason
                        • Always check the "success" field before using "output"
                        • If success=false, inform the user about the error from the "error" field
                        • When success=true, extract data from "metadata" field for detailed information

                        Tool execution guidelines:
                        • Parse the tool response JSON before responding to user
                        • If success=true: Use the output and metadata to answer user's question
                        • If success=false: Explain what went wrong using the error message
                        • Never assume tool success without checking the response
                        """.trimIndent(),
                        verbosityInstruction(settings.presets.verbosity),
                    )
                }.chatRequestTransformer { chatRequest, memoryId ->
                    ChatRequestTransformers.addCustomSystemMessagesAndRemoveDuplicates(
                        sessionId,
                        chatRequest,
                        memoryId,
                        ModelProvider.LMSTUDIO,
                        model,
                    )
                }
        if (retriever != null) {
            val retrievalAugmentor = DefaultRetrievalAugmentor
                .builder()
                .queryTransformer(CompressingQueryTransformer(createSecondaryChatModel(settings)))
                .contentRetriever(retriever)
                .contentInjector(
                    MetadataAwareContentInjector(
                        useAbsolutePaths = AppConfig.rag.useAbsolutePathInCitations,
                    ),
                ).build()
            builder.retrievalAugmentor(retrievalAugmentor)
                .storeRetrievedContentInChatMemory(false)
        }

        return builder.build()
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
            .timeout(Duration.ofSeconds(10))
            .httpClientBuilder(jdkHttpClientBuilder)
            .build()
    }

    override fun createUtilityClient(
        settings: LmStudioSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
