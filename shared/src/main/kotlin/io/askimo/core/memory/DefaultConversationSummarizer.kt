/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import io.askimo.core.logging.logger
import kotlinx.serialization.json.Json

/**
 * Default AI-powered conversation summarizer that uses a ChatModel
 * to generate structured summaries of conversations.
 *
 * This summarizer extracts:
 * - Key facts (as key-value pairs)
 * - Main topics discussed
 * - Recent context summary
 *
 * Example usage:
 * ```kotlin
 * val chatModel = OpenAiChatModel.builder()
 *     .apiKey(apiKey)
 *     .modelName("gpt-4o-mini")
 *     .build()
 *
 * val summarizer = DefaultConversationSummarizer(chatModel)
 * val summary = summarizer.summarize(conversationText)
 * ```
 */
class DefaultConversationSummarizer(
    private val chatModel: ChatModel,
) {
    private val log = logger<DefaultConversationSummarizer>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Summarizes a conversation text into a structured ConversationSummary.
     *
     * @param conversationText The formatted conversation text (User/Assistant exchanges)
     * @return A structured summary with key facts, topics, and context
     */
    fun summarize(conversationText: String): ConversationSummary {
        log.debug("Generating AI-powered summary for conversation ({} chars)", conversationText.length)

        val prompt = buildSummarizationPrompt(conversationText)

        return try {
            val response = chatModel.chat(UserMessage.from(prompt))
            val responseText = response.aiMessage().text()
            log.debug("Received AI summary response: {}...", responseText.take(200))

            // Try to parse as JSON
            parseJsonResponse(responseText)
        } catch (e: Exception) {
            log.error("Failed to generate structured summary", e)
            // Fallback: return empty summary (memory will use basic)
            ConversationSummary()
        }
    }

    private fun buildSummarizationPrompt(conversationText: String): String = """
        Analyze the following conversation and extract structured information.

        Conversation:
        $conversationText

        Provide your analysis in the following JSON format:
        {
          "keyFacts": {
            "fact_name": "fact_value"
          },
          "mainTopics": ["topic1", "topic2"],
          "recentContext": "A brief summary of the most recent discussion"
        }

        Guidelines:
        - keyFacts: Extract important information as key-value pairs. Each value MUST be a simple string, NOT an array or object. If you need to store multiple items, combine them into a comma-separated string.
          Example: "frameworks": "OpenAI Java SDK, LangChain4J 1.9, Spring Boot"
        - mainTopics: List the main subjects or themes discussed (max 5-7 topics)
        - recentContext: Summarize the most recent part of the conversation (2-3 sentences)

        IMPORTANT: Return ONLY the JSON object without markdown code blocks or extra text. Do not wrap the JSON in ```json``` tags.
    """.trimIndent()

    private fun parseJsonResponse(response: String): ConversationSummary {
        // Remove markdown code blocks if present
        val cleanedResponse = response
            .replace("```json", "")
            .replace("```", "")
            .trim()

        // Try to extract JSON from response (in case the model adds extra text)
        val jsonMatch = Regex("""\{[\s\S]*\}""").find(cleanedResponse)
        val jsonText = jsonMatch?.value ?: cleanedResponse

        return try {
            json.decodeFromString<ConversationSummary>(jsonText)
        } catch (e: Exception) {
            log.warn("Failed to parse JSON response, attempting to sanitize and retry", e)

            // Try to sanitize the JSON by converting arrays in keyFacts to comma-separated strings
            try {
                val sanitized = sanitizeKeyFacts(jsonText)
                json.decodeFromString<ConversationSummary>(sanitized)
            } catch (e2: Exception) {
                log.error("Failed to parse even after sanitization, returning empty summary", e2)
                ConversationSummary()
            }
        }
    }

    /**
     * Sanitizes keyFacts by converting array values to comma-separated strings.
     * This handles cases where the AI returns arrays instead of strings.
     */
    private fun sanitizeKeyFacts(jsonText: String): String {
        // Find the keyFacts section and only sanitize arrays within it
        val keyFactsRegex = Regex(""""keyFacts":\s*\{([^}]+)\}""")
        val keyFactsMatch = keyFactsRegex.find(jsonText)

        if (keyFactsMatch == null) {
            return jsonText
        }

        val keyFactsContent = keyFactsMatch.groupValues[1]

        // Replace array values with comma-separated strings in keyFacts only
        val sanitizedKeyFacts = Regex(""""([^"]+)":\s*\[([^\]]+)\]""").replace(keyFactsContent) { matchResult ->
            val key = matchResult.groupValues[1]
            val arrayContent = matchResult.groupValues[2]

            // Extract quoted strings from array and join with commas
            val values = Regex(""""([^"]+)"""").findAll(arrayContent)
                .map { it.groupValues[1] }
                .joinToString(", ")

            """"$key": "$values""""
        }

        // Replace the original keyFacts with the sanitized version
        return jsonText.replace(keyFactsMatch.value, """"keyFacts": {$sanitizedKeyFacts}""")
    }
}
