/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatRequestTransformers
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class OllamaModelFactory : ChatModelFactory<OllamaSettings> {
    private val log = logger<OllamaModelFactory>()

    override fun availableModels(settings: OllamaSettings): List<String> {
        val baseUrl = settings.baseUrl.takeIf { it.isNotBlank() } ?: return emptyList()

        return fetchModels(
            apiKey = "not-needed",
            url = "$baseUrl/models",
            providerName = ModelProvider.OLLAMA,
        )
    }

    override fun defaultSettings(): OllamaSettings = OllamaSettings()

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
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .logger(log)
                .logRequests(log.isDebugEnabled)
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
                    ChatRequestTransformers.addCustomSystemMessagesAndRemoveDuplicates(
                        sessionId,
                        chatRequest,
                        memoryId,
                        ModelProvider.OLLAMA,
                        model,
                    )
                }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor).storeRetrievedContentInChatMemory(false)
        }
        return builder.build()
    }

    override fun createUtilityClient(
        settings: OllamaSettings,
        fallbackModel: String,
    ): ChatClient {
        // Simple client for classification - no tools, no transformers, no custom messages
        val chatModel = OpenAiChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("ollama")
            .modelName(fallbackModel)
            .timeout(Duration.ofSeconds(10))
            .logger(log)
            .logRequests(log.isDebugEnabled)
            .build()

        return AiServices.builder(ChatClient::class.java)
            .chatModel(chatModel)
            .build()
    }
}
