/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.internal

import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Event emitted when a specific knowledge source should be removed from the index.
 * This is an internal event that triggers ProjectIndexer to delete the embeddings
 * and index state associated with the given knowledge source.
 */
data class ProjectIndexRemovalEvent(
    val projectId: String,
    val knowledgeSource: KnowledgeSourceConfig,
    val reason: String = "Knowledge source removed",
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails(): String = "Removal requested for project $projectId, source: ${knowledgeSource.resourceIdentifier} — $reason"
}
