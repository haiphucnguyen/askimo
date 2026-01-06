/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.Presets
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import java.time.Duration

class AnthropicModelFactory : ChatModelFactory<AnthropicSettings> {

    private val log = logger<AnthropicModelFactory>()

    companion object {
        private const val UTILITY_MODEL = "claude-3-haiku-20240307"
        private const val UTILITY_MODEL_TIMEOUT_SECONDS = 45L
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
        presets: Presets,
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

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.ANTHROPIC,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            verbosity = presets.verbosity,
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: AnthropicSettings): ChatModel = AnthropicChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(UTILITY_MODEL)
        .baseUrl(settings.baseUrl)
        .timeout(Duration.ofSeconds(UTILITY_MODEL_TIMEOUT_SECONDS))
        .build()

    override fun createUtilityClient(
        settings: AnthropicSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
