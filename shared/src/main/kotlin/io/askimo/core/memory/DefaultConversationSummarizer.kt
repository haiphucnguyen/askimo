/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.util.JsonUtils.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Default implementation of conversation summarizer using AI models.
 * Provides common functionality for generating structured summaries from conversations.
 */
object DefaultConversationSummarizer {
    private val log = logger<DefaultConversationSummarizer>()

    /**
     * Creates a summarizer function using the provided chat function.
     * The chat function should take a prompt string and return the AI's response text.
     *
     * @param chatFunction Function that takes a prompt and returns AI response text
     * @return A function that takes conversation text and returns a ConversationSummary
     */
    fun createSummarizer(
        provider: ModelProvider,
        chatFunction: (String) -> String,
    ): (String) -> ConversationSummary = { conversationText ->
        val prompt = """
                Analyze the following conversation and provide a structured summary in JSON format.
                Extract key facts, main topics, and recent context.

                Conversation:
                $conversationText

                Respond with valid JSON only (no markdown, no newlines in string values):
                {
                    "keyFacts": {"fact_name": "fact_value"},
                    "mainTopics": ["topic1", "topic2"],
                    "recentContext": "brief summary of the most recent discussion"
                }

                IMPORTANT: Ensure all JSON is on a single line with no newlines inside string values.
        """.trimIndent()

        try {
            var jsonText = chatFunction(prompt)

            // Remove markdown code blocks
            jsonText = jsonText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            jsonText = cleanJsonResponse(jsonText)

            // Sanitize arrays in keyFacts before parsing
            jsonText = sanitizeArraysInKeyFacts(jsonText)

            json.decodeFromString<ConversationSummary>(jsonText)
        } catch (e: Exception) {
            log.error("Failed to generate conversation summary with $provider summarizer. Response was likely malformed.", e)
            ConversationSummary(
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
}
