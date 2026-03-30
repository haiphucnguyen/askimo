/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.event.internal
import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant
/**
 * Emitted after a chat session is successfully deleted from the local database.
 *
 * @param sessionId The ID of the deleted session.
 */
data class SessionDeletedEvent(
    val sessionId: String,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL
    override fun getDetails() = "Session $sessionId deleted"
}
