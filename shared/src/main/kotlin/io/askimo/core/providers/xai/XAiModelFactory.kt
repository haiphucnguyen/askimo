/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools

class XAiModelFactory : ChatModelFactory<XAiSettings> {
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
        model: String,
        settings: XAiSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
    ): ChatClient {
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

        // Note: Memory is NOT included in the delegate returned by factory.
        // ChatSessionService will create session-specific memory and wrap this delegate in ChatClientImpl.
        val builder =
            AiServices
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .apply {
                    // Only enable tools for non-DESKTOP modes
                    if (executionMode != ExecutionMode.DESKTOP) {
                        tools(LocalFsTools)
                    }
                }
                .hallucinatedToolNameStrategy(ProviderModelUtils::hallucinatedToolHandler)
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

    private fun supportsSampling(model: String): Boolean = true
}
