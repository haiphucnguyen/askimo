/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.logging.logger
import io.askimo.core.rag.LuceneKeywordRetriever

/**
 * Coordinates indexing of text segments into both:
 * - JVector embedding store (for semantic/vector search)
 * - Lucene keyword index (for BM25/keyword search)
 *
 * This class separates the indexing coordination logic from the individual
 * retriever implementations, following single responsibility principle.
 */
class HybridIndexer(
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val keywordRetriever: LuceneKeywordRetriever,
) {
    private val log = logger<HybridIndexer>()

    /**
     * Index a batch of text segments into both stores:
     * 1. Generate embeddings and store in JVector (semantic search)
     * 2. Index text content in Lucene (keyword search)
     *
     * @param textSegments List of text segments to index
     */
    fun indexSegments(textSegments: List<TextSegment>) {
        if (textSegments.isEmpty()) {
            log.debug("No segments to index")
            return
        }

        try {
            // 1. Batch embed all segments in a single API call
            val embeddings = embeddingModel.embedAll(textSegments).content()

            // 2. Store embeddings in JVector (vector store)
            embeddings.forEachIndexed { index, embedding ->
                embeddingStore.add(embedding, textSegments[index])
            }

            // 3. Index in Lucene (keywords for BM25 search)
            keywordRetriever.indexSegments(textSegments)

            log.debug("Hybrid indexed ${textSegments.size} segments (vector + keyword) in batch")
        } catch (e: Exception) {
            log.error("Failed to hybrid index ${textSegments.size} segments", e)
            throw e
        }
    }

    /**
     * Clear both indexes.
     * Should be called before re-indexing.
     */
    fun clearIndexes() {
        try {
            // Note: JVector store clearing happens at directory level
            // in ProjectIndexer.clearAndReindex()
            keywordRetriever.clearIndex()
            log.debug("Cleared keyword index")
        } catch (e: Exception) {
            log.error("Failed to clear indexes", e)
            throw e
        }
    }
}
