/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.rag.content.Content
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.tool.ToolExecution
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import java.util.function.Consumer

/**
 * A no-operation implementation of the ChatService interface.
 *
 * This implementation is used as a fallback when no chat model is configured.
 */
object NoopChatClient : ChatClient {
    var appContext: AppContext? = null

    private val CLI_NO_PROVIDER_MESSAGE = """
        ⚠️  No AI provider configured yet!

        To start chatting, please configure a provider first:
        1. Check available providers: :providers
        2. Set a provider: :set-provider <provider_name>

        After setting a provider, you'll need to configure your API key or model settings.
    """.trimIndent()

    private val CLI_NO_MODEL_MESSAGE = """
        ⚠️  No model selected!

        You have a provider configured, but no model is selected yet.

        To start chatting:
        1. Check available models: :models
        2. Set a model: :set-param model <model_name>

        Make sure your API key and provider settings are configured correctly.
    """.trimIndent()

    private val DESKTOP_NO_PROVIDER_MESSAGE = """
        ⚠️  No AI provider configured yet!

        To start chatting, please configure a provider first:

        1. Open Settings from the menu or toolbar
        2. Navigate to the Providers section
        3. Select a provider and configure your API key
        4. Choose a model from the available options

        After configuration, you can start chatting with the AI assistant.
    """.trimIndent()

    private val DESKTOP_NO_MODEL_MESSAGE = """
        ⚠️  No model selected!

        You have a provider configured, but no model is selected yet.

        To start chatting:
        1. Open Settings from the menu or toolbar
        2. Navigate to the Models section
        3. Select a model from the available options

        Make sure your API key and provider settings are configured correctly.
    """.trimIndent()

    private fun getConfigurationMessage(): String {
        val currentProvider = appContext?.getActiveProvider() ?: ModelProvider.UNKNOWN
        val isDesktop = appContext?.mode == ExecutionMode.DESKTOP

        return if (currentProvider == ModelProvider.UNKNOWN) {
            if (isDesktop) DESKTOP_NO_PROVIDER_MESSAGE else CLI_NO_PROVIDER_MESSAGE
        } else {
            if (isDesktop) DESKTOP_NO_MODEL_MESSAGE else CLI_NO_MODEL_MESSAGE
        }
    }

    override fun sendMessageStreaming(prompt: String): TokenStream {
        return object : TokenStream {
            private var partialConsumer: Consumer<String>? = null
            private var completeConsumer: Consumer<ChatResponse>? = null

            override fun onPartialResponse(consumer: Consumer<String>): TokenStream {
                partialConsumer = consumer
                return this
            }

            override fun onCompleteResponse(completeResponseHandler: Consumer<ChatResponse>): TokenStream {
                completeConsumer = completeResponseHandler
                return this
            }

            override fun onError(consumer: Consumer<Throwable>): TokenStream = this

            override fun onToolExecuted(toolExecuteHandler: Consumer<ToolExecution>): TokenStream = this

            override fun ignoreErrors(): TokenStream = this

            override fun onRetrieved(contentHandler: Consumer<List<Content?>?>?): TokenStream = this

            override fun start() {
                val message = getConfigurationMessage()
                partialConsumer?.accept(message)

                val response = ChatResponse.builder()
                    .id("noop-response")
                    .modelName("no-model")
                    .aiMessage(AiMessage.from(message))
                    .build()
                completeConsumer?.accept(response)
            }
        }
    }

    override fun sendMessage(prompt: String): String = getConfigurationMessage()

    // Session management methods - no-op implementations
    override suspend fun switchSession(sessionId: String) {
        // No-op: NoopChatClient doesn't support session management
    }

    override suspend fun saveCurrentSession() {
        // No-op: NoopChatClient doesn't support session management
    }

    override fun getCurrentSessionId(): String? = null

    override fun clearMemory() {
        // No-op: NoopChatClient doesn't have memory
    }
}
