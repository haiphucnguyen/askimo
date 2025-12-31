/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatRequestTransformers
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.hallucinatedToolHandler
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.rag.MetadataAwareContentInjector
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class AnthropicModelFactory : ChatModelFactory<AnthropicSettings> {

    private val log = logger<AnthropicModelFactory>()

    companion object {
        private const val UTILITY_MODEL = "claude-3-haiku-20240307"
    }

    override fun availableModels(settings: AnthropicSettings): List<String> = listOf(
        "claude-opus-4-1",
        "claude-opus-4-0",
        "claude-sonnet-4-5",
        "claude-sonnet-4-0",
        "claude-3-7-sonnet-latest",
        "claude-3-5-haiku-latest",
    )

    override fun defaultSettings(): AnthropicSettings = AnthropicSettings()

    override fun create(
        sessionId: String?,
        model: String,
        settings: AnthropicSettings,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val chatModel =
            AnthropicStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .baseUrl(settings.baseUrl)
                .build()

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
                        ModelProvider.ANTHROPIC,
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

    private fun createSecondaryChatModel(settings: AnthropicSettings): ChatModel = AnthropicChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(UTILITY_MODEL)
        .baseUrl(settings.baseUrl)
        .timeout(Duration.ofSeconds(10))
        .build()

    override fun createUtilityClient(
        settings: AnthropicSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
