/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing.providers

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.context.AppContext
import io.askimo.core.rag.indexing.IndexingCoordinator
import io.askimo.core.rag.indexing.IndexingCoordinatorProvider
import io.askimo.core.rag.indexing.LocalFilesIndexingCoordinator
import java.nio.file.Paths

/**
 * Provider for creating IndexingCoordinator instances for local file knowledge sources.
 */
class LocalFilesIndexingProvider : IndexingCoordinatorProvider {
    override fun supportedType(): Class<out KnowledgeSourceConfig> = LocalFilesKnowledgeSourceConfig::class.java

    override fun createCoordinator(
        projectId: String,
        projectName: String,
        knowledgeSource: KnowledgeSourceConfig,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        appContext: AppContext,
    ): IndexingCoordinator {
        val config = knowledgeSource as LocalFilesKnowledgeSourceConfig
        val filePaths = config.resourceIdentifiers.map { Paths.get(it) }

        return LocalFilesIndexingCoordinator(
            projectId = projectId,
            projectName = projectName,
            filePaths = filePaths,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )
    }
}
