/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.context.AppContext

/**
 * Factory for creating appropriate IndexingCoordinator based on knowledge source types.
 * Uses a pluggable provider registry to support extensibility.
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
    ): IndexingCoordinator {
        val provider = IndexingCoordinatorProviderRegistry.getProvider(knowledgeSource)
            ?: throw IllegalArgumentException(
                "No provider registered for knowledge source type: ${knowledgeSource::class.simpleName}. " +
                    "Available types: ${IndexingCoordinatorProviderRegistry.getAllProviders()
                        .joinToString { it.supportedType().simpleName }}",
            )

        return provider.createCoordinator(
            projectId = projectId,
            projectName = projectName,
            knowledgeSource = knowledgeSource,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )
    }
}
