/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing.providers

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.UrlKnowledgeSourceConfig
import io.askimo.core.context.AppContext
import io.askimo.core.rag.indexing.IndexingCoordinator
import io.askimo.core.rag.indexing.IndexingCoordinatorProvider
import io.askimo.core.rag.indexing.UrlIndexingCoordinator

/**
 * Provider for creating IndexingCoordinator instances for URL knowledge sources.
 */
class UrlIndexingProvider : IndexingCoordinatorProvider {
    override fun supportedType(): Class<out KnowledgeSourceConfig> = UrlKnowledgeSourceConfig::class.java

    override fun createCoordinator(
        projectId: String,
        projectName: String,
        knowledgeSource: KnowledgeSourceConfig,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        appContext: AppContext,
    ): IndexingCoordinator {
        val config = knowledgeSource as UrlKnowledgeSourceConfig
        val urls = config.resourceIdentifiers

        return UrlIndexingCoordinator(
            projectId = projectId,
            projectName = projectName,
            urls = urls,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )
    }
}
