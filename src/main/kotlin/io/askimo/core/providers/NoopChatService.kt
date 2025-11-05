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
import java.util.function.Consumer

/**
 * A no-operation implementation of the ChatService interface.
 *
 * This implementation is used as a fallback when no chat model is configured.
 */
object NoopChatService : ChatService {
    private val CONFIGURATION_MESSAGE = """
        ⚠️  No AI provider configured yet!

        To start chatting, please configure a provider first:
        1. Check available providers: :providers
        2. Set a provider: :set-provider <provider_name>

        After setting a provider, you'll need to configure your API key or model settings.
    """.trimIndent()

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
                partialConsumer?.accept(CONFIGURATION_MESSAGE)

                val response = ChatResponse.builder()
                    .id("noop-response")
                    .modelName("no-model")
                    .aiMessage(AiMessage.from(CONFIGURATION_MESSAGE))
                    .build()
                completeConsumer?.accept(response)
            }
        }
    }

    override fun sendMessage(prompt: String): String = CONFIGURATION_MESSAGE
}
