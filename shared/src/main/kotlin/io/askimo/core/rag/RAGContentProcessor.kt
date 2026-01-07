/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.Query
import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatClient
import io.askimo.core.telemetry.TelemetryCollector
import kotlinx.coroutines.runBlocking

/**
 * Decorator that adds intelligent RAG triggering to content retrieval.
 *
 * Wraps a ContentRetriever and decides whether to actually retrieve based on:
 * - User message intent (classified by AI)
 * - Conversation history (from Query metadata)
 *
 * Uses decorator pattern: implements ContentRetriever and delegates to wrapped retriever
 * only when RAG is deemed necessary.
 *
 * The Query object contains metadata with:
 * - chatMessage: The current user message
 * - chatMemory: The conversation history
 *
 * @property delegate The wrapped retriever (e.g., HybridContentRetriever)
 * @property telemetry Optional telemetry collector for metrics tracking
 */
class RAGContentProcessor(
    private val delegate: ContentRetriever,
    private val classifierChatClient: ChatClient,
    private val telemetry: TelemetryCollector? = null,
) : ContentRetriever {

    private val log = logger<RAGContentProcessor>()
    private val classifier = RAGIntentClassifier(classifierChatClient)

    /**
     * Overrides retrieve() to add intelligent RAG triggering.
     *
     * Flow:
     * 1. Extract conversation history from Query metadata
     * 2. Classify user message intent using AI
     * 3. If RAG needed → delegate to wrapped retriever
     * 4. If RAG not needed → return empty list
     *
     * @param query The user's query (contains chatMessage and chatMemory in metadata)
     * @return Retrieved content if RAG is needed, empty list otherwise
     */
    override fun retrieve(query: Query): List<Content> {
        val userMessage = query.text()

        // Extract conversation history from query metadata
        val metadata = query.metadata()
        val rawChatMemory = metadata?.chatMemory() ?: emptyList()

        // Filter out system messages - they contain summaries/instructions that pollute classification
        // We only need User and AI messages to understand conversation context
        val conversationHistory = rawChatMemory.filterNot { it is SystemMessage }

        log.debug(
            "Evaluating RAG necessity for query: ${userMessage.take(100)}... " +
                "(${conversationHistory.size} conversation messages)",
        )

        // Measure classification time
        val classificationStartTime = System.currentTimeMillis()
        val shouldUseRAG = runBlocking {
            classifier.shouldUseRAG(userMessage, conversationHistory)
        }
        val classificationDuration = System.currentTimeMillis() - classificationStartTime

        // Record classification metrics
        telemetry?.recordRAGClassification(shouldUseRAG, classificationDuration)

        return if (shouldUseRAG) {
            log.info("RAG triggered - retrieving context for query")

            // Measure retrieval time
            val retrievalStartTime = System.currentTimeMillis()
            val results = delegate.retrieve(query)
            val retrievalDuration = System.currentTimeMillis() - retrievalStartTime

            // Record retrieval metrics
            telemetry?.recordRAGRetrieval(results.size, retrievalDuration)

            results
        } else {
            log.info("RAG skipped - direct chat without retrieval")
            emptyList()
        }
    }
}
