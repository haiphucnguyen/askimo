/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.lmstudio

import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderModelUtils.fetchModels
import io.askimo.core.providers.ProviderModelUtils.hallucinatedToolHandler
import io.askimo.core.providers.samplingFor
import io.askimo.core.providers.verbosityInstruction
import io.askimo.core.util.SystemPrompts.systemMessage
import io.askimo.tools.fs.LocalFsTools
import java.net.http.HttpClient
import java.time.Duration

class LmStudioModelFactory : ChatModelFactory<LmStudioSettings> {

    override fun availableModels(settings: LmStudioSettings): List<String> = fetchModels(
        apiKey = "lm-studio",
        url = "${settings.baseUrl}/models",
        providerName = ModelProvider.LMSTUDIO,
    )

    override fun defaultSettings(): LmStudioSettings = LmStudioSettings(
        baseUrl = "http://localhost:1234/v1",
    )

    override fun create(
        model: String,
        settings: LmStudioSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
        chatMemory: dev.langchain4j.memory.ChatMemory?,
    ): ChatClient {
        // LMStudio requires HTTP/1.1
        val httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        val jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder)

        val chatModel =
            OpenAiStreamingChatModel
                .builder()
                .baseUrl(settings.baseUrl)
                .apiKey("lm-studio")
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .httpClientBuilder(jdkHttpClientBuilder)
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
}
