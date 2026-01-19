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
 * Emitted when a new chat session is created.
 * UI components (like NavigationSidebar) can use this to refresh their session lists.
 */
data class SessionCreatedEvent(
    val sessionId: String,
    val projectId: String?,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.INTERNAL

    override fun getDetails() = "Session $sessionId created${projectId?.let { " in project $it" } ?: ""}"
}
