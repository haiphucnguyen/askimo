/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.tool.ToolExecution
import java.util.function.Consumer

/**
 * A no-operation implementation of the ChatService interface.
 *
 * This implementation is used as a fallback when no chat model is configured.
 * When the stream is started, it throws a RuntimeException informing the user
 * that they need to configure a chat model using the ':set-provider' command.
 */
object NoopChatService : ChatService {
    override fun stream(prompt: String): TokenStream {
        return object : TokenStream {
            private var errorConsumer: Consumer<Throwable>? = null

            override fun onPartialResponse(consumer: Consumer<String>): TokenStream = this

            override fun onCompleteResponse(completeResponseHandler: Consumer<ChatResponse>): TokenStream = this

            override fun onError(consumer: Consumer<Throwable>): TokenStream {
                errorConsumer = consumer
                return this
            }

            override fun onToolExecuted(toolExecuteHandler: Consumer<ToolExecution>): TokenStream = this

            override fun ignoreErrors(): TokenStream = this

            override fun onRetrieved(contentHandler: Consumer<List<dev.langchain4j.rag.content.Content?>?>?): TokenStream = this

            override fun start() {
                errorConsumer?.accept(
                    RuntimeException("No chat model configured. Please configure a chat model using the 'set provider' command."),
                )
            }
        }
    }
}
