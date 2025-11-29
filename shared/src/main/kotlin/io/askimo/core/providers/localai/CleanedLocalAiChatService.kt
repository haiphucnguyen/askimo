/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.localai

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.rag.content.Content
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.tool.ToolExecution
import io.askimo.core.providers.ChatService
import java.util.function.Consumer

/**
 * A wrapper for LocalAI's ChatService that cleans control tokens from responses.
 *
 * LocalAI sometimes includes special control tokens like <|channel|>, <|message|>, <|end|>, etc.
 * in its responses. This wrapper intercepts the streaming output and removes these tokens
 * before passing them to the consumer.
 */
class CleanedLocalAiChatService(
    private val delegate: ChatService,
) : ChatService {

    private fun cleanLocalAITokens(text: String): String {
        // Remove all LocalAI control tokens but keep actual content
        var cleaned = text
            .replace("<|channel|>", "")
            .replace("<|message|>", "")
            .replace("<|end|>", "")
            .replace("<|start|>", "")
            .trim()

        // Remove internal reasoning patterns that LocalAI sometimes includes
        // Pattern: "We need to answer: ... That's ... Provide answer." or similar
        cleaned = cleaned
            .replace(Regex("^We need to (answer|give|provide)[^.]+\\.[^.]+\\.\\s*Provide (answer|response|info)[.:]?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^analysis[:\\s]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^assistant[:\\s]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^final[:\\s]+", RegexOption.IGNORE_CASE), "")
            .trim()

        return cleaned
    }

    override fun sendMessage(prompt: String): String {
        val rawResponse = delegate.sendMessage(prompt)
        return cleanLocalAITokens(rawResponse)
    }

    override fun sendMessageStreaming(prompt: String): TokenStream {
        return object : TokenStream {
            private val delegateStream = delegate.sendMessageStreaming(prompt)
            private var partialConsumer: Consumer<String>? = null

            override fun onPartialResponse(consumer: Consumer<String>): TokenStream {
                partialConsumer = consumer
                delegateStream.onPartialResponse { token ->
                    val cleaned = cleanLocalAITokens(token)
                    if (cleaned.isNotBlank()) {
                        consumer.accept(cleaned)
                    }
                }
                return this
            }

            override fun onCompleteResponse(consumer: Consumer<ChatResponse>): TokenStream {
                delegateStream.onCompleteResponse { response ->
                    // Clean the complete response message as well
                    val originalMessage = response.aiMessage()
                    val cleanedText = cleanLocalAITokens(originalMessage.text())
                    val cleanedMessage = AiMessage.from(cleanedText)

                    val cleanedResponse = ChatResponse.builder()
                        .id(response.id())
                        .modelName(response.modelName())
                        .aiMessage(cleanedMessage)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build()

                    consumer.accept(cleanedResponse)
                }
                return this
            }

            override fun onError(consumer: Consumer<Throwable>): TokenStream {
                delegateStream.onError(consumer)
                return this
            }

            override fun onToolExecuted(consumer: Consumer<ToolExecution>): TokenStream {
                delegateStream.onToolExecuted(consumer)
                return this
            }

            override fun ignoreErrors(): TokenStream {
                delegateStream.ignoreErrors()
                return this
            }

            override fun onRetrieved(consumer: Consumer<List<Content?>?>?): TokenStream {
                delegateStream.onRetrieved(consumer)
                return this
            }

            override fun start() {
                delegateStream.start()
            }
        }
    }
}
