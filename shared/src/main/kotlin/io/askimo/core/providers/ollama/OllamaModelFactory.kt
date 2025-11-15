/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class OllamaModelFactory : ChatModelFactory {
    override fun availableModels(settings: ProviderSettings): List<String> = try {
        val process =
            ProcessBuilder("ollama", "list")
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        // Parse lines like:
        // llama2 7B   4.3 GB
        // mistral 7B 4.1 GB
        output
            .lines()
            .drop(1) // skip header
            .mapNotNull { line ->
                line.trim().split("\\s+".toRegex()).firstOrNull()
            }.filter { it.isNotBlank() }
            .distinct()
    } catch (e: Exception) {
        info("⚠️ Failed to fetch models from Ollama: ${e.message}")
        debug(e)
        emptyList()
    }

    override fun defaultSettings(): ProviderSettings = OllamaSettings(
        baseUrl = "http://localhost:11434", // default Ollama endpoint
    )

    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
    ): ChatService {
        require(settings is OllamaSettings) {
            "Invalid settings type for Ollama: ${settings::class.simpleName}"
        }

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl("${settings.baseUrl}/v1")
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
                .tools(LocalFsTools)
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
