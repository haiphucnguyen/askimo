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
import io.askimo.core.context.AppContext
import java.nio.file.Paths

/**
 * Factory for creating appropriate IndexingCoordinator based on knowledge source types.
 */
object IndexingCoordinatorFactory {

    /**
     * Create an indexing coordinator for the given knowledge sources.
     *
     * @param projectId The project ID
     * @param projectName The project name
     * @param knowledgeSources The knowledge sources to index
     * @param embeddingStore The embedding store
     * @param embeddingModel The embedding model
     * @param appContext The application context
     * @return An IndexingCoordinator instance
     * @throws IllegalArgumentException if no supported knowledge sources found
     */
    fun createCoordinator(
        projectId: String,
        projectName: String,
        knowledgeSources: List<KnowledgeSourceConfig>,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        appContext: AppContext,
    ): IndexingCoordinator {
        // For now, only handle LocalFiles
        // Future: support other knowledge source types
        val localFilesSources = knowledgeSources.filterIsInstance<LocalFilesKnowledgeSourceConfig>()

        if (localFilesSources.isEmpty()) {
            throw IllegalArgumentException("No supported knowledge sources found for project $projectId")
        }

        // Collect all paths from all local files sources
        val allPaths = localFilesSources
            .flatMap { it.resourceIdentifiers }
            .map { Paths.get(it) }

        return LocalFilesIndexingCoordinator(
            projectId = projectId,
            projectName = projectName,
            paths = allPaths,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )
    }
}
