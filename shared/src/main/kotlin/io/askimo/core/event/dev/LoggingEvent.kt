/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.dev

import io.askimo.core.event.DeveloperEvent
import io.askimo.core.event.EventSource
import java.time.Instant

sealed interface LoggingEvent : DeveloperEvent {
    override val source: EventSource get() = EventSource.SYSTEM

    data class LogMessage(
        val level: String,
        val logger: String,
        val message: String,
        override val timestamp: Instant = Instant.now(),
    ) : LoggingEvent {
        override fun getDetails(): String = "[$level] $logger: $message"
    }
}
