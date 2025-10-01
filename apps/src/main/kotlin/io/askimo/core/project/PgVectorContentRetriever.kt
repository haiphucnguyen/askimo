/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.Query

class PgVectorContentRetriever(
    private val indexer: PgVectorIndexer,
    private val topK: Int = 6,
) : ContentRetriever {
    override fun retrieve(query: Query): List<Content> {
        val q = query.text()
        val embedding = indexer.embed(q)
        val hits: List<String> = indexer.similaritySearch(embedding, topK)
        return hits.map { Content.from(it) }
    }
}
