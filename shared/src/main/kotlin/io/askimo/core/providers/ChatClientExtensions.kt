/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.memory.ConversationSummary
import io.askimo.core.util.JsonUtils.json
import io.askimo.core.util.RetryPresets
import io.askimo.core.util.RetryUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
    val appContext = AppContext.getInstance()
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

/**
 * Generates a structured summary of a conversation.
 *
 * This extension function analyzes conversation text and extracts:
 * - Key facts as name-value pairs
 * - Main topics discussed
 * - Recent context summary
 *
 * The AI model is instructed to respond with JSON only, which is then parsed
 * into a [ConversationSummary] object.
 *
 * @param conversationText The conversation text to summarize
 * @return A ConversationSummary containing key facts, main topics, and recent context
 */
fun ChatClient.getSummary(conversationText: String): ConversationSummary {
    val log = logger<ChatClient>()

    val prompt = """
        Analyze this conversation and create a HIGH-QUALITY structured summary.

        CONVERSATION:
        $conversationText

        INSTRUCTIONS:
        1. Extract only meaningful, actionable facts (ignore greetings, confirmations, filler)
        2. Identify concrete topics and user goals (not generic labels)
        3. Capture decisions, preferences, and important context
        4. Keep recentContext focused on latest user intent and progress
        5. Omit redundant or trivial information

        Respond with valid JSON only (no markdown, single line):
        {
            "keyFacts": {"specific_fact_name": "concrete_value"},
            "mainTopics": ["specific_topic_1", "specific_topic_2"],
            "recentContext": "concise summary of current state and user's immediate goal"
        }
    """.trimIndent()

    try {
        var jsonText = this.sendMessage(prompt)

        // Remove markdown code blocks
        jsonText = jsonText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        jsonText = cleanJsonResponse(jsonText)
        jsonText = sanitizeArraysInKeyFacts(jsonText)

        return json.decodeFromString<ConversationSummary>(jsonText)
    } catch (e: Exception) {
        log.error("Failed to generate conversation summary. Response was likely malformed.", e)
        return ConversationSummary(
            keyFacts = emptyMap(),
            mainTopics = emptyList(),
            recentContext = conversationText.takeLast(500),
        )
    }
}

/**
 * Clean JSON response from AI to handle common formatting issues.
 * Removes newlines inside string values while preserving structure.
 */
private fun cleanJsonResponse(jsonText: String): String {
    // First, try to find the actual JSON object
    val jsonStart = jsonText.indexOf('{')
    val jsonEnd = jsonText.lastIndexOf('}')

    if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
        return jsonText // Return as-is if no valid JSON structure found
    }

    val jsonOnly = jsonText.substring(jsonStart, jsonEnd + 1)

    // Remove newlines and excessive whitespace while preserving JSON structure
    return jsonOnly
        .replace("\n", " ") // Remove all newlines
        .replace("\r", " ") // Remove carriage returns
        .replace("\\s+".toRegex(), " ") // Collapse multiple spaces
        .trim()
}

/**
 * Sanitize arrays in keyFacts to comma-separated strings.
 * Since ConversationSummary.keyFacts is Map<String, String>, we need to convert
 * any array values to strings.
 */
private fun sanitizeArraysInKeyFacts(jsonText: String): String {
    val log = logger<ChatClient>()

    return try {
        val jsonParser = Json { ignoreUnknownKeys = true }
        val jsonElement = jsonParser.parseToJsonElement(jsonText)

        if (jsonElement !is JsonObject) {
            return jsonText
        }

        val keyFacts = jsonElement["keyFacts"]
        if (keyFacts !is JsonObject) {
            return jsonText
        }

        // Convert arrays in keyFacts to comma-separated strings
        val sanitizedKeyFacts = keyFacts.entries.associate { (key, value) ->
            key to when (value) {
                is JsonArray -> {
                    // Convert array to comma-separated string
                    val arrayValues = value.jsonArray.mapNotNull {
                        when (it) {
                            is JsonPrimitive -> it.jsonPrimitive.content
                            else -> null
                        }
                    }
                    JsonPrimitive(arrayValues.joinToString(", "))
                }
                else -> value
            }
        }

        // Rebuild JSON with sanitized keyFacts
        val sanitizedJson = JsonObject(
            jsonElement.toMap().toMutableMap().apply {
                put("keyFacts", JsonObject(sanitizedKeyFacts))
            },
        )

        sanitizedJson.toString()
    } catch (e: Exception) {
        log.debug("Failed to sanitize arrays in keyFacts, returning original: ${e.message}")
        jsonText
    }
}
