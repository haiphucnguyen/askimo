/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.system

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Internal event fired when user requests to invalidate caches.
 * This triggers cache cleanup across various cache systems.
 */
data class InvalidateCacheEvent(
    override val timestamp: Instant = Instant.now(),
) : Event {
    override val source: EventSource = EventSource.SYSTEM
    override val type: EventType = EventType.INTERNAL

    override fun getDetails(): String = "Cache invalidation requested"
}
