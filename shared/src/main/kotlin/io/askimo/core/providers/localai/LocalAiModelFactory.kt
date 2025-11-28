/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.localai

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.localai.LocalAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.session.SessionMode
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.core.util.logger
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class LocalAiModelFactory : ChatModelFactory<LocalAiSettings> {
    private val log = logger<LocalAiModelFactory>()

    override fun availableModels(settings: LocalAiSettings): List<String> = try {
        // LocalAI doesn't have a direct API to list models like Ollama
        // Users typically need to configure models manually
        // Return an empty list or implement a custom API call if LocalAI supports it
        log.info("⚠️ LocalAI model listing not implemented. Please configure models manually.")
        emptyList()
    } catch (e: Exception) {
        log.info("⚠️ Failed to fetch models from LocalAI: ${e.message}")
        log.error("Failed to fetch models", e)
        emptyList()
    }

    override fun defaultSettings(): LocalAiSettings = LocalAiSettings(
        baseUrl = "http://localhost:8080", // default LocalAI endpoint
    )

    override fun create(
        model: String,
        settings: LocalAiSettings,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
        sessionMode: SessionMode,
    ): ChatService {
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
}
