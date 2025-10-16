/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider.X_AI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools

class XAiModelFactory : ChatModelFactory {
    override fun availableModels(settings: ProviderSettings): List<String> {
        val apiKey =
            (settings as? XAiSettings)?.apiKey?.takeIf { it.isNotBlank() }
                ?: return emptyList()

        val baseUrl = (settings as? XAiSettings)?.baseUrl ?: return emptyList()
        val url = "${baseUrl.trimEnd('/')}/models"

        return fetchModels(
            apiKey = apiKey,
            url = url,
            providerName = X_AI,
        )
    }

    override fun defaultSettings(): ProviderSettings = XAiSettings()

    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
    ): ChatService {
        require(settings is XAiSettings) {
            "Invalid settings type for X_AI: ${settings::class.simpleName}"
        }

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(settings.presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                }.build()

        val builder =
            AiServices
                .builder(ChatService::class.java)
                .streamingChatModel(chatModel)
                .chatMemory(memory)
                .tools(LocalFsTools())
                .systemMessageProvider { systemMessage(verbosityInstruction(settings.presets.verbosity)) }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor)
        }
        return builder.build()
    }

    private fun supportsSampling(model: String): Boolean = true
}
