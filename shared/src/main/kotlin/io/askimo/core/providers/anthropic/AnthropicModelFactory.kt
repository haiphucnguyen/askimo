/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ProviderModelUtils.hallucinatedToolHandler
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools

class AnthropicModelFactory : ChatModelFactory<AnthropicSettings> {
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
        model: String,
        settings: AnthropicSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val chatModel =
            AnthropicStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .baseUrl(settings.baseUrl)
                .build()

        val builder =
            AiServices
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .apply {
                    if (chatMemory != null) {
                        chatMemory(chatMemory)
                    }
                    if (executionMode != ExecutionMode.DESKTOP) {
                        tools(LocalFsTools)
                    }
                }
                .hallucinatedToolNameStrategy(::hallucinatedToolHandler)
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

    private fun createSummarizerModel(settings: AnthropicSettings): AnthropicChatModel = AnthropicChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(settings.summarizerModel)
        .baseUrl(settings.baseUrl)
        .temperature(0.3)
        .build()
}
