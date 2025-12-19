/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.internal

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Emitted when a session title is updated (generated or renamed).
 * UI components (like NavigationSidebar) can use this to refresh their session lists.
 */
data class SessionTitleUpdatedEvent(
    val sessionId: String,
    val newTitle: String,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails() = "Session $sessionId title updated to: $newTitle"
}
