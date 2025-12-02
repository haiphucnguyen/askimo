/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event

import java.time.Instant

sealed interface ChatEvent : DeveloperEvent {
    override val source: EventSource get() = EventSource.CHAT

    val chatId: String

    data class ChatCreated(
        override val chatId: String,
        val prompt: String,
        override val timestamp: Instant = Instant.now(),
    ) : ChatEvent {
        override fun getDetails(): String = "Chat: $chatId..., Prompt: ${prompt.take(200)}..."
    }
}
