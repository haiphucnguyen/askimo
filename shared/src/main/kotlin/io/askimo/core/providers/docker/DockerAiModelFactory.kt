/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.docker

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatRequestTransformers
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ProcessBuilderExt
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.time.Duration

class DockerAiModelFactory : ChatModelFactory<DockerAiSettings> {
    private val log = logger<DockerAiModelFactory>()

    override fun availableModels(settings: DockerAiSettings): List<String> = try {
        val process =
            ProcessBuilderExt("docker", "model", "ls", "--openai")
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        // Parse JSON response from Docker AI
        // Expected format: { "object": "list", "data": [{ "id": "ai/gpt-oss:latest", ... }] }
        val modelIds = mutableListOf<String>()

        // Simple JSON parsing to extract "id" fields from "data" array
        val idPattern = """"id"\s*:\s*"([^"]+)"""".toRegex()
        idPattern.findAll(output).forEach { matchResult ->
            val modelId = matchResult.groupValues[1]
            if (modelId.isNotBlank()) {
                modelIds.add(modelId)
            }
        }

        modelIds.distinct()
    } catch (e: Exception) {
        log.displayError("⚠️ Failed to fetch models from Docker AI: ${e.message}", e)
        emptyList()
    }

    override fun defaultSettings(): DockerAiSettings = DockerAiSettings()

    override fun getNoModelsHelpText(): String = """
        You may not have any models installed yet.

        Make sure Docker AI is running and has models available.
        Visit Docker AI documentation for model installation instructions.
    """.trimIndent()

    override fun create(
        sessionId: String?,
        model: String,
        settings: DockerAiSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
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
                        ModelProvider.DOCKER,
                        model,
                    )
                }
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor).storeRetrievedContentInChatMemory(false)
        }
        return builder.build()
    }

    override fun createUtilityClient(
        settings: DockerAiSettings,
        fallbackModel: String,
    ): ChatClient {
        // Simple client for classification - no tools, no transformers, no custom messages
        val chatModel = OpenAiChatModel.builder()
            .baseUrl(settings.baseUrl)
            .apiKey("docker-ai")
            .modelName(fallbackModel)
            .timeout(Duration.ofSeconds(10))
            .build()

        return AiServices.builder(ChatClient::class.java)
            .chatModel(chatModel)
            .build()
    }
}
