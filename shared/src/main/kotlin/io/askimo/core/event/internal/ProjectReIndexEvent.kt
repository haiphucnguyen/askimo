/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.internal

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Event emitted when a project needs to be re-indexed.
 * This is an internal event that triggers ProjectIndexer to clear and rebuild the index.
 */
data class ProjectReIndexEvent(
    val projectId: String,
    val reason: String = "Manual re-index requested",
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails(): String = "Re-indexing project $projectId: $reason"
}
