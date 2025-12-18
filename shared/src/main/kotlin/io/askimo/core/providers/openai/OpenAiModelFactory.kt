/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.config.AppConfig
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.logger
import io.askimo.core.memory.ConversationSummary
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ProviderModelUtils
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.ApiKeyUtils.safeApiKey
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import kotlinx.serialization.json.Json

class OpenAiModelFactory : ChatModelFactory<OpenAiSettings> {
    private val log = logger<OpenAiModelFactory>()

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    override fun availableModels(settings: OpenAiSettings): List<String> {
        val apiKey = settings.apiKey.takeIf { it.isNotBlank() } ?: return emptyList()

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

    override fun defaultSettings(): OpenAiSettings = OpenAiSettings()

    override fun getNoModelsHelpText(): String = """
        One possible reason is that you haven't provided your OpenAI API key yet.

        1. Get your API key from: https://platform.openai.com/account/api-keys
        2. Then set it in the Settings

        Get an API key here: https://platform.openai.com/api-keys
    """.trimIndent()

    override fun create(
        model: String,
        settings: OpenAiSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory?,
    ): ChatClient {
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
                    configureProxy(settings.apiKey)
                }.build()

        val builder =
            AiServices
                .builder(ChatClient::class.java)
                .streamingChatModel(chatModel)
                .apply {
                    if (executionMode != ExecutionMode.DESKTOP) {
                        tools(LocalFsTools)
                    }
                    if (chatMemory != null) {
                        chatMemory(chatMemory)
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

    override fun createSummarizer(settings: OpenAiSettings): ((String) -> ConversationSummary)? {
        if (!settings.enableAiSummarization) {
            return null
        }

        val summarizerModel = createSummarizerModel(settings)

        return { conversationText ->
            val prompt = """
                Analyze the following conversation and provide a structured summary in JSON format.
                Extract key facts, main topics, and recent context.

                Conversation:
                $conversationText

                Respond with JSON only (no markdown formatting):
                {
                    "keyFacts": {"fact_name": "fact_value", ...},
                    "mainTopics": ["topic1", "topic2", ...],
                    "recentContext": "brief summary of the most recent discussion"
                }
            """.trimIndent()

            try {
                val response = summarizerModel.chat(UserMessage.from(prompt))
                val jsonText = response.aiMessage().text()
                    .removePrefix("```json").removeSuffix("```").trim()

                json.decodeFromString<ConversationSummary>(jsonText)
            } catch (e: Exception) {
                log.error("Failed to generate conversation summary with OpenAI summarizer", e)
                ConversationSummary(
                    keyFacts = emptyMap(),
                    mainTopics = emptyList(),
                    recentContext = conversationText.takeLast(500),
                )
            }
        }
    }

    private fun supportsSampling(model: String): Boolean {
        val m = model.lowercase()
        return !(m.startsWith("o") || m.startsWith("gpt-5") || m.contains("reasoning"))
    }

    /**
     * Build proxy headers for OpenAI requests
     */
    private fun buildProxyHeaders(apiKey: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (AppConfig.proxy.authToken.isNotBlank()) {
            headers["X-Proxy-Auth"] = AppConfig.proxy.authToken
        }
        headers["Authorization"] = "Bearer ${safeApiKey(apiKey)}"
        return headers
    }

    /**
     * Configure proxy settings for OpenAI streaming chat model builder.
     */
    private fun OpenAiStreamingChatModelBuilder.configureProxy(
        apiKey: String,
    ): OpenAiStreamingChatModelBuilder {
        if (AppConfig.proxy.enabled && AppConfig.proxy.url.isNotBlank()) {
            baseUrl("${AppConfig.proxy.url}/openai/v1")
            customHeaders(buildProxyHeaders(apiKey))
        }
        return this
    }

    /**
     * Configure proxy settings for OpenAI chat model builder.
     */
    private fun OpenAiChatModelBuilder.configureProxy(
        apiKey: String,
    ): OpenAiChatModelBuilder {
        if (AppConfig.proxy.enabled && AppConfig.proxy.url.isNotBlank()) {
            baseUrl("${AppConfig.proxy.url}/openai/v1")
            customHeaders(buildProxyHeaders(apiKey))
        }
        return this
    }

    /**
     * Create a non-streaming chat model for background summarization tasks.
     * Uses a cheaper model (default: gpt-4o-mini) to reduce costs.
     */
    private fun createSummarizerModel(settings: OpenAiSettings): OpenAiChatModel = OpenAiChatModel.builder()
        .apiKey(safeApiKey(settings.apiKey))
        .modelName(settings.summarizerModel)
        .temperature(0.3)
        .apply {
            configureProxy(settings.apiKey)
        }.build()
}
