/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.Query
import io.askimo.core.logging.logger

/**
 * Hybrid content retriever that combines multiple retrieval strategies.
 * Uses Reciprocal Rank Fusion (RRF) to merge results from:
 * - Vector similarity search (semantic)
 * - Keyword search (BM25/Lucene)
 *
 * RRF Formula: score(doc) = Σ 1 / (k + rank_i)
 * where k=60 (standard constant) and rank_i is the rank from retriever i
 *
 * This approach gives better results than pure vector search by:
 * - Catching exact keyword matches
 * - Leveraging semantic understanding
 * - Being robust to individual retriever failures
 */
class HybridContentRetriever(
    private val vectorRetriever: ContentRetriever,
    private val keywordRetriever: ContentRetriever,
    private val maxResults: Int = 5,
    private val k: Int = 60,
) : ContentRetriever {

    private val log = logger<HybridContentRetriever>()

    override fun retrieve(query: Query): List<Content> {
        log.debug("Hybrid retrieval for query: ${query.text()}")

        // Retrieve from both sources
        val vectorResults = try {
            vectorRetriever.retrieve(query)
        } catch (e: Exception) {
            log.warn("Vector retrieval failed: ${e.message}")
            emptyList()
        }

        val keywordResults = try {
            keywordRetriever.retrieve(query)
        } catch (e: Exception) {
            log.warn("Keyword retrieval failed: ${e.message}")
            emptyList()
        }

        log.debug("Vector retrieval: ${vectorResults.size} results")
        log.debug("Keyword retrieval: ${keywordResults.size} results")

        // If one retriever fails, fall back to the other
        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            log.warn("Both retrievers returned no results")
            return emptyList()
        }

        if (vectorResults.isEmpty()) {
            log.debug("Using only keyword results (vector retrieval failed)")
            return keywordResults.take(maxResults)
        }

        if (keywordResults.isEmpty()) {
            log.debug("Using only vector results (keyword retrieval failed)")
            return vectorResults.take(maxResults)
        }

        // Merge using Reciprocal Rank Fusion
        val merged = reciprocalRankFusion(vectorResults, keywordResults)

        log.info("Hybrid retrieval merged ${vectorResults.size} vector + ${keywordResults.size} keyword → ${merged.size} results")

        return merged.take(maxResults)
    }

    /**
     * Reciprocal Rank Fusion (RRF) algorithm.
     * Combines ranked lists by assigning scores based on rank position.
     *
     * For each document:
     *   RRF_score = Σ 1 / (k + rank_in_list_i)
     *
     * Documents are then sorted by RRF score (higher is better).
     */
    private fun reciprocalRankFusion(
        vectorResults: List<Content>,
        keywordResults: List<Content>,
    ): List<Content> {
        // Map content to unique key for deduplication
        // Using text segment content as key (assumes same content = same document)
        val contentMap = mutableMapOf<String, Content>()
        val scoreMap = mutableMapOf<String, Double>()

        // Process vector results
        vectorResults.forEachIndexed { index, content ->
            val key = content.textSegment().text()
            val rank = index + 1
            val score = 1.0 / (k + rank)

            contentMap[key] = content
            scoreMap[key] = scoreMap.getOrDefault(key, 0.0) + score
        }

        keywordResults.forEachIndexed { index, content ->
            val key = content.textSegment().text()
            val rank = index + 1
            val score = 1.0 / (k + rank)

            // If not already added from vector results, add it
            if (key !in contentMap) {
                contentMap[key] = content
            }
            scoreMap[key] = scoreMap.getOrDefault(key, 0.0) + score
        }

        // Sort by RRF score (descending)
        val sortedKeys = scoreMap.entries
            .sortedByDescending { it.value }
            .map { it.key }

        log.debug("RRF fusion: ${contentMap.size} unique documents from ${vectorResults.size + keywordResults.size} total results")

        return sortedKeys.mapNotNull { contentMap[it] }
    }
}
