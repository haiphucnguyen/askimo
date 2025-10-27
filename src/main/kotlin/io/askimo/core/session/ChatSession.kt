/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import java.time.LocalDateTime

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime,
)
