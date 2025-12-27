/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatRequestTransformers
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ProcessBuilderExt
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class OllamaModelFactory : ChatModelFactory<OllamaSettings> {
    private val log = logger<OllamaModelFactory>()

    override fun availableModels(settings: OllamaSettings): List<String> = try {
        val process =
            ProcessBuilderExt("ollama", "list")
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
        log.displayError("⚠️ Failed to fetch models from Ollama: ${e.message}", e)
        emptyList()
    }

    override fun defaultSettings(): OllamaSettings = OllamaSettings(
        baseUrl = "http://localhost:11434", // default Ollama endpoint
    )

    override fun getNoModelsHelpText(): String = """
        You may not have any models installed yet.

        Visit https://ollama.com/library to browse available models.
        Then run: ollama pull <modelName> to install a model locally.

        Example: ollama pull llama3
    """.trimIndent()

    override fun create(
        sessionId: String?,
        model: String,
        settings: OllamaSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
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
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .apply {
                    // Only enable tools for non-DESKTOP modes
                    // Integrate chat memory if provided
                    if (chatMemory != null) {
                        chatMemory(chatMemory)
                    }
                    if (executionMode.isToolEnabled()) {
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
                }.chatRequestTransformer { chatRequest, memoryId ->
                    ChatRequestTransformers.addCustomSystemMessagesAndRemoveDuplicates(sessionId, chatRequest, memoryId)
                }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor)
        }
        return builder.build()
    }
}
