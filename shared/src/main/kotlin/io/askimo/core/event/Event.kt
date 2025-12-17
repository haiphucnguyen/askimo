/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

/**
 * Classification of events for routing and handling
 */
enum class EventType {
    /**
     * Events shown to end users in the UI (notifications, errors, success messages)
     */
    USER,

    /**
     * Events for internal component communication (not shown in UI)
     * Examples: ModelChangedEvent, ProjectIndexedEvent, MemorySummarizedEvent
     */
    INTERNAL,

    /**
     * Events for debugging and development tools (shown only in dev mode)
     */
    DEVELOPER,
}

/**
 * Base interface for all events in the system.
 */
interface Event {
    /**
     * Type of event determines routing and visibility
     */
    val type: EventType get() = EventType.USER

    /** When the event occurred */
    val timestamp: Instant

    /** Event source/category for filtering */
    val source: EventSource

    /** Get displayable details for this event */
    fun getDetails(): String
}

/**
 * Base interface for developer events (debugging and diagnostics)
 */
interface DeveloperEvent : Event {
    override val type: EventType get() = EventType.DEVELOPER
}

/**
 * Event sources/categories for organization and filtering
 */
enum class EventSource {
    SYSTEM,
}
