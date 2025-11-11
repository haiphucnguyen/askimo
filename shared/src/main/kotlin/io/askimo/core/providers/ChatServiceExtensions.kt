/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.util.RetryPresets
import io.askimo.core.util.RetryUtils
import java.util.concurrent.CountDownLatch

/**
 * Provides a synchronous interface to chat with a language model.
 *
 * This extension function wraps the asynchronous streaming API of [ChatService]
 * into a blocking call that returns the complete response as a string.
 *
 * @param prompt The input text to send to the language model
 * @param onToken Optional callback function that is invoked for each token received from the model
 * @return The complete response from the language model as a string
 */
fun ChatService.sendStreamingMessageWithCallback(
    prompt: String,
    onToken: (String) -> Unit = {},
): String = RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
    val sb = StringBuilder()
    val done = CountDownLatch(1)
    var errorOccurred = false
    var isConfigurationError = false
    var capturedError: Throwable? = null

    sendMessageStreaming(prompt)
        .onPartialResponse { chunk ->
            sb.append(chunk)
            onToken(chunk)
        }.onCompleteResponse {
            done.countDown()
        }.onError { e ->
            errorOccurred = true
            capturedError = e

            val errorMessage = e.message ?: ""
            val isModelError = errorMessage.contains("model is required") ||
                errorMessage.contains("No model provided") ||
                errorMessage.contains("model not found") ||
                errorMessage.contains("invalid model")

            val isApiKeyError = errorMessage.contains("api key") ||
                errorMessage.contains("authentication") ||
                errorMessage.contains("unauthorized") ||
                errorMessage.contains("invalid API key") ||
                errorMessage.contains("Incorrect API key provided") ||
                errorMessage.contains("invalid_api_key") ||
                e is dev.langchain4j.exception.AuthenticationException

            if (isModelError || isApiKeyError) {
                isConfigurationError = true
                val helpMessage = when {
                    isModelError -> """
                        ⚠️  Model configuration required!

                        It looks like you haven't selected a model yet. Please configure your setup:

                        1. Set a provider: :set-provider openai
                        2. Check available models: :models
                        3. Select a model from the list
                    """.trimIndent()

                    isApiKeyError -> """
                        ⚠️  API key configuration required!

                        Your API key is missing or invalid. Please configure it:

                        Interactive mode: :set-param api_key YOUR_API_KEY
                        Command line: --set-param api_key YOUR_API_KEY
                    """.trimIndent()

                    else -> """
                        ⚠️  Configuration required!

                        Please set up your AI provider first:
                        :set-provider openai
                    """.trimIndent()
                }

                sb.append(helpMessage)
                onToken(helpMessage)
            } else {
                // For other errors, show the original error message
                val errorMsg = "\n[error] ${e.message ?: "unknown error"}\n"
                sb.append(errorMsg)
                onToken(errorMsg)
            }

            done.countDown()
        }.start()

    done.await()

    val result = sb.toString()

    if (isConfigurationError) {
        return@retry result
    }

    if (errorOccurred) {
        val errorDetails = capturedError?.message ?: "Unknown streaming error"
        throw RuntimeException("Streaming error occurred: $errorDetails", capturedError)
    }

    if (result.trim().isEmpty()) {
        throw IllegalStateException("Model returned empty streaming response")
    }

    result
}
