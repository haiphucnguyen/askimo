/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.internal

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Event emitted when a project needs to be indexed.
 * This is an internal event that triggers ProjectIndexer to index the project files
 * using the provided embedding store and model.
 *
 * @param knowledgeSources Optional list of specific sources to index.
 *   If null, all project sources will be indexed.
 */
data class ProjectIndexingRequestedEvent(
    val projectId: String,
    val embeddingStore: EmbeddingStore<TextSegment>? = null,
    val embeddingModel: EmbeddingModel? = null,
    val watchForChanges: Boolean = true,
    val knowledgeSources: List<KnowledgeSourceConfig>? = null,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails(): String {
        val sourcesInfo = if (knowledgeSources != null) {
            ", sources: [${knowledgeSources.joinToString { it.resourceIdentifier }}]"
        } else {
            ""
        }
        return "Indexing requested for project $projectId$sourcesInfo"
    }
}
