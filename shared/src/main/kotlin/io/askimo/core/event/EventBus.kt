/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Central event bus for all application events.
 * Events are automatically routed to appropriate channels based on isDeveloperEvent property.
 */
object EventBus {
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Developer events only (shown in developer dialog when enabled)
    private val _developerEvents = MutableSharedFlow<Event>(
        replay = 100, // Keep history for developer view
        extraBufferCapacity = 500,
    )

    // User events only (shown to end users)
    private val _userEvents = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 100,
    )

    /**
     * Developer-only events (requires developer mode enabled)
     * Subscribe to this for debugging/development tools
     */
    val developerEvents: SharedFlow<Event> = _developerEvents.asSharedFlow()

    /**
     * User-facing events (shown to end users)
     * Subscribe to this for user notifications and alerts
     */
    val userEvents: SharedFlow<Event> = _userEvents.asSharedFlow()

    /**
     * Emit an event - automatically routes to the appropriate channel
     * based on event.isDeveloperEvent property
     */
    suspend fun emit(event: Event) {
        if (event.isDeveloperEvent) {
            _developerEvents.emit(event)
        } else {
            _userEvents.emit(event)
        }
    }

    /**
     * Non-suspending emit (uses launch internally)
     */
    fun post(event: Event) {
        eventScope.launch {
            emit(event)
        }
    }
}
