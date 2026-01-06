/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import java.time.Duration

class XAiModelFactory : ChatModelFactory<XAiSettings> {
    private val log = logger<XAiModelFactory>()

    companion object {
        private const val UTILITY_MODEL_TIMEOUT_SECONDS = 45L
    }

    override fun availableModels(settings: XAiSettings): List<String> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "${settings.baseUrl.trimEnd('/')}/models"

        return fetchModels(
            apiKey = apiKey,
            url = url,
            providerName = XAI,
        )
    }

    override fun defaultSettings(): XAiSettings = XAiSettings()

    override fun create(
        sessionId: String?,
        model: String,
        settings: XAiSettings,
        presets: Presets,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                }.build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = XAI,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: XAiSettings): ChatModel = OpenAiChatModel.builder()
        .baseUrl(settings.baseUrl)
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppContext.getInstance().params.model)
        .timeout(Duration.ofSeconds(UTILITY_MODEL_TIMEOUT_SECONDS))
        .build()

    override fun createUtilityClient(
        settings: XAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()

    private fun supportsSampling(model: String): Boolean = true
}
