/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.context.AppContextFactory
import io.askimo.core.logging.logger
import io.askimo.core.util.RetryPresets
import io.askimo.core.util.RetryUtils
import java.util.concurrent.CountDownLatch

/**
 * Provides a synchronous interface to chat with a language model.
 *
 * This extension function wraps the asynchronous streaming API of [ChatClient]
 * into a blocking call that returns the complete response as a string.
 *
 * Features:
 * - Automatic retry on transient errors
 * - Context length error detection and automatic context size reduction
 * - Memory clearing on context errors to reduce conversation history
 * - User-friendly error messages for configuration issues
 *
 * @param prompt The input text to send to the language model
 * @param onToken Optional callback function that is invoked for each token received from the model
 * @return The complete response from the language model as a string
 */
fun ChatClient.sendStreamingMessageWithCallback(
    prompt: String,
    onToken: (String) -> Unit = {},
): String {
    val log = logger<ChatClient>()

    // Get provider and model from AppContext
    val appContext = AppContextFactory.createAppContext()
    val provider = appContext.getActiveProvider()
    val model = appContext.params.model

    var contextRetryCount = 0
    val maxContextRetries = 20 // 20 immediate retries for context errors

    while (contextRetryCount <= maxContextRetries) {
        try {
            if (contextRetryCount > 0) {
                log.debug("Retrying request with reduced context (attempt ${contextRetryCount + 1}/${maxContextRetries + 1})")
            }

            // Execute the streaming request with retry logic for transient errors
            return RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
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

                        // Check for context length errors first - let it bubble up for immediate retry
                        if (e.isContextLengthError()) {
                            done.countDown()
                            return@onError
                        }

                        // Check for insufficient context window - non-transient, show helpful message
                        if (e is InsufficientContextException) {
                            isConfigurationError = true
                            sb.append(e.message)
                            onToken(e.message ?: "Insufficient context window")
                            done.countDown()
                            return@onError
                        }

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

                                else -> """
                                    ⚠️  API key configuration required!

                                    Your API key is missing or invalid. Please configure it:

                                    Interactive mode: :set-param api_key YOUR_API_KEY
                                    Command line: --set-param api_key YOUR_API_KEY
                                """.trimIndent()
                            }

                            sb.append(helpMessage)
                            onToken(helpMessage)
                        } else {
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
        } catch (e: Exception) {
            // Check if this is a context length error - immediate retry without backoff
            if (e.isContextLengthError() && contextRetryCount < maxContextRetries) {
                contextRetryCount++

                val modelKey = ModelContextSizeCache.modelKey(provider, model)
                val currentSize = ModelContextSizeCache.get(modelKey)
                val newSize = ModelContextSizeCache.reduce(modelKey, currentSize)

                log.warn("Context length exceeded for $modelKey (attempt $contextRetryCount/${maxContextRetries + 1}). Reducing context size: $currentSize → $newSize tokens. Retrying immediately...")

                // Retry immediately with reduced context size (no backoff)
                // ChatRequestTransformers.enforceTokenBudget() will automatically truncate
                // messages to fit the new smaller budget on the next attempt
                continue
            }

            // Not a context error or out of retries - rethrow
            throw e
        }
    }

    // Should never reach here, but for completeness
    throw IllegalStateException("Failed to send message after ${maxContextRetries + 1} context retries")
}
