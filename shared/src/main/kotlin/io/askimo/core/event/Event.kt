/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

/**
 * Base interface for all events in the system.
 */
sealed interface Event {
    /**
     * Determines if this event should only be shown in developer mode
     */
    val isDeveloperEvent: Boolean get() = false

    /** When the event occurred */
    val timestamp: Instant

    /** Event source/category for filtering */
    val source: EventSource

    /** Get displayable details for this event */
    fun getDetails(): String
}

sealed interface DeveloperEvent : Event {
    override val isDeveloperEvent: Boolean get() = true
}

/**
 * Event sources/categories for organization and filtering
 */
enum class EventSource {
    STREAMING,
    SESSION,
    CHAT,
    DATABASE,
}
