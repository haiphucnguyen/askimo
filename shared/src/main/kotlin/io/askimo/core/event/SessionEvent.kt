/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

sealed interface SessionEvent : Event {
    override val source: EventSource get() = EventSource.SESSION

    val sessionId: String

    data class SummaryGenerated(
        override val sessionId: String,
        val chatId: String,
        val summary: String,
        val duration: Long,
        override val timestamp: Instant = Instant.now(),
    ) : SessionEvent {
        override fun getDetails(): String = "Session: ${sessionId.take(8)}..., Chat: ${chatId.take(8)}..."
    }

    data class SummaryFailed(
        override val sessionId: String,
        val chatId: String,
        val error: String,
        override val timestamp: Instant = Instant.now(),
    ) : SessionEvent {
        override fun getDetails(): String = "Session: ${sessionId.take(8)}..., Error: $error"
    }

    data class ContextPrepared(
        override val sessionId: String,
        val chatId: String,
        val contextSize: Int,
        val messageCount: Int,
        override val timestamp: Instant = Instant.now(),
    ) : SessionEvent {
        override fun getDetails(): String = "Session: ${sessionId.take(8)}..., Messages: $messageCount"
    }
}
