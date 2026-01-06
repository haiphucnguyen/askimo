/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import kotlinx.coroutines.withTimeout

/**
 * Determines if RAG retrieval is needed for the user's message.
 * Uses AI classification for all queries to support multiple languages (Spanish, German, etc.).
 */
class RAGIntentClassifier(
    private val classifierChatClient: ChatClient,
) {
    private val log = logger<RAGIntentClassifier>()

    companion object {
        private const val CLASSIFICATION_TIMEOUT_MS = 5000L
    }

    /**
     * Classifies whether RAG should be used.
     * Always uses AI to support multiple languages.
     *
     * @param userMessage The current user message to classify
     * @param conversationHistory Recent conversation context
     * @return true if RAG should be used, false otherwise
     */
    suspend fun shouldUseRAG(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
    ): Boolean = try {
        val prompt = buildPrompt(userMessage, conversationHistory)

        val response = withTimeout(CLASSIFICATION_TIMEOUT_MS) {
            classifierChatClient.sendMessage(prompt)
        }

        val decision = response.trim().uppercase() == "YES"
        log.debug("RAG Classification: ${if (decision) "Use RAG" else "Skip RAG"} (response: $response)")

        decision
    } catch (e: Exception) {
        log.warn("Intent classification failed: ${e.message}. Defaulting to using RAG", e)
        true
    }

    /**
     * Builds the classification prompt.
     * Includes recent conversation context to help AI understand if this is a follow-up.
     */
    private fun buildPrompt(
        userMessage: String,
        history: List<ChatMessage>,
    ): String {
        val recentContext = if (history.isEmpty()) {
            "No previous conversation"
        } else {
            history.takeLast(3).joinToString("\n") {
                val role = when {
                    it.type().name.contains("USER") -> "User"
                    it.type().name.contains("AI") -> "AI"
                    else -> it.type().name
                }
                val text = it.getTextContent()
                val content = text.take(150)
                "$role: $content${if (text.length > 150) "..." else ""}"
            }
        }

        return """
            Does this user message need to search the knowledge base (RAG)?

            Recent conversation:
            $recentContext

            Current message: "$userMessage"

            Respond ONLY with "YES" or "NO":

            YES - If the message is:
            - A NEW question requiring knowledge base lookup
            - Asking "who", "what", "where" about specific entities, people, or concepts
            - Asking "how to" do something
            - Requesting code examples or documentation
            - Looking up API/library/feature information
            - Asking about features, configuration, or implementation details
            - Asking about people, projects, or domain-specific concepts
            - Any factual query that might be in the knowledge base

            NO - If the message is:
            - A follow-up clarification ("explain more", "what about X?")
            - Correcting or critiquing the AI's previous answer
            - Casual conversation ("yes", "no", "thanks", "ok")
            - Requesting modification of the previous response
            - Discussing something already covered in recent messages
            - Asking the AI to correct/update its previous answer

            Answer:
        """.trimIndent()
    }

    /**
     * Extract text content from ChatMessage
     */
    private fun ChatMessage.getTextContent(): String = when (this) {
        is UserMessage -> this.singleText() ?: ""
        is AiMessage -> this.text() ?: ""
        is SystemMessage -> this.text() ?: ""
        else -> ""
    }
}
