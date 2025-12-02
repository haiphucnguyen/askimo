/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

sealed interface StreamingEvent : DeveloperEvent {
    override val source: EventSource get() = EventSource.STREAMING

    val chatId: String
    val threadId: String

    data class StreamStarted(
        override val chatId: String,
        override val threadId: String,
        val userMessage: String,
        override val timestamp: Instant = Instant.now(),
    ) : StreamingEvent {
        override fun getDetails(): String = "Chat: $chatId, Thread: $threadId"
    }

    data class StreamCompleted(
        override val chatId: String,
        override val threadId: String,
        val fullResponse: String,
        val duration: Long,
        override val timestamp: Instant = Instant.now(),
    ) : StreamingEvent {
        override fun getDetails(): String = "Chat: $chatId, Duration: ${duration}ms"
    }

    data class StreamFailed(
        override val chatId: String,
        override val threadId: String,
        val error: String,
        val partialResponse: String?,
        override val timestamp: Instant = Instant.now(),
    ) : StreamingEvent {
        override fun getDetails(): String = "Chat: $chatId, Error: $error"
    }

    data class StreamStopped(
        override val chatId: String,
        override val threadId: String,
        override val timestamp: Instant = Instant.now(),
    ) : StreamingEvent {
        override fun getDetails(): String = "Chat: $chatId, Thread: $threadId"
    }
}
