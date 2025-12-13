/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.lucence

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.Query

/**
 * Content retriever backed by a LuceneIndexer.
 *
 * Responsibility:
 * - Convert an incoming natural-language query into an embedding
 * - Perform a vector similarity search against the project's Lucene index
 * - Return the matched text segments as LangChain4j Content items for RAG
 *
 * This is a lightweight alternative to PgVectorContentRetriever that doesn't require Docker/PostgreSQL.
 */
class LuceneContentRetriever(
    private val indexer: LuceneIndexer,
    private val topK: Int = 6,
) : ContentRetriever {
    override fun retrieve(query: Query): List<Content> {
        val q = query.text()
        val embedding = indexer.embed(q)
        val hits: List<String> = indexer.similaritySearch(embedding, topK)
        return hits.map { Content.from(it) }
    }
}
