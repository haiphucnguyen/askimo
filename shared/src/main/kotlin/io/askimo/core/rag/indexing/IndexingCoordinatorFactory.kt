/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.context.AppContext
import java.nio.file.Paths

/**
 * Factory for creating appropriate IndexingCoordinator based on knowledge source types.
 */
object IndexingCoordinatorFactory {

    /**
     * Create an indexing coordinator for a single knowledge source.
     *
     * @param projectId The project ID
     * @param projectName The project name
     * @param knowledgeSource The knowledge source to index
     * @param embeddingStore The embedding store
     * @param embeddingModel The embedding model
     * @param appContext The application context
     * @return An IndexingCoordinator instance
     * @throws IllegalArgumentException if the knowledge source type is not supported
     */
    fun createCoordinator(
        projectId: String,
        projectName: String,
        knowledgeSource: KnowledgeSourceConfig,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        appContext: AppContext,
    ): IndexingCoordinator = when (knowledgeSource) {
        is LocalFoldersKnowledgeSourceConfig -> {
            val paths = knowledgeSource.resourceIdentifiers.map { Paths.get(it) }
            LocalFoldersIndexingCoordinator(
                projectId = projectId,
                projectName = projectName,
                paths = paths,
                embeddingStore = embeddingStore,
                embeddingModel = embeddingModel,
                appContext = appContext,
            )
        }
        is LocalFilesKnowledgeSourceConfig -> {
            val filePaths = knowledgeSource.resourceIdentifiers.map { Paths.get(it) }
            LocalFilesIndexingCoordinator(
                projectId = projectId,
                projectName = projectName,
                filePaths = filePaths,
                embeddingStore = embeddingStore,
                embeddingModel = embeddingModel,
                appContext = appContext,
            )
        }
    }
}
