/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequestParameters
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.intent.DetectAiResponseIntentCommand
import io.askimo.core.intent.DetectUserIntentCommand
import io.askimo.core.intent.FollowUpSuggestion
import io.askimo.core.intent.ToolRegistry
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
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.CountDownLatch

/**
 * Default chat request parameters with no custom sampling settings.
 * Used when sampling is disabled or not supported by the model.
 */
private val DEFAULT_PARAMETERS = ChatRequestParameters.builder().build()

/**
 * Extension function to detect if an exception is due to unsupported sampling parameters.
 * Checks for common error messages related to temperature, topP, or other sampling parameters.
 */
private fun Throwable.isUnsupportedSamplingError(): Boolean {
    val message = this.message ?: ""
    return (message.contains("temperature") || message.contains("top_p") || message.contains("topP")) &&
        (
            message.contains("does not support") ||
                message.contains("not supported") ||
                message.contains("unsupported") ||
                message.contains("Unsupported value") ||
                message.contains("cannot both be specified")
            )
}

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
 * - Two-stage intent detection:
 *   - Stage 1 (Pre-request): Detect user intent to attach relevant tools
 *   - Stage 2 (Post-response): Detect follow-up opportunities from AI response
 *
 * @param userMessage The input text to send to the language model
 * @param onToken Optional callback function that is invoked for each token received from the model
 * @param onFollowUpSuggestion Optional callback for follow-up suggestions based on AI response
 * @return The complete response from the language model as a string
 */
fun ChatClient.sendStreamingMessageWithCallback(
    projectId: String? = null,
    userMessage: UserMessage,
    onToken: (String) -> Unit = {},
    onFollowUpSuggestion: ((FollowUpSuggestion) -> Unit)? = null,
): String {
    val log = logger<ChatClient>()

    // === STAGE 1: Pre-request - Detect user intent ===
    // Analyze user message to determine which tools should be made available

    val availableTools = ToolRegistry.getIntentBased()
    val userIntent = DetectUserIntentCommand.execute(
        userMessage.singleText() ?: "",
        availableTools = availableTools,
    )

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

            // Build combined request parameters with sampling and tools
            val customParams = run {
                val builder = ChatRequestParameters.builder()
                var hasParams = false

                // Add sampling parameters if enabled and supported
                if (AppConfig.chat.sampling.enabled) {
                    val supportsSampling = ModelCapabilitiesCache.supportsSampling(provider, model)
                    if (supportsSampling) {
                        AppConfig.chat.sampling.let { samplingConfig ->
                            builder
                                .temperature(samplingConfig.temperature)
                                .topP(samplingConfig.topP)
                            hasParams = true
                        }
                    }
                }

                // Add tool specifications if user intent detected tools
                if (userIntent.tools.isNotEmpty()) {
                    builder.toolSpecifications(userIntent.tools.map { it.specification })
                    hasParams = true
                }

                if (hasParams) builder.build() else DEFAULT_PARAMETERS
            }

            // Execute the streaming request with retry logic for transient errors
            return RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
                val sb = StringBuilder()
                val done = CountDownLatch(1)
                var errorOccurred = false
                var isConfigurationError = false
                var capturedError: Throwable? = null

                sendMessageStreaming(userMessage, customParams)
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
                        if (e?.isContextLengthError() == true) {
                            done.countDown()
                            val modelKey = ModelCapabilitiesCache.modelKey(provider, model)
                            val currentSize = ModelCapabilitiesCache.get(modelKey).contextSize
                            val newSize = ModelCapabilitiesCache.reduceContextSize(modelKey, currentSize)

                            log.warn("Context length exceeded for $modelKey (attempt $contextRetryCount/${maxContextRetries + 1}). Reducing context size: $currentSize → $newSize tokens. Retrying immediately...")
                            return@onError
                        }

                        // Check for insufficient context window - non-transient, show helpful message
                        if (e is InsufficientContextException) {
                            isConfigurationError = true
                            sb.append(e.message)
                            onToken(e.message ?: "Insufficient context window")
                            done.countDown()
                            contextRetryCount++
                            return@onError
                        }

                        // Check for unsupported sampling parameters (temperature, topP)
                        if (e.isUnsupportedSamplingError()) {
                            log.warn("Unsupported sampling parameters detected. Falling back to non sampling settings.")
                            ModelCapabilitiesCache.setSamplingSupport(provider, model, false)
                            done.countDown()
                            return@onError
                        }

                        // Check if the underlying cause is a network connection issue
                        if (e.cause is UnresolvedAddressException) {
                            isConfigurationError = true
                            val connectionErrorMsg = """
                                ⚠️  Unable to connect to the server!

                                Cannot resolve the server address. Please check:
                                1. Your internet connection is working
                                2. The server URL/endpoint is correct
                                3. There are no firewall or proxy issues blocking the connection
                            """.trimIndent()
                            sb.append(connectionErrorMsg)
                            onToken(connectionErrorMsg)
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

                // === STAGE 2: Post-response - Detect follow-up opportunities ===
                if (onFollowUpSuggestion != null) {
                    val followUpSuggestion = DetectAiResponseIntentCommand.execute(
                        result,
                        availableTools = ToolRegistry.getFollowUpOnly(),
                    )

                    if (followUpSuggestion != null) {
                        log.debug("Detected follow-up opportunity (Stage 2): ${followUpSuggestion.question}")
                        onFollowUpSuggestion(followUpSuggestion)
                    }
                }

                result
            }
        } catch (e: Exception) {
            // Check if this is a context length error - immediate retry without backoff
            if ((e.isContextLengthError() || e.isUnsupportedSamplingError()) && contextRetryCount < maxContextRetries) {
                contextRetryCount++

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
        Analyze this conversation and create a HIGH-QUALITY structured summary. Focus on extracting meaningful information while preserving essential context.

        CONVERSATION:
        $conversationText

        CRITICAL INSTRUCTIONS:
        1. Extract ONLY meaningful, actionable facts - ignore:
           - Greetings and pleasantries (hello, thanks, etc.)
           - Simple confirmations (ok, yes, got it)
           - Filler words and transitional phrases
           - Repetitive information already captured

        2. Identify CONCRETE topics and user goals:
           - Use specific terms, not generic labels (e.g., "Python error handling" not "coding")
           - Capture the user's actual objectives and problems
           - Note technical details, file names, or specific requirements

        3. Preserve ESSENTIAL context:
           - Important decisions made during the conversation
           - User preferences and constraints
           - Critical information needed for future interactions
           - Any unresolved issues or pending tasks

        4. For recentContext:
           - Summarize the LATEST state of the conversation
           - What is the user currently working on or asking about?
           - What is the immediate next step or goal?
           - Keep it concise (1-3 sentences)

        5. Quality over quantity:
           - Better to have fewer high-quality facts than many trivial ones
           - Each fact should be actionable or informative
           - Avoid redundancy across keyFacts, mainTopics, and recentContext

        Respond with valid JSON only (no markdown, no code blocks, single line):
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
