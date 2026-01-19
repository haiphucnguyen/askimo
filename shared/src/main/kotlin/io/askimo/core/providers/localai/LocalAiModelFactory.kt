/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.localai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.LOCALAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.telemetry.TelemetryChatModelListener
import java.time.Duration

class LocalAiModelFactory : ChatModelFactory<LocalAiSettings> {

    override fun getProvider(): ModelProvider = LOCALAI

    override fun availableModels(settings: LocalAiSettings): List<String> {
        val baseUrl = settings.baseUrl.takeIf { it.isNotBlank() } ?: return emptyList()

        return fetchModels(
            apiKey = "not-needed",
            url = "$baseUrl/models",
            providerName = LOCALAI,
        )
    }

    override fun defaultSettings(): LocalAiSettings = LocalAiSettings()

    override fun create(
        sessionId: String?,
        model: String,
        settings: LocalAiSettings,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry
        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .apiKey("localai")
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .listeners(listOf(TelemetryChatModelListener(telemetry, LOCALAI.name.lowercase()))).build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = LOCALAI,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: LocalAiSettings): ChatModel = OpenAiChatModel.builder()
        .baseUrl(settings.baseUrl)
        .apiKey("localai")
        .modelName(AppContext.getInstance().params.model)
        .timeout(Duration.ofSeconds(AppConfig.models.localai.utilityModelTimeoutSeconds))
        .build()

    override fun createUtilityClient(
        settings: LocalAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
