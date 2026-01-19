/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.error

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Event emitted when an indexing error occurs.
 * Allows UI layers to handle and display errors appropriately.
 */
data class IndexingErrorEvent(
    val projectId: String,
    val errorType: IndexingErrorType,
    val details: Map<String, String> = emptyMap(),
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.ERROR

    override fun getDetails(): String = "Indexing error for project $projectId: ${errorType.name} - $details"
}

/**
 * Types of indexing errors that can occur.
 */
enum class IndexingErrorType {
    EMBEDDING_MODEL_NOT_FOUND,
    IO_ERROR,
    UNKNOWN_ERROR,
}
