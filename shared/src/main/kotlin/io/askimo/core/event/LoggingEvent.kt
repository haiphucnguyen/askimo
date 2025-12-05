/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

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
