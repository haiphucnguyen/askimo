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
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.Presets
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import java.time.Duration

class AnthropicModelFactory : ChatModelFactory<AnthropicSettings> {

    private val log = logger<AnthropicModelFactory>()

    override fun availableModels(settings: AnthropicSettings): List<String> = AppConfig.models.anthropic.availableModels

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
        val telemetry = AppContext.getInstance().telemetry

        val chatModel =
            AnthropicStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .listeners(listOf(TelemetryChatModelListener(telemetry, ModelProvider.ANTHROPIC.name.lowercase())))
                .baseUrl(settings.baseUrl)
                .build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = ModelProvider.ANTHROPIC,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: AnthropicSettings): ChatModel = AnthropicChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppConfig.models.anthropic.utilityModel)
        .baseUrl(settings.baseUrl)
        .timeout(Duration.ofSeconds(AppConfig.models.anthropic.utilityModelTimeoutSeconds))
        .build()

    override fun createUtilityClient(
        settings: AnthropicSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
