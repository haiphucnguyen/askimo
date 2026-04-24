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
 * Emitted when a Mermaid diagram in a message was auto-fixed by AI.
 * ChatViewModel subscribes and persists the corrected content to the database.
 */
data class DiagramFixedEvent(
    val messageId: String,
    val originalDiagram: String,
    val fixedDiagram: String,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails() = "Diagram auto-fixed in message $messageId"
}
