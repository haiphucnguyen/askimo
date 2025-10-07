/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.gemini

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools

class GeminiModelFactory : ChatModelFactory {
    override fun availableModels(settings: ProviderSettings): List<String> {
        val apiKey =
            (settings as? GeminiSettings)?.apiKey?.takeIf { it.isNotBlank() }
                ?: return emptyList()

        val baseUrl = (settings as? GeminiSettings)?.baseUrl ?: return emptyList()
        val url = "${baseUrl.trimEnd('/')}/models"

        return fetchModels(
            apiKey = apiKey,
            url = url,
            providerName = GEMINI,
        )
    }

    override fun defaultSettings(): ProviderSettings = GeminiSettings()

    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
    ): ChatService {
        require(settings is GeminiSettings) {
            "Invalid settings type for Gemini: ${settings::class.simpleName}"
        }

        val chatModel =
            GoogleAiGeminiStreamingChatModel
                .builder()
                .apiKey(settings.apiKey)
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
                .systemMessageProvider {
                    systemMessage(
                        """
                        You are a helpful assistant.

                        Tool-use rules:
                        • For general knowledge questions, answer directly. Do not mention tools.
                        • Use LocalFsTools **only** when the request clearly involves the local file system
                          (paths like ~, /, \\, or mentions such as "folder", "directory", "file", ".pdf", ".txt", etc.).
                        • Never refuse general questions by claiming you can only use tools.
                        • When using tools, call the most specific LocalFsTools function that matches the request.

                        Fallback policy:
                        • If the user asks about local resources but no matching tool is available (e.g. "delete pdf files"),
                          do not reject the request. Instead, provide safe, generic guidance on how they could do it
                          manually (for example, using the command line or a file manager).
                        """.trimIndent(),
                        verbosityInstruction(settings.presets.verbosity),
                    )
                }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor)
        }

        return builder.build()
    }

    private fun supportsSampling(model: String): Boolean = true
}
