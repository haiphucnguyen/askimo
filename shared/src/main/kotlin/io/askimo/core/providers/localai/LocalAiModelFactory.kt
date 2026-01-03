/* SPDX-License-Identifier: Apache-2.0
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
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.LOCALAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import java.time.Duration

class LocalAiModelFactory : ChatModelFactory<LocalAiSettings> {

    companion object {
        private const val UTILITY_MODEL_TIMEOUT_SECONDS = 45L
    }

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
        val s = samplingFor(settings.presets.style)
        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .apiKey("localai")
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .temperature(s.temperature)
                .topP(s.topP)
                .build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = LOCALAI,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            verbosity = settings.presets.verbosity,
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: LocalAiSettings): ChatModel = OpenAiChatModel.builder()
        .baseUrl(settings.baseUrl)
        .apiKey("localai")
        .modelName(AppContext.getInstance().params.model)
        .timeout(Duration.ofSeconds(UTILITY_MODEL_TIMEOUT_SECONDS))
        .build()

    override fun createUtilityClient(
        settings: LocalAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
