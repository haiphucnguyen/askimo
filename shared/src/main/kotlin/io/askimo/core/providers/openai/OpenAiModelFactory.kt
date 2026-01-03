/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import java.time.Duration

class OpenAiModelFactory : ChatModelFactory<OpenAiSettings> {
    private val log = logger<OpenAiModelFactory>()

    companion object {
        private const val UTILITY_MODEL = "gpt-3.5-turbo"
        private const val UTILITY_MODEL_TIMEOUT_SECONDS = 45L
    }

    override fun availableModels(settings: OpenAiSettings): List<String> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()

        return fetchModels(
            apiKey = apiKey,
            url = "https://api.openai.com/v1/models",
            providerName = OPENAI,
        )
    }

    override fun defaultSettings(): OpenAiSettings = OpenAiSettings()

    override fun getNoModelsHelpText(): String = """
        One possible reason is that you haven't provided your OpenAI API key yet.

        1. Get your API key from: https://platform.openai.com/account/api-keys
        2. Then set it in the Settings

        Get an API key here: https://platform.openai.com/api-keys
    """.trimIndent()

    override fun create(
        sessionId: String?,
        model: String,
        settings: OpenAiSettings,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(settings.presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                }.build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = OPENAI,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            verbosity = settings.presets.verbosity,
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
        )
    }

    private fun createSecondaryChatModel(settings: OpenAiSettings): ChatModel = OpenAiChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(UTILITY_MODEL)
        .timeout(Duration.ofSeconds(UTILITY_MODEL_TIMEOUT_SECONDS))
        .build()

    override fun createUtilityClient(
        settings: OpenAiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()

    private fun supportsSampling(model: String): Boolean {
        val m = model.lowercase()
        return !(m.startsWith("o") || m.startsWith("gpt-5") || m.contains("reasoning"))
    }
}
