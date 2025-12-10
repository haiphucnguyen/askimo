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
        - keyFacts: Extract important information like user preferences, names, dates, decisions, or specific requirements
        - mainTopics: List the main subjects or themes discussed (max 5-7 topics)
        - recentContext: Summarize the most recent part of the conversation (2-3 sentences)

        Return ONLY the JSON object, no additional text.
    """.trimIndent()

    private fun parseJsonResponse(response: String): ConversationSummary {
        // Try to extract JSON from response (in case the model adds extra text)
        val jsonMatch = Regex("""\{[\s\S]*\}""").find(response)
        val jsonText = jsonMatch?.value ?: response

        return try {
            json.decodeFromString<ConversationSummary>(jsonText)
        } catch (e: Exception) {
            log.warn("Failed to parse JSON response, returning empty summary", e)
            ConversationSummary()
        }
    }
}
