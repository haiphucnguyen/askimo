/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingSearchResult
import dev.langchain4j.store.embedding.EmbeddingStore

class LuceneEmbeddingStore : EmbeddingStore<TextSegment> {
    override fun add(embedding: Embedding?): String? {
        TODO("Not yet implemented")
    }

    override fun add(id: String?, embedding: Embedding?) {
        TODO("Not yet implemented")
    }

    override fun add(
        embedding: Embedding?,
        embedded: TextSegment?,
    ): String? {
        TODO("Not yet implemented")
    }

    override fun addAll(embeddings: List<Embedding?>?): List<String?>? {
        TODO("Not yet implemented")
    }

    override fun search(request: EmbeddingSearchRequest?): EmbeddingSearchResult<TextSegment?>? {
        TODO("Not yet implemented")
    }
}
