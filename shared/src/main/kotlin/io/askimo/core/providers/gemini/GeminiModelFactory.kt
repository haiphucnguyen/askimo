/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.gemini

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.providers.AiServiceBuilder
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.telemetry.TelemetryChatModelListener
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import java.time.Duration

class GeminiModelFactory : ChatModelFactory<GeminiSettings> {

    private val log = logger<GeminiModelFactory>()

    override fun availableModels(settings: GeminiSettings): List<String> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()

        val baseUrl = settings.baseUrl
        val url = "${baseUrl.trimEnd('/')}/models"

        return fetchModels(
            apiKey = apiKey,
            url = url,
            providerName = GEMINI,
        ).map { it.removePrefix("models/") }
    }

    override fun defaultSettings(): GeminiSettings = GeminiSettings()

    override fun create(
        sessionId: String?,
        model: String,
        settings: GeminiSettings,
        presets: Presets,
        retriever: ContentRetriever?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
        val telemetry = AppContext.getInstance().telemetry

        val chatModel =
            GoogleAiGeminiStreamingChatModel
                .builder()
                .apiKey(safeApiKey(settings.apiKey))
                .modelName(model)
                .logger(log)
                .logRequests(log.isDebugEnabled)
                .listeners(listOf(TelemetryChatModelListener(telemetry, GEMINI.name.lowercase())))
                .apply {
                    if (supportsSampling(model)) {
                        val s = samplingFor(presets.style)
                        temperature(s.temperature).topP(s.topP)
                    }
                }.build()

        return AiServiceBuilder.buildChatClient(
            sessionId = sessionId,
            model = model,
            provider = GEMINI,
            chatModel = chatModel,
            secondaryChatModel = createSecondaryChatModel(settings),
            chatMemory = chatMemory,
            retriever = retriever,
            executionMode = executionMode,
            toolInstructions = geminiToolInstructions(),
        )
    }

    private fun geminiToolInstructions(): String = """
        You are a helpful assistant.

        Tool-use rules:
        • For general knowledge questions, answer directly. Do not mention tools.
        • Use LocalFsTools **only** when the request clearly involves the local file system
          (paths like ~, /, \\, or mentions such as "folder", "directory", "file", ".pdf", ".txt", etc.).
        • Never refuse general questions by claiming you can only use tools.
        • When using tools, call the most specific LocalFsTools function that matches the request.

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

        Fallback policy:
        • If the user asks about local resources but no matching tool is available (e.g. "delete pdf files"),
          do not reject the request. Instead, provide safe, generic guidance on how they could do it
          manually (for example, using the command line or a file manager).
    """.trimIndent()

    private fun supportsSampling(model: String): Boolean = true

    private fun createSecondaryChatModel(settings: GeminiSettings): ChatModel = GoogleAiGeminiChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(AppConfig.models.gemini.utilityModel)
        .timeout(Duration.ofSeconds(AppConfig.models.gemini.utilityModelTimeoutSeconds))
        .build()

    override fun createUtilityClient(
        settings: GeminiSettings,
    ): ChatClient = AiServices.builder(ChatClient::class.java)
        .chatModel(createSecondaryChatModel(settings))
        .build()
}
