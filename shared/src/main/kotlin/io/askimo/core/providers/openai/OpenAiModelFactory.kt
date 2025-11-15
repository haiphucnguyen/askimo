/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.session.SessionMode
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools

class OpenAiModelFactory : ChatModelFactory {
    override fun availableModels(settings: ProviderSettings): List<String> {
        val apiKey =
            (settings as? OpenAiSettings)?.apiKey?.takeIf { it.isNotBlank() }
                ?: return emptyList()

        val baseUrl = if (AppConfig.proxy.enabled && AppConfig.proxy.url.isNotBlank()) {
            "${AppConfig.proxy.url}/openai"
        } else {
            "https://api.openai.com"
        }

        return fetchModels(
            apiKey = apiKey,
            url = "$baseUrl/v1/models",
            providerName = OPENAI,
        )
    }

    override fun defaultSettings(): ProviderSettings = OpenAiSettings()

    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
        sessionMode: SessionMode,
    ): ChatService {
        require(settings is OpenAiSettings) {
            "Invalid settings type for OpenAI: ${settings::class.simpleName}"
        }

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(settings.presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                    // Add proxy configuration if enabled
                    if (AppConfig.proxy.enabled && AppConfig.proxy.url.isNotBlank()) {
                        baseUrl("${AppConfig.proxy.url}/openai/v1")
                        // Add proxy authentication header and OpenAI API key if tokens are provided
                        val headers = mutableMapOf<String, String>()
                        if (AppConfig.proxy.authToken.isNotBlank()) {
                            headers["X-Proxy-Auth"] = AppConfig.proxy.authToken
                        }
                        // Include OpenAI API key in Authorization header for proxy forwarding
                        headers["Authorization"] = "Bearer ${safeApiKey(settings.apiKey)}"
                        customHeaders(headers)
                    }
                }.build()

        val builder =
            AiServices
                .builder(ChatService::class.java)
                .streamingChatModel(chatModel)
                .chatMemory(memory)
                .apply {
                    // Only enable tools for non-DESKTOP modes
                    if (sessionMode != SessionMode.DESKTOP) {
                        tools(LocalFsTools)
                    }
                }
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
                }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor)
        }
        return builder.build()
    }

    private fun supportsSampling(model: String): Boolean {
        val m = model.lowercase()
        return !(m.startsWith("o") || m.startsWith("gpt-5") || m.contains("reasoning"))
    }
}
