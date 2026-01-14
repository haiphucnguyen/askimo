/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing.providers

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.context.AppContext
import io.askimo.core.rag.indexing.IndexingCoordinator
import io.askimo.core.rag.indexing.IndexingCoordinatorProvider
import io.askimo.core.rag.indexing.LocalFoldersIndexingCoordinator
import java.nio.file.Paths

/**
 * Provider for creating IndexingCoordinator instances for local folder knowledge sources.
 */
class LocalFoldersIndexingProvider : IndexingCoordinatorProvider {
    override fun supportedType(): Class<out KnowledgeSourceConfig> = LocalFoldersKnowledgeSourceConfig::class.java

    override fun createCoordinator(
        projectId: String,
        projectName: String,
        knowledgeSource: KnowledgeSourceConfig,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        appContext: AppContext,
    ): IndexingCoordinator {
        val config = knowledgeSource as LocalFoldersKnowledgeSourceConfig
        val paths = config.resourceIdentifiers.map { Paths.get(it) }

        return LocalFoldersIndexingCoordinator(
            projectId = projectId,
            projectName = projectName,
            paths = paths,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )
    }
}
