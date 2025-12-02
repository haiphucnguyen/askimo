/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

sealed interface DatabaseEvent : DeveloperEvent {
    override val source: EventSource get() = EventSource.DATABASE

    data class SaveCompleted(
        val message: String,
        override val timestamp: Instant = Instant.now(),
    ) : DatabaseEvent {
        override fun getDetails(): String = "Msg: $message"
    }
}
