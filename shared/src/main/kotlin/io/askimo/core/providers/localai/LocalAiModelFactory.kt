/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.localai

import dev.langchain4j.model.localai.LocalAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatClientImpl
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.LOCALAI
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class LocalAiModelFactory : ChatModelFactory<LocalAiSettings> {
    private val log = logger<LocalAiModelFactory>()

    override fun availableModels(settings: LocalAiSettings): List<String> {
        val baseUrl = settings.baseUrl.takeIf { it.isNotBlank() } ?: return emptyList()

        return fetchModels(
            apiKey = "not-needed",
            url = "$baseUrl/v1/models",
            providerName = LOCALAI,
        )
    }

    override fun defaultSettings(): LocalAiSettings = LocalAiSettings(
        baseUrl = "http://localhost:8080",
    )

    override fun create(
        model: String,
        settings: LocalAiSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
    ): ChatClient {
        val chatModel =
            LocalAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .apply {
                    val s = samplingFor(settings.presets.style)
                    temperature(s.temperature)
                    topP(s.topP)
                }.build()

        // Create token-aware summarizing memory
        val chatMemory = TokenAwareSummarizingMemory.builder()
            .maxTokens(8000)
            .summarizationThreshold(0.75)
            .build()

        val builder =
            AiServices
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .chatMemory(chatMemory)
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
                        You are a helpful AI assistant. Follow these rules strictly:

                        Response format:
                        • Respond directly with your answer - no prefixes, no meta-commentary
                        • Do NOT include: "analysis", "User asks:", "Need to answer", "assistant", "final", or any reasoning steps
                        • Do NOT repeat the user's question
                        • Start your response with the actual answer immediately

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

        return ChatClientImpl(builder.build(), chatMemory)
    }
}
